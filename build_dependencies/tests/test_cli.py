import builtins
from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

from build_dependencies import pdfium_source
from build_dependencies.values import Arch


PROJECT_ROOT = Path(__file__).resolve().parents[2]
CLI_PATH = PROJECT_ROOT / "build_dependencies.py"
BUILD_STAGES = (
    "build_pdfium_from_source", "fetch_prebuilt_pdfium", "build_libpng_libs",
    "build_freetype_libs", "copy_shared_cpp_libs", "build_native_code",
)


class CliTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix=".cli-test-", dir=Path(__file__).parent)
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.addCleanup(os.chdir, Path.cwd())
        self.caller = self.root / "caller"
        self.decoy = self.caller / "build_dependencies"
        (self.decoy / "build").mkdir(parents=True)
        self.sentinel = self.decoy / "build" / "keep.txt"
        self.sentinel.write_text("caller data\n")
        self.stage_cwd = self.root / "inside-build-stage"
        self.stage_cwd.mkdir()
        self.libs = self.root / "libs"
        (self.libs / "arm64-v8a").mkdir(parents=True)
        for name in ("libmodpdfium.so", "libmodft2.so", "libmodpng.so", "libc++_shared.so"):
            (self.libs / "arm64-v8a" / name).write_bytes(b"fixture library")
        os.chdir(self.caller)

        spec = importlib.util.spec_from_file_location("mj_pdf_build_dependencies_cli_tests", CLI_PATH)
        assert spec is not None and spec.loader is not None
        self.cli = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.cli)
        self.stdout = self.enterContext(redirect_stdout(io.StringIO()))
        self.stderr = self.enterContext(redirect_stderr(io.StringIO()))
        self.events = []
        for name in BUILD_STAGES:
            def record(arches, stage=name):
                self.events.append((stage, list(arches), Path.cwd()))
                if stage == "build_native_code":
                    os.chdir(self.stage_cwd)

            self.enterContext(mock.patch.object(self.cli, name, side_effect=record))

        # Guard real side effects even if a regression bypasses the mocked build stages.
        for target in (
            "subprocess.Popen", "os.system", "socket.socket.connect", "socket.create_connection",
            "shutil.rmtree", "os.mkdir", "os.unlink", "os.remove", "os.rmdir",
            "os.rename", "os.replace",
        ):
            self.enterContext(mock.patch(target, side_effect=AssertionError(f"Forbidden side effect: {target}")))
        for module in (builtins, io):
            opener = module.open

            def read_only_open(file, mode="r", *args, _open=opener, **kwargs):
                self.assertFalse(set(mode) & set("wax+"), f"Unexpected file write: {file}")
                return _open(file, mode, *args, **kwargs)

            self.enterContext(mock.patch.object(module, "open", side_effect=read_only_open))

    def arguments(self, *arguments):
        return mock.patch.object(sys, "argv", [str(CLI_PATH), *arguments])

    def assert_prebuilt_warning(self, output):
        output = output.lower()
        for word in ("warning", "prebuilt", "local", "arabic", "patch"):
            self.assertIn(word, output)
        self.assertRegex(output, r"not included|does not include|do not include|without|omit|exclude")

    def test_parse_args_preserves_defaults_choices_and_flags(self):
        with self.arguments():
            args = self.cli.parse_args()
        self.assertEqual((args.pdfium, args.abi), ("from-source", "all"))
        self.assertFalse(args.fresh or args.native_only or args.dry_run)
        for mode in ("from-source", "prebuilt"):
            for abi in ("all", "arm64-v8a", "armeabi-v7a", "x86_64", "x86"):
                for flag in ("--fresh", "--native-only"):
                    with self.subTest(mode=mode, abi=abi, flag=flag), self.arguments(
                        "--pdfium", mode, "--abi", abi, flag, "--dry-run",
                    ):
                        args = self.cli.parse_args()
                        self.assertEqual((args.pdfium, args.abi), (mode, abi))
                        self.assertEqual(args.fresh, flag == "--fresh")
                        self.assertEqual(args.native_only, flag == "--native-only")
                        self.assertTrue(args.dry_run)
        for arguments in (("--pdfium", "unknown"), ("--abi", "arm64")):
            with self.subTest(arguments=arguments), self.arguments(*arguments):
                with self.assertRaises(SystemExit) as raised:
                    self.cli.parse_args()
                self.assertEqual(raised.exception.code, 2)

    def test_fresh_native_only_is_rejected_before_cleanup_even_for_dry_run(self):
        for entrypoint in (self.cli.parse_args, self.cli.main):
            for extra in ((), ("--dry-run",)):
                with self.subTest(entrypoint=entrypoint.__name__, extra=extra), self.arguments(
                    "--fresh", "--native-only", *extra,
                ), mock.patch.object(os, "chdir", side_effect=AssertionError("Unexpected chdir")):
                    self.stderr.seek(0)
                    self.stderr.truncate()
                    with self.assertRaises(SystemExit) as raised:
                        entrypoint()
                    self.assertEqual(raised.exception.code, 2)
                    self.assertIn("--fresh", self.stderr.getvalue())
                    self.assertIn("--native-only", self.stderr.getvalue())
                    self.assertIn("usage:", self.stderr.getvalue().lower())
                    self.assertEqual(self.events, [])
                    self.assertEqual(self.sentinel.read_text(), "caller data\n")

    def test_source_dry_run_reports_validated_pin_and_ordered_patches_without_side_effects(self):
        manifest = json.loads((PROJECT_ROOT / "build_dependencies/patches/pdfium/series.json").read_text())
        for selection in ((), ("--pdfium", "from-source")):
            with self.subTest(selection=selection), self.arguments(
                *selection, "--abi", "arm64-v8a", "--fresh", "--dry-run",
            ), mock.patch.object(os, "chdir", side_effect=AssertionError("Dry run changed cwd")):
                self.stdout.seek(0)
                self.stdout.truncate()
                self.cli.main()
                output = self.stdout.getvalue()
                self.assertIn(manifest["revision"], output)
                patch_names = [entry["file"] for entry in manifest["patches"]]
                self.assertTrue(patch_names, "The checked-in profile must contain local patches")
                positions = [output.index(name) for name in patch_names]
                self.assertEqual(positions, sorted(positions))
                self.assertIn("arm64-v8a", output)
                self.assertNotIn("armeabi-v7a", output)
                self.assertIn("clean outputs", output.lower())
                self.assertEqual(self.events, [])
                self.assertEqual(Path.cwd(), self.caller)
                self.assertEqual(self.sentinel.read_text(), "caller data\n")

        with self.arguments("--dry-run"), mock.patch.object(
            pdfium_source, "PATCH_DIR", self.root / "missing-profile",
        ), mock.patch.object(os, "chdir", side_effect=AssertionError("Dry run changed cwd")):
            with self.assertRaises((OSError, ValueError, RuntimeError, SystemExit)):
                self.cli.main()
            self.assertEqual(self.events, [])

    def test_prebuilt_and_native_only_dry_runs_skip_source_profile(self):
        manifest = json.loads((PROJECT_ROOT / "build_dependencies/patches/pdfium/series.json").read_text())
        patch_names = [entry["file"] for entry in manifest["patches"]]
        self.assertTrue(patch_names, "The checked-in profile must contain local patches")
        with mock.patch.object(pdfium_source, "PATCH_DIR", self.root / "missing-profile"):
            for arguments in (("--pdfium", "prebuilt"), ("--native-only",)):
                with self.subTest(arguments=arguments), self.arguments(
                    *arguments, "--dry-run",
                ), mock.patch.object(os, "chdir", side_effect=AssertionError("Dry run changed cwd")):
                    self.stdout.seek(0)
                    self.stdout.truncate()
                    self.cli.main()
                    output = self.stdout.getvalue()
                    self.assertIn("ndk-build", output)
                    for name in patch_names:
                        self.assertNotIn(name, output)
                    if "prebuilt" in arguments:
                        self.assert_prebuilt_warning(output)
                    else:
                        self.assertNotIn("libpng", output)
                        self.assertNotIn("FreeType", output)
                    self.assertEqual(self.events, [])
                    self.assertEqual(Path.cwd(), self.caller)

    def test_main_builds_selected_stages_from_project_and_restores_caller_cwd(self):
        cases = (
            ((), [Arch.arm64, Arch.armeabi, Arch.x86_64, Arch.x86], "build_pdfium_from_source"),
            (("--pdfium", "prebuilt", "--abi", "x86"), [Arch.x86], "fetch_prebuilt_pdfium"),
            (("--native-only", "--abi", "arm64-v8a"), [Arch.arm64], None),
        )
        for arguments, arches, pdfium_stage in cases:
            with self.subTest(arguments=arguments), self.arguments(*arguments):
                os.chdir(self.caller)
                self.events.clear()
                self.stdout.seek(0)
                self.stdout.truncate()
                with mock.patch.object(self.cli, "LIB_DIR_PATH", str(self.libs)):
                    if pdfium_stage != "build_pdfium_from_source":
                        with mock.patch.object(pdfium_source, "PATCH_DIR", self.root / "missing-profile"):
                            self.cli.main()
                    else:
                        self.cli.main()
                expected = ["build_native_code"]
                if pdfium_stage:
                    expected = [pdfium_stage, "build_libpng_libs", "build_freetype_libs", "copy_shared_cpp_libs", *expected]
                self.assertEqual(self.events, [
                    (stage, arches, PROJECT_ROOT / "build_dependencies") for stage in expected
                ])
                self.assertEqual(Path.cwd(), self.caller)
                if pdfium_stage == "fetch_prebuilt_pdfium":
                    self.assert_prebuilt_warning(self.stdout.getvalue())

    def test_main_restores_caller_cwd_and_stops_after_build_failure(self):
        for stage, failure in (
            ("build_pdfium_from_source", RuntimeError("injected build failure")),
            ("build_native_code", SystemExit(7)),
        ):
            with self.subTest(stage=stage), self.arguments("--abi", "arm64-v8a"):
                os.chdir(self.caller)
                self.events.clear()

                def fail(arches):
                    self.events.append((stage, list(arches), Path.cwd()))
                    os.chdir(self.stage_cwd)
                    raise failure

                with mock.patch.object(self.cli, stage, side_effect=fail):
                    with self.assertRaises(type(failure)) as raised:
                        self.cli.main()
                self.assertIs(raised.exception, failure)
                self.assertEqual(Path.cwd(), self.caller)
                expected = ["build_pdfium_from_source"]
                if stage == "build_native_code":
                    expected.extend(["build_libpng_libs", "build_freetype_libs", "copy_shared_cpp_libs", stage])
                self.assertEqual(self.events, [
                    (name, [Arch.arm64], PROJECT_ROOT / "build_dependencies") for name in expected
                ])


if __name__ == "__main__":
    unittest.main()
