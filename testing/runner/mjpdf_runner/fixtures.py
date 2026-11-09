from __future__ import annotations

import filecmp
import hashlib
import json
import time
import importlib.util
import os
import re
import shlex
import tarfile
import tempfile
from pathlib import Path
from typing import Callable

from .model import CommandSpec, RunnerConfig
from .process import CommandRunner

SDCARD = "/storage/emulated/0"
FIXTURE_MTIME = os.environ.get("FIXTURE_MTIME", "202401010000")
REQUIRED_FREE_MB = int(os.environ.get("REQUIRED_FREE_MB", "900"))
PERF_LIBRARY_FILES = 100000
PERF_LIBRARY_TOPS = ("fiction", "history", "reference", "science")
PICKER_ALIASES = {
    "large.pdf": "00-picker-large.pdf",
    "normal.pdf": "00-picker-normal.pdf",
}
GENERATED_PATHS = (
    f"{SDCARD}/Download/huge.pdf",
    f"{SDCARD}/Download/huge-500mb.pdf",
    f"{SDCARD}/Documents/backup-fixtures/oversized.json",
)


GENERATED_FIXTURES = {
    "HUGE_PDF": ("huge.pdf", f"{SDCARD}/Download/huge.pdf"),
    "LARGE_PDF": ("huge-500mb.pdf", f"{SDCARD}/Download/huge-500mb.pdf"),
    "OVERSIZED_BACKUP": ("oversized.json", f"{SDCARD}/Documents/backup-fixtures/oversized.json"),
}


def _digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def prepare_generated(config: RunnerConfig, destination: Path, requirements: frozenset[str],
                      cancelled: Callable[[], bool] = lambda: False) -> Path:
    unknown = requirements - GENERATED_FIXTURES.keys()
    if unknown:
        raise FixtureError("unknown fixture requirements: " + ", ".join(sorted(unknown)))
    wanted = requirements & GENERATED_FIXTURES.keys()
    if config.dry_run:
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".preparing-fixtures-", dir=destination.parent) as temporary:
        stage = Path(temporary)
        records = {}
        for resource in sorted(wanted):
            if cancelled():
                raise FixtureError("fixture preparation cancelled")
            started = time.monotonic()
            target = stage / GENERATED_FIXTURES[resource][0]
            inputs = {"generatorVersion": 1}
            if resource == "HUGE_PDF":
                base = config.repo_root / "testing/fixtures/out/perf-small.pdf"
                inputs["baseSha256"] = _digest(base)
                inputs["paddingMiB"] = 256
                Fixtures._stage_huge(base, target)
                if _digest(base) != inputs["baseSha256"]:
                    raise FixtureError("huge PDF input changed during preparation")
            elif resource == "LARGE_PDF":
                inputs.update(pages=200, streamMiBPerPage=2)
                Fixtures._stage_500(target)
            else:
                inputs["paddingMiB"] = 33
                prefix = (b'{"schemaVersion":1,"exportedAt":"2024-01-01T00:00:00Z",'
                          b'"settings":[],"pdfRecords":[],"userBookmarks":[],"pad":"')
                with target.open("wb") as output:
                    output.write(prefix)
                    for _ in range(33):
                        output.write(b"A" * (1024 * 1024))
                    output.write(b'"}')
            target.chmod(0o444)
            records[resource] = {"name": target.name, "sha256": _digest(target),
                                 "bytes": target.stat().st_size, "inputs": inputs,
                                 "preparationSeconds": time.monotonic() - started}
        if cancelled():
            raise FixtureError("fixture preparation cancelled")
        manifest = stage / "manifest.json"
        manifest.write_text(json.dumps({"resources": records}, indent=2, sort_keys=True) + "\n")
        manifest.chmod(0o444)
        os.replace(stage, destination)
    return destination


def cleanup_generated(directory: Path | None) -> None:
    if directory is not None:
        for name, _target in GENERATED_FIXTURES.values():
            (directory / name).unlink(missing_ok=True)


class FixtureError(RuntimeError):
    pass


