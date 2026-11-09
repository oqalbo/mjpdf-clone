from __future__ import annotations

import fcntl
import hashlib
import json
import os
import re
import shlex
import signal
import tempfile
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Callable, Iterable, Iterator, cast

from .apk_prep import (prepare_utility_apks, APP_APK, TEST_APK, ApkPreparationError, PreparedApks,
                       build_tool, prepare_apks)
from .fixtures import cleanup_generated, FixtureError, Fixtures, SDCARD
from .instrumentation import (DIRECT_PHASE_MIN_API, InstrumentationParser, RunError, RunFinished, TestFinished,
                               TestStarted, build_direct_command, build_orchestrator_command)
from .model import (CommandSpec, ExitCode, RunPaths, RunState, RunSummary, RunnerConfig,
                     StepRecord, StepState, TestKey, TestResult)
from .phases import PhaseRunner, PhaseScenario
from .pins import PinError, PinManager
from .process import CommandFailure, CommandRunner
from .results import status_from_code, write_junit, write_summary
from .storage import Storage
from .ui import Renderer

TIMING_LOG = re.compile(r"^\s*\d+\.\d+\s+\d+\s+\d+\s+[VDIWEF]\s+MjPdfTestTiming\s*:\s*(.*)$")

ORCHESTRATOR_PKG = "androidx.test.orchestrator"
SERVICES_PKG = "androidx.test.services"
RUNNER_CLASS = "com.gitlab.mudlej.MjPdfReader.suite.MjPdfTestRunner"
FAILURE_BUNDLE_ROOT = f"{SDCARD}/Documents/mjpdf-test-artifacts"
PHASE_BOOTSTRAP_TIMEOUT_SECONDS = 45.0
SYSTEM_ACTIVITY_PACKAGES = {
    "android",
    "com.android.documentsui",
    "com.android.launcher3",
    "com.android.permissioncontroller",
    "com.android.systemui",
    "com.google.android.apps.docs",
    "com.google.android.apps.nexuslauncher",
    "com.google.android.documentsui",
    "com.google.android.permissioncontroller",
}
INTENTIONAL_PRINT_TESTS = {
    (
        "com.gitlab.mudlej.MjPdfReader.suite.actions.PrintActionTest",
        "printingHandsTheFrameworkTheWholeDocument",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.actions.PrintActionTest",
        "backingOutOfThePrintPreviewLeavesNoCopyOfTheDocumentInTheCache",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.actions.PrintActionTest",
        "anUnlockedDocumentPrintsBecauseTheStagedCopyCarriesNoEncryption",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.artifacts.PrintStagingArtifactTest",
        "theStagedPrintDocumentCarriesTheSourcePageCountAndPageSizes",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.artifacts.PrintStagingArtifactTest",
        "theStagedPrintDocumentOfAProtectedFileIsDecryptedAndComplete",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.artifacts.PrintStagingArtifactTest",
        "staleStagingAndShareFilesAreSweptWhileFreshOnesAreKept",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.artifacts.SaveSizeTest",
        "theTwoFullRewritePathsAreComparedOnTheSameDocument",
    ),
}
SHELL_TARGET_TESTS = {
    (
        "com.gitlab.mudlej.MjPdfReader.suite.intake.IntentIntakeTest",
        "aPathContainingADotInAFolderNameStillMatchesTheViewFilter",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.security.ComponentExposureTest",
        "aNonExportedActivityCannotBeStartedFromOutsideTheApp",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.security.ComponentExposureTest",
        "aViewIntentWithNoDataAndNoExtrasFallsBackToThePicker",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.security.ComponentExposureTest",
        "externalIntentsForPrivateNonPdfFilesFailWithoutRendering",
    ),
    (
        "com.gitlab.mudlej.MjPdfReader.suite.security.OnlineDocumentSecurityTest",
        "anHttpUrlArrivingByIntentIsNotDownloadedWithoutTheSameWarning",
    ),
}


def _run_title(config: RunnerConfig) -> str:
    if config.phase_scenario:
        return config.phase_scenario.replace("-", " ").replace("_", " ").title()
    if len(config.packages) == 1:
        area = config.packages[0].rsplit(".", 1)[-1]
        if area == "intake":
            return "Document intake"
        return area.replace("_", " ").title()
    if len(config.classes) == 1:
        class_name = config.classes[0].split("#", 1)[0].rsplit(".", 1)[-1]
        return re.sub(r"Test$", "", class_name) or class_name
    if config.test_file is not None:
        return config.test_file.stem.replace("-", " ").replace("_", " ").title()
    return "Full suite"



class RunnerFailure(RuntimeError):
    def __init__(self, code: ExitCode, message: str):
        super().__init__(message)
        self.code = code


