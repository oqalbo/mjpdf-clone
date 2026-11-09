"""Backup and restore JSON fixtures, plus the two non JSON stand ins.

Every member name here comes from `BackupCodec.kt`, every setting key and kind
from `Preferences.backupSettingKinds`, and every enum value from
`Preferences.backupSettingEnumDomains`. Nothing is invented: a key the app does
not know is skipped as unknown, so a made up name would silently turn an "out of
domain enum" fixture into an "unknown key" fixture and leave the branch it was
built for untested.

Record hashes use the `h2` prefixed identity form. They are computed the way
`DocumentIdentity` computes one, over a fixed byte string per record rather than
over a real fixture file, because this group runs before the groups that produce
PDFs and must not depend on their output. That means a restored record is well
formed and matches the app's own hash format but does not point at a file on the
device. Tests that need a record to match a real file rekey it at seed time.

`bad-settings.json` follows `backup-and-restore.md`, which corrects the earlier
inventory wording: the out of domain enum entry uses `tapToTurnPages` with the
value `CORNERS`. The earlier `theme` with `purple` named a key that does not
exist in `Preferences` at all, so it was skipped as an unknown key and left the
enum domain branch uncovered.

Byte determinism comes from `json.dumps` with `sort_keys=True` and fixed
separators, and from fixed dates everywhere.
"""

import hashlib
import json
import os

from lib import manifest
from lib.registry import fixture_group, out_path

SCHEMA_VERSION = 1
APP_VERSION_CODE = 58
EXPORTED_AT = "2026-01-15T09:30:00Z"
LAST_OPENED = "2026-01-14T21:05:33"
CREATED_AT = "2026-01-10T08:00:00"

DICTIONARY_BYTES = 1024 * 1024
CORRUPT_BYTES = 400


def _identity(label):
    """The h2 identity the app would compute for a short file of these bytes.

    `DocumentIdentity` digests the head, then the rest of the file for anything
    inside the 2 MB whole file limit, then the eight byte big endian length.
    """
    payload = f"mj-pdf fixture record {label}\n".encode("ascii")
    digester = hashlib.md5()
    digester.update(payload)
    digester.update(len(payload).to_bytes(8, "big"))
    return "h2" + digester.hexdigest()


def _legacy_hash(label):
    """A plain lowercase hex digest, which is what the legacy sniffer looks for."""
    return hashlib.md5(f"mj-pdf legacy record {label}".encode("ascii")).hexdigest()


HASH_ONE = _identity("one")
HASH_TWO = _identity("two")
HASH_THREE = _identity("three")
HASH_DIRTY = _identity("dirty")
HASH_MISSING_ONE = _identity("missing-one")
HASH_MISSING_TWO = _identity("missing-two")
LEGACY_HASH_ONE = _legacy_hash("one")
LEGACY_HASH_TWO = _legacy_hash("two")
LEGACY_H2_ONE = _identity("legacy-h2-one")
LEGACY_H2_TWO = _identity("legacy-h2-two")

MISSING_URI_ONE = ("content://com.android.externalstorage.documents/document/"
                   "primary%3ADownload%2Fgone-one.pdf")
MISSING_URI_TWO = ("content://com.android.externalstorage.documents/document/"
                   "primary%3ADownload%2Fgone-two.pdf")


def _dump(obj, minified=False):
    if minified:
        return json.dumps(obj, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=False)
    return json.dumps(obj, sort_keys=True, indent=2, separators=(",", ": "),
                      ensure_ascii=False) + "\n"


def _write(root, name, text, description, **facts):
    path = out_path(root, "backups", name)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    return manifest.register(name, path, description, **facts).tag("backups")


def _setting(key, kind, value=None, values=None):
    entry = {"key": key, "type": kind}
    if values is not None:
        entry["values"] = values
    else:
        entry["value"] = value
    return entry


def _record(hash_value, page_number, file_name, **overrides):
    record = {
        "hash": hash_value,
        "pageNumber": page_number,
        "length": 20,
        "fileName": file_name,
        "lastOpened": LAST_OPENED,
        "reading": "READING",
        "favorite": False,
        "cropMarginsVersion": 0,
        "hidden": False,
        "uri": f"file:///storage/emulated/0/Download/{file_name}",
        "documentTitle": file_name[:-4].replace("-", " ").title(),
    }
    record.update(overrides)
    return record


