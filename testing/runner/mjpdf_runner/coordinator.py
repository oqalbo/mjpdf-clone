from __future__ import annotations

import json
import math
import os
import signal
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import TextIO

from .apk_prep import ApkPreparationError, PreparedApks, prepare_apks, prepare_utility_apks
from .catalog import TestSpec, TestUnit
from .devices import DeviceInfo
from .fixtures import FixtureError, Fixtures, prepare_generated, cleanup_generated
from .interactive import WizardResult, panel_frame
from .model import (CommandSpec, ExecutionCounts, ExitCode, RendererLane, RunPaths,
                    RunnerConfig, RunState, RunSummary, TestKey, TestResult, TestStatus)
from .phases import parse_scenario
from .planner import DURATION_CALIBRATION, duration_factor, duration_profile_key
from .process import CommandFailure, CommandRunner
from .runner import Runner
from .results import write_junit
from .terminal import InlinePanel
from .ui import Renderer, SPINNER

PACKAGE = "com.gitlab.mudlej.MjPdfReader"
MAX_CONCURRENT_SOFTWARE_DEVICES = 8


def _clock(seconds: float) -> str:
    value = max(0, int(seconds))
    return f"{value // 60:02d}:{value % 60:02d}"


def _simple_key(key: TestKey) -> str:
    return f"{key.class_name.rsplit('.', 1)[-1]}#{key.method}"

SLOW_STEP_SECONDS = 5.0


def _duration(seconds: float) -> str:
    value = max(0.0, seconds)
    if value < 60:
        return f"{value:.1f}s"
    minutes, remainder = divmod(value, 60)
    if minutes < 60:
        return f"{int(minutes)}m {remainder:04.1f}s"
    hours, minutes = divmod(int(minutes), 60)
    return f"{hours}h {minutes:02d}m {remainder:04.1f}s"


def _elapsed(seconds: float) -> str:
    value = max(0, int(round(seconds)))
    if value < 60:
        return f"{value}s"
    minutes, remainder = divmod(value, 60)
    if minutes < 60:
        return f"{minutes}m {remainder:02d}s"
    hours, minutes = divmod(minutes, 60)
    return f"{hours}h {minutes:02d}m {remainder:02d}s"


def _between(left: str, right: str, width: int) -> str:
    gap = width - len(left) - len(right)
    return f"{left}{' ' * gap}{right}" if gap > 0 else f"{left} · {right}"


def _percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, math.ceil(len(ordered) * fraction) - 1))
    return ordered[index]


@dataclass(frozen=True)
class DeviceTestResult:
    serial: str
    result: TestResult




@dataclass
class DeviceProgress:
    device: DeviceInfo
    total: int
    started_at: float | None = None
    finished_at: float | None = None
    completed: int = 0
    current: TestKey | None = None
    current_started_at: float | None = None
    step: str = "Waiting"
    verdict: str = "WAIT"
    counts: dict[str, int] = field(default_factory=dict)
    not_run: int = 0
    exit_code: ExitCode = ExitCode.CLEAN
    warning_count: int = 0
    before_tests_seconds: float = 0.0
    test_seconds: float = 0.0
    after_tests_seconds: float = 0.0
    before_step_seconds: dict[str, float] = field(default_factory=dict)
    after_step_seconds: dict[str, float] = field(default_factory=dict)


def _record_step_timings(progress: DeviceProgress, state: RunState) -> None:
    section = "before"
    for step in state.steps:
        if step.started_at is None or step.finished_at is None:
            continue
        seconds = max(0.0, step.finished_at - step.started_at)
        if step.name == "Run tests":
            progress.test_seconds += seconds
            section = "after"
        elif section == "before":
            progress.before_tests_seconds += seconds
            progress.before_step_seconds[step.name] = (
                progress.before_step_seconds.get(step.name, 0.0) + seconds
            )
        else:
            progress.after_tests_seconds += seconds
            progress.after_step_seconds[step.name] = (
                progress.after_step_seconds.get(step.name, 0.0) + seconds
            )

@dataclass
class CoordinatorState:
    started_at: float
    run_id: str
    phase: str
    devices: dict[str, DeviceProgress]
    expected_executions: int
    results: list[DeviceTestResult] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    finished_at: float | None = None
    exit_code: ExitCode = ExitCode.CLEAN
    verdict: str = "RUNNING"


