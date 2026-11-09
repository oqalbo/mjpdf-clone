from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum, IntEnum
from pathlib import Path
from typing import Sequence


class ExitCode(IntEnum):
    CLEAN = 0
    FAILED = 1
    USAGE = 2
    DEVICE_OR_PIN = 3
    LEFTOVERS = 4
    INTERRUPTED = 130
    TERMINATED = 143


class RendererLane(str, Enum):
    HARDWARE = "hardware"
    SOFTWARE = "software"


class StepState(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    COMPLETE = "complete"
    FAILED = "failed"
    SKIPPED = "skipped"


class TestStatus(str, Enum):
    PASS = "PASS"
    FAIL = "FAIL"
    ERROR = "ERROR"
    SKIP = "SKIP"


@dataclass(frozen=True)
class RunnerConfig:
    repo_root: Path
    runner_dir: Path
    run_id: str
    serial: str
    adb: str
    package: str
    test_package: str
    profile_size: str
    profile_density: str
    profile_locale: str
    profile_timezone: str
    logcat_buffer: str
    renderer_lane: RendererLane
    app_apk: Path | None = None
    test_apk: Path | None = None
    classes: tuple[str, ...] = ()
    packages: tuple[str, ...] = ()
    annotations: tuple[str, ...] = ()
    not_annotations: tuple[str, ...] = ()
    test_file: Path | None = None
    phase_scenario: str | None = None
    passthrough: tuple[str, ...] = ()
    with_perf_library: bool = False
    sweep_perf_library: bool = False
    skip_build: bool = False
    skip_generated: bool = False
    clear_package_data: bool = True
    keep_pins: bool = False
    dry_run: bool = False
    required_fixtures: frozenset[str] | None = None
    fixture_resolution: str = "legacy preparation; selection not resolved"
    prepared_fixtures: Path | None = None
    utility_apks: tuple[Path, ...] = ()
    trace_timing: bool = False
    force_stop_packages: tuple[str, ...] = ()


@dataclass(frozen=True)
class RunPaths:
    root: Path
    logs: Path
    junit: Path
    failure_bundles: Path
    device_files: Path
    storage: Path

    @classmethod
    def create(cls, root: Path) -> "RunPaths":
        root.mkdir(parents=True, exist_ok=False)
        paths = cls(root, root / "logs", root / "junit",
                    root / "failure-bundles", root / "device-files", root / "storage")
        for directory in (paths.logs, paths.junit, paths.failure_bundles,
                          paths.device_files, paths.storage):
            directory.mkdir()
        return paths


@dataclass(frozen=True)
class CommandSpec:
    argv: tuple[str, ...]
    name: str
    cwd: Path | None = None
    env: dict[str, str] | None = None
    mutate: bool = True
    instrument: bool = False
    acceptable: tuple[int, ...] = (0,)
    timeout_seconds: float | None = None


@dataclass(frozen=True)
class CompletedCommand:
    argv: tuple[str, ...]
    returncode: int
    output: str


@dataclass(frozen=True)
class PinRecord:
    name: str
    prior: str
    applied: str
    restore_argv: tuple[str, ...] = ()
    restore_shell: str | None = None


@dataclass(frozen=True)
class TestKey:
    class_name: str
    method: str

    def display(self) -> str:
        return f"{self.class_name}#{self.method}"


@dataclass
class TestResult:
    key: TestKey
    phase: int | None
    raw_status_code: int
    status: TestStatus
    duration_seconds: float
    stack: str = ""

    @property
    def first_error_line(self) -> str:
        for line in self.stack.splitlines():
            if line.strip():
                return line.strip()[:400]
        return ""

@dataclass(frozen=True)
class ExecutionCounts:
    """Actual outcomes plus planned assignments that never reported a result."""

    passed: int
    failed: int
    skipped: int
    not_run: int
    total: int

    @classmethod
    def from_results(cls, results: Sequence[TestResult], planned: int,
                     not_run: int | None = None) -> "ExecutionCounts":
        passed = sum(result.status is TestStatus.PASS for result in results)
        failed = sum(result.status in {TestStatus.FAIL, TestStatus.ERROR}
                     for result in results)
        skipped = sum(result.status is TestStatus.SKIP for result in results)
        missing = max(0, planned - len(results)) if not_run is None else max(0, not_run)
        # One catalog method may emit several parameterized invocations. Once
        # results exist, the only consistent execution total is actual rows
        # plus assignments known not to have run.
        total = len(results) + missing
        return cls(passed, failed, skipped, missing, total)

    @property
    def reported(self) -> int:
        return self.passed + self.failed + self.skipped


@dataclass
class StepRecord:
    name: str
    state: StepState = StepState.PENDING
    started_at: float | None = None
    finished_at: float | None = None
    detail: str = ""


@dataclass
class RunState:
    started_at: float
    artifact_dir: Path
    title: str = "Full suite"
    steps: list[StepRecord] = field(default_factory=list)
    tests: list[TestResult] = field(default_factory=list)
    current_test: TestKey | None = None
    current_test_started_at: float | None = None
    total_tests: int | None = None
    warnings: list[str] = field(default_factory=list)
    notices: list[str] = field(default_factory=list)

    @property
    def active_step(self) -> StepRecord | None:
        return next((s for s in self.steps if s.state == StepState.RUNNING), None)


@dataclass(frozen=True)
class PhaseAction:
    kind: str
    value: str = ""


@dataclass(frozen=True)
class PhaseScenario:
    name: str
    description: str
    requirements: tuple[str, ...]
    actions: tuple[PhaseAction, ...]

    @property
    def phase_count(self) -> int:
        return sum(action.kind == "phase" for action in self.actions)


@dataclass
class RunSummary:
    exit_code: ExitCode
    verdict: str
    started_at: float
    finished_at: float
    tests: Sequence[TestResult]
    run_errors: Sequence[str]
    warning_count: int
    bundle_count: int
    leftover_count: int
