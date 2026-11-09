from __future__ import annotations

import os
import re
from pathlib import Path

REPO = str(Path(__file__).resolve().parents[3])
SUITE = os.path.join(REPO, "app/src/androidTest/java/com/gitlab/mudlej/MjPdfReader/suite")
SUITE_PACKAGE = "com.gitlab.mudlej.MjPdfReader.suite"
FIXTURE_RESOURCES = {"HUGE_PDF", "LARGE_PDF", "OVERSIZED_BACKUP"}

CLASS_RE = re.compile(
    r"^(?:(?:open|abstract|internal|private|sealed|data)\s+)*class\s+(\w+)"
    r"(?:[^:{]*:\s*([\w.]+))?"
)
FUN_RE = re.compile(r"^\s*(?:private\s+|internal\s+)?fun\s+(\w+)\s*\(")
ANNOTATION_RE = re.compile(r"^\s*@(\w+)")
FIRST_STRING_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')


def _read(path: str) -> str:
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        return handle.read()


def logical_lines(text: str):
    raw = text.split("\n")
    index = 0
    while index < len(raw):
        line = raw[index]
        start = index
        if line.lstrip().startswith("@") and line.count("(") > line.count(")"):
            depth = line.count("(") - line.count(")")
            while depth > 0 and index + 1 < len(raw):
                index += 1
                line += " " + raw[index].strip()
                depth += raw[index].count("(") - raw[index].count(")")
        yield start + 1, line
        index += 1


def annotation_argument(line: str) -> str:
    match = FIRST_STRING_RE.search(line)
    return match.group(1) if match else ""


def apply_duration_annotation(line: str, tags: dict) -> None:
    match = re.fullmatch(
        r"\s*@TestDuration\s*\(\s*(?:value\s*=\s*)?"
        r"DurationCategory\.(SHORT|NORMAL|LONG|VERY_LONG)\s*,?\s*\)\s*",
        line,
    )
    if match is None:
        raise ValueError("invalid duration annotation: " + line.strip())
    if "duration_category" in tags:
        raise ValueError("duplicate duration annotation: " + line.strip())
    tags["duration_category"] = match.group(1)


def apply_requirement_annotation(kind: str, line: str, tags: dict) -> bool:
    if kind == "RequiresFixture":
        match = re.fullmatch(r"\s*@RequiresFixture\s*\((.*?)\)\s*", line)
        values = [] if match is None else [
            part.strip() for part in match.group(1).rstrip().rstrip(",").split(",")
        ]
        allowed = {"FixtureResource." + name for name in FIXTURE_RESOURCES}
        if (not values or any(value not in allowed for value in values)
                or len(set(values)) != len(values) or "required_fixtures" in tags):
            raise ValueError("invalid or duplicate fixture annotation: " + line.strip())
        tags["required_fixtures"] = tuple(sorted(value.split(".")[-1] for value in values))
        return True
    if kind == "SdkSuppress":
        minimum = re.search(r"minSdkVersion\s*=\s*(\d+)", line)
        maximum = re.search(r"maxSdkVersion\s*=\s*(\d+)", line)
        if minimum:
            tags["min_api"] = int(minimum.group(1))
        if maximum:
            tags["max_api"] = int(maximum.group(1))
        return True
    if kind == "RequiresSoftwareRenderer":
        tags["software_only"] = True
        return True
    if kind == "RequiresDeviceProfile":
        tags["required_profile"] = annotation_argument(line)
        return True
    return False


