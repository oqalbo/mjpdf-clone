from __future__ import annotations

import json
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

from .catalog import TestSpec, TestUnit
from .devices import DeviceInfo
from .model import RendererLane


DURATION_CALIBRATION = json.loads(
    (Path(__file__).resolve().parents[1] / "duration-costs.json").read_text(encoding="utf-8")
)


def duration_profile_key(device: DeviceInfo) -> str:
    return f"{device.pinned_profile}|{device.abi}|{device.model}"


def duration_factor(device: DeviceInfo) -> float:
    return DURATION_CALIBRATION["profileFactors"].get(duration_profile_key(device), 1.0)


def unit_seconds(unit: TestUnit) -> float:
    active = [test for test in unit.tests if not test.ignored]
    if not active:
        return 0.0
    seconds = sum(DURATION_CALIBRATION["categorySeconds"][test.duration_category]
                  for test in active)
    if unit.scenario:
        seconds += DURATION_CALIBRATION["scenarioOverheadSeconds"].get(
            unit.scenario, DURATION_CALIBRATION["isolatedOverheadSeconds"])
    elif unit.hostile:
        seconds += DURATION_CALIBRATION["isolatedOverheadSeconds"]
    return seconds


class ExecutionMode(str, Enum):
    EVERY = "every"
    MANUAL = "manual"
    AUTOMATIC = "automatic"


@dataclass(frozen=True)
class AssignmentPlan:
    mode: ExecutionMode
    assignments: dict[str, tuple[TestUnit, ...]]
    skipped: dict[str, tuple[TestUnit, ...]]

    @property
    def execution_count(self) -> int:
        return sum(unit.count for units in self.assignments.values() for unit in units)

    def counts(self) -> dict[str, int]:
        return {serial: sum(unit.count for unit in units)
                for serial, units in self.assignments.items()}

    def estimated_seconds(self, devices: tuple[DeviceInfo, ...]) -> dict[str, float]:
        return {
            device.serial: sum(unit_seconds(unit) for unit in self.assignments[device.serial])
            * duration_factor(device)
            for device in devices
        }

    def duration_fallback_counts(self) -> dict[str, int]:
        return {
            serial: sum(not test.duration_explicit and not test.ignored
                        for unit in units for test in unit.tests)
            for serial, units in self.assignments.items()
        }

    @property
    def skipped_count(self) -> int:
        return sum(unit.count for units in self.skipped.values() for unit in units)

    def skipped_counts(self) -> dict[str, int]:
        return {serial: sum(unit.count for unit in units)
                for serial, units in self.skipped.items()}


def _requirements_compatible(software_only: bool, required_profile: str | None,
                             min_api: int | None, max_api: int | None,
                             device: DeviceInfo) -> bool:
    if software_only and device.renderer is not RendererLane.SOFTWARE:
        return False
    if required_profile is not None and device.pinned_profile != required_profile:
        return False
    if min_api is not None and (device.api is None or device.api < min_api):
        return False
    if max_api is not None and (device.api is None or device.api > max_api):
        return False
    return True


def test_compatible(test: TestSpec, device: DeviceInfo) -> bool:
    return _requirements_compatible(test.software_only, test.required_profile,
                                    test.min_api, test.max_api, device)


def compatible(unit: TestUnit, device: DeviceInfo) -> bool:
    return _requirements_compatible(unit.software_only, unit.required_profile,
                                    unit.min_api, unit.max_api, device)


