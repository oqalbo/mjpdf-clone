from __future__ import annotations

import sys
import unittest
from unittest.mock import patch
from pathlib import Path

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.catalog import TestSpec, TestUnit
from mjpdf_runner.devices import DeviceInfo
from mjpdf_runner.interactive import SetupWizard, _support_label, device_rows
from mjpdf_runner.model import RendererLane
from mjpdf_runner.planner import automatic_plan, every_plan, test_compatible


class FakePanel:
    def __init__(self) -> None:
        self.size = type("Size", (), {"columns": 100, "lines": 30})()
        self.frames: list[list[str]] = []

    def draw(self, lines: list[str]) -> None:
        self.frames.append(lines)


class FakeKeys:
    def __init__(self, *keys: str):
        self.keys = iter(keys)

    def read(self) -> str:
        return next(self.keys)


class SetupWizardTests(unittest.TestCase):
    def setUp(self) -> None:
        repo = RUNNER_DIR.parents[1]
        self.panel = FakePanel()
        self.wizard = SetupWizard(repo, RUNNER_DIR, "adb", Path("/lease"),
                                  "test-agent", self.panel)

    def test_emulator_sections_align_across_different_device_metadata(self) -> None:
        devices = (
            DeviceInfo("emulator-5554", "Android SDK built for x86_64", "9", 28,
                       "x86_64", "1080x1920", "420", 3947, RendererLane.HARDWARE,
                       "hardware", True, "free"),
            DeviceInfo("emulator-5568", "sdk_gphone16k_x86_64", "17", 37,
                       "x86_64", "1344x2992", "480", 3914, RendererLane.SOFTWARE,
                       "SwiftShader", True, "free"),
        )

        rows = device_rows(devices, {"emulator-5554"}, 1, 0, 2, self.wizard.tests)

        separators = [
            [index for index in range(len(row)) if row.startswith(" · ", index)]
            for row in rows
        ]
        self.assertEqual(separators[0], separators[1])
        self.assertEqual(rows[0].index("emulator-5554"), rows[1].index("emulator-5568"))

    def test_emulator_rows_show_coloured_supported_test_counts(self) -> None:
        devices = (
            DeviceInfo("emulator-5570", "api28-sw", "9", 28, "x86_64", "1080x2400",
                       "440", 4096, RendererLane.SOFTWARE, "SwiftShader", True, "free"),
            DeviceInfo("emulator-5584", "api36-sw", "16", 36, "x86_64", "1080x2400",
                       "440", 4096, RendererLane.SOFTWARE, "SwiftShader", True, "free"),
        )

        rows = device_rows(devices, set(), 0, 0, 2, self.wizard.tests)

        total = len(self.wizard.tests)
        old_supported = sum(test_compatible(test, devices[0]) for test in self.wizard.tests)
        self.assertLess(old_supported, total)
        self.assertIn(f"{old_supported}/{total}", rows[0])
        self.assertIn(f"{total}/{total}", rows[1])
        self.assertIn(f"\x1b[38;2;0;255;0m{total}/{total}\x1b[0m", rows[1])

    def test_emulator_rows_colour_zero_support_red(self) -> None:
        device = DeviceInfo(
            "emulator-5554", "hardware", "15", 35, "x86_64", "1080x2400", "440",
            4096, RendererLane.HARDWARE, "hardware", True, "free",
        )
        test = TestSpec(
            selector="example.Pixels#renders", class_name="example.Pixels", method="renders",
            package="baseline", area="rendering", ignored=False, hostile=False,
            software_only=True, atomic_key="pixels",
            required_profile="api36-1080x2400-440dpi-3button-en-US-swiftshader",
        )

        row = device_rows((device,), set(), 0, 0, 1, (test,))[0]

        self.assertIn("\x1b[38;2;255;0;0m0/1\x1b[0m", row)

    def test_support_colour_uses_the_meaningful_top_ten_percent(self) -> None:
        self.assertEqual("\x1b[38;2;255;0;0m90/100\x1b[0m", _support_label(90, 100))
        self.assertEqual("\x1b[38;2;255;255;0m95/100\x1b[0m", _support_label(95, 100))
        self.assertEqual("\x1b[38;2;0;255;0m100/100\x1b[0m", _support_label(100, 100))

    def test_area_screen_can_replace_select_all_with_one_area(self) -> None:
        advanced = self.wizard._area_screen(FakeKeys("a", "down", "space", "n"))

        self.assertTrue(advanced)
        self.assertEqual({"intake"}, self.wizard.selected_areas)
        rendered = "\n".join(self.panel.frames[-1])
        self.assertIn("Document intake · 55 tests", rendered)
        self.assertIn("1 areas · 55 unique tests", rendered)

    def test_area_screen_rejects_empty_selection(self) -> None:
        keys = FakeKeys("a", "n", "space", "n")

        self.assertTrue(self.wizard._area_screen(keys))

        self.assertEqual({"entry"}, self.wizard.selected_areas)
        rendered = "\n".join(line for frame in self.panel.frames for line in frame)
        self.assertIn("Select at least one test area.", rendered)

    def test_review_runs_every_plan_after_skipping_incompatible_hardware(self) -> None:
        hardware = DeviceInfo(
            "emulator-5566", "hardware", "15", 37, "x86_64", "1080x2400", "440",
            4096, RendererLane.HARDWARE, "hardware", True, "free",
        )
        software = DeviceInfo(
            "emulator-5570", "software", "9", 28, "x86_64", "1080x1920", "420",
            4096, RendererLane.SOFTWARE, "SwiftShader", True, "free",
        )
        test = TestSpec(
            selector="example.Pixels#renders",
            class_name="example.Pixels",
            method="renders",
            package="example",
            area="rendering",
            ignored=False,
            hostile=False,
            software_only=True,
            atomic_key="pixels",
        )
        unit = TestUnit("pixels", "rendering", test.selector, (test,), True, False, None)
        devices = (hardware, software)
        plan = every_plan((unit,), devices)
        self.wizard.devices = devices
        self.wizard.selected_serials = {device.serial for device in devices}

        advanced = self.wizard._review_screen(FakeKeys("n"), plan, (unit,))

        rendered = "\n".join(self.panel.frames[-1])
        self.assertTrue(advanced)
        self.assertIn("Skipped       1 incompatible execution(s)", rendered)
        self.assertIn("[ Run: N ]", rendered)
        self.assertNotIn("[ Blocked: N ]", rendered)

    def test_automatic_preview_shows_cost_and_explicit_fallbacks(self) -> None:
        device = DeviceInfo("a", "unmeasured", "17", 37, "x86_64", "1080x2400",
                            "440", 4096, RendererLane.HARDWARE, "hardware", True, "free")
        test = TestSpec("example.Test#run", "example.Test", "run", "example", "entry",
                        False, False, False, "one")
        unit = TestUnit("one", "entry", test.selector, (test,), False, False, None)
        self.wizard.devices = (device,)
        self.wizard.selected_serials = {device.serial}
        plan = automatic_plan((unit,), (device,))

        self.assertTrue(self.wizard._preview_screen(FakeKeys("n"), plan))

        rendered = "\n".join(self.panel.frames[-1])
        self.assertIn("duration category", rendered)
        self.assertIn("min work", rendered)
        self.assertIn("1 unmarked test(s) use NORMAL", rendered)
        self.assertIn("Uncalibrated emulator profile", rendered)
        self.assertNotIn("Balanced by source test count", rendered)


if __name__ == "__main__":
    unittest.main()
