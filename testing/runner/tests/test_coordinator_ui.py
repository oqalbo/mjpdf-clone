from __future__ import annotations

import json
import signal
import re
from io import StringIO
import threading
import sys
import unittest
import tempfile
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import patch

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.catalog import TestSpec, TestUnit
from mjpdf_runner.coordinator import (MAX_CONCURRENT_SOFTWARE_DEVICES, Batch, Coordinator,
                                      CoordinatorState, DeviceProgress, DeviceTestResult,
                                      ExecutionView, WorkerRenderer)
from mjpdf_runner.devices import DeviceInfo
from mjpdf_runner.interactive import WizardResult
from mjpdf_runner.model import (CompletedCommand, ExecutionCounts, ExitCode, RendererLane,
                                 RunPaths, RunState, RunSummary, StepRecord, StepState, TestKey,
                                 TestResult, TestStatus)
from mjpdf_runner.planner import DURATION_CALIBRATION, automatic_plan
from mjpdf_runner.process import CommandFailure


class FakePanel:
    def __init__(self) -> None:
        self.size = type("Size", (), {"columns": 120, "lines": 30})()
        self.frames: list[list[str]] = []

    def draw(self, lines: list[str]) -> None:
        self.frames.append(lines)

    def erase(self) -> None:
        pass

    def print_static(self, lines: list[str]) -> None:
        self.frames.append(lines)

    def close(self) -> None:
        pass


