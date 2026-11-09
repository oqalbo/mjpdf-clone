from __future__ import annotations

import re
from dataclasses import dataclass, replace
from pathlib import Path

from .catalog import AREAS, TestSpec, TestUnit, area_counts, build_units, load_tests
from .devices import DeviceDiscoveryError, DeviceInfo, LeaseManager, discover_devices
from .planner import (DURATION_CALIBRATION, AssignmentPlan, ExecutionMode, automatic_plan,
                       compatible, duration_profile_key, every_plan, reassign,
                       test_compatible, validate_plan)
from .terminal import InlinePanel, RawKeys


class WizardCancelled(RuntimeError):
    pass


@dataclass(frozen=True)
class WizardResult:
    devices: tuple[DeviceInfo, ...]
    areas: tuple[str, ...]
    tests: tuple[TestSpec, ...]
    units: tuple[TestUnit, ...]
    plan: AssignmentPlan


ANSI_RE = re.compile(r"\x1b\[[0-9;]*m")


def _visible_length(text: str) -> int:
    return len(ANSI_RE.sub("", text))


def _fit(text: str, width: int) -> str:
    if width <= 0:
        return ""
    if _visible_length(text) <= width:
        return text
    remaining = max(0, width - 1)
    result: list[str] = []
    position = 0
    for match in ANSI_RE.finditer(text):
        plain = text[position:match.start()]
        result.append(plain[:remaining])
        remaining -= min(len(plain), remaining)
        if remaining == 0:
            break
        result.append(match.group(0))
        position = match.end()
    else:
        result.append(text[position:position + remaining])
    return "".join(result) + "\x1b[0m…"


def _pad(text: str, width: int) -> str:
    return text + " " * max(0, width - _visible_length(text))


def _support_label(supported: int, total: int) -> str:
    ratio = supported / total if total else 0.0
    # Compatibility normally clusters near 100%, so spread the final 10%
    # across the full red-to-green range instead of making every useful
    # distinction look green.
    score = max(0.0, min(1.0, (ratio - 0.9) / 0.1))
    if score <= 0.5:
        red, green = 255, round(510 * score)
    else:
        red, green = round(510 * (1 - score)), 255
    return f"\x1b[38;2;{red};{green};0m{supported}/{total}\x1b[0m"


def _clock(seconds: float) -> str:
    value = max(0, int(seconds))
    return f"{value // 60:02d}:{value % 60:02d}"


def panel_frame(title: str, body: list[str], terminal_width: int,
                right: str = "", minimum: int = 68) -> list[str]:
    natural = max([_visible_length(title) + _visible_length(right) + 5,
                   *(_visible_length(line) + 4 for line in body)])
    width = min(max(30, terminal_width), max(minimum, natural))
    inner = width - 4
    left_title = f" {title} "
    right_title = f" {right} " if right else ""
    dashes = max(1, width - len(left_title) - len(right_title) - 2)
    top = "╭" + _fit(left_title, width - len(right_title) - 2) + "─" * dashes + right_title + "╮"
    top = _fit(top, width).ljust(width, "─")
    if top[-1] != "╮":
        top = top[:-1] + "╮"
    lines = [top]
    for text in body:
        content = _fit(text, inner)
        lines.append(f"│ {_pad(content, inner)} │")
    lines.append("╰" + "─" * (width - 2) + "╯")
    return lines

def device_rows(devices: tuple[DeviceInfo, ...], selected_serials: set[str],
                 cursor: int, start: int, limit: int,
                 tests: tuple[TestSpec, ...]) -> list[str]:
    support = {
        device.serial: sum(test_compatible(test, device) for test in tests)
        for device in devices
    }
    fields = [(
        device.model,
        device.serial,
        f"Android {device.android}",
        f"API {device.api}" if device.api is not None else "API -",
        device.abi,
        f"{device.size}@{device.density}",
        device.short_renderer,
        f"{device.memory_mb}MB" if device.memory_mb is not None else "-",
        f"{support[device.serial]}/{len(tests)}",
        "free" if device.available else device.lease,
    ) for device in devices]
    if not fields:
        return []
    widths = tuple(max(len(row[column]) for row in fields)
                   for column in range(len(fields[0]) - 1))
    rows: list[str] = []
    for index in range(start, min(start + limit, len(devices))):
        device = devices[index]
        values = fields[index]
        pointer = "›" if index == cursor else " "
        mark = "✓" if device.serial in selected_serials else (" " if device.available else "–")
        columns = [value.ljust(widths[column]) for column, value in enumerate(values[:-1])]
        support_column = len(columns) - 1
        columns[support_column] = _pad(
            _support_label(support[device.serial], len(tests)), widths[support_column]
        )
        rows.append(f"{pointer} [{mark}] " + " · ".join([*columns, values[-1]]))
    return rows




