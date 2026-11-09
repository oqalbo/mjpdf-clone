from __future__ import annotations

import sys
import threading
import time
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from typing import TextIO

from .model import RunState, StepState, TestResult
from .terminal import InlinePanel


class Renderer(ABC):
    @abstractmethod
    def start(self, state: RunState) -> None: ...

    @abstractmethod
    def update(self, state: RunState) -> None: ...

    @abstractmethod
    def notice(self, message: str, warning: bool = False) -> None: ...

    @abstractmethod
    def finish(self, result: TestResult) -> None: ...
    @abstractmethod
    def reconcile(self, results: tuple[TestResult, ...]) -> None: ...


    @abstractmethod
    def close(self) -> None: ...


class NonTTYRenderer(Renderer):
    def __init__(self, stream: TextIO = sys.stdout):
        self.stream = stream
        self._step_states: dict[str, StepState] = {}

    @staticmethod
    def _stamp() -> str:
        return datetime.now(timezone.utc).strftime("%H:%M:%S")

    def _line(self, text: str) -> None:
        print(f"[{self._stamp()}] {text}", file=self.stream, flush=True)

    def start(self, state: RunState) -> None:
        self._line(f"run {state.artifact_dir.name} started")

    def update(self, state: RunState) -> None:
        for step in state.steps:
            previous = self._step_states.get(step.name)
            if previous != step.state:
                self._step_states[step.name] = step.state
                if step.state not in (StepState.PENDING, StepState.SKIPPED):
                    self._line(f"{step.state.value.upper():8} {step.name}{(': ' + step.detail) if step.detail else ''}")

    def notice(self, message: str, warning: bool = False) -> None:
        self._line(("WARNING " if warning else "NOTICE ") + message)

    def finish(self, result: TestResult) -> None:
        return
    def reconcile(self, results: tuple[TestResult, ...]) -> None:
        return


    def close(self) -> None:
        return


SPINNER = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"


def _fit(text: str, width: int) -> str:
    if width <= 0:
        return ""
    return text if len(text) <= width else text[:max(0, width - 1)] + "…"


def _right_join(left: str, right: str, width: int) -> str:
    if not right:
        return _fit(left, width)
    if len(right) >= width:
        return _fit(right, width)
    left = _fit(left, max(0, width - len(right) - 1))
    return f"{left}{' ' * max(1, width - len(left) - len(right))}{right}"


def _step_text(state: RunState, step, now: float, spinner: str) -> tuple[str, str]:
    marks = {
        StepState.PENDING: "–",
        StepState.RUNNING: spinner,
        StepState.COMPLETE: "✓",
        StepState.FAILED: "✗",
        StepState.SKIPPED: "–",
    }
    left = f"{marks[step.state]} {step.name}"
    if step.name == "Run tests" and step.state is StepState.RUNNING:
        complete = len(state.tests)
        total = state.total_tests
        passed = sum(result.status.value == "PASS" for result in state.tests)
        failed = sum(result.status.value in {"FAIL", "ERROR"} for result in state.tests)
        progress = f"{complete}/{total}" if total is not None else str(complete)
        left += f"  {progress} · {passed} passed · {failed} failed"
    elif step.detail:
        left += f"  {step.detail}"
    duration = ""
    if step.started_at is not None:
        end = step.finished_at if step.finished_at is not None else now
        duration = f"{max(0.0, end - step.started_at):.1f}s"
    return left, duration


def _step_line(state: RunState, step, width: int, now: float, spinner: str) -> str:
    return _right_join(*_step_text(state, step, now, spinner), width)


def _test_text(result: TestResult) -> tuple[str, str]:
    marks = {
        "PASS": "✓",
        "FAIL": "✗",
        "ERROR": "✗",
        "SKIP": "–",
    }
    name = f"{result.key.class_name.rsplit('.', 1)[-1]}.{result.key.method}"
    return f"{marks[result.status.value]} {name}", f"{result.duration_seconds:.1f}s"