class CoordinatorFrameTests(unittest.TestCase):
    @staticmethod
    def device(serial: str, api: int, lane: RendererLane) -> DeviceInfo:
        return DeviceInfo(serial, serial, "15", api, "x86_64", "1080x2400", "440",
                           4096, lane, lane.value, True, "mine")

    def test_manifest_records_costs_and_unknown_profile_without_changing_counts(self) -> None:
        device = self.device("unmeasured", 37, RendererLane.HARDWARE)
        test = TestSpec("example.Test#run", "example.Test", "run", "performance", "entry",
                        False, False, False, "one", required_fixtures=frozenset({"HUGE_PDF"}))
        unit = TestUnit("one", "entry", test.selector, (test,), False, False, None)
        plan = automatic_plan((unit,), (device,))
        selection = WizardResult((device,), ("entry",), (test,), (unit,), plan)

        with tempfile.TemporaryDirectory() as directory:
            paths = RunPaths.create(Path(directory) / "run")
            coordinator = Coordinator(selection, RUNNER_DIR.parents[1], RUNNER_DIR,
                                      paths, "adb", FakePanel(), StringIO())
            coordinator._write_manifest()
            manifest = json.loads((paths.root / "manifest.json").read_text())

        self.assertEqual(DURATION_CALIBRATION, manifest["durationCalibration"])
        self.assertEqual(1, manifest["executionCount"])
        recorded = manifest["devices"][0]
        self.assertEqual(1, recorded["assignedTests"])
        self.assertEqual(1, recorded["durationFallbackTests"])
        self.assertEqual(1.0, recorded["durationFactor"])
        self.assertFalse(recorded["durationProfileCalibrated"])
        self.assertAlmostEqual(DURATION_CALIBRATION["categorySeconds"]["NORMAL"],
                               recorded["estimatedTestSeconds"], places=2)
        self.assertAlmostEqual(plan.estimated_seconds((device,))[device.serial],
                               recorded["estimatedWorkSeconds"], places=2)

    def test_incompatible_planner_results_are_reported_as_skips(self) -> None:
        device = self.device("emulator-5570", 28, RendererLane.SOFTWARE)
        test = TestSpec(
            "example.Baseline#pixels", "example.Baseline", "pixels",
            "baseline", "rendering", False, False, True, "pixels",
            required_profile="api36-1080x2400-440dpi-3button-en-US-swiftshader",
        )
        unit = TestUnit(
            "pixels", "rendering", test.selector, (test,), True, False, None,
            test.required_profile,
        )
        plan = automatic_plan((unit,), (device,))
        selection = WizardResult(
            (device,), ("rendering",), (test,), (unit,), plan,
        )

        with tempfile.TemporaryDirectory() as directory:
            paths = RunPaths.create(Path(directory) / "run")
            coordinator = Coordinator(
                selection, RUNNER_DIR.parents[1], RUNNER_DIR, paths, "adb", FakePanel(),
                StringIO(),
            )
            (paths.root / "devices" / device.serial).mkdir(parents=True)
            coordinator._run_device(device, ())
            device_summary = json.loads(
                (paths.root / "devices" / device.serial / "summary.json").read_text()
            )

        progress = coordinator.state.devices[device.serial]
        self.assertEqual(1, coordinator.state.expected_executions)
        self.assertEqual(1, progress.total)
        self.assertEqual(1, progress.completed)
        self.assertEqual(1, len(coordinator.state.results))
        self.assertIs(TestStatus.SKIP, coordinator.state.results[0].result.status)
        self.assertEqual(1, device_summary["counts"]["SKIP"])
        self.assertEqual(0, device_summary["notRun"])
        self.assertEqual(1, device_summary["reportedResults"])

    def test_software_devices_use_bounded_slots_while_hardware_starts_directly(self) -> None:
        software = self.device("emulator-5568", 37, RendererLane.SOFTWARE)
        hardware = self.device("emulator-5566", 37, RendererLane.HARDWARE)
        state = CoordinatorState(
            started_at=100.0,
            run_id="example",
            phase="Running",
            devices={
                software.serial: DeviceProgress(software, 1),
                hardware.serial: DeviceProgress(hardware, 1),
            },
            expected_executions=2,
        )
        coordinator = object.__new__(Coordinator)
        coordinator._prepared_fixtures = None
        coordinator._lock = threading.RLock()
        coordinator.state = state
        coordinator.view = SimpleNamespace(changed=lambda: None)
        calls = []
        coordinator._run_device_safe = lambda device, units: calls.append((device, units))

        class Slot:
            entered = 0

            def __enter__(self):
                self.entered += 1

            def __exit__(self, *_args):
                return False

        slot = Slot()
        coordinator._run_device_scheduled(software, (), slot)
        coordinator._run_device_scheduled(hardware, (), slot)

        self.assertEqual(8, MAX_CONCURRENT_SOFTWARE_DEVICES)
        self.assertEqual("Waiting for software slot", state.devices[software.serial].step)
        self.assertEqual([(software, ()), (hardware, ())], calls)


    def test_running_frame_has_one_active_test_and_two_timers_per_emulator(self) -> None:
        first = self.device("emulator-5554", 28, RendererLane.HARDWARE)
        second = self.device("emulator-5568", 37, RendererLane.SOFTWARE)
        state = CoordinatorState(
            started_at=100.0,
            run_id="example",
            phase="Running",
            devices={
                first.serial: DeviceProgress(first, 10, started_at=110.0, completed=3,
                                             current=TestKey("example.IntakeTest", "opensPdf"),
                                             current_started_at=125.0, step="Run tests"),
                second.serial: DeviceProgress(second, 8, started_at=115.0, completed=2,
                                              current=TestKey("example.SearchTest", "findsText"),
                                              current_started_at=130.0, step="Run tests"),
            },
            expected_executions=18,
        )
        view = ExecutionView(FakePanel(), state)

        with patch("mjpdf_runner.coordinator.time.time", return_value=140.0):
            frame = view._frame()

        text = "\n".join(frame)
        self.assertEqual(1, sum("emulator-5554" in line for line in frame))
        self.assertEqual(1, sum("emulator-5568" in line for line in frame))
        self.assertIn(
            "run 00:30 · emulator-5554 · API 28 · HW · 3/10 · "
            "RUN · test 00:15 · IntakeTest#opensPdf", text
        )
        self.assertIn(
            "run 00:25 · emulator-5568 · API 37 · SW · 2/8 · "
            "RUN · test 00:10 · SearchTest#findsText", text
        )
        self.assertNotIn("com.gitlab", text)

    def test_finished_test_updates_aggregate_counts_before_batch_completion(self) -> None:
        device = self.device("emulator-5554", 28, RendererLane.HARDWARE)
        state = CoordinatorState(
            started_at=100.0,
            run_id="example",
            phase="Running",
            devices={device.serial: DeviceProgress(device, 1, started_at=100.0)},
            expected_executions=1,
        )
        coordinator = object.__new__(Coordinator)
        coordinator._prepared_fixtures = None
        coordinator._lock = threading.RLock()
        coordinator.state = state
        result = TestResult(TestKey("example.IntakeTest", "opensPdf"), None, 0,
                            TestStatus.PASS, 1.0)

        WorkerRenderer(coordinator, device.serial, 0, 0).finish(result)
        frame = ExecutionView(FakePanel(), state)._frame()

        self.assertEqual(1, len(state.results))
        self.assertIn("1 pass · 0 fail", "\n".join(frame))

    def test_live_parameterized_progress_expands_both_denominators(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.SOFTWARE)
        results = [
            TestResult(
                TestKey("example.ParameterizedTest", f"case[{index}]"),
                None, 0, TestStatus.PASS, 1.0,
            )
            for index in range(6)
        ]
        state = CoordinatorState(
            started_at=100.0,
            run_id="example",
            phase="Running",
            devices={device.serial: DeviceProgress(device, 1, started_at=100.0)},
            expected_executions=1,
            results=[DeviceTestResult(device.serial, result) for result in results],
        )
        coordinator = object.__new__(Coordinator)
        coordinator._prepared_fixtures = None
        coordinator._lock = threading.RLock()
        coordinator.state = state
        coordinator.view = SimpleNamespace(changed=lambda: None)
        runner_state = RunState(100.0, Path("/tmp/run"), tests=results)

        coordinator.worker_update(device.serial, 0, 0, runner_state)
        frame = ExecutionView(FakePanel(), state)._frame()
        text = "\n".join(frame)

        self.assertIn("Execute 6/6", text)
        self.assertIn("SW · 6/6", text)

    def test_reconciliation_retracts_only_provisional_results(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.SOFTWARE)
        state = CoordinatorState(
            started_at=100.0,
            run_id="example",
            phase="Running",
            devices={device.serial: DeviceProgress(device, 2, started_at=100.0)},
            expected_executions=2,
        )
        coordinator = object.__new__(Coordinator)
        coordinator._prepared_fixtures = None
        coordinator._lock = threading.RLock()
        coordinator.state = state
        coordinator.view = SimpleNamespace(changed=lambda: None)
        before = TestResult(TestKey("example.ScanTest", "before"), None, 0,
                            TestStatus.PASS, 1.0)
        contaminated = TestResult(TestKey("example.ScanTest", "contaminated"), None, -2,
                                  TestStatus.FAIL, 1.0)
        renderer = WorkerRenderer(coordinator, device.serial, 0, 0)

        renderer.finish(before)
        renderer.finish(contaminated)
        self.assertEqual(2, len(state.results))
        renderer.reconcile((before,))

        self.assertEqual([before], [item.result for item in state.results])

    def test_739_completion_interrupt_tracks_unfinished_assignment_as_not_run(self) -> None:
        device = self.device("emulator-5568", 37, RendererLane.SOFTWARE)
        tests = tuple(
            TestSpec(
                f"example.InterruptionTest#case{index}",
                "example.InterruptionTest", f"case{index}",
                "runner", "runner", False, False, False,
                f"example.InterruptionTest#case{index}",
            )
            for index in range(740)
        )
        unit = TestUnit("interrupted-unit", "runner", None, tests, False, False, None)
        reported = tuple(
            TestResult(TestKey(test.class_name, test.method), None, 0,
                       TestStatus.PASS, 0.01)
            for test in tests[:739]
        )
        live_counts: list[int] = []

        class InterruptedRunner:
            scenario_skipped = False
            scenario_skip_reason = None

            def __init__(self, _config: object, paths: object, renderer: WorkerRenderer,
                         _scenario: object) -> None:
                self.renderer = renderer
                self.state = RunState(100.0, paths.root)
                self.summary = None

            def run(self) -> ExitCode:
                for result in reported:
                    self.renderer.finish(result)
                live_counts.append(len(coordinator.state.results))
                self.renderer.reconcile(reported)
                self.state.steps = [
                    StepRecord("Run tests", StepState.FAILED, 100.0, 101.0),
                ]
                self.summary = RunSummary(
                    ExitCode.INTERRUPTED, "INTERRUPTED-2", 100.0, 101.0,
                    reported, ("interrupted by signal 2",), 0, 0, 0,
                )
                coordinator._cancelled.set()
                return ExitCode.INTERRUPTED

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.runner_dir = Path(directory)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, len(tests))}, len(tests),
            )
            coordinator._lock = threading.RLock()
            coordinator._cancelled = threading.Event()
            coordinator._active_runners = {}
            coordinator._device_summaries = {device.serial: []}
            coordinator._device_isolation = set()
            coordinator.view = SimpleNamespace(changed=lambda: None)
            coordinator._base_config = lambda *_args, **_kwargs: object()

            with patch("mjpdf_runner.coordinator.Runner", InterruptedRunner):
                coordinator._run_device_safe(device, (unit,))

        results = [item.result for item in coordinator.state.results]
        progress = coordinator.state.devices[device.serial]
        self.assertEqual([739], live_counts)
        self.assertEqual(739, len(results))
        self.assertEqual(739, sum(result.status is TestStatus.PASS for result in results))
        self.assertEqual(0, sum(result.status is TestStatus.ERROR for result in results))
        self.assertEqual(739, progress.completed)
        self.assertEqual(1, progress.not_run)


    def test_completed_batch_uses_summary_warning_count_without_becoming_error(self) -> None:
        device = self.device("emulator-5554", 28, RendererLane.HARDWARE)
        test = TestSpec(
            "example.IntakeTest#opensPdf", "example.IntakeTest", "opensPdf",
            "intake", "intake", False, False, False,
            "example.IntakeTest#opensPdf",
        )
        unit = TestUnit(test.atomic_key, "intake", test.selector, (test,),
                        False, False, None)
        result = TestResult(TestKey(test.class_name, test.method), None, 0,
                            TestStatus.PASS, 1.0)

        class FakeRunner:
            scenario_skipped = False
            scenario_skip_reason = None

            def __init__(self, _config: object, paths: object, renderer: WorkerRenderer,
                         _scenario: object) -> None:
                self.paths = paths
                self.renderer = renderer
                self.state = RunState(100.0, paths.root)
                self.summary = None

            def run(self) -> ExitCode:
                self.renderer.finish(result)
                self.state.steps = [
                    StepRecord("Require device", StepState.COMPLETE, 100.0, 102.0),
                    StepRecord("Seed fixtures", StepState.COMPLETE, 102.0, 108.0),
                    StepRecord("Run tests", StepState.COMPLETE, 108.0, 109.0),
                    StepRecord("Collect artifacts", StepState.COMPLETE, 109.0, 115.0),
                ]
                self.summary = RunSummary(
                    ExitCode.CLEAN, "CLEAN", 100.0, 101.0, (result,), (), 2, 0, 0,
                )
                return ExitCode.CLEAN

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.runner_dir = Path(directory)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, 1)}, 1,
            )
            coordinator._lock = threading.RLock()
            coordinator._cancelled = threading.Event()
            coordinator._active_runners = {}
            coordinator._device_summaries = {device.serial: []}
            coordinator.view = SimpleNamespace(changed=lambda: None)
            coordinator._base_config = lambda *_args, **_kwargs: object()

            with patch("mjpdf_runner.coordinator.Runner", FakeRunner):
                coordinator._run_device_safe(device, (unit,))

            progress = coordinator.state.devices[device.serial]
            summary_path = root / "devices" / device.serial / "summary.json"
            summary = json.loads(summary_path.read_text())
            self.assertEqual([], coordinator.state.errors)
            self.assertEqual("CLEAN", progress.verdict)
            self.assertEqual(8.0, progress.before_tests_seconds)
            self.assertEqual(1.0, progress.test_seconds)
            self.assertEqual(6.0, progress.after_tests_seconds)
            self.assertEqual(2, progress.warning_count)
            self.assertEqual(1, summary["reportedResults"])
            self.assertEqual(0, summary["notRun"])
            self.assertEqual("CLEAN", summary["verdict"])

    def test_final_output_has_one_lifecycle_box_and_per_emulator_metrics(self) -> None:
        first = self.device("emulator-5554", 28, RendererLane.HARDWARE)
        second = self.device("emulator-5568", 37, RendererLane.SOFTWARE)
        first_progress = DeviceProgress(
            first, 3, started_at=100.0, finished_at=112.0, completed=3,
            verdict="FAILED", exit_code=ExitCode.FAILED, warning_count=2,
            before_tests_seconds=12.5,
            test_seconds=20.0, after_tests_seconds=8.0,
            before_step_seconds={"Seed fixtures": 7.5, "Install APKs": 5.0},
            after_step_seconds={"Collect artifacts": 6.0, "Sweep fixtures": 2.0},
        )
        second_progress = DeviceProgress(
            second, 2, started_at=105.0, finished_at=125.0, completed=2,
            verdict="CLEAN", warning_count=1, before_tests_seconds=4.0,
            test_seconds=15.0, after_tests_seconds=1.0,
        )
        results = [
            DeviceTestResult(
                first.serial,
                TestResult(TestKey("example.FirstTest", "passes"), None, 0,
                           TestStatus.PASS, 1.0),
            ),
            DeviceTestResult(
                first.serial,
                TestResult(TestKey("example.FirstTest", "fails"), None, -2,
                           TestStatus.FAIL, 10.0),
            ),
            DeviceTestResult(
                first.serial,
                TestResult(TestKey("example.FirstTest", "skips"), None, -3,
                           TestStatus.SKIP, 2.0),
            ),
            DeviceTestResult(
                second.serial,
                TestResult(TestKey("example.SecondTest", "quick"), None, 0,
                           TestStatus.PASS, 3.0),
            ),
            DeviceTestResult(
                second.serial,
                TestResult(TestKey("example.SecondTest", "slow"), None, 0,
                           TestStatus.PASS, 60.0),
            ),
        ]
        state = CoordinatorState(
            100.0, "example", "Complete",
            {first.serial: first_progress, second.serial: second_progress}, 5,
            results, finished_at=130.0, exit_code=ExitCode.FAILED, verdict="FAILED",
        )
        view = ExecutionView(FakePanel(), state)

        frame = view._frame(final=True)

        text = "\n".join(frame)
        self.assertIn("╭ Emulator runs ", frame[0])
        self.assertEqual(1, sum("╭ Metrics summary " in line for line in frame))
        self.assertIn("emulator-5554 · API 28 · HW · FAILED", text)
        self.assertIn("emulator-5568 · API 37 · SW · PASSED", text)
        self.assertIn("├─ Before tests", text)
        self.assertIn("├─ Tests", text)
        self.assertIn("└─ After tests", text)
        self.assertEqual(1, text.count("Seed fixtures"))
        self.assertEqual(1, text.count("Collect artifacts"))
        self.assertNotIn("Install APKs", text)
        self.assertNotIn("Sweep fixtures", text)
        self.assertRegex(text, r"emulator-5554\s+1\s+1\s+1\s+0\s+3\s+2")
        self.assertRegex(text, r"emulator-5568\s+2\s+0\s+0\s+0\s+2\s+1")
        self.assertIn("Not run", text)
        self.assertIn("All tests", text)
        self.assertIn("Fleet elapsed", text)
        self.assertIn("Cumulative tests", text)
        self.assertIn("Parallelism", text)
        self.assertIn("12s", text)
        self.assertIn("20s", text)
        self.assertIn("RESULT", text)
        self.assertIn("FAILED · exit 1", text)
        self.assertIn("RUN", text)
        self.assertIn("testing/runner/runs/example", text)

    def test_final_device_and_fleet_labels_match_every_exit_semantic(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.SOFTWARE)
        cases = (
            (ExitCode.CLEAN, "CLEAN", "CLEAN", "PASSED"),
            (ExitCode.FAILED, "FAILED", "FAILED", "FAILED"),
            (ExitCode.FAILED, "OPERATIONAL FAILURE",
             "OPERATIONAL FAILURE", "OPERATIONAL FAILURE"),
            (ExitCode.DEVICE_OR_PIN, "DEVICE/PIN FAILURE",
             "DEVICE/PIN FAILURE", "DEVICE/PIN FAILURE"),
            (ExitCode.INTERRUPTED, "INTERRUPTED", "INTERRUPTED", "INTERRUPTED"),
            (ExitCode.TERMINATED, "TERMINATED", "TERMINATED", "TERMINATED"),
            (ExitCode.LEFTOVERS, "LEFTOVERS", "LEFTOVERS", "LEFTOVERS"),
        )
        for exit_code, fleet_verdict, device_verdict, device_label in cases:
            with self.subTest(exit_code=exit_code, verdict=fleet_verdict):
                progress = DeviceProgress(
                    device, 4, started_at=100.0, finished_at=110.0,
                    completed=3, not_run=1, exit_code=exit_code,
                    verdict=device_verdict,
                )
                state = CoordinatorState(
                    100.0, "labels", "Complete",
                    {device.serial: progress}, 4, finished_at=110.0,
                    exit_code=exit_code, verdict=fleet_verdict,
                )

                text = "\n".join(
                    ExecutionView(FakePanel(), state)._frame(final=True))

                self.assertIn(
                    f"emulator-5554 · API 37 · SW · {device_label}", text)
                self.assertRegex(
                    text,
                    rf"RESULT\s+{re.escape(fleet_verdict)} · exit {int(exit_code)}",
                )

    def test_device_isolation_stops_later_batches_and_wins_final_exit(self) -> None:
        device = self.device("emulator-5554", 28, RendererLane.HARDWARE)
        test = TestSpec(
            "example.ScanTest#scan", "example.ScanTest", "scan",
            "home", "home", False, False, False, "example.ScanTest#scan",
        )
        unit = TestUnit(test.atomic_key, "home", test.selector, (test,),
                        False, False, None)

        class IsolatedRunner:
            calls = 0
            scenario_skipped = False
            scenario_skip_reason = None

            def __init__(self, _config, paths, _renderer, _scenario) -> None:
                self.state = RunState(100.0, paths.root)
                self.summary = None

            def run(self) -> ExitCode:
                type(self).calls += 1
                self.summary = RunSummary(
                    ExitCode.DEVICE_OR_PIN, "DEVICE/PIN FAILURE", 100.0, 101.0,
                    (), ("device isolation failure: foreign instrumentation",), 0, 0, 0,
                )
                return ExitCode.DEVICE_OR_PIN

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.runner_dir = Path(directory)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, 2)}, 2,
            )
            coordinator._lock = threading.RLock()
            coordinator._cancelled = threading.Event()
            coordinator._active_runners = {}
            coordinator._device_summaries = {device.serial: []}
            coordinator._device_isolation = set()
            coordinator.view = SimpleNamespace(changed=lambda: None)
            coordinator._base_config = lambda *_args, **_kwargs: object()
            coordinator._batches = lambda _units: (
                Batch("first", (unit,)), Batch("second", (unit,)))

            with patch("mjpdf_runner.coordinator.Runner", IsolatedRunner):
                coordinator._run_device_safe(device, (unit,))
            coordinator._finish()

        self.assertEqual(1, IsolatedRunner.calls)
        self.assertEqual({"emulator-5554"}, coordinator._device_isolation)
        self.assertEqual(
            "DEVICE/PIN FAILURE", coordinator.state.devices[device.serial].verdict)
        self.assertEqual(ExitCode.DEVICE_OR_PIN, coordinator.state.exit_code)
        progress = coordinator.state.devices[device.serial]
        self.assertEqual(0, progress.completed)
        self.assertEqual(2, progress.not_run)
        self.assertEqual([], coordinator.state.results)
        self.assertEqual("DEVICE/PIN FAILURE", coordinator.state.verdict)


    def test_scenario_requirement_skip_is_a_durable_skip_not_not_run(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        test = TestSpec(
            "example.PhaseTest#afterRestart", "example.PhaseTest", "afterRestart",
            "phase", "phase", False, False, False,
            "example.PhaseTest#afterRestart",
        )
        unit = TestUnit(test.atomic_key, "phase", test.selector, (test,),
                        False, False, None)
        reason = "needs secondary volume"

        class SkippedRunner:
            scenario_skipped = True
            scenario_skip_reason = reason

            def __init__(self, _config: object, paths: object, renderer: WorkerRenderer,
                         _scenario: object) -> None:
                self.renderer = renderer
                self.state = RunState(100.0, paths.root)
                self.summary = None

            def run(self) -> ExitCode:
                self.summary = RunSummary(
                    ExitCode.CLEAN, "SCENARIO SKIPPED", 100.0, 101.0,
                    (), (), 0, 0, 0,
                )
                return ExitCode.CLEAN

            def record_platform_skips(self, results: list[TestResult]) -> None:
                assert self.summary is not None
                for result in results:
                    self.renderer.finish(result)
                self.summary.tests = tuple(results)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.runner_dir = Path(directory)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, 1)}, 1,
            )
            coordinator._lock = threading.RLock()
            coordinator._cancelled = threading.Event()
            coordinator._active_runners = {}
            coordinator._device_summaries = {device.serial: []}
            coordinator._device_isolation = set()
            coordinator.view = SimpleNamespace(changed=lambda: None)
            coordinator._base_config = lambda *_args, **_kwargs: object()

            with patch("mjpdf_runner.coordinator.Runner", SkippedRunner):
                coordinator._run_device_safe(device, (unit,))
            coordinator._finish()

            result_text = (root / "results.txt").read_text()
            coordinator.stream = StringIO()
            coordinator._print_non_pass_results()
            terminal_detail = coordinator.stream.getvalue()
            junit_text = (
                root / "junit" / "TEST-example.PhaseTest.xml"
            ).read_text()

        progress = coordinator.state.devices[device.serial]
        self.assertEqual(1, len(coordinator.state.results))
        self.assertEqual(TestStatus.SKIP, coordinator.state.results[0].result.status)
        self.assertEqual(reason, coordinator.state.results[0].result.stack)
        self.assertEqual(0, progress.not_run)
        self.assertEqual(1, progress.counts[TestStatus.SKIP.value])
        self.assertIn("SKIP", result_text)
        self.assertIn(reason, junit_text)
        self.assertEqual("", terminal_detail)

    def test_clean_incomplete_batch_is_operational_failure_without_fake_rows(
            self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        tests = tuple(
            TestSpec(
                f"example.PartialTest#case{index}", "example.PartialTest",
                f"case{index}", "runner", "runner", False, False, False,
                f"example.PartialTest#case{index}",
            )
            for index in range(2)
        )
        unit = TestUnit("partial", "runner", None, tests, False, False, None)
        passed = TestResult(
            TestKey(tests[0].class_name, tests[0].method), None, 0,
            TestStatus.PASS, 1.0,
        )

        class PartialRunner:
            scenario_skipped = False
            scenario_skip_reason = None

            def __init__(self, _config: object, paths: object, renderer: WorkerRenderer,
                         _scenario: object) -> None:
                self.renderer = renderer
                self.state = RunState(100.0, paths.root)
                self.summary = None

            def run(self) -> ExitCode:
                self.renderer.finish(passed)
                self.renderer.reconcile((passed,))
                self.summary = RunSummary(
                    ExitCode.CLEAN, "CLEAN", 100.0, 101.0,
                    (passed,), (), 0, 0, 0,
                )
                return ExitCode.CLEAN

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.runner_dir = Path(directory)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, 2)}, 2,
            )
            coordinator._lock = threading.RLock()
            coordinator._cancelled = threading.Event()
            coordinator._active_runners = {}
            coordinator._device_summaries = {device.serial: []}
            coordinator._device_isolation = set()
            coordinator.view = SimpleNamespace(changed=lambda: None)
            coordinator._base_config = lambda *_args, **_kwargs: object()

            with patch("mjpdf_runner.coordinator.Runner", PartialRunner):
                coordinator._run_device_safe(device, (unit,))
            coordinator._finish()

            result_lines = (root / "results.txt").read_text().splitlines()

        progress = coordinator.state.devices[device.serial]
        self.assertEqual("OPERATIONAL FAILURE", progress.verdict)
        self.assertEqual(1, progress.not_run)
        self.assertEqual(1, progress.completed)
        self.assertEqual(1, len(coordinator.state.errors))
        self.assertIn("1 assigned test(s) did not run", coordinator.state.errors[0])
        self.assertEqual("OPERATIONAL FAILURE", coordinator.state.verdict)
        self.assertEqual(ExitCode.FAILED, coordinator.state.exit_code)
        self.assertEqual(1, len(result_lines))
        self.assertIn("PASS", result_lines[0])
        self.assertFalse(any(
            item.result.status is TestStatus.ERROR for item in coordinator.state.results))

    def test_complete_non_clean_batch_preserves_failed_and_leftover_exits(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        test = TestSpec(
            "example.CleanupTest#passes", "example.CleanupTest", "passes",
            "runner", "runner", False, False, False,
            "example.CleanupTest#passes",
        )
        unit = TestUnit(test.atomic_key, "runner", test.selector, (test,),
                        False, False, None)

        for batch_exit, status, expected_verdict in (
                (ExitCode.FAILED, TestStatus.PASS, "OPERATIONAL FAILURE"),
                (ExitCode.FAILED, TestStatus.FAIL, "OPERATIONAL FAILURE"),
                (ExitCode.LEFTOVERS, TestStatus.PASS, "LEFTOVERS")):
            with self.subTest(batch_exit=batch_exit, status=status):
                result = TestResult(
                    TestKey(test.class_name, test.method), None,
                    -2 if status is TestStatus.FAIL else 0, status, 1.0,
                    "assertion failed" if status is TestStatus.FAIL else "")

                class NonCleanRunner:
                    scenario_skipped = False
                    scenario_skip_reason = None

                    def __init__(self, _config: object, paths: object,
                                 renderer: WorkerRenderer, _scenario: object) -> None:
                        self.renderer = renderer
                        self.state = RunState(100.0, paths.root)
                        self.summary = None

                    def run(self) -> ExitCode:
                        self.renderer.finish(result)
                        self.renderer.reconcile((result,))
                        run_errors = (
                            ("artifact collection failed",)
                            if batch_exit == ExitCode.FAILED else ())
                        self.summary = RunSummary(
                            batch_exit, batch_exit.name, 100.0, 101.0,
                            (result,), run_errors, 0, 0,
                            1 if batch_exit == ExitCode.LEFTOVERS else 0,
                        )
                        return batch_exit

                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory) / "run"
                    root.mkdir()
                    coordinator = object.__new__(Coordinator)
                    coordinator._prepared_fixtures = None
                    coordinator.paths = SimpleNamespace(root=root)
                    coordinator.runner_dir = Path(directory)
                    coordinator.state = CoordinatorState(
                        100.0, "run", "Running",
                        {device.serial: DeviceProgress(device, 1)}, 1,
                    )
                    coordinator._lock = threading.RLock()
                    coordinator._cancelled = threading.Event()
                    coordinator._active_runners = {}
                    coordinator._device_summaries = {device.serial: []}
                    coordinator._device_isolation = set()
                    coordinator.view = SimpleNamespace(changed=lambda: None)
                    coordinator._base_config = lambda *_args, **_kwargs: object()

                    with patch("mjpdf_runner.coordinator.Runner", NonCleanRunner):
                        coordinator._run_device_safe(device, (unit,))
                    coordinator._finish()

                progress = coordinator.state.devices[device.serial]
                self.assertEqual(batch_exit, progress.exit_code)
                self.assertEqual(batch_exit, coordinator.state.exit_code)
                self.assertEqual(expected_verdict, coordinator.state.verdict)
                self.assertEqual(1, progress.completed)
                self.assertEqual(0, progress.not_run)
                self.assertEqual(
                    1, sum(item.result.status is status
                           for item in coordinator.state.results))
                if batch_exit == ExitCode.FAILED:
                    self.assertIn(
                        "artifact collection failed", coordinator.state.errors[0])

    def test_registration_rechecks_signal_that_arrives_during_runner_construction(
            self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        test = TestSpec(
            "example.CancelTest#neverStarts", "example.CancelTest", "neverStarts",
            "runner", "runner", False, False, False,
            "example.CancelTest#neverStarts",
        )
        unit = TestUnit(test.atomic_key, "runner", test.selector, (test,),
                        False, False, None)
        events: list[str] = []

        class RacingRunner:
            scenario_skipped = False
            scenario_skip_reason = None

            def __init__(self, _config: object, paths: object, _renderer: WorkerRenderer,
                         _scenario: object) -> None:
                self.state = RunState(100.0, paths.root)
                self.summary = None
                coordinator.interrupt(signal.SIGINT)
                events.append("constructed")

            def interrupt(self, signum: int) -> None:
                events.append(f"interrupt-{signum}")

            def run(self) -> ExitCode:
                events.append("run")
                self.summary = RunSummary(
                    ExitCode.INTERRUPTED, "INTERRUPTED", 100.0, 101.0,
                    (), (), 0, 0, 0,
                )
                return ExitCode.INTERRUPTED

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.runner_dir = Path(directory)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, 1)}, 1,
            )
            coordinator._lock = threading.RLock()
            coordinator._cancelled = threading.Event()
            coordinator._cancel_signal = None
            coordinator._active_build = None
            coordinator._active_runners = {}
            coordinator._device_summaries = {device.serial: []}
            coordinator._device_isolation = set()
            coordinator.view = SimpleNamespace(changed=lambda: None)
            coordinator._base_config = lambda *_args, **_kwargs: object()

            with patch("mjpdf_runner.coordinator.Runner", RacingRunner):
                coordinator._run_device_safe(device, (unit,))

        self.assertEqual(
            ["constructed", f"interrupt-{signal.SIGINT}", "run"], events)
        self.assertEqual(1, coordinator.state.devices[device.serial].not_run)

    def test_sigterm_is_143_but_recorded_isolation_has_precedence(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        for isolated, expected_code, expected_verdict in (
                (False, ExitCode.TERMINATED, "TERMINATED"),
                (True, ExitCode.DEVICE_OR_PIN, "DEVICE/PIN FAILURE")):
            with self.subTest(isolated=isolated), tempfile.TemporaryDirectory() as directory:
                root = Path(directory) / "run"
                root.mkdir()
                coordinator = object.__new__(Coordinator)
                coordinator._prepared_fixtures = None
                coordinator.paths = SimpleNamespace(root=root)
                coordinator.state = CoordinatorState(
                    100.0, "run", "Running",
                    {device.serial: DeviceProgress(device, 0)}, 0,
                )
                coordinator._cancelled = threading.Event()
                coordinator._cancelled.set()
                coordinator._cancel_signal = signal.SIGTERM
                coordinator._device_isolation = (
                    {device.serial} if isolated else set())

                coordinator._finish()

                self.assertEqual(expected_code, coordinator.state.exit_code)
                self.assertEqual(expected_verdict, coordinator.state.verdict)

    def test_hostile_timers_remain_bound_to_their_own_runner(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        test = TestSpec(
            "example.HostileTest#runs", "example.HostileTest", "runs",
            "runner", "runner", False, True, False, "test:hostile",
        )
        unit = TestUnit("test:hostile", "runner", test.selector, (test,),
                        False, True, None)
        runners: list[object] = []
        callbacks: list[object] = []

        class TimedRunner:
            scenario_skipped = False
            scenario_skip_reason = None

            def __init__(self, _config: object, paths: object, renderer: WorkerRenderer,
                         _scenario: object) -> None:
                self.index = len(runners)
                self.renderer = renderer
                self.state = RunState(100.0, paths.root)
                self.summary = None
                self.interrupts: list[int] = []
                runners.append(self)

            def run(self) -> ExitCode:
                result = TestResult(
                    TestKey(test.class_name, f"{test.method}[{self.index}]"),
                    None, 0, TestStatus.PASS, 1.0)
                self.renderer.finish(result)
                self.renderer.reconcile((result,))
                self.summary = RunSummary(
                    ExitCode.CLEAN, "CLEAN", 100.0, 101.0,
                    (result,), (), 0, 0, 0,
                )
                return ExitCode.CLEAN

            def interrupt(self, signum: int) -> None:
                self.interrupts.append(signum)

        class DeferredTimer:
            def __init__(self, _seconds: float, callback: object) -> None:
                callbacks.append(callback)

            def start(self) -> None:
                pass

            def cancel(self) -> None:
                pass

            def join(self) -> None:
                pass

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.runner_dir = Path(directory)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, 2)}, 2,
            )
            coordinator._lock = threading.RLock()
            coordinator._cancelled = threading.Event()
            coordinator._active_runners = {}
            coordinator._device_summaries = {device.serial: []}
            coordinator._device_isolation = set()
            coordinator.view = SimpleNamespace(changed=lambda: None)
            coordinator._base_config = lambda *_args, **_kwargs: object()
            coordinator._batches = lambda _units: (
                Batch("hostile-1", (unit,), hostile=True),
                Batch("hostile-2", (unit,), hostile=True),
            )

            with (patch("mjpdf_runner.coordinator.Runner", TimedRunner),
                  patch("mjpdf_runner.coordinator.threading.Timer", DeferredTimer)):
                coordinator._run_device_safe(device, (unit,))
            for callback in callbacks:
                callback()

        self.assertEqual(2, len(runners))
        self.assertEqual(
            [[signal.SIGTERM], [signal.SIGTERM]],
            [runner.interrupts for runner in runners],
        )

    def test_build_cancellation_preserves_sigint_and_sigterm_without_gradle_error(
            self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)

        class BuildFixtures:
            def __init__(self, *_args: object) -> None:
                pass

            def check_asset_drift(self) -> None:
                pass

        for signum, expected_exit in (
                (signal.SIGINT, ExitCode.INTERRUPTED),
                (signal.SIGTERM, ExitCode.TERMINATED)):
            with self.subTest(signum=signum), tempfile.TemporaryDirectory() as directory:
                terminations: list[int] = []

                class BuildCommands:
                    def __init__(self, *_args: object) -> None:
                        pass

                    def terminate_active(self, received: int) -> None:
                        terminations.append(received)

                    def run(self, _spec: object) -> None:
                        coordinator.interrupt(signum)
                        raise CommandFailure(CompletedCommand(
                            ("gradlew",), -signum, "terminated"))

                root = Path(directory) / "run"
                root.mkdir()
                (root / "logs").mkdir()
                coordinator = object.__new__(Coordinator)
                coordinator._prepared_fixtures = None
                coordinator.paths = SimpleNamespace(
                    root=root, logs=root / "logs")
                coordinator.repo_root = Path(directory)
                coordinator.selection = SimpleNamespace(devices=(device,))
                coordinator.state = CoordinatorState(
                    100.0, "run", "Preparing",
                    {device.serial: DeviceProgress(device, 0)}, 0,
                )
                coordinator.view = SimpleNamespace(changed=lambda: None)
                coordinator._lock = threading.RLock()
                coordinator._cancelled = threading.Event()
                coordinator._cancel_signal = None
                coordinator._active_build = None
                coordinator._active_runners = {}
                coordinator._device_isolation = set()
                coordinator._base_config = lambda *_args, **_kwargs: object()

                with (patch("mjpdf_runner.coordinator.CommandRunner", BuildCommands),
                      patch("mjpdf_runner.coordinator.Fixtures", BuildFixtures)):
                    coordinator._build_once()
                coordinator._finish()
                summary = json.loads((root / "summary.json").read_text())

                self.assertEqual([signum], terminations)
                self.assertIsNone(coordinator._active_build)
                self.assertEqual(expected_exit, coordinator.state.exit_code)
                self.assertEqual([], coordinator.state.errors)
                self.assertEqual([], summary["errors"])

    def test_exception_after_cancellation_keeps_device_fallback_label_exact(
            self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        for signum, expected_exit, expected_label in (
                (signal.SIGINT, ExitCode.INTERRUPTED, "INTERRUPTED"),
                (signal.SIGTERM, ExitCode.TERMINATED, "TERMINATED")):
            with self.subTest(signum=signum), tempfile.TemporaryDirectory() as directory:
                root = Path(directory) / "run"
                root.mkdir()
                coordinator = object.__new__(Coordinator)
                coordinator._prepared_fixtures = None
                coordinator.paths = SimpleNamespace(root=root)
                coordinator.state = CoordinatorState(
                    100.0, "run", "Preparing",
                    {device.serial: DeviceProgress(device, 0)}, 0,
                )
                coordinator.selection = SimpleNamespace(devices=(device,))
                coordinator._lock = threading.RLock()
                coordinator._cancelled = threading.Event()
                coordinator._cancel_signal = None
                coordinator._active_build = None
                coordinator._active_runners = {}
                coordinator._device_isolation = set()
                coordinator.stream = StringIO()
                coordinator.view = SimpleNamespace(
                    start=lambda: None,
                    stop_and_erase=lambda: None,
                    print_final=lambda: None,
                )
                coordinator._write_manifest = lambda: None

                def fail_after_signal() -> None:
                    coordinator.interrupt(signum)
                    raise RuntimeError("late cancellation failure")

                coordinator._build_once = fail_after_signal

                code = coordinator.run()
                device_summary = json.loads(
                    (root / "devices" / device.serial / "summary.json").read_text())

                progress = coordinator.state.devices[device.serial]
                self.assertEqual(expected_exit, code)
                self.assertEqual(expected_exit, progress.exit_code)
                self.assertEqual(expected_label, progress.verdict)
                self.assertEqual(expected_label, progress.step)
                self.assertEqual(expected_label, device_summary["verdict"])
                self.assertEqual(int(expected_exit), device_summary["exitCode"])

    def test_execution_counts_separate_actual_outcomes_from_not_run(self) -> None:
        results = [
            TestResult(TestKey("example.Test", "passes"), None, 0,
                       TestStatus.PASS, 1.0),
            TestResult(TestKey("example.Test", "skips"), None, -3,
                       TestStatus.SKIP, 2.0),
        ]

        counts = ExecutionCounts.from_results(results, planned=5)

        self.assertEqual((1, 0, 1, 3, 5, 2), (
            counts.passed, counts.failed, counts.skipped, counts.not_run,
            counts.total, counts.reported,
        ))

        self.assertEqual(
            counts.total,
            counts.passed + counts.failed + counts.skipped + counts.not_run,
        )

        parameterized = [
            TestResult(TestKey("example.ParameterizedTest", f"case[{index}]"), None, 0,
                       TestStatus.PASS, 1.0)
            for index in range(6)
        ]
        expanded = ExecutionCounts.from_results(parameterized, planned=1, not_run=0)
        self.assertEqual((6, 0, 6), (
            expanded.reported, expanded.not_run, expanded.total))
        self.assertEqual(
            expanded.total,
            expanded.passed + expanded.failed + expanded.skipped + expanded.not_run,
        )

    def test_finish_summary_reports_only_actual_results_and_not_run(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        actual = [
            DeviceTestResult(
                device.serial,
                TestResult(TestKey("example.Test", "passes"), None, 0,
                           TestStatus.PASS, 1.5),
            ),
            DeviceTestResult(
                device.serial,
                TestResult(TestKey("example.Test", "skips"), None, -3,
                           TestStatus.SKIP, 0.5),
            ),
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, 5)}, 5, actual,
            )
            coordinator._cancelled = threading.Event()
            coordinator._device_isolation = set()
            coordinator.state.errors.append("coordinator operation failed")

            with patch("mjpdf_runner.coordinator.time.time", return_value=110.0):
                coordinator._finish()

            summary = json.loads((root / "summary.json").read_text())
            device_summary = json.loads(
                (root / "devices" / device.serial / "summary.json").read_text())
            result_lines = (root / "results.txt").read_text().splitlines()
            synthetic_junit_exists = (
                root / "junit" / "TEST-mjpdf-runner.xml").exists()

        self.assertEqual(2, summary["reportedResults"])
        self.assertEqual(3, summary["notRun"])
        self.assertEqual(2, device_summary["reportedResults"])
        self.assertEqual(3, device_summary["notRun"])
        self.assertEqual(2, len(result_lines))
        self.assertFalse(any("ERROR" in line for line in result_lines))
        self.assertFalse(synthetic_junit_exists)

        self.assertIn("durationSeconds", device_summary)
        self.assertIn("timings", device_summary)

    def test_unexpected_device_exception_leaves_assignments_not_run(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "run"
            root.mkdir()
            coordinator = object.__new__(Coordinator)
            coordinator._prepared_fixtures = None
            coordinator.paths = SimpleNamespace(root=root)
            coordinator.state = CoordinatorState(
                100.0, "run", "Running",
                {device.serial: DeviceProgress(device, 4)}, 4,
            )
            coordinator._lock = threading.RLock()
            coordinator.view = SimpleNamespace(changed=lambda: None)
            coordinator._run_device = lambda _device, _units: (_ for _ in ()).throw(
                RuntimeError("worker exploded"))

            coordinator._run_device_safe(device, ())

            summary = json.loads(
                (root / "devices" / device.serial / "summary.json").read_text())

        progress = coordinator.state.devices[device.serial]
        self.assertEqual([], coordinator.state.results)
        self.assertEqual(0, progress.completed)
        self.assertEqual(4, progress.not_run)
        self.assertEqual(0, summary["reportedResults"])
        self.assertEqual(4, summary["notRun"])
        self.assertEqual(["emulator-5554: worker exploded"], coordinator.state.errors)
        self.assertIn("durationSeconds", summary)
        self.assertIn("timings", summary)
        self.assertEqual(
            {"afterStepsSeconds", "afterTestsSeconds", "beforeStepsSeconds",
             "beforeTestsSeconds", "testSeconds"},
            set(summary["timings"]),
        )

    def test_terminal_detail_prints_only_first_twenty_actual_failures(self) -> None:
        device = self.device("emulator-5554", 37, RendererLane.HARDWARE)
        failures = [
            DeviceTestResult(
                device.serial,
                TestResult(TestKey("example.Test", f"fails{index}"), None, -2,
                           TestStatus.FAIL, 1.0),
            )
            for index in range(22)
        ]
        quiet = [
            DeviceTestResult(
                device.serial,
                TestResult(TestKey("example.Test", "passes"), None, 0,
                           TestStatus.PASS, 1.0),
            ),
            DeviceTestResult(
                device.serial,
                TestResult(TestKey("example.Test", "skips"), None, -3,
                           TestStatus.SKIP, 1.0),
            ),
        ]
        coordinator = object.__new__(Coordinator)
        coordinator._prepared_fixtures = None
        coordinator.state = CoordinatorState(
            100.0, "run", "Complete",
            {device.serial: DeviceProgress(device, 30)}, 30, failures + quiet,
        )
        coordinator.paths = SimpleNamespace(root=Path("/tmp/exact-run"))
        coordinator.stream = StringIO()

        coordinator._print_non_pass_results()

        lines = coordinator.stream.getvalue().splitlines()
        self.assertEqual(21, len(lines))
        self.assertTrue(all("FAIL" in line for line in lines[:20]))
        self.assertNotIn("passes", coordinator.stream.getvalue())
        self.assertNotIn("skips", coordinator.stream.getvalue())
        self.assertEqual(
            "2 additional non-pass result(s) omitted; full results: "
            "/tmp/exact-run/results.txt",
            lines[-1],
        )

        coordinator.state.results = quiet
        coordinator.stream = StringIO()
        coordinator._print_non_pass_results()
        self.assertEqual("", coordinator.stream.getvalue())


if __name__ == "__main__":
    unittest.main()