def _bookmark(file_hash, page_index, label, sort_order):
    return {
        "fileHash": file_hash,
        "pageIndex": page_index,
        "label": label,
        "createdAt": CREATED_AT,
        "sortOrder": sort_order,
    }


def _envelope(**members):
    envelope = {"schemaVersion": SCHEMA_VERSION,
                "appVersionCode": APP_VERSION_CODE,
                "exportedAt": EXPORTED_AT}
    envelope.update(members)
    return envelope


# The five kinds a backup can round trip. The long kind is deliberately absent:
# all three long keys are in BackupManager.excludedSettingKeys, so no long
# setting can survive an export, which BKP-09 asserts.
KNOWN_SETTINGS = [
    _setting("highQuality", "boolean", "true"),
    _setting("hideDelay", "int", "5000"),
    _setting("maxZoom", "float", "25.0"),
    _setting("homeViewMode", "string", "LIST"),
    _setting("shortcutBarActions", "stringSet",
             values=["search", "go_to_page", "bookmark_page"]),
]

RECORD_ONE = _record(HASH_ONE, 41, "normal.pdf", favorite=True,
                     cropMargins="0.1,0.1,0.1,0.1", cropMarginsVersion=3,
                     autoScrollSpeed=7, readingDirectionOverride="RTL",
                     detectedReadingDirection="LTR",
                     documentTitle="Normal Document")
RECORD_TWO = _record(HASH_TWO, 3, "hist-beta.pdf", reading="TO_READ",
                     documentTitle="Beta Document")
RECORD_THREE = _record(HASH_THREE, 0, "arabic-rtl.pdf", reading="COMPLETED",
                       detectedReadingDirection="RTL",
                       documentTitle="Arabic Document")

BOOKMARKS_THREE = [
    _bookmark(HASH_ONE, 4, "First mark", 0),
    _bookmark(HASH_ONE, 12, "Second mark", 1),
    _bookmark(HASH_TWO, 7, "Beta mark", 0),
]


@fixture_group("backups", "Backup JSON fixtures and the two binary stand ins")
def build(root):
    _valid(root)
    _legacy(root)
    _schema_edges(root)
    _rejected(root)
    _settings_surface(root)
    _binaries(root)


def _valid(root):
    payload = _envelope(settings=KNOWN_SETTINGS,
                        pdfRecords=[RECORD_ONE, RECORD_TWO],
                        userBookmarks=BOOKMARKS_THREE)
    _write(root, "valid-full.json", _dump(payload),
           "Canonical backup: schemaVersion 1, five known settings across five "
           "kinds, two records with h2 hashes, three bookmarks across both",
           schemaVersion=1, settings=len(KNOWN_SETTINGS), records=2, bookmarks=3,
           applied_settings=5, skipped_settings=0,
           record_hashes=[HASH_ONE, HASH_TWO],
           bookmark_pages=[4, 12, 7],
           includes_history=True,
           setting_keys=[s["key"] for s in KNOWN_SETTINGS])

    payload = _envelope(settings=KNOWN_SETTINGS)
    _write(root, "valid-settings-only.json", _dump(payload),
           "Settings only, with no pdfRecords and no userBookmarks members at "
           "all, so includesHistory is false and no wipe warning is shown",
           schemaVersion=1, settings=len(KNOWN_SETTINGS),
           applied_settings=5, skipped_settings=0,
           includes_history=False,
           members=["schemaVersion", "appVersionCode", "exportedAt", "settings"])

    payload = _envelope(pdfRecords=[RECORD_ONE, RECORD_TWO],
                        userBookmarks=BOOKMARKS_THREE)
    _write(root, "valid-history-only.json", _dump(payload),
           "History only, with no settings member, so an import wipes and "
           "restores history and touches no preference",
           schemaVersion=1, records=2, bookmarks=3, includes_history=True,
           record_hashes=[HASH_ONE, HASH_TWO],
           members=["schemaVersion", "appVersionCode", "exportedAt",
                    "pdfRecords", "userBookmarks"])

    with_password = dict(RECORD_TWO)
    with_password["password"] = "mjpdf"
    payload = _envelope(settings=KNOWN_SETTINGS,
                        pdfRecords=[RECORD_ONE, with_password],
                        userBookmarks=BOOKMARKS_THREE)
    _write(root, "valid-with-passwords.json", _dump(payload),
           "valid-full.json with a password member on the second record, which "
           "is the only difference between the two files",
           schemaVersion=1, records=2, bookmarks=3,
           password_record=HASH_TWO, password="mjpdf",
           records_with_password=1, includes_history=True)


