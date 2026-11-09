#!/usr/bin/env python3

import argparse
import json
import os
import re
import sys

BUDGETS_SOURCE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..",
    "macrobenchmark", "src", "main", "java", "com", "gitlab", "mudlej",
    "MjPdfReader", "macrobenchmark", "PerfBudgets.kt",
)

STARTUP_METRIC = "timeToInitialDisplayMs"
FRAME_DURATION_METRIC = "frameDurationCpuMs"
FRAME_OVERRUN_METRIC = "frameOverrunMs"

STARTUP_BENCHMARKS = {
    "coldStartOfHomeNoCompilation": "COLD_HOME",
    "coldStartOfHomePartialCompilation": "COLD_HOME",
    "warmStartOfHome": "WARM_HOME",
    "hotStartOfHome": "HOT_HOME",
    "coldStartOfHomeWithALargeLibrary": "COLD_HOME_LARGE_LIBRARY",
    "coldStartIntoTheReaderViaAViewIntent": "COLD_READER_VIEW_INTENT",
    "applicationOnCreateDoesNotDominateStartup": "APP_ON_CREATE",
}

FRAME_BENCHMARKS = {
    "scrollingAContinuousDocumentStaysWithinAJankBudget": "CONTINUOUS_SCROLL",
    "scrollingAScannedDocumentStaysWithinAJankBudget": "SCANNED_SCROLL",
    "framesDroppedDuringAPinchZoomGesture": "PINCH_ZOOM",
    "homeLibraryGridScrollsSmoothlyWithAColdCoverCache": "LIBRARY_GRID_SCROLL",
    "homeLibraryGridScrollsSmoothlyWithAWarmCoverCache": "LIBRARY_GRID_SCROLL",
    "thumbnailGridOpensAndScrollsSmoothlyOnAFiveHundredPageDocument": "THUMBNAIL_GRID_SCROLL",
    "foldersTabRendersWithinBudgetWithADeepTree": "FOLDERS_NAVIGATION",
    "readerDrawsSmoothlyOnAPageWithFiveHundredHighlights": "HIGHLIGHTED_PAGE_DRAW",
    "readerDrawsSmoothlyOnAPlainPageAsAControl": "HIGHLIGHTED_PAGE_DRAW",
    "textModeScrollsSmoothlyThroughALargeDocument": "TEXT_MODE_SCROLL",
    "sustainedSessionDegradationRatioFirstSegment": "SUSTAINED_SESSION",
    "sustainedSessionDegradationRatioLastSegment": "SUSTAINED_SESSION",
}

UNIMPLEMENTED_VARIATIONS = {
    "PERF-06": "the auto backup on variation needs host side preference seeding",
    "PERF-28": "the maxZoom variation needs host side preference seeding",
}


class Budgets:
    def __init__(self, startup, frames, ratios):
        self.startup = startup
        self.frames = frames
        self.ratios = ratios


def parse_double(token):
    token = token.strip()
    if token == "Double.MAX_VALUE":
        return float("inf")
    return float(token)


def load_budgets(path):
    if not os.path.isfile(path):
        raise SystemExit(
            "Budget source %s not found. It is the single source of truth for every number this "
            "script compares against, and it lives beside the benchmarks it describes." % path
        )
    text = open(path, encoding="utf-8").read()
    startup = {}
    for name, test_id, median in re.findall(
        r'val (\w+) = Startup\("([A-Z0-9-]+)",\s*([^)]+)\)', text
    ):
        startup[name] = {"id": test_id, "median_ms": parse_double(median)}
    frames = {}
    for name, test_id, p50, p99, overrun in re.findall(
        r'val (\w+) = Frames\(\s*"([A-Z0-9-]+)",\s*([^,]+),\s*([^,]+),\s*([^,)]+),?\s*\)', text
    ):
        frames[name] = {
            "id": test_id,
            "p50_ms": parse_double(p50),
            "p99_ms": parse_double(p99),
            "p99_overrun_ms": parse_double(overrun),
        }
    ratios = {}
    for name, value in re.findall(r'const val (\w+) = ([0-9.]+)', text):
        ratios[name] = float(value)
    if not startup or not frames:
        raise SystemExit(
            "Parsed no budgets out of %s. The declaration shape changed and this script would "
            "otherwise report a clean run while checking nothing." % path
        )
    return Budgets(startup, frames, ratios)


