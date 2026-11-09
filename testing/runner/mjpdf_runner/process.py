from __future__ import annotations

import os
import json
import uuid
import shlex
import signal
import subprocess
import threading
import time

from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO, Callable, Iterator

from .model import CommandSpec, CompletedCommand

ADB_COMMAND_TIMEOUT_SECONDS = 300.0
INSTRUMENT_IDLE_TIMEOUT_SECONDS = 600.0


class HostResourceMonitor:
    def __init__(self, output: Path, proc_root: Path = Path("/proc"), interval: float = 10.0):
        if interval <= 0:
            raise ValueError("resource sampling interval must be positive")
        self.output = output
        self.proc_root = proc_root
        self.interval = interval
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._started = time.monotonic()

    def sample(self) -> dict[str, object]:
        record: dict[str, object] = {
            "epochSeconds": time.time(),
            "elapsedSeconds": time.monotonic() - self._started,
            "memoryUnit": "KiB",
            "vmstatPageSizeBytes": os.sysconf("SC_PAGE_SIZE"),
        }
        unavailable = []
        for filename, keys in (
            ("meminfo", {"MemTotal", "MemAvailable", "SwapTotal", "SwapFree"}),
            ("vmstat", {"pswpin", "pswpout", "pgmajfault"}),
        ):
            try:
                values = {}
                for line in (self.proc_root / filename).read_text().splitlines():
                    fields = line.replace(":", "").split()
                    if fields and fields[0] in keys:
                        values[fields[0]] = int(fields[1])
                record[filename] = values
            except (OSError, ValueError, IndexError):
                unavailable.append(filename)
        for resource in ("cpu", "memory", "io"):
            name = f"pressure/{resource}"
            try:
                values = {}
                for line in (self.proc_root / name).read_text().splitlines():
                    fields = line.split()
                    values[fields[0]] = {
                        key: int(value) if key == "total" else float(value)
                        for key, value in (field.split("=", 1) for field in fields[1:])
                    }
                record[name] = values
            except (OSError, ValueError, IndexError):
                unavailable.append(name)
        record["unavailable"] = unavailable
        return record

    def _write_sample(self) -> None:
        try:
            with self.output.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(self.sample(), sort_keys=True) + "\n")
        except OSError:
            self._stop.set()

    def _run(self) -> None:
        while not self._stop.wait(self.interval):
            self._write_sample()

    def __enter__(self) -> HostResourceMonitor:
        self._write_sample()
        if not self._stop.is_set():
            self._thread = threading.Thread(target=self._run, name="mjpdf-host-resources", daemon=True)
            self._thread.start()
        return self

    def __exit__(self, *_args: object) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=1.0)
        self._write_sample()


@dataclass
class CommandObserver:
    spec: CommandSpec
    proc: subprocess.Popen[bytes]
    stream: BinaryIO
    path: Path
    evidence_offset: int = 0



class CommandFailure(RuntimeError):
    def __init__(self, completed: CompletedCommand, message: str | None = None):
        super().__init__(message or f"command failed ({completed.returncode}): {shlex.join(completed.argv)}")
        self.completed = completed


class CommandTimeout(CommandFailure):
    def __init__(self, completed: CompletedCommand, name: str, seconds: float):
        super().__init__(completed, f"command[{name}] timed out after {seconds:g}s: "
                         f"{shlex.join(completed.argv)}")


