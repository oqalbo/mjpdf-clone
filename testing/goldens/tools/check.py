#!/usr/bin/env python3
"""Validate the approved golden profile and, optionally, its test-APK packaging."""

from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import re
import struct
import sys
import zipfile
import zlib
from datetime import date, datetime, timezone
from pathlib import Path


REPO = Path(__file__).resolve().parents[3]
GOLDENS = REPO / "testing/goldens"
PROFILE = "api36-1080x2400-440dpi-3button-en-US-swiftshader"
PROFILE_DIR = GOLDENS / "baselines" / PROFILE
MANIFEST_PATH = PROFILE_DIR / "manifest.json"
BASELINE_SOURCE = REPO / "app/src/androidTest/java/com/gitlab/mudlej/MjPdfReader/suite/baseline"
TLCL_SOURCE = REPO / "testing/fixtures/assets/TLCL-25.12A.pdf"
TLCL_OUTPUT = REPO / "testing/fixtures/out/TLCL-25.12A.pdf"
TLCL_SIZE = 6_519_800
TLCL_SHA256 = "c6c564db237ea1d5f5758c5a0df5d8c804041ef483585259687d04d9ecf2cd05"
QUOTE_THEMES = ("light", "dark", "sepia", "rose", "lavender", "mint")
TEXT_THEMES = ("system", "light", "sepia", "dark", "black", "dracula")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def decode_png(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    require(data.startswith(b"\x89PNG\r\n\x1a\n"), f"{path.name} has no PNG signature")
    offset = 8
    width = height = channels = None
    compressed = bytearray()
    saw_end = False
    while offset < len(data):
        require(offset + 12 <= len(data), f"{path.name} has a truncated PNG chunk")
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_end = offset + 12 + length
        require(chunk_end <= len(data), f"{path.name} has a truncated {chunk_type!r} chunk")
        chunk = data[offset + 8 : offset + 8 + length]
        expected_crc = struct.unpack(">I", data[offset + 8 + length : chunk_end])[0]
        actual_crc = binascii.crc32(chunk_type + chunk) & 0xFFFFFFFF
        require(actual_crc == expected_crc, f"{path.name} has a bad {chunk_type!r} CRC")
        if chunk_type == b"IHDR":
            require(length == 13 and width is None, f"{path.name} has an invalid IHDR")
            width, height, depth, colour, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", chunk
            )
            require(depth == 8 and colour in (2, 6), f"{path.name} must be 8-bit RGB or RGBA")
            require(compression == filtering == interlace == 0, f"{path.name} uses unsupported PNG encoding")
            channels = 3 if colour == 2 else 4
        elif chunk_type == b"IDAT":
            compressed.extend(chunk)
        elif chunk_type == b"IEND":
            require(length == 0, f"{path.name} has an invalid IEND")
            saw_end = True
            require(chunk_end == len(data), f"{path.name} has bytes after IEND")
            break
        offset = chunk_end
    if width is None or height is None or channels is None:
        raise RuntimeError(f"{path.name} has no IHDR")
    require(saw_end and len(compressed) > 0, f"{path.name} has incomplete PNG image data")
    pixels = zlib.decompress(bytes(compressed))
    row_bytes = width * channels
    require(len(pixels) == height * (row_bytes + 1), f"{path.name} has the wrong decoded data length")
    require(
        all(pixels[row * (row_bytes + 1)] <= 4 for row in range(height)),
        f"{path.name} has an invalid PNG row filter",
    )
    return width, height


def source_inventory() -> set[str]:
    source = "\n".join(
        path.read_text(encoding="utf-8") for path in sorted(BASELINE_SOURCE.glob("*.kt"))
    )
    names = {
        name
        for name in re.findall(r'goldenName\s*=\s*"([^"]+\.png)"', source)
        if "${theme.name.lowercase()}" not in name
    }
    names.update(f"VIS-43-quote-card-{theme}.png" for theme in QUOTE_THEMES)
    names.update(f"VIS-50-text-mode-{theme}.png" for theme in TEXT_THEMES)
    return names


