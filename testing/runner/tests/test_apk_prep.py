from __future__ import annotations

import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))
from mjpdf_runner.apk_prep import (APP_APK, TEST_APK, ApkPreparationError,
                                   build_tool, prepare_apks, prepare_utility_apks)
from mjpdf_runner.model import CompletedCommand


class FakeCommands:
    def __init__(self, app_signer: str = "a" * 64, test_signer: str | None = None):
        self.app_signer = app_signer
        self.test_signer = test_signer or app_signer
        self.records: list[str] = []

    def run(self, spec, check=True):
        signer = self.test_signer if "androidTest" in spec.argv[-1] else self.app_signer
        return CompletedCommand(
            spec.argv,
            0,
            f"V2 Signer: certificate SHA-256 digest: {signer}\n",
        )

    def record(self, message: str) -> None:
        self.records.append(message)


def write_apk(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("AndroidManifest.xml", payload)


class ApkPreparationTests(unittest.TestCase):
    def test_utility_resolution_snapshots_both_apks_and_keeps_sources_independent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first, second = root / "orchestrator.apk", root / "services.apk"
            write_apk(first, b"orchestrator")
            write_apk(second, b"services")
            class Utilities(FakeCommands):
                def run(self, spec, check=True):
                    if spec.name == "resolve-orchestrator":
                        return CompletedCommand(spec.argv, 0, f"APK {first}\nAPK {second}")
                    return super().run(spec, check)
            commands = Utilities()
            with patch("mjpdf_runner.apk_prep.build_tool", return_value=Path("/apksigner")):
                prepared = prepare_utility_apks(root, RUNNER_DIR, root / "prepared", commands)
            self.assertEqual(2, len(prepared))
            original = [p.read_bytes() for p in prepared]
            first.write_bytes(b"changed later")
            self.assertEqual(original, [p.read_bytes() for p in prepared])
            self.assertTrue(all(p.stat().st_mode & 0o222 == 0 for p in prepared))
            records = json.loads((root / "prepared/manifest.json").read_text())
            self.assertTrue(all(record["sha256"] and record["signerSha256"] for record in records))

    def test_build_tool_uses_sdk_from_local_properties(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sdk = root / "configured-sdk"
            tool = sdk / "build-tools" / "36.0.0" / "apksigner"
            tool.parent.mkdir(parents=True)
            tool.write_text("#!/bin/sh\n", encoding="utf-8")
            (root / "local.properties").write_text(
                f"sdk.dir={sdk}\n",
                encoding="utf-8",
            )

            with patch("mjpdf_runner.apk_prep.shutil.which", return_value=None):
                resolved = build_tool("apksigner", root)

        self.assertEqual(tool, resolved)

    def test_snapshot_is_immutable_and_records_hashes_and_signer(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_apk(root / APP_APK, b"app")
            write_apk(root / TEST_APK, b"test")
            commands = FakeCommands()

            with patch("mjpdf_runner.apk_prep.build_tool", return_value=Path("/apksigner")):
                prepared = prepare_apks(root, root / "run" / "apks", commands)

            manifest = json.loads(prepared.manifest.read_text(encoding="utf-8"))
            app_mode = prepared.app.stat().st_mode & 0o777
            test_mode = prepared.test.stat().st_mode & 0o777

        self.assertEqual("a" * 64, manifest["signerSha256"])
        self.assertEqual(64, len(manifest["app"]["sha256"]))
        self.assertEqual(64, len(manifest["test"]["sha256"]))
        self.assertEqual(0o444, app_mode)
        self.assertEqual(0o444, test_mode)
        self.assertTrue(any("prepared immutable APK pair" in item for item in commands.records))

    def test_mismatched_signers_are_rejected_and_snapshots_removed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_apk(root / APP_APK, b"app")
            write_apk(root / TEST_APK, b"test")
            destination = root / "run" / "apks"
            commands = FakeCommands(test_signer="b" * 64)

            with patch("mjpdf_runner.apk_prep.build_tool", return_value=Path("/apksigner")):
                with self.assertRaisesRegex(ApkPreparationError, "signatures do not match"):
                    prepare_apks(root, destination, commands)

            self.assertFalse((destination / APP_APK.name).exists())
            self.assertFalse((destination / TEST_APK.name).exists())


if __name__ == "__main__":
    unittest.main()