def _legacy(root):
    # Legacy member names, a through t at record level. The sniffer accepts the
    # file only when exportedAt is a string and the first record hash is plain
    # hex of 32 or more characters, so these two hashes carry no h2 prefix.
    payload = {
        "a": APP_VERSION_CODE,
        "b": EXPORTED_AT,
        "c": [
            {"a": "highQuality", "b": "boolean", "c": "true"},
            {"a": "hideDelay", "b": "int", "c": "5000"},
            {"a": "shortcutBarActions", "b": "stringSet",
             "d": ["search", "go_to_page", "bookmark_page"]},
        ],
        "d": [
            {"a": LEGACY_HASH_ONE, "b": 12, "c": 20, "d": "legacy-one.pdf",
             "f": LAST_OPENED, "g": "READING", "h": False, "j": 0, "s": False,
             "o": "file:///storage/emulated/0/Download/legacy-one.pdf"},
            {"a": LEGACY_HASH_TWO, "b": 2, "c": 8, "d": "legacy-two.pdf",
             "f": LAST_OPENED, "g": "TO_READ", "h": True, "j": 0, "s": False,
             "o": "file:///storage/emulated/0/Download/legacy-two.pdf"},
        ],
        "e": [
            {"a": LEGACY_HASH_ONE, "b": 5, "c": "Legacy mark", "d": CREATED_AT,
             "e": 0},
        ],
    }
    _write(root, "legacy-minified.json", _dump(payload, minified=True),
           "Legacy minified member names, no schemaVersion member, record hashes "
           "as plain lowercase hex so the legacy sniffer recognises the file",
           schemaVersion="absent, decoded as 1",
           settings=3, records=2, bookmarks=1,
           record_hashes=[LEGACY_HASH_ONE, LEGACY_HASH_TWO],
           hash_form="32 character lowercase hex, no h2 prefix",
           applied_settings=3, skipped_settings=0, includes_history=True)

    payload = {
        "a": APP_VERSION_CODE,
        "b": EXPORTED_AT,
        "d": [
            {"a": LEGACY_H2_ONE, "b": 1, "c": 6, "d": "legacy-h2-one.pdf",
             "f": LAST_OPENED, "g": "UNSET", "h": False, "j": 0, "s": False,
             "o": "file:///storage/emulated/0/Download/legacy-h2-one.pdf"},
            {"a": LEGACY_H2_TWO, "b": 0, "c": 3, "d": "legacy-h2-two.pdf",
             "f": LAST_OPENED, "g": "UNSET", "h": False, "j": 0, "s": False,
             "o": "file:///storage/emulated/0/Download/legacy-h2-two.pdf"},
        ],
    }
    _write(root, "legacy-minified-h2-only.json", _dump(payload, minified=True),
           "Legacy minified with records only, whose hashes start with h2, so "
           "the sniffer's hex hash test does not match and the file is refused "
           "as not a backup (BKP-33)",
           schemaVersion="absent", settings=0, records=2, bookmarks=0,
           record_hashes=[LEGACY_H2_ONE, LEGACY_H2_TWO],
           hash_form="h2 prefixed, which the legacy hex test rejects",
           expectation="decode fails, nothing is imported")


