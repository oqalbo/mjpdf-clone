from __future__ import annotations

from dataclasses import replace
import io
import json
import signal
import sys
import tarfile
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

REPLAY_ARCHIVE = Path(__file__).resolve().parent / "run-replays.tar.gz"
_replay_directory: tempfile.TemporaryDirectory | None = None


def replay_runs() -> Path:
    global _replay_directory
    if _replay_directory is None:
        _replay_directory = tempfile.TemporaryDirectory()
        with tarfile.open(REPLAY_ARCHIVE) as archive:
            archive.extractall(_replay_directory.name)
    return Path(_replay_directory.name)


from mjpdf_runner.model import (CommandSpec, CompletedCommand, ExitCode, RendererLane,
                                 RunnerConfig, RunPaths, TestKey, TestResult, TestStatus,
                                 PhaseAction, PhaseScenario)
from mjpdf_runner.pins import PinError, PinManager
from mjpdf_runner.runner import APP_APK, TEST_APK, Runner, RunnerFailure
from mjpdf_runner.process import CommandFailure, CommandObserver, CommandRunner, CommandTimeout, HostResourceMonitor


class FakeCommands:
    def __init__(self) -> None:
        self.specs = []
        self.records: list[str] = []
        self.terminated: list[int] = []

    def run(self, spec, on_line=None, check=True):
        self.specs.append(spec)
        output = "34" if "ro.build.version.sdk" in spec.argv else ""
        return CompletedCommand(spec.argv, 0, output)

    def record(self, message: str) -> None:
        self.records.append(message)

    def terminate_active(self, sig: int = signal.SIGTERM) -> None:
        self.terminated.append(sig)

    def start_observer(self, spec):
        self.specs.append(spec)
        return spec

    def wait_observer(self, observer, marker, timeout=5.0):
        pass

    def stop_observer(self, observer):
        return CompletedCommand(observer.argv, 0, "")



class FakeRenderer:
    def __init__(self) -> None:
        self.finished = []
        self.finish_calls = []


    def start(self, state) -> None:
        pass

    def update(self, state) -> None:
        pass

    def notice(self, message, warning=False) -> None:
        pass

    def finish(self, result) -> None:
        self.finish_calls.append(result)
        self.finished.append(result)

    def reconcile(self, results) -> None:
        self.finished = list(results)


    def close(self) -> None:
        pass


