import argparse
import gzip
import hashlib
import os
import re
import shutil
import sqlite3
import sys
import tarfile
from pathlib import Path

from build_dependencies.common import download_file

parser = argparse.ArgumentParser()
parser.add_argument("--fresh", action="store_true", help="Redownload WordNet and rebuild the database from scratch.")
args = parser.parse_args()

DICTIONARY_VERSION = 1
WORDNET_URL = "https://wordnetcode.princeton.edu/wn3.1.dict.tar.gz"
WORDNET_SHA256 = "3f7d8be8ef6ecc7167d39b10d66954ec734280b5bdcd57f7d9eafe429d11c22a"
WORK_DIR = Path("dist/wordnet")
DICT_DIR = WORK_DIR / "dict"
TARBALL = WORK_DIR / "wn3.1.dict.tar.gz"
OUT_DB = Path("dist") / f"mj-pdf-dictionary-en-{DICTIONARY_VERSION}.db"
OUT_GZ = Path(str(OUT_DB) + ".gz")
INSTALLER_KT = Path("app/src/main/java/com/gitlab/mudlej/MjPdfReader/data/translation/DictionaryInstaller.kt")
UPLOAD_URL = (
    "https://gitlab.com/api/v4/projects/mudlej_android%2Fmj_pdf_reader"
    f"/packages/generic/dictionary/{DICTIONARY_VERSION}/{OUT_GZ.name}"
)

POS_FILES = {
    "n": ("index.noun", "data.noun", "noun.exc"),
    "v": ("index.verb", "data.verb", "verb.exc"),
    "a": ("index.adj", "data.adj", "adj.exc"),
    "r": ("index.adv", "data.adv", "adv.exc"),
}

ADJ_MARKER = re.compile(r"\((a|p|ip)\)$")


def log(msg):
    print("* " + msg)


def error(msg):
    log("Error !!! " + msg)
    sys.exit(1)


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch_wordnet():
    WORK_DIR.mkdir(parents=True, exist_ok=True)
    if args.fresh and DICT_DIR.exists():
        shutil.rmtree(DICT_DIR)
    if args.fresh and TARBALL.exists():
        TARBALL.unlink()
    if TARBALL.exists() and sha256(TARBALL) != WORDNET_SHA256:
        log("Discarding cached WordNet archive with an invalid SHA-256.")
        TARBALL.unlink()
    if not TARBALL.exists():
        download_file(WORDNET_URL, TARBALL, sha256=WORDNET_SHA256)
    else:
        log("Using cached " + str(TARBALL))
    if not DICT_DIR.exists():
        log("Extracting " + str(TARBALL))
        with tarfile.open(TARBALL) as tar:
            tar.extractall(WORK_DIR)
    if not (DICT_DIR / "index.noun").exists():
        error("WordNet extraction failed, expected " + str(DICT_DIR / "index.noun"))


def parse_data(path):
    synsets = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("  "):
                continue
            head, _, gloss = line.partition("|")
            fields = head.split()
            offset = fields[0]
            word_count = int(fields[3], 16)
            words = []
            for i in range(word_count):
                raw = fields[4 + i * 2]
                words.append(ADJ_MARKER.sub("", raw).replace("_", " "))
            synsets[offset] = (words, gloss.strip())
    return synsets


def split_gloss(gloss):
    definition_parts = []
    examples = []
    for part in (p.strip() for p in gloss.split("; ")):
        if part.startswith('"'):
            examples.append(part.strip('"'))
        else:
            definition_parts.append(part)
    definition = "; ".join(definition_parts).strip()
    example = examples[0] if examples else None
    return definition, example