def _schema_edges(root):
    payload = _envelope(settings=KNOWN_SETTINGS,
                        pdfRecords=[RECORD_ONE],
                        userBookmarks=[BOOKMARKS_THREE[0]])
    payload["schemaVersion"] = 0
    _write(root, "schema-zero.json", _dump(payload),
           "Canonical shape with schemaVersion 0, refused as not valid because "
           "the parser requires a version of at least 1",
           schemaVersion=0, expectation="backup_error_not_valid")

    payload = _envelope(settings=KNOWN_SETTINGS,
                        pdfRecords=[RECORD_ONE],
                        userBookmarks=[BOOKMARKS_THREE[0]])
    payload["schemaVersion"] = 2
    _write(root, "schema-future.json", _dump(payload),
           "Canonical shape with schemaVersion 2, one above the app's own, "
           "refused with the newer version message",
           schemaVersion=2, expectation="backup_error_newer_version")

    payload = {"schemaVersion": SCHEMA_VERSION, "exportedAt": EXPORTED_AT}
    _write(root, "empty-sections.json", _dump(payload),
           "Envelope only: no settings, no pdfRecords and no userBookmarks, so "
           "the parser reports that there is nothing to import",
           schemaVersion=1, settings=0, records=0, bookmarks=0,
           includes_history=False,
           expectation="backup_error_nothing_to_import")


def _rejected(root):
    full = _dump(_envelope(settings=KNOWN_SETTINGS,
                           pdfRecords=[RECORD_ONE, RECORD_TWO],
                           userBookmarks=BOOKMARKS_THREE))
    marker = '"pdfRecords"'
    cut = full.index(marker) + len(marker) + 40
    _write(root, "truncated.json", full[:cut],
           "valid-full.json cut inside the pdfRecords array so the JSON never "
           "closes and the parser throws",
           bytes_kept=cut, cut_inside="the first object of pdfRecords",
           note="the cut falls before the schemaVersion member, which never "
                "matters because JsonParser throws first",
           expectation="backup_error_not_valid")

    # Exactly three unusable entries: one unknown key, one known key with the
    # wrong kind, and one enum key with a value outside its domain.
    bad = [
        _setting("notARealSetting", "string", "whatever"),
        _setting("horizontalScroll", "int", "1"),
        _setting("tapToTurnPages", "string", "CORNERS"),
        _setting("screenOn", "boolean", "true"),
        _setting("scrollSpeed", "int", "5"),
    ]
    _write(root, "bad-settings.json", _dump(_envelope(settings=bad)),
           "Three unusable settings and two usable ones: notARealSetting is not "
           "a known key, horizontalScroll is a boolean declared as int, and "
           "tapToTurnPages is a real enum key whose value CORNERS is outside "
           "TapToTurnZones",
           schemaVersion=1, settings=5, applied_settings=2, skipped_settings=3,
           applied_keys=["screenOn", "scrollSpeed"],
           skipped_keys=["notARealSetting", "horizontalScroll", "tapToTurnPages"],
           unknown_key="notARealSetting",
           wrong_kind="horizontalScroll declared int, real kind boolean",
           bad_enum="tapToTurnPages CORNERS, domain is LEFT_RIGHT, TOP_BOTTOM, OFF",
           includes_history=False)

    dirty_records = [
        _record("", 3, "empty-hash.pdf"),
        _record(HASH_DIRTY, 6, "bad-date.pdf", lastOpened="not a date at all"),
        _record(HASH_ONE, 9, "valid.pdf"),
    ]
    dirty_bookmarks = [
        _bookmark(HASH_ONE, -1, "Negative page", 0),
        _bookmark(HASH_ONE, 8, "Negative sort", -1),
        _bookmark(HASH_DIRTY, 2, "Valid mark", 1),
    ]
    _write(root, "dirty-records.json",
           _dump(_envelope(pdfRecords=dirty_records,
                           userBookmarks=dirty_bookmarks)),
           "One record with an empty hash, one with an unparseable lastOpened, "
           "one fully valid record, one bookmark with pageIndex -1 and one with "
           "sortOrder -1",
           schemaVersion=1, records=3, bookmarks=3,
           records_kept=2, bookmarks_kept=2,
           empty_hash_record="empty-hash.pdf, dropped by toRecords",
           unparseable_date_record=HASH_DIRTY,
           unparseable_date_value="not a date at all, becomes LocalDateTime.MIN",
           negative_page_bookmark="dropped, pageIndex -1",
           negative_sort_bookmark="kept, sortOrder falls back to pageIndex 8",
           valid_record_hash=HASH_ONE, includes_history=True)

    missing = [
        _record(HASH_MISSING_ONE, 0, "gone-one.pdf", uri=MISSING_URI_ONE),
        _record(HASH_MISSING_TWO, 0, "gone-two.pdf", uri=MISSING_URI_TWO),
    ]
    _write(root, "missing-files.json", _dump(_envelope(pdfRecords=missing)),
           "Two records whose content URIs resolve to nothing on this device, so "
           "the rows restore and then probe as unavailable",
           schemaVersion=1, records=2, bookmarks=0, includes_history=True,
           record_hashes=[HASH_MISSING_ONE, HASH_MISSING_TWO],
           uris=[MISSING_URI_ONE, MISSING_URI_TWO],
           expectation="rows restore, AvailabilityProbe reports them MISSING")


