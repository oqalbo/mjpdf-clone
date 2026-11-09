"""Highlight, note, form field and signature fixtures.

Everything the highlights list, the notes list and the reader's annotation layer
read. The counts, colours, page indexes and creation dates below are asserted
literally by `highlights-and-notes-lists.md` and `annotations.md`, so they are
derived from the data in this module and re-counted before registration rather
than written out by hand.

Three details of the annotation format decide whether these fixtures work at
all, all three confirmed against `PdfiumAndroid/src/main/jni/src/mainJNILib.cpp`:

- The list reads the quote from `MJQuote` and the note from `Contents`. The
  library's `highlight()` copies the quote into `Contents` when no note is
  given, which would make every highlighted row a note, so `_mj_highlight`
  removes it again. Only groups that really carry a note keep a `Contents`.
- A highlight covering three lines is three annotations sharing one `MJGroup`,
  which is what `nativeAddHighlight` writes, one annotation per rectangle. One
  annotation holding three quad point sets would collapse the dedupe path the
  list tests exercise, so `_mj_group` makes one annotation per line.
- Colour is read from `MJColor` first and only then from `/C`, so app authored
  highlights carry both.

Creation dates use `D:yyyyMMddHHmmss` with no UTC offset. `DateUtil` treats an
offset free date as local time, so the row text does not move with the device
time zone.
"""

import json
import re

from lib import arabic, manifest
from lib.doc import Document, OutlineEntry
from lib.pdfwriter import Name, Raw
from lib.registry import fixture_group, out_path

LEFT = 72.0
BODY_TOP = 700.0
BODY_LEADING = 16.0
BODY_SIZE = 11.0
BODY_FONT = "serif"
BODY_WIDTH = 468.0
BODY_LINES = 30

RTL_TOP = 700.0
RTL_LEADING = 24.0
RTL_SIZE = 13.0

PALETTE = {
    "YELLOW": 0xFFFFF176,
    "ORANGE": 0xFFFFB74D,
    "GREEN": 0xFF81C784,
    "BLUE": 0xFF64B5F6,
    "PINK_RED": 0xFFF06292,
    "PURPLE": 0xFFB39DDB,
}
NON_PALETTE = 0xFF123456
FOREIGN_GREEN = 0xFF00FF00
FOREIGN_ORANGE = 0xFFFFA500

FILLER = (
    "Memory is a promise the machine makes to itself, and the model is the "
    "shape of that promise. A program asks for room, uses it for a while, and "
    "then either gives it back or forgets that it ever had it. The runtime "
    "stands between those two habits and tries to make the second one "
    "harmless. Every scheme in this book is a different answer to one small "
    "question: when is a piece of storage no longer anybody's business. Some "
    "answers are counted, some are traced, and some are simply waited out "
    "until the program ends and the system settles the account on its behalf. "
)

ARABIC_BODY = [
    "الذاكرة وعد تقطعه الآلة على نفسها",
    "وكل نموذج هو شكل ذلك الوعد",
    "يطلب البرنامج مساحة ثم يعيدها أو ينساها",
    "ويقف النظام بين العادتين ليجعل الثانية بلا ضرر",
    "كل طريقة في هذا الكتاب جواب مختلف عن سؤال واحد",
    "متى تصبح قطعة من الذاكرة بلا صاحب",
]

# Nine app owned groups. The list surface asserts the page pill order
# [1, 1, 3, 3, 6, 6, 9, 10, 10], four yellow, two blue, two green and one non
# palette colour, three notes, and the newest date on the page index 9 group.
RICH_GROUPS = [
    {
        "key": "mj-group-01", "page": 0, "at": [3], "argb": PALETTE["YELLOW"],
        "created": "D:20240310090000",
        "note": "The ledger idea returns in chapter four.",
        "text": ["A ledger of every allocation is the only honest map of a heap."],
    },
    {
        "key": "mj-group-02", "page": 0, "at": [9], "argb": PALETTE["BLUE"],
        "created": "D:20240115143000", "note": None,
        "text": ["An arena grows forward and forgets nothing until the cadence ends."],
    },
    {
        "key": "mj-group-03", "page": 2, "at": [5, 6, 7], "argb": PALETTE["GREEN"],
        "created": "D:20240522101500", "note": None,
        "text": [
            "Compaction moves the living objects together so that the gaps",
            "between them close, and the address of every survivor changes at",
            "once, which is why a pointer held across a move is a ruin.",
        ],
    },
    {
        "key": "mj-group-04", "page": 2, "at": [14], "argb": PALETTE["YELLOW"],
        "created": "D:20240103081000", "note": None,
        "text": ["A quarantine page is never reused while a live pointer may name it."],
    },
    {
        "key": "mj-group-05", "page": 5, "at": [4], "argb": PALETTE["YELLOW"],
        "created": "D:20240418173000", "note": None,
        "text": ["The collector opens with a handshake and closes with a cadence."],
    },
    {
        # Lines 21 to 23 sit below the middle of the page, which is what LIST-15
        # needs when it proves the reader zooms to the passage and not the page.
        "key": "mj-group-06", "page": 5, "at": [21, 22, 23], "argb": PALETTE["BLUE"],
        "created": "D:20240207120000",
        "note": "Check this marginalia against the second edition.",
        "text": [
            "A tombstone marks the place where an object used to be, and it",
            "stays readable long after the object is gone so that a stale",
            "reference is reported instead of quietly reading a stranger.",
        ],
    },
    {
        "key": "mj-group-07", "page": 8, "at": [6], "argb": NON_PALETTE,
        "created": "D:20240630200000", "note": None,
        "text": ["Sediment gathers at the bottom of a long lived heap and stays."],
    },
    {
        "key": "mj-group-08", "page": 9, "at": [5], "argb": PALETTE["GREEN"],
        "created": "D:20240711064500",
        "note": "The lantern metaphor is borrowed from the tracing literature.",
        "text": ["A lantern pass lights only the objects the roots can still reach."],
    },
    {
        "key": "mj-group-09", "page": 9, "at": [12], "argb": PALETTE["YELLOW"],
        "created": "D:20240915215900", "note": None,
        "text": ["The harbour of the old generation is quiet until the tide turns."],
    },
]

# Words the list search tests type. Each maps to the groups it may appear in,
# counting quote and note text together, and nothing else in the fixture.
RICH_SEARCH_WORDS = {
    "compaction": {"mj-group-03"},
    "quarantine": {"mj-group-04"},
    "handshake": {"mj-group-05"},
    "tombstone": {"mj-group-06"},
    "marginalia": {"mj-group-06"},
    "sediment": {"mj-group-07"},
    "harbour": {"mj-group-09"},
    "cadence": {"mj-group-02", "mj-group-05"},
}

