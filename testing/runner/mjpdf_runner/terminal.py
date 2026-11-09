from __future__ import annotations

import os
import select
import shutil
import sys
import termios
import threading
import tty
from typing import TextIO


class InlinePanel:
    """Own a bounded block in the normal terminal buffer; never use an alternate screen."""

    def __init__(self, stream: TextIO = sys.stdout):
        self.stream = stream
        self.rows = 0
        self._cursor_hidden = False
        self._lock = threading.RLock()

    @property
    def size(self) -> os.terminal_size:
        return shutil.get_terminal_size(fallback=(100, 30))

    def draw(self, lines: list[str]) -> None:
        with self._lock:
            if not self._cursor_hidden:
                self.stream.write("\x1b[?25l")
                self._cursor_hidden = True
            old_rows = self.rows
            if old_rows:
                self.stream.write(f"\x1b[{old_rows}F")
            count = max(old_rows, len(lines))
            for index in range(count):
                self.stream.write("\x1b[2K")
                if index < len(lines):
                    self.stream.write(lines[index])
                self.stream.write("\r\n")
            if old_rows > len(lines):
                self.stream.write(f"\x1b[{old_rows - len(lines)}F")
            self.stream.flush()
            self.rows = len(lines)

    def erase(self) -> None:
        with self._lock:
            if self.rows:
                self.stream.write(f"\x1b[{self.rows}F")
                for _ in range(self.rows):
                    self.stream.write("\x1b[2K\r\n")
                self.stream.write(f"\x1b[{self.rows}F")
                self.stream.flush()
                self.rows = 0

    def print_static(self, lines: list[str]) -> None:
        with self._lock:
            self.erase()
            self.stream.write("\r\n".join(lines) + "\r\n")
            self.stream.flush()

    def close(self, erase: bool = False) -> None:
        with self._lock:
            if erase:
                self.erase()
            if self._cursor_hidden:
                self.stream.write("\x1b[?25h")
                self.stream.flush()
                self._cursor_hidden = False


class RawKeys:
    def __init__(self, stream: TextIO = sys.stdin):
        self.stream = stream
        self.fd = stream.fileno()
        self._saved: list[int | list[bytes]] | None = None

    def __enter__(self) -> "RawKeys":
        self._saved = termios.tcgetattr(self.fd)
        tty.setraw(self.fd)
        return self

    def __exit__(self, _kind, _value, _traceback) -> None:
        if self._saved is not None:
            termios.tcsetattr(self.fd, termios.TCSADRAIN, self._saved)
            self._saved = None

    def read(self) -> str:
        first = os.read(self.fd, 1)
        if first in {b"\x03", b"\x04"}:
            return "cancel"
        if first in {b"\r", b"\n"}:
            return "enter"
        if first == b" ":
            return "space"
        if first in {b"\x7f", b"\x08"}:
            return "backspace"
        if first == b"\x1b":
            ready, _, _ = select.select([self.fd], [], [], 0.05)
            if not ready:
                return "escape"
            second = os.read(self.fd, 1)
            if second != b"[":
                return "escape"
            ready, _, _ = select.select([self.fd], [], [], 0.05)
            if not ready:
                return "escape"
            third = os.read(self.fd, 1)
            return {b"A": "up", b"B": "down", b"C": "right", b"D": "left",
                    b"Z": "backtab"}.get(third, "escape")
        try:
            return first.decode("utf-8")
        except UnicodeDecodeError:
            return ""