def find_reports(target):
    if os.path.isfile(target):
        return [target]
    found = []
    for root, _dirs, files in os.walk(target):
        for name in files:
            if name.endswith("benchmarkData.json"):
                found.append(os.path.join(root, name))
    return sorted(found)


def load_benchmarks(paths):
    results = {}
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            payload = json.load(handle)
        for entry in payload.get("benchmarks", []):
            results[entry["name"]] = entry
    return results


def metric_value(entry, metric, key):
    for group in ("metrics", "sampledMetrics"):
        holder = entry.get(group, {})
        if metric in holder and key in holder[metric]:
            return holder[metric][key]
    return None


class Report:
    def __init__(self):
        self.checks = []
        self.problems = []

    def check(self, label, observed, limit, unit="ms"):
        if observed is None:
            self.problems.append("%s: metric missing from the report" % label)
            return
        ok = observed <= limit
        self.checks.append((label, observed, limit, unit, ok))
        if not ok:
            self.problems.append(
                "%s: %.2f%s exceeds the budget of %.2f%s" % (label, observed, unit, limit, unit)
            )

    def note_problem(self, message):
        self.problems.append(message)


def check_absolute(report, benchmarks, budgets):
    for method, budget_name in sorted(STARTUP_BENCHMARKS.items()):
        entry = benchmarks.get(method)
        if entry is None:
            report.note_problem("%s: no result in the benchmark report" % method)
            continue
        budget = budgets.startup[budget_name]
        report.check(
            "%s %s median" % (budget["id"], method),
            metric_value(entry, STARTUP_METRIC, "median"),
            budget["median_ms"],
        )

    for method, budget_name in sorted(FRAME_BENCHMARKS.items()):
        entry = benchmarks.get(method)
        if entry is None:
            report.note_problem("%s: no result in the benchmark report" % method)
            continue
        budget = budgets.frames[budget_name]
        label = "%s %s" % (budget["id"], method)
        if budget["p50_ms"] != float("inf"):
            report.check(
                "%s frame duration P50" % label,
                metric_value(entry, FRAME_DURATION_METRIC, "P50"),
                budget["p50_ms"],
            )
        report.check(
            "%s frame duration P99" % label,
            metric_value(entry, FRAME_DURATION_METRIC, "P99"),
            budget["p99_ms"],
        )
        report.check(
            "%s frame overrun P99" % label,
            metric_value(entry, FRAME_OVERRUN_METRIC, "P99"),
            budget["p99_overrun_ms"],
        )


def check_relations(report, benchmarks, budgets):
    cold = benchmarks.get("coldStartOfHomeNoCompilation")
    warm = benchmarks.get("warmStartOfHome")
    large = benchmarks.get("coldStartOfHomeWithALargeLibrary")

    cold_median = metric_value(cold, STARTUP_METRIC, "median") if cold else None

    if cold_median and warm:
        warm_median = metric_value(warm, STARTUP_METRIC, "median")
        report.check(
            "PERF-02 warm start against the cold median",
            warm_median,
            cold_median * budgets.ratios["WARM_BELOW_COLD_FRACTION"],
        )

    if cold_median and large:
        large_median = metric_value(large, STARTUP_METRIC, "median")
        report.check(
            "PERF-04 large library median against the cold median",
            large_median,
            cold_median * budgets.ratios["LARGE_LIBRARY_RATIO"],
        )
        report.check(
            "PERF-04 large library absolute difference",
            large_median - cold_median,
            budgets.ratios["LARGE_LIBRARY_ABSOLUTE_MS"],
        )

    highlighted = benchmarks.get("readerDrawsSmoothlyOnAPageWithFiveHundredHighlights")
    control = benchmarks.get("readerDrawsSmoothlyOnAPlainPageAsAControl")
    if highlighted and control:
        highlighted_p99 = metric_value(highlighted, FRAME_DURATION_METRIC, "P99")
        control_p99 = metric_value(control, FRAME_DURATION_METRIC, "P99")
        if highlighted_p99 and control_p99:
            report.check(
                "PERF-42 highlighted page against the plain control",
                highlighted_p99,
                control_p99 * budgets.ratios["HIGHLIGHT_CONTROL_RATIO"],
            )

    cold_grid = benchmarks.get("homeLibraryGridScrollsSmoothlyWithAColdCoverCache")
    warm_grid = benchmarks.get("homeLibraryGridScrollsSmoothlyWithAWarmCoverCache")
    if cold_grid and warm_grid:
        for percentile in ("P50", "P90", "P99"):
            cold_value = metric_value(cold_grid, FRAME_DURATION_METRIC, percentile)
            warm_value = metric_value(warm_grid, FRAME_DURATION_METRIC, percentile)
            if cold_value is not None and warm_value is not None:
                report.check(
                    "PERF-37 warm cover cache against cold at %s" % percentile,
                    warm_value,
                    cold_value,
                )

    first = benchmarks.get("sustainedSessionDegradationRatioFirstSegment")
    last = benchmarks.get("sustainedSessionDegradationRatioLastSegment")
    if bool(first) != bool(last):
        report.note_problem(
            "PERF-50 reported only one of its two segments. The degradation ratio is the whole "
            "point of that test and neither half means anything alone."
        )
    elif first and last:
        first_p99 = metric_value(first, FRAME_DURATION_METRIC, "P99")
        last_p99 = metric_value(last, FRAME_DURATION_METRIC, "P99")
        if first_p99 and last_p99:
            report.check(
                "PERF-50 last segment against the first segment",
                last_p99,
                first_p99 * budgets.ratios["SUSTAINED_DEGRADATION_RATIO"],
            )