class CommandRunner:
    """The only host subprocess boundary; command output is evidence, never UI."""

    def __init__(self, runner_log: Path, logs_dir: Path, instrument_raw: Path,
                 dry_run: bool = False):
        self.runner_log = runner_log
        self.logs_dir = logs_dir
        self.instrument_raw = instrument_raw
        self.dry_run = dry_run
        # Python signal handlers run on the main thread between bytecodes. Keep
        # publication lock-free so a handler can terminate the current process
        # even if it arrives at either assignment below.
        self._active: subprocess.Popen[bytes] | None = None
        self._pending_termination: int | None = None
        self._timing_stack: list[str] = []



    @staticmethod
    def _safe_name(name: str) -> str:
        cleaned = "".join(c if c.isalnum() or c in "._-" else "-" for c in name)
        return cleaned.strip("-") or "command"

    def record(self, message: str) -> None:
        with self.runner_log.open("a", encoding="utf-8") as fh:
            fh.write(message.rstrip("\n") + "\n")

    def run(self, spec: CommandSpec, on_line: Callable[[str], None] | None = None,
            check: bool = True) -> CompletedCommand:
        invocation = uuid.uuid4().hex
        parent = self._timing_stack[-1] if self._timing_stack else None
        self._timing_stack.append(invocation)
        started = time.monotonic()
        outcome = "error"
        returncode = None
        try:
            result = self._run(spec, on_line, check)
            returncode = result.returncode
            outcome = "dry-run" if self.dry_run else ("success" if returncode in spec.acceptable else "failure")
            return result
        except CommandTimeout as exc:
            outcome, returncode = "timeout", exc.completed.returncode
            raise
        except CommandFailure as exc:
            outcome, returncode = "failure", exc.completed.returncode
            raise
        finally:
            self._timing_stack.pop()
            record = {"invocationId": invocation, "parentInvocationId": parent, "name": spec.name,
                      "elapsedSeconds": time.monotonic() - started, "outcome": outcome,
                      "returncode": returncode, "instrumentation": spec.instrument}
            try:
                with (self.logs_dir.parent / "command-timings.jsonl").open("a") as output:
                    output.write(json.dumps(record, sort_keys=True) + "\n")
            except OSError:
                pass

    def _run(self, spec: CommandSpec, on_line: Callable[[str], None] | None = None,
            check: bool = True) -> CompletedCommand:
        command_text = shlex.join(spec.argv)
        self.record(f"command[{spec.name}] {command_text}")
        log_path = self.logs_dir / f"{self._safe_name(spec.name)}.log"
        if self.dry_run:
            with log_path.open("a", encoding="utf-8") as log:
                log.write(f"DRY-RUN {command_text}\n")
            return CompletedCommand(spec.argv, 0, "")

        env = os.environ.copy()
        if spec.env:
            env.update(spec.env)
        proc = subprocess.Popen(
            spec.argv,
            cwd=spec.cwd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        parent = self._active
        if parent is None:
            self._publish_active(proc)

        timeout = spec.timeout_seconds
        idle_timeout = None
        if timeout is None and Path(spec.argv[0]).name == "adb":
            if spec.instrument:
                idle_timeout = INSTRUMENT_IDLE_TIMEOUT_SECONDS
            else:
                timeout = ADB_COMMAND_TIMEOUT_SECONDS
        started = last_output = time.monotonic()
        expired = threading.Event()
        stop_watchdog = threading.Event()

        def watch() -> None:
            while not stop_watchdog.is_set():
                seconds = timeout if timeout is not None else idle_timeout
                assert seconds is not None
                deadline = (started if timeout is not None else last_output) + seconds
                remaining = deadline - time.monotonic()
                if remaining > 0:
                    stop_watchdog.wait(min(remaining, 0.2))
                    continue
                if proc.poll() is None:
                    expired.set()
                    try:
                        os.killpg(proc.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                return

        watchdog = (threading.Thread(target=watch, name=f"mjpdf-watch-{spec.name}", daemon=True)
                    if timeout is not None or idle_timeout is not None else None)
        if watchdog is not None:
            watchdog.start()
        chunks: list[str] = []
        raw = self.instrument_raw.open("ab") if spec.instrument else None
        try:
            assert proc.stdout is not None
            with proc.stdout, log_path.open("ab") as command_log:
                for data in iter(proc.stdout.readline, b""):
                    last_output = time.monotonic()
                    command_log.write(data)
                    if raw is not None:
                        raw.write(data)
                    text = data.decode("utf-8", errors="replace").rstrip("\r\n")
                    if not spec.instrument:
                        chunks.append(text)
                    if on_line is not None:
                        on_line(text)
            returncode = proc.wait()
        finally:
            stop_watchdog.set()
            if watchdog is not None:
                watchdog.join()
            if proc.poll() is None:
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    try:
                        os.killpg(proc.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    proc.wait()
            if raw is not None:
                raw.close()
            if self._active is proc:
                self._active = parent
        completed = CompletedCommand(spec.argv, returncode, "\n".join(chunks))
        if expired.is_set():
            seconds = timeout if timeout is not None else idle_timeout
            assert seconds is not None
            self.record(f"command[{spec.name}] timed out after {seconds:g}s")
            raise CommandTimeout(completed, spec.name, seconds)
        if check and returncode not in spec.acceptable:
            raise CommandFailure(completed)
        return completed

    def start_observer(self, spec: CommandSpec) -> CommandObserver:
        command_text = shlex.join(spec.argv)
        self.record(f"observer[{spec.name}] {command_text}")
        path = self.logs_dir / f"{self._safe_name(spec.name)}.log"
        stream = path.open("wb")
        env = os.environ.copy()
        if spec.env:
            env.update(spec.env)
        try:
            proc = subprocess.Popen(
                spec.argv,
                cwd=spec.cwd,
                env=env,
                stdout=stream,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
            self._publish_active(proc)
        except Exception:
            stream.close()
            raise


        if self._active is proc:
            self._active = None

        return CommandObserver(spec, proc, stream, path)

    def wait_observer(self, observer: CommandObserver, marker: str,
                      timeout: float = 5.0) -> None:
        deadline = time.monotonic() + timeout
        marker_bytes = marker.encode("utf-8")
        retained = bytearray()
        with observer.path.open("rb") as evidence:
            while True:
                data = evidence.readline()
                if data:
                    retained.extend(data)
                    if len(retained) > 65536:
                        del retained[:-65536]
                    if marker_bytes in data:
                        observer.evidence_offset = evidence.tell()
                        return
                    continue
                returncode = observer.proc.poll()
                output = retained.decode("utf-8", errors="replace")
                if returncode is not None:
                    completed = CompletedCommand(
                        observer.spec.argv, returncode if returncode else 1, output)
                    raise CommandFailure(completed)
                if time.monotonic() >= deadline:
                    raise CommandFailure(CompletedCommand(observer.spec.argv, 124, output))
                time.sleep(0.02)

    def observer_lines(self, observer: CommandObserver) -> Iterator[str]:
        with observer.path.open("rb") as evidence:
            evidence.seek(observer.evidence_offset)
            for data in evidence:
                yield data.decode("utf-8", errors="replace").rstrip("\r\n")

    @staticmethod
    def _observer_tail(path: Path, limit: int = 65536) -> str:
        with path.open("rb") as evidence:
            evidence.seek(0, os.SEEK_END)
            size = evidence.tell()
            evidence.seek(max(0, size - limit))
            return evidence.read(limit).decode("utf-8", errors="replace")


    def stop_observer(self, observer: CommandObserver) -> CompletedCommand:
        proc = observer.proc
        unexpected = proc.poll() is not None
        if not unexpected:
            try:
                os.killpg(proc.pid, signal.SIGTERM)
            except ProcessLookupError:
                unexpected = True
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
        observer.stream.close()
        output = self._observer_tail(observer.path)
        returncode = proc.returncode if unexpected else 0
        if returncode == 0 and unexpected:
            returncode = 1
        self.record(f"observer[{observer.spec.name}] exit {returncode}")
        return CompletedCommand(observer.spec.argv, returncode, output)

    def _publish_active(self, proc: subprocess.Popen[bytes]) -> None:
        self._active = proc
        sig = self._pending_termination
        if sig is None:
            return
        self._pending_termination = None
        try:
            os.killpg(proc.pid, sig)
        except ProcessLookupError:
            return

    def terminate_active(self, sig: int = signal.SIGTERM) -> None:
        self._pending_termination = sig
        proc = self._active
        if proc is None:
            return
        self._pending_termination = None
        try:
            os.killpg(proc.pid, sig)
        except ProcessLookupError:
            return

