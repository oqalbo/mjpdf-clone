#!/usr/bin/env python3
"""Generate every test fixture for the MJ PDF Reader end to end suite.

    python3 generate.py                 build everything
    python3 generate.py --list          show the groups without building
    python3 generate.py --only core     build one group (repeatable)
    python3 generate.py --skip-slow     skip the multi hundred megabyte fixtures
    python3 generate.py --verify        rebuild twice and prove byte determinism
    python3 generate.py --check         validate every produced PDF parses

Output goes to ./out, which is gitignored. Committed source material that cannot
be generated lives in ./assets and is copied into place by the assets group.
"""

import argparse
import hashlib
import importlib
import os
import shutil
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from lib import manifest, registry  # noqa: E402

GENERATOR_MODULES = [
    "generators.core",
    "generators.structural",
    "generators.text_and_search",
    "generators.approved_arabic",
    "generators.annotations",
    "generators.malformed",
    "generators.security",
    "generators.library",
    "generators.backups",
    "generators.perf_and_memory",
    "generators.assets",
]


def load_generators():
    for mod in GENERATOR_MODULES:
        importlib.import_module(mod)


def build(out_root, only=None, skip_slow=False):
    manifest.reset()
    groups = registry.groups()
    if only:
        unknown = set(only) - set(groups)
        if unknown:
            sys.exit(f"unknown group(s): {', '.join(sorted(unknown))}\n"
                     f"available: {', '.join(groups)}")
        groups = {k: v for k, v in groups.items() if k in only}
    if skip_slow:
        groups = {k: v for k, v in groups.items() if not v["slow"]}

    for name, g in groups.items():
        started = time.time()
        print(f"  {name} ...", end="", flush=True)
        g["fn"](out_root)
        print(f" {time.time() - started:.1f}s")
    return groups


def tree_hash(root):
    h = hashlib.sha256()
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort()
        for fn in sorted(filenames):
            if fn in ("MANIFEST.json", "MANIFEST.md"):
                continue
            p = os.path.join(dirpath, fn)
            h.update(os.path.relpath(p, root).encode())
            with open(p, "rb") as fh:
                for chunk in iter(lambda: fh.read(1 << 20), b""):
                    h.update(chunk)
    return h.hexdigest()


def check_pdfs(root):
    try:
        import pikepdf
    except ImportError:
        print("  pikepdf not installed, skipping structural check")
        return 0, 0
    ok = bad = 0
    records = manifest.all_records()
    passwords = {r.path: r.facts.get("password") for r in records.values()}
    expect_broken = {r.path for r in records.values() if "malformed" in r.tags}
    for dirpath, _, filenames in os.walk(root):
        for fn in sorted(filenames):
            if not fn.lower().endswith(".pdf"):
                continue
            p = os.path.join(dirpath, fn)
            if p in expect_broken:
                continue
            try:
                pikepdf.open(p, password=passwords.get(p) or "").close()
                ok += 1
            except Exception as exc:
                bad += 1
                print(f"    INVALID {os.path.relpath(p, root)}: {exc}")
    return ok, bad


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=os.path.join(HERE, "out"))
    ap.add_argument("--only", action="append", default=None)
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--skip-slow", action="store_true")
    ap.add_argument("--verify", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--clean", action="store_true")
    args = ap.parse_args()

    load_generators()

    if args.list:
        print("Fixture groups:")
        for name, g in registry.groups().items():
            slow = "  [slow]" if g["slow"] else ""
            desc = (g["description"] or "").strip().splitlines()
            print(f"  {name:<18}{slow} {desc[0] if desc else ''}")
        return 0

    if args.clean and os.path.isdir(args.out):
        shutil.rmtree(args.out)
    os.makedirs(args.out, exist_ok=True)

    print(f"Building fixtures into {args.out}")
    build(args.out, only=args.only, skip_slow=args.skip_slow)
    count = manifest.write(args.out)
    print(f"  manifest: {count} fixtures recorded")

    if args.check:
        print("Checking every PDF parses")
        ok, bad = check_pdfs(args.out)
        print(f"  {ok} valid, {bad} invalid")
        if bad:
            return 1

    if args.verify:
        # Build both trees fresh into temp locations so the result never depends
        # on whatever state out/ happens to be in. Comparing against a dirty out/
        # (for example one that still holds slow fixtures from an earlier run)
        # would report a false mismatch.
        print("Verifying byte determinism (two fresh builds)")
        hashes = []
        for tag in ("a", "b"):
            alt = args.out.rstrip("/") + f"-verify-{tag}"
            if os.path.isdir(alt):
                shutil.rmtree(alt)
            os.makedirs(alt, exist_ok=True)
            build(alt, only=args.only, skip_slow=args.skip_slow)
            hashes.append(tree_hash(alt))
            shutil.rmtree(alt)
        if hashes[0] == hashes[1]:
            print(f"  deterministic: {hashes[0][:16]}")
        else:
            print(f"  NOT deterministic\n    {hashes[0]}\n    {hashes[1]}")
            return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
