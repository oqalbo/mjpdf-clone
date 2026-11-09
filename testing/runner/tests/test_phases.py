from __future__ import annotations

import sys
import tempfile
import unittest
import itertools
from unittest.mock import patch
from pathlib import Path
import xml.etree.ElementTree as ET
from types import SimpleNamespace
from typing import Any, cast

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.model import (CompletedCommand, PhaseAction, PhaseScenario, TestKey,
                                TestResult, TestStatus)
from mjpdf_runner.phases import PhaseRunner, parse_scenario


class FakeCommands:
    def __init__(self, outputs: dict[str, str] | None = None,
                 events: list[tuple[str, object]] | None = None) -> None:
        self.outputs = outputs or {}
        self.events = events
        self.specs = []
        self.records: list[str] = []

    def run(self, spec, check=True):
        self.specs.append(spec)
        if self.events is not None:
            self.events.append(("command", spec.name))
        return CompletedCommand(spec.argv, 0, self.outputs.get(spec.name, ""))

    def record(self, message: str) -> None:
        self.records.append(message)


class FakeStorage:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.deleted: list[str] = []

    def delete_media_rows_under(self, path: str) -> None:
        self.deleted.append(path)


class PhaseScenarioTests(unittest.TestCase):
    def test_ui_text_actions_preserve_text_with_spaces(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "ABOUT-16.phases"
            path.write_text(
                "describe process restoration\n"
                "phase example.PhaseOne\n"
                "assert-ui-text Do more than read\n"
                "assert-ui-any-text What's New || Library\n"
                "click-ui-text Get started\n"
                "await-app-background\n"
                "record-app-task\n"
                "restore-app-task\n",
                encoding="utf-8",
            )
            scenario = parse_scenario(path, "ABOUT-16")

        self.assertEqual(
            [(action.kind, action.value) for action in scenario.actions],
            [
                ("phase", "example.PhaseOne"),
                ("assert-ui-text", "Do more than read"),
                ("assert-ui-any-text", "What's New || Library"),
                ("click-ui-text", "Get started"),
                ("await-app-background", ""),
                ("record-app-task", ""),
                ("restore-app-task", ""),
            ],
        )

    def test_ui_marker_is_shell_safe_and_deterministic(self) -> None:
        self.assertEqual(
            PhaseRunner._ui_marker("What's New"),
            "/storage/emulated/0/Documents/mjpdf-phase-state/ui-What-s-New",
        )

    def test_app_task_parser_accepts_modern_root_task_output(self) -> None:
        stacks = (
            "RootTask id=944 bounds=[0,0][1080,2400]\n"
            "  taskId=944: com.example/com.example.Launcher visible=true\n"
        )
        self.assertEqual(
            PhaseRunner._app_task_from_stacks(stacks, "com.example"),
            ("944", "944"),
        )

    def test_app_task_parser_accepts_api28_stack_output(self) -> None:
        stacks = (
            "Stack id=665 bounds=[0,0][1080,2400]\n"
            "  taskId=1268: com.example/com.example.Launcher visible=true\n"
        )
        self.assertEqual(
            PhaseRunner._app_task_from_stacks(stacks, "com.example"),
            ("1268", "665"),
        )

    def test_click_lookup_reuses_the_preceding_assertion_hierarchy(self) -> None:
        runner = object.__new__(PhaseRunner)
        runner.ui_nodes = [
            ET.fromstring('<node text="Do more than read"/>'),
            ET.fromstring('<node text="Next" bounds="[1,2][3,4]"/>'),
        ]

        node = runner._find_ui_text("Next", use_cached=True)

        self.assertEqual("[1,2][3,4]", node.get("bounds"))

    def test_process_boundary_and_cleanup_directives_parse_explicitly(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "PROCESS.phases"
            path.write_text(
                "phase example.PhaseOne\n"
                "kill-app\n"
                "launch-task-after-start\n"
                "phase example.PhaseTwo\n"
                "launch-view-after-start file:///sdcard/Download/normal.pdf\n"
                "phase example.PhaseThree\n"
                "cleanup-device-shell sm mount volume-id\n",
                encoding="utf-8",
            )

            scenario = parse_scenario(path, "PROCESS")

        self.assertEqual(
            [
                ("phase", "example.PhaseOne"),
                ("kill", ""),
                ("task-after-start", ""),
                ("phase", "example.PhaseTwo"),
                ("view-after-start", "file:///sdcard/Download/normal.pdf"),
                ("phase", "example.PhaseThree"),
                ("cleanup-device-shell", "sm mount volume-id"),
            ],
            [(action.kind, action.value) for action in scenario.actions],
        )

    def test_scoped_process_scenarios_defer_their_phase_two_launch(self) -> None:
        expected = {
            "HIST-36": "task-after-start",
            "PRIV-19": "task-after-start",
            "PRIV-31": "task-after-start",
            "RES-53": "view-after-start",
            "RES-54": "view-after-start",
        }

        for name, launch_kind in expected.items():
            with self.subTest(name=name):
                scenario = parse_scenario(RUNNER_DIR / "phases" / f"{name}.phases", name)
                kinds = [action.kind for action in scenario.actions]
                self.assertEqual(1, kinds.count(launch_kind))
                self.assertLess(kinds.index("kill"), kinds.index(launch_kind))
                self.assertLess(kinds.index(launch_kind), kinds.index("phase", 1))

    def test_secondary_volume_parser_maps_a_reported_uuid_to_its_mount_path(self) -> None:
        volumes = PhaseRunner._secondary_volumes(
            "private mounted null\n"
            "emulated;0 mounted null\n"
            "public:253,80 mounted 0000-0000\n"
            "public:253,81 unmounted AAAA-BBBB\n"
        )

        self.assertEqual(
            [("public:253,80", "/storage/0000-0000")],
            volumes,
        )

    def test_process_boundary_preserves_phase_one_and_relaunches_after_phase_two_starts(
            self) -> None:
        scenario = PhaseScenario(
            "PROCESS",
            "process boundary",
            (),
            (
                PhaseAction("phase", "example.PhaseOne"),
                PhaseAction("kill"),
                PhaseAction("task-after-start"),
                PhaseAction("phase", "example.PhaseTwo"),
            ),
        )
        events: list[tuple[str, object]] = []

        with tempfile.TemporaryDirectory() as temporary:
            commands = FakeCommands(events=events)
            runner = PhaseRunner(
                cast(Any, SimpleNamespace(
                    adb="adb", serial="serial", package="example.app", dry_run=False)),
                cast(Any, commands),
                cast(Any, FakeStorage(Path(temporary))),
                scenario,
                lambda args, phase, after_started=None: self._passing_phase(
                    events, args, phase, after_started
                ),
                lambda _message: None,
                lambda _message: None,
            )
            runner._kill = lambda: events.append(("kill", None))

            skipped, reason, actions, abort_reason = runner.run()

        phase_one_args = cast(
            list[str], next(value for kind, value in events if kind == "phase-1-args"))
        phase_two_started = events.index(("phase-2-started", None))
        launch = events.index(("command", "phase-task-after-start"))
        instrument_two_finished = events.index(("phase-2-finished", None))
        self.assertFalse(skipped)
        self.assertIsNone(reason)
        self.assertIsNone(abort_reason)
        self.assertIn(
            ["-e", "waitForActivitiesToComplete", "false"],
            [phase_one_args[index:index + 3] for index in range(len(phase_one_args) - 2)],
        )
        self.assertNotIn("clearPackageData", phase_one_args)
        self.assertLess(phase_two_started, instrument_two_finished)
        self.assertLess(phase_two_started, launch)
        self.assertLess(launch, instrument_two_finished)
        self.assertEqual(
            ["phase", "kill", "task-after-start", "phase"],
            [action["kind"] for action in actions],
        )

    def test_process_boundary_without_a_task_kills_the_process_without_recording_a_task(self) -> None:
        scenario = PhaseScenario(
            "PROCESS",
            "headless process boundary",
            (),
            (
                PhaseAction("phase", "example.PhaseOne"),
                PhaseAction("kill"),
                PhaseAction("phase", "example.PhaseTwo"),
            ),
        )
        with tempfile.TemporaryDirectory() as temporary:
            commands = FakeCommands()
            runner = PhaseRunner(
                cast(Any, SimpleNamespace(
                    adb="adb", serial="serial", package="example.app", dry_run=False)),
                cast(Any, commands),
                cast(Any, FakeStorage(Path(temporary))),
                scenario,
                lambda *_args: [],
                lambda _message: None,
                lambda _message: None,
            )
            pids = iter(("123", ""))
            runner._pid = lambda: next(pids)
            runner._record_app_task = lambda: self.fail("headless scenario tried to record a task")

            runner._kill()

        self.assertTrue(any(spec.name == "phase-am-kill" for spec in commands.specs))

    def test_kill_uses_available_privilege_and_requires_the_original_pid_to_exit(self) -> None:
        for shell_uid, su_uid, permitted in (("0", "", True), ("2000", "0", True),
                                              ("2000", "denied", True), ("2000", "denied", False)):
            with self.subTest(shell_uid=shell_uid, su_uid=su_uid, permitted=permitted), \
                    tempfile.TemporaryDirectory() as temporary:
                scenario = PhaseScenario("KILL", "process death", (), (PhaseAction("task-after-start"),))
                prefix = ("shell",) if shell_uid == "0" else (
                    ("shell", "su", "0") if su_uid == "0" else ("shell", "run-as", "example.app"))
                live = True
                calls = []
                commands = FakeCommands()
                runner = PhaseRunner(
                    cast(Any, SimpleNamespace(adb="adb", serial="serial", package="example.app", dry_run=False)),
                    cast(Any, commands), cast(Any, FakeStorage(Path(temporary))), scenario,
                    lambda *_args: [], lambda _message: None, lambda _message: None)

                def command(name, *args, **kwargs):
                    nonlocal live
                    calls.append((name, args))
                    if name == "kill-shell-uid":
                        return shell_uid
                    if name == "kill-su-uid":
                        return su_uid
                    if name == "kill9-123" and args == (*prefix, "kill", "-9", "123") and permitted:
                        live = False
                    return "" if not live else "Permission denied"

                def record_task():
                    runner.app_task = ("17", "example.app/.Reader")

                runner.run_command = command
                runner._pid = lambda: "123" if live else ""
                runner._record_app_task = record_task
                with patch("mjpdf_runner.phases.time.monotonic", side_effect=itertools.count(0, 0.25)), \
                        patch("mjpdf_runner.phases.time.sleep"):
                    if permitted:
                        runner._kill()
                        self.assertFalse(live)
                        self.assertIn("pid=123", (Path(temporary) / "kills.txt").read_text())
                    else:
                        with self.assertRaisesRegex(RuntimeError, "still alive"):
                            runner._kill()
                        self.assertFalse((Path(temporary) / "kills.txt").exists())
                self.assertEqual(("17", "example.app/.Reader"), runner.app_task)
                self.assertEqual([(*prefix, "kill", "-9", "123")],
                                 [args for name, args in calls if name == "kill9-123"])
                self.assertFalse(any("force-stop" in args for _, args in calls))

    def test_privileged_kill_rechecks_pid_ownership_before_signaling(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner = PhaseRunner(
                cast(Any, SimpleNamespace(adb="adb", serial="serial", package="example.app", dry_run=False)),
                cast(Any, FakeCommands()), cast(Any, FakeStorage(Path(temporary))),
                PhaseScenario("KILL", "process death", (), ()),
                lambda *_args: [], lambda _message: None, lambda _message: None)
            current_pid = "123"

            def command(name, *args, **kwargs):
                nonlocal current_pid
                self.assertFalse(name.startswith("kill9-"), "the old PID no longer belongs to the target")
                if name == "kill-shell-uid":
                    current_pid = "456"
                    return "0"
                return ""

            runner.run_command = command
            runner._pid = lambda: current_pid
            with patch("mjpdf_runner.phases.time.monotonic", side_effect=itertools.count(0, 0.25)), \
                    patch("mjpdf_runner.phases.time.sleep"):
                runner._kill()
            self.assertIn("pid=123", (Path(temporary) / "kills.txt").read_text())

    def test_failed_prerequisite_phase_aborts_kill_and_later_phase(self) -> None:
        scenario = PhaseScenario(
            "RES-54",
            "failed setup",
            (),
            (
                PhaseAction("phase", "example.PhaseOne"),
                PhaseAction("kill"),
                PhaseAction("view-after-start", "file:///sdcard/Download/large.pdf"),
                PhaseAction("phase", "example.PhaseTwo"),
            ),
        )
        phases: list[int] = []

        def instrument(_args, phase, _after_started=None):
            phases.append(phase)
            return [TestResult(
                TestKey("example.Test", f"phase{phase}"), phase, -2,
                TestStatus.FAIL, 0.1, "setup failed",
            )]

        with tempfile.TemporaryDirectory() as temporary:
            commands = FakeCommands()
            runner = PhaseRunner(
                cast(Any, SimpleNamespace(
                    adb="adb", serial="serial", package="example.app", dry_run=False)),
                cast(Any, commands),
                cast(Any, FakeStorage(Path(temporary))),
                scenario,
                instrument,
                lambda _message: None,
                lambda _message: None,
            )
            runner._kill = lambda: self.fail("kill ran after the prerequisite phase failed")

            skipped, reason, actions, abort_reason = runner.run()

        self.assertFalse(skipped)
        self.assertIsNone(reason)
        self.assertEqual([1], phases)
        self.assertEqual(["phase"], [action["kind"] for action in actions])
        self.assertIn("phase 1", abort_reason or "")
        self.assertIn("FAIL", abort_reason or "")
        self.assertTrue(any(spec.name == "phase-abort-force-stop" for spec in commands.specs))

    def test_after_start_view_is_restricted_to_the_target_package(self) -> None:
        scenario = PhaseScenario(
            "RES-53",
            "direct view",
            (),
            (PhaseAction("phase", "example.Phase"),),
        )
        with tempfile.TemporaryDirectory() as temporary:
            commands = FakeCommands()
            runner = PhaseRunner(
                cast(Any, SimpleNamespace(
                    adb="adb", serial="serial", package="example.app", dry_run=False)),
                cast(Any, commands),
                cast(Any, FakeStorage(Path(temporary))),
                scenario,
                lambda *_args: [],
                lambda _message: None,
                lambda _message: None,
            )

            runner._run_action(PhaseAction(
                "view-after-start", "file:///sdcard/Download/normal.pdf"))

        launch = commands.specs[-1].argv
        self.assertEqual("example.app", launch[launch.index("-p") + 1])
        self.assertEqual(
            "file:///sdcard/Download/normal.pdf",
            launch[launch.index("-d") + 1],
        )

    def test_cleanup_device_shell_runs_after_a_terminal_phase_failure(self) -> None:
        scenario = PhaseScenario(
            "CLEANUP",
            "device shell cleanup",
            (),
            (
                PhaseAction("phase", "example.PhaseOne"),
                PhaseAction("device-shell", "sm unmount volume"),
                PhaseAction("cleanup-device-shell", "sm mount volume"),
                PhaseAction("phase", "example.PhaseTwo"),
            ),
        )

        def instrument(_args, phase, _after_started=None):
            status = TestStatus.PASS if phase == 1 else TestStatus.FAIL
            return [TestResult(
                TestKey("example.Test", f"phase{phase}"), phase,
                0 if status is TestStatus.PASS else -2, status, 0.1,
            )]

        with tempfile.TemporaryDirectory() as temporary:
            commands = FakeCommands()
            runner = PhaseRunner(
                cast(Any, SimpleNamespace(
                    adb="adb", serial="serial", package="example.app", dry_run=False)),
                cast(Any, commands),
                cast(Any, FakeStorage(Path(temporary))),
                scenario,
                instrument,
                lambda _message: None,
                lambda _message: None,
            )

            _skipped, _reason, actions, abort_reason = runner.run()

        self.assertIn("phase 2", abort_reason or "")
        self.assertEqual(
            ["phase", "device-shell", "phase", "cleanup-device-shell"],
            [action["kind"] for action in actions],
        )
        cleanup = next(spec for spec in commands.specs
                       if spec.name == "phase-cleanup-device-shell")
        self.assertEqual("sm mount volume", cleanup.argv[-1])

    @staticmethod
    def _passing_phase(events, args, phase, after_started):
        events.append((f"phase-{phase}-args", list(args)))
        events.append((f"phase-{phase}-started", None))
        if after_started is not None:
            after_started()
        events.append((f"phase-{phase}-finished", None))
        return [TestResult(
            TestKey("example.Test", f"phase{phase}"), phase, 0,
            TestStatus.PASS, 0.1,
        )]


if __name__ == "__main__":
    unittest.main()