def validate_plan(plan: AssignmentPlan, units: tuple[TestUnit, ...],
                  devices: tuple[DeviceInfo, ...]) -> tuple[str, ...]:
    errors: list[str] = []
    device_by_serial = {device.serial: device for device in devices}
    expected = {unit.key for unit in units}
    seen: dict[str, int] = {key: 0 for key in expected}
    for serial, assigned in plan.assignments.items():
        device = device_by_serial.get(serial)
        if device is None:
            errors.append(f"assignment names unknown emulator {serial}")
            continue
        for unit in assigned:
            if unit.key not in seen:
                errors.append(f"assignment contains unselected test unit {unit.display}")
                continue
            seen[unit.key] += 1
            if not compatible(unit, device):
                errors.append(f"{unit.display} is incompatible with {serial}")
    for unit in units:
        occurrences = seen[unit.key]
        compatible_devices = sum(compatible(unit, device) for device in devices)
        wanted_occurrences = compatible_devices if plan.mode is ExecutionMode.EVERY else min(
            1, compatible_devices)
        if occurrences != wanted_occurrences:
            errors.append(
                f"{unit.display} is assigned {occurrences} time(s), expected {wanted_occurrences}"
            )
    expected_skipped = {
        serial: {
            unit.key for unit in units
            if (plan.mode is ExecutionMode.EVERY and not compatible(unit, device))
        }
        for serial, device in device_by_serial.items()
    }
    if plan.mode is not ExecutionMode.EVERY:
        unsupported = {unit.key for unit in units
                       if not any(compatible(unit, device) for device in devices)}
        expected_skipped = {serial: set() for serial in device_by_serial}
        if devices:
            expected_skipped[devices[0].serial] = unsupported
    actual_skipped = {
        serial: {unit.key for unit in skipped}
        for serial, skipped in plan.skipped.items()
    }
    duplicate_skips = any(
        len(skipped) != len({unit.key for unit in skipped})
        for skipped in plan.skipped.values()
    )
    if duplicate_skips or actual_skipped != expected_skipped:
        errors.append("incompatible skipped assignments do not match device compatibility")
    return tuple(errors)


def every_plan(units: tuple[TestUnit, ...], devices: tuple[DeviceInfo, ...]) -> AssignmentPlan:
    assignments = {
        device.serial: tuple(unit for unit in units if compatible(unit, device))
        for device in devices
    }
    skipped = {
        device.serial: tuple(unit for unit in units if not compatible(unit, device))
        for device in devices
    }
    return AssignmentPlan(ExecutionMode.EVERY, assignments, skipped)


def automatic_plan(units: tuple[TestUnit, ...], devices: tuple[DeviceInfo, ...],
                   mode: ExecutionMode = ExecutionMode.AUTOMATIC) -> AssignmentPlan:
    if not devices:
        raise ValueError("automatic distribution needs at least one emulator")
    assigned: dict[str, list[TestUnit]] = {device.serial: [] for device in devices}
    skipped: list[TestUnit] = []
    loads = {device.serial: 0.0 for device in devices}
    counts = {device.serial: 0 for device in devices}
    costs = {unit.key: unit_seconds(unit) for unit in units}
    factors = {device.serial: duration_factor(device) for device in devices}
    choices_by_key = {
        unit.key: tuple(device for device in devices if compatible(unit, device))
        for unit in units
    }
    ordered = sorted(units, key=lambda unit: (
        len(choices_by_key[unit.key]), -costs[unit.key], unit.area, unit.display, unit.key))
    for unit in ordered:
        choices = choices_by_key[unit.key]
        if not choices:
            skipped.append(unit)
            continue
        target = min(choices, key=lambda device: (
            loads[device.serial] + costs[unit.key] * factors[device.serial],
            counts[device.serial],
            device.serial,
        ))
        assigned[target.serial].append(unit)
        loads[target.serial] += costs[unit.key] * factors[target.serial]
        counts[target.serial] += unit.count
    skipped_by_device: dict[str, tuple[TestUnit, ...]] = {
        device.serial: () for device in devices
    }
    skipped_by_device[devices[0].serial] = tuple(skipped)
    return AssignmentPlan(mode, {serial: tuple(value) for serial, value in assigned.items()},
                          skipped_by_device)


def reassign(plan: AssignmentPlan, unit_key: str, serial: str) -> AssignmentPlan:
    assignments = {device: list(units) for device, units in plan.assignments.items()}
    moved: TestUnit | None = None
    for units in assignments.values():
        for unit in list(units):
            if unit.key == unit_key:
                units.remove(unit)
                moved = unit
                break
        if moved is not None:
            break
    if moved is None:
        raise KeyError(unit_key)
    if serial not in assignments:
        raise KeyError(serial)
    assignments[serial].append(moved)
    return AssignmentPlan(ExecutionMode.MANUAL,
                          {device: tuple(units) for device, units in assignments.items()},
                          plan.skipped)