class Runner:
    def __init__(self, config: RunnerConfig, paths: RunPaths, renderer: Renderer,
                 scenario: PhaseScenario | None = None):
        self.config = config
        self.paths = paths
        self.renderer = renderer
        self.state = RunState(time.time(), paths.root, title=_run_title(config))
        planned = [
            "Require device", "Build", "Prepare APKs", "Install APKs",
            "Install orchestrator", "Reset system pickers", "Pin environment", "Clear stale evidence",
            "Snapshot pre-seed",
            "Seed fixtures", "Snapshot post-seed",
        ]
        if config.test_file is not None:
            planned.append("Push test shard")
        planned.extend([
            "Run tests", "Snapshot post-run", "Collect artifacts", "Classify results",
            "Sweep fixtures", "Snapshot post-sweep", "Restore pins", "Check leftovers",
            "Write summary",
        ])
        self.state.steps = [StepRecord(name) for name in planned]
        self.commands = CommandRunner(paths.root / "runner.log", paths.logs,
                                      paths.root / "instrument-raw.txt", config.dry_run)
        self.fixtures = Fixtures(config, self.commands, paths.root, self.warn, self.notice,
                                 lambda: self._interrupted is not None)
        self.storage = Storage(config, self.commands, paths.root, self.fixtures, self.warn, self.notice)
        self.pins = PinManager(config, self.commands, paths.root, self.warn, self.notice)
        self.scenario = scenario
        self.run_errors: list[str] = []
        self.summary: RunSummary | None = None
        self.profile = "unknown"
        self.bundle_count = 0
        self.scenario_skipped = False
        self.scenario_skip_reason: str | None = None
        self.scenario_abort_reason: str | None = None
        self.scenario_actions: list[dict[str, object]] = []
        self._preseed = False
        self._seed_started = False
        self._suite_started = False
        self._device_test_file: str | None = None
        self._phase_freezer_supported: bool | None = None
        self._interrupted: int | None = None
        self._interrupted_at: float | None = None
        self._contamination_observed = False

        self._cleanup_started = False
        self._all_files_grant_api: int | None = None
        self._owned_uids: set[int] = set()
        self._lease_fd: int | None = None
        for artifact in ("device-isolation.txt", "instrument-command.txt", "instrument-raw.txt",
                         "seed-warnings.txt", "mtime-probe.txt", "kills.txt",
                         "skipped-scenarios.txt"):
            (paths.root / artifact).touch()
        (paths.root / "fixture-plan.json").write_text(json.dumps({
            "requirements": sorted(config.required_fixtures) if config.required_fixtures is not None else None,
            "resolution": config.fixture_resolution, "skipGenerated": config.skip_generated,
            "withPerformanceLibrary": config.with_perf_library,
            "preparedFixtures": str(config.prepared_fixtures) if config.prepared_fixtures else None,
        }, indent=2) + "\n")
        self.commands.record(f"run start id={config.run_id} serial={config.serial} dryRun={config.dry_run}")

    def interrupt(self, signum: int) -> None:
        # A Python signal handler may interrupt this thread at any bytecode.
        # Only publish state and use CommandRunner's lock-free termination path.
        if self._interrupted is None:
            self._interrupted_at = time.time()
            self._interrupted = signum

        self.commands.terminate_active(signal.SIGTERM)

    def warn(self, message: str) -> None:
        self.state.warnings.append(message)
        self.commands.record(f"warning: {message}")
        self.renderer.notice(message, warning=True)

    def notice(self, message: str) -> None:
        self.state.notices.append(message)
        self.commands.record(f"notice: {message}")
        self.renderer.notice(message)

    @contextmanager
    def step(self, name: str) -> Iterator[StepRecord]:
        if self._interrupted is not None and not self._cleanup_started:
            raise RunnerFailure(ExitCode.FAILED, f"interrupted by signal {self._interrupted}")
        record = next((step for step in self.state.steps
                       if step.name == name and step.state is StepState.PENDING), None)
        if record is None:
            record = StepRecord(name)
            self.state.steps.append(record)
        record.state = StepState.RUNNING
        record.started_at = time.time()
        self.renderer.update(self.state)
        try:
            yield record
            if self._interrupted is not None and not self._cleanup_started:
                raise RunnerFailure(ExitCode.FAILED, f"interrupted by signal {self._interrupted}")
        except Exception as exc:
            record.state = StepState.FAILED
            record.detail = str(exc)
            raise
        else:
            if record.state is StepState.RUNNING:
                record.state = StepState.COMPLETE
        finally:
            record.finished_at = time.time()
            self.renderer.update(self.state)

    def _adb(self, *args: str) -> tuple[str, ...]:
        return (self.config.adb, "-s", self.config.serial, *args)

    def _device(self, name: str, *args: str, mutate: bool = True,
                check: bool = True, timeout_seconds: float | None = None) -> str:
        return self.commands.run(CommandSpec(self._adb(*args), name, mutate=mutate,
                                             timeout_seconds=timeout_seconds),
                                  check=check).output.strip().replace("\r", "")

    def _require_device(self) -> None:
        if self.config.dry_run:
            return
        try:
            state = self._device("device-state", "get-state", mutate=False, check=False)
        except OSError as exc:
            raise RunnerFailure(ExitCode.DEVICE_OR_PIN,
                                f"could not run {self.config.adb}: {exc}") from exc
        if state != "device":
            raise RunnerFailure(ExitCode.DEVICE_OR_PIN,
                                f"{self.config.serial} is not attached (adb get-state said {state or 'nothing'!r})")
        self._assert_no_active_instrumentation("before batch")

    @staticmethod
    def _instrumentation_packages(output: str) -> set[str]:
        return {
            match.group(1)
            for line in output.splitlines()
            if "ActiveInstrumentation{" in line
            for match in [re.search(
                r"\b([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_$]+)+)/[A-Za-z0-9_.$]+",
                line,
            )]
            if match is not None
        }
    @staticmethod
    def _device_command_error(output: str) -> bool:
        return re.search(
            r"(?im)^\s*(?:unknown command\b|bad activity command\b|"
            r"error:\s*(?:unknown|unsupported)\b|(?:cmd:\s*)?can't find service\b)",
            output,
        ) is not None


    def _isolation_device(self, name: str, *args: str) -> str:
        try:
            completed = self.commands.run(
                CommandSpec(self._adb(*args), name, mutate=False), check=False)
        except OSError as exc:
            message = f"device isolation failure: evidence command {name} could not run: {exc}"
            raise self._record_isolation(message, [str(exc)]) from exc
        output = completed.output.strip().replace("\r", "")
        if completed.returncode and self._interrupted is not None:
            code = (
                ExitCode.INTERRUPTED
                if self._interrupted == signal.SIGINT else ExitCode.TERMINATED
            )
            raise RunnerFailure(code, f"interrupted by signal {self._interrupted}")

        if completed.returncode or self._device_command_error(output):
            detail = (
                f"exited {completed.returncode}"
                if completed.returncode
                else "reported an unsupported command or service error"
            )
            message = f"device isolation failure: evidence command {name} {detail}"
            raise self._record_isolation(message, [output or "(no output)"])
        return output


    def _active_instrumentation_packages(self, name: str) -> set[str]:
        output = self._isolation_device(
            name, "shell", "dumpsys", "activity", "processes")
        return self._instrumentation_packages(output)


    def _record_isolation(self, message: str, evidence: list[str]) -> RunnerFailure:
        path = self.paths.root / "device-isolation.txt"
        with path.open("a", encoding="utf-8") as fh:
            fh.write(message + "\n")
            for line in evidence:
                fh.write(line.strip() + "\n")
        self.commands.record(message)
        return RunnerFailure(ExitCode.DEVICE_OR_PIN, message)

    def _acquire_device_lease(self) -> None:
        if self.config.dry_run:
            return
        digest = hashlib.sha256(self.config.serial.encode("utf-8")).hexdigest()[:16]
        path = Path(tempfile.gettempdir()) / f"mjpdf-runner-device-{digest}.lock"
        fd = os.open(path, os.O_CREAT | os.O_RDWR, 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            owner = os.read(fd, 4096).decode("utf-8", errors="replace").strip()
            os.close(fd)
            message = (
                f"device isolation failure: {self.config.serial} is leased by another runner"
            )
            raise self._record_isolation(message, [owner or "(owner unavailable)"]) from exc
        os.ftruncate(fd, 0)
        os.write(fd, f"pid={os.getpid()} run={self.config.run_id}\n".encode("utf-8"))
        self._lease_fd = fd

    def _release_device_lease(self) -> None:
        fd = self._lease_fd
        self._lease_fd = None
        if fd is not None:
            fcntl.flock(fd, fcntl.LOCK_UN)
            os.close(fd)

    def _assert_no_active_instrumentation(self, point: str) -> None:
        packages = self._active_instrumentation_packages(
            f"device-isolation-{point.replace(' ', '-')}")
        if packages:
            owners = ", ".join(sorted(packages))
            message = f"device isolation failure: active instrumentation {point}: {owners}"
            self._contamination_observed = True
            raise self._record_isolation(message, [f"{point}: {owners}"])

    def _load_owned_uids(self) -> None:
        owned: set[int] = set()
        for package in (self.config.package, self.config.test_package):
            output = self._isolation_device(
                f"device-isolation-uid-{package}", "shell", "cmd", "package",
                "list", "packages", "-U", package)
            match = next((
                re.search(r"\buid:(\d+)\b", line)
                for line in output.splitlines()
                if line.startswith(f"package:{package} ")
            ), None)
            if match is None:
                message = f"device isolation failure: could not resolve uid for {package}"
                raise self._record_isolation(message, [output or "(no output)"])
            owned.add(int(match.group(1)))
        self._owned_uids = owned

    def _expected_instrumentation_package(self, package: str) -> bool:
        return package in {
            self.config.package,
            self.config.test_package,
            ORCHESTRATOR_PKG,
            SERVICES_PKG,
        }

    def _allowed_activity_package(self, package: str) -> bool:
        return self._expected_instrumentation_package(package) or package in SYSTEM_ACTIVITY_PACKAGES

    def _expected_target_starts(
            self, test_intervals: list[tuple[TestKey, float, float | None]],
            phase: int | None, args: list[str] | None = None) -> int:
        if phase is not None:
            return max(1, len(test_intervals))
        selector = None
        arguments = iter(self._instrument_args() if args is None else args)
        for argument in arguments:
            if argument == "-e":
                key = next(arguments, "")
                value = next(arguments, "")
                if key == "class":
                    selector = value
        if selector is not None and re.fullmatch(r"[\w.?]+#\w+", selector, flags=re.ASCII):
            return 1
        return max(1, len(test_intervals) + 1)

    @staticmethod
    def _event_time(line: str) -> float | None:
        match = re.match(r"\s*(\d+\.\d+)", line)
        return float(match.group(1)) if match is not None else None

    @staticmethod
    def _activity_package(line: str) -> str | None:
        for pattern in (
            r"\bcmp=([A-Za-z][A-Za-z0-9_.$]+)(?:/|\s|\})",
            r"\bpkg=([A-Za-z][A-Za-z0-9_.$]+)(?:\s|\})",
            r"\bA=([A-Za-z][A-Za-z0-9_.$]+)(?:\s|\})",
            r"(?<![/:])\b([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_.$]+)+)/[A-Za-z0-9_.$]+",
        ):
            match = re.search(pattern, line)
            if match is not None:
                return match.group(1)
        return None

    @staticmethod
    def _ui_automation_owner_collision(text: str) -> bool:
        return (
            "IllegalStateException" in text
            and "UiAutomationService" in text
            and "already registered!" in text
        )
    def _execution_isolation(
        self,
        test_intervals: list[tuple[TestKey, float, float | None]] | None = None,
        captured_evidence: str | Iterable[str] = "",
        allow_terminal_unmatched: bool = False,
        expected_target_starts: int | None = None,
        allow_incomplete_evidence: bool = False,
        known_contamination: bool = False,
        allow_shell_target_start: bool = False,
        allow_owned_active_instrumentation: bool = False,
    ) -> tuple[set[str], list[str], float | None]:
        complete_evidence = not isinstance(captured_evidence, str)
        evidence_lines = (
            captured_evidence.splitlines()
            if isinstance(captured_evidence, str) else captured_evidence
        )
        active: set[str] = set()
        packages: set[str] = set()
        lines: list[str] = []
        earliest_boundary: float | None = None
        intentional_external: set[str] = set()
        owned_instrumentation_active = False
        instrumentation_boundaries: set[str] = set()
        intervals = test_intervals or []
        native_intervals: list[tuple[TestKey, float, float | None]] = []
        if intervals:
            evidence_lines = tuple(evidence_lines)
            own_keys = {interval[0] for interval in intervals}
            for line in evidence_lines:
                lifecycle = re.search(
                    r"\bTestRunner\s*:\s*(started|finished):\s*([^()]+)\(([^()]+)\)",
                    line,
                )
                event_time = self._event_time(line)
                if lifecycle is None or event_time is None:
                    continue
                key = TestKey(lifecycle.group(3), lifecycle.group(2).strip())
                if key not in own_keys:
                    continue
                if lifecycle.group(1) == "started":
                    native_intervals.append((key, event_time, None))
                else:
                    for index in range(len(native_intervals) - 1, -1, -1):
                        candidate, started_at, ended_at = native_intervals[index]
                        if candidate == key and ended_at is None:
                            native_intervals[index] = (key, started_at, event_time)
                            break
            native_intervals = [
                interval for interval in native_intervals
                if interval[2] is not None
                or any(host[0] == interval[0] and host[2] is None for host in intervals)
            ]
        ownership_intervals = native_intervals or intervals
        expected_target_markers: list[tuple[str, str]] = []
        unbounded_cardinality = False

        def interval_at(event_time: float | None) -> tuple[TestKey, float, float | None] | None:
            if event_time is None:
                return None
            return next((
                interval for interval in ownership_intervals
                if interval[1] <= event_time <= (
                    interval[2] if interval[2] is not None else float("inf"))
            ), None)

        def record(package: str, line: str) -> None:
            nonlocal earliest_boundary
            packages.add(package)
            if len(lines) < 200 and line not in lines:
                lines.append(line)
            event_time = self._event_time(line)
            if event_time is not None and native_intervals:
                owner = interval_at(event_time)
                native_anchor = owner or native_intervals[0]
                host_anchor = next(interval for interval in intervals if interval[0] == native_anchor[0])
                event_time = (
                    host_anchor[1] if owner is not None
                    else event_time + host_anchor[1] - native_anchor[1]
                )
            if event_time is not None:
                earliest_boundary = (
                    event_time
                    if earliest_boundary is None else min(earliest_boundary, event_time)
                )

        for line in evidence_lines:
            if TIMING_LOG.match(line):
                continue
            instrumentation = re.search(
                r"Force stopping ([A-Za-z][A-Za-z0-9_.$]+).*?:\s*start instr\b",
                line,
            )
            if instrumentation is not None:
                package = instrumentation.group(1)
                if package in {self.config.package, self.config.test_package}:
                    intentional_external.clear()
                    owned_instrumentation_active = True
                    expected_target_markers.append((package, line))
                elif not self._expected_instrumentation_package(package):
                    instrumentation_boundaries.add(package)
                    record(package, line)
                continue
            lifecycle_end = re.search(
                r"Force stopping ([A-Za-z][A-Za-z0-9_.$]+).*?:\s*"
                r"(?:finished inst|clear data|clearApplicationUserData)\b",
                line,
            )
            if (lifecycle_end is not None
                    and lifecycle_end.group(1) in {
                        self.config.package, self.config.test_package,
                    }):
                intentional_external.clear()
                owned_instrumentation_active = False
                continue
            if "ActivityTaskManager" in line and " START " in line:
                package = self._activity_package(line)
                if package is None:
                    continue
                direct = re.search(r"\bfrom uid (\d+)\b", line)
                real = re.search(r"\brealCallingUid=(\d+)\b", line)
                caller = real or direct
                caller_uid = int(caller.group(1)) if caller is not None else None
                caller_package_match = re.search(
                    r"\bfrom uid \d+ \(([A-Za-z][A-Za-z0-9_.$]+)\)", line)
                caller_package = (
                    caller_package_match.group(1)
                    if caller_package_match is not None else None
                )
                owner = interval_at(self._event_time(line))
                collection_epochs = (
                    1
                    if expected_target_starts is not None
                    and expected_target_starts > len(intervals)
                    else 0
                )
                epoch_index = len(expected_target_markers) - collection_epochs - 1
                epoch_owners = tuple(
                    intervals[index]
                    for index in (epoch_index, epoch_index + 1)
                    if 0 <= index < len(intervals)
                )
                shell_owners = (owner, *epoch_owners)
                if package in {self.config.package, self.config.test_package}:
                    intentional_shell_target = (
                        caller_uid in {0, 2000}
                        and (
                            allow_shell_target_start and owner is not None
                            or any(
                                owned_instrumentation_active
                                and
                                candidate is not None
                                and (candidate[0].class_name, candidate[0].method)
                                in SHELL_TARGET_TESTS
                                for candidate in shell_owners
                            )
                        )
                    )
                    if caller_uid not in self._owned_uids and not intentional_shell_target:
                        record(package, line)
                    continue
                if self._allowed_activity_package(package):
                    continue
                mediated_settings = (
                    caller_uid in {0, 1000, 2000}
                    and package == "com.android.settings"
                    and "act=android.settings.SETTINGS" in line
                )
                mediated_print = (
                    caller_uid == 1000
                    and package == "com.android.printspooler"
                    and "act=android.print.PRINT_DIALOG" in line
                    and owner is not None
                    and (owner[0].class_name, owner[0].method) in INTENTIONAL_PRINT_TESTS
                )
                external_disruption = (
                    owner is not None
                    and owner[0].class_name.endswith(".ExternalDisruptionTest")
                    and owner[0].method == "anInterruptingActivityReturnsTheReaderExactlyWhereItWas"
                )
                continued = (
                    owned_instrumentation_active
                    and package in intentional_external
                    and (
                        caller_package == package
                        or caller_uid is not None and caller_uid >= 10_000
                        or caller_uid == 1000 and package == "com.android.settings"
                    )
                )
                if (owned_instrumentation_active
                        and (caller_uid in self._owned_uids
                             or mediated_settings and external_disruption
                             or mediated_print)):
                    intentional_external.add(package)
                elif not continued:
                    record(package, line)
                continue
            lowered = line.lower()
            resumed_event = "wm_set_resumed_activity" in line
            task_front_event = "movetasktofront" in lowered or "moved to front" in lowered
            if not resumed_event and not task_front_event:
                continue
            package = self._activity_package(line)
            if package in {self.config.package, self.config.test_package}:
                intentional_external.clear()
                continue
            if resumed_event and not task_front_event:
                continue
            if not owned_instrumentation_active:
                continue
            if (package is not None
                    and not self._allowed_activity_package(package)
                    and not (
                        owned_instrumentation_active
                        and package in intentional_external
                    )):
                record(package, line)
        if (complete_evidence and expected_target_starts is not None
                and not packages and not known_contamination):
            observed = len(expected_target_markers)
            if observed < expected_target_starts:
                message = (
                    "activity observer evidence incomplete: expected at least "
                    f"{expected_target_starts} owned instrumentation starts, observed {observed}"
                )
                if allow_incomplete_evidence and (observed > 0 or not intervals):
                    self.warn(message)
                else:
                    self.commands.record(f"operational failure: {message}")
                    raise RunnerFailure(ExitCode.FAILED, message)
            last_marker_time = (
                self._event_time(expected_target_markers[-1][1])
                if expected_target_markers else None
            )
            last_test_start = max(
                (interval[1] for interval in intervals), default=float("-inf"))
            causal_terminal = (
                last_marker_time is not None
                and last_marker_time > last_test_start
            )
            terminal_allowance = (
                allow_terminal_unmatched
                and observed == expected_target_starts + 1
                and causal_terminal
            )
            if observed > expected_target_starts and not terminal_allowance:
                unbounded_cardinality = True
                package, line = expected_target_markers[expected_target_starts]
                instrumentation_boundaries.add(package)
                record(package, line)
        if self._interrupted is None and not known_contamination:
            try:
                active = self._active_instrumentation_packages(
                    "device-isolation-execution-active")
            except RunnerFailure:
                if not packages:
                    raise

        if allow_owned_active_instrumentation:
            active.difference_update({self.config.package, self.config.test_package})

        unbounded_active = active.difference(instrumentation_boundaries)
        if active:
            packages.update(active)
            if len(lines) < 200:
                lines.append(
                    "active instrumentation after command: " + ", ".join(sorted(active)))
        boundary = (
            None if unbounded_active or unbounded_cardinality else earliest_boundary)
        return packages, lines, boundary

    def _build(self) -> None:
        try:
            self.fixtures.check_asset_drift()
        except FixtureError as exc:
            raise RunnerFailure(ExitCode.FAILED, str(exc)) from exc
        if self.config.skip_build:
            self.notice("skipping build as requested")
            return
        spec = CommandSpec((str(self.config.repo_root / "gradlew"), "-p", str(self.config.repo_root),
                            ":app:assembleDebug", ":app:assembleDebugAndroidTest"),
                           "gradle-build", cwd=self.config.repo_root)
        try:
            self.commands.run(spec)
        except CommandFailure as exc:
            raise RunnerFailure(ExitCode.FAILED, "Gradle build failed") from exc

    def _prepare_apks(self) -> PreparedApks:
        app = self.config.app_apk
        test = self.config.test_apk
        if (app is None) != (test is None):
            raise RunnerFailure(
                ExitCode.FAILED,
                "app_apk and test_apk must be supplied together",
            )
        if app is None or test is None:
            try:
                return prepare_apks(
                    self.config.repo_root,
                    self.paths.root / "apks",
                    self.commands,
                    dry_run=self.config.dry_run,
                )
            except ApkPreparationError as exc:
                raise RunnerFailure(ExitCode.FAILED, str(exc)) from exc

        app = app if app.is_absolute() else self.config.repo_root / app
        test = test if test.is_absolute() else self.config.repo_root / test
        if not self.config.dry_run:
            missing = [str(path) for path in (app, test) if not path.is_file()]
            if missing:
                raise RunnerFailure(
                    ExitCode.FAILED,
                    "missing prepared APK snapshot(s): " + ", ".join(missing),
                )
        return PreparedApks(app, test, app.parent / "manifest.json")

    def _apk_package(self, apk: Path, fallback: str) -> str:
        try:
            aapt2 = build_tool("aapt2", self.config.repo_root)
        except ApkPreparationError:
            return fallback
        completed = self.commands.run(CommandSpec((str(aapt2), "dump", "packagename", str(apk)),
                                                  "aapt-package", mutate=False), check=False)
        package = completed.output.strip().splitlines()
        if package:
            return package[0].strip()
        return fallback

    def _install_apk(self, apk: Path, package: str) -> None:
        if not apk.is_file() and not self.config.dry_run:
            raise RunnerFailure(ExitCode.FAILED, f"missing prepared APK snapshot: {apk}")
        observer = None if self.config.dry_run else self.commands.start_observer(CommandSpec(
            self._adb("logcat", "-b", "all", "-v", "threadtime", "-T", "1",
                      "PackageManager:I", "PackageInstaller:I", "PackageInstallerSession:I",
                      "installd:I", "dex2oat:I", "adbd:I", "*:S"),
            f"install-system-{apk.stem}", mutate=False,
        ))
        try:
            self._install_prepared_apk(apk, package)
        finally:
            if observer is not None:
                self.commands.stop_observer(observer)

    def _install_prepared_apk(self, apk: Path, package: str) -> None:
        api_text = self._device("install-api", "shell", "getprop", "ro.build.version.sdk",
                                mutate=False, check=False)
        try:
            api = int(api_text)
        except ValueError:
            api = None
        transport = ("--no-streaming",) if api is not None and api <= 28 else ()
        install = ("install", "-r", "-t", "-g", *transport, str(apk))
        completed = self.commands.run(CommandSpec(self._adb(*install),
                                                   f"install-{apk.name}"), check=False)
        if completed.returncode:
            if ("INSTALL_FAILED_UPDATE_INCOMPATIBLE" not in completed.output
                    and "signatures do not match" not in completed.output):
                raise RunnerFailure(ExitCode.FAILED, f"adb install of {apk} failed")
            installed_package = self._apk_package(apk, package)
            self.warn(f"{installed_package} has an incompatible signature; reinstalling from scratch")
            self._device(f"uninstall-{installed_package}", "uninstall", installed_package, check=False)
            try:
                self._device(f"reinstall-{installed_package}", *install)
            except CommandFailure as exc:
                raise RunnerFailure(ExitCode.FAILED, f"adb reinstall of {apk} failed: {exc}") from exc
        if api is not None and api >= 30:
            self._all_files_grant_api = api
            try:
                self._device("grant-all-files", "shell", "appops", "set", "--uid", self.config.package,
                             "MANAGE_EXTERNAL_STORAGE", "allow")
            except CommandFailure as exc:
                raise RunnerFailure(ExitCode.FAILED, "could not grant MANAGE_EXTERNAL_STORAGE") from exc

    def _reset_system_pickers(self) -> None:
        for package in ("com.google.android.documentsui", "com.android.documentsui"):
            installed = self._device(
                f"system-picker-path-{package}", "shell", "pm", "path", package,
                mutate=False, check=False)
            if any(line.startswith("package:") for line in installed.splitlines()):
                self._device(f"system-picker-stop-{package}", "shell", "am", "force-stop", package)

    def _install_orchestrator(self) -> None:
        apks = self.config.utility_apks
        if not apks:
            apks = prepare_utility_apks(self.config.repo_root, self.config.runner_dir,
                                        self.paths.root / "utility-apks", self.commands,
                                        self.config.dry_run)
        if self.config.dry_run:
            return
        for package in (ORCHESTRATOR_PKG, SERVICES_PKG):
            self._device(f"uninstall-{package}", "uninstall", package, check=False)
        api_text = self._device("orchestrator-api", "shell", "getprop", "ro.build.version.sdk",
                                mutate=False, check=False)
        try:
            force_queryable = int(api_text) >= 30
        except ValueError:
            force_queryable = False
        for index, apk in enumerate(apks):
            args = ["install", "-r", "-g"]
            if force_queryable:
                args.append("--force-queryable")
            args.append(str(apk))
            try:
                self._device(f"install-test-util-{index}", *args)
            except CommandFailure as exc:
                raise RunnerFailure(ExitCode.FAILED, f"adb install of {apk} failed") from exc
        installed = self._device("installed-packages", "shell", "pm", "list", "packages",
                                 mutate=False, check=False).splitlines()
        installed_set = {line.removeprefix("package:") for line in installed}
        for package in (ORCHESTRATOR_PKG, SERVICES_PKG):
            if package not in installed_set:
                raise RunnerFailure(ExitCode.FAILED, f"{package} is not installed after install step")

    def _instrument_args(self) -> list[str]:
        args: list[str] = []
        for key, values in (("class", self.config.classes), ("package", self.config.packages),
                            ("annotation", self.config.annotations),
                            ("notAnnotation", self.config.not_annotations)):
            if values:
                args += ["-e", key, ",".join(values)]
        if (self.config.clear_package_data
                and self._passthrough_argument("storageAccess") != "denied"):
            args += ["-e", "clearPackageData", "true"]
        if self.config.trace_timing:
            args += ["-e", "mjpdfTraceTiming", "true"]
        args += list(self.config.passthrough)
        if self._device_test_file:
            args += ["-e", "testFile", self._device_test_file]
        return args

    def _passthrough_argument(self, name: str) -> str | None:
        arguments = list(self.config.passthrough)
        for index in range(len(arguments) - 2):
            if arguments[index:index + 2] == ["-e", name]:
                return arguments[index + 2]
        return None

    def _phase_instrument_args(self) -> list[str]:
        args: list[str] = []
        for key, values in (("package", self.config.packages),
                            ("annotation", self.config.annotations),
                            ("notAnnotation", self.config.not_annotations)):
            if values:
                args += ["-e", key, ",".join(values)]
        if self.config.trace_timing:
            args += ["-e", "mjpdfTraceTiming", "true"]
        args += list(self.config.passthrough)
        if self._device_test_file:
            args += ["-e", "testFile", self._device_test_file]
        return args


    def _handle_event(self, event: object) -> TestResult | None:
        if isinstance(event, TestStarted):
            self.state.current_test = event.key
            self.state.current_test_started_at = time.time()
            if event.total is not None:
                self.state.total_tests = event.total
            self.renderer.update(self.state)
        elif isinstance(event, TestFinished):
            result = TestResult(event.key, event.phase, event.raw_code,
                                status_from_code(event.raw_code), event.duration, event.stack)
            self.state.tests.append(result)
            self.state.current_test = None
            self.state.current_test_started_at = None
            self.renderer.finish(result)
            self.renderer.update(self.state)
            return result
        elif isinstance(event, RunError):
            self.run_errors.append(event.message)
            self.warn(event.message)
        elif isinstance(event, RunFinished):
            self.commands.record(f"instrumentation final code {event.code}")
        return None

    def _instrument_once(
        self,
        args: list[str],
        phase: int | None = None,
        on_test_started: Callable[[], None] | None = None,
        orchestrated: bool = True,
    ) -> list[TestResult]:
        services = "$(pm path androidx.test.services | sed s/package://)"
        if orchestrated and not self.config.dry_run:
            paths = self._device("services-path", "shell", "pm", "path", SERVICES_PKG,
                                 mutate=False, check=False).splitlines()
            services = next((line.removeprefix("package:") for line in paths if line.startswith("package:")), "")
            if not services:
                raise RunnerFailure(ExitCode.FAILED, f"{SERVICES_PKG} is not installed")
        command = (
            build_orchestrator_command(
                services, self.config.test_package, RUNNER_CLASS,
                SERVICES_PKG, ORCHESTRATOR_PKG, args)
            if orchestrated else
            build_direct_command(self.config.test_package, RUNNER_CLASS, args)
        )
        with (self.paths.root / "instrument-command.txt").open("a", encoding="utf-8") as fh:
            fh.write(shlex.join(self._adb("shell", command)) + "\n")
        parser = InstrumentationParser(phase)
        started = time.monotonic()
        test_intervals: list[tuple[TestKey, float, float | None]] = []
        finished: list[tuple[TestResult, float]] = []
        ownership_collisions: list[tuple[float, str]] = []
        pending_start_action = on_test_started

        def handle_line(line: str) -> None:
            nonlocal pending_start_action
            for event in parser.feed(line, time.monotonic()):
                if isinstance(event, TestStarted):
                    test_intervals.append((event.key, time.time(), None))
                    if pending_start_action is not None:
                        action = pending_start_action
                        pending_start_action = None
                        action()
                if isinstance(event, TestFinished):
                    ended_at = time.time()
                    if self._ui_automation_owner_collision(event.stack):
                        ownership_collisions.append((ended_at, event.stack))
                    for index in range(len(test_intervals) - 1, -1, -1):
                        key, began_at, interval_end = test_intervals[index]
                        if key == event.key and interval_end is None:
                            test_intervals[index] = (key, began_at, ended_at)
                            break
                    result = self._handle_event(event)
                    assert result is not None
                    finished.append((result, ended_at))

                elif (isinstance(event, RunError)
                      and self._ui_automation_owner_collision(event.message)):
                    ownership_collisions.append((time.time(), event.message))
                else:
                    self._handle_event(event)
        def reconcile(boundary: float | None, contaminated: bool) -> None:
            invalid = {
                id(result)
                for result, ended_at in finished
                if (
                    contaminated and (boundary is None or ended_at >= boundary)
                    or self._interrupted_at is not None
                    and ended_at >= self._interrupted_at
                )
            }
            if invalid:
                self.state.tests[:] = [
                    result for result in self.state.tests if id(result) not in invalid
                ]
            self.renderer.reconcile(tuple(self.state.tests))
            if invalid:
                self.renderer.update(self.state)


        observer = None
        observer_result = None
        if not self.config.dry_run:
            observer_spec = CommandSpec(
                self._adb(
                    "logcat", "-b", "all", "-v", "epoch",
                    "ActivityManager:I", "ActivityTaskManager:I",
                    "wm_set_resumed_activity:I",
                    "TestRunner:I",
                    "AndroidRuntime:E", "libc:F", "DEBUG:F",
                    "AndroidTestOrchestrator:E", "AndroidJUnitRunner:E", "ShellExecutorImpl:E",
                    *(("MjPdfTestTiming:I",) if self.config.trace_timing else ()), "*:S",
                ),
                f"device-isolation-observer-{phase or 0}",
                mutate=False,
            )
            marker = (
                f"mjpdf-observer-ready-{self.config.run_id}-{phase or 0}-{time.time_ns()}"
            )
            try:
                self._device(
                    f"device-isolation-observer-ready-{phase or 0}",
                    "shell", "log", "-p", "i", "-t", "ActivityManager", marker,
                    mutate=False)
                observer = self.commands.start_observer(observer_spec)
                self.commands.wait_observer(observer, marker)
            except (CommandFailure, OSError) as exc:
                captured = exc.completed.output if isinstance(exc, CommandFailure) else str(exc)
                if observer is not None:
                    stopped = self.commands.stop_observer(observer)
                    observer = None
                    captured = "\n".join(
                        part for part in (captured, stopped.output) if part)
                message = "activity observer readiness failed"
                self.commands.record(f"operational failure: {message}")
                raise RunnerFailure(ExitCode.FAILED, message) from exc
        try:
            completed = self.commands.run(CommandSpec(self._adb("shell", command),
                                                       f"instrument-{phase or 0}", instrument=True),
                                          on_line=handle_line, check=False)
        finally:
            if observer is not None:
                try:
                    observer_result = self.commands.stop_observer(observer)
                except OSError as exc:
                    if ownership_collisions:
                        collision_boundary = min(item[0] for item in ownership_collisions)
                        reconcile(collision_boundary, True)
                        message = (
                            "device isolation failure: foreign instrumentation/foreground takeover "
                            "during execution: foreign UiAutomation owner"
                        )
                        self._contamination_observed = True
                        raise self._record_isolation(
                            message,
                            [item[1][:8192] for item in ownership_collisions],
                        ) from exc
                    message = "activity observer transport failed during teardown"
                    self.commands.record(f"operational failure: {message}: {exc}")
                    raise RunnerFailure(ExitCode.FAILED, message) from exc
        if not self.config.dry_run:
            assert observer_result is not None
            observer_evidence: str | Iterable[str] = observer_result.output
            observer_reader = getattr(self.commands, "observer_lines", None)
            if callable(observer_reader) and observer is not None:
                observer_evidence = cast(Iterable[str], observer_reader(observer))
            try:
                packages, evidence, boundary = self._execution_isolation(
                    test_intervals,
                    observer_evidence,
                    allow_terminal_unmatched=(
                        completed.returncode != 0 or not parser.saw_run_finished),
                    expected_target_starts=(
                        self._expected_target_starts(test_intervals, phase, args)
                        if orchestrated else None
                    ),
                    known_contamination=bool(ownership_collisions),
                    allow_incomplete_evidence=(
                        completed.returncode == 0 and parser.saw_run_finished
                    ),
                    allow_shell_target_start=on_test_started is not None,
                    allow_owned_active_instrumentation=not orchestrated,
                )

            except RunnerFailure:
                raise



            if ownership_collisions:
                collision_boundary = min(item[0] for item in ownership_collisions)
                boundary = (
                    min(boundary, collision_boundary)
                    if boundary is not None else collision_boundary
                )
                packages.add("foreign UiAutomation owner")
                for _ended_at, collision in ownership_collisions:
                    if len(evidence) >= 200:
                        break
                    evidence.append(collision[:8192])
            if not packages:
                unfinished_reason = (
                    "direct phase instrumentation exceeded its 150-second watchdog"
                    if not orchestrated and completed.returncode == 124 else
                    "test started and never reported a result, which means the process died while it was running"
                )
                for event in parser.finish(time.monotonic(), unfinished_reason):
                    result = self._handle_event(event)
                    if result is not None:
                        finished.append((result, time.time()))
            reconcile(boundary, bool(packages))
            if packages:
                self._contamination_observed = True
                self.state.current_test = None
                self.state.current_test_started_at = None
                self.renderer.update(self.state)
                owners = ", ".join(sorted(packages))
                message = (
                    "device isolation failure: foreign instrumentation/foreground takeover "
                    f"during execution: {owners}"
                )
                raise self._record_isolation(message, evidence)
            if observer_result.returncode:
                message = (
                    "activity observer transport exited "
                    f"{observer_result.returncode} before instrumentation completed"
                )
                self.commands.record(f"operational failure: {message}")
                raise RunnerFailure(ExitCode.FAILED, message)
            if completed.returncode and self._interrupted is None:
                self.run_errors.append(f"instrumentation command exited {completed.returncode}")
        elapsed = time.monotonic() - started
        self.commands.record(f"instrumentation elapsed {elapsed:.3f}s")
        if self._interrupted is not None:
            code = (
                ExitCode.INTERRUPTED
                if self._interrupted == signal.SIGINT else ExitCode.TERMINATED
            )
            raise RunnerFailure(code, f"interrupted by signal {self._interrupted}")
        retained = {id(result) for result in self.state.tests}
        return [result for result, _ended_at in finished if id(result) in retained]

    def _push_shard(self) -> None:
        if self.config.test_file is None:
            return
        self._device_test_file = f"/data/local/tmp/mjpdf-tests-{self.config.run_id}.txt"
        self._device("push-test-file", "push", str(self.config.test_file), self._device_test_file)

    def _prepare_phase_process(self) -> str | None:
        if self.config.dry_run:
            self.commands.record("dry-run: bootstrap target process for direct phase instrumentation")
            return
        component = f"{self.config.package}/.ui.reader.MainActivity"
        self._device(
            "phase-process-bootstrap",
            "shell", "am", "start", "-f", "0x58800000",
            "-n", component,
            "--ez", "mjpdf.debug.bootstrap_process", "true",
            timeout_seconds=PHASE_BOOTSTRAP_TIMEOUT_SECONDS,
        )
        deadline = time.monotonic() + 5.0
        while time.monotonic() < deadline:
            pid = self._device(
                "phase-process-bootstrap-pid", "shell", "pidof", self.config.package,
                mutate=False, check=False,
            ).strip()
            if pid.isdecimal():
                return pid
            time.sleep(0.05)
        raise RunnerFailure(ExitCode.FAILED, "debug process bootstrap did not leave the target process alive")

    @contextmanager
    def _phase_process_unfrozen(self, pid: str | None) -> Iterator[None]:
        if self.config.dry_run or pid is None:
            yield
            return
        if self._phase_freezer_supported is None:
            help_text = self._device(
                "phase-freezer-capabilities", "shell", "am", "help",
                mutate=False, check=False, timeout_seconds=10.0,
            )
            self._phase_freezer_supported = "unfreeze [--sticky] <PROCESS>" in help_text
        if not self._phase_freezer_supported:
            yield
            return
        try:
            self._device(
                "phase-freezer-hold", "shell", "am", "unfreeze", "--sticky", pid,
                timeout_seconds=10.0,
            )
            yield
        finally:
            current = self._device(
                "phase-freezer-current-pid", "shell", "pidof", self.config.package,
                mutate=False, check=False, timeout_seconds=10.0,
            )
            if pid in current.split():
                self._device(
                    "phase-freezer-release", "shell", "am", "unfreeze", pid,
                    check=False, timeout_seconds=10.0,
                )

    def _run_tests(self) -> None:
        if not self.config.dry_run:
            self._assert_no_active_instrumentation("before instrumentation")
            self._load_owned_uids()
            self._isolation_device(
                "device-isolation-logcat-clear", "logcat", "-b", "all", "-c")
        self._suite_started = True
        if self.scenario is None:
            self._instrument_once(self._instrument_args())
            return
        if not self.config.dry_run and self.pins.api is not None and self.pins.api < DIRECT_PHASE_MIN_API:
            self.scenario_skipped = True
            self.scenario_skip_reason = (
                f"direct phase instrumentation requires API {DIRECT_PHASE_MIN_API}+ "
                f"(--no-restart); device is API {self.pins.api}"
            )
            with (self.paths.root / "skipped-scenarios.txt").open("a", encoding="utf-8") as output:
                output.write(f"scenario {self.scenario.name} skipped: {self.scenario_skip_reason}\n")
            self.notice(self.scenario_skip_reason)
            return
        phase_shared_args = self._phase_instrument_args()

        def run_phase(
            args: list[str],
            phase: int,
            on_started: Callable[[], None] | None = None,
        ) -> list[TestResult]:
            pid = self._prepare_phase_process()
            with self._phase_process_unfrozen(pid):
                return self._instrument_once(
                    phase_shared_args + args, phase, on_started, orchestrated=False
                )

        phases = PhaseRunner(
            self.config, self.commands, self.storage, self.scenario,
            run_phase,
            self.warn, self.notice)
        (self.scenario_skipped,
         self.scenario_skip_reason,
         self.scenario_actions,
         self.scenario_abort_reason) = phases.run()
        if (self.scenario_abort_reason is not None
                and not any(test.status.value in ("FAIL", "ERROR")
                            for test in self.state.tests)):
            self.run_errors.append(self.scenario_abort_reason)

    def _collect(self) -> None:
        if self.config.trace_timing:
            records = []
            for log in sorted(self.paths.logs.glob("device-isolation-observer-*.log")):
                for line in log.read_text(errors="replace").splitlines():
                    timing = TIMING_LOG.match(line)
                    if timing is not None:
                        try:
                            record = json.loads(timing.group(1))
                            record["source"] = log.name
                            records.append(record)
                        except ValueError:
                            self.warn("could not parse a diagnostic test timing record")
            (self.paths.root / "test-timings.jsonl").write_text(
                "".join(json.dumps(record) + "\n" for record in records))
        if self.config.dry_run:
            self.commands.record(f"dry-run: collect {FAILURE_BUNDLE_ROOT}, external files, logcat")
            return
        count = self._device("evidence-count", "shell",
                             f"if [ -d {FAILURE_BUNDLE_ROOT} ]; then ls -1 {FAILURE_BUNDLE_ROOT} | wc -l; fi",
                             mutate=False, check=False)
        if count:
            completed = self.commands.run(CommandSpec(self._adb("pull", FAILURE_BUNDLE_ROOT,
                                                                 str(self.paths.failure_bundles)),
                                                       "pull-failure-bundles"), check=False)
            if completed.returncode:
                self.warn(f"could not pull {FAILURE_BUNDLE_ROOT}; retaining the device copy")
            else:
                # adb may nest the remote basename depending on destination existence.
                nested = self.paths.failure_bundles / "mjpdf-test-artifacts"
                source = nested if nested.is_dir() else self.paths.failure_bundles
                self.bundle_count = sum(path.is_dir() for path in source.iterdir())
                self._device("remove-collected-evidence", "shell", "rm", "-rf", FAILURE_BUNDLE_ROOT,
                             check=False)
                self.storage.delete_media_rows_under(FAILURE_BUNDLE_ROOT)
        device_files = f"{SDCARD}/Android/data/{self.config.package}/files"
        exists = self._device("external-files-probe", "shell",
                              f"if [ -d {device_files} ]; then echo yes; fi",
                              mutate=False, check=False)
        if exists == "yes":
            completed = self.commands.run(CommandSpec(self._adb("pull", device_files,
                                                                 str(self.paths.device_files)),
                                                       "pull-device-files"), check=False)
            if completed.returncode:
                self.warn(f"could not pull {device_files}")
        logcat = self.commands.run(CommandSpec(
            self._adb("logcat", "-d", "-t", "20000", "-v", "threadtime"),
            "collect-logcat", mutate=False,
        ), check=False)
        (self.paths.root / "logcat.txt").write_text(
            logcat.output + ("\n" if logcat.output else ""), encoding="utf-8")
        if logcat.returncode:
            self.warn("could not read logcat")

    def _classify_results(self) -> tuple[bool, bool]:
        if self.config.dry_run or self.scenario_skipped:
            return False, False
        no_tests = not self.state.tests
        failed = any(test.status.value in ("FAIL", "ERROR") for test in self.state.tests)
        return failed or bool(self.run_errors) or no_tests, no_tests

    def run(self) -> ExitCode:
        try:
            return self._run_leased()
        finally:
            try:
                if self.config.prepared_fixtures is None:
                    cleanup_generated(self.paths.root / "prepared-fixtures")
            finally:
                self._release_device_lease()

    def _scenario_json(self) -> dict[str, object] | None:
        if self.scenario is None:
            return None
        return {
            "name": self.scenario.name,
            "description": self.scenario.description,
            "skipped": self.scenario_skipped,
            "skipReason": self.scenario_skip_reason,
            "abortReason": self.scenario_abort_reason,
            "phaseCount": self.scenario.phase_count,
            "actions": self.scenario_actions,
        }

    def record_platform_skips(self, results: list[TestResult]) -> None:
        """Persist coordinator-assigned methods skipped by a platform requirement."""
        if self.summary is None:
            raise RuntimeError("cannot record platform skips before runner completion")
        for result in results:
            self.state.tests.append(result)
            self.renderer.finish(result)
        self.summary.tests = tuple(self.state.tests)
        write_junit(self.summary.tests, self.paths.junit, run_errors=self.run_errors)
        write_summary(
            self.config, self.paths, self.state, self.summary, self.profile,
            self._scenario_json(),
        )

    def _run_leased(self) -> ExitCode:
        self.renderer.start(self.state)
        started = self.state.started_at
        operational: RunnerFailure | None = None
        suite_failed = False
        try:
            self._acquire_device_lease()
            with self.step("Require device"):
                self._require_device()
            with self.step("Build"):
                self._build()
            with self.step("Prepare APKs"):
                prepared_apks = self._prepare_apks()
            with self.step("Install APKs"):
                self._install_apk(prepared_apks.app, self.config.package)
                self._install_apk(prepared_apks.test, self.config.test_package)
            with self.step("Install orchestrator"):
                self._install_orchestrator()
            with self.step("Reset system pickers"):
                self._reset_system_pickers()
            with self.step("Pin environment"):
                try:
                    self.profile = self.pins.apply()
                except (PinError, CommandFailure) as exc:
                    raise RunnerFailure(ExitCode.DEVICE_OR_PIN, str(exc)) from exc
            with self.step("Clear stale evidence"):
                self.storage.clear_stale_evidence()
            with self.step("Snapshot pre-seed"):
                self.storage.snapshot("pre-seed")
                self._preseed = True
            with self.step("Seed fixtures"):
                self._seed_started = True
                try:
                    self.fixtures.seed()
                except FixtureError as exc:
                    raise RunnerFailure(ExitCode.FAILED, str(exc)) from exc
            with self.step("Snapshot post-seed"):
                self.storage.snapshot("post-seed")
            if self.config.test_file is not None:
                with self.step("Push test shard"):
                    self._push_shard()
            with self.step("Run tests"):
                self._run_tests()
        except RunnerFailure as exc:
            operational = exc
            self.run_errors.append(str(exc))
        except (CommandFailure, OSError, RuntimeError) as exc:
            operational = RunnerFailure(ExitCode.FAILED, str(exc))
            self.run_errors.append(str(exc))
        finally:
            self._cleanup_started = True
            if self._suite_started:
                try:
                    with self.step("Snapshot post-run"):
                        self.storage.snapshot("post-run")
                    with self.step("Collect artifacts"):
                        self._collect()
                    with self.step("Classify results"):
                        suite_failed, _ = self._classify_results()
                except Exception as exc:
                    self.run_errors.append(f"evidence collection failed: {exc}")
                    if operational is None:
                        operational = RunnerFailure(ExitCode.FAILED, str(exc))
            if self._seed_started or self._all_files_grant_api is not None:
                try:
                    with self.step("Sweep fixtures"):
                        try:
                            if self._seed_started:
                                self.storage.sweep()
                        finally:
                            if self._all_files_grant_api is not None:
                                self.storage.revoke_all_files(self._all_files_grant_api)
                    if self._preseed and self._seed_started:
                        with self.step("Snapshot post-sweep"):
                            self.storage.snapshot("post-sweep")
                except Exception as exc:
                    self.run_errors.append(f"cleanup failed: {exc}")
                    if operational is None:
                        operational = RunnerFailure(ExitCode.FAILED, str(exc))
            if self._device_test_file:
                self._device("remove-test-file", "shell", "rm", "-f", self._device_test_file,
                             check=False)
            try:
                with self.step("Restore pins"):
                    self.pins.restore()
            except Exception as exc:
                self.warn(f"pin restoration failed: {exc}")
        leftovers: list[str] = []
        if self._preseed and self._seed_started and (self.paths.storage / "post-sweep.txt").exists():
            try:
                with self.step("Check leftovers"):
                    leftovers = self.storage.check_leftovers()
            except Exception as exc:
                self.run_errors.append(f"leftover check failed: {exc}")
                if operational is None:
                    operational = RunnerFailure(ExitCode.FAILED, str(exc))
        if self._interrupted is not None:
            self.commands.record(f"signal {self._interrupted}")
        if (operational is not None
                and operational.code is ExitCode.DEVICE_OR_PIN
                and self._contamination_observed):
            code = ExitCode.DEVICE_OR_PIN
            verdict = "DEVICE/PIN FAILURE"
        elif self._interrupted is not None:
            code = ExitCode.INTERRUPTED if self._interrupted == signal.SIGINT else ExitCode.TERMINATED
            verdict = f"INTERRUPTED-{self._interrupted}"
        elif operational is not None:
            code = operational.code
            verdict = "DEVICE/PIN FAILURE" if code is ExitCode.DEVICE_OR_PIN else "OPERATIONAL FAILURE"
        elif self.config.dry_run:
            code, verdict = ExitCode.CLEAN, "DRY-RUN"
        elif self.scenario_skipped:
            code, verdict = ExitCode.CLEAN, "SCENARIO SKIPPED"
        elif suite_failed:
            code, verdict = ExitCode.FAILED, "FAILED"
        elif leftovers:
            code, verdict = ExitCode.LEFTOVERS, "LEFTOVERS"
        else:
            code, verdict = ExitCode.CLEAN, "CLEAN"
        has_failing_test = any(
            test.status.value in ("FAIL", "ERROR") for test in self.state.tests)
        junit_failure: str | None = None
        if code != ExitCode.CLEAN and not self.run_errors and not has_failing_test:
            junit_failure = ("no tests executed" if not self.state.tests
                             else f"runner verdict {verdict} (exit {int(code)})")
        write_junit(self.state.tests, self.paths.junit,
                    run_errors=self.run_errors, runner_failure=junit_failure)
        for pending in self.state.steps:
            if pending.state is StepState.PENDING and pending.name != "Write summary":
                pending.state = StepState.SKIPPED
                pending.finished_at = time.time()
        finished = time.time()
        self.summary = RunSummary(
            code, verdict, started, finished, self.state.tests, self.run_errors,
            len(self.state.warnings), self.bundle_count, len(leftovers),
        )
        scenario_json = self._scenario_json()
        summary_time = time.time()
        summary_step = next(step for step in self.state.steps if step.name == "Write summary")
        summary_step.state = StepState.COMPLETE
        summary_step.started_at = summary_time
        summary_step.finished_at = summary_time
        self.renderer.update(self.state)
        write_summary(self.config, self.paths, self.state, self.summary, self.profile, scenario_json)
        self.renderer.close()
        return code