PALETTE_PHRASES = [
    ("YELLOW", "Ships in the harbour wait for the morning tide."),
    ("ORANGE", "The lighthouse keeper counts every passing hull."),
    ("GREEN", "Nets dry on the wall beside the customs house."),
    ("BLUE", "A ferry crosses twice an hour in fair weather."),
    ("PINK_RED", "The chandler sells rope by the fathom and coil."),
    ("PURPLE", "Gulls settle on the roof of the fish market."),
]

SPANNING_SENTENCE = (
    "The sentence that follows is deliberately long enough to wrap across "
    "three rendered lines, so that a selection over the whole of it produces "
    "three separate highlight rectangles rather than one single tidy box."
)

WRAPPED_SENTENCE = (
    "This one sentence is long enough to wrap onto a second rendered line, so "
    "a highlight over all of it yields a quote carrying a line break."
)

LONG_QUOTE_SOURCE = (
    "A long quotation is the hardest thing a list row has to hold, because the "
    "row must stay one line tall while the text behind it runs on for whole "
    "paragraphs, and the export has to write every character of it back out "
    "again without folding, trimming or quietly dropping the tail. This "
    "passage exists to be exactly that awkward: it is ordinary English prose, "
    "it has no unusual characters in it at all, and it is long enough that "
    "every cap on quote length in the reader, the list row, the share card and "
    "the exported text file is crossed by a comfortable margin rather than by "
    "a single character, so a regression in any one of them shows up plainly. "
    "The wording carries no punctuation that a shell would have to escape, no "
    "quotation marks that could be mistaken for the ones the export adds "
    "around it, and no hyphen that a reflow pass might join across a line "
    "break, so what a test reads back is what this generator wrote and nothing "
    "in between has had a chance to change it on the way."
)


def _csv(values):
    """One readable line, because the manifest collapses long lists to a count."""
    return ", ".join(str(v) for v in values)


def _rgb(argb):
    return ((argb >> 16 & 0xFF) / 255.0, (argb >> 8 & 0xFF) / 255.0,
            (argb & 0xFF) / 255.0)


def _hex6(argb):
    return f"{argb & 0xFFFFFF:06X}"


def _pdf_text(s):
    """Non ASCII PDF strings need UTF-16BE and a byte order mark.

    A bare UTF-8 literal is read back as PDFDocEncoding, which would turn every
    Arabic quote into mojibake before a test ever saw it.
    """
    if isinstance(s, bytes) or all(ord(c) < 128 for c in s):
        return s
    return b"\xfe\xff" + s.encode("utf-16-be")


def _mj_highlight(page, rects, argb, quote, group, created, note=None):
    """One app owned highlight annotation, shaped the way the app writes one."""
    h = page.highlight(rects, color=_rgb(argb), quote=_pdf_text(quote),
                       note=_pdf_text(note) if note is not None else None,
                       group=group, created=created)
    h["MJColor"] = _hex6(argb)
    if note is None:
        # The library falls back to the quote for Contents, which the app reads
        # as a note. Only a group that really carries one keeps it.
        h.pop("Contents", None)
    if not quote:
        h["MJQuote"] = ""
    return h


def _mj_group(page, rects, argb, quote, group, created, note=None):
    """One annotation per line, all sharing the group key, as the app writes."""
    return [_mj_highlight(page, [r], argb, quote, group, created, note)
            for r in rects]


def _foreign_highlight(page, rects, argb, contents=None, title="Acrobat"):
    """A highlight written by another tool: no MJGroup, no MJQuote, other T."""
    h = page.highlight(rects, color=_rgb(argb), quote="", note=contents,
                       group=None, created="", app_owned=False)
    h["T"] = title
    return h


def _text_page(doc, heading, overrides=None, count=BODY_LINES):
    """A page of filler with chosen lines replaced, returning their rectangles."""
    p = doc.add_page()
    p.text(LEFT, p.height - 60, heading, font="serif-bold", size=13)
    lines = doc.wrap(FILLER * 5, BODY_FONT, BODY_SIZE, BODY_WIDTH)[:count]
    if len(lines) < count:
        raise AssertionError("filler is too short for the page")
    for index, text in (overrides or {}).items():
        lines[index] = text
    rects = []
    y = BODY_TOP
    for line in lines:
        if doc.font(BODY_FONT).text_width(line, BODY_SIZE) > BODY_WIDTH:
            raise AssertionError(f"line runs past the text column: {line}")
        p.text(LEFT, y, line, font=BODY_FONT, size=BODY_SIZE)
        rects.append(doc.line_rect(p, LEFT, y, line, BODY_FONT, BODY_SIZE))
        y -= BODY_LEADING
    return p, rects


def _rtl_rect(doc, right_x, y, left_x, font="sans", size=RTL_SIZE, pad=2.0):
    f = doc.font(font)
    asc = f.face.ascent * size / f.face.units_per_em
    desc = f.face.descent * size / f.face.units_per_em
    return (left_x, y + desc - pad, right_x, y + asc + pad)


def _rtl_page(doc, lines, font="sans", size=RTL_SIZE):
    """A right aligned Arabic page, returning one rectangle per drawn line."""
    p = doc.add_page()
    right = p.width - 72
    rects = []
    y = RTL_TOP
    for line in lines:
        if doc.font(font).glyphs_width(arabic.shape(line), size) > p.width - 144:
            raise AssertionError("Arabic line runs past the text column")
        left = p.text_rtl(right, y, line, font=font, size=size)
        rects.append(_rtl_rect(doc, right, y, left, font, size))
        y -= RTL_LEADING
    return p, rects


def _rtl_run(doc, page, x, y, s, font="sans", size=BODY_SIZE):
    """Arabic positioned as one run inside a left to right line."""
    w = doc.font(font).glyphs_width(arabic.shape(s), size)
    page.text_rtl(x + w, y, s, font=font, size=size)
    return x + w


def _grey_page(doc, seed):
    """An image only page: no text object at all, so nothing extracts."""
    w, h = 560, 720
    rows = bytearray()
    for y in range(h):
        for x in range(w):
            v = 236
            if 40 < y < 70 and 40 < x < 320:
                v = 45
            elif 120 < y % 200 < 138 and 40 < x < 480:
                v = 95
            elif (x + y * 5 + seed * 11) % 811 == 0:
                v = 165
            rows.append(v)
    p = doc.add_page()
    p.image(36, 36, p.width - 72, p.height - 72, gray=(w, h, bytes(rows)))
    return p


