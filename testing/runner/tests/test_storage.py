from __future__ import annotations

import unittest
import shlex
import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace

RUNNER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RUNNER_DIR))

from mjpdf_runner.storage import Storage
from mjpdf_runner.fixtures import Fixtures
from mjpdf_runner.model import CompletedCommand


class StorageLeftoverTest(unittest.TestCase):
    def test_materialized_parent_of_preexisting_media_row_is_bookkeeping(self) -> None:
        before = ["_data=/storage/emulated/0/Documents/MJ PDF, _size=NULL"]

        self.assertTrue(
            Storage._is_materialized_parent_row(
                "_data=/storage/emulated/0/Documents, _size=NULL",
                before,
            )
        )

    def test_new_empty_directory_remains_a_leftover(self) -> None:
        before = ["_data=/storage/emulated/0/Documents/MJ PDF, _size=NULL"]

        self.assertFalse(
            Storage._is_materialized_parent_row(
                "_data=/storage/emulated/0/NewDirectory, _size=NULL",
                before,
            )
        )

    def test_file_row_remains_a_leftover(self) -> None:
        before = ["_data=/storage/emulated/0/Documents/MJ PDF, _size=NULL"]

        self.assertFalse(
            Storage._is_materialized_parent_row(
                "_data=/storage/emulated/0/Documents/leak.pdf, _size=123",
                before,
            )
        )


