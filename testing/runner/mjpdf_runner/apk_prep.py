from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path

from .model import CommandSpec
from .process import CommandRunner

APP_APK = Path("app/build/outputs/apk/debug/app-debug.apk")
TEST_APK = Path("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
_COPY_ATTEMPTS = 3


class ApkPreparationError(RuntimeError):
    pass


@dataclass(frozen=True)
class PreparedApks:
    app: Path
    test: Path
    manifest: Path


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _stable_copy(source: Path, destination: Path) -> str:
    destination.parent.mkdir(parents=True, exist_ok=True)
    for _attempt in range(_COPY_ATTEMPTS):
        before = _sha256(source)
        with tempfile.NamedTemporaryFile(
            dir=destination.parent,
            prefix=f".{destination.name}.",
            delete=False,
        ) as temporary:
            temporary_path = Path(temporary.name)
        try:
            shutil.copyfile(source, temporary_path)
            copied = _sha256(temporary_path)
            after = _sha256(source)
            if before == copied == after:
                os.replace(temporary_path, destination)
                destination.chmod(0o444)
                return copied
        finally:
            temporary_path.unlink(missing_ok=True)
    raise ApkPreparationError(f"APK changed while being snapshotted: {source}")


def _configured_sdk(repo_root: Path) -> Path | None:
    properties = repo_root / "local.properties"
    if not properties.is_file():
        return None
    for raw_line in properties.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() != "sdk.dir":
            continue
        decoded = value.strip().replace("\\:", ":").replace("\\\\", "\\")
        path = Path(decoded).expanduser()
        return path if path.is_absolute() else repo_root / path
    return None


def build_tool(name: str, repo_root: Path) -> Path:
    executable = shutil.which(name)
    if executable is not None:
        return Path(executable)
    sdk_roots: list[Path] = []
    configured = _configured_sdk(repo_root)
    if configured is not None:
        sdk_roots.append(configured)
    sdk_roots.extend(
        Path(value)
        for value in (os.environ.get("ANDROID_HOME", ""), os.environ.get("ANDROID_SDK_ROOT", ""))
        if value
    )
    sdk_roots.extend((
        Path.home() / "Android" / "Sdk",
        Path("/opt/android-sdk"),
        Path("/usr/lib/android-sdk"),
    ))
    roots = tuple(dict.fromkeys(root / "build-tools" for root in sdk_roots))
    candidates = [path for root in roots if root.is_dir() for path in root.glob(f"*/{name}")]
    if not candidates:
        searched = ", ".join(str(root) for root in roots) or "(none)"
        raise ApkPreparationError(
            f"Android build tool {name} was not found; searched PATH and {searched}"
        )
    return max(
        candidates,
        key=lambda path: tuple(int(part) for part in re.findall(r"\d+", path.parent.name)),
    )


def _validate_zip(path: Path) -> None:
    try:
        with zipfile.ZipFile(path) as archive:
            corrupt = archive.testzip()
    except (OSError, zipfile.BadZipFile) as exc:
        raise ApkPreparationError(f"invalid APK archive {path}: {exc}") from exc
    if corrupt is not None:
        raise ApkPreparationError(f"invalid APK archive {path}: corrupt member {corrupt}")


def _parse_signer_digest(output: str, path: Path) -> str:
    digests = {
        match.lower()
        for match in re.findall(
            r"^.*\bsigner\b.*certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$",
            output,
            re.IGNORECASE | re.MULTILINE,
        )
    }
    if not digests:
        raise ApkPreparationError(f"APK signer digest is missing for {path}")
    if len(digests) != 1:
        raise ApkPreparationError(
            f"APK has multiple distinct signer digests for {path}: {sorted(digests)}"
        )
    return next(iter(digests))


def _signer_digest(path: Path, commands: CommandRunner, repo_root: Path) -> str:
    apksigner = build_tool("apksigner", repo_root)
    completed = commands.run(
        CommandSpec(
            (str(apksigner), "verify", "--print-certs", str(path)),
            f"verify-{path.name}",
            mutate=False,
        ),
        check=False,
    )
    if completed.returncode:
        raise ApkPreparationError(f"APK signature verification failed for {path}")
    return _parse_signer_digest(completed.output, path)


def prepare_apks(
    repo_root: Path,
    destination: Path,
    commands: CommandRunner,
    *,
    dry_run: bool = False,
) -> PreparedApks:
    app = destination / APP_APK.name
    test = destination / TEST_APK.name
    manifest = destination / "manifest.json"
    if dry_run:
        commands.record(f"dry-run: snapshot and verify APK pair in {destination}")
        return PreparedApks(app, test, manifest)

    sources = (repo_root / APP_APK, repo_root / TEST_APK)
    missing = [str(path) for path in sources if not path.is_file()]
    if missing:
        raise ApkPreparationError("missing APK build output(s): " + ", ".join(missing))

    source_before = tuple(_sha256(path) for path in sources)
    app_hash = _stable_copy(sources[0], app)
    test_hash = _stable_copy(sources[1], test)
    source_after = tuple(_sha256(path) for path in sources)
    if source_before != source_after:
        app.unlink(missing_ok=True)
        test.unlink(missing_ok=True)
        raise ApkPreparationError("APK build outputs changed while the pair was being snapshotted")

    try:
        _validate_zip(app)
        _validate_zip(test)
        app_signer = _signer_digest(app, commands, repo_root)
        test_signer = _signer_digest(test, commands, repo_root)
        if app_signer != test_signer:
            raise ApkPreparationError(
                "app and instrumentation APK signatures do not match: "
                f"{app_signer} != {test_signer}"
            )

        payload = {
            "app": {"path": str(app), "sha256": app_hash},
            "test": {"path": str(test), "sha256": test_hash},
            "signerSha256": app_signer,
        }
        manifest.write_text(
            json.dumps(payload, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        manifest.chmod(0o444)
    except ApkPreparationError:
        app.unlink(missing_ok=True)
        test.unlink(missing_ok=True)
        manifest.unlink(missing_ok=True)
        raise
    commands.record(f"prepared immutable APK pair: {manifest}")
    return PreparedApks(app, test, manifest)


def prepare_utility_apks(repo_root: Path, runner_dir: Path, destination: Path,
                         commands: CommandRunner, dry_run: bool = False) -> tuple[Path, ...]:
    spec = CommandSpec((str(repo_root / "gradlew"), "-q", "-p", str(repo_root),
                        "--init-script", str(runner_dir / "androidTestUtil.init.gradle"),
                        ":app:printAndroidTestUtilApks"), "resolve-orchestrator", cwd=repo_root)
    completed = commands.run(spec, check=False)
    if dry_run:
        return ()
    sources = [Path(line[4:]) for line in completed.output.splitlines() if line.startswith("APK ")]
    if completed.returncode or not sources:
        raise ApkPreparationError("could not resolve androidTestUtil APKs through Gradle")
    records = []
    for index, source in enumerate(sources):
        target = destination / f"{index}-{source.name}"
        digest = _stable_copy(source, target)
        _validate_zip(target)
        signer = _signer_digest(target, commands, repo_root)
        records.append({"path": str(target), "sha256": digest, "signerSha256": signer})
    manifest = destination / "manifest.json"
    manifest.write_text(json.dumps(records, indent=2, sort_keys=True) + "\n")
    manifest.chmod(0o444)
    return tuple(Path(record["path"]) for record in records)
