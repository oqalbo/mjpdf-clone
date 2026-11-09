from __future__ import annotations

import shlex
from dataclasses import dataclass
from typing import TypeAlias

from .model import TestKey

STATUS = "INSTRUMENTATION_STATUS: "
STATUS_CODE = "INSTRUMENTATION_STATUS_CODE: "
RESULT = "INSTRUMENTATION_RESULT: "
CODE = "INSTRUMENTATION_CODE: "
KNOWN_CODES = {1, 0, -1, -2, -3, -4}
DIRECT_PHASE_MIN_API = 31


@dataclass(frozen=True)
class TestStarted:
    key: TestKey
    current: int | None
    total: int | None
    phase: int | None


@dataclass(frozen=True)
class TestFinished:
    key: TestKey
    raw_code: int
    stack: str
    duration: float
    phase: int | None


@dataclass(frozen=True)
class RunError:
    message: str


@dataclass(frozen=True)
class RunFinished:
    code: int


InstrumentationEvent: TypeAlias = TestStarted | TestFinished | RunError | RunFinished


class InstrumentationParser:
    """Incrementally parses `am instrument -r` without losing continuation lines."""

    def __init__(self, phase: int | None = None):
        self.phase = phase
        self.pending: dict[str, str] = {}
        self.pending_key: str | None = None
        self.started: list[tuple[TestKey, float, int | None]] = []
        self.tests_finished = 0
        self.saw_run_finished = False
        self.crash_status_attributed = False

    @staticmethod
    def _number(value: str | None) -> int | None:
        if value is None:
            return None
        try:
            return int(value.strip())
        except ValueError:
            return None

    def feed(self, line: str, now: float) -> list[InstrumentationEvent]:
        events: list[InstrumentationEvent] = []
        if line.startswith(STATUS):
            if not self.pending:
                self.crash_status_attributed = False
            body = line[len(STATUS):]
            if "=" in body:
                key, value = body.split("=", 1)
                self.pending_key = key
                self.pending[key] = value
            else:
                self.pending_key = None
            return events
        if line.startswith(STATUS_CODE):
            raw = line[len(STATUS_CODE):].strip()
            try:
                code = int(raw)
            except ValueError:
                events.append(RunError(f"noninteger instrumentation status code: {raw!r}"))
                code = -1
            key = TestKey(self.pending.get("class", "unknown"),
                          self.pending.get("test", "unknown"))
            if code not in KNOWN_CODES:
                events.append(RunError(f"unknown instrumentation status code {code} for {key.display()}"))
            if code == 1:
                started_phase = self._number(self.pending.get("mjpdfPhaseIndex")) or self.phase
                self.started.append((key, now, started_phase))
                events.append(TestStarted(key, self._number(self.pending.get("current")),
                                          self._number(self.pending.get("numtests")), started_phase))
            else:
                started_at = now
                occurrence_phase = self.phase
                for index in range(len(self.started) - 1, -1, -1):
                    if self.started[index][0] == key:
                        _, started_at, occurrence_phase = self.started.pop(index)
                        break
                self.tests_finished += 1
                stack = self.pending.get("stack", "")
                self.crash_status_attributed = "process crashed" in stack.lower()
                events.append(TestFinished(key, code, stack,
                                           max(0.0, now - started_at), occurrence_phase))
            self.pending = {}
            self.pending_key = None
            return events
        if line.startswith(RESULT):
            body = line[len(RESULT):]
            is_crash = "process crashed" in body.lower()
            if is_crash and self.crash_status_attributed:
                self.crash_status_attributed = False
            elif is_crash and self.started:
                key, started_at, occurrence_phase = self.started.pop()
                self.tests_finished += 1
                events.append(TestFinished(
                    key, -1, body, max(0.0, now - started_at), occurrence_phase,
                ))
            elif body.startswith("shortMsg=") or body.startswith("longMsg=") or is_crash:
                events.append(RunError(body))
            self.pending_key = None
            return events
        if line.startswith(CODE):
            raw = line[len(CODE):].strip()
            try:
                code = int(raw)
            except ValueError:
                events.append(RunError(f"noninteger instrumentation final code: {raw!r}"))
                code = -1
            self.saw_run_finished = True
            events.append(RunFinished(code))
            self.pending_key = None
            return events
        if "Process crashed" in line:
            events.append(RunError(line))
        if self.pending_key is not None:
            self.pending[self.pending_key] = self.pending.get(self.pending_key, "") + "\n" + line
        return events

    def finish(
        self,
        now: float,
        unfinished_reason: str = "test started and never reported a result, which means the process died while it was running",
    ) -> list[InstrumentationEvent]:
        events: list[InstrumentationEvent] = []
        for key, started_at, phase in self.started:
            events.append(TestFinished(
                key, -1,
                unfinished_reason,
                max(0.0, now - started_at), phase,
            ))
        self.started.clear()
        if not self.saw_run_finished:
            events.append(RunError("instrumentation ended without a final status code"))
        return events


def build_orchestrator_command(services_apk: str, test_package: str,
                               runner_class: str, services_package: str,
                               orchestrator_package: str, args: list[str]) -> str:
    prefix = [f"CLASSPATH={services_apk}", "app_process", "/",
              f"{services_package}.shellexecutor.ShellMain", "am", "instrument", "-r", "-w",
              "-e", "targetInstrumentation", f"{test_package}/{runner_class}"]
    command = " ".join([prefix[0], shlex.join(prefix[1:] + args + [
        f"{orchestrator_package}/{orchestrator_package}.AndroidTestOrchestrator"
    ])])
    return command


def build_direct_command(test_package: str, runner_class: str, args: list[str]) -> str:
    return shlex.join([
        "timeout", "150s", "am", "instrument", "-r", "-w", "--no-restart",
        *args,
        f"{test_package}/{runner_class}",
    ])