def _settings_surface(root):
    _write(root, "settings-only-backup.json",
           _dump(_envelope(settings=KNOWN_SETTINGS)),
           "Settings surface copy of a settings only backup, carrying one key of "
           "each round trippable kind (SET-71)",
           schemaVersion=1, settings=5, applied_settings=5, skipped_settings=0,
           highQuality="boolean true", hideDelay="int 5000",
           maxZoom="float 25.0", homeViewMode="string LIST",
           shortcutBarActions=["search", "go_to_page", "bookmark_page"],
           includes_history=False)

    _write(root, "full-backup.json",
           _dump(_envelope(settings=KNOWN_SETTINGS,
                           pdfRecords=[RECORD_ONE, RECORD_TWO, RECORD_THREE],
                           userBookmarks=BOOKMARKS_THREE[:2])),
           "The same settings with three records and two bookmarks, so the "
           "import takes the history wipe path and shows the countdown (SET-72)",
           schemaVersion=1, settings=5, records=3, bookmarks=2,
           applied_settings=5, skipped_settings=0, includes_history=True,
           record_hashes=[HASH_ONE, HASH_TWO, HASH_THREE])

    unknown = [
        _setting("highQuality", "boolean", "true"),
        _setting("enableRootMode", "boolean", "true"),
        _setting("pdfDarkThemeV2", "string", "dark"),
        _setting("zz_unknown", "int", "7"),
    ]
    _write(root, "unknown-keys-backup.json", _dump(_envelope(settings=unknown)),
           "One known key and three keys absent from Preferences.backupSettingKinds, "
           "so a file from a newer or modified build cannot inject keys (SET-73)",
           schemaVersion=1, settings=4, applied_settings=1, skipped_settings=3,
           applied_keys=["highQuality"],
           skipped_keys=["enableRootMode", "pdfDarkThemeV2", "zz_unknown"],
           includes_history=False)

    wrong_types = [
        _setting("highQuality", "string", "true"),
        _setting("hideDelay", "boolean", "true"),
        _setting("maxZoom", "int", "25"),
    ]
    _write(root, "wrong-types-backup.json",
           _dump(_envelope(settings=wrong_types)),
           "Three known keys whose declared type is not the kind Preferences "
           "records, so all three are skipped and keep their defaults (SET-74)",
           schemaVersion=1, settings=3, applied_settings=0, skipped_settings=3,
           skipped_keys=["highQuality", "hideDelay", "maxZoom"],
           declared_types=["highQuality string, real boolean",
                           "hideDelay boolean, real int",
                           "maxZoom int, real float"],
           defaults_kept=["highQuality false", "hideDelay 3000", "maxZoom 10.0"],
           includes_history=False)

    bad_enum = [
        _setting("homeViewMode", "string", "DIAGONAL"),
        _setting("pageFitPolicy", "string", "CONTAIN"),
        _setting("textModeTheme", "string", "NEON"),
        _setting("screenOn", "boolean", "true"),
    ]
    _write(root, "bad-enum-backup.json", _dump(_envelope(settings=bad_enum)),
           "Three enum backed keys whose values are outside their declared "
           "domains, plus one valid key that must still be applied (SET-75)",
           schemaVersion=1, settings=4, applied_settings=1, skipped_settings=3,
           applied_keys=["screenOn"],
           skipped_keys=["homeViewMode", "pageFitPolicy", "textModeTheme"],
           domains=["homeViewMode GRID or LIST",
                    "pageFitPolicy WIDTH, HEIGHT or BOTH",
                    "textModeTheme SYSTEM, LIGHT, SEPIA, DARK, BLACK, DRACULA"],
           includes_history=False)

    out_of_range = [
        _setting("textModeFontSize", "float", "999.0"),
        _setting("textModeLineSpacing", "float", "0.1"),
        _setting("textModeHorizontalMargin", "int", "500"),
    ]
    _write(root, "out-of-range-text-mode-backup.json",
           _dump(_envelope(settings=out_of_range)),
           "Three correctly typed text mode values far outside their usable "
           "range, so validation accepts them and TextModeSettings.snap clamps "
           "them on load (SET-76)",
           schemaVersion=1, settings=3, applied_settings=3, skipped_settings=0,
           stored_values=["textModeFontSize 999.0", "textModeLineSpacing 0.1",
                          "textModeHorizontalMargin 500"],
           clamped_to=["font size 36", "line spacing 1.0", "margin 48"],
           includes_history=False)

    corrupt = ("This file is not JSON. It is four hundred bytes of ordinary "
               "prose so the parser fails on the very first token rather than "
               "part way through a structure it half understood. ")
    text = (corrupt * 4)[:CORRUPT_BYTES]
    assert len(text) == CORRUPT_BYTES
    _write(root, "corrupt-backup.json", text,
           "Exactly 400 bytes of non JSON text, refused before anything changes",
           bytes=CORRUPT_BYTES, is_json=False,
           expectation="backup_error_not_valid")

    _write(root, "empty-backup.json", "",
           "Zero bytes, refused with a clear message rather than an unhandled "
           "parse error",
           bytes=0, is_json=False, expectation="backup_error_not_valid")

    payload = _envelope(settings=KNOWN_SETTINGS)
    payload["schemaVersion"] = 99
    _write(root, "newer-schema-backup.json", _dump(payload),
           "Valid JSON with schemaVersion 99, refused by version with the newer "
           "version message (SET-79)",
           schemaVersion=99, expectation="backup_error_newer_version")


