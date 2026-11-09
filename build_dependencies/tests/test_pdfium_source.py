import ast
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock

from build_dependencies import pdfium_source
from build_dependencies.values import Arch


ENGINE = (
    "header\nmode = stock\ncontext 1\ncontext 2\ncontext 3\n"
    + "".join(f"unchanged {number}\n" for number in range(12))
    + "outside hunk = original\n"
)
FIRST_PATCH = """diff --git a/engine.txt b/engine.txt
--- a/engine.txt
+++ b/engine.txt
@@ -1,5 +1,5 @@
 header
-mode = stock
+mode = intermediate
 context 1
 context 2
 context 3
diff --git a/public/feature.h b/public/feature.h
new file mode 100644
--- /dev/null
+++ b/public/feature.h
@@ -0,0 +1 @@
+#define FEATURE 1
"""
SECOND_PATCH = """diff --git a/engine.txt b/engine.txt
--- a/engine.txt
+++ b/engine.txt
@@ -1,5 +1,5 @@
 header
-mode = intermediate
+mode = final
 context 1
 context 2
 context 3
diff --git a/public/feature.h b/public/feature.h
--- a/public/feature.h
+++ b/public/feature.h
@@ -1 +1 @@
-#define FEATURE 1
+#define FEATURE 2
"""

# Do not count missing APIs, wrong signatures, or safety-guard failures as rejection.
REJECTION = (ValueError, RuntimeError, OSError, subprocess.CalledProcessError, SystemExit)


class PdfiumSourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix=".pdfium-test-", dir=Path(__file__).parent)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
        self.env.update(
            GIT_CONFIG_NOSYSTEM="1",
            GIT_CONFIG_GLOBAL=os.devnull,
            GIT_TERMINAL_PROMPT="0",
            GIT_AUTHOR_NAME="PDFium test",
            GIT_AUTHOR_EMAIL="pdfium-test@example.invalid",
            GIT_COMMITTER_NAME="PDFium test",
            GIT_COMMITTER_EMAIL="pdfium-test@example.invalid",
            GIT_AUTHOR_DATE="2026-01-01T00:00:00+00:00",
            GIT_COMMITTER_DATE="2026-01-01T00:00:00+00:00",
        )
        self.enterContext(mock.patch.dict(os.environ, self.env, clear=True))
        self.commands = []
        self.git_commands = []
        real_run, real_popen = subprocess.run, subprocess.Popen

        def guarded_popen(command, *args, **kwargs):
            self.assertIsInstance(command, (list, tuple), "Shell commands are not allowed")
            self.assertEqual(Path(command[0]).name, "git", "No real build or network processes")
            cwd = Path(kwargs.get("cwd") or os.getcwd()).resolve()
            self.assertTrue(cwd.is_relative_to(self.root), f"Git escaped test directory: {cwd}")
            self.assertFalse(kwargs.get("shell"), "Shell commands are not allowed")
            self.assertFalse(
                {"reset", "clean", "stash", "clone", "fetch", "pull", "push", "commit"}
                .intersection(map(str, command)),
                f"Forbidden Git command: {command}",
            )
            if {"-R", "--reverse"}.intersection(map(str, command)):
                self.assertIn("--check", command, "Reverse application must not write the worktree")
            self.git_commands.append((list(map(str, command)), cwd))
            return real_popen(command, *args, **kwargs)

        def local_run(command, *args, **kwargs):
            self.assertIsInstance(command, (list, tuple))
            if Path(command[0]).name == "git":
                return real_run(command, *args, **kwargs)
            cwd = Path(kwargs.get("cwd") or os.getcwd()).resolve()
            self.assertTrue(cwd.is_relative_to(self.root), f"Command escaped test directory: {cwd}")
            command = list(map(str, command))
            self.commands.append((command, cwd))
            self.external_command(command, cwd)
            empty = "" if kwargs.get("text") or kwargs.get("encoding") else b""
            return subprocess.CompletedProcess(command, 0, stdout=empty, stderr=empty)

        self.enterContext(mock.patch.object(subprocess, "Popen", side_effect=guarded_popen))
        self.enterContext(mock.patch.object(subprocess, "run", side_effect=local_run))
        self.enterContext(mock.patch.object(
            pdfium_source, "reset_patched_repositories", create=True,
            side_effect=AssertionError("The legacy reset function must never run"),
        ))
        self.source = self.root / "seed"
        self.source.mkdir()
        self.git(self.source, "init", "--quiet", "--object-format=sha1", "--template=")
        self.assertEqual(Path(self.git(self.source, "rev-parse", "--show-toplevel")), self.source)
        (self.source / "engine.txt").write_text(ENGINE)
        (self.source / "unrelated.txt").write_text("keep me\n")
        (self.source / ".gitignore").write_text("/build/\n")
        self.git(self.source, "add", ".")
        tree = self.git(self.source, "write-tree")
        self.revision = self.git(self.source, "commit-tree", tree, input="fixture\n")
        self.git(self.source, "update-ref", "HEAD", self.revision)
        self.dependency = self.root / "build-seed"
        self.dependency.mkdir()
        self.git(self.dependency, "init", "--quiet", "--object-format=sha1", "--template=")
        (self.dependency / "BUILD.gn").write_text("dependency = pristine\n")
        (self.dependency / ".gitignore").write_text("generated.gni\n")
        self.git(self.dependency, "add", ".")
        tree = self.git(self.dependency, "write-tree")
        self.dependency_revision = self.git(self.dependency, "commit-tree", tree, input="dependency\n")
        self.git(self.dependency, "update-ref", "HEAD", self.dependency_revision)
        self.patch_dir = self.root / "patches"
        self.patch_dir.mkdir()
        self.enterContext(mock.patch.object(pdfium_source, "PATCH_DIR", self.patch_dir, create=True))
        (self.patch_dir / "first.patch").write_text(FIRST_PATCH)
        (self.patch_dir / "second.patch").write_text(SECOND_PATCH)
        self.manifest = {
            "revision": self.revision,
            "url": "https://example.invalid/pdfium.git",
            "gn_args": ['target_os = "android"', "is_debug = false", "contract_test_setting = true"],
            "patches": [
                {"file": "first.patch", "sha256": hashlib.sha256(FIRST_PATCH.encode()).hexdigest()},
                {"file": "second.patch", "sha256": hashlib.sha256(SECOND_PATCH.encode()).hexdigest()},
            ],
        }
        self.write_manifest()
        self.entries = {
            "pdfium": self.manifest["url"],
            "pdfium/build": "https://example.invalid/build.git@" + self.dependency_revision,
            "pdfium/tools:gn/linux": "https://example.invalid/gn@version:1",
            "pdfium/sysroot:archive.tar.xz": "gs://example.invalid/archive.tar.xz",
            "pdfium/disabled": None,
        }
        self.checkout_root = self.root / "dependencies"
        self.checkout_root.mkdir()

    def git(self, source, *args, **kwargs):
        return subprocess.run(
            ["git", *args], cwd=source, env=self.env, check=True,
            capture_output=True, text=True, **kwargs,
        ).stdout.strip()

    def write_manifest(self):
        data = (json.dumps(self.manifest, indent=2) + "\n").encode()
        (self.patch_dir / "series.json").write_bytes(data)
        self.config = dict(self.manifest, fingerprint=hashlib.sha256(data).hexdigest())

    def snapshot(self, directory):
        return {
            str(path.relative_to(directory)): (
                ("symlink", os.readlink(path)) if path.is_symlink()
                else ("directory",) if path.is_dir()
                else ("file", path.stat().st_mode, path.read_bytes())
            )
            for path in directory.rglob("*")
        }

    def apply_fixture(self, source=None):
        self.git(
            source or self.source, "apply",
            str(self.patch_dir / "first.patch"), str(self.patch_dir / "second.patch"),
        )

    def external_command(self, command, cwd) -> None:
        self.fail(f"Unexpected external command: {command}")

    def fake_gclient(self, command, cwd):
        self.assertEqual(command[:2], ["gclient", "sync"])
        self.assertFalse((cwd / ".prepared").exists(), "Stamp was written before preparation")
        shutil.copytree(self.source, cwd / "pdfium")
        shutil.copytree(self.dependency, cwd / "pdfium/build")
        (cwd / "pdfium/build/generated.gni").write_text("generated = true\n")
        (cwd / ".gclient_entries").write_text(f"entries = {self.entries!r}\n")

    def cache_path(self):
        return self.checkout_root / "pdfium_source" / (
            self.config["revision"] + "-" + self.config["fingerprint"][:12]
        )

    def test_load_patchset_validates_manifest_and_fingerprints_exact_bytes(self):
        config = pdfium_source.load_patchset()
        self.assertEqual(config["revision"], self.revision)
        self.assertEqual(config["url"], self.manifest["url"])
        self.assertEqual(config["gn_args"], self.manifest["gn_args"])
        self.assertEqual([entry["file"] for entry in config["patches"]], ["first.patch", "second.patch"])
        self.assertEqual(config["fingerprint"], self.config["fingerprint"])
        self.assertRegex(config["fingerprint"], r"^[0-9a-f]{64}$")
        manifest_path = self.patch_dir / "series.json"
        manifest_path.write_bytes(manifest_path.read_bytes() + b" \n")
        reformatted = pdfium_source.load_patchset()
        self.assertNotEqual(reformatted["fingerprint"], config["fingerprint"])
        self.assertEqual(reformatted["fingerprint"], hashlib.sha256(manifest_path.read_bytes()).hexdigest())

    def test_load_patchset_rejects_invalid_manifest_fields(self):
        load = pdfium_source.load_patchset
        original = json.loads(json.dumps(self.manifest))
        for field, invalid in [
            ("revision", "a" * 39), ("revision", "g" * 40),
            ("url", None), ("gn_args", "is_debug = false"), ("gn_args", [42]),
            ("patches", {}), ("patches", [{"file": "first.patch"}]),
            ("patches", [{"file": "first.patch", "sha256": "not-a-digest"}]),
        ]:
            with self.subTest(field=field, invalid=invalid):
                self.manifest = dict(original, **{field: invalid})
                self.write_manifest()
                with self.assertRaises(REJECTION):
                    load()

    def test_load_patchset_rejects_patch_paths_outside_patch_directory(self):
        load = pdfium_source.load_patchset
        outside = self.root / "outside.patch"
        outside.write_text(FIRST_PATCH)
        (self.patch_dir / "escape.patch").symlink_to(outside)
        for filename in ("../outside.patch", str(outside), "escape.patch"):
            with self.subTest(filename=filename):
                self.manifest["patches"][0]["file"] = filename
                self.write_manifest()
                with self.assertRaises(REJECTION):
                    load()

    def test_build_rejects_missing_or_corrupt_patchset_before_depot_setup(self):
        manifest_path = self.patch_dir / "series.json"
        valid_manifest = manifest_path.read_bytes()
        second = self.patch_dir / "second.patch"
        for invalid in ("missing-manifest", "invalid-json", "missing-late-patch", "wrong-late-hash"):
            with self.subTest(invalid=invalid):
                manifest_path.write_bytes(valid_manifest)
                second.write_text(SECOND_PATCH)
                if invalid == "missing-manifest":
                    manifest_path.unlink()
                elif invalid == "invalid-json":
                    manifest_path.write_text("{")
                elif invalid == "missing-late-patch":
                    second.unlink()
                else:
                    second.write_text(SECOND_PATCH + "\n")
                before = self.snapshot(self.checkout_root)
                with mock.patch.object(pdfium_source, "depot_tools_env") as depot:
                    with self.assertRaises(REJECTION):
                        pdfium_source.build_pdfium_from_source([Arch.arm64])
                depot.assert_not_called()
                self.assertEqual(self.commands, [])
                self.assertEqual(self.snapshot(self.checkout_root), before)

    def test_apply_ordered_patchset_handles_new_files_and_dependent_hunks(self):
        index = (self.source / ".git/index").read_bytes()
        objects = self.snapshot(self.source / ".git/objects")
        self.git_commands.clear()
        pdfium_source.apply_pdfium_patches(self.source, self.config, self.env)
        self.assertEqual((self.source / "engine.txt").read_text(), ENGINE.replace("stock", "final"))
        self.assertEqual((self.source / "public/feature.h").read_text(), "#define FEATURE 2\n")
        self.assertEqual((self.source / "unrelated.txt").read_text(), "keep me\n")
        self.assertEqual(self.git(self.source, "rev-parse", "HEAD"), self.revision)
        self.assertEqual((self.source / ".git/index").read_bytes(), index)
        self.assertEqual(self.snapshot(self.source / ".git/objects"), objects)
        worktree_applications = [
            command for command, cwd in self.git_commands
            if "apply" in command and cwd == self.source
            and not {"--cached", "--check", "--stat", "--numstat", "--summary"}.intersection(command)
        ]
        self.assertEqual(len(worktree_applications), 1, "Apply the whole patchset in one worktree write")
        self.assertNotIn("--reject", worktree_applications[0])

    def test_exact_already_patched_source_is_a_noop(self):
        self.apply_fixture()
        before = self.snapshot(self.source)
        mtimes = {path: path.stat().st_mtime_ns for path in (self.source / "engine.txt", self.source / "public/feature.h")}
        pdfium_source.apply_pdfium_patches(self.source, self.config, self.env)
        pdfium_source.apply_pdfium_patches(self.source, self.config, self.env)
        self.assertEqual(self.snapshot(self.source), before)
        self.assertEqual({path: path.stat().st_mtime_ns for path in mtimes}, mtimes)

    def test_invalid_late_patch_leaves_entire_pristine_source_untouched(self):
        broken = SECOND_PATCH.replace("-mode = intermediate", "-mode = nonexistent")
        (self.patch_dir / "second.patch").write_text(broken)
        self.manifest["patches"][1]["sha256"] = hashlib.sha256(broken.encode()).hexdigest()
        self.write_manifest()
        before = self.snapshot(self.source)
        with self.assertRaises(REJECTION):
            pdfium_source.apply_pdfium_patches(self.source, self.config, self.env)
        self.assertEqual(self.snapshot(self.source), before)
        self.assertEqual((self.source / "engine.txt").read_text(), ENGINE)
        self.assertFalse((self.source / "public/feature.h").exists())

    def test_wrong_revision_is_rejected_without_changes(self):
        tree = self.git(self.source, "rev-parse", "HEAD^{tree}")
        other_revision = self.git(self.source, "commit-tree", tree, input="different revision\n")
        self.manifest["revision"] = other_revision
        self.write_manifest()
        before = self.snapshot(self.source)
        with self.assertRaises(REJECTION):
            pdfium_source.apply_pdfium_patches(self.source, self.config, self.env)
        self.assertEqual(self.snapshot(self.source), before)

    def test_staged_change_is_rejected_and_index_is_preserved(self):
        (self.source / "unrelated.txt").write_text("staged work\n")
        self.git(self.source, "add", "unrelated.txt")
        before = self.snapshot(self.source)
        with self.assertRaises(REJECTION):
            pdfium_source.apply_pdfium_patches(self.source, self.config, self.env)
        self.assertEqual(self.snapshot(self.source), before)

    def test_unknown_tracked_untracked_and_patch_artifacts_are_preserved(self):
        for filename in (
            "unrelated.txt", "notes.txt", "notes.txt.orig", "notes.txt.rej",
            "engine.txt.orig", "engine.txt.rej", "public/feature.h",
        ):
            with self.subTest(filename=filename):
                source = self.root / ("dirty-" + filename.replace("/", "-"))
                shutil.copytree(self.source, source)
                dirty = source / filename
                dirty.parent.mkdir(parents=True, exist_ok=True)
                dirty.write_text("user work\n")
                before = self.snapshot(source)
                with self.assertRaises(REJECTION):
                    pdfium_source.apply_pdfium_patches(source, self.config, self.env)
                self.assertEqual(self.snapshot(source), before)

    def test_ignored_backups_adjacent_to_patch_targets_are_refused_untouched(self):
        for already_patched in (False, True):
            for filename in ("engine.txt.orig", "engine.txt.rej", "public/feature.h.orig", "public/feature.h.rej"):
                with self.subTest(already_patched=already_patched, filename=filename):
                    source = self.root / f"ignored-{already_patched}-{filename.replace('/', '-')}"
                    shutil.copytree(self.source, source)
                    if already_patched:
                        self.apply_fixture(source)
                    exclude = source / ".git/info/exclude"
                    exclude.parent.mkdir(exist_ok=True)
                    exclude.write_text("*.orig\n*.rej\n")
                    backup = source / filename
                    backup.parent.mkdir(parents=True, exist_ok=True)
                    backup.write_text("local backup\n")
                    before = self.snapshot(source)
                    with self.assertRaises(REJECTION):
                        pdfium_source.apply_pdfium_patches(source, self.config, self.env)
                    self.assertEqual(self.snapshot(source), before)

    def test_edit_outside_hunk_in_patch_owned_file_is_rejected_and_preserved(self):
        for already_patched in (False, True):
            with self.subTest(already_patched=already_patched):
                source = self.root / f"owned-edit-{already_patched}"
                shutil.copytree(self.source, source)
                if already_patched:
                    self.apply_fixture(source)
                owned = source / "engine.txt"
                owned.write_text(owned.read_text().replace("outside hunk = original", "outside hunk = user work"))
                before = self.snapshot(source)
                with self.assertRaises(REJECTION):
                    pdfium_source.apply_pdfium_patches(source, self.config, self.env)
                self.assertEqual(self.snapshot(source), before)

    def test_source_must_be_exact_repository_root_not_ancestor_or_symlink(self):
        nested = self.source / "nested"
        nested.mkdir()
        link = self.root / "source-link"
        link.symlink_to(self.source, target_is_directory=True)
        for source in (nested, link):
            with self.subTest(source=source.name):
                before = self.snapshot(self.root)
                with self.assertRaises(REJECTION):
                    pdfium_source.apply_pdfium_patches(source, self.config, self.env)
                self.assertEqual(self.snapshot(self.root), before)

    def test_new_cache_syncs_pinned_minimal_android_checkout_and_stamps_after_patching(self):
        self.external_command = self.fake_gclient
        source = Path(pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config))
        cache = self.cache_path()
        self.assertEqual(source, cache / "pdfium")
        syncs = [(command, cwd) for command, cwd in self.commands if command[:2] == ["gclient", "sync"]]
        self.assertEqual(len(syncs), 1)
        command, cwd = syncs[0]
        self.assertEqual(cwd, cache)
        self.assertEqual(command[command.index("-r") + 1], "pdfium@" + self.revision)
        self.assertIn("--no-history", command)
        self.assertIn("--shallow", command)
        settings = {}
        for statement in ast.parse((cache / ".gclient").read_text()).body:
            if isinstance(statement, ast.Assign):
                for target in statement.targets:
                    if isinstance(target, ast.Name):
                        settings[target.id] = ast.literal_eval(statement.value)
        self.assertEqual(settings["target_os"], ["android"])
        solution = settings["solutions"][0]
        self.assertEqual(solution["name"], "pdfium")
        self.assertEqual(solution["url"], self.config["url"])
        self.assertEqual(solution["custom_vars"]["checkout_configuration"], "minimal")
        self.assertEqual((source / "engine.txt").read_text(), ENGINE.replace("stock", "final"))
        self.assertEqual((source / "build/BUILD.gn").read_text(), "dependency = pristine\n")
        self.assertEqual(self.snapshot(source / "build/.git"), self.snapshot(self.dependency / ".git"))
        self.assertEqual(json.loads((cache / ".prepared").read_text()), {
            "fingerprint": self.config["fingerprint"],
            "entries_sha256": hashlib.sha256((cache / ".gclient_entries").read_bytes()).hexdigest(),
        })

        failed_root = self.root / "failed-preparation"
        failed_root.mkdir()
        self.apply_fixture()
        (self.source / "engine.txt").write_text("unexpected checkout contents\n")
        with self.assertRaises(REJECTION):
            pdfium_source.sync_pdfium(failed_root, self.env, self.config)
        self.assertEqual(list(failed_root.rglob(".prepared")), [])

    def test_prepared_cache_skips_sync_but_revalidates_source(self):
        self.external_command = self.fake_gclient
        source = Path(pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config))
        self.commands.clear()
        self.external_command = lambda command, cwd: self.fail("Prepared cache must not sync")
        before = self.snapshot(self.checkout_root)
        self.assertEqual(Path(pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config)), source)
        self.assertEqual(self.commands, [])
        self.assertEqual(self.snapshot(self.checkout_root), before)
        (source / "unrelated.txt").write_text("local work\n")
        before = self.snapshot(self.checkout_root)
        with self.assertRaises(REJECTION):
            pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config)
        self.assertEqual(self.commands, [])
        self.assertEqual(self.snapshot(self.checkout_root), before)

    def test_ignored_vendor_cargo_backups_allow_preparation_and_repeat_cache_noops(self):
        vendor = Path("third_party/rust-toolchain/lib/rustlib/src/rust/library/vendor")
        exclude = self.source / ".git/info/exclude"
        exclude.parent.mkdir(exist_ok=True)
        exclude.write_text("/third_party/rust-toolchain/\n")
        for number in range(30):
            original = self.source / vendor / f"crate-{number}" / "Cargo.toml.orig"
            original.parent.mkdir(parents=True)
            original.write_text(f'name = "crate-{number}"\n')
        self.entries["pdfium/third_party/rust-toolchain:rust.tar.xz"] = "gs://example.invalid/rust.tar.xz"
        self.external_command = self.fake_gclient
        source = pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config)
        self.assertEqual((source / "engine.txt").read_text(), ENGINE.replace("stock", "final"))
        self.assertEqual((source / "public/feature.h").read_text(), "#define FEATURE 2\n")
        self.assertEqual(self.snapshot(source / vendor), self.snapshot(self.source / vendor))
        originals = list((source / vendor).rglob("Cargo.toml.orig"))
        self.assertEqual(len(originals), 30)
        mtimes = {path: path.stat().st_mtime_ns for path in originals}
        before = self.snapshot(source.parent)
        self.commands.clear()
        for _ in range(2):
            self.assertEqual(pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config), source)
        self.assertEqual(self.commands, [])
        self.assertEqual(self.snapshot(source.parent), before)
        self.assertEqual({path: path.stat().st_mtime_ns for path in originals}, mtimes)

    def test_prepared_cache_revalidates_nested_git_dependencies_without_writes_or_sync(self):
        self.external_command = self.fake_gclient
        prepared = Path(pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config))
        for change in (
            "tracked", "staged", "untracked", "assume-unchanged", "skip-worktree", "revision",
            "missing", "missing-git", "missing-ref", "symlink", "symlink-ref",
        ):
            with self.subTest(change=change):
                root = self.root / change
                shutil.copytree(self.checkout_root, root)
                dependency = root / prepared.relative_to(self.checkout_root) / "build"
                if change in ("tracked", "staged", "assume-unchanged", "skip-worktree"):
                    (dependency / "BUILD.gn").write_text("dependency = user_work\n")
                    if change == "tracked":
                        (dependency.parent / "engine.txt").write_text(ENGINE)
                        (dependency.parent / "public/feature.h").unlink()
                    if change == "staged":
                        self.git(dependency, "add", "BUILD.gn")
                    elif change in ("assume-unchanged", "skip-worktree"):
                        self.git(dependency, "update-index", "--" + change, "BUILD.gn")
                elif change == "untracked":
                    (dependency / "notes.txt").write_text("user work\n")
                elif change == "revision":
                    tree = self.git(dependency, "rev-parse", "HEAD^{tree}")
                    revision = self.git(dependency, "commit-tree", tree, input="different pin\n")
                    self.git(dependency, "update-ref", "HEAD", revision)
                elif change == "missing-git":
                    (dependency / ".git").rename(self.root / "moved-git")
                elif change in ("missing-ref", "symlink-ref"):
                    ref = dependency / ".git" / self.git(dependency, "symbolic-ref", "HEAD")
                    moved = self.root / (change + "-ref")
                    ref.rename(moved)
                    if change == "symlink-ref":
                        ref.symlink_to(moved)
                else:
                    moved = self.root / (change + "-dependency")
                    dependency.rename(moved)
                    if change == "symlink":
                        dependency.symlink_to(moved, target_is_directory=True)
                self.commands.clear()
                before = self.snapshot(self.root)
                with self.assertRaises(REJECTION):
                    pdfium_source.sync_pdfium(root, self.env, self.config)
                self.assertEqual(self.commands, [])
                self.assertEqual(self.snapshot(self.root), before)

    def test_prepared_cache_rejects_changed_or_missing_inventory_and_metadata(self):
        self.external_command = self.fake_gclient
        prepared = Path(pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config))
        for change in (
            "changed", "removed-dependency", "missing", "symlink",
            "stamp-profile", "stamp-entries", "missing-config", "missing-stamp",
        ):
            with self.subTest(change=change):
                root = self.root / change
                shutil.copytree(self.checkout_root, root)
                entries = root / prepared.parent.relative_to(self.checkout_root) / ".gclient_entries"
                if change == "changed":
                    entries.write_bytes(entries.read_bytes() + b" \n")
                elif change == "removed-dependency":
                    entries.write_text(f"entries = {{'pdfium': {self.config['url']!r}}}\n")
                elif change == "missing":
                    entries.unlink()
                elif change.startswith("stamp-"):
                    stamp = entries.parent / ".prepared"
                    fields = json.loads(stamp.read_text())
                    fields["fingerprint" if change == "stamp-profile" else "entries_sha256"] = "0" * 64
                    stamp.write_text(json.dumps(fields))
                elif change in ("missing-config", "missing-stamp"):
                    (entries.parent / (".gclient" if change == "missing-config" else ".prepared")).unlink()
                else:
                    moved = self.root / "outside-entries"
                    entries.rename(moved)
                    entries.symlink_to(moved)
                self.commands.clear()
                before = self.snapshot(self.root)
                with self.assertRaises(REJECTION):
                    pdfium_source.sync_pdfium(root, self.env, self.config)
                self.assertEqual(self.commands, [])
                self.assertEqual(self.snapshot(self.root), before)

    def test_sync_requires_safe_literal_pinned_entries_before_patching(self):
        url = self.entries["pdfium/build"]
        invalid = [
            None,
            "entries = {",
            "entries = []",
            "entries = {}",
            "entries = __import__('pathlib').Path('executed').touch()",
            f"entries = {self.entries!r}\nraise RuntimeError('must not execute')\n",
        ]
        for path in (
            "../build", "/tmp/build", "pdfium/../build", "pdfium/./build", "pdfium//build",
            "pdfium\\build", "pdfium", "another-root/build", "../tools:binary", "../disabled",
        ):
            entries = dict(self.entries, **{path: None if path.endswith("disabled") else url})
            invalid.append(f"entries = {entries!r}\n")
        for value in ("https://example.invalid/build.git@main", "https://example.invalid/build.git@abc", 42):
            entries = dict(self.entries, **{"pdfium/build": value})
            invalid.append(f"entries = {entries!r}\n")
        for index, data in enumerate(invalid):
            with self.subTest(data=data):
                root = self.root / f"invalid-entries-{index}"
                root.mkdir()

                def sync(command, cwd):
                    self.fake_gclient(command, cwd)
                    entries = cwd / ".gclient_entries"
                    if data is None:
                        entries.unlink()
                    else:
                        entries.write_text(data)

                self.external_command = sync
                with self.assertRaises(REJECTION):
                    pdfium_source.sync_pdfium(root, self.env, self.config)
                cache = root / self.cache_path().relative_to(self.checkout_root)
                self.assertEqual((cache / "pdfium/engine.txt").read_text(), ENGINE)
                self.assertEqual(self.snapshot(cache / "pdfium/.git"), self.snapshot(self.source / ".git"))
                self.assertFalse((cache / ".prepared").exists())
                self.assertFalse((cache / "executed").exists())
                self.commands.clear()
                before = self.snapshot(root)
                with self.assertRaises(REJECTION):
                    pdfium_source.sync_pdfium(root, self.env, self.config)
                self.assertEqual(self.commands, [])
                self.assertEqual(self.snapshot(root), before)

    def test_fresh_cache_rejects_dirty_or_wrong_revision_dependency_before_patching(self):
        for change in ("tracked", "staged", "revision"):
            with self.subTest(change=change):
                root = self.root / change
                root.mkdir()

                def sync(command, cwd):
                    self.fake_gclient(command, cwd)
                    dependency = cwd / "pdfium/build"
                    if change == "revision":
                        tree = self.git(dependency, "rev-parse", "HEAD^{tree}")
                        revision = self.git(dependency, "commit-tree", tree, input="wrong pin\n")
                        self.git(dependency, "update-ref", "HEAD", revision)
                    else:
                        (dependency / "BUILD.gn").write_text("dependency = changed\n")
                        if change == "staged":
                            self.git(dependency, "add", "BUILD.gn")

                self.external_command = sync
                with self.assertRaises(REJECTION):
                    pdfium_source.sync_pdfium(root, self.env, self.config)
                cache = root / self.cache_path().relative_to(self.checkout_root)
                self.assertFalse((cache / ".prepared").exists())
                self.assertEqual((cache / "pdfium/engine.txt").read_text(), ENGINE)
                self.assertEqual(self.snapshot(cache / "pdfium/.git"), self.snapshot(self.source / ".git"))

    def test_sync_sanitizes_git_redirects_for_root_and_dependencies(self):
        self.external_command = self.fake_gclient
        env = dict(self.env, **{
            name: str(self.root / "redirected") for name in (
                "GIT_DIR", "GIT_COMMON_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE",
                "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES",
            )
        })
        env.update(GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="core.worktree", GIT_CONFIG_VALUE_0=str(self.root))
        source = pdfium_source.sync_pdfium(self.checkout_root, env, self.config)
        self.commands.clear()
        before = self.snapshot(self.root)
        self.assertEqual(pdfium_source.sync_pdfium(self.checkout_root, env, self.config), source)
        self.assertEqual(self.commands, [])
        self.assertEqual(self.snapshot(self.root), before)
        self.assertFalse((self.root / "redirected").exists())

    def test_incomplete_existing_cache_is_refused_untouched(self):
        cache = self.cache_path()
        cache.mkdir(parents=True)
        (cache / ".gclient").write_text("incomplete checkout\n")
        shutil.copytree(self.source, cache / "pdfium")
        (cache / "pdfium/engine.txt").write_text("unfinished work\n")
        for stamp in (None, "wrong fingerprint\n"):
            with self.subTest(stamp=stamp):
                if stamp is not None:
                    (cache / ".prepared").write_text(stamp)
                before = self.snapshot(self.checkout_root)
                with self.assertRaises(REJECTION):
                    pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config)
                self.assertEqual(self.commands, [])
                self.assertEqual(self.snapshot(self.checkout_root), before)

    def test_patch_revision_and_gn_changes_select_new_caches(self):
        self.external_command = self.fake_gclient
        previous = {}
        for change in ("initial", "patch", "revision", "gn"):
            with self.subTest(change=change):
                if change == "patch":
                    changed = SECOND_PATCH.replace("+mode = final", "+mode = faster")
                    (self.patch_dir / "second.patch").write_text(changed)
                    self.manifest["patches"][1]["sha256"] = hashlib.sha256(changed.encode()).hexdigest()
                elif change == "revision":
                    tree = self.git(self.source, "rev-parse", "HEAD^{tree}")
                    revision = self.git(self.source, "commit-tree", tree, input="new pin\n")
                    self.git(self.source, "update-ref", "HEAD", revision)
                    self.manifest["revision"] = revision
                elif change == "gn":
                    self.manifest["gn_args"].append("use_thin_lto = true")
                self.write_manifest()
                source = Path(pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config))
                self.assertEqual(source, self.cache_path() / "pdfium")
                self.assertNotIn(source.parent, previous)
                for cache, snapshot in previous.items():
                    self.assertEqual(self.snapshot(cache), snapshot)
                previous[source.parent] = self.snapshot(source.parent)
        self.assertEqual(len(previous), 4)

    def test_prepared_cache_rejects_source_symlink_escape_without_sync(self):
        self.external_command = self.fake_gclient
        source = Path(pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config))
        escaped = self.root / "escaped-source"
        source.rename(escaped)
        source.symlink_to(escaped, target_is_directory=True)
        self.commands.clear()
        before = self.snapshot(self.root)
        with self.assertRaises(REJECTION):
            pdfium_source.sync_pdfium(self.checkout_root, self.env, self.config)
        self.assertEqual(self.commands, [])
        self.assertEqual(self.snapshot(self.root), before)

    def test_build_pdfium_syncs_once_before_building_all_arches(self):
        events = []
        arches = [Arch.arm64, Arch.x86]
        with (
            mock.patch.object(
                pdfium_source, "load_patchset", side_effect=lambda: events.append("load") or self.config,
            ) as load,
            mock.patch.object(
                pdfium_source, "depot_tools_env", side_effect=lambda root: events.append("depot") or self.env,
            ),
            mock.patch.object(
                pdfium_source, "sync_pdfium",
                side_effect=lambda root, env, config: events.append("sync") or self.source,
            ) as sync,
            mock.patch.object(
                pdfium_source, "build_arch",
                side_effect=lambda source, arch, env, config: events.append(arch),
            ) as build,
        ):
            pdfium_source.build_pdfium_from_source(arches)
        load.assert_called_once_with()
        sync.assert_called_once_with(Path(pdfium_source.__file__).resolve().parent, self.env, self.config)
        self.assertEqual(events, ["load", "depot", "sync", *arches])
        self.assertEqual(build.call_args_list, [
            mock.call(self.source, arch, self.env, self.config) for arch in arches
        ])
        self.assertTrue(all(call.args[-1] is self.config for call in [sync.call_args, *build.call_args_list]))

    def test_build_keeps_one_profile_snapshot_when_first_gn_changes_manifest(self):
        original_args = list(self.manifest["gn_args"])
        generations = []

        def command(command, cwd):
            if command[0] == "gclient":
                self.fake_gclient(command, cwd)
            elif command[0] == "gn":
                generations.append(cwd / command[2] / "args.gn")
                if len(generations) == 1:
                    self.manifest["gn_args"].append("changed_between_arches = true")
                    self.write_manifest()
            else:
                self.assertEqual(command[0], "ninja")

        self.external_command = command
        with (
            mock.patch.object(pdfium_source, "__file__", str(self.checkout_root / "pdfium_source.py")),
            mock.patch.object(pdfium_source, "depot_tools_env", return_value=self.env),
            mock.patch.object(pdfium_source, "install_lib"),
            mock.patch.object(pdfium_source, "load_patchset", wraps=pdfium_source.load_patchset) as load,
        ):
            pdfium_source.build_pdfium_from_source([Arch.arm64, Arch.x86])
        self.assertEqual(len(generations), 2)
        for path, cpu in zip(generations, ("arm64", "x86")):
            self.assertEqual(path.read_text().splitlines(), original_args + [f'target_cpu = "{cpu}"'])
        load.assert_called_once_with()

    def test_build_arch_uses_manifest_gn_settings_target_cpu_and_six_ninja_jobs(self):
        self.external_command = lambda command, cwd: self.assertIn(command[0], ("gn", "ninja"))
        with mock.patch.object(pdfium_source, "install_lib") as install:
            pdfium_source.build_arch(self.source, Arch.arm64, self.env, self.config)
        gn, ninja = self.commands
        self.assertEqual(gn[0][0:2], ["gn", "gen"])
        self.assertEqual(gn[1], self.source)
        out_dir = self.source / gn[0][2]
        self.assertEqual(
            (out_dir / "args.gn").read_text().splitlines(),
            self.manifest["gn_args"] + ['target_cpu = "arm64"'],
        )
        self.assertEqual(ninja[0][0], "ninja")
        self.assertEqual(ninja[1], self.source)
        self.assertIn("-j6", ninja[0])
        self.assertEqual(self.source / ninja[0][ninja[0].index("-C") + 1], out_dir)
        self.assertIn("pdfium", ninja[0])
        install.assert_called_once()
        self.assertEqual(Path(install.call_args.args[0]), out_dir)
        self.assertEqual(install.call_args.args[1], Arch.arm64)

    def test_install_lib_is_atomic_and_previous_library_survives_copy_failure(self):
        out_dir = self.root / "out"
        out_dir.mkdir()
        built = out_dir / "libpdfium.so"
        built.write_bytes(b"new library")
        destination = self.root / "installed/libmodpdfium.so"
        destination.parent.mkdir()
        destination.write_bytes(b"previous library")
        real_replace = os.replace

        def replace(temporary, target):
            self.assertEqual(Path(target), destination)
            self.assertEqual(Path(temporary).parent, destination.parent)
            self.assertNotIn(Path(temporary), (built, destination))
            self.assertEqual(destination.read_bytes(), b"previous library")
            self.assertEqual(Path(temporary).read_bytes(), b"new library")
            return real_replace(temporary, target)

        def fail_copy(source, target, *args, **kwargs):
            Path(target).write_bytes(b"partial copy")
            raise OSError("injected copy failure")

        with mock.patch.object(pdfium_source, "get_lib_path", return_value=str(destination)):
            with self.subTest(operation="atomic replacement"):
                with mock.patch.object(os, "replace", side_effect=replace) as replacement:
                    pdfium_source.install_lib(out_dir, Arch.arm64)
                replacement.assert_called_once()
                self.assertEqual(destination.read_bytes(), b"new library")
            with self.subTest(operation="copy failure"):
                destination.write_bytes(b"previous library")
                before = self.snapshot(destination.parent)
                with (
                    mock.patch.object(shutil, "copyfile", side_effect=fail_copy),
                    mock.patch.object(os, "replace", wraps=real_replace) as replacement,
                ):
                    with self.assertRaises(REJECTION):
                        pdfium_source.install_lib(out_dir, Arch.arm64)
                replacement.assert_not_called()
                self.assertEqual(self.snapshot(destination.parent), before)


if __name__ == "__main__":
    unittest.main()