def _widget_object_numbers(path):
    """Object numbers of the widget annotations, in file order."""
    with open(path, "rb") as fh:
        data = fh.read()
    nums = []
    for m in re.finditer(rb"(\d+) 0 obj\n(.*?)\nendobj\n", data, re.S):
        body = m.group(2)
        if b"/Subtype /Widget" in body and b"/T (" in body:
            nums.append(int(m.group(1)))
    return nums


def _carriage_return_in_strings(path):
    """True if any object outside a stream payload holds a raw carriage return.

    The writer only ever emits line feeds, so a carriage return here can only
    have come from ciphertext, and a reader folds one to a line feed before
    decryption ever runs.
    """
    with open(path, "rb") as fh:
        data = fh.read()
    for m in re.finditer(rb"(\d+) 0 obj\n(.*?)\nendobj\n", data, re.S):
        body = m.group(2)
        cut = body.find(b"\nstream\n")
        head = body if cut < 0 else body[:cut]
        if b"\r" in head:
            return True
    return False


def _save_encrypted(d, path, password):
    """Save encrypted, choosing a salt that survives the literal string rules.

    The library encrypts the escaped form of a literal string and re-escapes
    only the parentheses and the backslash, so a ciphertext byte of 0x0D is
    read back as 0x0A and everything after it in that string decrypts to
    rubbish. The document id seeds the RC4 key and is an MD5 of the content, so
    one unread catalog key moves every ciphertext byte in the file. The lowest
    salt that leaves no carriage return wins, which keeps the choice
    deterministic across machines.
    """
    for salt in range(64):
        d.extra_catalog["MJSalt"] = "s" * salt
        d.save(path, encrypt_password=password)
        if not _carriage_return_in_strings(path):
            return salt
    raise AssertionError("no salt produced a readable encrypted file")


def _save_with_acroform(d, path):
    """Save twice so /AcroForm can name the widget objects by number.

    pdfium finds no form field at all unless the catalog's AcroForm /Fields
    array reaches the field dictionaries, and here the widget annotations are
    those dictionaries. The writer assigns their object numbers during save, so
    the first save reveals them and the second writes them into the catalog.
    The AcroForm entry is inline in the catalog and creates no new object, so
    the numbers are identical in both passes, which the check below proves.
    """
    d.save(path)
    refs = _widget_object_numbers(path)
    if not refs:
        raise AssertionError("no widget annotations were written")
    d.extra_catalog["AcroForm"] = {
        "Fields": Raw("[" + " ".join(f"{n} 0 R" for n in refs) + "]"),
        "DA": "/Helv 0 Tf 0 g",
        "NeedAppearances": True,
    }
    d.save(path)
    if _widget_object_numbers(path) != refs:
        raise AssertionError("widget object numbers moved between the two saves")
    return refs


@fixture_group("annotations", "Highlights, notes, form fields and the signature")
def build(root):
    _check_rich_spec()
    _rich(root, "highlights-rich.pdf")
    _rich(root, "highlights-rich-mixed-dates.pdf", undated_pages=(0,))
    _plain(root)
    _none(root)
    _arabic(root)
    _mixed_quote(root)
    _thirdparty(root)
    _scanned(root)
    _long_quote(root)
    _locked(root)
    _mj_authored(root)
    _foreign(root)
    _forms_all_field_types(root)
    _form_values(root)
    _palette(root)
    _spanning(root)
    _wrapped_quote(root)
    _signature(root)
    _huge(root)


def _check_rich_spec():
    """The rich fixture's numbers are asserted literally, so check them here."""
    pages = [g["page"] for g in RICH_GROUPS]
    if pages != [0, 0, 2, 2, 5, 5, 8, 9, 9]:
        raise AssertionError(f"group page order changed: {pages}")
    if pages != sorted(pages):
        raise AssertionError("groups must be listed in page order")
    argbs = [g["argb"] for g in RICH_GROUPS]
    expected = {PALETTE["YELLOW"]: 4, PALETTE["BLUE"]: 2, PALETTE["GREEN"]: 2,
                NON_PALETTE: 1}
    for argb, count in expected.items():
        if argbs.count(argb) != count:
            raise AssertionError(f"colour {argb:08X} used {argbs.count(argb)} times")
    if len(argbs) != sum(expected.values()):
        raise AssertionError("an unexpected colour is in use")
    notes = [g for g in RICH_GROUPS if g["note"]]
    if len(notes) != 3:
        raise AssertionError(f"{len(notes)} notes, three are specified")
    spans = [g for g in RICH_GROUPS if len(g["at"]) == 3]
    if len(spans) != 2:
        raise AssertionError(f"{len(spans)} three line groups, two are specified")
    if len({g["at"][0] for g in RICH_GROUPS}) < 1:
        raise AssertionError("no lines chosen")
    dates = [g["created"] for g in RICH_GROUPS]
    if len(set(dates)) != len(dates):
        raise AssertionError("creation dates must all differ")
    newest = max(RICH_GROUPS, key=lambda g: g["created"])
    if newest["page"] != 9:
        raise AssertionError("the newest date must belong to a page index 9 group")
    # First appearance order decides the colour chip order after the All chip.
    seen = []
    for g in RICH_GROUPS:
        if g["argb"] not in seen:
            seen.append(g["argb"])
    if seen != [PALETTE["YELLOW"], PALETTE["BLUE"], PALETTE["GREEN"], NON_PALETTE]:
        raise AssertionError("colour first appearance order changed")
    for word, owners in RICH_SEARCH_WORDS.items():
        found = set()
        for g in RICH_GROUPS:
            body = " ".join(g["text"]) + " " + (g["note"] or "")
            if word in body.lower():
                found.add(g["key"])
        if found != owners:
            raise AssertionError(f"'{word}' is in {sorted(found)}, expected {sorted(owners)}")