def check_profile() -> set[str]:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    require(manifest.get("schema") == "mjpdf-rendering-golden-profile", "wrong manifest schema")
    require(manifest.get("schemaVersion") == 1, "wrong manifest schemaVersion")
    require(manifest.get("approvalStatus") == "approved", "profile is not approved")
    try:
        approval_date = date.fromisoformat(manifest.get("approvalDate", ""))
    except (TypeError, ValueError) as error:
        raise RuntimeError("profile requires a valid approval date") from error
    require(date(2026, 9, 17) <= approval_date <= datetime.now(timezone.utc).date(), "approval date is outside the profile history")
    require(manifest.get("profile") == PROFILE, "manifest profile does not match its directory")
    require(manifest.get("count") == 62, "manifest must declare exactly 62 images")

    records = manifest.get("images")
    require(isinstance(records, list) and len(records) == 62, "manifest must contain 62 image records")
    names = [record.get("filename") for record in records]
    require(names == sorted(names), "manifest image records must be sorted by filename")
    require(len(set(names)) == 62, "manifest image filenames must be distinct")

    expected_files = set(names) | {"manifest.json"}
    actual_files = {path.name for path in PROFILE_DIR.iterdir() if path.is_file()}
    require(actual_files == expected_files, f"profile file mismatch: expected={expected_files - actual_files}, extra={actual_files - expected_files}")
    require(not any(path.is_symlink() for path in PROFILE_DIR.iterdir()), "profile must not contain symlinks")

    for record in records:
        path = PROFILE_DIR / record["filename"]
        dimensions = decode_png(path)
        require(
            dimensions == (record.get("width"), record.get("height")),
            f"{path.name} dimensions are {dimensions}, manifest declares "
            f"{record.get('width')}x{record.get('height')}",
        )
        require(sha256(path) == record.get("sha256"), f"{path.name} SHA-256 mismatch")

    source_names = source_inventory()
    require(source_names == set(names), f"source inventory mismatch: missing={set(names) - source_names}, extra={source_names - set(names)}")

    source = manifest.get("source", {})
    require(source.get("sizeBytes") == TLCL_SIZE, "manifest TLCL size is wrong")
    require(source.get("sha256") == TLCL_SHA256, "manifest TLCL SHA-256 is wrong")
    require(source.get("pageCount") == 597, "manifest TLCL page count is wrong")
    for path in (TLCL_SOURCE, TLCL_OUTPUT):
        require(path.stat().st_size == TLCL_SIZE, f"{path} size mismatch")
        require(sha256(path) == TLCL_SHA256, f"{path} SHA-256 mismatch")

    return set(names)


def check_apk(apk: Path, names: set[str]) -> None:
    require(apk.is_file(), f"test APK does not exist: {apk}")
    prefix = f"assets/baselines/{PROFILE}/"
    expected = {prefix + "manifest.json"} | {prefix + name for name in names}
    with zipfile.ZipFile(apk) as archive:
        archived = {name for name in archive.namelist() if not name.endswith("/")}
        actual = {name for name in archived if name.startswith(prefix)}
        require(actual == expected, f"test APK profile mismatch: missing={expected - actual}, extra={actual - expected}")
        require(archive.read(prefix + "manifest.json") == MANIFEST_PATH.read_bytes(), "test APK manifest differs from durable source")
        forbidden = {
            name
            for name in archived
            if name.startswith("assets/reviews/")
            or name.startswith("assets/tools/")
            or name == "assets/README.md"
        }
        require(not forbidden, f"governance files leaked into test APK: {sorted(forbidden)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, help="also validate an assembled androidTest APK")
    args = parser.parse_args()
    try:
        names = check_profile()
        if args.apk:
            check_apk(args.apk.resolve(), names)
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as error:
        print(f"golden check FAILED: {error}", file=sys.stderr)
        return 1
    scope = "profile and APK" if args.apk else "profile"
    print(f"golden check PASS: {scope}; 62 approved PNGs; TLCL source/output bytes verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
