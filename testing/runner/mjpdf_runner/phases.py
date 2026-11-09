from __future__ import annotations

import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Callable

from .fixtures import SDCARD
from .model import (CommandSpec, PhaseAction, PhaseScenario, RunnerConfig, TestResult,
                    TestStatus)
from .process import CommandRunner
from .storage import Storage

PHASE_STATE_DIR = f"{SDCARD}/Documents/mjpdf-phase-state"


class ScenarioError(ValueError):
    pass


def parse_scenario(path: Path, name: str) -> PhaseScenario:
    description = ""
    requirements: list[str] = []
    actions: list[PhaseAction] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith(("#", "//")):
            continue
        directive, separator, value = line.partition(" ")
        value = value.strip() if separator else ""
        if directive == "describe":
            description = value
        elif directive == "require":
            if not value:
                raise ScenarioError(f"{path}:{number}: require names no capability")
            if value != "secondary-volume":
                raise ScenarioError(f"{path}:{number}: unknown capability {value!r}")
            requirements.append(value)
        elif directive == "phase":
            if not value:
                raise ScenarioError(f"{path}:{number}: phase names no test")
            actions.append(PhaseAction("phase", value))
        elif directive == "kill-app":
            if value:
                raise ScenarioError(f"{path}:{number}: kill-app takes no value")
            actions.append(PhaseAction("kill"))
        elif directive == "launch-view":
            if not value:
                raise ScenarioError(f"{path}:{number}: launch-view names no document")
            actions.append(PhaseAction("view", value))
        elif directive == "launch-view-after-start":
            if not value:
                raise ScenarioError(
                    f"{path}:{number}: launch-view-after-start names no document"
                )
            actions.append(PhaseAction("view-after-start", value))
        elif directive == "launch-task":
            if value:
                raise ScenarioError(f"{path}:{number}: launch-task takes no value")
            actions.append(PhaseAction("task"))
        elif directive == "launch-task-after-start":
            if value:
                raise ScenarioError(
                    f"{path}:{number}: launch-task-after-start takes no value"
                )
            actions.append(PhaseAction("task-after-start"))
        elif directive == "assert-ui-text":
            if not value:
                raise ScenarioError(f"{path}:{number}: assert-ui-text names no text")
            actions.append(PhaseAction("assert-ui-text", value))
        elif directive == "assert-ui-any-text":
            alternatives = tuple(part.strip() for part in value.split("||"))
            if len(alternatives) < 2 or any(not part for part in alternatives):
                raise ScenarioError(
                    f"{path}:{number}: assert-ui-any-text needs two or more ||-separated texts"
                )
            actions.append(PhaseAction("assert-ui-any-text", value))
        elif directive == "click-ui-text":
            if not value:
                raise ScenarioError(f"{path}:{number}: click-ui-text names no text")
            actions.append(PhaseAction("click-ui-text", value))
        elif directive == "await-app-background":
            if value:
                raise ScenarioError(f"{path}:{number}: await-app-background takes no value")
            actions.append(PhaseAction("await-app-background"))
        elif directive in ("record-app-task", "restore-app-task"):
            if value:
                raise ScenarioError(f"{path}:{number}: {directive} takes no value")
            actions.append(PhaseAction(directive))
        elif directive == "device-shell":
            if not value:
                raise ScenarioError(f"{path}:{number}: device-shell has no command")
            actions.append(PhaseAction("device-shell", value))
        elif directive == "cleanup-device-shell":
            if not value:
                raise ScenarioError(
                    f"{path}:{number}: cleanup-device-shell has no command"
                )
            actions.append(PhaseAction("cleanup-device-shell", value))
        else:
            raise ScenarioError(f"{path}:{number}: unknown directive {directive!r}")
    if not any(action.kind == "phase" for action in actions):
        raise ScenarioError(f"{path} declares no phases")
    return PhaseScenario(name, description, tuple(requirements), tuple(actions))