def build_database():
    if OUT_DB.exists():
        OUT_DB.unlink()
    con = sqlite3.connect(OUT_DB)
    cur = con.cursor()
    cur.execute(
        "CREATE TABLE senses (lemma TEXT NOT NULL, pos TEXT NOT NULL, "
        "senseNumber INTEGER NOT NULL, definition TEXT NOT NULL, "
        "example TEXT, synonyms TEXT)"
    )
    cur.execute(
        "CREATE TABLE exceptions (inflected TEXT NOT NULL, pos TEXT NOT NULL, "
        "lemma TEXT NOT NULL)"
    )
    cur.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

    sense_rows = 0
    lemmas = set()
    for pos, (index_file, data_file, exc_file) in POS_FILES.items():
        log("Converting " + data_file)
        synsets = parse_data(DICT_DIR / data_file)
        with open(DICT_DIR / index_file, encoding="utf-8") as f:
            for line in f:
                if line.startswith("  "):
                    continue
                fields = line.split()
                lemma = fields[0]
                if "_" in lemma:
                    continue
                pointer_count = int(fields[3])
                offsets = fields[6 + pointer_count:]
                for sense_number, offset in enumerate(offsets, start=1):
                    words, gloss = synsets[offset]
                    definition, example = split_gloss(gloss)
                    if not definition:
                        continue
                    synonyms = ", ".join(
                        w for w in dict.fromkeys(words) if w.lower() != lemma
                    ) or None
                    cur.execute(
                        "INSERT INTO senses VALUES (?, ?, ?, ?, ?, ?)",
                        (lemma, pos, sense_number, definition, example, synonyms),
                    )
                    sense_rows += 1
                lemmas.add(lemma)

        with open(DICT_DIR / exc_file, encoding="utf-8") as f:
            for line in f:
                fields = line.split()
                inflected = fields[0]
                if "_" in inflected:
                    continue
                for lemma in fields[1:]:
                    if "_" in lemma:
                        continue
                    cur.execute(
                        "INSERT INTO exceptions VALUES (?, ?, ?)",
                        (inflected, pos, lemma),
                    )

    cur.execute("CREATE INDEX idx_senses_lemma ON senses (lemma)")
    cur.execute("CREATE INDEX idx_exceptions_inflected ON exceptions (inflected)")
    cur.executemany(
        "INSERT INTO meta VALUES (?, ?)",
        [
            ("schemaVersion", "1"),
            ("language", "en"),
            ("source", "Princeton WordNet 3.1"),
            ("license", "WordNet 3.0 license, Princeton University"),
        ],
    )
    con.commit()
    cur.execute("VACUUM")
    con.close()
    log(f"Lemmas: {len(lemmas)}  Senses: {sense_rows}")


def compress_database():
    log("Compressing " + str(OUT_DB))
    with open(OUT_DB, "rb") as src, open(OUT_GZ, "wb") as out:
        with gzip.GzipFile(filename="", mode="wb", fileobj=out, compresslevel=9, mtime=0) as dst:
            shutil.copyfileobj(src, dst)


def check_app_constants(gz_hash):
    if not INSTALLER_KT.exists():
        log("Skipping app constant check, missing " + str(INSTALLER_KT))
        return
    source = INSTALLER_KT.read_text(encoding="utf-8")
    checks = [
        ("expectedSha256", f'"{gz_hash}"'),
        ("downloadSizeBytes", format(OUT_GZ.stat().st_size, "_d") + "L"),
        ("installedSizeBytes", format(OUT_DB.stat().st_size, "_d") + "L"),
    ]
    mismatches = [name for name, value in checks if f"{name} = {value}" not in source]
    if mismatches:
        log("WARNING: update these constants in " + str(INSTALLER_KT) + ":")
        for name in mismatches:
            log("  " + name)
        log(f"  expectedSha256 = {gz_hash}")
        log(f"  downloadSizeBytes = {format(OUT_GZ.stat().st_size, '_d')}L")
        log(f"  installedSizeBytes = {format(OUT_DB.stat().st_size, '_d')}L")
        log("The in-app download fails its integrity check until they match the uploaded file.")
    else:
        log("App constants in DictionaryInstaller.kt match this build.")


log("Start " + __file__)
fetch_wordnet()
build_database()
compress_database()
gz_hash = sha256(OUT_GZ)
log(f"Database: {OUT_DB} ({OUT_DB.stat().st_size} bytes)")
log(f"Package:  {OUT_GZ} ({OUT_GZ.stat().st_size} bytes)")
log("SHA-256:  " + gz_hash)
check_app_constants(gz_hash)
log("Upload with:")
log(f'  curl --header "PRIVATE-TOKEN: <token>" --upload-file {OUT_GZ} "{UPLOAD_URL}"')
