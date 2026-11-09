from __future__ import annotations

import difflib
import re
import shlex
from pathlib import Path
from typing import Callable

from .fixtures import GENERATED_PATHS, PICKER_ALIASES, SDCARD, Fixtures
from .model import CommandSpec, RunnerConfig
from .process import CommandRunner


class Storage:
    def __init__(self, config: RunnerConfig, commands: CommandRunner, root: Path,
                 fixtures: Fixtures, warn: Callable[[str], None], notice: Callable[[str], None]):
        self.config = config
        self.commands = commands
        self.root = root
        self.directory = root / "storage"
        self.fixtures = fixtures
        self.warn = warn
        self.notice = notice
        self.leftovers: list[str] = []
        self._ignore = self._read_ignore_patterns()

    def _read_ignore_patterns(self) -> list[re.Pattern[str]]:
        source = self.config.runner_dir / "storage-ignore.txt"
        patterns: list[re.Pattern[str]] = []
        for raw in source.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            try:
                patterns.append(re.compile(line))
            except re.error as exc:
                raise RuntimeError(f"invalid storage ignore pattern {line!r}: {exc}") from exc
        (self.directory / ".ignore-patterns").write_text(
            "\n".join(pattern.pattern for pattern in patterns) + "\n", encoding="utf-8")
        return patterns

    def _adb(self, *args: str) -> tuple[str, ...]:
        return (self.config.adb, "-s", self.config.serial, *args)

    def run(self, name: str, *args: str, mutate: bool = True, check: bool = True) -> str:
        return self.commands.run(CommandSpec(self._adb(*args), f"storage-{name}", mutate=mutate),
                                 check=check).output.replace("\r", "").strip()

    def content(
        self, name: str, *args: str, mutate: bool = True, check: bool = True
    ) -> str:
        command = shlex.join(("content", *args))
        return self.run(name, "shell", command, mutate=mutate, check=check)

    def _filter(self, lines: list[str]) -> list[str]:
        return sorted(line for line in lines if line and not any(pattern.search(line) for pattern in self._ignore))

    def snapshot(self, label: str) -> None:
        if self.config.dry_run:
            self.commands.record(f"dry-run: snapshot shared storage and MediaStore as {label}")
            return
        files = self.run(f"snapshot-{label}", "shell",
                         f"find {SDCARD} '(' -path {SDCARD}/Android -o -path {SDCARD}/Documents/perf-library ')' "
                         f"-prune -o -type f -exec stat -c '%s %n' {{}} + 2>/dev/null",
                         mutate=False, check=False).splitlines()
        (self.directory / f"{label}.txt").write_text(
            "\n".join(self._filter(files)) + "\n", encoding="utf-8")
        if label == "pre-seed":
            directories = self.run(f"directories-{label}", "shell",
                                   f"find {SDCARD} '(' -path {SDCARD}/Android -o -path {SDCARD}/Documents/perf-library ')' "
                                   "-prune -o -type d -print 2>/dev/null",
                                   mutate=False, check=False).splitlines()
            (self.directory / f"{label}-directories.txt").write_text(
                "\n".join(sorted(directories)) + "\n", encoding="utf-8")
        rows_raw = self.content(f"media-{label}", "query", "--uri",
                                "content://media/external/file", "--projection", "_data:_size",
                                "--where", f"LIKE('{SDCARD}/%',_data)",
                                mutate=False, check=False)
        rows: list[str] = []
        for line in rows_raw.splitlines():
            match = re.match(r"Row: \d+ (.*)", line)
            if match and f"{SDCARD}/Documents/perf-library" not in line:
                rows.append(match.group(1))
        (self.directory / f"{label}-mediastore.txt").write_text(
            "\n".join(self._filter(rows)) + "\n", encoding="utf-8")

    def delete_media_rows_under(self, root: str) -> None:
        for scope, where in (("children", f"LIKE('{root}/%',_data)"),
                             ("root", f"_data='{root}'")):
            name = root.rsplit("/", 1)[-1] or "storage"
            self.content(f"delete-media-{name}-{scope}", "delete", "--uri",
                         "content://media/external/file", "--where", where, check=False)

    def clear_stale_evidence(self) -> None:
        evidence = f"{SDCARD}/Documents/mjpdf-test-artifacts"
        # `test` has no output. Query count as the authoritative existence probe.
        count = self.run("stale-count", "shell", f"if [ -d {evidence} ]; then ls -1 {evidence} | wc -l; fi",
                         mutate=False, check=False).strip()
        if count:
            self.warn(f"discarding {count} stale evidence entries left by an earlier run")
            self.run("stale-remove", "shell", "rm", "-rf", evidence, check=False)
            self.delete_media_rows_under(evidence)

    def sweep(self) -> None:
        roots = list(self.fixtures.roots())
        for root in roots:
            self.run(f"remove-{root.rsplit('/', 1)[-1]}", "shell", "rm", "-rf", root, check=False)
        for path in GENERATED_PATHS:
            self.run(f"remove-{path.rsplit('/', 1)[-1]}", "shell", "rm", "-f", path, check=False)
        names = [
            *(f"{SDCARD}/Download/{path.name}" for path in self.fixtures._root_files()),
            *(f"{SDCARD}/Download/{alias}" for alias in PICKER_ALIASES.values()),
        ]
        for start in range(0, len(names), 40):
            self.run(f"remove-root-{start // 40}", "shell",
                     shlex.join(("rm", "-f", *names[start:start + 40])), check=False)
        picker_alias_pattern = f"{SDCARD}/Download/00-picker-*"
        self.run("remove-picker-aliases", "shell", "rm", "-f", picker_alias_pattern, check=False)
        per_test = f"{SDCARD}/Documents/mjpdf-test"
        self.run("remove-per-test", "shell", "rm", "-rf", per_test, check=False)
        roots.append(per_test)
        if self.config.sweep_perf_library:
            perf = f"{SDCARD}/Documents/perf-library"
            self.run("remove-perf", "shell", "rm", "-rf", perf, check=False)
            roots.append(perf)

        media_predicates = [
            predicate
            for root in roots
            for predicate in (f"LIKE('{root}/%',_data)", f"_data='{root}'")
        ]
        media_predicates.extend("_data='" + path.replace("'", "''") + "'" for path in (*GENERATED_PATHS, *names))
        media_predicates.append(f"LIKE('{SDCARD}/Download/00-picker-%',_data)")
        self.content(
            "delete-suite-storage", "delete", "--uri", "content://media/external/file",
            "--where", " OR ".join(f"({predicate})" for predicate in media_predicates),
            check=False,
        )

        baseline = self.directory / "pre-seed-directories.txt"
        if baseline.is_file():
            existing = set(self._read_lines(baseline))
            for index, relative in enumerate(("Documents/MJ PDF", "Pictures/MJ PDF", "Documents", "Pictures", "Download")):
                path = f"{SDCARD}/{relative}"
                if path in existing:
                    continue
                quoted = shlex.quote(path)
                result = self.run(f"remove-empty-output-{index}", "shell",
                                  f"rmdir {quoted} 2>/dev/null; if [ ! -e {quoted} ]; then echo absent; fi",
                                  check=False)
                if result == "absent":
                    self.content(f"delete-empty-output-{index}", "delete", "--uri",
                                 "content://media/external/file", "--where", f"_data='{path}'",
                                 check=False)

    def revoke_all_files(self, api: int | None) -> None:
        if api is not None and api < 30:
            return
        self.run("revoke-appop", "shell", "appops", "set", "--uid", self.config.package,
                 "MANAGE_EXTERNAL_STORAGE", "deny", check=False)

    @staticmethod
    def _read_lines(path: Path) -> list[str]:
        return [line for line in path.read_text(encoding="utf-8").splitlines() if line] if path.is_file() else []

    def _diff(self, before: str, after: str, name: str, media: bool = False) -> list[str]:
        suffix = "-mediastore" if media else ""
        old = self._read_lines(self.directory / f"{before}{suffix}.txt")
        new = self._read_lines(self.directory / f"{after}{suffix}.txt")
        diff = list(difflib.unified_diff(old, new, fromfile=before, tofile=after, lineterm=""))
        (self.directory / f"{name}{suffix}.diff").write_text("\n".join(diff) + ("\n" if diff else ""),
                                                              encoding="utf-8")
        return [line[1:] for line in diff if line.startswith("+") and not line.startswith("+++")]

    @staticmethod
    def _is_materialized_parent_row(line: str, pre_seed_rows: list[str],
                                    pre_seed_directories: set[str] | None = None) -> bool:
        match = re.fullmatch(r"_data=(.+), _size=(NULL|\d+)", line)
        if match is None:
            return False
        if match.group(1) in (pre_seed_directories or set()):
            return True
        if match.group(2) != "NULL":
            return False
        prefix = f"_data={match.group(1).rstrip('/')}/"
        return any(row.startswith(prefix) for row in pre_seed_rows)

    def check_leftovers(self) -> list[str]:
        if self.config.dry_run:
            return []
        self._diff("post-seed", "post-run", "suite-produced")
        self._diff("post-seed", "post-run", "suite-produced", media=True)
        files = self._diff("pre-seed", "post-sweep", "leftovers")
        rows = self._diff("pre-seed", "post-sweep", "leftovers", media=True)
        pre_seed_rows = self._read_lines(self.directory / "pre-seed-mediastore.txt")
        pre_seed_directories = set(self._read_lines(self.directory / "pre-seed-directories.txt"))
        rows = [row for row in rows
                if not self._is_materialized_parent_row(row, pre_seed_rows, pre_seed_directories)]
        self.leftovers = [f"FILE {line}" for line in files] + [f"MEDIA {line}" for line in rows]
        report = self.root / "leftovers.txt"
        if self.leftovers:
            sections: list[str] = []
            if files:
                sections.append("Files left on shared storage:\n" + "\n".join(files))
            if rows:
                sections.append("MediaStore rows left behind:\n" + "\n".join(rows))
            report.write_text("\n\n".join(sections) + "\n", encoding="utf-8")
        elif report.exists():
            report.unlink()
        return self.leftovers
