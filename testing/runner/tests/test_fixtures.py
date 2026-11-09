from __future__ import annotations

import sys
from dataclasses import replace
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.fixtures import FixtureError, Fixtures, PERF_LIBRARY_TOPS, PICKER_ALIASES, prepare_generated, cleanup_generated
from mjpdf_runner.model import CompletedCommand, RendererLane, RunnerConfig


class FakeCommands:
    def __init__(self, outputs: dict[str, str] | None = None) -> None:
        self.specs = []
        self.records: list[str] = []
        self.outputs = outputs or {}

    def run(self, spec, on_line=None, check=True):
        self.specs.append(spec)
        return CompletedCommand(spec.argv, 0, self.outputs.get(spec.name, ""))

    def record(self, message: str) -> None:
        self.records.append(message)


class GeneratedFixtureStagingTests(unittest.TestCase):
    def test_selective_staging_reuses_sources_and_repairs_device_files_each_batch(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = SimpleNamespace(repo_root=RUNNER_DIR.parents[1], adb="adb", serial="device",
                                     dry_run=False, skip_generated=False, required_fixtures=frozenset({"HUGE_PDF"}))
            with patch.object(Fixtures, "_stage_huge", side_effect=lambda base, target: target.write_bytes(b"fixture")) as huge, \
                 patch.object(Fixtures, "_stage_500") as large:
                config.prepared_fixtures = prepare_generated(config, root / "prepared", config.required_fixtures)
                for batch in range(2):
                    commands = FakeCommands()
                    fixtures = Fixtures(config, commands, root / str(batch), lambda _: None, lambda _: None)
                    fixtures._free_space_guard = lambda: None
                    fixtures._push_generated()
                    pushes = [s for s in commands.specs if s.argv[3] == "push"]
                    self.assertEqual(1, len(pushes))
                    self.assertEqual("/storage/emulated/0/Download/huge.pdf", pushes[0].argv[-1])
                huge.assert_called_once()
                large.assert_not_called()
            source = config.prepared_fixtures / "huge.pdf"
            source.chmod(0o644)
            source.write_bytes(b"changed")
            with self.assertRaises(FixtureError):
                fixtures._push_generated()
            cleanup_generated(config.prepared_fixtures)
            self.assertFalse(source.exists())
            self.assertTrue((config.prepared_fixtures / "manifest.json").exists())

    def test_empty_optional_plan_still_resets_reseeds_and_scans_every_batch(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            out = root / "testing/fixtures/out"
            for relative in ("", "scan", "backups", "library/TestBooks"):
                (out / relative).mkdir(parents=True, exist_ok=True)
            (out / "normal.pdf").write_bytes(b"fixture")
            config = SimpleNamespace(repo_root=root, adb="adb", serial="device", package="example.app",
                                     passthrough=(), dry_run=True, with_perf_library=False,
                                     skip_generated=False, required_fixtures=frozenset())
            for batch in range(2):
                run = root / str(batch)
                run.mkdir()
                commands = FakeCommands()
                fixtures = Fixtures(config, commands, run, lambda _: None, lambda _: None)
                fixtures.check_registry = lambda: None
                fixtures.seed()
                names = [spec.name for spec in commands.specs]
                self.assertIn("fixture-recents-stop", names)
                self.assertIn("fixture-recents-clear", names)
                self.assertLess(names.index("fixture-recents-clear"), names.index("fixture-push-root"))
                self.assertIn("fixture-scan-Download", names)
                self.assertIn("fixture-picker-alias-mtime", names)
                self.assertIn("fixture-readonly", names)
                self.assertFalse(any(name.startswith("fixture-push-generated") for name in names))

    def test_empty_requirements_skip_generation_and_space_probe(self):
        config = SimpleNamespace(repo_root=RUNNER_DIR.parents[1], adb="adb", serial="device", skip_generated=False,
                                 required_fixtures=frozenset(), dry_run=False)
        fixtures = Fixtures(config, FakeCommands(), Path("unused"), lambda _: None, lambda _: None)
        with patch.object(fixtures, "_free_space_guard") as probe:
            fixtures._push_generated()
            probe.assert_not_called()

    def test_cancelled_generation_never_publishes_partial_cache(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = SimpleNamespace(repo_root=RUNNER_DIR.parents[1], dry_run=False)
            cancelled = [False]
            def generate(base, target):
                target.write_bytes(b"partial")
                cancelled[0] = True
            with patch.object(Fixtures, "_stage_huge", side_effect=generate), self.assertRaises(FixtureError):
                prepare_generated(config, root / "prepared", frozenset({"HUGE_PDF"}), lambda: cancelled[0])
            self.assertEqual([], list(root.iterdir()))

    def test_skip_generated_remains_explicit_and_dry_run_does_not_stage(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            warnings = []
            config = SimpleNamespace(repo_root=RUNNER_DIR.parents[1], dry_run=True, skip_generated=True,
                                     required_fixtures=frozenset({"HUGE_PDF"}))
            fixtures = Fixtures(config, FakeCommands(), root, warnings.append, lambda _: None)
            fixtures._push_generated()
            self.assertIn("HUGE_PDF", warnings[0])
            prepare_generated(config, root / "prepared", config.required_fixtures)
            self.assertEqual([], list(root.iterdir()))

    def test_large_fixture_is_staged_as_one_complete_pdf(self) -> None:
        base = RUNNER_DIR.parent / "fixtures" / "out" / "perf-small.pdf"
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "huge.pdf"

            size = Fixtures._stage_huge(base, target, pad_mib=1)
            data = target.read_bytes()

        self.assertEqual(size, len(data))
        self.assertGreater(size, 1024 * 1024)
        self.assertTrue(data.startswith(base.read_bytes()))
        self.assertTrue(data.endswith(b"%%EOF\n"))

    def test_multi_page_fixture_is_staged_as_one_complete_pdf(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "huge-500mb.pdf"

            Fixtures._stage_500(target, pages=2, mib=1)
            data = target.read_bytes()

        self.assertGreater(len(data), 2 * 1024 * 1024)
        self.assertTrue(data.startswith(b"%PDF-1.7"))
        self.assertIn(b"/Count 2", data)
        self.assertIn(b"/Length 1048576", data)
        self.assertTrue(data.endswith(b"%%EOF\n"))

    def test_picker_aliases_are_copied_to_the_top_of_downloads(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            out = root / "testing" / "fixtures" / "out"
            out.mkdir(parents=True)
            for source in PICKER_ALIASES:
                (out / source).write_bytes(b"fixture")
            commands = FakeCommands()
            config = SimpleNamespace(repo_root=root, adb="adb", serial="fake-device")
            fixtures = Fixtures(config, commands, root / "run", lambda _: None, lambda _: None)

            fixtures._push_root()

        alias_command = next(spec for spec in commands.specs
                             if spec.name == "fixture-picker-aliases")
        shell = alias_command.argv[-1]
        for source, alias in PICKER_ALIASES.items():
            self.assertTrue(alias.startswith("00-picker-"))
            self.assertIn(f"/Download/{source} /storage/emulated/0/Download/{alias}", shell)


class LegacyStorageGrantTests(unittest.TestCase):
    @staticmethod
    def fixtures(api: int, storage_access: str | None = None) -> tuple[Fixtures, FakeCommands]:
        root = Path("/tmp/mjpdf-runner-test")
        commands = FakeCommands({"fixture-storage-api": str(api)})
        passthrough = () if storage_access is None else ("-e", "storageAccess", storage_access)
        config = SimpleNamespace(
            repo_root=root,
            adb="adb",
            serial="fake-device",
            package="com.example.app",
            dry_run=False,
            passthrough=passthrough,
        )
        return Fixtures(config, commands, root, lambda _: None, lambda _: None), commands

    def test_api_28_regrants_legacy_permissions_immediately_after_clear(self) -> None:
        fixtures, commands = self.fixtures(28)

        fixtures._clear_target_data()

        names = [spec.name for spec in commands.specs]
        self.assertEqual(
            [
                "fixture-recents-stop",
                "fixture-recents-clear",
                "fixture-storage-api",
                "fixture-grant-read-storage",
                "fixture-grant-write-storage",
            ],
            names,
        )
        grants = [spec.argv for spec in commands.specs if spec.name.startswith("fixture-grant-")]
        self.assertEqual(
            [
                ("adb", "-s", "fake-device", "shell", "pm", "grant", "com.example.app",
                 "android.permission.READ_EXTERNAL_STORAGE"),
                ("adb", "-s", "fake-device", "shell", "pm", "grant", "com.example.app",
                 "android.permission.WRITE_EXTERNAL_STORAGE"),
            ],
            grants,
        )

    def test_api_30_does_not_request_legacy_permissions(self) -> None:
        fixtures, commands = self.fixtures(30)

        fixtures._clear_target_data()

        self.assertFalse(any(spec.name.startswith("fixture-grant-") for spec in commands.specs))

    def test_denied_mode_leaves_api_28_permissions_revoked(self) -> None:
        fixtures, commands = self.fixtures(28, storage_access="denied")

        fixtures._clear_target_data()

        self.assertFalse(any(spec.name.startswith("fixture-grant-") for spec in commands.specs))

    def test_denied_mode_revokes_api_30_all_files_access(self) -> None:
        fixtures, commands = self.fixtures(30, storage_access="denied")

        fixtures._clear_target_data()

        deny = next(spec for spec in commands.specs if spec.name == "fixture-deny-all-files")
        self.assertEqual(
            ("adb", "-s", "fake-device", "shell", "appops", "set", "--uid", "com.example.app",
             "MANAGE_EXTERNAL_STORAGE", "deny"),
            deny.argv,
        )


class PerfLibraryDryRunTests(unittest.TestCase):
    def config(self, root: Path) -> RunnerConfig:
        return RunnerConfig(
            repo_root=root,
            runner_dir=RUNNER_DIR,
            run_id="dry-run",
            serial="fake-device",
            adb="adb",
            package="com.example.app",
            test_package="com.example.app.test",
            profile_size="1080x2400",
            profile_density="440",
            profile_locale="en-US",
            profile_timezone="UTC",
            logcat_buffer="64M",
            renderer_lane=RendererLane.HARDWARE,
            with_perf_library=True,
            dry_run=True,
        )

    def make_source(self, root: Path) -> Path:
        source = root / "testing" / "fixtures" / "out" / "perf-library-100k"
        for name in PERF_LIBRARY_TOPS:
            (source / name).mkdir(parents=True, exist_ok=True)
        return source

    def test_perf_dry_run_records_remote_plan_without_creating_archives(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(root)
            commands = FakeCommands()
            fixtures = Fixtures(self.config(root), commands, root / "run", lambda _: None, lambda _: None)

            with patch("mjpdf_runner.fixtures.tarfile.open", side_effect=AssertionError("archive staged")):
                fixtures._push_perf_library()

            names = [spec.name for spec in commands.specs]
            self.assertEqual(4, sum(name.startswith("fixture-perf-push-") for name in names))
            self.assertIn("fixture-perf-extract", names)
            self.assertIn("fixture-perf-stage-clean", names)
            self.assertIn("fixture-perf-count-after", names)
            self.assertEqual(["dry-run: stage 4 perf-library tar archives"], commands.records)

    def test_non_perf_run_removes_a_stale_perf_library(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            commands = FakeCommands()
            config = replace(
                self.config(root),
                with_perf_library=False,
                dry_run=False,
            )
            fixtures = Fixtures(config, commands, root / "run", lambda _: None, lambda _: None)

            fixtures._push_perf_library()

        command = commands.specs[-1]
        self.assertEqual("fixture-perf-remove-unrequested", command.name)
        self.assertEqual(
            ("adb", "-s", "fake-device", "shell", "rm", "-rf",
             "/storage/emulated/0/Documents/perf-library"),
            command.argv,
        )

    def test_perf_dry_run_rejects_an_unexpected_source_shape(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = self.make_source(root)
            (source / "unexpected").mkdir()
            fixtures = Fixtures(self.config(root), FakeCommands(), root / "run",
                                lambda _: None, lambda _: None)

            with self.assertRaisesRegex(FixtureError, "must contain exactly"):
                fixtures._push_perf_library()


if __name__ == "__main__":
    unittest.main()