class StorageSweepTest(unittest.TestCase):
    def make_storage(self, root, outputs=None):
        fixture_root = root / "testing" / "fixtures" / "out"
        fixture_root.mkdir(parents=True)
        for name in ("normal.pdf", "reader's book.pdf"):
            (fixture_root / name).write_bytes(b"pdf fixture")
        run = root / "run"
        (run / "storage").mkdir(parents=True)
        specs = []
        outputs = outputs or {}

        class Commands:
            def run(self, spec, check=True):
                specs.append(spec)
                return CompletedCommand(spec.argv, 0, outputs.get(spec.name, ""))

        config = SimpleNamespace(repo_root=root, runner_dir=RUNNER_DIR, adb="adb", serial="test-device",
                                 dry_run=False, sweep_perf_library=False)
        commands = Commands()
        fixtures = Fixtures(config, commands, run, lambda _: None, lambda _: None)
        return Storage(config, commands, run, fixtures, lambda _: None, lambda _: None), specs

    def test_sweep_clears_exact_seeded_download_rows_without_a_broad_download_delete(self):
        with tempfile.TemporaryDirectory() as temporary:
            storage, specs = self.make_storage(Path(temporary))
            storage.sweep()
            spec = next(s for s in specs if s.name == "storage-delete-suite-storage")
            where = shlex.split(spec.argv[-1])[-1]
            self.assertIn("_data='/storage/emulated/0/Download/normal.pdf'", where)
            self.assertIn("_data='/storage/emulated/0/Download/reader''s book.pdf'", where)
            self.assertIn("_data='/storage/emulated/0/Download/00-picker-normal.pdf'", where)
            self.assertNotIn("LIKE('/storage/emulated/0/Download/%'", where)

    def test_preseed_snapshot_records_existing_directories(self):
        with tempfile.TemporaryDirectory() as temporary:
            storage, _ = self.make_storage(Path(temporary), {
                "storage-directories-pre-seed": "/storage/emulated/0/Documents/MJ PDF\n/storage/emulated/0/Documents",
            })
            storage.snapshot("pre-seed")
            self.assertEqual([
                "/storage/emulated/0/Documents", "/storage/emulated/0/Documents/MJ PDF",
            ], (storage.directory / "pre-seed-directories.txt").read_text().splitlines())

    def test_created_empty_output_directories_are_removed_without_recursive_deletion(self):
        with tempfile.TemporaryDirectory() as temporary:
            storage, specs = self.make_storage(Path(temporary), {
                "storage-remove-empty-output-0": "absent",
                "storage-remove-empty-output-1": "absent",
            })
            (storage.directory / "pre-seed-directories.txt").write_text(
                "/storage/emulated/0/Documents\n/storage/emulated/0/Pictures\n/storage/emulated/0/Download\n")
            storage.sweep()
            removals = [s for s in specs if s.name.startswith("storage-remove-empty-output-")]
            self.assertEqual(2, len(removals))
            self.assertTrue(all("rmdir" in s.argv[-1] and "rm -rf" not in s.argv[-1] for s in removals))
            deletes = [s for s in specs if s.name.startswith("storage-delete-empty-output-")]
            self.assertEqual(2, len(deletes))
            self.assertTrue(all("LIKE" not in s.argv[-1] for s in deletes))

    def test_preexisting_and_nonempty_output_directories_are_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            storage, specs = self.make_storage(Path(temporary))
            (storage.directory / "pre-seed-directories.txt").write_text(
                "/storage/emulated/0/Documents/MJ PDF\n/storage/emulated/0/Documents\n"
                "/storage/emulated/0/Pictures\n/storage/emulated/0/Download\n")
            storage.sweep()
            self.assertFalse(any(s.name == "storage-remove-empty-output-0" for s in specs))
            self.assertTrue(any(s.name == "storage-remove-empty-output-1" for s in specs))
            self.assertFalse(any(s.name.startswith("storage-delete-empty-output-") for s in specs))

    def test_missing_directory_baseline_does_not_remove_existing_output_directories(self):
        with tempfile.TemporaryDirectory() as temporary:
            storage, specs = self.make_storage(Path(temporary))
            storage.sweep()
            self.assertFalse(any(s.name.startswith("storage-remove-empty-output-") for s in specs))

    def test_sweep_to_empty_snapshots_leaves_no_blank_records(self):
        with tempfile.TemporaryDirectory() as temporary:
            storage, _ = self.make_storage(Path(temporary), {
                "storage-snapshot-pre-seed": "123 /storage/emulated/0/Download/normal.pdf\n",
                "storage-media-pre-seed": "Row: 0 _data=/storage/emulated/0/Download/normal.pdf, _size=123\n",
            })
            for label in ("pre-seed", "post-seed", "post-run", "post-sweep"):
                storage.snapshot(label)
            self.assertEqual([], storage.check_leftovers())
            self.assertFalse((storage.root / "leftovers.txt").exists())

    def test_empty_baseline_still_detects_new_zero_byte_file_and_media_row(self):
        with tempfile.TemporaryDirectory() as temporary:
            storage, _ = self.make_storage(Path(temporary), {
                "storage-snapshot-post-sweep": "0 /storage/emulated/0/Documents/leak file.pdf\n",
                "storage-media-post-sweep": "Row: 0 _data=/storage/emulated/0/Documents/leak file.pdf, _size=0\n",
            })
            for label in ("pre-seed", "post-seed", "post-run", "post-sweep"):
                storage.snapshot(label)
            self.assertEqual([
                "FILE 0 /storage/emulated/0/Documents/leak file.pdf",
                "MEDIA _data=/storage/emulated/0/Documents/leak file.pdf, _size=0",
            ], storage.check_leftovers())

    def test_existing_directory_metadata_is_bookkeeping_but_new_files_still_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            storage, _ = self.make_storage(Path(temporary))
            (storage.directory / "pre-seed-directories.txt").write_text("/storage/emulated/0/Documents/MJ PDF\n")
            (storage.directory / "post-sweep-mediastore.txt").write_text(
                "_data=/storage/emulated/0/Documents/MJ PDF, _size=4096\n"
                "_data=/storage/emulated/0/Documents/new-folder, _size=4096\n"
                "_data=/storage/emulated/0/Documents/leaked.pdf, _size=0\n")
            self.assertEqual([
                "MEDIA _data=/storage/emulated/0/Documents/new-folder, _size=4096",
                "MEDIA _data=/storage/emulated/0/Documents/leaked.pdf, _size=0",
            ], storage.check_leftovers())


if __name__ == "__main__":
    unittest.main()
