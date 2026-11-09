from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, call, patch

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.devices import DeviceDiscoveryError, LeaseManager, discover_devices
from run import _run_with_shared_lease


class DeviceDiscoveryTests(unittest.TestCase):
    def test_selected_discovery_never_probes_an_excluded_device(self) -> None:
        listed = subprocess.CompletedProcess([], 0, "List of devices attached\nemulator-5554\tdevice\nemulator-5580\tdevice\n")
        owned = subprocess.CompletedProcess([], 0, "mine\n")
        with patch("mjpdf_runner.devices._run", side_effect=[listed, owned]) as run, patch(
                "mjpdf_runner.devices._adb", return_value="") as adb:
            devices = discover_devices("adb", Path("/lease"), "test-agent",
                                       selected_serials=("emulator-5554",))
        self.assertEqual(["emulator-5554"], [device.serial for device in devices])
        self.assertTrue(adb.call_args_list)
        self.assertTrue(all(item.args[1] == "emulator-5554" for item in adb.call_args_list))
        self.assertEqual(["/lease", "check", "emulator-5554"], run.call_args_list[1].args[0])

    def test_discovery_without_a_lease_script_lists_every_emulator_as_free(self) -> None:
        listed = subprocess.CompletedProcess([], 0, "List of devices attached\nemulator-5554\tdevice\n")
        with patch("mjpdf_runner.devices._run", side_effect=[listed]) as run, patch(
                "mjpdf_runner.devices._adb", return_value=""):
            devices = discover_devices("adb", None, "test-agent")
        self.assertEqual(1, run.call_count)
        self.assertEqual([("emulator-5554", True, "free")],
                         [(device.serial, device.available, device.lease) for device in devices])


class LeaseManagerTests(unittest.TestCase):
    free = subprocess.CompletedProcess([], 1, "free\n", "")
    mine = subprocess.CompletedProcess([], 0, "mine\n", "")

    @staticmethod
    def stop_renewal(manager: LeaseManager) -> None:
        manager._stop.set()
        assert manager._thread is not None
        manager._thread.join(timeout=2)

    def test_claim_requests_all_free_selected_serials_atomically(self) -> None:
        manager = LeaseManager(Path("/lease-an-emulator.sh"), "test-agent", 7200)
        claimed = subprocess.CompletedProcess([], 0, "emulator-5554\nemulator-5568\n", "")

        with patch.object(
            manager,
            "_command",
            side_effect=[self.free, self.free, claimed],
        ) as command:
            manager.claim(("emulator-5554", "emulator-5568"))
            self.stop_renewal(manager)

        self.assertEqual([
            call("check", "emulator-5554"),
            call("check", "emulator-5568"),
            call(
                "claim", "--serial", "emulator-5554", "--serial", "emulator-5568",
                "--ttl", "7200", "--task", "MJ PDF interactive test run",
            ),
        ], command.call_args_list)
        self.assertEqual(("emulator-5554", "emulator-5568"), manager.serials)

    def test_partial_atomic_claim_is_rejected(self) -> None:
        manager = LeaseManager(Path("/lease-an-emulator.sh"), "test-agent")
        partial = subprocess.CompletedProcess([], 0, "emulator-5554\n", "")
        released = subprocess.CompletedProcess([], 0, "released emulator-5554\n", "")

        with patch.object(
            manager,
            "_command",
            side_effect=[self.free, self.free, partial, released],
        ) as command:
            with self.assertRaisesRegex(DeviceDiscoveryError, "could not claim"):
                manager.claim(("emulator-5554", "emulator-5568"))

        self.assertEqual((), manager.serials)
        command.assert_called_with("release", "emulator-5554")

    def test_same_agent_lease_is_adopted_without_reclaiming_or_releasing_it(self) -> None:
        manager = LeaseManager(Path("/lease-an-emulator.sh"), "test-agent")

        with patch.object(manager, "_command", return_value=self.mine) as command:
            manager.claim(("emulator-5554",))
            manager.claim(("emulator-5554",))
            manager.release()

        command.assert_called_once_with("check", "emulator-5554")

    def test_mixed_owned_and_free_selection_claims_and_releases_only_free_serials(self) -> None:
        manager = LeaseManager(Path("/lease-an-emulator.sh"), "test-agent")
        claimed = subprocess.CompletedProcess([], 0, "emulator-5568\n", "")
        released = subprocess.CompletedProcess([], 0, "released emulator-5568\n", "")

        with patch.object(
            manager,
            "_command",
            side_effect=[self.mine, self.free, claimed, released],
        ) as command:
            manager.claim(("emulator-5554", "emulator-5568"))
            manager.release()

        self.assertEqual([
            call("check", "emulator-5554"),
            call("check", "emulator-5568"),
            call(
                "claim", "--serial", "emulator-5568", "--ttl", "7200",
                "--task", "MJ PDF interactive test run",
            ),
            call("release", "emulator-5568"),
        ], command.call_args_list)

    def test_foreign_lease_is_rejected_before_any_claim(self) -> None:
        manager = LeaseManager(Path("/lease-an-emulator.sh"), "test-agent")
        held = subprocess.CompletedProcess([], 1, "held by another-agent\n", "")

        with patch.object(manager, "_command", return_value=held) as command:
            with self.assertRaisesRegex(DeviceDiscoveryError, "held by another-agent"):
                manager.claim(("emulator-5554",))

        command.assert_called_once_with("check", "emulator-5554")


class UnleasedManagerTests(unittest.TestCase):
    def test_claim_and_release_without_a_lease_script_run_no_commands(self) -> None:
        manager = LeaseManager(None, "test-agent")
        with patch("mjpdf_runner.devices._run") as run:
            manager.claim(("emulator-5554", "emulator-5568"))
            self.assertEqual(("emulator-5554", "emulator-5568"), manager.serials)
            self.assertIsNone(manager._thread)
            manager.release()
        run.assert_not_called()
        self.assertEqual((), manager.serials)


class CliLeaseTests(unittest.TestCase):
    def test_cli_action_runs_inside_shared_lease_lifecycle(self) -> None:
        lease = Mock()

        with patch("run.LeaseManager", return_value=lease) as lease_type:
            result = _run_with_shared_lease(
                Path("/lease-an-emulator.sh"),
                "cli-agent",
                "emulator-5554",
                lambda: 7,
            )

        self.assertEqual(7, result)
        lease_type.assert_called_once_with(
            Path("/lease-an-emulator.sh"),
            "cli-agent",
            task="MJ PDF CLI test run",
        )
        lease.claim.assert_called_once_with(("emulator-5554",))
        lease.release.assert_called_once_with()

    def test_cli_action_releases_shared_lease_after_failure(self) -> None:
        lease = Mock()

        def fail() -> int:
            raise RuntimeError("runner failed")

        with patch("run.LeaseManager", return_value=lease):
            with self.assertRaisesRegex(RuntimeError, "runner failed"):
                _run_with_shared_lease(
                    Path("/lease-an-emulator.sh"),
                    "cli-agent",
                    "emulator-5554",
                    fail,
                )

        lease.release.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