def _rich(root, name, undated_pages=()):
    d = Document(title="The Memory Model", author="A. Writer")
    rects = []
    for i in range(12):
        overrides = {}
        for g in RICH_GROUPS:
            if g["page"] == i:
                for line_index, text in zip(g["at"], g["text"]):
                    overrides[line_index] = text
        _, page_rects = _text_page(d, f"Chapter {i + 1}", overrides)
        rects.append(page_rects)

    annotations = 0
    for g in RICH_GROUPS:
        created = "" if g["page"] in undated_pages else g["created"]
        made = _mj_group(
            d.pages[g["page"]],
            [rects[g["page"]][i] for i in g["at"]],
            g["argb"], "\n".join(g["text"]), g["key"], created, g["note"])
        annotations += len(made)

    d.outline = [
        OutlineEntry("Part One", 0, children=[OutlineEntry("Allocation", 2)]),
        OutlineEntry("Part Two", 6, children=[OutlineEntry("Collection", 8)]),
    ]
    path = out_path(root, name)
    d.save(path)

    undated = [g["key"] for g in RICH_GROUPS if g["page"] in undated_pages]
    described = ("12 pages, nine app owned highlight groups, two of them three "
                 "annotations under one group key. Two level outline")
    if undated:
        described += ". Variant with the two page index 0 groups undated"
    manifest.register(
        name, path, described,
        pages=12, meta_title="The Memory Model", meta_author="A. Writer",
        highlight_groups=len(RICH_GROUPS), highlight_annotations=annotations,
        group_page_indexes=_csv(g["page"] for g in RICH_GROUPS),
        page_pill_order=_csv(g["page"] + 1 for g in RICH_GROUPS),
        groups_with_notes=len([g for g in RICH_GROUPS if g["note"]]),
        note_page_indexes=_csv(g["page"] for g in RICH_GROUPS if g["note"]),
        multi_line_groups=_csv(g["key"] for g in RICH_GROUPS if len(g["at"]) == 3),
        colour_counts="0xFFFFF176 four, 0xFF64B5F6 two, 0xFF81C784 two, "
                      "0xFF123456 one",
        colour_per_group=_csv(f"{g['key']}=0x{g['argb']:08X}" for g in RICH_GROUPS),
        non_palette_colour="0xFF123456, chip label #123456",
        colour_chip_order="All, Yellow, Blue, Green, #123456",
        group_keys=_csv(g["key"] for g in RICH_GROUPS),
        creation_dates=_csv(
            "{}={}".format(g["key"], "" if g["page"] in undated_pages else g["created"])
            for g in RICH_GROUPS),
        undated_groups=_csv(undated) or "none",
        newest_group=max(RICH_GROUPS, key=lambda g: g["created"])["key"],
        outline_entries=4,
        outline="Part One at 0, Allocation at 2, Part Two at 6, Collection at 8",
        chapter_path_page_5="Part One > Allocation",
        chapter_path_page_8="Part Two > Collection",
        first_quote=RICH_GROUPS[0]["text"][0],
        verbatim_text="every quote and note is in MANIFEST.json under quotes "
                      "and notes",
        quotes={g["key"]: "\n".join(g["text"]) for g in RICH_GROUPS},
        notes={g["key"]: g["note"] for g in RICH_GROUPS if g["note"]},
        search_words=_csv(f"{w} in {'+'.join(sorted(o))}"
                          for w, o in sorted(RICH_SEARCH_WORDS.items())),
        deepest_page_used="index 9",
    ).tag("annotations")


def _plain(root):
    d = Document()
    rects = []
    quotes = [
        "A free list is a rumour about where the room used to be.",
        "Counting references is cheap until two of them point at each other.",
    ]
    for i in range(3):
        overrides = {3: quotes[i]} if i < len(quotes) else {}
        _, page_rects = _text_page(d, f"Section {i + 1}", overrides)
        rects.append(page_rects)
    groups = []
    for i, quote in enumerate(quotes):
        key = f"plain-group-{i + 1}"
        _mj_highlight(d.pages[i], [rects[i][3]], PALETTE["YELLOW"], quote, key, "")
        groups.append(key)
    path = out_path(root, "highlights-plain.pdf")
    d.save(path)
    manifest.register(
        "highlights-plain.pdf", path,
        "3 pages, no outline and no meta Title or Author, two yellow highlight "
        "groups, no notes and no creation dates",
        pages=3, outline_entries=0, meta_title=None, meta_author=None,
        highlight_groups=2, highlight_annotations=2, groups_with_notes=0,
        group_keys=groups, group_page_indexes=[0, 1],
        colour="0xFFFFF176 on both", creation_dates="absent on both",
        export_name="highlights-plain.pdf - My Highlights.txt",
        quotes=quotes, deepest_page_used="index 1",
    ).tag("annotations")


def _none(root):
    d = Document(title="Nothing Marked")
    for i in range(3):
        _text_page(d, f"Section {i + 1}")
    path = out_path(root, "highlights-none.pdf")
    d.save(path)
    manifest.register(
        "highlights-none.pdf", path,
        "3 pages of ordinary text carrying zero highlight annotations",
        pages=3, highlight_groups=0, highlight_annotations=0,
        annotations_of_any_kind=0, deepest_page_used="index 0",
    ).tag("annotations")


def _arabic(root):
    # No Title and no Author on purpose, so the export file name falls back to
    # the ASCII file name and a shell read of it needs no quoting.
    d = Document()
    quotes = [
        "القراءة رحلة هادئة بين الصفحة والعين",
        "الذاكرة وعد تقطعه الآلة على نفسها",
        "كل فقرة تقدم شكلا والقارئ يقبله أو يرفضه",
    ]
    note = "راجع هذه الفكرة في الفصل الرابع"
    placements = [(0, 2), (1, 4), (3, 1)]
    pages = []
    for i in range(6):
        lines = list(ARABIC_BODY)
        for (page_index, line_index), quote in zip(placements, quotes):
            if page_index == i:
                lines[line_index] = quote
        pages.append(_rtl_page(d, lines))
    groups = []
    for index, ((page_index, line_index), quote) in enumerate(zip(placements, quotes)):
        key = f"ar-group-{index + 1}"
        page, rects = pages[page_index]
        _mj_highlight(page, [rects[line_index]], PALETTE["YELLOW"], quote, key,
                      f"D:2024051{index + 1}12000{index}",
                      note if index == 1 else None)
        groups.append(key)
    path = out_path(root, "highlights-arabic.pdf")
    d.save(path)
    manifest.register(
        "highlights-arabic.pdf", path,
        "RTL Arabic, 6 pages, three app owned highlight groups with Arabic "
        "quotes, one carrying an Arabic note",
        pages=6, reading_direction="rtl", highlight_groups=3,
        highlight_annotations=3, groups_with_notes=1,
        group_keys=groups, group_page_indexes=[p for p, _ in placements],
        quotes=quotes, note=note, note_group="ar-group-2",
        colour="0xFFFFF176 on all three",
        strings_encoded="UTF-16BE with a byte order mark",
        meta_title=None, meta_author=None,
        export_name="highlights-arabic.pdf - My Highlights.txt",
        deepest_page_used="index 3",
    ).tag("annotations")