BKP30_TEXT = (
    "This is not a backup. BKP-30 picks it in the file picker to prove that a "
    "file the picker offers is still refused when it is not a backup at all.\n"
)


def _binaries(root):
    path = out_path(root, "backups", "fake-dictionary.db")
    # Sized, listed and deleted, never queried, so a real SQLite file would only
    # invite a test to read it.
    header = b"SQLite format 3\x00"
    body = bytearray(header)
    block = 0
    while len(body) < DICTIONARY_BYTES:
        body += hashlib.md5(f"dictionary:{block}".encode("ascii")).digest()
        block += 1
    payload = bytes(body[:DICTIONARY_BYTES])
    with open(path, "wb") as fh:
        fh.write(payload)
    assert os.path.getsize(path) == DICTIONARY_BYTES
    manifest.register(
        "fake-dictionary.db", path,
        "Exactly 1,048,576 bytes standing in for an installed dictionary at "
        "filesDir/dictionaries/en-wordnet.db. Never queried, only sized, listed "
        "and deleted",
        bytes=DICTIONARY_BYTES, header="SQLite format 3, then filler",
        installed_as="filesDir/dictionaries/en-wordnet.db",
        formatted_size="the summary shows the real 1,048,576 bytes, not the "
                       "constant install size",
    ).tag("backups")

    _write(root, "not-a-backup.txt", BKP30_TEXT,
           "Short plain text file for the BKP-30 fallback, picked in the file "
           "picker where a backup is expected",
           bytes=len(BKP30_TEXT.encode("utf-8")), is_json=False,
           expectation="backup_error_not_valid")
