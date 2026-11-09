from __future__ import annotations

from io import StringIO
import sys
import unittest
from pathlib import Path

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.model import (ExitCode, RunState, RunSummary, StepRecord, StepState,
                                TestKey, TestResult, TestStatus)
from mjpdf_runner.results import print_final
from mjpdf_runner.terminal import InlinePanel
from mjpdf_runner.ui import TTYRenderer, build_frame


class TUIFrameTests(unittest.TestCase):
    def test_frame_wraps_content_and_nests_three_tests_under_run_step(self) -> None:
        state = RunState(100.0, Path("/tmp/runs/example"), title="Document intake")
        state.steps = [
            StepRecord("Build", StepState.COMPLETE, 100.0, 106.8),
            StepRecord("Run tests", StepState.RUNNING, 107.0),
            StepRecord("Collect artifacts"),
        ]
        state.total_tests = 54
        state.tests = [
            TestResult(TestKey("example.IntakeTest", f"case{index}"), None, 0,
                       TestStatus.PASS, float(index))
            for index in range(1, 6)
        ]
        state.current_test = TestKey("example.IntakeTest", "case6")
        state.current_test_started_at = 110.0

        frame = build_frame(state, width=120, height=30, elapsed=70.0, now=112.0)

        self.assertEqual(9, len(frame))
        self.assertTrue(all(len(line) == len(frame[0]) for line in frame))
        self.assertLess(len(frame[0]), 120)
        self.assertIn("MJ PDF · Document intake", frame[0])
        self.assertIn("01:10", frame[0])
        self.assertTrue(any("✓ Build" in line and "6.8s" in line for line in frame))
        run_row = next(index for index, line in enumerate(frame) if "Run tests" in line)
        self.assertIn("5/54 · 5 passed · 0 failed", frame[run_row])
        test_rows = frame[run_row + 1:run_row + 4]
        self.assertEqual(3, len(test_rows))
        self.assertNotIn("case3", "\n".join(test_rows))
        self.assertIn("├─ ✓ IntakeTest.case4", test_rows[0])
        self.assertIn("├─ ✓ IntakeTest.case5", test_rows[1])
        self.assertIn("└─", test_rows[2])
        self.assertIn("case6", test_rows[2])
        self.assertIn("test 00:02", test_rows[2])
        self.assertIn("Collect artifacts", frame[run_row + 4])

    def test_narrow_frame_stays_bounded(self) -> None:
        state = RunState(0.0, Path("runs/example"), title="A very long selected area name")
        state.steps = [StepRecord("Require a device with a very long description", StepState.RUNNING, 1.0)]

        frame = build_frame(state, width=40, height=12, elapsed=2.0, now=3.0)

        self.assertEqual(4, len(frame))
        self.assertTrue(all(len(line) == 40 for line in frame))
        self.assertTrue(frame[0].startswith("╭"))
        self.assertTrue(frame[-1].endswith("╯"))

    def test_inline_panel_uses_carriage_returns_while_terminal_is_raw(self) -> None:
        stream = StringIO()
        panel = InlinePanel(stream)

        panel.draw(["first", "second"])
        panel.draw(["updated"])

        output = stream.getvalue()
        self.assertIn("\x1b[2Kfirst\r\n\x1b[2Ksecond\r\n", output)
        self.assertIn("\x1b[2F\x1b[2Kupdated\r\n\x1b[2K\r\n", output)

    def test_final_panel_is_printed_after_the_result_list(self) -> None:
        stream = StringIO()
        panel = InlinePanel(stream)
        renderer = TTYRenderer(stream, panel)
        state = RunState(100.0, Path("/tmp/runs/example"), title="Document intake")
        state.steps = [StepRecord("Run tests", StepState.COMPLETE, 100.0, 101.0)]
        result = TestResult(TestKey("example.IntakeTest", "opensPdf"), None, 0,
                            TestStatus.PASS, 1.0)
        state.tests = [result]
        renderer._state = state
        renderer._started = 100.0
        summary = RunSummary(
            ExitCode.CLEAN, "CLEAN", 100.0, 101.0, [result], [], 0, 0, 0,
        )

        renderer.close()
        print_final(summary, state.artifact_dir, stream, include_tests=True)
        renderer.print_final_panel()

        output = stream.getvalue()
        result_at = output.index("PASS")
        panel_at = output.rindex("╭ MJ PDF")
        self.assertLess(result_at, panel_at)
        self.assertTrue(output.rstrip().endswith("╯"))


if __name__ == "__main__":
    unittest.main()