def _mixed_quote(root):
    d = Document(title="Mixed Direction Quote")
    p = d.add_page()
    p.text(LEFT, p.height - 60, "One quote, two directions", font="serif-bold",
           size=13)
    y = BODY_TOP
    quote = "The word كتاب means book and the word قلم means pen in Arabic."
    x = LEFT
    x = p.text(x, y, "The word ", font=BODY_FONT, size=BODY_SIZE)
    x = _rtl_run(d, p, x, y, "كتاب")
    x = p.text(x, y, " means book and the word ", font=BODY_FONT, size=BODY_SIZE)
    x = _rtl_run(d, p, x, y, "قلم")
    x = p.text(x, y, " means pen in Arabic.", font=BODY_FONT, size=BODY_SIZE)
    f = d.font(BODY_FONT)
    asc = f.face.ascent * BODY_SIZE / f.face.units_per_em
    desc = f.face.descent * BODY_SIZE / f.face.units_per_em
    rect = (LEFT, y + desc - 2.0, x, y + asc + 2.0)
    d.paragraph(p, LEFT, y - 40, FILLER, font=BODY_FONT, size=BODY_SIZE,
                leading=BODY_LEADING, max_width=BODY_WIDTH)
    _mj_highlight(p, [rect], PALETTE["YELLOW"], quote, "mixed-group-1",
                  "D:20240401101500")
    path = out_path(root, "highlights-mixed-quote.pdf")
    d.save(path)
    latin = sum(1 for c in quote if "a" <= c.lower() <= "z")
    arabic_chars = sum(1 for c in quote if "؀" <= c <= "ۿ")
    manifest.register(
        "highlights-mixed-quote.pdf", path,
        "One highlight whose quote is mostly English with two Arabic words "
        "inside it, first strongly directional character Latin",
        pages=1, highlight_groups=1, highlight_annotations=1,
        quote=quote, latin_letters=latin, arabic_letters=arabic_chars,
        first_strong_character="T, Latin",
        arabic_words=["كتاب", "قلم"],
        colour="0xFFFFF176", group_key="mixed-group-1",
        creation_date="D:20240401101500",
    ).tag("annotations")


def _thirdparty(root):
    d = Document(title="Marked Elsewhere")
    quotes = [
        "The first passage another application chose to mark.",
        "The second passage another application chose to mark.",
        "The third passage another application chose to mark.",
    ]
    rects = []
    for i in range(4):
        overrides = {4: quotes[i]} if i < len(quotes) else {}
        _, page_rects = _text_page(d, f"Leaf {i + 1}", overrides)
        rects.append(page_rects)
    for i in range(3):
        # No Contents, so the row has no note, no date and no chapter path and
        # its expand arrow stays invisible.
        _foreign_highlight(d.pages[i], [rects[i][4]], FOREIGN_GREEN)
    path = out_path(root, "highlights-thirdparty.pdf")
    d.save(path)
    manifest.register(
        "highlights-thirdparty.pdf", path,
        "4 pages, three highlights written by another application: no MJGroup, "
        "no MJQuote, no Contents, no CreationDate, T is Acrobat",
        pages=4, outline_entries=0, highlight_annotations=3,
        highlight_groups=3, app_owned=False, group_key="empty on all three",
        colour="0xFF00FF00 on all three", creation_dates="absent on all three",
        notes=0, quotes="empty on all three",
        annotation_page_indexes=[0, 1, 2],
        row_expand_arrow="invisible, nothing to reveal",
    ).tag("annotations")


def _scanned(root):
    d = Document(title="Scanned Figure")
    p = _grey_page(d, 3)
    rect = (90.0, 420.0, 470.0, 560.0)
    _mj_highlight(p, [rect], PALETTE["YELLOW"], "", "scanned-group-1",
                  "D:20240812093000", "check this figure")
    path = out_path(root, "highlights-scanned.pdf")
    d.save(path)
    manifest.register(
        "highlights-scanned.pdf", path,
        "One image only page carrying a single highlight with an empty MJQuote "
        "and the note 'check this figure'",
        pages=1, extractable_characters=0, highlight_groups=1,
        highlight_annotations=1, groups_with_notes=1,
        group_key="scanned-group-1", quote="", note="check this figure",
        colour="0xFFFFF176", creation_date="D:20240812093000",
        highlight_rect=[90.0, 420.0, 470.0, 560.0],
        export_name="highlights-scanned.pdf - My Notes.txt",
    ).tag("annotations")


def _long_quote(root):
    quote = " ".join(LONG_QUOTE_SOURCE.split())
    if len(quote) < 900:
        raise AssertionError(f"long quote source is only {len(quote)} characters")
    quote = quote[:900]
    if quote.endswith(" "):
        raise AssertionError("the 900 character cut must not land on a space")
    d = Document(title="One Very Long Quote")
    lines = d.wrap(quote, BODY_FONT, BODY_SIZE, BODY_WIDTH)
    if "\n".join(lines).replace("\n", " ") != quote:
        raise AssertionError("wrapping did not preserve the quote text")
    overrides = {i + 2: line for i, line in enumerate(lines)}
    p, rects = _text_page(d, "A single passage", overrides)
    _mj_group(p, [rects[i + 2] for i in range(len(lines))], PALETTE["YELLOW"],
              "\n".join(lines), "long-group-1", "D:20240220154500")
    path = out_path(root, "highlights-long-quote.pdf")
    d.save(path)
    manifest.register(
        "highlights-long-quote.pdf", path,
        "One highlight group whose quote is 900 characters of English prose, "
        "one annotation per rendered line",
        pages=1, highlight_groups=1, highlight_annotations=len(lines),
        quote_characters=900, quote_lines=len(lines),
        quote_with_spaces=quote, group_key="long-group-1",
        colour="0xFFFFF176", creation_date="D:20240220154500",
    ).tag("annotations")


def _locked(root):
    # Every string in this fixture stays ASCII, because the encryption pass
    # encrypts the escaped form of a literal string rather than its bytes.
    d = Document()
    quotes = [
        "A locked document still belongs to the reader who has the password.",
        "Encryption hides the bytes, not the marks a reader made in them.",
    ]
    rects = []
    for i in range(3):
        overrides = {3: quotes[i]} if i < len(quotes) else {}
        _, page_rects = _text_page(d, f"Sealed {i + 1}", overrides)
        rects.append(page_rects)
    groups = []
    for i, quote in enumerate(quotes):
        key = f"locked-group-{i + 1}"
        note = "the password is on the cover letter" if i == 0 else None
        _mj_highlight(d.pages[i], [rects[i][3]], PALETTE["YELLOW"], quote, key,
                      f"D:2024061{i + 4}080000", note)
        groups.append(key)
    path = out_path(root, "highlights-locked.pdf")
    salt = _save_encrypted(d, path, "letmein")
    manifest.register(
        "highlights-locked.pdf", path,
        "User password letmein, 3 pages, two highlight groups of which one "
        "carries a note",
        pages=3, password="letmein", encryption="RC4 40 bit, revision 2",
        highlight_groups=2, highlight_annotations=2, groups_with_notes=1,
        group_keys=groups, group_page_indexes=[0, 1], quotes=quotes,
        note="the password is on the cover letter", note_group="locked-group-1",
        colour="0xFFFFF176 on both", deepest_page_used="index 1",
        catalog_salt=f"MJSalt of {salt} characters, read by nothing",
    ).tag("annotations")