class SetupWizard:
    def __init__(self, repo_root: Path, runner_dir: Path, adb: str,
                 lease_script: Path | None, agent: str, panel: InlinePanel):
        self.repo_root = repo_root
        self.runner_dir = runner_dir
        self.adb = adb
        self.lease_script = lease_script
        self.agent = agent
        self.panel = panel
        self.leases = LeaseManager(lease_script, agent)
        self.devices: tuple[DeviceInfo, ...] = ()
        self.selected_serials: set[str] = set()
        self.tests = load_tests(repo_root, runner_dir)
        self.counts = area_counts(self.tests)
        self.selected_areas: set[str] = {area.key for area in AREAS}
        self.mode = ExecutionMode.AUTOMATIC
        self.error = ""

    def _draw(self, title: str, body: list[str], right: str = "") -> None:
        if self.error:
            body += ["", f"! {self.error}"]
        self.panel.draw(panel_frame(title, body, self.panel.size.columns, right))

    @staticmethod
    def _buttons(back: bool = True, next_label: str = "Next") -> str:
        left = "[ Back: B ]" if back else "[ Cancel: Esc ]"
        return f"{left}    [ {next_label}: N ]"

    def _refresh_devices(self) -> None:
        self.error = ""
        self._draw("MJ PDF · Setup 1/4 · Emulators", ["Discovering available emulators…"])
        self.devices = discover_devices(self.adb, self.lease_script, self.agent)
        available = {device.serial for device in self.devices if device.available}
        self.selected_serials.intersection_update(available)
        if not self.selected_serials and available:
            self.selected_serials.add(next(device.serial for device in self.devices if device.available))

    def _device_screen(self, keys: RawKeys) -> bool:
        self._refresh_devices()
        cursor = 0
        while True:
            visible_height = max(3, self.panel.size.lines - 9)
            start = max(0, min(cursor - visible_height // 2, max(0, len(self.devices) - visible_height)))
            rows = device_rows(
                self.devices, self.selected_serials, cursor, start, visible_height, self.tests
            )
            if not rows:
                rows = ["No online emulators were reported by ADB."]
            body = ["Select one or more free emulators.", "", *rows, "",
                    f"{len(self.selected_serials)} selected · "
                    f"{sum(device.available for device in self.devices)} free",
                    "↑↓ move · Space select · A select all · R refresh",
                    self._buttons(back=False)]
            self._draw("MJ PDF · Setup 1/4 · Emulators", body)
            key = keys.read()
            if key == "up" and self.devices:
                cursor = (cursor - 1) % len(self.devices)
            elif key == "down" and self.devices:
                cursor = (cursor + 1) % len(self.devices)
            elif key == "space" and self.devices and self.devices[cursor].available:
                serial = self.devices[cursor].serial
                if serial in self.selected_serials:
                    self.selected_serials.remove(serial)
                else:
                    self.selected_serials.add(serial)
                self.error = ""
            elif key.lower() == "a":
                available = {device.serial for device in self.devices if device.available}
                self.selected_serials = set() if self.selected_serials == available else available
            elif key.lower() == "r":
                self._refresh_devices()
                cursor = min(cursor, max(0, len(self.devices) - 1))
            elif key in {"cancel", "escape", "b", "B"}:
                raise WizardCancelled()
            elif key in {"enter", "right", "n", "N"}:
                if not self.selected_serials:
                    self.error = "Select at least one free emulator."
                    continue
                try:
                    self._draw("MJ PDF · Setup 1/4 · Emulators", ["Claiming selected emulators atomically…"])
                    self.leases.claim(sorted(self.selected_serials))
                except DeviceDiscoveryError as exc:
                    self.error = str(exc)
                    self._refresh_devices()
                    continue
                return True

    def _area_lines(self, cursor: int, expanded: set[str]) -> tuple[list[str], list[int]]:
        lines: list[str] = []
        positions: list[int] = []
        for index, area in enumerate(AREAS):
            positions.append(len(lines))
            pointer = "›" if index == cursor else " "
            mark = "✓" if area.key in self.selected_areas else " "
            disclosure = "▾" if area.key in expanded else "▸"
            lines.append(f"{pointer} [{mark}] {disclosure} {area.name} · {self.counts[area.key]} tests")
            if area.key in expanded:
                for package in area.packages:
                    count = sum(test.package == package for test in self.tests)
                    name = "Top-level suite" if package == "(root)" else package.replace("_", " ").title()
                    lines.append(f"        {name} · {count} tests")
        return lines, positions

    def _area_screen(self, keys: RawKeys) -> bool:
        cursor = 0
        expanded: set[str] = set()
        while True:
            rows, positions = self._area_lines(cursor, expanded)
            max_rows = max(5, self.panel.size.lines - 10)
            focus = positions[cursor]
            start = max(0, min(focus - max_rows // 2, max(0, len(rows) - max_rows)))
            visible = rows[start:start + max_rows]
            count = sum(self.counts[key] for key in self.selected_areas)
            body = ["Select the areas to run.", "", *visible, "",
                    f"{len(self.selected_areas)} areas · {count} unique tests",
                    "↑↓ move · Space select · A select all · Enter expand",
                    self._buttons()]
            self._draw("MJ PDF · Setup 2/4 · Test areas", body)
            key = keys.read()
            if key == "up":
                cursor = (cursor - 1) % len(AREAS)
            elif key == "down":
                cursor = (cursor + 1) % len(AREAS)
            elif key == "space":
                area = AREAS[cursor].key
                if area in self.selected_areas:
                    self.selected_areas.remove(area)
                else:
                    self.selected_areas.add(area)
                self.error = ""
            elif key.lower() == "a":
                all_areas = {area.key for area in AREAS}
                self.selected_areas = set() if self.selected_areas == all_areas else all_areas
            elif key == "enter":
                area = AREAS[cursor].key
                if area in expanded:
                    expanded.remove(area)
                else:
                    expanded.add(area)
            elif key in {"left", "b", "B"}:
                self.leases.release()
                return False
            elif key in {"right", "n", "N"}:
                if not self.selected_areas:
                    self.error = "Select at least one test area."
                else:
                    return True
            elif key in {"cancel", "escape"}:
                raise WizardCancelled()

    def _strategy_screen(self, keys: RawKeys) -> bool:
        choices = (ExecutionMode.EVERY, ExecutionMode.MANUAL, ExecutionMode.AUTOMATIC)
        cursor = choices.index(self.mode)
        selected = self._selected_tests()
        selected_tests = len(selected)
        devices = self._selected_devices()
        compatible_executions = sum(
            sum(test_compatible(test, device) for test in selected)
            for device in devices
        )
        while True:
            rows = []
            descriptions = {
                ExecutionMode.EVERY: ("Run every selected test on every compatible emulator",
                                      f"{len(self.selected_serials)} device runs · "
                                      f"{compatible_executions} compatible executions"),
                ExecutionMode.MANUAL: ("Manually distribute tests",
                                        "Each supported test runs once; edit emulator assignment"),
                ExecutionMode.AUTOMATIC: ("Automatically distribute by test count",
                                           "Each supported test runs once; skip unsupported atomic units"),
            }
            for index, mode in enumerate(choices):
                pointer = "›" if index == cursor else " "
                mark = "●" if self.mode is mode else " "
                name, description = descriptions[mode]
                rows += [f"{pointer} ({mark}) {name}", f"      {description}", ""]
            body = [f"How should {selected_tests} selected tests run on "
                    f"{len(self.selected_serials)} emulators?", "", *rows,
                    "↑↓ move · Space select", self._buttons()]
            self._draw("MJ PDF · Setup 3/4 · Execution strategy", body)
            key = keys.read()
            if key == "up":
                cursor = (cursor - 1) % len(choices)
            elif key == "down":
                cursor = (cursor + 1) % len(choices)
            elif key in {"space", "enter"}:
                self.mode = choices[cursor]
            elif key in {"left", "b", "B"}:
                return False
            elif key in {"right", "n", "N"}:
                self.mode = choices[cursor]
                return True
            elif key in {"cancel", "escape"}:
                raise WizardCancelled()

    def _selected_devices(self) -> tuple[DeviceInfo, ...]:
        return tuple(device for device in self.devices if device.serial in self.selected_serials)

    def _selected_tests(self) -> tuple[TestSpec, ...]:
        return tuple(test for test in self.tests if test.area in self.selected_areas)

    def _preview_screen(self, keys: RawKeys, plan: AssignmentPlan) -> bool:
        counts = plan.counts()
        devices = self._selected_devices()
        estimates = plan.estimated_seconds(devices)
        fallbacks = plan.duration_fallback_counts()
        rows = ["Balanced by duration category and emulator profile.", ""]
        for device in devices:
            rows.append(f"{device.model} · {device.serial} · API {device.api or '-'} · "
                        f"{device.short_renderer} · {counts[device.serial]} tests · "
                        f"~{estimates[device.serial] / 60:.1f} min work")
            if fallbacks[device.serial]:
                rows.append(f"  {fallbacks[device.serial]} unmarked test(s) use NORMAL")
            if duration_profile_key(device) not in DURATION_CALIBRATION["profileFactors"]:
                rows.append("  Uncalibrated emulator profile: neutral speed estimate")
        values = list(estimates.values())
        difference = max(values) - min(values) if values else 0
        rows += ["", f"Estimated work spread: {difference / 60:.1f} min",
                 "Excludes build, install and common setup.",
                 "Atomic parameterized classes and phase scenarios remain intact.", "",
                 self._buttons(next_label="Review")]
        while True:
            self._draw("MJ PDF · Setup 4/5 · Automatic distribution", rows)
            key = keys.read()
            if key in {"left", "b", "B"}:
                return False
            if key in {"enter", "right", "n", "N"}:
                return True
            if key in {"cancel", "escape"}:
                raise WizardCancelled()

    def _manual_screen(self, keys: RawKeys, plan: AssignmentPlan) -> tuple[bool, AssignmentPlan]:
        units = tuple(unit for assigned in plan.assignments.values() for unit in assigned)
        units = tuple(sorted(units, key=lambda unit: (unit.area, unit.display)))
        devices = self._selected_devices()
        cursor = 0
        query = ""
        while True:
            shown = tuple(unit for unit in units if query.lower() in unit.display.lower())
            if shown:
                cursor = min(cursor, len(shown) - 1)
            else:
                cursor = 0
            max_rows = max(4, self.panel.size.lines - 11)
            start = max(0, min(cursor - max_rows // 2, max(0, len(shown) - max_rows)))
            assigned_to = {unit.key: serial for serial, assigned in plan.assignments.items()
                           for unit in assigned}
            rows = []
            for index, unit in enumerate(shown[start:start + max_rows], start):
                pointer = "›" if index == cursor else " "
                atomic = f" ({unit.count} atomic)" if unit.count > 1 else ""
                rows.append(f"{pointer} {unit.display}{atomic} → {assigned_to[unit.key]}")
            counts = plan.counts()
            estimates = plan.estimated_seconds(devices)
            totals = " · ".join(f"{serial} {count} tests ~{estimates[serial] / 60:.1f}m"
                                for serial, count in counts.items())
            body = [f"{plan.execution_count} tests · {len(units)} assignment units", "",
                    *(rows or ["No tests match the filter."]), "", totals,
                    f"Filter: {query or '-'}",
                    "↑↓ move · ←→ assign · G assign area · / filter",
                    self._buttons(next_label="Review")]
            self._draw("MJ PDF · Setup 4/5 · Manual distribution", body)
            key = keys.read()
            if key == "up" and shown:
                cursor = (cursor - 1) % len(shown)
            elif key == "down" and shown:
                cursor = (cursor + 1) % len(shown)
            elif key in {"left", "right"} and shown:
                unit = shown[cursor]
                current = next(index for index, device in enumerate(devices)
                               if device.serial == assigned_to[unit.key])
                direction = -1 if key == "left" else 1
                for offset in range(1, len(devices) + 1):
                    target = devices[(current + direction * offset) % len(devices)]
                    if compatible(unit, target):
                        plan = reassign(plan, unit.key, target.serial)
                        break
            elif key == "G" and shown:
                unit = shown[cursor]
                target_serial = assigned_to[unit.key]
                target = next(device for device in devices if device.serial == target_serial)
                for candidate in units:
                    if candidate.area == unit.area and compatible(candidate, target):
                        plan = reassign(plan, candidate.key, target_serial)
            elif key == "/":
                query = self._filter(keys, query)
                cursor = 0
            elif key in {"backspace"} and query:
                query = query[:-1]
                cursor = 0
            elif key in {"b", "B"}:
                return False, plan
            elif key in {"enter", "n", "N"}:
                return True, plan
            elif key in {"cancel", "escape"}:
                raise WizardCancelled()

    def _filter(self, keys: RawKeys, initial: str) -> str:
        value = initial
        while True:
            self._draw("MJ PDF · Filter assignments", ["Type a class, method, or scenario name.", "",
                                                        f"/ {value}_", "",
                                                        "Enter apply · Esc clear"])
            key = keys.read()
            if key == "enter":
                return value
            if key == "escape":
                return ""
            if key == "backspace":
                value = value[:-1]
            elif len(key) == 1 and key.isprintable():
                value += key

    def _review_screen(self, keys: RawKeys, plan: AssignmentPlan,
                       units: tuple[TestUnit, ...]) -> bool:
        devices = self._selected_devices()
        errors = validate_plan(plan, units, devices)
        counts = plan.counts()
        estimates = plan.estimated_seconds(devices)
        fallbacks = plan.duration_fallback_counts()
        skipped_executions = (
            sum(unit.count for unit in units) * len(devices) - plan.execution_count
            if plan.mode is ExecutionMode.EVERY
            else plan.skipped_count
        )
        skipped_rows = (
            [f"Skipped       {skipped_executions} incompatible execution(s)"]
            if skipped_executions
            else []
        )
        area_names = [area.name for area in AREAS if area.key in self.selected_areas]
        rows = [f"Devices       {len(devices)}",
                f"Areas         {', '.join(area_names)}",
                f"Strategy      {plan.mode.value.replace('_', ' ').title()}",
                f"Tests         {sum(unit.count for unit in units)} unique · "
                f"{plan.execution_count} executions",
                *skipped_rows,
                "Distribution  " + " / ".join(str(counts[device.serial]) for device in devices),
                "Work + setup  " + " / ".join(
                    f"~{estimates[device.serial] / 60:.1f} min" for device in devices),
                f"Unmarked      {sum(fallbacks.values())} test(s) use NORMAL",
                "Profiles      " + str(sum(
                    duration_profile_key(device) not in DURATION_CALIBRATION["profileFactors"]
                    for device in devices)) + " uncalibrated; neutral speed estimate",
                f"Renderer      {sum(device.renderer.value == 'hardware' for device in devices)} hardware · "
                f"{sum(device.renderer.value == 'software' for device in devices)} SwiftShader",
                "Build         Once, shared by every device run",
                "", "✓ Every selected emulator is leased and online"]
        if errors:
            rows += [f"✗ {message}" for message in errors[:4]]
        else:
            rows += ["✓ Every selected test has the required assignment count",
                     "✓ Renderer and atomic-unit constraints are satisfied"]
        rows += ["", self._buttons(next_label="Run" if not errors else "Blocked")]
        while True:
            self._draw("MJ PDF · Review", rows)
            key = keys.read()
            if key in {"left", "b", "B"}:
                return False
            if key in {"enter", "right", "n", "N"} and not errors:
                return True
            if key in {"cancel", "escape"}:
                raise WizardCancelled()

    def run(self) -> WizardResult:
        with RawKeys() as keys:
            while not self._device_screen(keys):
                pass
            while True:
                if not self._area_screen(keys):
                    while not self._device_screen(keys):
                        pass
                    continue
                if not self._strategy_screen(keys):
                    continue
                tests = self._selected_tests()
                units = build_units(tests)
                try:
                    devices = self._selected_devices()
                    if self.mode is ExecutionMode.EVERY:
                        plan = every_plan(units, devices)
                    else:
                        plan = automatic_plan(units, devices,
                                              ExecutionMode.MANUAL if self.mode is ExecutionMode.MANUAL
                                              else ExecutionMode.AUTOMATIC)
                except (ValueError, DeviceDiscoveryError) as exc:
                    self.error = str(exc)
                    continue
                self.error = ""
                if self.mode is ExecutionMode.AUTOMATIC:
                    advanced = self._preview_screen(keys, plan)
                    if not advanced:
                        continue
                elif self.mode is ExecutionMode.MANUAL:
                    advanced, plan = self._manual_screen(keys, plan)
                    if not advanced:
                        continue
                if not self._review_screen(keys, plan, units):
                    continue
                return WizardResult(devices, tuple(sorted(self.selected_areas)), tests, units, plan)
