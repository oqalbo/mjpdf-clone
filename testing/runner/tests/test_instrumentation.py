from __future__ import annotations

import sys
import unittest
from pathlib import Path

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.instrumentation import (
    InstrumentationParser,
    RunError,
    RunFinished,
    TestFinished,
    TestStarted,
    build_direct_command,
)
from mjpdf_runner.model import TestKey


class InstrumentationParserTests(unittest.TestCase):
    def feed(self, parser: InstrumentationParser, lines: list[str]) -> list[object]:
        events: list[object] = []
        for now, line in enumerate(lines, start=1):
            events.extend(parser.feed(line, float(now)))
        return events

    def test_bare_process_crash_is_attributed_to_the_running_test(self) -> None:
        parser = InstrumentationParser()
        key = TestKey("example.CrashTest", "crashes")

        events = self.feed(
            parser,
            [
                "INSTRUMENTATION_STATUS: class=example.CrashTest",
                "INSTRUMENTATION_STATUS: test=crashes",
                "INSTRUMENTATION_STATUS_CODE: 1",
                "INSTRUMENTATION_RESULT: shortMsg=Process crashed.",
                "INSTRUMENTATION_CODE: 0",
            ],
        )
        events.extend(parser.finish(6.0))

        self.assertEqual([TestStarted, TestFinished, RunFinished], [type(event) for event in events])
        finished = next(event for event in events if isinstance(event, TestFinished))
        self.assertEqual(key, finished.key)
        self.assertEqual(-1, finished.raw_code)
        self.assertIn("Process crashed", finished.stack)

    def test_crash_result_does_not_turn_an_attributed_test_crash_into_a_run_error(self) -> None:
        parser = InstrumentationParser()

        events = self.feed(
            parser,
            [
                "INSTRUMENTATION_STATUS: class=example.CrashTest",
                "INSTRUMENTATION_STATUS: test=crashes",
                "INSTRUMENTATION_STATUS_CODE: 1",
                "INSTRUMENTATION_STATUS: class=example.CrashTest",
                "INSTRUMENTATION_STATUS: stack=Test instrumentation process crashed.",
                "INSTRUMENTATION_STATUS: test=crashes",
                "INSTRUMENTATION_STATUS_CODE: -2",
                "INSTRUMENTATION_RESULT: shortMsg=Process crashed.",
                "INSTRUMENTATION_CODE: 0",
            ],
        )
        events.extend(parser.finish(10.0))

        finished = [event for event in events if isinstance(event, TestFinished)]
        self.assertEqual(1, len(finished))
        self.assertEqual(-2, finished[0].raw_code)
        self.assertFalse(any(isinstance(event, RunError) for event in events))

    def test_process_crash_without_a_running_test_remains_a_run_error(self) -> None:
        parser = InstrumentationParser()

        events = self.feed(
            parser,
            [
                "INSTRUMENTATION_RESULT: shortMsg=Process crashed.",
                "INSTRUMENTATION_CODE: 0",
            ],
        )

        self.assertTrue(any(isinstance(event, RunError) for event in events))
        self.assertFalse(any(isinstance(event, TestFinished) for event in events))

    def test_direct_phase_command_preserves_the_target_process(self) -> None:
        command = build_direct_command(
            "com.example.test",
            "androidx.test.runner.AndroidJUnitRunner",
            ["-e", "class", "example.PhaseTest#phaseOne"],
        )

        self.assertIn("timeout 150s am instrument -r -w --no-restart", command)
        self.assertIn("-e class 'example.PhaseTest#phaseOne'", command)
        self.assertTrue(command.endswith(
            "com.example.test/androidx.test.runner.AndroidJUnitRunner"))


if __name__ == "__main__":
    unittest.main()
