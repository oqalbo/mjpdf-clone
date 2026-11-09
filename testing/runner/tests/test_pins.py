from __future__ import annotations

import sys
import unittest
from pathlib import Path

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.pins import FOREGROUND_THIEVES, force_stop_packages


class ForceStopPackageTests(unittest.TestCase):
    def test_default_list_is_the_platform_packages_only(self) -> None:
        self.assertEqual(FOREGROUND_THIEVES, force_stop_packages())

    def test_extra_packages_are_added_once(self) -> None:
        self.assertEqual(
            (*FOREGROUND_THIEVES, "com.example.one", "com.example.two"),
            force_stop_packages(("com.example.one", "com.example.two", "com.android.vending")),
        )


if __name__ == "__main__":
    unittest.main()
