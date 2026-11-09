from __future__ import annotations

import json
import os
import re
import tempfile
import xml.etree.ElementTree as ET
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, TextIO

from .model import RunPaths, RunState, RunSummary, RunnerConfig, TestResult, TestStatus



def status_from_code(code: int) -> TestStatus:
    return {0: TestStatus.PASS, -1: TestStatus.ERROR, -2: TestStatus.FAIL,
            -3: TestStatus.SKIP, -4: TestStatus.SKIP}.get(code, TestStatus.FAIL)
def _xml_text(value: str) -> str:
    return "".join(
        char
        if (
            char in "\t\n\r"
            or "\u0020" <= char <= "\ud7ff"
            or "\ue000" <= char <= "\ufffd"
            or "\U00010000" <= char <= "\U0010ffff"
        )
        else "\ufffd"
        for char in value
    )




def write_junit(tests: Iterable[TestResult], directory: Path, *,
                run_errors: Iterable[str] = (),
                runner_failure: str | None = None) -> None:
    grouped: dict[str, list[TestResult]] = defaultdict(list)
    for test in tests:
        grouped[test.key.class_name].append(test)
    for cls in sorted(grouped):
        cases = grouped[cls]
        suite = ET.Element("testsuite", {
            "name": _xml_text(cls),
            "tests": str(len(cases)),
            "failures": str(sum(t.status is TestStatus.FAIL for t in cases)),
            "errors": str(sum(t.status is TestStatus.ERROR for t in cases)),
            "skipped": str(sum(t.status is TestStatus.SKIP for t in cases)),
            "time": f"{sum(t.duration_seconds for t in cases):.3f}",
        })
        for case in cases:
            node = ET.SubElement(suite, "testcase", {
                "classname": _xml_text(cls),
                "name": _xml_text(case.key.method),
                "time": f"{case.duration_seconds:.3f}",
            })
            if case.status is TestStatus.FAIL:
                ET.SubElement(node, "failure", {
                    "message": _xml_text(case.first_error_line or case.status.value),
                }).text = _xml_text(case.stack)
            elif case.status is TestStatus.ERROR:
                ET.SubElement(node, "error", {
                    "message": _xml_text(case.first_error_line or "error"),
                }).text = _xml_text(case.stack)
            elif case.status is TestStatus.SKIP:
                ET.SubElement(node, "skipped", {
                    "message": _xml_text(case.first_error_line or case.status.value),
                })
        safe = re.sub(r"[^A-Za-z0-9._-]", "_", cls)
        ET.ElementTree(suite).write(directory / f"TEST-{safe}.xml",
                                    encoding="UTF-8", xml_declaration=True)

    errors = [message for message in run_errors if message]
    if runner_failure:
        errors.append(runner_failure)
    if errors:
        suite = ET.Element("testsuite", {
            "name": "mjpdf.runner",
            "tests": "1",
            "failures": "0",
            "errors": "1",
            "skipped": "0",
            "time": "0.000",
        })
        case = ET.SubElement(suite, "testcase", {
            "classname": "mjpdf.runner",
            "name": "runnerVerdict",
            "time": "0.000",
        })
        ET.SubElement(case, "error", {"message": _xml_text(errors[0])}).text = _xml_text(
            "\n".join(errors))
        ET.ElementTree(suite).write(directory / "TEST-mjpdf-runner.xml",
                                    encoding="UTF-8", xml_declaration=True)


def _iso(timestamp: float) -> str:
    return datetime.fromtimestamp(timestamp, timezone.utc).isoformat()


def write_summary(config: RunnerConfig, paths: RunPaths, state: RunState,
                  summary: RunSummary, profile: str, scenario: dict[str, object] | None) -> None:
    tests_json = [{
        "class": test.key.class_name,
        "name": test.key.method,
        "phase": test.phase,
        "status": test.status.value,
        "rawStatusCode": test.raw_status_code,
        "durationSeconds": round(test.duration_seconds, 6),
        "firstErrorLine": test.first_error_line,
    } for test in summary.tests]
    payload = {
        "schemaVersion": 1,
        "run": {"id": config.run_id, "path": str(paths.root)},
        "profile": profile,
        "device": config.serial,
        "renderer": config.renderer_lane.value,
        "filters": {"class": list(config.classes), "package": list(config.packages),
                    "annotation": list(config.annotations), "notAnnotation": list(config.not_annotations),
                    "testFile": str(config.test_file) if config.test_file else None},
        "scenario": scenario,
        "startedAt": _iso(summary.started_at),
        "finishedAt": _iso(summary.finished_at),
        "durationSeconds": round(summary.finished_at - summary.started_at, 6),
        "exitCode": int(summary.exit_code),
        "verdict": summary.verdict,
        "warningCount": summary.warning_count,
        "bundleCount": summary.bundle_count,
        "leftoverCount": summary.leftover_count,
        "runErrors": list(summary.run_errors),
        "steps": [{"name": step.name, "state": step.state.value,
                   "startedAt": step.started_at, "finishedAt": step.finished_at,
                   "detail": step.detail} for step in state.steps],
        "tests": tests_json,
    }
    fd, temporary = tempfile.mkstemp(prefix=".summary-", suffix=".json", dir=paths.root)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2, sort_keys=True)
            fh.write("\n")
            fh.flush()
            os.fsync(fh.fileno())
        os.replace(temporary, paths.root / "summary.json")
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    counts = {status: sum(test.status is status for test in summary.tests) for status in TestStatus}
    lines = [
        f"run       {config.run_id}", f"device    {config.serial}", f"profile   {profile}",
        f"renderer  {config.renderer_lane.value} lane", f"warnings  {summary.warning_count}",
        f"bundles   {summary.bundle_count} failure bundle(s) collected",
        f"tests     {len(summary.tests)} total, {counts[TestStatus.PASS]} passed, "
        f"{counts[TestStatus.FAIL] + counts[TestStatus.ERROR]} failed, "
        f"{counts[TestStatus.SKIP]} skipped",
        f"verdict   {summary.verdict} (exit {int(summary.exit_code)})",
        f"run path  {paths.root}",
    ]
    (paths.root / "summary.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (paths.root / "results.txt").write_text("\n".join(
        f"{test.status.value:9} {test.duration_seconds:7.3f}s {test.key.display()}"
        for test in summary.tests) + ("\n" if summary.tests else ""), encoding="utf-8")

def _console_test_name(test: TestResult) -> str:
    return f"{test.key.class_name.rsplit('.', 1)[-1]}#{test.key.method}"


def print_final(summary: RunSummary, path: Path, stream: TextIO,
                include_tests: bool) -> None:
    if include_tests:
        for test in summary.tests:
            print(f"TEST {test.status.value:9} {test.duration_seconds:7.3f}s "
                  f"{_console_test_name(test)}", file=stream)
    print(f"RUN {summary.verdict} exit={int(summary.exit_code)} "
          f"tests={len(summary.tests)}", file=stream)
    print(f"ARTIFACTS {path}", file=stream)
