from __future__ import annotations

import sys
import unittest
import tempfile
from types import SimpleNamespace
from unittest.mock import patch
from dataclasses import replace
from pathlib import Path

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.catalog import (AREAS, TestSpec, TestUnit, area_counts, build_units,
                                  load_tests as load_catalog_tests)
from mjpdf_runner.devices import DeviceInfo
from mjpdf_runner.model import RendererLane
from mjpdf_runner.planner import (DURATION_CALIBRATION, ExecutionMode, automatic_plan,
                                  compatible, duration_factor, every_plan,
                                  reassign, unit_seconds, validate_plan)


class PlannerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.repo = RUNNER_DIR.parents[1]
        cls.tests = load_catalog_tests(cls.repo, RUNNER_DIR)

    @staticmethod
    def device(serial: str, lane: RendererLane, api: int = 35) -> DeviceInfo:
        return DeviceInfo(serial, serial, "15", api, "x86_64", "1080x2400", "440",
                           4096, lane, lane.value, True, "free")

    @staticmethod
    def unit(name: str, count: int = 1, software: bool = False,
             profile: str | None = None, min_api: int | None = None) -> TestUnit:
        tests = tuple(TestSpec(
            selector=f"example.{name}#{index}", class_name=f"example.{name}",
            method=str(index), package="example", area="entry", ignored=False,
            hostile=False, software_only=software, atomic_key=name,
            required_profile=profile, min_api=min_api,
        ) for index in range(count))
        return TestUnit(name, "entry", tests[0].class_name if count > 1 else tests[0].selector,
                         tests, software, False, None, profile, min_api, None)

    def test_large_fixture_requirements_match_active_consumers(self):
        actual = {t.method: t.required_fixtures for t in self.tests if t.required_fixtures}
        self.assertEqual({
            "aVeryLargeSharedFileAsksBeforeCopyingEvenInAlwaysCopyMode": frozenset({"HUGE_PDF"}),
            "aFourHundredMegabytePdfOpensWithoutLoadingItselfIntoTheHeap": frozenset({"LARGE_PDF"}),
            "searchingAFourHundredMegabytePdfStaysBounded": frozenset({"LARGE_PDF"}),
            "aFileAboveTheSizeCeilingIsRefused": frozenset({"OVERSIZED_BACKUP"}),
        }, actual)

    def test_fixture_annotations_union_inheritance_and_methods_and_reject_errors(self):
        from mjpdf_runner import discovery as ledger
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "Tests.kt"
            source.write_text("// Written by Mudlej. License is GPLv3.\n"
                             "@RequiresFixture(FixtureResource.HUGE_PDF)\nopen class Base {}\n"
                             "@RequiresFixture(FixtureResource.LARGE_PDF)\nclass Child : Base() {}\n")
            with patch.object(ledger, "SUITE", temporary):
                classes = ledger.collect_class_requirements()
            method = {}
            ledger.apply_requirement_annotation("RequiresFixture", "@RequiresFixture(FixtureResource.OVERSIZED_BACKUP)", method)
            test = ledger.build_test("memory", "Child", "run", method, classes["Child"], "Tests.kt", 1)
            self.assertEqual({"HUGE_PDF", "LARGE_PDF", "OVERSIZED_BACKUP"}, set(test["required_fixtures"]))
        for annotation in ["@RequiresFixture()", "@RequiresFixture(FixtureResource.UNKNOWN)",
                           "@RequiresFixture(FixtureResource.HUGE_PDF, FixtureResource.HUGE_PDF)"]:
            with self.subTest(annotation=annotation), self.assertRaises(ValueError):
                ledger.apply_requirement_annotation("RequiresFixture", annotation, {})
        with self.assertRaises(ValueError):
            ledger.apply_requirement_annotation("RequiresFixture", "@RequiresFixture(FixtureResource.HUGE_PDF)", method)

    def test_fixture_selection_is_conservative_for_opaque_filters(self):
        from mjpdf_runner.catalog import resolve_fixture_requirements
        config = SimpleNamespace(repo_root=self.repo, runner_dir=RUNNER_DIR, app_apk=None, test_apk=None,
                                 passthrough=(), packages=(), annotations=(), test_file=None,
                                 phase_scenario=None, skip_build=False, classes=("com.gitlab.mudlej.MjPdfReader.suite.memory.LargeDocumentTest",))
        self.assertEqual(frozenset({"LARGE_PDF"}), resolve_fixture_requirements(config)[0])
        for change in [dict(passthrough=("-e", "class", "unknown.Test")), dict(packages=("memory",)),
                       dict(annotations=("unknown",)), dict(test_file=Path("tests.txt")),
                       dict(classes=("unknown.Test",)), dict(app_apk=Path("custom.apk")), dict(skip_build=True), dict(classes=())]:
            clone = SimpleNamespace(**(vars(config) | change))
            self.assertIsNone(resolve_fixture_requirements(clone)[0])
        config.classes = ()
        config.phase_scenario = "HOME-73-ACCESS"
        self.assertEqual(frozenset(), resolve_fixture_requirements(config)[0])

    def test_fixture_requirements_union_across_atomic_members(self):
        unit = self.unit("parameterized", count=2)
        tests = (replace(unit.tests[0], required_fixtures=frozenset({"HUGE_PDF"})),
                 replace(unit.tests[1], required_fixtures=frozenset({"LARGE_PDF"})))
        self.assertEqual(frozenset({"HUGE_PDF", "LARGE_PDF"}), build_units(tests)[0].required_fixtures)
        self.assertEqual(frozenset({"HUGE_PDF"}), build_units((tests[0], replace(tests[1], ignored=True)))[0].required_fixtures)

    def test_catalog_assigns_every_discovered_test_to_one_area(self) -> None:
        counts = area_counts(self.tests)

        self.assertGreater(len(self.tests), 1400)
        self.assertTrue(all(test.duration_explicit for test in self.tests if not test.ignored))
        self.assertEqual(len(self.tests), sum(counts.values()))
        self.assertEqual({area.key for area in AREAS}, set(counts))
        self.assertTrue(all(count > 0 for count in counts.values()))
        self.assertEqual(12, sum(unit.scenario is not None for unit in build_units(self.tests)))
        self.assertNotIn(
            "com.gitlab.mudlej.MjPdfReader.suite.selection.ExternalArabicObservationTest",
            {test.class_name for test in self.tests},
        )

    def test_automatic_plan_respects_software_requirement(self) -> None:
        hardware = self.device("emulator-1", RendererLane.HARDWARE)
        software = self.device("emulator-2", RendererLane.SOFTWARE)
        units = (self.unit("large", 3), self.unit("small-a"), self.unit("small-b"),
                 self.unit("pixels", 2, software=True))

        plan = automatic_plan(units, (hardware, software))

        self.assertFalse(any(unit.software_only for unit in plan.assignments[hardware.serial]))
        self.assertIn("pixels", {unit.key for unit in plan.assignments[software.serial]})
        self.assertEqual(7, plan.execution_count)
        self.assertEqual((), validate_plan(plan, units, (hardware, software)))

    def test_every_plan_skips_software_only_units_on_hardware(self) -> None:
        hardware = self.device("emulator-1", RendererLane.HARDWARE)
        software = self.device("emulator-2", RendererLane.SOFTWARE)
        devices = (hardware, software)
        units = (self.unit("ordinary", 2), self.unit("pixels", software=True))

        plan = every_plan(units, devices)

        self.assertEqual({"ordinary"}, {unit.key for unit in plan.assignments[hardware.serial]})
        self.assertEqual(
            {"ordinary", "pixels"},
            {unit.key for unit in plan.assignments[software.serial]},
        )
        self.assertEqual({hardware.serial: 2, software.serial: 3}, plan.counts())
        self.assertEqual(5, plan.execution_count)
        self.assertEqual((), validate_plan(plan, units, devices))

    def test_pinned_rendering_tests_only_support_the_exact_software_profile(self) -> None:
        required = "api36-1080x2400-440dpi-3button-en-US-swiftshader"
        unit = self.unit("pixels", software=True, profile=required)

        self.assertTrue(compatible(unit, self.device("api36", RendererLane.SOFTWARE, 36)))
        self.assertFalse(compatible(unit, self.device("api28", RendererLane.SOFTWARE, 28)))
        self.assertFalse(compatible(unit, self.device("api36-hw", RendererLane.HARDWARE, 36)))

    def test_automatic_plan_skips_units_unsupported_by_every_selected_device(self) -> None:
        api28 = self.device("api28", RendererLane.SOFTWARE, 28)
        units = (
            self.unit("ordinary", 2),
            self.unit(
                "pixels", 3, software=True,
                profile="api36-1080x2400-440dpi-3button-en-US-swiftshader",
            ),
            self.unit("modern", min_api=33),
        )

        plan = automatic_plan(units, (api28,))

        self.assertEqual({"ordinary"}, {unit.key for unit in plan.assignments[api28.serial]})
        self.assertEqual(2, plan.execution_count)
        self.assertEqual(4, plan.skipped_count)
        self.assertEqual((), validate_plan(plan, units, (api28,)))

    def test_catalog_marks_pinned_suites_and_api_limited_tests(self) -> None:
        pinned = "api36-1080x2400-440dpi-3button-en-US-swiftshader"
        performance = [test for test in self.tests if test.package == "performance"]
        visual_baselines = [
            test for test in self.tests
            if test.package == "baseline" and not test.class_name.endswith(".PixelComparisonTest")
        ]
        pixel_comparisons = [
            test for test in self.tests if test.class_name.endswith(".PixelComparisonTest")
        ]
        by_method = {test.method: test for test in self.tests}

        self.assertTrue(performance)
        self.assertTrue(visual_baselines)
        self.assertTrue(all(test.required_profile == pinned for test in performance + visual_baselines))
        self.assertTrue(all(test.required_profile is None for test in pixel_comparisons))
        self.assertEqual(33, by_method["theLocaleRobotSetsAndClearsThePerAppLocale"].min_api)
        self.assertEqual(30, by_method["theConsentDialogCanSendTheUserToTheFileAccessSettings"].min_api)
        self.assertEqual(30, by_method[
            "grantingAccessLaterCachesMediaStoreForFoldersWithoutWalkingOrReadingMetadata"
        ].min_api)

    def test_direct_phase_scenarios_are_assigned_only_to_api_31_or_newer(self) -> None:
        scenarios = [test for test in self.tests if test.scenario is not None]
        self.assertTrue(scenarios)
        self.assertTrue(all(test.min_api is not None and test.min_api >= 31 for test in scenarios))
        older = self.device("api28", RendererLane.HARDWARE, api=28)
        newer = self.device("api31", RendererLane.HARDWARE, api=31)
        units = build_units(tuple(scenarios))
        plan = automatic_plan(units, (older, newer))
        self.assertEqual((), plan.assignments[older.serial])
        self.assertEqual(len(scenarios), sum(unit.count for unit in plan.assignments[newer.serial]))

    def test_manual_reassignment_moves_one_atomic_unit(self) -> None:
        devices = (self.device("emulator-1", RendererLane.HARDWARE),
                   self.device("emulator-2", RendererLane.SOFTWARE))
        units = (self.unit("one"), self.unit("two", 2))
        plan = automatic_plan(units, devices, ExecutionMode.MANUAL)

        moved = reassign(plan, "one", "emulator-1")

        self.assertEqual({"one", "two"},
                         {unit.key for unit in moved.assignments["emulator-1"]})
        self.assertEqual((), validate_plan(moved, units, devices))

    @staticmethod
    def marked(unit: TestUnit, category: str, **changes) -> TestUnit:
        return replace(unit, tests=tuple(replace(test, duration_category=category,
                                                 duration_explicit=True, **changes)
                                         for test in unit.tests))

    def test_duration_balancing_assigns_fewer_tests_to_a_long_workload(self) -> None:
        devices = (self.device("a", RendererLane.HARDWARE),
                   self.device("b", RendererLane.HARDWARE))
        long = self.marked(self.unit("long"), "VERY_LONG")
        shorts = tuple(self.marked(self.unit(f"short-{i}"), "SHORT") for i in range(20))
        units = (long, *shorts)

        plan = automatic_plan(units, devices)

        self.assertIn(long, plan.assignments["a"])
        self.assertLessEqual(plan.counts()["a"], 2)
        self.assertGreaterEqual(plan.counts()["b"], 19)
        estimates = plan.estimated_seconds(devices)
        self.assertLess(abs(estimates["a"] - estimates["b"]), unit_seconds(shorts[0]) * 2)
        self.assertEqual((), validate_plan(plan, units, devices))

    def test_pinned_work_is_reserved_before_flexible_work(self) -> None:
        devices = (self.device("a", RendererLane.SOFTWARE, 36),
                   self.device("b", RendererLane.HARDWARE, 37))
        pinned = self.marked(self.unit(
            "z-pinned", software=True,
            profile="api36-1080x2400-440dpi-3button-en-US-swiftshader"), "VERY_LONG")
        flexible = self.marked(self.unit("a-flexible"), "VERY_LONG")

        plan = automatic_plan((flexible, pinned), devices)

        self.assertEqual((pinned,), plan.assignments["a"])
        self.assertEqual((flexible,), plan.assignments["b"])

    def test_profile_speed_uses_configuration_and_assigns_more_work_to_faster_device(self) -> None:
        fast = replace(self.device("z-fast", RendererLane.HARDWARE, 37),
                       model="sdk_gphone16k_x86_64")
        slow = replace(self.device("a-slow", RendererLane.SOFTWARE, 28),
                       model="Android SDK built for x86_64")
        units = tuple(self.marked(self.unit(f"test-{i}"), "NORMAL") for i in range(100))

        plan = automatic_plan(units, (slow, fast))

        self.assertGreater(plan.counts()[fast.serial], plan.counts()[slow.serial])
        estimates = plan.estimated_seconds((slow, fast))
        self.assertLess(abs(estimates[fast.serial] - estimates[slow.serial]),
                        unit_seconds(units[0]) * duration_factor(slow))
        self.assertEqual(duration_factor(slow), duration_factor(replace(slow, serial="changed")))
        self.assertEqual(1.0, duration_factor(replace(slow, model="unmeasured")))
        self.assertEqual(1.0, duration_factor(replace(slow, profile_density="480")))

    def test_atomic_groups_sum_method_costs_without_splitting(self) -> None:
        devices = (self.device("a", RendererLane.HARDWARE),
                   self.device("b", RendererLane.HARDWARE))
        group = self.marked(self.unit("parameterized", 2), "LONG")
        scenario = replace(self.marked(self.unit("phases", 3), "SHORT"), scenario="ABOUT-16")

        plan = automatic_plan((group, scenario), devices)

        self.assertEqual(unit_seconds(group), 2 * DURATION_CALIBRATION["categorySeconds"]["LONG"])
        self.assertEqual(unit_seconds(scenario),
                         3 * DURATION_CALIBRATION["categorySeconds"]["SHORT"]
                         + DURATION_CALIBRATION["scenarioOverheadSeconds"]["ABOUT-16"])
        self.assertEqual({group.key, scenario.key},
                         {unit.key for units in plan.assignments.values() for unit in units})
        self.assertEqual((), validate_plan(plan, (group, scenario), devices))

    def test_ignored_tests_have_zero_cost_and_unmarked_tests_are_reported(self) -> None:
        device = self.device("a", RendererLane.HARDWARE)
        ignored = self.marked(self.unit("ignored"), "VERY_LONG", ignored=True)
        unmarked = self.unit("unmarked")
        marked = self.marked(self.unit("marked"), "SHORT")
        plan = automatic_plan((ignored, unmarked, marked), (device,))

        self.assertEqual(0, unit_seconds(ignored))
        self.assertEqual(3, plan.execution_count)
        self.assertEqual({"a": 1}, plan.duration_fallback_counts())
        self.assertEqual(unit_seconds(unmarked) + unit_seconds(marked),
                         plan.estimated_seconds((device,))["a"])

    def test_weighted_assignments_are_deterministic_and_manual_moves_recompute_load(self) -> None:
        devices = (self.device("a", RendererLane.HARDWARE),
                   self.device("b", RendererLane.HARDWARE))
        units = tuple(self.marked(self.unit(str(i)), "NORMAL") for i in range(6))
        plan = automatic_plan(units, devices)

        self.assertEqual(plan, automatic_plan(tuple(reversed(units)), tuple(reversed(devices))))
        unit = plan.assignments["b"][0]
        moved = reassign(plan, unit.key, "a")
        before = plan.estimated_seconds(devices)
        after = moved.estimated_seconds(devices)
        self.assertAlmostEqual(before["a"] + unit_seconds(unit), after["a"])
        self.assertAlmostEqual(before["b"] - unit_seconds(unit), after["b"])
        self.assertEqual(ExecutionMode.MANUAL, moved.mode)

    def test_real_catalog_has_parameterized_total_and_history_method_override(self) -> None:
        by_method = {test.method: test for test in self.tests}
        self.assertEqual("LONG", by_method["theSwatchTheUserPicksIsTheColourThatGetsStored"].duration_category)
        self.assertEqual("NORMAL", by_method["aJumpToThePageYouAreAlreadyOnRecordsNothing"].duration_category)
        self.assertEqual("LONG", by_method["theBackStackIsCappedAtFiftyEntries"].duration_category)


if __name__ == "__main__":
    unittest.main()