class ExecutionView:
    def __init__(self, panel: InlinePanel, state: CoordinatorState):
        self.panel = panel
        self.state = state
        self._lock = threading.RLock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._stop.clear()
        self.draw()
        self._thread = threading.Thread(target=self._refresh, name="mjpdf-fleet-tui", daemon=True)
        self._thread.start()

    def _refresh(self) -> None:
        while not self._stop.wait(0.1):
            self.draw()

    def changed(self) -> None:
        self.draw()

    @staticmethod
    def _device_result_label(progress: DeviceProgress) -> str:
        if progress.exit_code == ExitCode.CLEAN:
            return "PASSED"
        if progress.exit_code == ExitCode.FAILED:
            return (
                "OPERATIONAL FAILURE"
                if progress.verdict in {"ERROR", "OPERATIONAL FAILURE"}
                else "FAILED"
            )
        return {
            ExitCode.DEVICE_OR_PIN: "DEVICE/PIN FAILURE",
            ExitCode.LEFTOVERS: "LEFTOVERS",
            ExitCode.INTERRUPTED: "INTERRUPTED",
            ExitCode.TERMINATED: "TERMINATED",
        }.get(progress.exit_code, progress.verdict)

    def _final_frames(self) -> list[str]:
        width = min(max(30, self.panel.size.columns), 96)
        inner = width - 4
        serials = sorted(self.state.devices)
        lifecycle: list[str] = []
        results_by_serial: dict[str, list[TestResult]] = {
            serial: [
                item.result for item in self.state.results if item.serial == serial
            ]
            for serial in serials
        }
        for index, serial in enumerate(serials):
            progress = self.state.devices[serial]
            total_seconds = 0.0
            if progress.started_at is not None:
                end = progress.finished_at or self.state.finished_at or progress.started_at
                total_seconds = end - progress.started_at
            header = (
                f"{serial} · API {progress.device.api or '-'} · "
                f"{progress.device.short_renderer} · "
                f"{self._device_result_label(progress)}"
            )
            lifecycle.append(_between(header, _elapsed(total_seconds), inner))
            lifecycle.append(_between(
                "├─ Before tests", _duration(progress.before_tests_seconds), inner,
            ))
            before_slow = [
                (name, seconds)
                for name, seconds in progress.before_step_seconds.items()
                if seconds > SLOW_STEP_SECONDS
            ]
            for slow_index, (name, seconds) in enumerate(before_slow):
                branch = "└─" if slow_index == len(before_slow) - 1 else "├─"
                lifecycle.append(_between(
                    f"│  {branch} {name}", _duration(seconds), inner,
                ))
            lifecycle.append(_between(
                "├─ Tests",
                f"{_duration(progress.test_seconds)} · "
                f"{progress.completed}/{progress.total}",
                inner,
            ))
            lifecycle.append(_between(
                "└─ After tests", _duration(progress.after_tests_seconds), inner,
            ))
            after_slow = [
                (name, seconds)
                for name, seconds in progress.after_step_seconds.items()
                if seconds > SLOW_STEP_SECONDS
            ]
            for slow_index, (name, seconds) in enumerate(after_slow):
                branch = "└─" if slow_index == len(after_slow) - 1 else "├─"
                lifecycle.append(_between(
                    f"   {branch} {name}", _duration(seconds), inner,
                ))
            if index != len(serials) - 1:
                lifecycle.append("")

        frames = panel_frame(
            "Emulator runs", lifecycle, self.panel.size.columns, minimum=96,
        )
        frames.append("")

        label_width = max([len("Fleet total"), *(len(serial) for serial in serials)])
        metrics: list[str] = ["", "RESULTS"]
        metrics.append(
            f"{'':<{label_width}}  {'Passed':>7}  {'Failed':>7}  "
            f"{'Skipped':>7}  {'Not run':>7}  {'Total':>7}  {'Warnings':>8}"
        )
        fleet_results: list[TestResult] = []
        total_warnings = 0
        for serial in serials:
            device_results = results_by_serial[serial]
            fleet_results.extend(device_results)
            progress = self.state.devices[serial]
            counts = ExecutionCounts.from_results(
                device_results, progress.total, progress.not_run)
            warnings = progress.warning_count
            total_warnings += warnings
            metrics.append(
                f"{serial:<{label_width}}  {counts.passed:>7}  {counts.failed:>7}  "
                f"{counts.skipped:>7}  {counts.not_run:>7}  {counts.total:>7}  "
                f"{warnings:>8}"
            )
        metrics.append("─" * inner)
        fleet_counts = ExecutionCounts.from_results(
            fleet_results, self.state.expected_executions,
            sum(progress.not_run for progress in self.state.devices.values()))
        metrics.append(
            f"{'Fleet total':<{label_width}}  {fleet_counts.passed:>7}  "
            f"{fleet_counts.failed:>7}  {fleet_counts.skipped:>7}  "
            f"{fleet_counts.not_run:>7}  {fleet_counts.total:>7}  "
            f"{total_warnings:>8}"
        )
        metrics += ["", "TEST DURATION"]
        metrics.append(
            f"{'':<{label_width}}  {'Average':>9}  {'P95':>9}  "
            f"{'P99':>9}  {'Slowest':>9}"
        )
        for serial in serials:
            durations = [result.duration_seconds for result in results_by_serial[serial]]
            average = sum(durations) / len(durations) if durations else 0.0
            metrics.append(
                f"{serial:<{label_width}}  {_duration(average):>9}  "
                f"{_duration(_percentile(durations, 0.95)):>9}  "
                f"{_duration(_percentile(durations, 0.99)):>9}  "
                f"{_duration(max(durations, default=0.0)):>9}"
            )
        metrics.append("─" * inner)
        fleet_durations = [result.duration_seconds for result in fleet_results]
        fleet_average = (
            sum(fleet_durations) / len(fleet_durations) if fleet_durations else 0.0
        )
        metrics.append(
            f"{'All tests':<{label_width}}  {_duration(fleet_average):>9}  "
            f"{_duration(_percentile(fleet_durations, 0.95)):>9}  "
            f"{_duration(_percentile(fleet_durations, 0.99)):>9}  "
            f"{_duration(max(fleet_durations, default=0.0)):>9}"
        )

        metrics += ["", "RUNTIME"]
        for serial in serials:
            progress = self.state.devices[serial]
            total_seconds = 0.0
            if progress.started_at is not None:
                end = progress.finished_at or self.state.finished_at or progress.started_at
                total_seconds = end - progress.started_at
            metrics.append(_between(serial, _elapsed(total_seconds), inner))
        fleet_elapsed = (
            (self.state.finished_at - self.state.started_at)
            if self.state.finished_at is not None else 0.0
        )
        cumulative = sum(fleet_durations)
        parallelism = cumulative / fleet_elapsed if fleet_elapsed > 0 else 0.0
        metrics.append(_between("Fleet elapsed", _elapsed(fleet_elapsed), inner))
        metrics.append(_between("Cumulative tests", _elapsed(cumulative), inner))
        metrics.append(_between("Parallelism", f"{parallelism:.2f}×", inner))
        if self.state.errors:
            message = self.state.errors[0].replace("\n", " ").strip()
            if len(self.state.errors) > 1:
                message += f" (+{len(self.state.errors) - 1} more; see summary.json)"
            metrics += ["", _between("ERROR", message, inner)]
        metrics += [
            "",
            _between(
                "RESULT",
                f"{self.state.verdict} · exit {int(self.state.exit_code)}",
                inner,
            ),
            _between(
                "RUN", f"testing/runner/runs/{self.state.run_id}", inner,
            ),
            "",
        ]
        frames.extend(panel_frame(
            "Metrics summary", metrics, self.panel.size.columns, minimum=96,
        ))
        return frames

    def _frame(self, final: bool = False) -> list[str]:
        if final:
            with self._lock:
                return self._final_frames()
        with self._lock:
            now = self.state.finished_at if final and self.state.finished_at is not None else time.time()
            elapsed = now - self.state.started_at
            completed = len(self.state.results)
            live_total = max(self.state.expected_executions, completed)
            spinner = SPINNER[int(elapsed * 10) % len(SPINNER)]
            title_state = "Complete" if final else self.state.phase
            body = [
                (f"Build {'✓' if self.state.phase not in {'Preparing', 'Building', 'Preparing APKs'} else spinner} · "
                 f"Execute {completed}/{live_total} · "
                 f"Finalize {'✓' if final else '-'}"),
                "",
            ]
            for serial in sorted(self.state.devices):
                progress = self.state.devices[serial]
                device_elapsed = 0.0
                if progress.started_at is not None:
                    device_end = progress.finished_at if progress.finished_at is not None else now
                    device_elapsed = device_end - progress.started_at
                prefix = (
                    f"run {_clock(device_elapsed)} · {serial} · "
                    f"API {progress.device.api or '-'} · "
                    f"{progress.device.short_renderer} · "
                    f"{progress.completed}/{progress.total}"
                )
                if final:
                    detail = progress.verdict
                elif progress.current is not None:
                    test_elapsed = now - (progress.current_started_at or now)
                    detail = (
                        f"RUN · test {_clock(test_elapsed)} · "
                        f"{_simple_key(progress.current)}"
                    )
                else:
                    detail = progress.step
                body.append(f"{prefix} · {detail}")
            counts = {
                status.value: sum(item.result.status is status for item in self.state.results)
                for status in TestStatus
            }
            failed = counts["FAIL"] + counts["ERROR"]
            warnings = sum(progress.warning_count for progress in self.state.devices.values())
            body += [
                "",
                f"{counts['PASS']} pass · {failed} fail · {counts['SKIP']} skip · "
                f"warnings {warnings}",
            ]
            if final:
                body += [
                    f"VERDICT {self.state.verdict} · exit {int(self.state.exit_code)}",
                    f"RUN testing/runner/runs/{self.state.run_id}",
                ]
            else:
                body.append(
                    f"logs runs/{self.state.run_id}/devices/*/batches/*/runner.log · "
                    "Ctrl-C cancels"
                )
            return panel_frame(
                f"MJ PDF · {title_state}", body, self.panel.size.columns,
                _clock(elapsed), minimum=78,
            )

    def draw(self) -> None:
        self.panel.draw(self._frame())

    def stop_and_erase(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2)
            self._thread = None
        self.panel.erase()

    def print_final(self) -> None:
        self.panel.print_static(self._frame(final=True))
        self.panel.close()


