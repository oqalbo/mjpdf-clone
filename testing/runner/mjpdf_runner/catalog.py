from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from . import discovery
from .phases import parse_scenario
from .instrumentation import DIRECT_PHASE_MIN_API
from .model import RunnerConfig

SUITE_PREFIX = "com.gitlab.mudlej.MjPdfReader.suite."
NON_CATALOG_PACKAGES = {"fixtures"}
NON_CATALOG_CLASSES = {
    f"{SUITE_PREFIX}selection.ExternalArabicObservationTest",
}


@dataclass(frozen=True)
class Area:
    key: str
    name: str
    packages: tuple[str, ...]


AREAS = (
    Area("entry", "App entry and onboarding", ("(root)", "about")),
    Area("intake", "Document intake", ("intake",)),
    Area("home", "Home and library", ("home", "scan")),
    Area("navigation", "Reader navigation", ("navigation", "bookmarks")),
    Area("display", "Reader display and gestures", ("actions", "view", "zoom")),
    Area("annotations", "Annotations and forms", ("annotations", "lists")),
    Area("artifacts", "Saving and produced artifacts", ("annotationsave", "artifacts")),
    Area("text", "Text mode and selection", ("text", "selection")),
    Area("search", "Search", ("search",)),
    Area("history", "History, settings and backup", ("history", "settings", "backup")),
    Area("security", "Privacy and security", ("privacy", "security")),
    Area("localization", "Localization and layout", ("localization",)),
    Area("reliability", "Reliability and resource use", ("resilience", "memory", "state")),
    Area("rendering", "Rendering and performance", ("baseline", "performance")),
)

_PACKAGE_TO_AREA = {package: area.key for area in AREAS for package in area.packages}


@dataclass(frozen=True)
class TestSpec:
    selector: str
    class_name: str
    method: str
    package: str
    area: str
    ignored: bool
    hostile: bool
    software_only: bool
    atomic_key: str
    scenario: str | None = None
    required_profile: str | None = None
    min_api: int | None = None
    max_api: int | None = None
    duration_category: str = "NORMAL"
    duration_explicit: bool = False
    required_fixtures: frozenset[str] = frozenset()

    @property
    def display(self) -> str:
        return f"{self.class_name.rsplit('.', 1)[-1]}#{self.method}"


@dataclass(frozen=True)
class TestUnit:
    key: str
    area: str
    selector: str | None
    tests: tuple[TestSpec, ...]
    software_only: bool
    hostile: bool
    scenario: str | None
    required_profile: str | None = None
    min_api: int | None = None
    max_api: int | None = None

    @property
    def required_fixtures(self) -> frozenset[str]:
        return frozenset(resource for test in self.tests if not test.ignored for resource in test.required_fixtures)

    @property
    def count(self) -> int:
        return len(self.tests)

    @property
    def display(self) -> str:
        if self.scenario:
            return self.scenario
        if len(self.tests) > 1:
            return self.tests[0].class_name.rsplit('.', 1)[-1]
        return self.tests[0].display