class Fixtures:
    def __init__(self, config: RunnerConfig, commands: CommandRunner, root: Path,
                 warn: Callable[[str], None], notice: Callable[[str], None],
                 cancelled: Callable[[], bool] = lambda: False):
        self.config = config
        self.commands = commands
        self.root = root
        self.warn = warn
        self.notice = notice
        self.cancelled = cancelled
        self.fixture_dir = config.repo_root / "testing" / "fixtures"
        self.out = self.fixture_dir / "out"

    def _adb(self, *args: str) -> tuple[str, ...]:
        return (self.config.adb, "-s", self.config.serial, *args)

    def run(self, name: str, *args: str, mutate: bool = True, check: bool = True) -> str:
        return self.commands.run(CommandSpec(self._adb(*args), f"fixture-{name}", mutate=mutate),
                                 check=check).output.strip().replace("\r", "")

    def content(
        self, name: str, *args: str, mutate: bool = True, check: bool = True
    ) -> str:
        command = shlex.join(("content", *args))
        return self.run(name, "shell", command, mutate=mutate, check=check)

    def roots(self) -> tuple[str, ...]:
        return (f"{SDCARD}/scan", f"{SDCARD}/TestBooks",
                f"{SDCARD}/Documents/backup-fixtures",
                f"{SDCARD}/Download/dotted.folder", f"{SDCARD}/Download/fifty-distinct")

    def require_tree(self) -> None:
        if not self.out.is_dir() or not any(self.out.glob("*.pdf")):
            raise FixtureError(f"no fixture PDFs at {self.out}. Run: python3 {self.fixture_dir / 'generate.py'}")

    def check_registry(self) -> None:
        registry_path = self.fixture_dir / "lib" / "registry.py"
        spec = importlib.util.spec_from_file_location("mjpdf_fixture_registry", registry_path)
        if spec is None or spec.loader is None:
            raise FixtureError(f"cannot import {registry_path}")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        expected = {
            "": "/sdcard/Download", "assets": "/sdcard/Download", "scan": "/sdcard/scan",
            "backups": "/sdcard/Documents/backup-fixtures", "library": "/sdcard/TestBooks",
            "perf": "/sdcard/Documents/perf-library",
        }
        actual = {key: value.rstrip("/") for key, value in module.DEVICE_TARGETS.items()}
        if actual != expected:
            raise FixtureError(f"registry DEVICE_TARGETS differs from runner placement: {actual!r}")

    def check_asset_drift(self) -> None:
        assets = self.config.repo_root / "app" / "src" / "androidTest" / "assets"
        if not assets.is_dir():
            return
        self.require_tree()
        errors: list[str] = []
        for asset in sorted(path for path in assets.rglob("*") if path.is_file()):
            relative = asset.relative_to(assets)
            direct = self.out / relative
            if direct.is_file():
                fixture = direct
            else:
                candidates = sorted(path for path in self.out.rglob(asset.name) if path.is_file())
                if not candidates:
                    continue
                if len(candidates) > 1 and any(not filecmp.cmp(candidates[0], item, shallow=False)
                                                for item in candidates[1:]):
                    errors.append(f"{relative} ambiguously matches differing fixtures: {candidates}")
                    continue
                fixture = candidates[0]
            if not filecmp.cmp(asset, fixture, shallow=False):
                errors.append(f"asset {asset} differs from fixture {fixture}")
        if errors:
            raise FixtureError("bundled test assets have drifted:\n" + "\n".join(errors))

    def _root_files(self) -> list[Path]:
        return sorted(path for path in self.out.iterdir()
                      if path.is_file() and path.name not in ("MANIFEST.md", "MANIFEST.json", "generate-on-device.sh"))

    def _push_tree(self, source: Path, target: str) -> None:
        if not source.is_dir():
            raise FixtureError(f"missing fixture tree {source}")
        self.run(f"remove-{source.name}", "shell", "rm", "-rf", target)
        self.run(f"mkdir-{source.name}", "shell", "mkdir", "-p", target.rsplit("/", 1)[0])
        self.run(f"push-{source.name}", "push", str(source), target)

    def _push_root(self) -> None:
        files = self._root_files()
        if not files:
            raise FixtureError(f"{self.out} has no top-level fixture files to push")
        self.run("mkdir-download", "shell", "mkdir", "-p", f"{SDCARD}/Download")
        self.run("push-root", "push", *(str(path) for path in files), f"{SDCARD}/Download/")
        aliases = "; ".join(
            f"cp {SDCARD}/Download/{source} {SDCARD}/Download/{alias}"
            for source, alias in PICKER_ALIASES.items()
        )
        self.run("picker-aliases", "shell", f"set -e; {aliases}")
        for name in ("dotted.folder", "fifty-distinct"):
            source = self.out / name
            if source.is_dir():
                self._push_tree(source, f"{SDCARD}/Download/{name}")

    def _free_space_guard(self) -> None:
        output = self.run("free-space", "shell",
                          f"df -k {SDCARD} | tail -1 | awk '{{print $4}}'",
                          mutate=False, check=False)
        if self.config.dry_run:
            return
        try:
            free_mb = int(output.splitlines()[-1]) // 1024
        except (ValueError, IndexError):
            self.warn(f"could not read free space on {SDCARD}; space guard did not run")
            return
        if free_mb < REQUIRED_FREE_MB:
            raise FixtureError(f"only {free_mb} MB free; generated fixtures need about {REQUIRED_FREE_MB} MB")

    @staticmethod
    def _stage_huge(base: Path, target: Path, pad_mib: int = 256) -> int:
        data = base.read_bytes()
        size_match = re.search(rb"/Size (\d+)", data)
        root_match = re.search(rb"/Root (\d+) 0 R", data)
        info_match = re.search(rb"/Info (\d+) 0 R", data)
        id_match = re.search(rb"/ID\s*\[\s*<([0-9A-Fa-f]+)>", data)
        if not all((size_match, root_match, info_match, id_match)):
            raise FixtureError(f"{base} lacks the PDF trailer fields needed for huge.pdf")
        prev = int(data.rsplit(b"startxref", 1)[1].split()[0])
        pad = pad_mib * 1024 * 1024
        xref_at = len(data) + pad + 2
        tail = (b"xref\n0 1\n0000000000 65535 f \ntrailer\n<< /Size " + size_match.group(1)
                + b" /Root " + root_match.group(1) + b" 0 R /Info " + info_match.group(1)
                + b" 0 R /Prev " + str(prev).encode() + b" /ID [<" + id_match.group(1)
                + b"> <" + id_match.group(1) + b">] >>\nstartxref\n"
                + str(xref_at).encode() + b"\n%%EOF\n")
        block = b"A" * (1024 * 1024)
        with target.open("wb") as output:
            output.write(data)
            output.write(b"%")
            for _ in range(pad_mib):
                output.write(block)
            output.write(b"\n")
            output.write(tail)
        return target.stat().st_size

    @staticmethod
    def _stage_500(target: Path, pages: int = 200, mib: int = 2) -> None:
        block = bytes((y * 251 + x * 7) % 256
                      for y in range(1024) for x in range(1024))
        offsets: dict[int, int] = {}
        cursor = 0
        with target.open("wb") as output:
            def emit(data: bytes) -> None:
                nonlocal cursor
                output.write(data)
                cursor += len(data)

            emit(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
            page_objs = lambda i: (3 + i * 3, 4 + i * 3, 5 + i * 3)
            offsets[1] = cursor
            emit(b"1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            kids = " ".join(f"{page_objs(i)[2]} 0 R" for i in range(pages))
            offsets[2] = cursor
            emit(f"2 0 obj\n<< /Type /Pages /Kids [{kids}] /Count {pages} >>\nendobj\n".encode())
            for index in range(pages):
                content_n, image_n, page_n = page_objs(index)
                content = b"q 612 0 0 792 0 0 cm /Im0 Do Q"
                offsets[content_n] = cursor
                emit(f"{content_n} 0 obj\n<< /Length {len(content)} >>\nstream\n".encode()
                     + content + b"\nendstream\nendobj\n")
                offsets[image_n] = cursor
                emit(f"{image_n} 0 obj\n<< /Type /XObject /Subtype /Image "
                     f"/Width 1024 /Height {1024 * mib} /ColorSpace /DeviceGray "
                     f"/BitsPerComponent 8 /Length {len(block) * mib} >>\nstream\n".encode())
                for _ in range(mib):
                    emit(block)
                ending = b"\nendstream\nendobj\n"
                offsets[page_n] = cursor + len(ending)
                emit(ending + f"{page_n} 0 obj\n<< /Type /Page /Parent 2 0 R "
                     f"/MediaBox [0 0 612 792] /Resources << /XObject << /Im0 {image_n} 0 R >> >> "
                     f"/Contents {content_n} 0 R >>\nendobj\n".encode())
            count = 3 + pages * 3
            xref = ["xref", f"0 {count}", "0000000000 65535 f "]
            xref.extend(f"{offsets[number]:010d} 00000 n " for number in range(1, count))
            emit(("\n".join(xref) + f"\ntrailer\n<< /Size {count} /Root 1 0 R >>\n"
                  f"startxref\n{cursor}\n%%EOF\n").encode())

    def _push_generated(self) -> None:
        requirements = getattr(self.config, "required_fixtures", None)
        wanted = frozenset(GENERATED_FIXTURES) if requirements is None else requirements & GENERATED_FIXTURES.keys()
        if self.config.skip_generated:
            self.notice("skipping generated huge fixtures as requested")
            if requirements is not None and wanted:
                self.warn("required fixtures explicitly skipped: " + ", ".join(sorted(wanted)))
            return
        unrequested = [target for resource, (_name, target) in GENERATED_FIXTURES.items() if resource not in wanted]
        if unrequested:
            self.run("remove-unrequested-generated", "shell", shlex.join(("rm", "-f", *unrequested)))
            self.content("remove-unrequested-generated-media", "delete", "--uri", "content://media/external/file",
                         "--where", " OR ".join(f"_data='{target}'" for target in unrequested), check=False)
        if not wanted:
            self.commands.record("no generated fixtures required by this batch")
            return
        self._free_space_guard()
        if self.config.dry_run:
            self.commands.record("dry-run: stage and push " + ", ".join(GENERATED_FIXTURES[r][0] for r in sorted(wanted)))
            return
        stage = getattr(self.config, "prepared_fixtures", None)
        if stage is None:
            stage = prepare_generated(self.config, self.root / "prepared-fixtures", frozenset(wanted),
                                      self.cancelled)
        manifest = json.loads((stage / "manifest.json").read_text())
        for resource in sorted(wanted):
            name, target = GENERATED_FIXTURES[resource]
            source = stage / name
            entry = manifest["resources"].get(resource)
            if (entry is None or entry.get("name") != name or not source.is_file()
                    or source.stat().st_size != entry["bytes"] or _digest(source) != entry["sha256"]):
                raise FixtureError(f"prepared fixture is missing or changed: {resource}")
            self.run(f"push-generated-{resource}", "push", str(source), target)

    def _push_perf_library(self) -> None:
        target = f"{SDCARD}/Documents/perf-library"
        if not self.config.with_perf_library:
            self.run("perf-remove-unrequested", "shell", "rm", "-rf", target, check=False)
            return
        source = self.out / "perf-library-100k"
        if not source.is_dir():
            raise FixtureError(f"missing {source}; run generate.py first")
        tops = sorted(source.iterdir())
        if (tuple(top.name for top in tops) != PERF_LIBRARY_TOPS
                or any(not top.is_dir() for top in tops)):
            raise FixtureError(
                f"{source} must contain exactly the directories {', '.join(PERF_LIBRARY_TOPS)}")
        target = f"{SDCARD}/Documents/perf-library"
        have = self.run("perf-count-before", "shell",
                        f"find {target} -name '*.pdf' 2>/dev/null | wc -l",
                        mutate=False, check=False).strip()
        if have == str(PERF_LIBRARY_FILES):
            return
        self.run("perf-clear", "shell", "rm", "-rf", target)
        if self.config.dry_run:
            self.commands.record(f"dry-run: stage {len(tops)} perf-library tar archives")
            archives = [f"/tmp/mjpdf-perflib/{top.name}.tar" for top in tops]
            for top, archive in zip(tops, archives):
                self.run(f"perf-push-{top.name}", "push", archive,
                         f"/data/local/tmp/perflib-{top.name}.tar")
            extracts = " ".join(
                f"tar -xf /data/local/tmp/perflib-{top.name}.tar -C {target} &"
                for top in tops)
            self.run("perf-extract", "shell", f"mkdir -p {target}; ( {extracts} wait )")
            remote = " ".join(
                f"/data/local/tmp/perflib-{top.name}.tar" for top in tops)
            self.run("perf-stage-clean", "shell", f"rm -f {remote}")
            self.run("perf-count-after", "shell",
                     f"find {target} -name '*.pdf' | wc -l", mutate=False)
            return
        with tempfile.TemporaryDirectory(prefix="mjpdf-perflib-") as temporary:
            stage = Path(temporary)
            for top in tops:
                archive = stage / f"{top.name}.tar"
                with tarfile.open(archive, "w") as tar:
                    tar.add(top, arcname=top.name)
                self.run(f"perf-push-{top.name}", "push", str(archive),
                         f"/data/local/tmp/perflib-{top.name}.tar")
            extracts = " ".join(
                f"tar -xf /data/local/tmp/perflib-{top.name}.tar -C {target} &"
                for top in tops)
            self.run("perf-extract", "shell", f"mkdir -p {target}; ( {extracts} wait )")
            remote = " ".join(
                f"/data/local/tmp/perflib-{top.name}.tar" for top in tops)
            self.run("perf-stage-clean", "shell", f"rm -f {remote}")
        got = self.run("perf-count-after", "shell",
                       f"find {target} -name '*.pdf' | wc -l", mutate=False).strip()
        if got != str(PERF_LIBRARY_FILES):
            raise FixtureError(f"perf-library push incomplete: {got or '0'} of {PERF_LIBRARY_FILES} files")

    def _clear_target_data(self) -> None:
        self.run("recents-stop", "shell", "am", "force-stop", self.config.package, check=False)
        self.run("recents-clear", "shell", "pm", "clear", self.config.package)
        api_text = self.run("storage-api", "shell", "getprop", "ro.build.version.sdk",
                            mutate=False, check=False)
        if self.config.dry_run and not api_text:
            self.commands.record("dry-run: grant legacy storage permissions on API 23-29")
            return
        try:
            api = int(api_text)
        except ValueError as exc:
            raise FixtureError(f"could not determine device API after clearing {self.config.package}") from exc
        storage_access = self._instrumentation_argument("storageAccess")
        if storage_access == "denied":
            if api >= 30:
                self.run("deny-all-files", "shell", "appops", "set", "--uid", self.config.package,
                         "MANAGE_EXTERNAL_STORAGE", "deny")
            return
        if api >= 30:
            return
        for name, permission in (
            ("read", "android.permission.READ_EXTERNAL_STORAGE"),
            ("write", "android.permission.WRITE_EXTERNAL_STORAGE"),
        ):
            self.run(f"grant-{name}-storage", "shell", "pm", "grant",
                     self.config.package, permission)

    def _instrumentation_argument(self, name: str) -> str | None:
        arguments = list(self.config.passthrough)
        for index in range(len(arguments) - 2):
            if arguments[index:index + 2] == ["-e", name]:
                return arguments[index + 2]
        return None

    def seed(self) -> None:
        self.require_tree()
        self.check_registry()
        self._clear_target_data()
        self._push_root()
        self._push_tree(self.out / "scan", f"{SDCARD}/scan")
        self._push_tree(self.out / "backups", f"{SDCARD}/Documents/backup-fixtures")
        self._push_tree(self.out / "library" / "TestBooks", f"{SDCARD}/TestBooks")
        self._push_perf_library()
        self._push_generated()
        for root in (*self.roots(), f"{SDCARD}/Download"):
            self.run(f"mtime-{root.rsplit('/', 1)[-1]}", "shell",
                     f"find {root} -type f -exec touch -t {FIXTURE_MTIME} {{}} + 2>/dev/null || true")
        picker_paths = " ".join(
            f"{SDCARD}/Download/{alias}" for alias in PICKER_ALIASES.values()
        )
        self.run("picker-alias-mtime", "shell", f"touch {picker_paths}")
        readonly = f"{SDCARD}/Download/readonly.pdf"
        self.run("readonly", "shell", "chmod", "444", readonly, check=False)
        mode = self.run("readonly-probe", "shell", "stat", "-c", "%a", readonly,
                        mutate=False, check=False).strip()
        if not self.config.dry_run and mode != "444":
            with (self.root / "seed-warnings.txt").open("a", encoding="utf-8") as fh:
                fh.write(f"readonly.pdf mode is {mode or 'missing'}, not 444. Expected on FUSE.\n")
        for root in (*self.roots(), f"{SDCARD}/Download"):
            self.content(f"scan-{root.rsplit('/', 1)[-1]}", "call", "--uri",
                         "content://media", "--method", "scan_file", "--arg", root,
                         check=False)
        probe = f"{SDCARD}/Download/normal.pdf"
        fs_time = self.run("mtime-fs", "shell", "stat", "-c", "%Y", probe,
                           mutate=False, check=False).strip()
        query = self.content("mtime-media", "query", "--uri",
                             "content://media/external/file", "--projection", "date_modified",
                             "--where", f"_data='{probe}'", mutate=False, check=False)
        match = re.search(r"date_modified=(\d+)", query)
        store_time = match.group(1) if match else ""
        (self.root / "mtime-probe.txt").write_text(
            f"filesystem mtime {fs_time or 'none'}, MediaStore date_modified {store_time or 'none'}\n",
            encoding="utf-8")
        if not self.config.dry_run and fs_time != store_time:
            self.warn("filesystem and MediaStore fixture mtimes do not agree")