def check_for_unmapped(report, benchmarks):
    known = set(STARTUP_BENCHMARKS) | set(FRAME_BENCHMARKS)
    for name in sorted(benchmarks):
        if name not in known:
            report.note_problem(
                "%s produced a result but has no budget mapping in this script, so it ran "
                "unchecked. Add it to STARTUP_BENCHMARKS or FRAME_BENCHMARKS." % name
            )


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Compares a Macrobenchmark benchmarkData.json against the budgets in PerfBudgets.kt "
            "and fails the run on a breach. MacrobenchmarkRule has no assertion API, so without "
            "this step the benchmarks only prove that the flows run without crashing."
        ),
        epilog=(
            "The budgets in PerfBudgets.kt are calibration seeds rather than measured values. "
            "Run with --report-only for the first calibration passes, which records every number "
            "without failing, then freeze the measured budgets and drop the flag."
        ),
    )
    parser.add_argument(
        "report",
        help="a benchmarkData.json file, or a directory that is searched for one",
    )
    parser.add_argument(
        "--budgets",
        default=BUDGETS_SOURCE,
        help="path to PerfBudgets.kt, the single source of every budget number",
    )
    parser.add_argument(
        "--report-only",
        action="store_true",
        help="print the comparison and exit zero even on a breach",
    )
    arguments = parser.parse_args()

    budgets = load_budgets(arguments.budgets)
    paths = find_reports(arguments.report)
    if not paths:
        raise SystemExit(
            "No benchmarkData.json under %s. Macrobenchmark writes it into the connected test "
            "additional output directory, and a run that produced none did not measure anything."
            % arguments.report
        )

    benchmarks = load_benchmarks(paths)
    if not benchmarks:
        raise SystemExit("The report at %s carries no benchmarks." % ", ".join(paths))

    report = Report()
    check_absolute(report, benchmarks, budgets)
    check_relations(report, benchmarks, budgets)
    check_for_unmapped(report, benchmarks)

    width = max(len(label) for label, _o, _l, _u, _ok in report.checks) if report.checks else 0
    for label, observed, limit, unit, ok in report.checks:
        print("%s %-*s %8.2f%s against %8.2f%s" % (
            "PASS" if ok else "FAIL", width, label, observed, unit, limit, unit,
        ))

    for test_id, reason in sorted(UNIMPLEMENTED_VARIATIONS.items()):
        print("NOTE %s carries a variation that is not implemented: %s" % (test_id, reason))

    print()
    print("%d comparisons, %d problems, from %s" % (
        len(report.checks), len(report.problems), ", ".join(paths),
    ))
    for problem in report.problems:
        print("  %s" % problem)

    if report.problems and not arguments.report_only:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
