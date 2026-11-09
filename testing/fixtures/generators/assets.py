"""Committed source material, copied into the output tree.

A few fixtures cannot honestly be generated. A real scanned page carries sensor
noise, skew, dust and JPEG ringing that no procedural raster reproduces, and a
real world paper carries a layout nobody would think to write down. Those are
committed under `assets/` and copied into `out/` from here, so that every
fixture, generated or committed, ends up in one directory under one manifest.

The directory currently includes the permanent TLCL rendering-baseline book.
`assets/README.md` records why it is committed and the terms under which it may
be redistributed unchanged.
"""

import hashlib
import os
import shutil

from lib import manifest
from lib.registry import fixture_group, out_path

ASSET_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                         "assets")

# Files that document the directory rather than being fixtures in it.
SKIPPED = {"README.md", ".gitkeep", ".gitignore"}

# Why a given asset is committed rather than generated. A name that is not
# listed still gets copied, with the generic reason, because a fixture missing
# from the manifest is worse than a fixture with a vague description.
REASONS = {
    "TLCL-25.12A.pdf":
        "the permanent real-world golden book, whose authored 597-page layout, "
        "typography, tables, figures and code samples cannot be reproduced by "
        "the fixture generator",
    "scanned-real.pdf":
        "a genuine scanner output, with the sensor noise, skew, dust and JPEG "
        "ringing that a procedural raster cannot reproduce",
    "paper-real.pdf":
        "a real world scientific paper, whose two column layout, figures, "
        "tables and reference list exercise a structure nobody would invent",
}
GENERIC_REASON = ("committed source material, which means it cannot be "
                  "reproduced from a script in this directory")


def _asset_files():
    """Every committed asset, in sorted order, with its path under assets/."""
    found = []
    if not os.path.isdir(ASSET_DIR):
        return found
    for dirpath, dirnames, filenames in os.walk(ASSET_DIR):
        dirnames.sort()
        for fn in sorted(filenames):
            if fn in SKIPPED or fn.startswith("."):
                continue
            src = os.path.join(dirpath, fn)
            rel = os.path.relpath(src, ASSET_DIR)
            found.append((rel, src))
    return sorted(found)


@fixture_group("assets", "Committed source material that cannot be generated")
def build(root):
    for rel, src in _asset_files():
        name = os.path.basename(rel)
        data = open(src, "rb").read()
        if rel == "TLCL-25.12A.pdf":
            expected_size = 6519800
            expected_sha256 = "c6c564db237ea1d5f5758c5a0df5d8c804041ef483585259687d04d9ecf2cd05"
            actual_sha256 = hashlib.sha256(data).hexdigest()
            if len(data) != expected_size or actual_sha256 != expected_sha256:
                raise ValueError(
                    "TLCL-25.12A.pdf must remain the exact unchanged 25.12A download: "
                    f"expected {expected_size} bytes and SHA-256 {expected_sha256}, "
                    f"found {len(data)} bytes and {actual_sha256}"
                )
        dest = out_path(root, *rel.split(os.sep))
        shutil.copyfile(src, dest)
        manifest.register(
            rel.replace(os.sep, "/"), dest,
            f"Committed asset, not generated: {REASONS.get(name, GENERIC_REASON)}",
            committed_asset=True,
            source=os.path.join("assets", rel),
            bytes=len(data),
            sha256=hashlib.sha256(data).hexdigest(),
            note="regenerating the fixture tree copies this file, it does not "
                 "rebuild it, so a golden recorded against it stays valid until "
                 "the committed bytes change",
        ).tag("assets")