def _mj_authored(root):
    d = Document(title="Written By MJ PDF", author="A. Writer")
    single = "A single line the reader marked and then wrote a note against."
    span = [
        "A passage that runs across three rendered lines is stored as three",
        "annotations that share one group key, which is how the reader knows",
        "to treat them as one highlight rather than as three separate ones.",
    ]
    blank = "A region marked where no text could be read from the page."
    overrides = {3: single, 12: blank}
    for i, line in enumerate(span):
        overrides[6 + i] = line
    rects = []
    for i in range(3):
        _, page_rects = _text_page(d, f"Page {i + 1}",
                                   overrides if i == 0 else None)
        rects.append(page_rects)
    p = d.pages[0]
    _mj_highlight(p, [rects[0][3]], PALETTE["YELLOW"], single, "mj-single",
                  "D:20240305111500", "a note on the single line highlight")
    _mj_group(p, [rects[0][6], rects[0][7], rects[0][8]], PALETTE["GREEN"],
              "\n".join(span), "mj-span", "D:20240306092000",
              "a note on the three line highlight")
    _mj_highlight(p, [rects[0][12]], PALETTE["BLUE"], "", "mj-noquote",
                  "D:20240307143000", "a note on a highlight with no quote")
    path = out_path(root, "highlights_mj_authored.pdf")
    d.save(path)
    manifest.register(
        "highlights_mj_authored.pdf", path,
        "3 pages. Page index 0 carries a single line highlight with a note, a "
        "three line highlight under one group key, and a highlight whose "
        "MJQuote is present and empty. All three are app owned",
        pages=3, highlight_groups=3, highlight_annotations=5,
        app_owned=True, markers="T = MJ PDF, MJGroup, MJQuote, MJColor",
        single_line_group="mj-single", single_line_quote=single,
        single_line_colour="0xFFFFF176",
        single_line_rect=[round(v, 2) for v in rects[0][3]],
        span_group="mj-span", span_annotations=3, span_colour="0xFF81C784",
        span_rects=[[round(v, 2) for v in rects[0][i]] for i in (6, 7, 8)],
        empty_quote_group="mj-noquote", empty_quote_colour="0xFF64B5F6",
        empty_quote_rect=[round(v, 2) for v in rects[0][12]],
        notes=3, deepest_page_used="index 0",
    ).tag("annotations")


def _foreign(root):
    d = Document(title="Written By Another App", author="A. Writer")
    quote = "A line another application highlighted before this reader saw it."
    rects = []
    for i in range(3):
        _, page_rects = _text_page(d, f"Page {i + 1}", {3: quote} if i == 0 else None)
        rects.append(page_rects)
    _foreign_highlight(d.pages[0], [rects[0][3]], FOREIGN_ORANGE, contents=quote)
    path = out_path(root, "highlights_foreign.pdf")
    d.save(path)
    manifest.register(
        "highlights_foreign.pdf", path,
        "3 pages. One highlight on page index 0 written by another tool: no "
        "MJGroup, no MJQuote, T is Acrobat, over readable text",
        pages=3, highlight_annotations=1, app_owned=False,
        annotation_page_index=0, title_key="Acrobat",
        colour="0xFFFFA500", quote_key="absent",
        contents=quote, creation_date="absent",
        highlight_rect=[round(v, 2) for v in rects[0][3]],
        underlying_text=quote, deepest_page_used="index 0",
    ).tag("annotations")


def _form_page_one(d, values=None):
    """Page 1 of the form fixtures. values fills the fields that accept one."""
    values = values or {}
    p = d.add_page()
    h = p.height
    p.text(LEFT, h - 60, "Application", font="serif-bold", size=14)
    fields = []

    p.text(LEFT, h - 110, "Full name", size=BODY_SIZE)
    fields.append(p.widget((200, h - 126, 460, h - 104), {
        "FT": Name("Tx"), "T": "fullname", "TU": "Full name",
        "V": values.get("fullname", ""), "DA": "/Helv 11 Tf 0 g",
        "MK": {"BC": [0, 0, 0], "BG": [1, 1, 1]},
    }))

    p.text(LEFT, h - 160, "Subscribe", size=BODY_SIZE)
    checked = values.get("subscribe", False)
    fields.append(p.widget((200, h - 176, 216, h - 160), {
        "FT": Name("Btn"), "T": "subscribe",
        "V": Name("Yes" if checked else "Off"),
        "AS": Name("Yes" if checked else "Off"),
        "MK": {"BC": [0, 0, 0], "BG": [1, 1, 1], "CA": "4"},
    }))

    p.text(LEFT, h - 210, "Plan", size=BODY_SIZE)
    p.text(228, h - 222, "Standard", size=9)
    p.text(288, h - 222, "Premium", size=9)
    # One radio field with two controls: both widgets carry the same /T, which
    # is how pdfium merges them. /Opt gives each control a distinct on state
    # name, so exactly one of them can read as checked without an appearance
    # stream naming the state.
    chosen = values.get("plan")
    for index, x in enumerate((200, 260)):
        state = Name(str(index)) if chosen == index else Name("Off")
        fields.append(p.widget((x, h - 226, x + 16, h - 210), {
            "FT": Name("Btn"), "T": "plan", "Ff": 32768,
            "Opt": ["Standard", "Premium"],
            "V": Name(str(chosen)) if chosen is not None else Name("Off"),
            "AS": state,
            "MK": {"BC": [0, 0, 0], "BG": [1, 1, 1]},
        }))
    return p, fields