def build_frame(state: RunState, width: int, height: int, elapsed: float,
                now: float | None = None) -> list[str]:
    """Build a bounded frame whose dimensions follow its visible content."""
    max_width = max(20, width)
    max_height = max(6, height)
    now = time.time() if now is None else now
    spinner = SPINNER[int(elapsed * 10) % len(SPINNER)]
    clock = f"{spinner} {int(elapsed) // 60:02d}:{int(elapsed) % 60:02d}"

    completed = state.tests[-(2 if state.current_test else 3):]
    test_rows: list[tuple[str, str]] = [_test_text(result) for result in completed]
    if state.current_test is not None:
        name = (f"{state.current_test.class_name.rsplit('.', 1)[-1]}."
                f"{state.current_test.method}")
        test_elapsed = max(0, int(now - (state.current_test_started_at or now)))
        test_rows.append((f"{spinner} {name}", f"test {test_elapsed // 60:02d}:{test_elapsed % 60:02d}"))

    footer = f"warnings {len(state.warnings)} · log {state.artifact_dir.name}/runner.log"
    natural_widths = [len(f" MJ PDF · {state.title} ") + len(f" {clock} ") + 2,
                      len(footer) + 4]
    for step in state.steps:
        left, right = _step_text(state, step, now, spinner)
        natural_widths.append(len(left) + len(right) + 5)
    for left, right in test_rows:
        natural_widths.append(len(left) + len(right) + 11)
    frame_width = min(max_width, max(48, *natural_widths))
    inner = frame_width - 4

    title = f" MJ PDF · {state.title} "
    right = f" {clock} "
    dash_count = max(1, frame_width - len(title) - len(right) - 2)
    top = "╭" + _fit(title, frame_width - len(right) - 2) + "─" * dash_count + right + "╮"
    top = _fit(top, frame_width).ljust(frame_width, "─")
    if top[-1] != "╮":
        top = top[:-1] + "╮"

    def boxed(text: str = "") -> str:
        content = _fit(text, inner)
        return f"│ {content.ljust(inner)} │"

    available = max_height - 3
    nested_count = min(3, len(test_rows))
    step_capacity = max(1, available - nested_count)
    active_index = next(
        (index for index, step in enumerate(state.steps) if step.state is StepState.RUNNING),
        max(0, next((index for index in range(len(state.steps) - 1, -1, -1)
                    if state.steps[index].state is not StepState.PENDING), 0)),
    )
    run_index = next((index for index, step in enumerate(state.steps)
                      if step.name == "Run tests"), -1)
    focus_index = run_index if test_rows and run_index >= 0 else active_index
    start = max(0, min(focus_index - step_capacity // 2,
                       max(0, len(state.steps) - step_capacity)))
    visible = state.steps[start:start + step_capacity]

    lines = [top]
    for step in visible:
        lines.append(boxed(_step_line(state, step, inner, now, spinner)))
        if step.name == "Run tests":
            for index, (left, right) in enumerate(test_rows):
                branch = "└─" if index == len(test_rows) - 1 else "├─"
                lines.append(boxed(_right_join(f"  {branch} {left}", right, inner)))
    lines.append(boxed(footer))
    lines.append("╰" + "─" * (frame_width - 2) + "╯")
    return lines[:max_height]


class TTYRenderer(Renderer):
    """Refresh a small inline block without entering curses' full-screen mode."""

    def __init__(self, stream: TextIO = sys.stdout, panel: InlinePanel | None = None):
        self.stream = stream
        self.panel = panel or InlinePanel(stream)
        self._state: RunState | None = None
        self._started = 0.0
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._lock = threading.Lock()
        self._closed = False

    def start(self, state: RunState) -> None:
        self._state = state
        self._started = time.monotonic()
        self._closed = False
        self._stop.clear()
        self._draw()
        self._thread = threading.Thread(target=self._refresh, name="mjpdf-tui", daemon=True)
        self._thread.start()

    def update(self, state: RunState) -> None:
        with self._lock:
            self._state = state
        self._draw()

    def notice(self, message: str, warning: bool = False) -> None:
        return

    def finish(self, result: TestResult) -> None:
        return
    def reconcile(self, results: tuple[TestResult, ...]) -> None:
        return


    def _refresh(self) -> None:
        while not self._stop.wait(0.1):
            self._draw()

    def _frame(self) -> list[str]:
        with self._lock:
            state = self._state
        if state is None:
            return []
        terminal = self.panel.size
        return build_frame(
            state,
            width=terminal.columns,
            height=max(6, terminal.lines - 1),
            elapsed=time.monotonic() - self._started,
        )

    def _draw(self) -> None:
        frame = self._frame()
        if frame:
            self.panel.draw(frame)

    def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2.0)
            self._thread = None
        self.panel.close(erase=True)

    def print_final_panel(self) -> None:
        frame = self._frame()
        if frame:
            self.panel.print_static(frame)
        self.panel.close()


def make_renderer(force_non_tty: bool = False) -> Renderer:
    return TTYRenderer() if sys.stdout.isatty() and not force_non_tty else NonTTYRenderer()