def _scenario_map(runner_dir: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for path in sorted((runner_dir / "phases").glob("*.phases")):
        scenario = parse_scenario(path, path.stem)
        for action in scenario.actions:
            if action.kind != "phase":
                continue
            if action.value in result:
                raise RuntimeError(f"phase selector {action.value} belongs to two scenarios")
            result[action.value] = scenario.name
    return result


def load_tests(repo_root: Path, runner_dir: Path) -> tuple[TestSpec, ...]:
    phase_scenarios = _scenario_map(runner_dir)
    raw_tests = discovery.collect_tests()
    specs: list[TestSpec] = []
    missing_packages: set[str] = set()
    source_cache: dict[str, str] = {}
    for raw in raw_tests:
        if raw["class"] in NON_CATALOG_CLASSES:
            continue
        package = raw["package"]
        area = _PACKAGE_TO_AREA.get(package)
        if area is None:
            if package not in NON_CATALOG_PACKAGES:
                missing_packages.add(package)
            continue
        selector = f"{raw['class']}#{raw['method']}"
        source = source_cache.get(raw["path"])
        if source is None:
            source = (repo_root / raw["path"]).read_text(encoding="utf-8", errors="replace")
            source_cache[raw["path"]] = source
        parameterized = "Parameterized::class" in source or "Parameterized.class" in source
        scenario = phase_scenarios.get(selector)
        min_api = raw.get("min_api")
        if scenario is not None:
            min_api = max(min_api or 0, DIRECT_PHASE_MIN_API)
        atomic_key = f"scenario:{scenario}" if scenario else (
            f"class:{raw['class']}" if parameterized else f"test:{selector}"
        )
        specs.append(TestSpec(
            selector=selector,
            class_name=raw["class"],
            method=raw["method"],
            package=package,
            area=area,
            ignored=bool(raw["ignored"]),
            hostile=bool(raw["hostile_bug"]),
            software_only=bool(raw.get("software_only")),
            atomic_key=atomic_key,
            scenario=scenario,
            required_profile=raw.get("required_profile"),
            min_api=min_api,
            max_api=raw.get("max_api"),
            duration_category=raw.get("duration_category") or "NORMAL",
            duration_explicit=raw.get("duration_category") is not None,
            required_fixtures=frozenset(raw.get("required_fixtures", ())),
        ))
    if missing_packages:
        raise RuntimeError("test packages missing from the area catalog: " + ", ".join(sorted(missing_packages)))
    selectors = [test.selector for test in specs]
    if len(selectors) != len(set(selectors)):
        raise RuntimeError("test discovery returned duplicate selectors")
    return tuple(sorted(specs, key=lambda test: (test.area, test.class_name, test.method)))


def build_units(tests: tuple[TestSpec, ...]) -> tuple[TestUnit, ...]:
    grouped: dict[str, list[TestSpec]] = {}
    for test in tests:
        grouped.setdefault(test.atomic_key, []).append(test)
    units: list[TestUnit] = []
    for key, members in grouped.items():
        areas = {member.area for member in members}
        scenarios = {member.scenario for member in members}
        if len(areas) != 1 or len(scenarios) != 1:
            raise RuntimeError(f"atomic unit {key} crosses catalog boundaries")
        scenario = next(iter(scenarios))
        profiles = {member.required_profile for member in members if member.required_profile is not None}
        if len(profiles) > 1:
            raise RuntimeError(f"atomic unit {key} crosses device profiles")
        selector = None if scenario else (
            members[0].class_name if key.startswith("class:") else members[0].selector
        )
        units.append(TestUnit(
            key=key,
            area=next(iter(areas)),
            selector=selector,
            tests=tuple(sorted(members, key=lambda test: test.selector)),
            software_only=any(member.software_only for member in members),
            hostile=any(member.hostile for member in members),
            scenario=scenario,
            required_profile=next(iter(profiles), None),
            min_api=max((member.min_api for member in members if member.min_api is not None),
                        default=None),
            max_api=min((member.max_api for member in members if member.max_api is not None),
                        default=None),
        ))
    return tuple(sorted(units, key=lambda unit: (unit.area, unit.display)))


def area_counts(tests: tuple[TestSpec, ...]) -> dict[str, int]:
    return {area.key: sum(test.area == area.key for test in tests) for area in AREAS}


def resolve_fixture_requirements(config: RunnerConfig) -> tuple[frozenset[str] | None, str]:
    if config.skip_build:
        return None, "conservative fallback: skipped build may not match source fixture metadata"
    if config.app_apk is not None or config.test_apk is not None:
        return None, "conservative fallback: custom APK selection"
    if config.passthrough or config.packages or config.annotations or config.test_file:
        return None, "conservative fallback: filters or passthrough require runtime discovery"
    tests = load_tests(config.repo_root, config.runner_dir)
    if config.phase_scenario:
        selected = [test for test in tests if test.scenario == config.phase_scenario]
        if not selected:
            return None, "conservative fallback: scenario outside the catalog"
    elif config.classes:
        selected = []
        for selector in (part for value in config.classes for part in value.split(",")):
            matches = [test for test in tests if selector in (test.selector, test.class_name)]
            if not matches:
                return None, "conservative fallback: selector outside the catalog"
            selected.extend(matches)
    else:
        return None, "conservative fallback: unrestricted instrumentation selection"
    return frozenset(resource for test in selected if not test.ignored
                     for resource in test.required_fixtures), "catalog selection"