def _forms_all_field_types(root):
    d = Document(title="Every Field Type", author="A. Writer")
    p, fields = _form_page_one(d)
    h = p.height

    p.text(LEFT, h - 270, "Comments", size=BODY_SIZE)
    fields.append(p.widget((200, h - 330, 460, h - 264), {
        "FT": Name("Tx"), "T": "comments", "Ff": 4096, "V": "",
        "DA": "/Helv 11 Tf 0 g", "MK": {"BC": [0, 0, 0], "BG": [1, 1, 1]},
    }))

    p.text(LEFT, h - 370, "Reference", size=BODY_SIZE)
    fields.append(p.widget((200, h - 386, 460, h - 364), {
        "FT": Name("Tx"), "T": "reference", "Ff": 1, "V": "PDF-2026-0142",
        "DA": "/Helv 11 Tf 0 g", "MK": {"BC": [0, 0, 0], "BG": [0.9, 0.9, 0.9]},
    }))

    d.paragraph(p, LEFT, h - 430, FILLER, font=BODY_FONT, size=BODY_SIZE,
                leading=BODY_LEADING, max_width=BODY_WIDTH)

    p2 = d.add_page()
    p2.text(LEFT, p2.height - 60, "Continued", font="serif-bold", size=14)
    p2.text(LEFT, p2.height - 110, "Referee", size=BODY_SIZE)
    fields.append(p2.widget((200, p2.height - 126, 460, p2.height - 104), {
        "FT": Name("Tx"), "T": "referee", "TU": "Referee name", "V": "",
        "DA": "/Helv 11 Tf 0 g", "MK": {"BC": [0, 0, 0], "BG": [1, 1, 1]},
    }))
    d.paragraph(p2, LEFT, p2.height - 160, FILLER, font=BODY_FONT,
                size=BODY_SIZE, leading=BODY_LEADING, max_width=BODY_WIDTH)

    path = out_path(root, "forms_all_field_types.pdf")
    refs = _save_with_acroform(d, path)
    if len(refs) != len(fields):
        raise AssertionError(f"{len(refs)} field objects for {len(fields)} widgets")
    manifest.register(
        "forms_all_field_types.pdf", path,
        "2 pages. Page index 0 carries a single line text field with the "
        "alternate name 'Full name', a multiline text field, an unchecked "
        "checkbox, a two option radio group with neither option selected, and "
        "a read only text field holding a value. Page index 1 carries one "
        "further text field so a page turn has somewhere to go",
        pages=2, acroform_fields=len(refs),
        page1_fields=["fullname (Tx, TU 'Full name')",
                      "subscribe (Btn checkbox, V Off)",
                      "plan (Btn radio, two controls, V Off)",
                      "comments (Tx multiline, Ff 4096, no TU)",
                      "reference (Tx read only, Ff 1, V 'PDF-2026-0142')"],
        page2_fields=["referee (Tx, TU 'Referee name')"],
        widget_order="fullname, subscribe, plan[0], plan[1], comments, "
                     "reference, referee",
        radio_on_states="control 0 is '0', control 1 is '1', from /Opt",
        readonly_value="PDF-2026-0142",
        multiline_title_falls_back_to="comments",
        rects="fullname 200 666 460 688, subscribe 200 616 216 632, "
              "plan[0] 200 566 216 582, plan[1] 260 566 276 582, "
              "comments 200 462 460 528, reference 200 406 460 428",
        appearance_streams="none, pdfium generates them on load",
        note="Only text, checkbox and radio are reachable in the app. This "
             "fixture replaces forms_two_pages.pdf",
    ).tag("annotations")


def _form_values(root):
    d = Document(title="Form Document", author="A. Writer")
    p, fields = _form_page_one(d, values={
        "fullname": "Ada Lovelace", "subscribe": True, "plan": 0,
    })
    p2 = d.add_page()
    p2.text(LEFT, p2.height - 60, "Second page", font="serif-bold", size=13)
    p2.text(LEFT, p2.height - 110, "Reference", size=BODY_SIZE)
    fields.append(p2.widget((200, p2.height - 126, 460, p2.height - 104), {
        "FT": Name("Tx"), "T": "reference", "V": "", "DA": "/Helv 11 Tf 0 g",
        "MK": {"BC": [0, 0, 0], "BG": [1, 1, 1]},
    }))
    path = out_path(root, "formvalues.pdf")
    refs = _save_with_acroform(d, path)
    if len(refs) != len(fields):
        raise AssertionError(f"{len(refs)} field objects for {len(fields)} widgets")
    manifest.register(
        "formvalues.pdf", path,
        "The forms.pdf layout with its text field, checkbox and radio group "
        "already filled in, for the filled form rendering baseline",
        pages=2, acroform_fields=len(refs),
        fullname="Ada Lovelace", subscribe="checked, V and AS are Yes",
        plan="first control selected, V and AS are 0",
        page2_fields=["reference (Tx, empty)"],
        geometry="page 1 matches forms.pdf so the two baselines compare",
        difference_from_forms_pdf="carries an /AcroForm, and plan is one radio "
                                  "field with two controls rather than two fields",
    ).tag("annotations")


def _palette(root):
    d = Document(title="Palette", author="A. Writer")
    overrides = {2 + i * 3: phrase for i, (_, phrase) in enumerate(PALETTE_PHRASES)}
    p, rects = _text_page(d, "Six colours on one screen", overrides)
    groups = []
    for i, (name, phrase) in enumerate(PALETTE_PHRASES):
        key = f"palette-{name.lower()}"
        _mj_highlight(p, [rects[2 + i * 3]], PALETTE[name], phrase, key,
                      f"D:2024010{i + 1}120000")
        groups.append(key)
    path = out_path(root, "palette.pdf")
    d.save(path)
    manifest.register(
        "palette.pdf", path,
        "One page carrying six app owned highlights, one in each default "
        "palette colour, each over a distinct phrase and all on one screen at "
        "fit width",
        pages=1, highlight_groups=6, highlight_annotations=6, groups_with_notes=0,
        group_keys=groups,
        colours=[f"{name} 0x{PALETTE[name]:08X}" for name, _ in PALETTE_PHRASES],
        phrases=[phrase for _, phrase in PALETTE_PHRASES],
        line_indexes=[2 + i * 3 for i in range(6)],
        rects=[[round(v, 2) for v in rects[2 + i * 3]] for i in range(6)],
        baseline="a rendering golden is recorded against this file",
    ).tag("annotations")


def _spanning(root):
    d = Document(title="Spanning Sentence", author="A. Writer")
    lines = d.wrap(SPANNING_SENTENCE, BODY_FONT, BODY_SIZE, BODY_WIDTH)
    if len(lines) != 3:
        raise AssertionError(f"the sentence wraps to {len(lines)} lines, three needed")
    overrides = {2: "A short opening line before the long sentence starts."}
    for i, line in enumerate(lines):
        overrides[3 + i] = line
    overrides[6] = "A short closing line after the long sentence ends."
    p, rects = _text_page(d, "One sentence, three lines", overrides)
    path = out_path(root, "spanning.pdf")
    d.save(path)
    manifest.register(
        "spanning.pdf", path,
        "One paragraph in which a single sentence wraps across three rendered "
        "lines, for the multi line highlight rectangle baseline. No highlight "
        "is present; the test creates one",
        pages=1, highlight_annotations=0,
        sentence=SPANNING_SENTENCE, wrapped_lines=lines,
        line_indexes=[3, 4, 5],
        line_rects=[[round(v, 2) for v in rects[3 + i]] for i in range(3)],
        expected_highlight_rectangles=3,
    ).tag("annotations")