class PhaseRunner:
    def __init__(self, config: RunnerConfig, commands: CommandRunner, storage: Storage,
                 scenario: PhaseScenario,
                  instrument: Callable[
                      [list[str], int, Callable[[], None] | None], list[TestResult]
                  ],
                  warn: Callable[[str], None], notice: Callable[[str], None]):
        self.config = config
        self.commands = commands
        self.storage = storage
        self.scenario = scenario
        self.instrument = instrument
        self.warn = warn
        self.notice = notice
        self.actions: list[dict[str, object]] = []
        self.app_task: tuple[str, str] | None = None
        self.ui_nodes: list[ET.Element] = []

    def _adb(self, *args: str) -> tuple[str, ...]:
        return (self.config.adb, "-s", self.config.serial, *args)

    def run_command(self, name: str, *args: str, mutate: bool = True, check: bool = True) -> str:
        return self.commands.run(CommandSpec(self._adb(*args), f"phase-{name}", mutate=mutate),
                                 check=check).output.strip().replace("\r", "")

    def requirement_skip(self) -> str | None:
        for requirement in self.scenario.requirements:
            if requirement == "secondary-volume":
                volumes = self.run_command("volumes", "shell", "sm", "list-volumes",
                                           mutate=False, check=False)
                if self.config.dry_run or not self._secondary_volumes(volumes):
                    return "the device has no secondary storage volume"
        return None

    @staticmethod
    def _secondary_volumes(output: str) -> list[tuple[str, str]]:
        volumes: list[tuple[str, str]] = []
        for line in output.splitlines():
            fields = line.strip().split()
            if (len(fields) < 3
                    or not fields[0].startswith(("public:", "private:"))
                    or fields[1] != "mounted"):
                continue
            location = fields[-1]
            if location == "null":
                continue
            if location.startswith("/"):
                path = location
            elif re.fullmatch(r"[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}", location):
                path = f"/storage/{location}"
            else:
                continue
            volumes.append((fields[0], path))
        return volumes

    def _pid(self) -> str:
        return self.run_command("pid", "shell", "pidof", self.config.package,
                                mutate=False, check=False).strip()

    def _record_kill(self, pid: str) -> None:
        stamp = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
        with (self.storage.root / "kills.txt").open("a", encoding="utf-8") as fh:
            fh.write(f"{stamp} scenario={self.scenario.name} pid={pid}\n")
        self.run_command("kill-ledger", "shell",
                         f"echo 'scenario={self.scenario.name} pid={pid}' >> "
                         f"{PHASE_STATE_DIR}/intentional-kills.txt", check=False)

    def _kill(self) -> None:
        if self.config.dry_run:
            self.commands.record("dry-run: prove app PID dies after am kill / kill -9")
            return
        before = self._pid()
        original_pids = set(before.split())
        if not original_pids:
            return
        restores_task = any(action.kind == "task-after-start" for action in self.scenario.actions)
        if restores_task and self.app_task is None:
            self._record_app_task()
        self.run_command("am-kill", "shell", "am", "kill", self.config.package, check=False)
        deadline = time.monotonic() + 1.0
        remaining = original_pids
        while remaining and time.monotonic() < deadline:
            remaining = original_pids.intersection(self._pid().split())
            if remaining:
                time.sleep(0.1)
        kill_prefix = ("shell", "run-as", self.config.package)
        if remaining:
            shell_uid = self.run_command("kill-shell-uid", "shell", "id", "-u", mutate=False)
            if shell_uid == "0":
                kill_prefix = ("shell",)
            elif self.run_command(
                "kill-su-uid", "shell", "su", "0", "id", "-u", mutate=False, check=False,
            ) == "0":
                kill_prefix = ("shell", "su", "0")
        for pid in sorted(remaining):
            if pid not in self._pid().split():
                continue
            self.run_command(
                f"kill9-{pid}", *kill_prefix, "kill", "-9", pid, check=False,
            )
        deadline = time.monotonic() + 5.0
        while remaining and time.monotonic() < deadline:
            remaining = original_pids.intersection(self._pid().split())
            if remaining:
                time.sleep(0.1)
        if remaining:
            raise RuntimeError(
                f"app process {' '.join(sorted(remaining))} is still alive after am kill and kill -9"
            )
        self._record_kill(before)

    @staticmethod
    def _ui_text_node(
        nodes: list[ET.Element], expected: str | tuple[str, ...]
    ) -> ET.Element | None:
        alternatives = (expected,) if isinstance(expected, str) else expected
        return next(
            (
                node
                for node in nodes
                if any(
                    alternative in (node.get("text", ""), node.get("content-desc", ""))
                    for alternative in alternatives
                )
            ),
            None,
        )

    def _find_ui_text(
        self, expected: str | tuple[str, ...], use_cached: bool = False
    ) -> ET.Element:
        if use_cached:
            cached = self._ui_text_node(self.ui_nodes, expected)
            if cached is not None:
                return cached
        dump = f"{PHASE_STATE_DIR}/window.xml"
        deadline = time.monotonic() + 30.0
        visible: list[str] = []
        while time.monotonic() < deadline:
            self.run_command("ui-dump", "shell", "uiautomator", "dump", dump, check=False)
            hierarchy = self.run_command(
                "ui-read", "shell", "cat", dump, mutate=False, check=False
            )
            try:
                root = ET.fromstring(hierarchy)
            except ET.ParseError:
                time.sleep(0.1)
                continue
            self.ui_nodes = list(root.iter("node"))
            visible = [
                value
                for node in self.ui_nodes
                for value in (node.get("text", ""), node.get("content-desc", ""))
                if value
            ]
            match = self._ui_text_node(self.ui_nodes, expected)
            if match is not None:
                return match
            time.sleep(0.1)
        raise RuntimeError(
            f"the UI did not expose {expected!r} within 30 seconds; "
            f"the final hierarchy exposed {visible}"
        )

    def _await_app_background(self) -> None:
        dump = f"{PHASE_STATE_DIR}/window.xml"
        deadline = time.monotonic() + 30.0
        while time.monotonic() < deadline:
            self.run_command("ui-dump", "shell", "uiautomator", "dump", dump, check=False)
            hierarchy = self.run_command(
                "ui-read", "shell", "cat", dump, mutate=False, check=False
            )
            try:
                root = ET.fromstring(hierarchy)
            except ET.ParseError:
                time.sleep(0.1)
                continue
            if not any(
                node.get("package") == self.config.package for node in root.iter("node")
            ):
                return
            time.sleep(0.1)
        raise RuntimeError(
            f"{self.config.package} remained visible for 30 seconds after being backgrounded"
        )

    @staticmethod
    def _app_task_from_stacks(stacks: str, package: str) -> tuple[str, str] | None:
        root_task = ""
        for line in stacks.splitlines():
            root_match = re.match(r"(?:RootTask|Stack) id=(\d+)", line)
            if root_match is not None:
                root_task = root_match.group(1)
                continue
            task_match = re.search(r"taskId=(\d+):", line)
            if task_match is not None and package in line and root_task:
                return task_match.group(1), root_task
        return None

    def _record_app_task(self) -> None:
        stacks = self.run_command(
            "stack-list", "shell", "am", "stack", "list", mutate=False
        )
        self.app_task = self._app_task_from_stacks(stacks, self.config.package)
        if self.app_task is None:
            raise RuntimeError(f"no activity task belongs to {self.config.package}; stacks were {stacks}")

    def _restore_app_task(self) -> None:
        if self.app_task is None:
            raise RuntimeError("restore-app-task ran before record-app-task")
        task_id, _ = self.app_task
        self.run_command("task-restore", "shell", "am", "task", "lock", task_id)
        self.run_command("task-unlock", "shell", "am", "task", "lock", "stop")

    @staticmethod
    def _ui_marker(expected: str) -> str:
        safe = re.sub(r"[^A-Za-z0-9_.-]+", "-", expected).strip("-")
        return f"{PHASE_STATE_DIR}/ui-{safe}"

    def _assert_ui_text(self, expected: str) -> None:
        self._find_ui_text(expected)
        self.run_command("ui-marker", "shell", "touch", self._ui_marker(expected))

    def _assert_ui_any_text(self, expected: str) -> None:
        alternatives = tuple(part.strip() for part in expected.split("||"))
        self._find_ui_text(alternatives)
        self.run_command("ui-marker", "shell", "touch", self._ui_marker(expected))

    def _click_ui_text(self, expected: str) -> None:
        node = self._find_ui_text(expected, use_cached=True)
        self.ui_nodes = []
        bounds = node.get("bounds", "")
        match = re.fullmatch(r"\[(\d+),(\d+)]\[(\d+),(\d+)]", bounds)
        if match is None:
            raise RuntimeError(f"the UI node {expected!r} has invalid bounds {bounds!r}")
        left, top, right, bottom = map(int, match.groups())
        self.run_command(
            "ui-click", "shell", "input", "tap",
            str((left + right) // 2), str((top + bottom) // 2),
        )

    def _run_action(self, action: PhaseAction) -> None:
        started = time.time()
        if action.kind == "kill":
            self._kill()
        elif action.kind in {"view", "view-after-start"}:
            package_args = (
                ("-p", self.config.package) if action.kind == "view-after-start" else ()
            )
            self.run_command(
                action.kind,
                "shell", "am", "start", "-a", "android.intent.action.VIEW",
                *package_args,
                "-d", action.value, "-t", "application/pdf",
            )
        elif action.kind == "task-after-start" and self.app_task is not None:
            self._restore_app_task()
        elif action.kind in {"task", "task-after-start"}:
            self.run_command(
                action.kind,
                "shell", "monkey", "-p", self.config.package,
                "-c", "android.intent.category.LAUNCHER", "1",
            )
        elif action.kind == "assert-ui-text":
            self._assert_ui_text(action.value)
        elif action.kind == "assert-ui-any-text":
            self._assert_ui_any_text(action.value)
        elif action.kind == "click-ui-text":
            self._click_ui_text(action.value)
        elif action.kind == "await-app-background":
            self._await_app_background()
        elif action.kind == "record-app-task":
            self._record_app_task()
        elif action.kind == "restore-app-task":
            self._restore_app_task()
        elif action.kind in {"device-shell", "cleanup-device-shell"}:
            self.run_command(action.kind, "shell", action.value)
        else:
            raise RuntimeError(f"unsupported phase action {action.kind!r}")
        self.actions.append({
            "kind": action.kind,
            "value": action.value,
            "startedAt": started,
            "finishedAt": time.time(),
        })

    def run(self) -> tuple[bool, str | None, list[dict[str, object]], str | None]:
        reason = self.requirement_skip()
        if reason:
            with (self.storage.root / "skipped-scenarios.txt").open("a", encoding="utf-8") as fh:
                fh.write(f"scenario {self.scenario.name} skipped: {reason}\n")
            return True, reason, self.actions, None
        self.run_command("state-remove", "shell", "rm", "-rf", PHASE_STATE_DIR)
        self.run_command("state-create", "shell", "mkdir", "-p", PHASE_STATE_DIR)
        phase_index = 0
        pending_after_start: list[PhaseAction] = []
        abort_reason: str | None = None
        cleanup_actions = [
            action for action in self.scenario.actions if action.kind == "cleanup-device-shell"
        ]
        actions_completed = False
        try:
            for action_index, action in enumerate(self.scenario.actions):
                if action.kind == "cleanup-device-shell":
                    continue
                if action.kind in {"view-after-start", "task-after-start"}:
                    pending_after_start.append(action)
                    continue
                if action.kind == "phase":
                    phase_index += 1
                    started = time.time()
                    args = ["-e", "class", action.value]
                    has_later_phase = any(
                        later.kind == "phase"
                        for later in self.scenario.actions[action_index + 1:]
                    )
                    if has_later_phase:
                        args += ["-e", "waitForActivitiesToComplete", "false"]
                    args += ["-e", "mjpdfPhaseStateDir", PHASE_STATE_DIR,
                             "-e", "mjpdfScenario", self.scenario.name,
                             "-e", "mjpdfPhaseIndex", str(phase_index)]
                    after_start: Callable[[], None] | None = None
                    if pending_after_start:
                        deferred = tuple(pending_after_start)
                        after_start_ran = False

                        def run_after_start() -> None:
                            nonlocal after_start_ran
                            if after_start_ran:
                                return
                            after_start_ran = True
                            for deferred_action in deferred:
                                self._run_action(deferred_action)

                        after_start = run_after_start

                    results = self.instrument(args, phase_index, after_start)
                    if self.config.dry_run and after_start is not None:
                        after_start()
                    pending_after_start.clear()
                    self.actions.append({
                        "kind": action.kind,
                        "value": action.value,
                        "startedAt": started,
                        "finishedAt": time.time(),
                    })
                    if not self.config.dry_run:
                        non_pass = next(
                            (result for result in results
                             if result.status is not TestStatus.PASS),
                            None,
                        )
                        if non_pass is not None or not results:
                            outcome = non_pass.status.value if non_pass is not None else "NO RESULT"
                            abort_reason = (
                                f"scenario {self.scenario.name} stopped after prerequisite "
                                f"phase {phase_index} reported {outcome}"
                            )
                            break
                else:
                    self._run_action(action)
            if pending_after_start and abort_reason is None:
                raise RuntimeError("post-start launch action has no following phase")
            actions_completed = True
        finally:
            cleanup_failed = False
            try:
                for cleanup_action in cleanup_actions:
                    self._run_action(cleanup_action)
            except Exception:
                cleanup_failed = True
                raise
            finally:
                if abort_reason is not None or not actions_completed or cleanup_failed:
                    self.run_command(
                        "abort-force-stop", "shell", "am", "force-stop", self.config.package,
                        check=False,
                    )
                self.run_command(
                    "state-remove-final", "shell", "rm", "-rf", PHASE_STATE_DIR,
                    check=False,
                )
                self.storage.delete_media_rows_under(PHASE_STATE_DIR)
        return False, None, self.actions, abort_reason