class WorkerRenderer(Renderer):
    def __init__(self, coordinator: "Coordinator", serial: str, completed_offset: int,
                 warning_offset: int):
        self.coordinator = coordinator
        self.serial = serial
        self.completed_offset = completed_offset
        self.warning_offset = warning_offset
        self.published: list[TestResult] = []


    def start(self, state: RunState) -> None:
        self.update(state)

    def update(self, state: RunState) -> None:
        self.coordinator.worker_update(
            self.serial, self.completed_offset, self.warning_offset, state
        )

    def notice(self, message: str, warning: bool = False) -> None:
        return

    def finish(self, result: TestResult) -> None:
        self.published.append(result)
        self.coordinator.worker_finish(self.serial, result)

    def reconcile(self, results: tuple[TestResult, ...]) -> None:
        self.coordinator.worker_reconcile(self.serial, self.published, results)
        self.published = list(results)


    def close(self) -> None:
        return


@dataclass(frozen=True)
class Batch:
    name: str
    units: tuple[TestUnit, ...]
    scenario: str | None = None
    hostile: bool = False

    @property
    def tests(self) -> tuple[TestSpec, ...]:
        return tuple(test for unit in self.units for test in unit.tests)


class Coordinator:
    def __init__(self, selection: WizardResult, repo_root: Path, runner_dir: Path,
                 paths: RunPaths, adb: str, panel: InlinePanel, stream: TextIO,
                 force_stop_packages: tuple[str, ...] = ()):
        self.selection = selection
        self.force_stop_packages = force_stop_packages
        self.repo_root = repo_root
        self.runner_dir = runner_dir
        self.paths = paths
        self.adb = adb
        self.stream = stream
        counts = selection.plan.counts()
        self._planned_skip_counts = selection.plan.skipped_counts()
        skipped_results = [
            DeviceTestResult(
                serial,
                TestResult(
                    TestKey(test.class_name, test.method), None, 0, TestStatus.SKIP, 0.0,
                    "Skipped by the host planner because the selected emulator does not "
                    "satisfy this test's API or pinned-profile requirement.",
                ),
            )
            for serial, units in selection.plan.skipped.items()
            for unit in units
            for test in unit.tests
        ]
        self.state = CoordinatorState(
            started_at=time.time(), run_id=paths.root.name, phase="Preparing",
            devices={device.serial: DeviceProgress(
                device,
                counts[device.serial] + self._planned_skip_counts[device.serial],
                completed=self._planned_skip_counts[device.serial],
            )
                      for device in selection.devices},
            expected_executions=selection.plan.execution_count + selection.plan.skipped_count,
            results=skipped_results,
        )
        self._lock = threading.RLock()
        self.view = ExecutionView(panel, self.state)
        self.view._lock = self._lock
        self._cancelled = threading.Event()
        self._active_runners: dict[str, Runner] = {}
        self._cancel_signal: int | None = None
        self._active_build: CommandRunner | None = None
        self._prepared_apks: PreparedApks | None = None
        self._prepared_fixtures: Path | None = None
        self._utility_apks: tuple[Path, ...] = ()
        self._device_summaries: dict[str, list[RunSummary]] = {
            device.serial: [] for device in selection.devices
        }
        self._device_isolation: set[str] = set()

    def interrupt(self, signum: int) -> None:
        with self._lock:
            if self._cancel_signal is None:
                self._cancel_signal = signum
            self._cancelled.set()
            runners = tuple(self._active_runners.values())
            build = self._active_build
            effective_signal = self._cancel_signal
        if build is not None:
            build.terminate_active(effective_signal)
        for runner in runners:
            runner.interrupt(effective_signal)

    def worker_update(self, serial: str, completed_offset: int, warning_offset: int,
                      state: RunState) -> None:
        with self._lock:
            progress = self.state.devices[serial]
            planned_skips = getattr(self, "_planned_skip_counts", {}).get(serial, 0)
            if progress.started_at is None:
                progress.started_at = time.time()
            progress.completed = (
                planned_skips + completed_offset + len(state.tests)
            )
            progress.total = max(progress.total, progress.completed)
            progress.current = state.current_test
            progress.current_started_at = state.current_test_started_at
            active = state.active_step
            progress.step = active.name if active is not None else progress.step
            progress.warning_count = warning_offset + len(state.warnings)
        self.view.changed()

    def worker_finish(self, serial: str, result: TestResult) -> None:
        with self._lock:
            self.state.results.append(DeviceTestResult(serial, result))

    def worker_reconcile(self, serial: str, published: list[TestResult],
                         results: tuple[TestResult, ...]) -> None:
        retained = {id(result) for result in results}
        discarded = {id(result) for result in published if id(result) not in retained}
        if not discarded:
            return
        with self._lock:
            self.state.results[:] = [
                item for item in self.state.results
                if item.serial != serial or id(item.result) not in discarded
            ]
        self.view.changed()

    def _base_config(self, device: DeviceInfo, run_id: str, test_file: Path | None = None,
                     scenario: str | None = None,
                     requirements: frozenset[str] | None = None) -> RunnerConfig:
        return RunnerConfig(
            repo_root=self.repo_root,
            runner_dir=self.runner_dir,
            run_id=run_id,
            serial=device.serial,
            adb=self.adb,
            package=PACKAGE,
            test_package=f"{PACKAGE}.test",
            profile_size=os.environ.get("PROFILE_SIZE", "1080x2400"),
            profile_density=os.environ.get("PROFILE_DENSITY", "440"),
            profile_locale=os.environ.get("PROFILE_LOCALE", "en-US"),
            profile_timezone=os.environ.get("PROFILE_TIMEZONE", "UTC"),
            logcat_buffer=os.environ.get("LOGCAT_BUFFER", "64M"),
            renderer_lane=device.renderer,
            app_apk=self._prepared_apks.app if self._prepared_apks is not None else None,
            test_apk=self._prepared_apks.test if self._prepared_apks is not None else None,
            test_file=test_file,
            phase_scenario=scenario,
            required_fixtures=requirements,
            fixture_resolution="coordinator batch selection",
            prepared_fixtures=self._prepared_fixtures,
            utility_apks=self._utility_apks,
            trace_timing=os.environ.get("MJPDF_TRACE_TIMING") == "1",
            force_stop_packages=self.force_stop_packages,

            skip_build=True,
        )

    def _build_once(self) -> None:
        self.state.phase = "Building"
        self.view.changed()
        device = self.selection.devices[0]
        config = self._base_config(device, f"{self.paths.root.name}-build")
        commands = CommandRunner(self.paths.root / "runner.log", self.paths.logs,
                                 self.paths.root / "instrument-raw.txt")
        fixtures = Fixtures(config, commands, self.paths.root,
                            lambda message: self.state.errors.append(message),
                            lambda _message: None)
        try:
            fixtures.check_asset_drift()
        except FixtureError as exc:
            raise RuntimeError(str(exc)) from exc
        spec = CommandSpec((str(self.repo_root / "gradlew"), "-p", str(self.repo_root),
                            ":app:assembleDebug", ":app:assembleDebugAndroidTest"),
                           "gradle-build", cwd=self.repo_root)
        with self._lock:
            self._active_build = commands
            cancel_signal = self._cancel_signal
        if cancel_signal is not None:
            commands.terminate_active(cancel_signal)
        try:
            commands.run(spec)
            self.state.phase = "Preparing APKs"
            self.view.changed()
            self._prepared_apks = prepare_apks(
                self.repo_root,
                self.paths.root / "apks",
                commands,
            )
            self._utility_apks = prepare_utility_apks(
                self.repo_root, self.runner_dir, self.paths.root / "utility-apks", commands)
            requirements = frozenset(resource for units in self.selection.plan.assignments.values()
                                     for unit in units for resource in unit.required_fixtures)
            self._prepared_fixtures = prepare_generated(
                config, self.paths.root / "prepared-fixtures", requirements, self._cancelled.is_set)
        except CommandFailure as exc:
            if self._cancelled.is_set():
                return
            raise RuntimeError("Gradle build failed") from exc
        except ApkPreparationError as exc:
            raise RuntimeError(f"APK preparation failed: {exc}") from exc
        finally:
            with self._lock:
                if self._active_build is commands:
                    self._active_build = None

    @staticmethod
    def _batches(units: tuple[TestUnit, ...]) -> tuple[Batch, ...]:
        ordinary = tuple(unit for unit in units if unit.scenario is None and not unit.hostile)
        hostile = tuple(unit for unit in units if unit.scenario is None and unit.hostile)
        scenarios = tuple(unit for unit in units if unit.scenario is not None)
        batches: list[Batch] = []
        if ordinary:
            batches.append(Batch("ordinary", ordinary))
        batches.extend(Batch(f"hostile-{index + 1}", (unit,), hostile=True)
                       for index, unit in enumerate(hostile))
        batches.extend(Batch(f"scenario-{unit.scenario}", (unit,), scenario=unit.scenario)
                       for unit in scenarios)
        return tuple(batches)

    def _write_shard(self, serial: str, index: int, batch: Batch) -> Path | None:
        selectors = sorted({unit.selector for unit in batch.units if unit.selector is not None})
        if not selectors:
            return None
        directory = self.paths.root / "shards"
        directory.mkdir(exist_ok=True)
        path = directory / f"{serial}-{index:03d}-{batch.name}.txt"
        path.write_text("\n".join(selectors) + "\n", encoding="utf-8")
        return path

    @staticmethod
    def _matches(expected: TestSpec, result: TestResult) -> bool:
        return (result.key.class_name == expected.class_name
                and (result.key.method == expected.method
                     or result.key.method.startswith(expected.method + "[")))

    def _run_device(self, device: DeviceInfo, units: tuple[TestUnit, ...]) -> None:
        serial = device.serial
        progress = self.state.devices[serial]
        progress.started_at = time.time()
        device_root = self.paths.root / "devices" / serial
        batches_root = device_root / "batches"
        reported_assignments = 0
        incomplete_assignments = 0
        incomplete_batches: list[str] = []
        device_exit = ExitCode.CLEAN
        operational_details: list[str] = []
        completed_offset = 0
        warning_offset = 0
        device_results: list[DeviceTestResult] = []
        planned_skip_results = [
            item.result for item in self.state.results
            if item.serial == serial and item.result.status is TestStatus.SKIP
        ]
        planned_skip_count = len(planned_skip_results)
        planned_assignments = progress.total - planned_skip_count
        summaries: list[dict[str, object]] = []
        batches = self._batches(units)
        for index, batch in enumerate(batches, 1):
            if self._cancelled.is_set():
                break
            shard = self._write_shard(serial, index, batch)
            run_id = f"{self.paths.root.name}-{serial}-{index:03d}"
            batch_paths = RunPaths.create(batches_root / f"{index:03d}-{batch.name}")
            scenario = None
            if batch.scenario:
                scenario = parse_scenario(
                    self.runner_dir / "phases" / f"{batch.scenario}.phases",
                    batch.scenario,
                )
            requirements = frozenset(resource for unit in batch.units for resource in unit.required_fixtures)
            config = self._base_config(device, run_id, shard, batch.scenario, requirements)
            renderer = WorkerRenderer(self, serial, completed_offset, warning_offset)
            runner = Runner(config, batch_paths, renderer, scenario)
            with self._lock:
                self._active_runners[serial] = runner
                cancel_signal = getattr(self, "_cancel_signal", None)
            if cancel_signal is not None:
                runner.interrupt(cancel_signal)
            timeout = (
                threading.Timer(
                    180.0,
                    lambda batch_runner=runner: batch_runner.interrupt(signal.SIGTERM),
                )
                if batch.hostile else None
            )
            if timeout is not None:
                timeout.start()
            try:
                code = runner.run()
            finally:
                if timeout is not None:
                    timeout.cancel()
                    timeout.join()
                with self._lock:
                    self._active_runners.pop(serial, None)
            assert runner.summary is not None
            with self._lock:
                _record_step_timings(progress, runner.state)
                progress.warning_count = warning_offset + runner.summary.warning_count
            self._device_summaries[serial].append(runner.summary)
            actual = list(runner.summary.tests)
            missing = [test for test in batch.tests
                       if not any(self._matches(test, result) for result in actual)]
            if runner.scenario_skipped and missing:
                reason = runner.scenario_skip_reason or "scenario requirements not met"
                platform_skips = [
                    TestResult(TestKey(test.class_name, test.method), None, -3,
                               TestStatus.SKIP, 0.0, reason)
                    for test in missing
                ]
                runner.record_platform_skips(platform_skips)
                actual = list(runner.summary.tests)
                missing = []
            for result in actual:
                device_results.append(DeviceTestResult(serial, result))
            if (missing and code != ExitCode.DEVICE_OR_PIN
                    and not self._cancelled.is_set()):
                incomplete_assignments += len(missing)
                incomplete_batches.append(batch.name)
                device_exit = ExitCode.FAILED
            has_failing_result = any(
                result.status in {TestStatus.FAIL, TestStatus.ERROR}
                for result in actual)
            if code == ExitCode.FAILED:
                device_exit = ExitCode.FAILED
                if runner.summary.run_errors:
                    detail = "; ".join(runner.summary.run_errors)
                    operational_details.append(f"{batch.name}: {detail}")
                elif not has_failing_result:
                    operational_details.append(
                        f"{batch.name}: {runner.summary.verdict}")
            elif code == ExitCode.LEFTOVERS and device_exit == ExitCode.CLEAN:
                device_exit = ExitCode.LEFTOVERS
            elif (code not in {ExitCode.CLEAN, ExitCode.DEVICE_OR_PIN}
                  and not self._cancelled.is_set()):
                device_exit = ExitCode.FAILED
                operational_details.append(
                    f"{batch.name}: runner verdict {runner.summary.verdict} "
                    f"(exit {int(code)})")
            reported_assignments += len(batch.tests) - len(missing)
            completed_offset += len(actual)
            warning_offset += runner.summary.warning_count
            with self._lock:
                progress.completed = planned_skip_count + completed_offset
                progress.total = max(progress.total, completed_offset)
                progress.current = None
                progress.current_started_at = None
                progress.step = ("Device isolation"
                                 if code is ExitCode.DEVICE_OR_PIN else "Next batch")
            summaries.append({
                "batch": batch.name,
                "path": str(batch_paths.root),
                "exitCode": int(code),
                "verdict": runner.summary.verdict,
                "tests": len(actual),
                "missing": len(missing),
            })
            if code is ExitCode.DEVICE_OR_PIN:
                reason = "; ".join(runner.summary.run_errors) or (
                    f"{serial}: device isolation failure in batch {batch.name}"
                )
                with self._lock:
                    self._device_isolation.add(serial)
                    self.state.errors.append(f"{serial}: {reason}")
                for future_index, future_batch in enumerate(batches[index:], index + 1):
                    summaries.append({
                        "batch": future_batch.name,
                        "path": str(batches_root / f"{future_index:03d}-{future_batch.name}"),
                        "exitCode": int(ExitCode.DEVICE_OR_PIN),
                        "verdict": "NOT RUN - DEVICE ISOLATION",
                        "tests": 0,
                        "missing": len(future_batch.tests),
                    })
                self.view.changed()
                break
            self.view.changed()
        error_parts: list[str] = []
        if incomplete_assignments:
            names = ", ".join(incomplete_batches)
            error_parts.append(
                f"{incomplete_assignments} assigned test(s) did not run "
                f"after batch(es): {names}")
        error_parts.extend(operational_details)
        if error_parts:
            with self._lock:
                self.state.errors.append(f"{serial}: {'; '.join(error_parts)}")
        actual_results = planned_skip_results + [item.result for item in device_results]
        outcome_counts = ExecutionCounts.from_results(
            actual_results, progress.total,
            not_run=planned_assignments - reported_assignments)
        progress.counts = {
            TestStatus.PASS.value: outcome_counts.passed,
            TestStatus.FAIL.value: sum(result.status is TestStatus.FAIL for result in actual_results),
            TestStatus.ERROR.value: sum(result.status is TestStatus.ERROR for result in actual_results),
            TestStatus.SKIP.value: outcome_counts.skipped,
        }
        progress.not_run = outcome_counts.not_run
        progress.completed = outcome_counts.reported
        progress.total = outcome_counts.total
        progress.finished_at = time.time()
        progress.current = None
        progress.current_started_at = None
        if any(result.status in {TestStatus.FAIL, TestStatus.ERROR}
               for result in actual_results):
            device_exit = ExitCode.FAILED
        if serial in getattr(self, "_device_isolation", set()):
            progress.exit_code = ExitCode.DEVICE_OR_PIN
            progress.verdict = "DEVICE/PIN FAILURE"
        elif self._cancelled.is_set():
            progress.exit_code = (
                ExitCode.TERMINATED
                if getattr(self, "_cancel_signal", None) == signal.SIGTERM
                else ExitCode.INTERRUPTED
            )
            progress.verdict = (
                "TERMINATED" if progress.exit_code == ExitCode.TERMINATED
                else "INTERRUPTED")
        else:
            progress.exit_code = device_exit
            progress.verdict = {
                ExitCode.CLEAN: "CLEAN",
                ExitCode.LEFTOVERS: "LEFTOVERS",
            }.get(
                device_exit,
                "OPERATIONAL FAILURE" if error_parts else "FAILED",
            )
        progress.step = progress.verdict
        (device_root / "summary.json").write_text(json.dumps({
            "device": serial,
            "model": device.model,
            "api": device.api,
            "renderer": device.renderer.value,
            "plannedTests": progress.total,
            "reportedResults": outcome_counts.reported,
            "notRun": outcome_counts.not_run,
            "verdict": progress.verdict,
            "exitCode": int(progress.exit_code),
            "counts": progress.counts,
            "durationSeconds": (
                progress.finished_at - progress.started_at
                if progress.started_at is not None and progress.finished_at is not None
                else 0.0
            ),
            "timings": {
                "beforeTestsSeconds": progress.before_tests_seconds,
                "testSeconds": progress.test_seconds,
                "afterTestsSeconds": progress.after_tests_seconds,
                "beforeStepsSeconds": progress.before_step_seconds,
                "afterStepsSeconds": progress.after_step_seconds,
            },
            "batches": summaries,
        }, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        self.view.changed()

    def _run_device_scheduled(
        self,
        device: DeviceInfo,
        units: tuple[TestUnit, ...],
        software_slots: threading.BoundedSemaphore,
    ) -> None:
        if device.renderer is not RendererLane.SOFTWARE:
            self._run_device_safe(device, units)
            return
        with self._lock:
            self.state.devices[device.serial].step = "Waiting for software slot"
        self.view.changed()
        with software_slots:
            self._run_device_safe(device, units)

    def _run_device_safe(self, device: DeviceInfo, units: tuple[TestUnit, ...]) -> None:
        try:
            self._run_device(device, units)
        except Exception as exc:
            message = f"{device.serial}: {exc}"
            with self._lock:
                self.state.errors.append(message)
                progress = self.state.devices[device.serial]
                if progress.started_at is None:
                    progress.started_at = self.state.started_at
                progress.finished_at = time.time()
                progress.current = None
                progress.current_started_at = None
                progress.verdict = "ERROR"
                progress.step = "ERROR"
                existing = [item.result for item in self.state.results
                            if item.serial == device.serial]
                assigned = tuple(test for unit in units for test in unit.tests)
                planned = len(assigned) if assigned else progress.total
                not_run = sum(
                    not any(self._matches(test, result) for result in existing)
                    for test in assigned)
                if not assigned:
                    not_run = max(0, planned - len(existing))
                counts = ExecutionCounts.from_results(
                    existing, planned, not_run=not_run)
                progress.completed = counts.reported
                progress.not_run = counts.not_run
                progress.total = counts.total
                progress.exit_code = ExitCode.FAILED
                progress.counts = {
                    TestStatus.PASS.value: counts.passed,
                    TestStatus.FAIL.value: sum(result.status is TestStatus.FAIL
                                               for result in existing),
                    TestStatus.ERROR.value: sum(result.status is TestStatus.ERROR
                                                for result in existing),
                    TestStatus.SKIP.value: counts.skipped,
                }
            device_root = self.paths.root / "devices" / device.serial
            device_root.mkdir(parents=True, exist_ok=True)
            (device_root / "summary.json").write_text(json.dumps({
                "device": device.serial,
                "model": device.model,
                "api": device.api,
                "renderer": device.renderer.value,
                "plannedTests": progress.total,
                "reportedResults": counts.reported,
                "notRun": counts.not_run,
                "verdict": progress.verdict,
                "exitCode": int(progress.exit_code),
                "counts": progress.counts,
                "error": message,
                "durationSeconds": (
                    progress.finished_at - progress.started_at
                    if progress.started_at is not None and progress.finished_at is not None
                    else 0.0
                ),
                "timings": {
                    "beforeTestsSeconds": progress.before_tests_seconds,
                    "testSeconds": progress.test_seconds,
                    "afterTestsSeconds": progress.after_tests_seconds,
                    "beforeStepsSeconds": progress.before_step_seconds,
                    "afterStepsSeconds": progress.after_step_seconds,
                },
            }, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            self.view.changed()

    def _write_manifest(self) -> None:
        estimates = self.selection.plan.estimated_seconds(self.selection.devices)
        fallbacks = self.selection.plan.duration_fallback_counts()
        payload = {
            "schemaVersion": 1,
            "runId": self.paths.root.name,
            "mode": self.selection.plan.mode.value,
            "areas": list(self.selection.areas),
            "selectedTests": len(self.selection.tests),
            "executionCount": self.selection.plan.execution_count,
            "skippedIncompatible": self.selection.plan.skipped_count,
            "durationCalibration": DURATION_CALIBRATION,
            "devices": [{
                "serial": device.serial,
                "model": device.model,
                "api": device.api,
                "renderer": device.renderer.value,
                "assignedTests": self.selection.plan.counts()[device.serial],
                "requiredFixtures": sorted({resource for unit in self.selection.plan.assignments[device.serial]
                                            for resource in unit.required_fixtures}),
                "estimatedTestSeconds": round(estimates[device.serial], 2),
                "estimatedWorkSeconds": round(estimates[device.serial], 2),
                "durationFallbackTests": fallbacks[device.serial],
                "durationProfile": duration_profile_key(device),
                "durationFactor": duration_factor(device),
                "durationProfileCalibrated": duration_profile_key(device)
                in DURATION_CALIBRATION["profileFactors"],
            } for device in self.selection.devices],
        }
        (self.paths.root / "manifest.json").write_text(
            json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

    def _finish(self) -> None:
        self.state.results.sort(
            key=lambda item: (item.serial, item.result.key.class_name, item.result.key.method)
        )
        self.state.finished_at = time.time()
        results_by_serial = {
            serial: [item.result for item in self.state.results if item.serial == serial]
            for serial in self.state.devices
        }
        for serial, progress in self.state.devices.items():
            inferred_not_run = max(
                0, progress.total - len(results_by_serial[serial]))
            progress.not_run = max(progress.not_run, inferred_not_run)
            device_counts = ExecutionCounts.from_results(
                results_by_serial[serial], progress.total, progress.not_run)
            progress.completed = device_counts.reported
            progress.total = device_counts.total
            progress.counts = {
                TestStatus.PASS.value: device_counts.passed,
                TestStatus.FAIL.value: sum(
                    result.status is TestStatus.FAIL for result in results_by_serial[serial]),
                TestStatus.ERROR.value: sum(
                    result.status is TestStatus.ERROR for result in results_by_serial[serial]),
                TestStatus.SKIP.value: device_counts.skipped,
            }
            device_summary = self.paths.root / "devices" / serial / "summary.json"
            if not device_summary.exists():
                device_summary.parent.mkdir(parents=True, exist_ok=True)
                device_summary.write_text(json.dumps({
                    "device": serial,
                    "model": progress.device.model,
                    "api": progress.device.api,
                    "renderer": progress.device.renderer.value,
                    "plannedTests": progress.total,
                    "reportedResults": device_counts.reported,
                    "notRun": device_counts.not_run,
                    "verdict": progress.verdict,
                    "exitCode": int(progress.exit_code),
                    "counts": progress.counts,
                    "durationSeconds": (
                        progress.finished_at - progress.started_at
                        if (progress.started_at is not None
                            and progress.finished_at is not None)
                        else 0.0
                    ),
                    "timings": {
                        "beforeTestsSeconds": progress.before_tests_seconds,
                        "testSeconds": progress.test_seconds,
                        "afterTestsSeconds": progress.after_tests_seconds,
                        "beforeStepsSeconds": progress.before_step_seconds,
                        "afterStepsSeconds": progress.after_step_seconds,
                    },
                }, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        failing = any(item.result.status in {TestStatus.FAIL, TestStatus.ERROR}
                      for item in self.state.results)
        device_failure = any(
            progress.exit_code == ExitCode.FAILED
            for progress in self.state.devices.values())
        leftovers = any(
            progress.exit_code == ExitCode.LEFTOVERS
            for progress in self.state.devices.values())
        if getattr(self, "_device_isolation", set()):
            self.state.exit_code = ExitCode.DEVICE_OR_PIN
            self.state.verdict = "DEVICE/PIN FAILURE"
        elif self._cancelled.is_set():
            self.state.exit_code = (
                ExitCode.TERMINATED
                if getattr(self, "_cancel_signal", None) == signal.SIGTERM
                else ExitCode.INTERRUPTED
            )
            self.state.verdict = (
                "TERMINATED" if self.state.exit_code == ExitCode.TERMINATED
                else "INTERRUPTED")
        elif self.state.errors or device_failure:
            self.state.exit_code = ExitCode.FAILED
            self.state.verdict = (
                "OPERATIONAL FAILURE" if self.state.errors else "FAILED")
        elif failing:
            self.state.exit_code = ExitCode.FAILED
            self.state.verdict = "FAILED"
        elif leftovers:
            self.state.exit_code = ExitCode.LEFTOVERS
            self.state.verdict = "LEFTOVERS"
        else:
            self.state.exit_code = ExitCode.CLEAN
            self.state.verdict = "CLEAN"
        self.state.phase = "Complete"
        fleet_results = [item.result for item in self.state.results]
        fleet_counts = ExecutionCounts.from_results(
            fleet_results, self.state.expected_executions,
            sum(progress.not_run for progress in self.state.devices.values()))
        counts = {
            TestStatus.PASS.value: fleet_counts.passed,
            TestStatus.FAIL.value: sum(result.status is TestStatus.FAIL
                                       for result in fleet_results),
            TestStatus.ERROR.value: sum(result.status is TestStatus.ERROR
                                        for result in fleet_results),
            TestStatus.SKIP.value: fleet_counts.skipped,
        }
        fleet_junit = self.paths.root / "junit"
        fleet_junit.mkdir(exist_ok=True)
        write_junit(fleet_results, fleet_junit)
        payload = {
            "schemaVersion": 1,
            "runId": self.paths.root.name,
            "startedAt": self.state.started_at,
            "finishedAt": self.state.finished_at,
            "durationSeconds": self.state.finished_at - self.state.started_at,
            "exitCode": int(self.state.exit_code),
            "verdict": self.state.verdict,
            "expectedExecutions": fleet_counts.total,
            "reportedResults": fleet_counts.reported,
            "notRun": fleet_counts.not_run,
            "counts": counts,
            "errors": self.state.errors,
        }
        (self.paths.root / "summary.json").write_text(
            json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        lines = [
            f"{item.result.status.value:9} {item.serial:16} {item.result.duration_seconds:7.3f}s "
            f"{_simple_key(item.result.key)}" for item in self.state.results
        ]
        (self.paths.root / "results.txt").write_text(
            "\n".join(lines) + ("\n" if lines else ""), encoding="utf-8"
        )

    def _print_non_pass_results(self) -> None:
        non_pass = [
            item for item in self.state.results
            if item.result.status in {TestStatus.FAIL, TestStatus.ERROR}
        ]
        for item in non_pass[:20]:
            print(f"{item.result.status.value:9} {item.serial:16} "
                  f"{item.result.duration_seconds:7.3f}s {_simple_key(item.result.key)}",
                  file=self.stream)
        omitted = len(non_pass) - 20
        if omitted > 0:
            print(f"{omitted} additional non-pass result(s) omitted; full results: "
                  f"{self.paths.root / 'results.txt'}", file=self.stream)

    def run(self) -> ExitCode:
        self._write_manifest()
        self.view.start()
        try:
            self._build_once()
            self.state.phase = "Running"
            threads = []
            software_slots = threading.BoundedSemaphore(MAX_CONCURRENT_SOFTWARE_DEVICES)
            for device in self.selection.devices:
                units = self.selection.plan.assignments[device.serial]
                thread = threading.Thread(
                    target=self._run_device_scheduled,
                    args=(device, units, software_slots),
                    name=f"mjpdf-{device.serial}",
                )
                thread.start()
                threads.append(thread)
            for thread in threads:
                thread.join()
        except Exception as exc:
            self.state.errors.append(str(exc))
            for progress in self.state.devices.values():
                if progress.started_at is None:
                    progress.started_at = self.state.started_at
                progress.finished_at = time.time()
                if self._cancelled.is_set():
                    progress.exit_code = (
                        ExitCode.TERMINATED
                        if self._cancel_signal == signal.SIGTERM
                        else ExitCode.INTERRUPTED)
                    progress.verdict = (
                        "TERMINATED"
                        if progress.exit_code == ExitCode.TERMINATED
                        else "INTERRUPTED")
                    progress.step = progress.verdict
                else:
                    progress.exit_code = ExitCode.FAILED
                    progress.verdict = "ERROR"
                    progress.step = "ERROR"
        finally:
            cleanup_generated(self._prepared_fixtures)
            self._finish()
            self.view.stop_and_erase()
        self._print_non_pass_results()
        self.view.print_final()
        return self.state.exit_code
