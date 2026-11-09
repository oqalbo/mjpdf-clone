from __future__ import annotations

from io import StringIO
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.model import ExitCode, RunSummary, TestKey, TestResult, TestStatus
from mjpdf_runner.results import print_final, write_junit


class JUnitResultTests(unittest.TestCase):
    def result(self, cls: str, method: str, status: TestStatus,
               duration: float) -> TestResult:
        return TestResult(TestKey(cls, method), None, 0, status, duration)

    def read_suite(self, directory: Path, name: str) -> ET.Element:
        return ET.parse(directory / name).getroot()

    def test_failure_and_pass_durations_are_summed_for_the_class(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            failed = self.result("example.One", "fails", TestStatus.FAIL, 1.25)
            passing = self.result("example.One", "passes", TestStatus.PASS, 2.5)

            write_junit([failed, passing], directory)

            suite = self.read_suite(directory, "TEST-example.One.xml")
            self.assertEqual("1", suite.get("failures"))
            self.assertEqual("3.750", suite.get("time"))
            failed_case = suite.find("./testcase[@name='fails']")
            self.assertIsNotNone(failed_case)
            self.assertIsNotNone(failed_case.find("failure"))

    def test_xml_invalid_failure_text_is_replaced_without_losing_unicode(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            class_name = "example.نتيجة😀"
            method = "اختبار عربي 😀"
            stack = "فشل عربي 😀 \ufffe postcondition"
            failed = TestResult(
                TestKey(class_name, method), None, -2,
                TestStatus.FAIL, 1.0, stack,
            )

            write_junit([failed], directory)

            report = next(directory.glob("TEST-*.xml"))
            suite = ET.parse(report).getroot()
            case = suite.find("./testcase")
            failure = case.find("failure")
            self.assertEqual(class_name, suite.get("name"))
            self.assertEqual(method, case.get("name"))
            self.assertEqual(stack.replace("\ufffe", "\ufffd"), failure.get("message"))
            self.assertEqual(stack.replace("\ufffe", "\ufffd"), failure.text)
            self.assertNotIn("\ufffe", report.read_text(encoding="utf-8"))

    def test_each_class_suite_uses_its_own_testcase_durations(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            tests = [
                self.result("example.One", "first", TestStatus.PASS, 1.125),
                self.result("example.One", "second", TestStatus.PASS, 2.25),
                self.result("example.Two", "third", TestStatus.PASS, 4.5),
            ]

            write_junit(tests, directory)

            one = self.read_suite(directory, "TEST-example.One.xml")
            two = self.read_suite(directory, "TEST-example.Two.xml")
            self.assertEqual("3.375", one.get("time"))
            self.assertEqual("4.500", two.get("time"))

    def test_run_error_after_passing_test_emits_failing_runner_suite(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            passing = self.result("example.One", "passes", TestStatus.PASS, 0.25)

            write_junit([passing], directory, run_errors=["Process crashed after collection"])

            suite = self.read_suite(directory, "TEST-mjpdf-runner.xml")
            self.assertEqual("1", suite.get("errors"))
            error = suite.find("./testcase/error")
            self.assertIsNotNone(error)
            self.assertEqual("Process crashed after collection", error.get("message"))

    def test_zero_test_failure_emits_failing_runner_suite(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)

            write_junit([], directory, runner_failure="no tests executed")

            suite = self.read_suite(directory, "TEST-mjpdf-runner.xml")
            self.assertEqual("1", suite.get("tests"))
            self.assertEqual("1", suite.get("errors"))
            self.assertEqual("no tests executed", suite.find("./testcase/error").get("message"))

    def test_final_console_results_omit_the_package_name(self) -> None:
        passing = self.result(
            "com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeManifestTest",
            "onlyTheLauncherAliasAndTheReaderAreExported",
            TestStatus.PASS,
            0.125,
        )
        summary = RunSummary(
            ExitCode.CLEAN, "CLEAN", 1.0, 2.0, [passing], [], 0, 0, 0,
        )
        stream = StringIO()

        print_final(summary, Path("/tmp/run"), stream, include_tests=True)

        output = stream.getvalue()
        self.assertIn(
            "IntakeManifestTest#onlyTheLauncherAliasAndTheReaderAreExported",
            output,
        )
        self.assertNotIn("com.gitlab.mudlej.MjPdfReader", output)
        lines = output.splitlines()
        self.assertTrue(lines[0].startswith("TEST PASS "))
        self.assertEqual("RUN CLEAN exit=0 tests=1", lines[-2])
        self.assertEqual("ARTIFACTS /tmp/run", lines[-1])

    def test_final_console_distinguishes_passing_test_from_operational_failure(
            self) -> None:
        passing = self.result(
            "example.IntakeTest", "opensPdf", TestStatus.PASS, 0.5)
        summary = RunSummary(
            ExitCode.FAILED, "OPERATIONAL FAILURE", 1.0, 2.0,
            [passing], ["artifact collection failed"], 0, 0, 0,
        )
        stream = StringIO()

        print_final(
            summary, Path("/tmp/runs/operational"), stream, include_tests=True)

        lines = stream.getvalue().splitlines()
        self.assertTrue(lines[0].startswith("TEST PASS "))
        self.assertIn("IntakeTest#opensPdf", lines[0])
        self.assertEqual(
            "RUN OPERATIONAL FAILURE exit=1 tests=1", lines[1])
        self.assertEqual(
            "ARTIFACTS /tmp/runs/operational", lines[2])


if __name__ == "__main__":
    unittest.main()