class RunnerLifecycleTests(unittest.TestCase):
    def test_system_error_dialog_pin_restores_the_original_setting(self):
        for prior in (None, "0", "1"):
            with self.subTest(prior=prior), tempfile.TemporaryDirectory() as temporary:
                runner, _ = self.make_runner(temporary)

                class SettingsCommands(FakeCommands):
                    def __init__(self):
                        super().__init__()
                        self.values = {} if prior is None else {("global", "hide_error_dialogs"): prior}

                    def run(self, spec, on_line=None, check=True):
                        self.specs.append(spec)
                        args = spec.argv[3:]
                        output = ""
                        if args[:2] == ("shell", "settings"):
                            action, namespace, key = args[2:5]
                            if action == "get":
                                output = self.values.get((namespace, key), "null")
                            elif action == "put":
                                self.values[(namespace, key)] = args[5]
                            elif action == "delete":
                                self.values.pop((namespace, key), None)
                        return CompletedCommand(spec.argv, 0, output)

                commands = SettingsCommands()
                pins = PinManager(runner.config, commands, runner.paths.root, lambda _: None, lambda _: None)
                pins._misc_pins()
                self.assertEqual("1", commands.values.get(("global", "hide_error_dialogs")))
                pins.restore()
                self.assertEqual(prior, commands.values.get(("global", "hide_error_dialogs")))

    def test_legacy_install_and_signature_recovery_use_staged_apks(self):
        for api in (28, 35):
            with self.subTest(api=api), tempfile.TemporaryDirectory() as temporary:
                runner, _ = self.make_runner(temporary)
                class InstallCommands(FakeCommands):
                    def __init__(self):
                        super().__init__()
                        self.stopped = []
                    def run(self, spec, on_line=None, check=True):
                        self.specs.append(spec)
                        if spec.name == "install-api":
                            return CompletedCommand(spec.argv, 0, str(api))
                        if spec.name.startswith("install-app-debug"):
                            return CompletedCommand(spec.argv, 1, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
                        return CompletedCommand(spec.argv, 0, "Success")
                    def stop_observer(self, observer):
                        self.stopped.append(observer)
                        return super().stop_observer(observer)
                commands = InstallCommands()
                runner.commands = commands
                runner._apk_package = lambda _apk, package: package
                runner._install_apk(runner.config.test_apk, runner.config.test_package)
                installs = [spec for spec in commands.specs if spec.argv[3] == "install"]
                self.assertEqual(2, len(installs))
                self.assertTrue(all(("--no-streaming" in spec.argv) == (api == 28) for spec in installs))
                self.assertTrue(all(spec.timeout_seconds is None for spec in installs))
                self.assertEqual(1, len(commands.stopped))

    def test_install_timeout_keeps_diagnostics_and_does_not_retry(self):
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            class TimeoutCommands(FakeCommands):
                def __init__(self):
                    super().__init__()
                    self.stopped = []
                def run(self, spec, on_line=None, check=True):
                    self.specs.append(spec)
                    if spec.name == "install-api":
                        return CompletedCommand(spec.argv, 0, "28")
                    if spec.argv[3] == "install":
                        raise CommandTimeout(CompletedCommand(spec.argv, -9, ""), spec.name, 300)
                    return CompletedCommand(spec.argv, 0, "")
                def stop_observer(self, observer):
                    self.stopped.append(observer)
                    return super().stop_observer(observer)
            commands = TimeoutCommands()
            runner.commands = commands
            with self.assertRaises(CommandTimeout):
                runner._install_apk(runner.config.test_apk, runner.config.test_package)
            self.assertEqual(1, sum(spec.argv[3] == "install" for spec in commands.specs))
            self.assertEqual(1, len(commands.stopped))

    def test_command_timing_records_success_failure_timeout_and_nested_parent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "logs").mkdir()
            commands = CommandRunner(root / "runner.log", root / "logs", root / "raw.txt")
            child = CommandSpec((sys.executable, "-c", "print('child')"), "repeated")
            parent = CommandSpec((sys.executable, "-c", "print('parent')"), "repeated")
            result = commands.run(parent, on_line=lambda _line: commands.run(child))
            self.assertEqual("parent", result.output)
            commands.run(CommandSpec((sys.executable, "-c", "raise SystemExit(7)"), "failure"), check=False)
            with self.assertRaises(CommandFailure):
                commands.run(CommandSpec((sys.executable, "-c", "import time; time.sleep(1)"), "timeout", timeout_seconds=0.02))
            rows = [json.loads(line) for line in (root / "command-timings.jsonl").read_text().splitlines()]
            self.assertEqual(4, len({row["invocationId"] for row in rows}))
            self.assertEqual(rows[1]["invocationId"], rows[0]["parentInvocationId"])
            self.assertEqual(["success", "success", "failure", "timeout"], [row["outcome"] for row in rows])
            self.assertTrue(all(row["elapsedSeconds"] >= 0 for row in rows))
            self.assertEqual(7, rows[2]["returncode"])


    def test_diagnostic_timing_text_cannot_become_an_activity_isolation_event(self):
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10123}
            runner._active_instrumentation_packages = lambda name: set()
            metrics = '100.0 10 11 I MjPdfTestTiming: {"test":"ActivityTaskManager START u0 {cmp=com.false.app/.Main} from uid 10999"}'
            actual = "101.0 20 21 I ActivityTaskManager: START u0 {cmp=com.real.app/.Main} from uid 10999"
            packages, _lines, boundary = runner._execution_isolation(captured_evidence=metrics + "\n" + actual)
            self.assertEqual({"com.real.app"}, packages)
            self.assertEqual(101.0, boundary)

    def test_prepared_utilities_do_not_skip_per_batch_reinstallation(self):
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.config = replace(runner.config, utility_apks=(Path("orchestrator.apk"), Path("services.apk")))
            specs = []
            class Commands:
                def run(self, spec, check=True):
                    specs.append(spec)
                    output = "35" if spec.name == "orchestrator-api" else (
                        "package:androidx.test.orchestrator\npackage:androidx.test.services" if spec.name == "installed-packages" else "")
                    return CompletedCommand(spec.argv, 0, output)
            runner.commands = Commands()
            for _ in range(2):
                runner._install_orchestrator()
            self.assertEqual(4, sum(spec.name.startswith("uninstall-") for spec in specs))
            installs = [spec for spec in specs if spec.name.startswith("install-test-util-")]
            self.assertEqual(4, len(installs))
            self.assertTrue(all("--force-queryable" in spec.argv for spec in installs))
            self.assertFalse(any(spec.name == "resolve-orchestrator" for spec in specs))

    def make_runner(self, temporary: str) -> tuple[Runner, FakeCommands]:
        base = Path(temporary)
        repo = base / "repo"
        for relative in (APP_APK, TEST_APK):
            apk = repo / relative
            apk.parent.mkdir(parents=True, exist_ok=True)
            apk.write_bytes(b"apk")
        paths = RunPaths.create(base / "run")
        config = RunnerConfig(
            repo_root=repo,
            runner_dir=RUNNER_DIR,
            run_id="lifecycle",
            serial="fake-device",
            adb="adb",
            package="com.example.app",
            test_package="com.example.app.test",
            profile_size="1080x2400",
            profile_density="440",
            profile_locale="en-US",
            profile_timezone="UTC",
            logcat_buffer="64M",
            renderer_lane=RendererLane.HARDWARE,
            app_apk=repo / APP_APK,
            test_apk=repo / TEST_APK,
        )
        runner = Runner(config, paths, FakeRenderer(), None)
        commands = FakeCommands()
        runner.commands = commands
        runner.fixtures.commands = commands
        runner.storage.commands = commands
        runner.pins.commands = commands
        runner._require_device = lambda: None
        runner._build = lambda: None
        runner.pins.restore = lambda: None
        return runner, commands

    def assert_revoke_ran(self, commands: FakeCommands) -> None:
        revokes = [spec.argv for spec in commands.specs
                   if "MANAGE_EXTERNAL_STORAGE" in spec.argv and "deny" in spec.argv]
        self.assertEqual(1, len(revokes), commands.specs)

    def test_denied_storage_mode_disables_orchestrator_data_clearing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.config = replace(
                runner.config,
                passthrough=("-e", "storageAccess", "denied"),
            )

            args = runner._instrument_args()

        self.assertNotIn("clearPackageData", args)

    def test_granted_storage_mode_keeps_orchestrator_data_clearing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.config = replace(
                runner.config,
                passthrough=("-e", "storageAccess", "granted"),
            )

            args = runner._instrument_args()

        self.assertIn("clearPackageData", args)

    def test_orchestrator_failure_after_apk_grant_still_revokes_access(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, commands = self.make_runner(temporary)
            runner._install_orchestrator = lambda: (_ for _ in ()).throw(
                RunnerFailure(ExitCode.FAILED, "orchestrator failed"))

            code = runner.run()

            self.assertEqual(ExitCode.FAILED, code)
            self.assert_revoke_ran(commands)

    def test_pin_failure_after_apk_grant_still_revokes_access(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, commands = self.make_runner(temporary)
            runner._install_orchestrator = lambda: None
            runner.pins.apply = lambda: (_ for _ in ()).throw(PinError("pin failed"))

            code = runner.run()

            self.assertEqual(ExitCode.DEVICE_OR_PIN, code)
            self.assert_revoke_ran(commands)

    def test_stalled_phase_bootstrap_does_not_hold_the_fleet(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = CommandRunner(
                runner.paths.root / "runner.log", runner.paths.logs,
                runner.paths.root / "instrument-raw.txt")

            def fake_adb(*args: str) -> tuple[str, ...]:
                script = (
                    "import time; print('Starting: Intent', flush=True); time.sleep(1.5)"
                    if args[:3] == ("shell", "am", "start") else "print('2468')"
                )
                return (sys.executable, "-u", "-c", script)

            started = time.monotonic()
            with patch.object(runner, "_adb", side_effect=fake_adb), \
                    patch("mjpdf_runner.runner.PHASE_BOOTSTRAP_TIMEOUT_SECONDS", 0.15,
                          create=True):
                with self.assertRaises(CommandFailure) as error:
                    runner._prepare_phase_process()

            self.assertLess(time.monotonic() - started, 0.8)
            self.assertIn("timed out", str(error.exception))
            self.assertIn("Starting: Intent", (
                runner.paths.logs / "phase-process-bootstrap.log").read_text())

    def test_phase_bootstrap_waits_for_process_not_a_drawn_window(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = CommandRunner(
                runner.paths.root / "runner.log", runner.paths.logs,
                runner.paths.root / "instrument-raw.txt")
            pid_queries = 0

            def fake_adb(*args: str) -> tuple[str, ...]:
                nonlocal pid_queries
                if args[:3] == ("shell", "am", "start"):
                    script = "print('Starting: Intent', flush=True)"
                    if "-W" in args:
                        script += "; import time; time.sleep(1.5)"
                else:
                    self.assertEqual(("shell", "pidof", runner.config.package), args)
                    pid_queries += 1
                    script = "print('2468')" if pid_queries > 1 else "print('')"
                return (sys.executable, "-u", "-c", script)

            with patch.object(runner, "_adb", side_effect=fake_adb), \
                    patch("mjpdf_runner.runner.PHASE_BOOTSTRAP_TIMEOUT_SECONDS", 0.15):
                self.assertEqual("2468", runner._prepare_phase_process())
            self.assertEqual(2, pid_queries)

    def test_direct_phase_freezer_exemption_is_scoped_to_the_live_target(self) -> None:
        for supported, alive, body_fails in (
            (True, True, False), (True, True, True),
            (True, False, False), (False, True, False),
        ):
            with self.subTest(supported=supported, alive=alive, body_fails=body_fails), \
                    tempfile.TemporaryDirectory() as temporary:
                runner, _ = self.make_runner(temporary)
                calls = []

                def device(name, *args, **kwargs):
                    calls.append((name, args, kwargs))
                    if name == "phase-freezer-capabilities":
                        return "  unfreeze [--sticky] <PROCESS>" if supported else "am help"
                    if name == "phase-freezer-current-pid":
                        return "2468" if alive else "9876"
                    return "Unfreezing process com.example.app (2468)"

                runner._device = device
                try:
                    with runner._phase_process_unfrozen("2468"):
                        sticky = [c for c in calls if c[0] == "phase-freezer-hold"]
                        self.assertEqual(int(supported), len(sticky))
                        if supported:
                            self.assertEqual(("shell", "am", "unfreeze", "--sticky", "2468"), sticky[0][1])
                        if body_fails:
                            raise RuntimeError("phase failed")
                except RuntimeError as error:
                    self.assertTrue(body_fails)
                    self.assertEqual("phase failed", str(error))
                release = [c for c in calls if c[0] == "phase-freezer-release"]
                self.assertEqual(int(supported and alive), len(release))
                if release:
                    self.assertEqual(("shell", "am", "unfreeze", "2468"), release[0][1])
                    self.assertFalse(release[0][2]["check"])
                self.assertTrue(all(c[2].get("timeout_seconds") for c in calls))

    def test_old_android_help_exit_does_not_block_direct_phases(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = CommandRunner(
                runner.paths.root / "runner.log", runner.paths.logs,
                runner.paths.root / "instrument-raw.txt")

            def fake_adb(*args: str) -> tuple[str, ...]:
                self.assertEqual(("shell", "am", "help"), args)
                return (sys.executable, "-c", "import sys; print('Activity manager commands'); sys.exit(255)")

            with patch.object(runner, "_adb", side_effect=fake_adb):
                with runner._phase_process_unfrozen("2468"):
                    self.assertFalse(runner._phase_freezer_supported)

    def test_unsupported_phase_device_skips_before_bootstrap(self) -> None:
        for api in (28, 29, 30):
            with self.subTest(api=api), tempfile.TemporaryDirectory() as temporary:
                runner, commands = self.make_runner(temporary)
                runner.scenario = PhaseScenario(
                    "test-phase", "test", (), (PhaseAction("phase", "example.Test#phase"),))
                runner.pins.api = api
                runner._assert_no_active_instrumentation = lambda _point: None
                runner._load_owned_uids = lambda: None
                runner._prepare_phase_process = lambda: self.fail("unsupported phase was bootstrapped")
                runner._run_tests()
                self.assertTrue(runner.scenario_skipped)
                self.assertIn("31", runner.scenario_skip_reason)
                self.assertIn("--no-restart", runner.scenario_skip_reason)
                self.assertFalse(any("am" in spec.argv and "start" in spec.argv for spec in commands.specs))

    def test_stalled_instrumentation_transport_times_out_despite_check_false(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "logs").mkdir()
            adb = root / "adb"
            adb.write_text(
                f"#!{sys.executable}\n"
                "import subprocess, sys, time\n"
                "subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(1.5)'])\n"
                "print('INSTRUMENTATION_STATUS: started', flush=True)\n"
                "time.sleep(1.5)\n"
            )
            adb.chmod(0o755)
            commands = CommandRunner(root / "runner.log", root / "logs",
                                     root / "instrument-raw.txt")
            started = time.monotonic()
            with patch("mjpdf_runner.process.INSTRUMENT_IDLE_TIMEOUT_SECONDS", 0.15):
                with self.assertRaises(CommandFailure) as error:
                    commands.run(CommandSpec((str(adb), "shell", "am", "instrument"),
                                             "instrument-0", instrument=True), check=False)

            self.assertLess(time.monotonic() - started, 0.8)
            self.assertIn("timed out", str(error.exception))
            self.assertIn("INSTRUMENTATION_STATUS: started", (
                root / "instrument-raw.txt").read_text())

    def test_streaming_instrumentation_can_run_longer_than_its_idle_limit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "logs").mkdir()
            adb = root / "adb"
            adb.write_text(
                f"#!{sys.executable}\n"
                "import time\n"
                "for index in range(5):\n"
                "    print(f'INSTRUMENTATION_STATUS: {index}', flush=True)\n"
                "    time.sleep(0.1)\n"
            )
            adb.chmod(0o755)
            commands = CommandRunner(root / "runner.log", root / "logs",
                                     root / "instrument-raw.txt")

            with patch("mjpdf_runner.process.INSTRUMENT_IDLE_TIMEOUT_SECONDS", 0.3):
                completed = commands.run(CommandSpec(
                    (str(adb), "shell", "am", "instrument"), "instrument-0", instrument=True))

            self.assertEqual(0, completed.returncode)
            self.assertIn("INSTRUMENTATION_STATUS: 4", (
                root / "instrument-raw.txt").read_text())

    def test_signal_terminates_an_active_process_without_locking(self) -> None:
        class ActiveProcess:
            pid = 4242

            @staticmethod
            def poll():
                raise AssertionError("signal path polled subprocess state")

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "logs").mkdir()
            commands = CommandRunner(root / "runner.log", root / "logs",
                                     root / "instrument-raw.txt")
            commands._active = ActiveProcess()
            runner = object.__new__(Runner)
            runner._interrupted = None
            runner.commands = commands
            previous = signal.getsignal(signal.SIGUSR1)
            signal.signal(signal.SIGUSR1, lambda signum, _frame: runner.interrupt(signum))
            try:
                with patch("mjpdf_runner.process.os.killpg") as killpg:
                    signal.raise_signal(signal.SIGUSR1)
            finally:
                signal.signal(signal.SIGUSR1, previous)

        self.assertEqual(signal.SIGUSR1, runner._interrupted)
        killpg.assert_called_once_with(4242, signal.SIGTERM)

    def test_signal_during_command_spawn_is_latched_after_publication(self) -> None:
        class SpawnedProcess:
            pid = 5252
            returncode = -signal.SIGTERM

            def __init__(self):
                self.stdout = io.BytesIO()

            def poll(self):
                return self.returncode

            def wait(self, timeout=None):
                return self.returncode

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            logs = root / "logs"
            logs.mkdir()
            commands = CommandRunner(
                root / "runner.log", logs, root / "instrument-raw.txt")
            proc = SpawnedProcess()

            def spawn(*_args, **_kwargs):
                commands.terminate_active(signal.SIGTERM)
                return proc

            with patch("mjpdf_runner.process.subprocess.Popen", side_effect=spawn), \
                    patch("mjpdf_runner.process.os.killpg") as killpg:
                completed = commands.run(
                    CommandSpec(("spawned",), "spawned"), check=False)

        self.assertEqual(-signal.SIGTERM, completed.returncode)
        killpg.assert_called_once_with(proc.pid, signal.SIGTERM)

    def test_signal_during_observer_spawn_is_latched_after_publication(self) -> None:
        class SpawnedObserver:
            pid = 5353
            returncode = -signal.SIGTERM

            def poll(self):
                return self.returncode

            def wait(self, timeout=None):
                return self.returncode

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            logs = root / "logs"
            logs.mkdir()
            commands = CommandRunner(
                root / "runner.log", logs, root / "instrument-raw.txt")
            proc = SpawnedObserver()

            def spawn(*_args, **_kwargs):
                commands.terminate_active(signal.SIGTERM)
                return proc

            with patch("mjpdf_runner.process.subprocess.Popen", side_effect=spawn), \
                    patch("mjpdf_runner.process.os.killpg") as killpg:
                observer = commands.start_observer(
                    CommandSpec(("observer",), "observer", mutate=False))
                completed = commands.stop_observer(observer)

        self.assertEqual(-signal.SIGTERM, completed.returncode)
        killpg.assert_called_once_with(proc.pid, signal.SIGTERM)

    def test_signal_before_command_record_is_latched_for_next_spawn(self) -> None:
        class SpawnedProcess:
            pid = 5454
            returncode = -signal.SIGTERM

            def __init__(self):
                self.stdout = io.BytesIO()

            def poll(self):
                return self.returncode

            def wait(self, timeout=None):
                return self.returncode

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            logs = root / "logs"
            logs.mkdir()
            commands = CommandRunner(
                root / "runner.log", logs, root / "instrument-raw.txt")
            proc = SpawnedProcess()
            commands.terminate_active(signal.SIGTERM)

            with patch("mjpdf_runner.process.subprocess.Popen", return_value=proc), \
                    patch("mjpdf_runner.process.os.killpg") as killpg:
                completed = commands.run(
                    CommandSpec(("spawned",), "spawned"), check=False)

        self.assertEqual(-signal.SIGTERM, completed.returncode)
        killpg.assert_called_once_with(proc.pid, signal.SIGTERM)

    def test_nested_boundary_command_keeps_outer_instrumentation_interruptible(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            logs = root / "logs"
            logs.mkdir()
            commands = CommandRunner(
                root / "runner.log", logs, root / "instrument-raw.txt")

            def after_start(line: str) -> None:
                if line != "phase-started":
                    return
                commands.run(CommandSpec(
                    (sys.executable, "-c", "print('boundary-complete')"),
                    "phase-boundary",
                ))
                commands.terminate_active(signal.SIGTERM)

            completed = commands.run(
                CommandSpec(
                    (
                        sys.executable,
                        "-c",
                        "import time; print('phase-started', flush=True); time.sleep(1)",
                    ),
                    "instrument",
                    instrument=True,
                ),
                on_line=after_start,
                check=False,
            )

        self.assertEqual(-signal.SIGTERM, completed.returncode)

    def test_signal_during_active_owner_record_kills_the_probe_spawn(self) -> None:
        class SpawnedProbe:
            pid = 5555
            returncode = -signal.SIGTERM

            def __init__(self):
                self.stdout = io.BytesIO()

            def poll(self):
                return self.returncode

            def wait(self, timeout=None):
                return self.returncode

        class InterruptingCommands(CommandRunner):
            runner = None

            def record(self, message):
                super().record(message)
                if "command[owner-race]" in message:
                    self.runner.interrupt(signal.SIGINT)

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            commands = InterruptingCommands(
                runner.paths.root / "runner.log",
                runner.paths.logs,
                runner.paths.root / "instrument-raw.txt",
            )
            commands.runner = runner
            runner.commands = commands
            proc = SpawnedProbe()

            with patch("mjpdf_runner.process.subprocess.Popen", return_value=proc), \
                    patch("mjpdf_runner.process.os.killpg") as killpg:
                with self.assertRaises(RunnerFailure):
                    runner._active_instrumentation_packages("owner-race")

        self.assertEqual(signal.SIGINT, runner._interrupted)
        killpg.assert_called_once_with(proc.pid, signal.SIGTERM)


    def test_device_lease_rejects_a_second_runner_for_the_same_serial(self) -> None:
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            first_runner, _ = self.make_runner(first)
            second_runner, _ = self.make_runner(second)
            first_runner._acquire_device_lease()
            try:
                with self.assertRaises(RunnerFailure) as raised:
                    second_runner._acquire_device_lease()
            finally:
                first_runner._release_device_lease()

        self.assertEqual(ExitCode.DEVICE_OR_PIN, raised.exception.code)
        self.assertIn("leased by another runner", str(raised.exception))

    def test_active_owner_signature_reports_foreign_instrumentation_package(self) -> None:
        output = (
            " Active instrumentation:\n"
            " Instrumentation #0: ActiveInstrumentation{a1b2c3 "
            "com.foreign.tests/androidx.test.runner.AndroidJUnitRunner 1 procs}"
        )

        self.assertEqual(
            {"com.foreign.tests"},
            Runner._instrumentation_packages(output),
        )

    def test_external_activity_attribution_and_task_front_detection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10123}
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "99.0 I ActivityTaskManager: START u0 {act=android.print.PRINT_DIALOG cmp=com.android.printspooler/.ui.PrintActivity} from uid 1000 (realCallingUid=10123)",
                "99.5 I ActivityTaskManager: START u0 {act=android.settings.SETTINGS cmp=com.android.settings/.Settings} from uid 2000",
                "100.0 I ActivityTaskManager: START u0 {cmp=com.example.browser/.Main} from uid 10123",
                "101.0 I wm_set_resumed_activity: [0,com.example.browser/.Main,realActivityChanged]",
                "102.0 I ActivityTaskManager: START u0 {cmp=com.foreign.app/.Main} from uid 10999",
                "103.0 I wm_set_resumed_activity: [0,com.foreign.app/.Main,realActivityChanged]",
            ))
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [
                (TestKey("example.PrintActionTest", "print"), 98.5, 99.25),
                (TestKey(
                    "example.ExternalDisruptionTest",
                    "anInterruptingActivityReturnsTheReaderExactlyWhereItWas",
                ), 99.4, 99.75),
                (TestKey("example.LinksTest", "browser"), 99.8, 101.5),
            ]
            packages, lines, boundary = runner._execution_isolation(intervals, evidence)


        self.assertEqual({"com.foreign.app"}, packages)
        self.assertEqual(102.0, boundary)
        self.assertTrue(all(
            package not in line
            for line in lines
            for package in ("com.example.browser", "com.android.printspooler",
                            "com.android.settings")
        ))
    def test_system_mediated_print_dialog_is_test_scoped(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10123}
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {act=android.print.PRINT_DIALOG "
                "cmp=com.android.printspooler/.ui.PrintActivity} from uid 1000",
                "100.1 I wm_set_resumed_activity: "
                "[0,com.android.printspooler/.ui.PrintActivity,minimalResumeActivityLocked]",
                "101.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
            ))
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [(
                TestKey(
                    "com.gitlab.mudlej.MjPdfReader.suite.actions.PrintActionTest",
                    "printingHandsTheFrameworkTheWholeDocument",
                ),
                99.0,
                100.8,
            )]

            packages, _lines, boundary = runner._execution_isolation(intervals, evidence)

        self.assertEqual(set(), packages)
        self.assertIsNone(boundary)

    def test_system_mediated_print_dialog_allows_other_intentional_tests(self) -> None:
        intentional_tests = (
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
        )
        evidence = "\n".join((
            "1788152388.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
            "1788152390.230 I ActivityTaskManager: START u0 {act=android.print.PRINT_DIALOG "
            "dat=printjob: cmp=com.android.printspooler/.ui.PrintActivity (has extras)} from uid 1000",
            "1788152390.263 I wm_set_resumed_activity: "
            "[0,com.android.printspooler/.ui.PrintActivity,minimalResumeActivityLocked]",
            "1788152392.6 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
        ))

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._active_instrumentation_packages = lambda name: set()

            for class_name, method in intentional_tests:
                with self.subTest(class_name=class_name, method=method):
                    intervals = [(TestKey(class_name, method), 1788152388.395, 1788152392.530)]

                    packages, _lines, boundary = runner._execution_isolation(intervals, evidence)

                    self.assertEqual(set(), packages)
                    self.assertIsNone(boundary)

    def test_system_mediated_print_dialog_rejects_an_unlisted_test(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {act=android.print.PRINT_DIALOG "
                "cmp=com.android.printspooler/.ui.PrintActivity} from uid 1000",
            ))
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [(TestKey("example.ArbitraryTest", "opensPrintUi"), 99.0, 100.8)]

            packages, _lines, boundary = runner._execution_isolation(intervals, evidence)

        self.assertEqual({"com.android.printspooler"}, packages)
        self.assertEqual(100.0, boundary)

    def test_system_mediated_print_dialog_rejects_unlisted_current_before_allowlisted_next(
            self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {act=android.print.PRINT_DIALOG "
                "cmp=com.android.printspooler/.ui.PrintActivity} from uid 1000",
            ))
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [
                (TestKey("example.ArbitraryTest", "opensPrintUi"), 99.0, 100.8),
                (
                    TestKey(
                        "com.gitlab.mudlej.MjPdfReader.suite.artifacts.PrintStagingArtifactTest",
                        "theStagedPrintDocumentCarriesTheSourcePageCountAndPageSizes",
                    ),
                    101.0,
                    103.0,
                ),
            ]

            packages, _lines, boundary = runner._execution_isolation(intervals, evidence)

        self.assertEqual({"com.android.printspooler"}, packages)
        self.assertEqual(100.0, boundary)

    def test_activity_scenario_fallback_does_not_count_as_takeover(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I wm_set_resumed_activity: "
                "[0,com.example.app/.MainActivity,setFocusedTask-alreadyTop]",
                "100.1 I wm_set_resumed_activity: "
                "[0,com.mudlej.mjgallery.debug/com.mudlej.mjgallery.MainActivity,resumeTopActivity]",
                "101.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
            ))
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [(TestKey("example.ScreenshotTest", "capture"), 99.0, 100.8)]

            packages, _lines, boundary = runner._execution_isolation(intervals, evidence)

        self.assertEqual(set(), packages)
        self.assertIsNone(boundary)


    def test_unowned_same_app_start_is_foreign(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10123}
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "99.0 I ActivityTaskManager: START u0 {cmp=com.example.app/.Launcher} from uid 10123",
                "100.0 I ActivityTaskManager: START u0 {cmp=com.example.app/.Launcher} from uid 10999",
            ))
            runner._active_instrumentation_packages = lambda name: set()

            packages, _lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual({"com.example.app"}, packages)
        self.assertEqual(100.0, boundary)

    def test_launcher_same_app_start_is_foreign(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] cmp=com.example.app/.Launcher} from uid 10187 (com.google.android.apps.nexuslauncher)",
            ))
            runner._active_instrumentation_packages = lambda name: set()

            packages, _lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual({"com.example.app"}, packages)
        self.assertEqual(100.0, boundary)

    def test_known_shell_same_app_start_is_test_scoped(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {act=android.intent.action.VIEW cmp=com.example.app/.MainActivity} from uid 2000 (com.android.shell)",
            ))
            runner._active_instrumentation_packages = lambda name: set()
            known = [(
                TestKey(
                    "com.gitlab.mudlej.MjPdfReader.suite.intake.IntentIntakeTest",
                    "aPathContainingADotInAFolderNameStillMatchesTheViewFilter",
                ),
                99.0,
                101.0,
            )]
            unknown = [(TestKey("example.OtherTest", "shellStart"), 99.0, 101.0)]

            allowed, _lines, clean_boundary = runner._execution_isolation(known, evidence)
            foreign, _lines, foreign_boundary = runner._execution_isolation(unknown, evidence)

        self.assertEqual(set(), allowed)
        self.assertIsNone(clean_boundary)
        self.assertEqual({"com.example.app"}, foreign)
        self.assertEqual(100.0, foreign_boundary)

    def test_shell_launches_accept_root_only_during_their_known_test(self) -> None:
        cases = (
            (
                TestKey(
                    "com.gitlab.mudlej.MjPdfReader.suite.intake.IntentIntakeTest",
                    "aPathContainingADotInAFolderNameStillMatchesTheViewFilter",
                ),
                "act=android.intent.action.VIEW cmp=com.example.app/.MainActivity",
                "com.example.app",
            ),
            (
                TestKey(
                    "com.gitlab.mudlej.MjPdfReader.suite.resilience.ExternalDisruptionTest",
                    "anInterruptingActivityReturnsTheReaderExactlyWhereItWas",
                ),
                "act=android.settings.SETTINGS cmp=com.android.settings/.homepage.SettingsHomepageActivity",
                "com.android.settings",
            ),
        )
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._active_instrumentation_packages = lambda name: set()
            for owner, intent, package in cases:
                for uid in (0, 2000):
                    for current, active in (
                        (owner, True),
                        (TestKey("example.OtherTest", "ordinary"), True),
                        (owner, False),
                    ):
                        with self.subTest(owner=owner, uid=uid, current=current, active=active):
                            evidence = "\n".join((
                                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                                *(() if active else (
                                    "99.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                                )),
                                f"100.0 I ActivityTaskManager: START u0 {{{intent}}} "
                                f"with LAUNCH_MULTIPLE from uid {uid} (BAL_ALLOW_ALLOWLISTED_UID) result code=0",
                            ))
                            packages, _lines, boundary = runner._execution_isolation(
                                [(current, 99.5, 101.0)], evidence)
                            allowed = current == owner and active
                            self.assertEqual(set() if allowed else {package}, packages)
                            self.assertEqual(None if allowed else 100.0, boundary)

    def test_deferred_phase_shell_start_is_owned(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = (
                "100.0 I ActivityTaskManager: START u0 {act=android.intent.action.MAIN "
                "cat=[android.intent.category.LAUNCHER] cmp=com.example.app/.Launcher} "
                "from uid 2000 (com.android.shell)"
            )
            runner._active_instrumentation_packages = lambda name: set()
            intervals: list[tuple[TestKey, float, float | None]] = [
                (TestKey("example.ProcessDeathTest", "phaseTwo"), 99.0, 101.0)
            ]

            packages, lines, boundary = runner._execution_isolation(
                intervals,
                evidence,
                allow_shell_target_start=True,
            )

        self.assertEqual(set(), packages, lines)
        self.assertIsNone(boundary)

    def test_phase_shell_start_uses_device_test_interval(self) -> None:
        self._assert_phase_start_uses_device_test_interval(103.0, False)

    def test_phase_shell_start_before_device_test_interval_is_foreign(self) -> None:
        self._assert_phase_start_uses_device_test_interval(101.5, True)

    def test_phase_shell_start_after_device_test_interval_is_foreign(self) -> None:
        self._assert_phase_start_uses_device_test_interval(104.5, True)

    def _assert_phase_start_uses_device_test_interval(self, event_time: float, foreign: bool) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._active_instrumentation_packages = lambda name: set()
            key = TestKey("example.ProcessDeathTest", "phaseTwo")
            intervals = [(key, 99.0, 101.0)]
            evidence = "\n".join((
                "102.0 123 456 I TestRunner: started: phaseTwo(example.ProcessDeathTest)",
                f"{event_time} I ActivityTaskManager: START u0 {{act=android.intent.action.VIEW "
                "cmp=com.example.app/.MainActivity} from uid 0",
                "104.0 123 456 I TestRunner: finished: phaseTwo(example.ProcessDeathTest)",
            ))
            packages, _lines, _boundary = runner._execution_isolation(
                intervals, iter(evidence.splitlines()), allow_shell_target_start=True,
            )
        self.assertEqual({"com.example.app"} if foreign else set(), packages)

    def test_ordinary_system_activities_use_device_test_intervals(self) -> None:
        cases = (
            ("com.gitlab.mudlej.MjPdfReader.suite.artifacts.PrintStagingArtifactTest",
             "theStagedPrintDocumentOfAProtectedFileIsDecryptedAndComplete",
             "com.android.printspooler", "android.print.PRINT_DIALOG", 1000),
            ("com.gitlab.mudlej.MjPdfReader.suite.resilience.ExternalDisruptionTest",
             "anInterruptingActivityReturnsTheReaderExactlyWhereItWas",
             "com.android.settings", "android.settings.SETTINGS", 2000),
        )
        for class_name, method, package, action, uid in cases:
            for event_time, foreign in ((101.0, True), (103.0, False), (105.0, True)):
                with self.subTest(package=package, event_time=event_time):
                    with tempfile.TemporaryDirectory() as temporary:
                        runner, _ = self.make_runner(temporary)
                        runner._active_instrumentation_packages = lambda name: set()
                        key = TestKey(class_name, method)
                        intervals = [(key, 99.0, 101.0)]
                        events = (
                            (100.0, "I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr"),
                            (102.0, f"I TestRunner: started: {method}({class_name})"),
                            (event_time, f"I ActivityTaskManager: START u0 {{act={action} cmp={package}/.Activity}} from uid {uid}"),
                            (104.0, f"I TestRunner: finished: {method}({class_name})"),
                            (106.0, "I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst"),
                        )
                        evidence = [f"{epoch} {body}" for epoch, body in sorted(events)]
                        packages, _lines, _boundary = runner._execution_isolation(
                            intervals, iter(evidence), expected_target_starts=1,
                        )
                    self.assertEqual({package} if foreign else set(), packages)

    def test_known_shell_start_uses_instrumentation_epoch_when_status_is_delayed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = (
                "90.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "91.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {act=android.intent.action.VIEW "
                "cmp=com.example.app/.MainActivity} from uid 2000 (com.android.shell)",
                "101.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
            )
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [(
                TestKey(
                    "com.gitlab.mudlej.MjPdfReader.suite.intake.IntentIntakeTest",
                    "aPathContainingADotInAFolderNameStillMatchesTheViewFilter",
                ),
                100.5,
                100.9,
            )]

            packages, _lines, boundary = runner._execution_isolation(
                intervals,
                iter(evidence),
                expected_target_starts=2,
            )

        self.assertEqual(set(), packages)
        self.assertIsNone(boundary)
    def test_known_shell_start_tolerates_one_missing_owned_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = (
                "90.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "91.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {act=android.intent.action.VIEW "
                "cmp=com.example.app/.MainActivity} from uid 2000 (com.android.shell)",
                "101.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
            )
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [
                (TestKey("example.OtherTest", "before"), 97.0, 97.5),
                (
                    TestKey(
                        "com.gitlab.mudlej.MjPdfReader.suite.intake.IntentIntakeTest",
                        "aPathContainingADotInAFolderNameStillMatchesTheViewFilter",
                    ),
                    100.5,
                    100.9,
                ),
            ]

            packages, _lines, boundary = runner._execution_isolation(
                intervals,
                iter(evidence),
                expected_target_starts=3,
                allow_incomplete_evidence=True,
            )

        self.assertEqual(set(), packages)
        self.assertIsNone(boundary)


    def test_expected_instrumentation_epoch_count_never_is_foreign(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = (
                "90.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "91.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                "92.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "93.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                "94.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "95.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
            )
            intervals = [
                (TestKey("example.ScanTest", "first"), 90.5, 90.8),
                (TestKey("example.ScanTest", "second"), 94.5, 94.8),
            ]
            runner._active_instrumentation_packages = lambda name: set()

            packages, _lines, boundary = runner._execution_isolation(
                intervals, iter(evidence))

        self.assertEqual(set(), packages)
        self.assertIsNone(boundary)

    def test_no_match_run_allows_zero_observed_owned_starts(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._active_instrumentation_packages = lambda name: set()

            expected = runner._expected_target_starts([], phase=None)
            packages, lines, boundary = runner._execution_isolation(
                [],
                iter(()),
                expected_target_starts=expected,
                allow_incomplete_evidence=True,
            )

        self.assertEqual(1, expected)
        self.assertEqual(set(), packages, lines)
        self.assertEqual([], lines)
        self.assertIsNone(boundary)
        self.assertTrue(any(
            "activity observer evidence incomplete" in warning
            for warning in runner.state.warnings
        ))

    def test_successful_runner_owned_instrumentation_rejects_zero_starts(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [(TestKey("example.ScanTest", "passes"), 100.0, 101.0)]

            with self.assertRaises(RunnerFailure) as raised:
                runner._execution_isolation(
                    intervals,
                    iter(()),
                    expected_target_starts=1,
                    allow_incomplete_evidence=True,
                )

        self.assertEqual(ExitCode.FAILED, raised.exception.code)
        self.assertIn("observed 0", str(raised.exception))

    def test_successful_runner_owned_instrumentation_accepts_one_start(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [(TestKey("example.ScanTest", "passes"), 100.0, 101.0)]
            evidence = iter((
                "99.0 I ActivityManager: Force stopping "
                "com.example.app appid=1 user=0: start instr",
            ))

            packages, lines, boundary = runner._execution_isolation(
                intervals,
                evidence,
                expected_target_starts=1,
                allow_incomplete_evidence=True,
            )

        self.assertEqual(set(), packages)
        self.assertEqual([], lines)
        self.assertIsNone(boundary)

    def test_successful_runner_owned_instrumentation_rejects_duplicate_start(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [(TestKey("example.ScanTest", "passes"), 100.0, 101.0)]
            evidence = iter((
                "99.0 I ActivityManager: Force stopping "
                "com.example.app appid=1 user=0: start instr",
                "102.0 I ActivityManager: Force stopping "
                "com.example.app appid=1 user=0: start instr",
            ))

            packages, lines, boundary = runner._execution_isolation(
                intervals,
                evidence,
                expected_target_starts=1,
                allow_incomplete_evidence=True,
            )

        self.assertEqual({"com.example.app"}, packages)
        self.assertEqual(1, len(lines))
        self.assertIn("102.0", lines[0])
        self.assertIsNone(boundary)

    def test_interrupted_trailing_owned_epoch_is_not_isolation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._interrupted = signal.SIGINT
            runner._interrupted_at = 91.9
            evidence = (
                "90.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "91.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                "92.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "93.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                "94.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "95.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
            )
            intervals = [
                (TestKey("example.ScanTest", "first"), 90.5, 90.8),
                (TestKey("example.ScanTest", "second"), 94.5, 94.8),
            ]

            packages, lines, boundary = runner._execution_isolation(
                intervals, iter(evidence))

        self.assertEqual(set(), packages)
        self.assertEqual([], lines)
        self.assertIsNone(boundary)

    def test_terminal_expected_instrumentation_epoch_is_not_foreign(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = (
                "90.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "91.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                "92.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "93.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
            )
            intervals = [(TestKey("example.ScanTest", "first"), 90.5, 90.8)]
            runner._active_instrumentation_packages = lambda name: set()

            packages, lines, boundary = runner._execution_isolation(
                intervals, iter(evidence))

        self.assertEqual(set(), packages)
        self.assertEqual([], lines)
        self.assertIsNone(boundary)

    def test_repeated_expected_instrumentation_start_is_not_foreign(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = (
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "99.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
            )
            runner._active_instrumentation_packages = lambda name: set()

            packages, _lines, boundary = runner._execution_isolation([], iter(evidence))

        self.assertEqual(set(), packages)
        self.assertIsNone(boundary)

    def test_resume_only_takeover_after_intentional_epoch_is_foreign(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10123}
            evidence = "\n".join((
                "99.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {cmp=com.example.browser/.Main} from uid 10123",
                "100.5 I wm_set_resumed_activity: [0,com.example.browser/.Main,realActivityChanged]",
                "101.0 I wm_set_resumed_activity: [0,com.example.app/.MainActivity,returnToApp]",
                "102.1 I wm_set_resumed_activity: [0,com.example.browser/.Main,moveTaskToFront]",
            ))
            runner._active_instrumentation_packages = lambda name: set()

            packages, _lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual({"com.example.browser"}, packages)
        self.assertEqual(102.1, boundary)

    def test_intentional_browser_may_continue_from_its_own_application_uid(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10123}
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {cmp=com.example.browser/.IntentDispatcher} "
                "from uid 10123",
                "100.1 I ActivityTaskManager: START u0 {cmp=com.example.browser/.IntentDispatcher} "
                "from uid 10160",
                "100.2 I ActivityTaskManager: START u0 {cmp=com.example.browser/.FirstRunActivity} "
                "from uid 10160",
            ))
            runner._active_instrumentation_packages = lambda name: set()

            packages, _lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual(set(), packages)
        self.assertIsNone(boundary)

    def test_uid1000_browser_handoff_is_foreign(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10123}
            evidence = "\n".join((
                "98.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "100.0 I ActivityTaskManager: START u0 {cmp=com.example.browser/.Main} from uid 10123",
                "101.0 I ActivityTaskManager: START u0 {cmp=com.example.browser/.Main} from uid 1000",
            ))
            runner._active_instrumentation_packages = lambda name: set()

            packages, _lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual({"com.example.browser"}, packages)
        self.assertEqual(101.0, boundary)

    def test_actual_api36_observer_settings_handoffs_are_clean(self) -> None:
        observer_path = (
            replay_runs() / "20260820-060625-153600" / "devices"
            / "emulator-5584" / "batches" / "001-ordinary" / "logs"
            / "device-isolation-observer-0.log"
        )
        captured = observer_path.read_text(encoding="utf-8").splitlines()
        marker = next(
            index for index, line in enumerate(captured)
            if "mjpdf-observer-ready-" in line
        )
        captured = captured[marker + 1:]
        epochs: list[tuple[float, float | None, list[str]]] = []
        current: tuple[float, list[str]] | None = None
        app = "com.gitlab.mudlej.MjPdfReader"
        for line in captured:
            if f"Force stopping {app} " in line and "start instr" in line:
                if current is not None:
                    epochs.append((current[0], None, current[1]))
                current = (Runner._event_time(line) or 0.0, [line])
                continue
            if current is None:
                continue
            current[1].append(line)
            if f"Force stopping {app} " in line and "finished inst" in line:
                epochs.append((current[0], Runner._event_time(line), current[1]))
                current = None
        if current is not None:
            epochs.append((current[0], None, current[1]))

        intervals = []
        for index, (started_at, ended_at, epoch_lines) in enumerate(epochs[1:], 1):
            finish = ended_at if ended_at is not None else started_at + 1.0
            key = TestKey("example.ObserverReplayTest", f"test{index}")
            if any("from uid 2000" in line and f"cmp={app}/" in line
                   for line in epoch_lines):
                key = TestKey(
                    "com.gitlab.mudlej.MjPdfReader.suite.intake.IntentIntakeTest",
                    "aPathContainingADotInAFolderNameStillMatchesTheViewFilter",
                )
            elif any("act=android.settings.SETTINGS" in line and "from uid 2000" in line
                     for line in epoch_lines):
                key = TestKey(
                    "example.ExternalDisruptionTest",
                    "anInterruptingActivityReturnsTheReaderExactlyWhereItWas",
                )
            intervals.append((key, started_at, finish))

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.config = replace(
                runner.config, package=app, test_package=f"{app}.test")
            runner._owned_uids = {10221}
            runner._active_instrumentation_packages = lambda name: set()

            packages, lines, boundary = runner._execution_isolation(
                intervals, iter(captured), expected_target_starts=len(intervals) + 1)

        spa_handoffs = [
            line for line in captured
            if "ActivityTaskManager: START " in line
            and "cmp=com.android.settings/.spa.SpaActivity" in line
            and "from uid 1000" in line
        ]
        self.assertEqual(3, len(spa_handoffs))
        self.assertEqual(set(), packages, lines[:10])
        self.assertEqual([], lines)
        self.assertIsNone(boundary)




    def test_interrupted_three_device_observers_preserve_raw_outcomes(self) -> None:
        root = replay_runs() / "20260820-131540-205981" / "devices"
        app = "com.gitlab.mudlej.MjPdfReader"
        expected_outcomes = {
            "emulator-5574": 64,
            "emulator-5584": 64,
            "emulator-5588": 38,
        }
        expected_started = {
            "emulator-5574": 65,
            "emulator-5584": 64,
            "emulator-5588": 39,
        }
        observed_outcomes = {}
        observed_started = {}
        for serial in expected_outcomes:
            batch = root / serial / "batches" / "001-ordinary"
            captured = (
                batch / "logs" / "device-isolation-observer-0.log"
            ).read_text(encoding="utf-8").splitlines()
            marker = next(
                index for index, line in enumerate(captured)
                if "mjpdf-observer-ready-" in line
            )
            captured = captured[marker + 1:]
            epochs: list[tuple[float, float | None, list[str]]] = []
            current: tuple[float, list[str]] | None = None
            for line in captured:
                if f"Force stopping {app} " in line and "start instr" in line:
                    if current is not None:
                        epochs.append((current[0], None, current[1]))
                    current = (Runner._event_time(line) or 0.0, [line])
                    continue
                if current is None:
                    continue
                current[1].append(line)
                if f"Force stopping {app} " in line and "finished inst" in line:
                    epochs.append((current[0], Runner._event_time(line), current[1]))
                    current = None
            if current is not None:
                epochs.append((current[0], None, current[1]))

            intervals = []
            for index, (started_at, ended_at, epoch_lines) in enumerate(epochs, 1):
                finish = ended_at if ended_at is not None else started_at + 1.0
                key = TestKey("example.ObserverReplayTest", f"test{index}")
                if any("from uid 2000" in line and f"cmp={app}/" in line
                       for line in epoch_lines):
                    key = TestKey(
                        "com.gitlab.mudlej.MjPdfReader.suite.intake.IntentIntakeTest",
                        "aPathContainingADotInAFolderNameStillMatchesTheViewFilter",
                    )
                elif any("act=android.settings.SETTINGS" in line and "from uid 2000" in line
                         for line in epoch_lines):
                    key = TestKey(
                        "example.ExternalDisruptionTest",
                        "anInterruptingActivityReturnsTheReaderExactlyWhereItWas",
                    )
                intervals.append((key, started_at, finish))

            uid_line = next(
                line for line in captured
                if f"Force stopping {app} " in line and "appid=" in line
            )
            uid = int(uid_line.split("appid=", 1)[1].split()[0])
            raw_lines = (batch / "instrument-raw.txt").read_text(
                encoding="utf-8").splitlines()
            observed_outcomes[serial] = sum(
                line.startswith("INSTRUMENTATION_STATUS_CODE:")
                and not line.endswith(": 1")
                for line in raw_lines
            )
            observed_started[serial] = sum(
                line == "INSTRUMENTATION_STATUS_CODE: 1" for line in raw_lines)

            with tempfile.TemporaryDirectory() as temporary:
                runner, _ = self.make_runner(temporary)
                runner.config = replace(
                    runner.config, package=app, test_package=f"{app}.test")
                runner._owned_uids = {uid}
                runner._active_instrumentation_packages = lambda name: set()
                packages, lines, boundary = runner._execution_isolation(
                    intervals,
                    iter(captured),
                    expected_target_starts=observed_started[serial] + 1,
                )

            self.assertEqual(set(), packages, (serial, lines[:10]))
            self.assertEqual([], lines)
            self.assertIsNone(boundary)

        self.assertEqual(expected_outcomes, observed_outcomes)
        self.assertEqual(expected_started, observed_started)
        self.assertEqual(166, sum(observed_outcomes.values()))

    def test_cardinality_replays_clean_and_scenario_artifacts(self) -> None:
        app = "com.gitlab.mudlej.MjPdfReader"
        runs = ("20260820-071501-340602", "20260820-063900-788905")
        ordinary_evidence = None
        for run_id in runs:
            batch = (
                replay_runs() / run_id / "devices" / "emulator-5584"
                / "batches" / "001-ordinary"
            )
            observer = (
                batch / "logs" / "device-isolation-observer-0.log"
            ).read_text(encoding="utf-8").splitlines()
            marker = next(
                index for index, line in enumerate(observer)
                if "mjpdf-observer-ready-" in line
            )
            evidence = observer[marker + 1:]
            lifecycle = [
                line for line in evidence if f"Force stopping {app} " in line]
            starts = sum("start instr" in line for line in lifecycle)
            raw = (batch / "instrument-raw.txt").read_text(
                encoding="utf-8").splitlines()
            test_starts = sum(
                line == "INSTRUMENTATION_STATUS_CODE: 1" for line in raw)
            self.assertEqual((242, 241), (starts, test_starts))

            with tempfile.TemporaryDirectory() as temporary:
                runner, _ = self.make_runner(temporary)
                runner.config = replace(
                    runner.config, package=app, test_package=f"{app}.test")
                runner._active_instrumentation_packages = lambda name: set()
                packages, lines, boundary = runner._execution_isolation(
                    [], iter(lifecycle), expected_target_starts=test_starts + 1)
            self.assertEqual(set(), packages)
            self.assertEqual([], lines)
            self.assertIsNone(boundary)
            ordinary_evidence = lifecycle

        scenario_observers = sorted(
            path for path in (
                replay_runs() / "20260820-071501-340602"
                / "devices" / "emulator-5584" / "batches"
            ).glob("*scenario*/logs/device-isolation-observer-*.log")
            if "observer-ready" not in path.name
        )
        self.assertGreater(len(scenario_observers), 0)
        for observer_path in scenario_observers:
            observer = observer_path.read_text(encoding="utf-8").splitlines()
            marker = next(
                index for index, line in enumerate(observer)
                if "mjpdf-observer-ready-" in line
            )
            lifecycle = [
                line for line in observer[marker + 1:]
                if f"Force stopping {app} " in line
            ]
            self.assertEqual(1, sum("start instr" in line for line in lifecycle))
            with tempfile.TemporaryDirectory() as temporary:
                runner, _ = self.make_runner(temporary)
                runner.config = replace(
                    runner.config, package=app, test_package=f"{app}.test")
                runner._active_instrumentation_packages = lambda name: set()
                packages, _lines, boundary = runner._execution_isolation(
                    [], iter(lifecycle), expected_target_starts=1)
            self.assertEqual(set(), packages, observer_path)
            self.assertIsNone(boundary)

        assert ordinary_evidence is not None
        target_markers = [
            line for line in ordinary_evidence if "start instr" in line]
        extra_marker = target_markers[-1]
        marker_prefix, marker_body = extra_marker.strip().split(maxsplit=1)
        terminal_marker = f"{float(marker_prefix) + 1.0:.3f} {marker_body}"
        cardinality_intervals = [
            (TestKey("example.CardinalityTest", f"test{index}"),
             (Runner._event_time(line) or 0.0) + 0.001, None)
            for index, line in enumerate(target_markers[1:], 1)
        ]
        excess_variants = {}
        for position in ("beginning", "middle", "end"):
            evidence = list(ordinary_evidence)
            index = {"beginning": 0, "middle": len(evidence) // 2, "end": len(evidence)}[
                position]
            evidence.insert(index, terminal_marker if position == "end" else extra_marker)
            excess_variants[position] = evidence
        fewer = list(ordinary_evidence)
        fewer.remove(next(
            line for line in reversed(fewer) if "start instr" in line))
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.config = replace(
                runner.config, package=app, test_package=f"{app}.test")
            runner._active_instrumentation_packages = lambda name: set()
            excess_results = {
                position: runner._execution_isolation(
                    cardinality_intervals,
                    iter(evidence),
                    expected_target_starts=242,
                )
                for position, evidence in excess_variants.items()
            }
            with self.assertRaises(RunnerFailure) as incomplete:
                runner._execution_isolation(
                    cardinality_intervals, iter(fewer), expected_target_starts=242)
            allowed_incomplete = runner._execution_isolation(
                cardinality_intervals,
                iter(fewer),
                expected_target_starts=242,
                allow_incomplete_evidence=True,
            )
            runner._interrupted = signal.SIGINT
            runner._interrupted_at = float(marker_prefix) + 1.1
            terminal, terminal_lines, terminal_boundary = runner._execution_isolation(
                cardinality_intervals,
                iter(excess_variants["end"]),
                allow_terminal_unmatched=True,
                expected_target_starts=242,
            )
            abnormal_middle = runner._execution_isolation(
                cardinality_intervals,
                iter(excess_variants["middle"]),
                allow_terminal_unmatched=True,
                expected_target_starts=242,
            )

        for packages, _lines, boundary in excess_results.values():
            self.assertEqual({app}, packages)
            self.assertIsNone(boundary)
        self.assertEqual(ExitCode.FAILED, incomplete.exception.code)
        self.assertEqual(set(), allowed_incomplete[0])
        self.assertIsNone(allowed_incomplete[2])
        self.assertTrue(any(
            "activity observer evidence incomplete" in warning
            for warning in runner.state.warnings
        ))
        self.assertEqual(set(), terminal)
        self.assertEqual([], terminal_lines)
        self.assertIsNone(terminal_boundary)
        self.assertEqual({app}, abnormal_middle[0])
        self.assertIsNone(abnormal_middle[2])

    def test_stream_collision_survives_owner_probe_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = (
                "100.0 I ActivityManager: Force stopping "
                "com.foreign.app appid=9 user=0: start instr"
            )
            runner._active_instrumentation_packages = lambda name: (_ for _ in ()).throw(
                RunnerFailure(ExitCode.DEVICE_OR_PIN, "owner probe failed"))

            packages, lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual({"com.foreign.app"}, packages)
        self.assertEqual([evidence], lines)
        self.assertEqual(100.0, boundary)

    def test_active_owner_without_instrumentation_marker_has_no_boundary(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = (
                "102.0 I wm_set_resumed_activity: "
                "[0,com.foreign.app/.Main,moveTaskToFront]"
            )
            runner._active_instrumentation_packages = lambda name: {"com.foreign.app"}

            packages, _lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual({"com.foreign.app"}, packages)
        self.assertIsNone(boundary)

    def test_multiple_explicit_tests_account_for_orchestrator_collection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            intervals = [
                (TestKey("example.FirstTest", "first"), 100.0, 101.0),
                (TestKey("example.SecondTest", "second"), 102.0, 103.0),
            ]

            self.assertEqual(3, runner._expected_target_starts(intervals, phase=None))
            self.assertEqual(2, runner._expected_target_starts(intervals[:1], phase=None))
            self.assertEqual(2, runner._expected_target_starts(intervals, phase=1))

    def test_collection_count_follows_the_effective_selector_not_the_result_count(self) -> None:
        cases = (
            (("example.OneTest",), (), 1, 2),
            (("example.OneTest#passes",), (), 1, 1),
            (("example.OneTest#passes",), (), 3, 1),
            (("example.OneTest#passes[0]",), (), 1, 2),
            (("example.Outer$Inner#passes",), (), 1, 2),
            (("example.OneTest#passes,example.TwoTest#passes",), (), 1, 2),
            ((), ("-e", "package", "example"), 1, 2),
            ((), ("-e", "testFile", "/data/local/tmp/tests.txt"), 1, 2),
            (("example.OneTest",), ("-e", "class", "example.OneTest#passes"), 1, 1),
            (("example.OneTest#passes",), ("-e", "class", "example.OneTest"), 1, 2),
        )
        for classes, passthrough, count, expected in cases:
            with self.subTest(classes=classes, passthrough=passthrough, count=count), \
                    tempfile.TemporaryDirectory() as temporary:
                runner, _ = self.make_runner(temporary)
                runner.config = replace(runner.config, classes=classes, passthrough=passthrough)
                intervals = [(TestKey("example.OneTest", f"test{i}"), 100.0 + i, 101.0 + i)
                             for i in range(count)]
                self.assertEqual(expected, runner._expected_target_starts(intervals, phase=None))

    def test_one_method_class_accepts_collection_but_rejects_a_third_start(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.config = replace(runner.config, classes=("example.OneTest",))
            runner._active_instrumentation_packages = lambda _name: set()
            intervals = [(TestKey("example.OneTest", "passes"), 100.5, 101.0)]
            evidence = [
                "99.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "99.5 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
                "100.0 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
            ]
            expected = runner._expected_target_starts(intervals, phase=None)
            packages, lines, boundary = runner._execution_isolation(
                intervals, iter(evidence), expected_target_starts=expected)
            self.assertEqual(set(), packages, lines)
            self.assertIsNone(boundary)
            evidence.append(
                "100.75 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr")
            packages, lines, _ = runner._execution_isolation(
                intervals, iter(evidence), expected_target_starts=expected)
            self.assertEqual({"com.example.app"}, packages)
            self.assertTrue(any("100.75" in line for line in lines))

    def test_unattributed_shell_settings_launch_is_foreign(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10123}
            evidence = (
                "100.0 I ActivityTaskManager: START u0 "
                "{act=android.settings.APPLICATION_DETAILS_SETTINGS "
                "cmp=com.android.settings/.Settings} from uid 2000"
            )
            runner._active_instrumentation_packages = lambda name: set()

            packages, _lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual({"com.android.settings"}, packages)
        self.assertEqual(100.0, boundary)

    def test_owned_settings_handoff_allows_spa_transitions_and_resumes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10246}
            evidence = "\n".join((
                "1787201368.500 I ActivityManager: Force stopping com.example.app appid=1 user=0: start instr",
                "1787201369.009 I ActivityTaskManager: START u0 {act=android.settings.APPLICATION_DETAILS_SETTINGS dat=package: flg=0x10000000 xflg=0x4 cmp=com.android.settings/.applications.InstalledAppDetails} with LAUNCH_MULTIPLE from uid 10246 (com.gitlab.mudlej.MjPdfReader) (BAL_ALLOW_VISIBLE_WINDOW) result code=0",
                "1787201369.010 I wm_set_resumed_activity: [0,com.android.settings/.applications.InstalledAppDetails,realStartActivityLocked - onActivityStateChanged]",
                "1787201369.091 I ActivityTaskManager: START u0 {xflg=0x4 cmp=com.android.settings/.spa.SpaActivity (has extras)} with LAUNCH_MULTIPLE from uid 1000 (com.android.settings) (BAL_ALLOW_VISIBLE_WINDOW) result code=0",
                "1787201369.093 I wm_set_resumed_activity: [0,com.android.settings/.spa.SpaActivity,realStartActivityLocked - onActivityStateChanged]",
                "1787201370.231 I wm_set_resumed_activity: [0,com.android.settings/.spa.SpaActivity,setFocusedTask-alreadyTop]",
                "1787201370.500 I ActivityManager: Force stopping com.example.app appid=1 user=0: finished inst",
            ))
            runner._active_instrumentation_packages = lambda name: set()

            packages, lines, boundary = runner._execution_isolation([], evidence)

        self.assertEqual(set(), packages)
        self.assertEqual([], lines)
        self.assertIsNone(boundary)

    def test_https_uri_host_is_not_parsed_as_activity_component(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._owned_uids = {10246}
            evidence = (
                "1787201135.564 I ActivityTaskManager: START u0 "
                "{act=android.intent.action.VIEW dat=https://gitlab.com/... xflg=0x4} "
                "with LAUNCH_MULTIPLE from uid 10246 "
                "(com.gitlab.mudlej.MjPdfReader) result code=-91"
            )
            runner._active_instrumentation_packages = lambda name: set()

            packages, lines, boundary = runner._execution_isolation([], evidence)

        self.assertIsNone(Runner._activity_package(evidence))
        self.assertEqual(set(), packages)
        self.assertEqual([], lines)
        self.assertIsNone(boundary)


    def test_streamed_takeover_survives_device_log_clear(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            captured = "\n".join((
                "100.0 I ActivityManager: Force stopping com.foreign.app appid=9 user=0: start instr",
                "100.1 I ActivityTaskManager: START u0 {cmp=com.foreign.app/.Main} from uid 10999",
            ))
            runner._isolation_device = lambda name, *args: ""
            runner._active_instrumentation_packages = lambda name: set()

            packages, lines, boundary = runner._execution_isolation([], captured)

        self.assertEqual({"com.foreign.app"}, packages)
        self.assertEqual(100.0, boundary)
        self.assertEqual(2, len(lines))

    def test_interrupted_isolation_skips_owner_probe_but_classifies_stream(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._interrupted = signal.SIGINT
            runner._active_instrumentation_packages = lambda _name: (_ for _ in ()).throw(
                AssertionError("active-owner probe ran after interrupt"))
            captured = (
                "100.0 I ActivityManager: Force stopping "
                "com.foreign.app appid=9 user=0: start instr"
            )

            packages, lines, boundary = runner._execution_isolation([], captured)

        self.assertEqual({"com.foreign.app"}, packages)
        self.assertEqual(100.0, boundary)
        self.assertEqual([captured], lines)


    def test_instrumentation_uses_streamed_evidence_after_ring_clear(self) -> None:
        captured = (
            "100.0 I ActivityManager: Force stopping "
            "com.foreign.app appid=9 user=0: start instr"
        )

        class ObserverCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                output = "package:/services.apk" if spec.name == "services-path" else ""
                return CompletedCommand(spec.argv, 0, output)

            def stop_observer(self, observer):
                return CompletedCommand(observer.argv, 0, captured)

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = ObserverCommands()
            runner._isolation_device = lambda name, *args: ""
            runner._active_instrumentation_packages = lambda name: set()

            with self.assertRaises(RunnerFailure) as raised:
                runner._instrument_once([])

        self.assertEqual(ExitCode.DEVICE_OR_PIN, raised.exception.code)
        self.assertIn("com.foreign.app", str(raised.exception))

    def test_system_picker_reset_stops_only_installed_picker_processes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            calls = []
            def device(name, *args, **kwargs):
                calls.append(args)
                return "package:/system/DocumentsUI.apk" if args == (
                    "shell", "pm", "path", "com.google.android.documentsui") else ""
            runner._device = device
            runner._reset_system_pickers()
        self.assertEqual([
            ("shell", "pm", "path", "com.google.android.documentsui"),
            ("shell", "am", "force-stop", "com.google.android.documentsui"),
            ("shell", "pm", "path", "com.android.documentsui"),
        ], calls)

    def test_instrumentation_waits_for_observer_marker(self) -> None:
        class HandshakeCommands(FakeCommands):
            def __init__(self):
                super().__init__()
                self.events = []
                self.observer_spec = None


            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.name == "device-isolation-observer-ready-0":
                    self.events.append(("marker", spec.argv[-1]))
                if spec.instrument:
                    self.events.append(("instrument", None))
                    on_line("INSTRUMENTATION_CODE: 0")
                return CompletedCommand(spec.argv, 0, "")

            def start_observer(self, spec):
                self.observer_spec = spec

                self.events.append(("start", None))
                return spec

            def wait_observer(self, observer, marker, timeout=5.0):
                self.events.append(("ready", marker))

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            commands = HandshakeCommands()
            runner.commands = commands
            runner._active_instrumentation_packages = lambda name: set()

            runner._instrument_once([])

        self.assertEqual(
            ["marker", "start", "ready", "instrument"],
            [event for event, _value in commands.events],
        )
        self.assertEqual(commands.events[0][1], commands.events[2][1])
        self.assertTrue(commands.events[0][1].startswith("mjpdf-observer-ready-"))
        self.assertNotIn("-T", commands.observer_spec.argv)

    def test_observer_keeps_readiness_marker_during_unrelated_log_burst(self) -> None:
        class BusyLogCommands(FakeCommands):
            def __init__(self):
                super().__init__()
                self.buffer = []
                self.captured = []
                self.instrumented = False

            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.name == "device-isolation-observer-ready-0":
                    self.buffer.append(spec.argv[-1])
                if spec.instrument:
                    self.instrumented = True
                    on_line("INSTRUMENTATION_CODE: 0")
                return CompletedCommand(spec.argv, 0, "")

            def start_observer(self, spec):
                self.buffer.extend(f"W skia: unrelated log {index}" for index in range(450))
                self.captured = self.buffer
                if "-T" in spec.argv:
                    count = int(spec.argv[spec.argv.index("-T") + 1])
                    self.captured = self.buffer[-count:]
                return spec

            def wait_observer(self, observer, marker, timeout=5.0):
                if not any(marker in line for line in self.captured):
                    raise CommandFailure(CompletedCommand(observer.argv, 124, ""))

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            commands = BusyLogCommands()
            runner.commands = commands
            runner._active_instrumentation_packages = lambda name: set()
            runner._instrument_once([])
            self.assertTrue(commands.instrumented)

    def test_phase_start_callback_runs_after_started_status_and_returns_phase_result(self) -> None:
        events: list[str] = []

        class PhaseCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.instrument:
                    events.append("instrument")
                    for line in (
                        "INSTRUMENTATION_STATUS: class=example.PhaseTest",
                        "INSTRUMENTATION_STATUS: test=restores",
                        "INSTRUMENTATION_STATUS_CODE: 1",
                    ):
                        on_line(line)
                    events.append("after-start-status")
                    for line in (
                        "INSTRUMENTATION_STATUS: class=example.PhaseTest",
                        "INSTRUMENTATION_STATUS: test=restores",
                        "INSTRUMENTATION_STATUS_CODE: 0",
                        "INSTRUMENTATION_CODE: 0",
                    ):
                        on_line(line)
                return CompletedCommand(spec.argv, 0, "")

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.config = replace(runner.config, dry_run=True)
            runner.commands = PhaseCommands()

            results = runner._instrument_once(
                [], phase=2, on_test_started=lambda: events.append("callback"))

        self.assertEqual(["instrument", "callback", "after-start-status"], events)
        self.assertEqual(1, len(results))
        self.assertEqual(2, results[0].phase)
        self.assertEqual("restores", results[0].key.method)




    def test_empty_buffer_observer_exit_is_operational_failure(self) -> None:
        class EmptyObserverCommands(FakeCommands):
            def __init__(self):
                super().__init__()
                self.events = []

            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.name == "device-isolation-observer-ready-0":
                    self.events.append("marker")
                return CompletedCommand(spec.argv, 0, "")

            def start_observer(self, spec):
                self.events.append("start")
                raise CommandFailure(CompletedCommand(spec.argv, 1, ""))

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            commands = EmptyObserverCommands()
            runner.commands = commands

            with self.assertRaises(RunnerFailure) as raised:
                runner._instrument_once([])

        self.assertEqual(["marker", "start"], commands.events)
        self.assertEqual(ExitCode.FAILED, raised.exception.code)
        self.assertNotEqual(ExitCode.DEVICE_OR_PIN, raised.exception.code)
        self.assertIn("activity observer readiness failed", str(raised.exception))

    def test_direct_class_and_package_artifacts_need_no_discovery_marker(self) -> None:
        app = "com.gitlab.mudlej.MjPdfReader"
        cases = (
            ("20260822-214337-691841", ("example.DirectClassTest",), ()),
            ("20260822-214442-656435", (), ("home",)),
        )

        class DirectCommands(FakeCommands):
            def __init__(self, raw: list[str], observer: list[str]):
                super().__init__()
                self.raw = raw
                self.observer = observer

            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.instrument:
                    for line in self.raw:
                        on_line(line)
                return CompletedCommand(spec.argv, 0, "")

            def observer_lines(self, observer):
                marker = next(
                    index for index, line in enumerate(self.observer)
                    if "mjpdf-observer-ready-" in line
                )
                return iter(self.observer[marker + 1:])

        for run_id, classes, packages in cases:
            root = replay_runs() / run_id
            raw = (root / "instrument-raw.txt").read_text(
                encoding="utf-8").splitlines()
            observer = (
                root / "logs" / "device-isolation-observer-0.log"
            ).read_text(encoding="utf-8").splitlines()
            with tempfile.TemporaryDirectory() as temporary:
                runner, _ = self.make_runner(temporary)
                runner.config = replace(
                    runner.config,
                    package=app,
                    test_package=f"{app}.test",
                    classes=classes,
                    packages=packages,
                    test_file=None,
                )
                runner._owned_uids = {
                    int(next(
                        line for line in observer
                        if f"Force stopping {app} " in line and "appid=" in line
                    ).split("appid=", 1)[1].split()[0])
                }
                runner.commands = DirectCommands(raw, observer)

                runner._instrument_once([])

            self.assertEqual(1, len(runner.state.tests), run_id)
            self.assertEqual([], runner.run_errors, run_id)

    def test_sparse_owned_instrumentation_markers_do_not_fail_clean_run(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            evidence = "\n".join(
                f"{100.0 + index} I ActivityManager: Force stopping "
                "com.example.app appid=2 user=0: start instr"
                for index in range(4)
            )
            runner._active_instrumentation_packages = lambda name: set()
            intervals = [
                (TestKey("example.ScanTest", f"test{index}"),
                 99.0 + index, 99.5 + index)
                for index in range(21)
            ]

            packages, lines, boundary = runner._execution_isolation(intervals, evidence)

        self.assertEqual(set(), packages)
        self.assertEqual([], lines)
        self.assertIsNone(boundary)


    def test_instrumentation_takeover_preserves_only_results_before_boundary(self) -> None:
        class ScriptedCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.instrument:
                    lines = (
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=beforeTakeover",
                        "INSTRUMENTATION_STATUS_CODE: 1",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=beforeTakeover",
                        "INSTRUMENTATION_STATUS_CODE: 0",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=afterTakeover",
                        "INSTRUMENTATION_STATUS_CODE: 1",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=afterTakeover",
                        "INSTRUMENTATION_STATUS: stack=foreign foreground",
                        "INSTRUMENTATION_STATUS_CODE: -2",
                        "INSTRUMENTATION_CODE: -1",
                    )
                    for line in lines:
                        on_line(line)
                return CompletedCommand(spec.argv, 0, "")

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            commands = ScriptedCommands()
            runner.commands = commands
            runner._execution_isolation = lambda *_args, **_kwargs: (
                {"com.foreign.app"}, ["102.0 foreign takeover"], 5.5)
            with patch(
                "mjpdf_runner.runner.time.time",
                side_effect=(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0),
            ):
                with self.assertRaises(RunnerFailure) as raised:
                    runner._instrument_once([])

            self.assertEqual(ExitCode.DEVICE_OR_PIN, raised.exception.code)
            self.assertEqual(["beforeTakeover"], [result.key.method for result in runner.state.tests])
            self.assertEqual(
                ["beforeTakeover"], [result.key.method for result in runner.renderer.finished])
            self.assertEqual(
                ["beforeTakeover", "afterTakeover"],
                [result.key.method for result in runner.renderer.finish_calls],
            )

    def test_ui_automation_single_owner_collision_is_device_isolation(self) -> None:
        class CollisionCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.name == "device-isolation-execution-active":
                    raise AssertionError("owner probe masked known UiAutomation collision")
                if spec.instrument:
                    lines = (
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=beforeCollision",
                        "INSTRUMENTATION_STATUS_CODE: 1",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=beforeCollision",
                        "INSTRUMENTATION_STATUS_CODE: 0",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=collided",
                        "INSTRUMENTATION_STATUS_CODE: 1",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=collided",
                        "INSTRUMENTATION_STATUS: stack=java.lang.IllegalStateException: UiAutomationService android.accessibilityservice.IAccessibilityServiceClient already registered!",
                        "INSTRUMENTATION_STATUS_CODE: -2",
                        "INSTRUMENTATION_CODE: -1",
                    )
                    for line in lines:
                        on_line(line)
                return CompletedCommand(spec.argv, 0, "")

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = CollisionCommands()
            with patch(
                "mjpdf_runner.runner.time.time",
                side_effect=(1.0, 2.0, 3.0, 4.0, 5.0, 6.0),
            ):
                with self.assertRaises(RunnerFailure) as raised:
                    runner._instrument_once([])

        self.assertEqual(ExitCode.DEVICE_OR_PIN, raised.exception.code)
        self.assertIn("foreign UiAutomation owner", str(raised.exception))
        self.assertEqual(
            ["beforeCollision"], [result.key.method for result in runner.state.tests])

    def test_final_preflight_failure_does_not_mark_suite_started(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner._assert_no_active_instrumentation = lambda point: (_ for _ in ()).throw(
                RunnerFailure(ExitCode.DEVICE_OR_PIN, "foreign instrumentation"))

            with self.assertRaises(RunnerFailure):
                runner._run_tests()

            self.assertFalse(runner._suite_started)

    def test_observer_teardown_failure_preserves_results_as_operational(self) -> None:
        class FailedTeardownCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.instrument:
                    for line in (
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=provisional",
                        "INSTRUMENTATION_STATUS_CODE: 1",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=provisional",
                        "INSTRUMENTATION_STATUS_CODE: 0",
                    ):
                        on_line(line)
                return CompletedCommand(spec.argv, 0, "")

            def stop_observer(self, observer):
                raise OSError("observer teardown failed")

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = FailedTeardownCommands()

            with self.assertRaises(RunnerFailure) as raised:
                runner._instrument_once([])

        self.assertEqual(ExitCode.FAILED, raised.exception.code)
        self.assertEqual(
            ["provisional"], [result.key.method for result in runner.state.tests])
        self.assertEqual(
            ["provisional"], [result.key.method for result in runner.renderer.finished])
        self.assertEqual(
            ["provisional"],
            [result.key.method for result in runner.renderer.finish_calls],
        )

    def test_observer_early_exit_preserves_results_as_operational(self) -> None:
        class EarlyExitCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.instrument:
                    for line in (
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=completed",
                        "INSTRUMENTATION_STATUS_CODE: 1",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=completed",
                        "INSTRUMENTATION_STATUS_CODE: 0",
                        "INSTRUMENTATION_CODE: 0",
                    ):
                        on_line(line)
                return CompletedCommand(spec.argv, 0, "")

            def stop_observer(self, observer):
                return CompletedCommand(observer.argv, 17, "")

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = EarlyExitCommands()

            with self.assertRaises(RunnerFailure) as raised:
                runner._instrument_once([])

        self.assertEqual(ExitCode.FAILED, raised.exception.code)
        self.assertEqual(
            ["completed"], [result.key.method for result in runner.state.tests])
        self.assertEqual(
            ["completed"], [result.key.method for result in runner.renderer.finished])

    def test_ctrl_c_retracts_fail_and_error_reported_after_signal(self) -> None:
        class InterruptedPostflightCommands(FakeCommands):
            runner = None

            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.instrument:
                    def report(name, code, stack=""):
                        on_line("INSTRUMENTATION_STATUS: class=example.BulkActionsTest")
                        on_line(f"INSTRUMENTATION_STATUS: test={name}")
                        on_line("INSTRUMENTATION_STATUS_CODE: 1")
                        on_line("INSTRUMENTATION_STATUS: class=example.BulkActionsTest")
                        on_line(f"INSTRUMENTATION_STATUS: test={name}")
                        if stack:
                            on_line(f"INSTRUMENTATION_STATUS: stack={stack}")
                        on_line(f"INSTRUMENTATION_STATUS_CODE: {code}")

                    for index in range(4):
                        report(f"preSignalPass{index}", 0)
                    self.runner.interrupt(signal.SIGINT)
                    report("cancellingSelectionLeavesDocumentsUntouched", -2, "post-signal fail")
                    report("aDeleteActionRemovesTheSelectedDocument", -1, "post-signal error")
                    return CompletedCommand(spec.argv, -15, "")
                if spec.name == "device-isolation-execution-active":
                    raise AssertionError("active-owner probe ran after interrupt")
                return CompletedCommand(spec.argv, 0, "")

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            commands = InterruptedPostflightCommands()
            commands.runner = runner
            runner.commands = commands

            with self.assertRaises(RunnerFailure) as raised:
                runner._instrument_once([])

        expected = [f"preSignalPass{index}" for index in range(4)]
        self.assertEqual(ExitCode.INTERRUPTED, raised.exception.code)
        self.assertEqual(signal.SIGINT, runner._interrupted)
        self.assertEqual(expected, [result.key.method for result in runner.state.tests])
        self.assertEqual(expected, [result.key.method for result in runner.renderer.finished])
        self.assertEqual(6, len(runner.renderer.finish_calls))
        self.assertEqual(
            [-2, -1],
            [result.raw_status_code for result in runner.renderer.finish_calls[-2:]],
        )
        self.assertNotIn(
            "device-isolation-execution-active",
            [spec.name for spec in commands.specs],
        )

    def test_interrupted_phase_callback_stops_later_phases(self) -> None:
        class InterruptedPhaseCommands(FakeCommands):
            runner = None
            instrument_count = 0

            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.instrument:
                    self.instrument_count += 1
                    on_line("INSTRUMENTATION_STATUS: class=example.PhaseTest")
                    on_line("INSTRUMENTATION_STATUS: test=firstPhase")
                    on_line("INSTRUMENTATION_STATUS_CODE: 1")
                    on_line("INSTRUMENTATION_STATUS: class=example.PhaseTest")
                    on_line("INSTRUMENTATION_STATUS: test=firstPhase")
                    on_line("INSTRUMENTATION_STATUS_CODE: 0")
                    self.runner.interrupt(signal.SIGINT)
                    return CompletedCommand(spec.argv, -15, "")
                return CompletedCommand(spec.argv, 0, "")

        class MultiPhaseRunner:
            attempts = []

            def __init__(self, _config, _commands, _storage, _scenario,
                         instrument, _warn, _notice):
                self.instrument = instrument

            def run(self):
                self.attempts.append(1)
                self.instrument([], 1)
                self.attempts.append(2)
                self.instrument([], 2)
                return False, None, []

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            commands = InterruptedPhaseCommands()
            commands.runner = runner
            runner.commands = commands
            runner.scenario = object()
            runner._assert_no_active_instrumentation = lambda _point: None
            runner._load_owned_uids = lambda: None
            runner._prepare_phase_process = lambda: None

            with patch("mjpdf_runner.runner.PhaseRunner", MultiPhaseRunner):
                with self.assertRaises(RunnerFailure) as raised:
                    runner._run_tests()

        self.assertEqual(ExitCode.INTERRUPTED, raised.exception.code)
        self.assertEqual([1], MultiPhaseRunner.attempts)
        self.assertEqual(1, commands.instrument_count)
        self.assertEqual(["firstPhase"], [
            result.key.method for result in runner.state.tests
        ])

    def test_skipped_prerequisite_phase_is_an_operational_error(self) -> None:
        reason = "scenario PREREQUISITE stopped after prerequisite phase 1 reported SKIP"

        class AbortedPhaseRunner:
            def __init__(self, *_args, **_kwargs):
                pass

            def run(self):
                return False, None, [], reason

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.scenario = object()
            runner._assert_no_active_instrumentation = lambda _point: None
            runner._load_owned_uids = lambda: None
            runner.state.tests.append(TestResult(
                TestKey("example.PrerequisiteTest", "phaseOne"), 1, -4,
                TestStatus.SKIP, 0.1, "capability mismatch",
            ))

            with patch("mjpdf_runner.runner.PhaseRunner", AbortedPhaseRunner):
                runner._run_tests()

        self.assertEqual([reason], runner.run_errors)
        self.assertEqual((True, False), runner._classify_results())

    def test_owner_probe_failure_preserves_completed_results(self) -> None:
        class ProbeFailureCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                if spec.name == "services-path":
                    return CompletedCommand(spec.argv, 0, "package:/services.apk")
                if spec.instrument:
                    for line in (
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=completed",
                        "INSTRUMENTATION_STATUS_CODE: 1",
                        "INSTRUMENTATION_STATUS: class=example.ScanTest",
                        "INSTRUMENTATION_STATUS: test=completed",
                        "INSTRUMENTATION_STATUS_CODE: 0",
                        "INSTRUMENTATION_CODE: 0",
                    ):
                        on_line(line)
                if spec.name == "device-isolation-execution-active":
                    return CompletedCommand(spec.argv, 17, "transport unavailable")
                return CompletedCommand(spec.argv, 0, "")

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = ProbeFailureCommands()

            with self.assertRaises(RunnerFailure) as raised:
                runner._instrument_once([])

        self.assertEqual(ExitCode.DEVICE_OR_PIN, raised.exception.code)
        self.assertEqual(
            ["completed"], [result.key.method for result in runner.state.tests])
        self.assertEqual(
            ["completed"], [result.key.method for result in runner.renderer.finished])

    def test_observed_isolation_outranks_signal_but_signal_alone_does_not(self) -> None:
        def execute(temporary: str, contaminated: bool) -> ExitCode:
            runner, _ = self.make_runner(temporary)
            runner._install_apk = lambda _apk, _package: None
            runner._install_orchestrator = lambda: None
            runner.pins.apply = lambda: "test"
            runner.storage.clear_stale_evidence = lambda: None
            runner.storage.snapshot = lambda _name: None
            runner.fixtures.seed = lambda: None
            runner.storage.sweep = lambda: None
            runner.pins.restore = lambda: None
            runner._collect = lambda: None
            runner._classify_results = lambda: (False, False)

            def interrupted_run() -> None:
                runner._suite_started = True
                runner.interrupt(signal.SIGINT)
                if contaminated:
                    runner._contamination_observed = True
                    raise RunnerFailure(
                        ExitCode.DEVICE_OR_PIN,
                        "device isolation failure: com.foreign.app",
                    )
                raise RunnerFailure(ExitCode.INTERRUPTED, "interrupted by signal 2")

            runner._run_tests = interrupted_run
            return runner.run()

        with tempfile.TemporaryDirectory() as contaminated_dir:
            contaminated_code = execute(contaminated_dir, True)
        with tempfile.TemporaryDirectory() as interrupted_dir:
            interrupted_code = execute(interrupted_dir, False)

        self.assertEqual(ExitCode.DEVICE_OR_PIN, contaminated_code)
        self.assertEqual(ExitCode.INTERRUPTED, interrupted_code)

    def test_api37_unsupported_activity_command_output_fails_closed(self) -> None:
        class UnsupportedCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                self.specs.append(spec)
                return CompletedCommand(
                    spec.argv, 0,
                    "Unknown command: instrumentation\n"
                    "Bad activity command, or no activities match: instrumentation\n"
                    "Use -h for help.",
                )

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            commands = UnsupportedCommands()
            runner.commands = commands

            with self.assertRaises(RunnerFailure) as raised:
                runner._active_instrumentation_packages("api37-active-owner")

        self.assertEqual(ExitCode.DEVICE_OR_PIN, raised.exception.code)
        self.assertIn("unsupported command or service error", str(raised.exception))
        self.assertEqual(
            ("shell", "dumpsys", "activity", "processes"),
            commands.specs[0].argv[-4:],
        )

    def test_command_output_is_durable_without_runner_log_duplication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            logs = root / "logs"
            logs.mkdir()
            runner_log = root / "runner.log"
            commands = CommandRunner(runner_log, logs, root / "instrument-raw.txt")

            completed = commands.run(CommandSpec(
                (sys.executable, "-c", "print('durable-' + 'payload')"),
                "large-evidence", mutate=False,
            ))

            self.assertEqual("durable-payload", completed.output)
            self.assertIn("durable-payload", (logs / "large-evidence.log").read_text())
            self.assertNotIn("durable-payload", runner_log.read_text())

    def test_observer_boundary_discards_stale_phase_evidence(self) -> None:
        class RunningProcess:
            @staticmethod
            def poll():
                return None

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            logs = root / "logs"
            logs.mkdir()
            path = logs / "observer.log"
            path.write_text(
                "90.0 stale prior phase\n"
                "100.0 current-phase-marker\n"
                "101.0 fresh current phase\n",
                encoding="utf-8",
            )
            stream = path.open("ab")
            commands = CommandRunner(
                root / "runner.log", logs, root / "instrument-raw.txt")
            spec = CommandSpec(("observer",), "observer", mutate=False)
            observer = CommandObserver(spec, RunningProcess(), stream, path)
            try:
                commands.wait_observer(observer, "current-phase-marker")
                current = list(commands.observer_lines(observer))
            finally:
                stream.close()

        self.assertEqual(["101.0 fresh current phase"], current)

    def test_observer_stop_retains_bounded_tail_without_materializing_file(self) -> None:
        class ExitedProcess:
            returncode = 1

            @staticmethod
            def poll():
                return 1

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            logs = root / "logs"
            logs.mkdir()
            path = logs / "observer.log"
            path.write_text(
                "".join(f"{index:04d} evidence payload\n" for index in range(5000)),
                encoding="utf-8",
            )
            stream = path.open("ab")
            commands = CommandRunner(
                root / "runner.log", logs, root / "instrument-raw.txt")
            spec = CommandSpec(("observer",), "observer", mutate=False)
            observer = CommandObserver(spec, ExitedProcess(), stream, path)

            with patch.object(Path, "read_text",
                              side_effect=AssertionError("observer file materialized")):
                completed = commands.stop_observer(observer)
                line_count = sum(1 for _line in commands.observer_lines(observer))

        self.assertLessEqual(len(completed.output.encode("utf-8")), 65536)
        self.assertEqual(5000, line_count)


    def test_isolation_evidence_command_failure_is_device_failure(self) -> None:
        class FailedCommands(FakeCommands):
            def run(self, spec, on_line=None, check=True):
                return CompletedCommand(spec.argv, 17, "transport unavailable")

        with tempfile.TemporaryDirectory() as temporary:
            runner, _ = self.make_runner(temporary)
            runner.commands = FailedCommands()

            with self.assertRaises(RunnerFailure) as raised:
                runner._active_instrumentation_packages("active-owner")

        self.assertEqual(ExitCode.DEVICE_OR_PIN, raised.exception.code)
        self.assertIn("evidence command active-owner exited 17", str(raised.exception))

class HostResourceMonitorTests(unittest.TestCase):
    def test_records_pressure_and_swap_counters_and_stops_on_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "pressure").mkdir()
            (root / "meminfo").write_text("MemAvailable: 123 kB\nSwapTotal: 1000 kB\nSwapFree: 200 kB\n")
            (root / "vmstat").write_text("pswpin 7\npswpout 8\npgmajfault 9\n")
            (root / "pressure" / "memory").write_text(
                "some avg10=1.25 avg60=2.50 avg300=3.00 total=4000\n")
            monitor = HostResourceMonitor(root / "samples.jsonl", root, interval=60)
            with self.assertRaisesRegex(RuntimeError, "run failed"):
                with monitor:
                    raise RuntimeError("run failed")
            records = [json.loads(line) for line in monitor.output.read_text().splitlines()]
            self.assertEqual(2, len(records))
            self.assertEqual(123, records[0]["meminfo"]["MemAvailable"])
            self.assertEqual(7, records[0]["vmstat"]["pswpin"])
            self.assertEqual(1.25, records[0]["pressure/memory"]["some"]["avg10"])
            self.assertEqual(4000, records[0]["pressure/memory"]["some"]["total"])
            self.assertIn("pressure/cpu", records[0]["unavailable"])
            self.assertFalse(monitor._thread.is_alive())

    def test_unavailable_telemetry_does_not_fail_the_run(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            monitor = HostResourceMonitor(root / "samples.jsonl", root / "absent")
            with monitor:
                pass
            self.assertEqual(5, len(monitor.sample()["unavailable"]))
            self.assertFalse(monitor._thread.is_alive())

    def test_unwritable_output_does_not_start_a_sampling_thread(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with HostResourceMonitor(root / "absent" / "samples.jsonl", root) as monitor:
                self.assertIsNone(monitor._thread)


if __name__ == "__main__":
    unittest.main()
