#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import signal
import sys
from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, TypeVar

from mjpdf_runner.catalog import resolve_fixture_requirements
from mjpdf_runner.coordinator import Coordinator
from mjpdf_runner.devices import DeviceDiscoveryError, LeaseManager
from mjpdf_runner.interactive import SetupWizard, WizardCancelled
from mjpdf_runner.model import RendererLane, RunPaths, RunnerConfig
from mjpdf_runner.phases import ScenarioError, parse_scenario
from mjpdf_runner.results import print_final
from mjpdf_runner.runner import Runner
from mjpdf_runner.process import HostResourceMonitor
from mjpdf_runner.ui import TTYRenderer, make_renderer
from mjpdf_runner.terminal import InlinePanel

PACKAGE = "com.gitlab.mudlej.MjPdfReader"
SOFTWARE_ANNOTATION = f"{PACKAGE}.suite.rules.RequiresSoftwareRenderer"
T = TypeVar("T")


class Parser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        self.print_usage(sys.stderr)
        print(f"run.py: error: {message}", file=sys.stderr)
        raise SystemExit(2)


def shared_parser() -> argparse.ArgumentParser:
    result = Parser(add_help=False, allow_abbrev=False)
    leasing = result.add_argument_group("emulator leasing (also accepted by the interactive TUI)")
    leasing.add_argument("--lease-script", type=Path, metavar="PATH",
                         help="lease-an-emulator.sh to lease emulators with, "
                              "default ../Scripts/android-emulator/lease-an-emulator.sh next to the repository")
    leasing.add_argument("--lease-agent", metavar="NAME",
                         help="name to hold leases under, default EMU_AGENT or mjpdf-<mode>-<pid>")
    leasing.add_argument("--no-lease", action="store_true",
                         help="run without leasing, so other runs and people may use the same emulators")
    leasing.add_argument("--force-stop", dest="force_stop", action="append", default=[], metavar="PACKAGE",
                         help="also force stop this package before the run, repeatable")
    return result


def parser() -> argparse.ArgumentParser:
    result = Parser(
        parents=[shared_parser()],
        prog="python3 testing/runner/run.py",
        description="Run the MJ PDF Reader Android E2E suite under Android Test Orchestrator.",
        epilog=("Exit status: 0 clean/help/dry-run/scenario skip; 1 suite or operational failure; "
                "2 usage; 3 device/pin failure; 4 shared-storage leftovers. "
                "The separate testing/runner/release-guard.sh checks release builds for debug hooks."),
    )
    filters = result.add_argument_group("filters (repeatable)")
    filters.add_argument("--class", dest="classes", action="append", default=[], metavar="FQCN[#method]")
    filters.add_argument("--package", dest="packages", action="append", default=[], metavar="FQCN")
    filters.add_argument("--annotation", dest="annotations", action="append", default=[], metavar="FQCN")
    filters.add_argument("--not-annotation", dest="not_annotations", action="append", default=[], metavar="FQCN")
    filters.add_argument("--test-file", type=Path, metavar="HOST_PATH")
    shape = result.add_argument_group("run shape")
    shape.add_argument("--phase-scenario", metavar="NAME")
    shape.add_argument("--with-perf-library", action="store_true",
                       help="place the 100000 file perf library the macrobenchmark needs under Documents/perf-library")
    shape.add_argument("--sweep-perf-library", action="store_true",
                       help="remove the macrobenchmark perf library and its MediaStore rows during cleanup")
    shape.add_argument("--skip-build", action="store_true")
    shape.add_argument("--skip-generated", action="store_true")
    shape.add_argument("--no-clear-package-data", action="store_true")
    shape.add_argument("--keep-pins", action="store_true")
    shape.add_argument("--renderer-lane", choices=[lane.value for lane in RendererLane], default="hardware")
    shape.add_argument("--dry-run", action="store_true")
    shape.add_argument("--trace-timing", action="store_true")
    shape.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    result.add_argument("passthrough", nargs=argparse.REMAINDER,
                        help="arguments after -- are passed to am instrument unchanged")
    return result


def _nonempty(values: list[str], option: str, argument_parser: argparse.ArgumentParser) -> tuple[str, ...]:
    if any(not value.strip() for value in values):
        argument_parser.error(f"{option} values must not be empty")
    return tuple(values)


def _create_paths(runner_dir: Path) -> tuple[str, RunPaths]:
    runs = runner_dir / "runs"
    while True:
        run_id = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f")
        try:
            return run_id, RunPaths.create(runs / run_id)
        except FileExistsError:
            continue

NO_LEASE_WARNING = ("run.py: WARNING: --no-lease given, emulators are not leased and "
                    "other runs or people may use the same emulators")


