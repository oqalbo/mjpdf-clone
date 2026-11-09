from __future__ import annotations

import io
import sys
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path
from unittest.mock import patch

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

import run


class LeaseOptionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo_root = Path(self.temp.name) / "repo"
        self.repo_root.mkdir()

    def options(self, *args: str):
        return run.parser().parse_args([*args, "--dry-run"])

    def test_a_missing_default_lease_script_stops_with_exit_status_3(self) -> None:
        stderr = io.StringIO()
        with redirect_stderr(stderr):
            found = run._lease_script(self.repo_root, self.options())
        self.assertEqual(3, found)
        self.assertIn("--no-lease", stderr.getvalue())

    def test_a_missing_given_lease_script_stops_with_exit_status_3(self) -> None:
        missing = Path(self.temp.name) / "missing.sh"
        with redirect_stderr(io.StringIO()):
            found = run._lease_script(self.repo_root, self.options("--lease-script", str(missing)))
        self.assertEqual(3, found)

    def test_the_default_lease_script_next_to_the_repository_is_used(self) -> None:
        script = self.repo_root.parent / "Scripts" / "android-emulator" / "lease-an-emulator.sh"
        script.parent.mkdir(parents=True)
        script.write_text("")
        found = run._lease_script(self.repo_root, self.options())
        self.assertIsInstance(found, Path)
        self.assertEqual(script.resolve(), found.resolve())

    def test_a_given_lease_script_is_used(self) -> None:
        script = Path(self.temp.name) / "lease.sh"
        script.write_text("")
        self.assertEqual(script, run._lease_script(self.repo_root, self.options("--lease-script", str(script))))

    def test_no_lease_runs_unleased_and_warns(self) -> None:
        stderr = io.StringIO()
        with redirect_stderr(stderr):
            found = run._lease_script(self.repo_root, self.options("--no-lease"))
        self.assertIsNone(found)
        self.assertIn("WARNING", stderr.getvalue())

    def test_no_lease_cannot_be_combined_with_a_lease_script(self) -> None:
        argument_parser = run.parser()
        options = argument_parser.parse_args(["--no-lease", "--lease-script", "x.sh"])
        with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            run._check_shared(options, argument_parser)
        self.assertEqual(2, raised.exception.code)

    def test_the_lease_agent_option_wins(self) -> None:
        with patch.dict("os.environ", {"EMU_AGENT": "from-environment"}):
            self.assertEqual("given", run._lease_agent(self.options("--lease-agent", "given"), "cli"))


class InteractiveRoutingTests(unittest.TestCase):
    def test_leasing_options_alone_open_the_interactive_tui(self) -> None:
        with patch("run.interactive_main", return_value=0) as interactive:
            self.assertEqual(0, run.main(["--no-lease", "--force-stop", "com.example.app"]))
        shared = interactive.call_args.args[2]
        self.assertTrue(shared.no_lease)
        self.assertEqual(["com.example.app"], shared.force_stop)

    def test_run_options_go_to_the_command_line_runner(self) -> None:
        with patch("run.interactive_main") as interactive, patch("run.Runner") as runner_type, \
                patch("run.HostResourceMonitor"), patch("run._create_paths",
                                                        return_value=("id", unittest.mock.Mock())), \
                patch("run.resolve_fixture_requirements", return_value=(None, "test")), \
                patch("run.print_final"), patch("run.make_renderer"):
            runner_type.return_value.run.return_value = 0
            self.assertEqual(0, run.main(["--dry-run", "--force-stop", "com.example.app"]))
        interactive.assert_not_called()
        config = runner_type.call_args.args[0]
        self.assertEqual(("com.example.app",), config.force_stop_packages)


if __name__ == "__main__":
    unittest.main()