def collect_class_requirements() -> dict[str, dict]:
    direct: dict[str, dict] = {}
    parents: dict[str, str | None] = {}
    for directory, _, names in os.walk(SUITE):
        for name in sorted(names):
            if not name.endswith(".kt"):
                continue
            pending: dict = {}
            for _, line in logical_lines(_read(os.path.join(directory, name))):
                annotation = ANNOTATION_RE.match(line)
                if annotation:
                    apply_requirement_annotation(annotation.group(1), line, pending)
                    continue
                declaration = CLASS_RE.match(line)
                if declaration:
                    class_name = declaration.group(1)
                    direct[class_name] = dict(pending)
                    parents[class_name] = declaration.group(2)
                    pending = {}
                    continue
                if FUN_RE.match(line):
                    pending = {}

    resolved: dict[str, dict] = {}

    def resolve(class_name: str, visiting: tuple[str, ...] = ()) -> dict:
        if class_name in resolved:
            return resolved[class_name]
        if class_name in visiting:
            raise RuntimeError("cyclic test class inheritance: " + " -> ".join((*visiting, class_name)))
        inherited: dict = {}
        parent = parents.get(class_name)
        if parent in direct:
            inherited.update(resolve(parent, (*visiting, class_name)))
        fixtures = (set(inherited.get("required_fixtures", ()))
                    | set(direct.get(class_name, {}).get("required_fixtures", ())))
        inherited.update(direct.get(class_name, {}))
        inherited["required_fixtures"] = tuple(sorted(fixtures))
        resolved[class_name] = inherited
        return inherited

    for class_name in direct:
        resolve(class_name)
    return resolved


def collect_tests() -> list[dict]:
    tests = []
    class_requirements = collect_class_requirements()
    for directory, _, names in os.walk(SUITE):
        for name in sorted(names):
            if not name.endswith(".kt"):
                continue
            path = os.path.join(directory, name)
            text = _read(path)
            if "@Test" not in text:
                continue
            relative = os.path.relpath(path, REPO)
            package = os.path.relpath(directory, SUITE).replace(os.sep, ".")
            package = "" if package == "." else package
            class_name = None
            class_tags: dict = {}
            pending: dict = {}
            for number, line in logical_lines(text):
                annotation = ANNOTATION_RE.match(line)
                if annotation:
                    kind = annotation.group(1)
                    if kind in ("TestId", "HarnessHostile"):
                        pending[kind] = annotation_argument(line)
                    elif kind == "Test":
                        pending["Test"] = True
                    elif kind == "Ignore":
                        pending["Ignore"] = True
                    elif kind == "TestDuration":
                        try:
                            apply_duration_annotation(line, pending)
                        except ValueError as exc:
                            raise ValueError(f"{relative}:{number}: {exc}") from exc
                    else:
                        apply_requirement_annotation(kind, line, pending)
                    continue
                declaration = CLASS_RE.match(line)
                if declaration:
                    class_name = declaration.group(1)
                    class_tags = dict(pending)
                    class_tags.update(class_requirements.get(class_name, {}))
                    pending = {}
                    continue
                function = FUN_RE.match(line)
                if function:
                    if pending.get("Test") and class_name:
                        tests.append(build_test(package, class_name, function.group(1),
                                                pending, class_tags, relative, number))
                    pending = {}
                    continue
                if line.strip() and not line.lstrip().startswith("//"):
                    if not line.lstrip().startswith("@"):
                        pending.pop("Test", None)
    return tests


def _sanitize(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", value)


def build_test(package: str, class_name: str, method: str, pending: dict, class_tags: dict,
               path: str, line: int) -> dict:
    declared = pending.get("TestId") or class_tags.get("TestId") or ""
    if declared.strip():
        test_id = _sanitize(declared)
        id_source = "method" if pending.get("TestId") else "class"
    else:
        test_id = _sanitize(f"{class_name}.{method}")
        id_source = "derived"
    hostile = pending.get("HarnessHostile") or class_tags.get("HarnessHostile") or ""
    full_class = SUITE_PACKAGE
    if package:
        full_class += "." + package
    full_class += "." + class_name
    return {
        "test_id": test_id,
        "id_source": id_source,
        "package": package or "(root)",
        "class": full_class,
        "simple_class": class_name,
        "method": method,
        "hostile_bug": hostile,
        "ignored": bool(pending.get("Ignore")),
        "software_only": bool(pending.get("software_only", class_tags.get("software_only", False))),
        "required_profile": pending.get("required_profile", class_tags.get("required_profile")),
        "min_api": pending.get("min_api", class_tags.get("min_api")),
        "max_api": pending.get("max_api", class_tags.get("max_api")),
        "required_fixtures": tuple(sorted(set(class_tags.get("required_fixtures", ()))
                                          | set(pending.get("required_fixtures", ())))),
        "duration_category": pending.get("duration_category", class_tags.get("duration_category")),
        "path": path,
        "line": line,
    }