def _wrapped_quote(root):
    d = Document(title="Wrapped Quote", author="A. Writer")
    lines = d.wrap(WRAPPED_SENTENCE, BODY_FONT, BODY_SIZE, BODY_WIDTH)
    if len(lines) != 2:
        raise AssertionError(f"the sentence wraps to {len(lines)} lines, two needed")
    overrides = {3: lines[0], 4: lines[1]}
    p, rects = _text_page(d, "One sentence, two lines", overrides)
    path = out_path(root, "wrapped-quote.pdf")
    d.save(path)
    manifest.register(
        "wrapped-quote.pdf", path,
        "One sentence wrapping across two rendered lines, so a highlight over "
        "the whole sentence produces a quote carrying a line break. No "
        "highlight is present; the test creates one",
        pages=1, highlight_annotations=0,
        sentence=WRAPPED_SENTENCE, wrapped_lines=lines,
        expected_quote="\n".join(lines),
        line_indexes=[3, 4],
        line_rects=[[round(v, 2) for v in rects[3 + i]] for i in range(2)],
    ).tag("annotations")


def _line_cubic(points):
    """A polyline in the layout SignatureView reads: x0, y0, then six a segment."""
    out = [points[0][0], points[0][1]]
    for (sx, sy), (ex, ey) in zip(points, points[1:]):
        out += [sx + (ex - sx) / 3.0, sy + (ey - sy) / 3.0,
                sx + 2.0 * (ex - sx) / 3.0, sy + 2.0 * (ey - sy) / 3.0,
                ex, ey]
    return [round(v, 6) for v in out]


def _signature(root):
    strokes = [
        _line_cubic([(0.0, 0.30), (0.25, 0.05), (0.50, 0.35), (0.75, 0.05),
                     (1.0, 0.30)]),
        _line_cubic([(0.12, 0.20), (0.88, 0.20)]),
    ]
    for stroke in strokes:
        if len(stroke) < 2 or (len(stroke) - 2) % 6 != 0:
            raise AssertionError("a stroke must be two floats plus six a segment")
    aspect = 0.4
    xs = [v for s in strokes for v in s[0::2]]
    ys = [v for s in strokes for v in s[1::2]]
    if min(xs) < 0.0 or max(xs) > 1.0 or min(ys) < 0.0 or max(ys) > aspect:
        raise AssertionError("strokes must stay inside the normalized box")
    # SignatureData.color is an Int, so the value is the signed form of
    # 0xFF1A237E rather than the unsigned one Gson would reject.
    data = {
        "aspect": aspect,
        "strokeWidth": 0.02,
        "color": 0xFF1A237E - (1 << 32),
        "strokes": strokes,
    }
    path = out_path(root, "signature.json")
    with open(path, "w") as fh:
        json.dump(data, fh, separators=(",", ":"), sort_keys=False)
        fh.write("\n")
    manifest.register(
        "signature.json", path,
        "A SignatureData document for filesDir/signature/signature.json, so "
        "stamp geometry is fixed without drawing by hand. Not a PDF",
        strokes=len(strokes),
        stroke_float_counts=[len(s) for s in strokes],
        stroke_layout="x0, y0, then six floats a cubic segment",
        aspect=aspect, stroke_width=0.02,
        color=data["color"], color_argb="0xFF1A237E",
        coordinate_space="x in 0 to 1, y in 0 to aspect",
        target="filesDir/signature/signature.json",
    ).tag("annotations")


def _huge(root):
    """600 pages carrying 400 groups, four to a page on every sixth page."""
    d = Document(title="Six Hundred Pages", author="A. Writer")
    group_pages = list(range(0, 600, 6))
    if len(group_pages) * 4 != 400:
        raise AssertionError(f"{len(group_pages) * 4} groups, 400 specified")
    groups = 0
    notes = 0
    note_pages = []
    for i in range(600):
        p = d.add_page()
        p.text(LEFT, p.height - 60, f"Page {i + 1}", font="serif-bold", size=13)
        lines = d.wrap(FILLER * 2, BODY_FONT, BODY_SIZE, BODY_WIDTH)[:14]
        if len(lines) < 14:
            raise AssertionError("the huge fixture page is short of lines")
        y = BODY_TOP
        rects = []
        for line in lines:
            p.text(LEFT, y, line, font=BODY_FONT, size=BODY_SIZE)
            rects.append(d.line_rect(p, LEFT, y, line, BODY_FONT, BODY_SIZE))
            y -= BODY_LEADING
        if i not in group_pages:
            continue
        for n in range(4):
            key = f"huge-{groups + 1:04d}"
            note = None
            if groups % 10 == 0:
                note = f"note number {groups // 10 + 1}"
                notes += 1
                note_pages.append(i)
            line_index = 1 + n * 3
            _mj_highlight(p, [rects[line_index]], PALETTE["YELLOW"],
                          f"group {groups + 1} on page {i + 1}: {lines[line_index]}",
                          key, f"D:2024{1 + groups % 12:02d}{1 + groups % 28:02d}"
                               f"{groups % 24:02d}{groups % 60:02d}00", note)
            groups += 1
    if groups != 400:
        raise AssertionError(f"{groups} groups written, 400 specified")
    if notes != 40:
        raise AssertionError(f"{notes} notes written, 40 specified")
    path = out_path(root, "highlights-huge.pdf")
    d.save(path)
    manifest.register(
        "highlights-huge.pdf", path,
        "600 pages carrying 400 app owned highlight groups, four to a page on "
        "every sixth page, 40 of them with a note. The slowest fixture here",
        pages=600, highlight_groups=groups, highlight_annotations=groups,
        groups_with_notes=notes, groups_per_marked_page=4,
        marked_page_indexes="every sixth page, 0 to 594",
        marked_pages=len(group_pages),
        noted_groups="every tenth group, huge-0001 then huge-0011 and so on",
        note_page_indexes=_csv(note_pages),
        scan_progress_max=600, expected_adapter_count=400,
        expected_notes_adapter_count=40,
        colour="0xFFFFF176 on all groups",
        first_group_key="huge-0001", last_group_key=f"huge-{groups:04d}",
        last_group_page_index=group_pages[-1],
    ).tag("annotations", "slow")