def _check_shared(options: argparse.Namespace, argument_parser: argparse.ArgumentParser) -> None:
    if options.no_lease and (options.lease_script is not None or options.lease_agent is not None):
        argument_parser.error("--no-lease cannot be combined with --lease-script or --lease-agent")
    if options.lease_agent is not None and not options.lease_agent.strip():
        argument_parser.error("--lease-agent must not be empty")
    _nonempty(options.force_stop, "--force-stop", argument_parser)


def _lease_script(repo_root: Path, options: argparse.Namespace) -> Path | None | int:
    if options.no_lease:
        print(NO_LEASE_WARNING, file=sys.stderr)
        return None
    script = options.lease_script
    if script is None:
        tools = repo_root.parent / "Scripts" / "android-emulator"
        script = Path(os.path.relpath(tools, Path.cwd())) / "lease-an-emulator.sh"
    if not script.is_file():
        print(f"run.py: emulator lease manager not found: {script}. "
              "Pass --lease-script PATH, or --no-lease to run without leasing", file=sys.stderr)
        return 3
    return script


def _lease_agent(options: argparse.Namespace, mode: str) -> str:
    return options.lease_agent or os.environ.get("EMU_AGENT", f"mjpdf-{mode}-{os.getpid()}")

def _run_with_shared_lease(
    lease_script: Path,
    agent: str,
    serial: str,
    action: Callable[[], T],
) -> T:
    lease = LeaseManager(
        lease_script,
        agent,
        task="MJ PDF CLI test run",
    )
    lease.claim((serial,))
    try:
        return action()
    finally:
        lease.release()




def interactive_main(repo_root: Path, runner_dir: Path, shared: argparse.Namespace) -> int:
    if not sys.stdin.isatty() or not sys.stdout.isatty():
        print("run.py: no run options opens the interactive TUI and requires a terminal", file=sys.stderr)
        return 2
    adb = os.environ.get("ADB", "adb")
    lease_script = _lease_script(repo_root, shared)
    if isinstance(lease_script, int):
        return lease_script
    agent = _lease_agent(shared, "interactive")
    panel = InlinePanel(sys.stdout)
    wizard: SetupWizard | None = None
    try:
        wizard = SetupWizard(repo_root, runner_dir, adb, lease_script, agent, panel)
        selection = wizard.run()
        _, paths = _create_paths(runner_dir)
        coordinator = Coordinator(selection, repo_root, runner_dir, paths, adb, panel, sys.stdout,
                                  force_stop_packages=tuple(shared.force_stop))
        previous = {sig: signal.getsignal(sig) for sig in (signal.SIGINT, signal.SIGTERM)}
        for sig in previous:
            signal.signal(sig, lambda signum, _frame: coordinator.interrupt(signum))
        try:
            with HostResourceMonitor(paths.root / "host-resources.jsonl"):
                return int(coordinator.run())
        finally:
            for sig, handler in previous.items():
                signal.signal(sig, handler)
    except WizardCancelled:
        panel.close(erase=True)
        print("Setup cancelled.", file=sys.stdout)
        return 130
    except (OSError, RuntimeError, ValueError) as exc:
        panel.close(erase=True)
        print(f"run.py: {exc}", file=sys.stderr)
        return 1
    finally:
        if wizard is not None:
            wizard.leases.release()
        panel.close()



def main(argv: list[str] | None = None) -> int:
    raw_args = list(sys.argv[1:] if argv is None else argv)
    repo_root = Path(__file__).resolve().parents[2]
    runner_dir = repo_root / "testing" / "runner"
    shared, rest = shared_parser().parse_known_args(raw_args)
    if not rest:
        _check_shared(shared, parser())
        return interactive_main(repo_root, runner_dir, shared)
    argument_parser = parser()
    options = argument_parser.parse_args(raw_args)
    _check_shared(options, argument_parser)
    classes = _nonempty(options.classes, "--class", argument_parser)
    packages = _nonempty(options.packages, "--package", argument_parser)
    annotations = list(_nonempty(options.annotations, "--annotation", argument_parser))
    not_annotations = list(_nonempty(options.not_annotations, "--not-annotation", argument_parser))
    if not options.serial.strip():
        argument_parser.error("--serial must not be empty")
    test_file = options.test_file.resolve() if options.test_file else None
    if test_file is not None and not test_file.is_file():
        argument_parser.error(f"test file does not exist: {test_file}")
    scenario = None
    if options.phase_scenario:
        if not re.fullmatch(r"[A-Za-z0-9._-]+", options.phase_scenario):
            argument_parser.error("--phase-scenario must be a scenario name, not a path")
        scenario_path = runner_dir / "phases" / f"{options.phase_scenario}.phases"
        if not scenario_path.is_file():
            argument_parser.error(f"no scenario file at {scenario_path}")
        try:
            scenario = parse_scenario(scenario_path, options.phase_scenario)
        except ScenarioError as exc:
            argument_parser.error(str(exc))
    lane = RendererLane(options.renderer_lane)
    if lane is RendererLane.HARDWARE:
        not_annotations.append(SOFTWARE_ANNOTATION)
    else:
        annotations.append(SOFTWARE_ANNOTATION)
    passthrough = options.passthrough
    if passthrough and passthrough[0] == "--":
        passthrough = passthrough[1:]
    profile_size = os.environ.get("PROFILE_SIZE", "1080x2400")
    profile_density = os.environ.get("PROFILE_DENSITY", "440")
    if not re.fullmatch(r"[1-9]\d*x[1-9]\d*", profile_size):
        argument_parser.error(f"invalid PROFILE_SIZE: {profile_size!r}")
    if not profile_density.isdigit() or int(profile_density) <= 0:
        argument_parser.error(f"invalid PROFILE_DENSITY: {profile_density!r}")
    profile_locale = os.environ.get("PROFILE_LOCALE", "en-US")
    profile_timezone = os.environ.get("PROFILE_TIMEZONE", "UTC")
    logcat_buffer = os.environ.get("LOGCAT_BUFFER", "64M")
    adb = os.environ.get("ADB", "adb")
    if not profile_locale.strip() or not profile_timezone.strip():
        argument_parser.error("PROFILE_LOCALE and PROFILE_TIMEZONE must not be empty")
    if not re.fullmatch(r"[1-9]\d*[KMG]?", logcat_buffer, re.IGNORECASE):
        argument_parser.error(f"invalid LOGCAT_BUFFER: {logcat_buffer!r}")
    if not adb.strip():
        argument_parser.error("ADB must not be empty")
    lease_script: Path | None = None
    lease_agent: str | None = None
    if not options.dry_run:
        found = _lease_script(repo_root, options)
        if isinstance(found, int):
            return found
        lease_script = found
        if lease_script is not None:
            lease_agent = _lease_agent(options, "cli")
    run_id, paths = _create_paths(runner_dir)
    config = RunnerConfig(
        repo_root=repo_root, runner_dir=runner_dir, run_id=run_id,
        serial=options.serial, adb=adb, package=PACKAGE,
        test_package=f"{PACKAGE}.test", profile_size=profile_size,
        profile_density=profile_density, profile_locale=profile_locale,
        profile_timezone=profile_timezone,
        logcat_buffer=logcat_buffer, renderer_lane=lane,
        classes=classes, packages=packages, annotations=tuple(annotations),
        not_annotations=tuple(not_annotations), test_file=test_file,
        phase_scenario=options.phase_scenario, passthrough=tuple(passthrough),
        with_perf_library=options.with_perf_library,
        sweep_perf_library=options.sweep_perf_library, skip_build=options.skip_build,
        skip_generated=options.skip_generated, clear_package_data=not options.no_clear_package_data,
        keep_pins=options.keep_pins, dry_run=options.dry_run, trace_timing=options.trace_timing,
        force_stop_packages=tuple(options.force_stop),
    )
    requirements, resolution = resolve_fixture_requirements(config)
    config = replace(config, required_fixtures=requirements, fixture_resolution=resolution)
    renderer = make_renderer()
    runner = Runner(config, paths, renderer, scenario)
    previous = {sig: signal.getsignal(sig) for sig in (signal.SIGINT, signal.SIGTERM)}
    for sig in previous:
        signal.signal(sig, lambda signum, _frame: runner.interrupt(signum))
    try:
        if options.dry_run:
            code = runner.run()
        elif lease_script is None:
            with HostResourceMonitor(paths.root / "host-resources.jsonl"):
                code = runner.run()
        else:
            with HostResourceMonitor(paths.root / "host-resources.jsonl"):
                code = _run_with_shared_lease(
                    lease_script,
                    lease_agent,
                    options.serial,
                    runner.run,
                )
    except DeviceDiscoveryError as exc:
        print(f"run.py: {exc}", file=sys.stderr)
        return 3
    finally:
        for sig, handler in previous.items():
            signal.signal(sig, handler)
        # Idempotent and guarantees terminal restoration if summary writing failed.
        renderer.close()
    assert runner.summary is not None
    print_final(runner.summary, paths.root, sys.stdout, include_tests=True)
    if isinstance(renderer, TTYRenderer):
        renderer.print_final_panel()
    return int(code)


if __name__ == "__main__":
    raise SystemExit(main())
