"""Search, text mode, selection and localization fixtures.

Every occurrence count in this module is counted from the strings actually
drawn, never asserted from intent: each builder records the lines it draws and
_count_pages walks them the way Kotlin's Regex.findAll does, non overlapping and
case insensitive. The counts then go straight into the manifest, so a fixture
whose prose drifts fails the build rather than a test on the emulator.

Two things about the search fixtures are load bearing. The searched text is
PdfiumCore.getPageText, which is NFKC normalized, so a U+FB03 drawn as one raw
character is matched as "ffi". And matching is a literal escaped regex, so the
page 12 punctuation specimens match only themselves.

search_text.pdf forbids a set of substrings everywhere except where a test wants
them: a stray "chapter" in the filler would break SRCH-28's count of 3. The ban
list is checked over the whole document at build time.

text-structured.pdf carries the only library workaround here. lib/doc.py writes
/Flags 4 for every embedded font, so pdfium reports no fixed pitch face and the
text mode code block detector never fires. The save damage hook rewrites that
one descriptor to /Flags 5, which is the same byte length and leaves the cross
reference table valid. Real producers set the flag; the library cannot.
"""

import math

from lib import manifest
from lib.doc import Document
from lib.registry import fixture_group, out_path

BODY_SIZE = 11.0
BODY_LEADING = 15.0
MARGIN = 72.0
BODY_WIDTH = 468.0
TOP_LINE = 695.0

FILLER_A = (
    "The keeper of the light wrote his notes in a slender ledger, one line for "
    "every night of the winter, and the ledger outlived him by sixty years. He "
    "counted the ships that went by, the weather, and the hours he slept. "
    "Nothing in the ledger is of note, and that is the reason it survives, "
    "since no one thought to throw it out. The paper is thin and the ink has "
    "gone the colour of weak tea, yet every entry is still legible to a patient "
    "eye. A reader who follows it from the first page to the last learns the "
    "shape of one winter, told in the fewest words the keeper could manage. "
)

FILLER_B = (
    "In the third winter the lens cracked and the keeper set two lamps in its "
    "place, which he said gave a poorer light but a steadier one. He wrote that "
    "the fishermen did not notice, and he wrote it twice, once in the ledger "
    "and once on the wall of the store room where it is still legible. The "
    "store room is gone now, with the pier and the two sheds, and the ledger is "
    "the only thing left of the whole station. "
)

# Substrings no filler line may contain, because a test counts them elsewhere.
SEARCH_TEXT_BANNED = (
    "beacon", "kalliope", "chapter", "zephyrus", "quick", "brown", "hyphen",
    "obsidian", "group", "bracket", "ab", "a.b", "a*b", "x+y",
)


def _count_pages(pages, needle):
    """Pages, one based, carrying each non overlapping case insensitive hit.

    Kotlin's Regex.findAll does not overlap, and SearchCoordinator matches over
    one page at a time, so counting must do both.
    """
    hits = []
    lowered = needle.lower()
    for index, lines in enumerate(pages):
        text = "\n".join(lines).lower()
        start = 0
        while True:
            found = text.find(lowered, start)
            if found < 0:
                break
            hits.append(index + 1)
            start = found + len(lowered)
    return hits


def _expect(actual, expected, what):
    if actual != expected:
        raise AssertionError(f"{what}: counted {actual}, fixture requires {expected}")


def _check_widths(doc, lines, font, size, max_width):
    f = doc.font(font)
    for line in lines:
        if f.text_width(line, size) > max_width:
            raise AssertionError(f"line overflows {max_width:.0f}pt: {line!r}")


def _draw_lines(doc, page, lines, x=MARGIN, top=TOP_LINE, font="serif",
                size=BODY_SIZE, leading=BODY_LEADING):
    _check_widths(doc, lines, font, size, page.width - 2 * x)
    y = top
    for line in lines:
        page.text(x, y, line, font=font, size=size)
        y -= leading
    return y


def _fixed_pitch_damage(font_key):
    """Turn on the FixedPitch flag for one embedded font descriptor.

    Same byte length, so every cross reference offset stays correct.
    """
    needle = f"/FontName /MJFixture+{font_key} /Flags 4".encode("ascii")
    patched = needle.replace(b"/Flags 4", b"/Flags 5")

    def damage(data):
        if data.count(needle) != 1:
            raise AssertionError(f"expected exactly one {font_key} descriptor")
        return data.replace(needle, patched)

    return damage


@fixture_group("text_search", "Search, text mode, selection and localization")
def build(root):
    _search_text(root)
    _search_accents(root)
    _search_ligature(root)
    _search_digits(root)
    _ligatures(root)
    _text_structured(root)
    _text_single_page(root)
    _selection_latin(root)
    _selection_url(root)
    _selection_long_quote(root)
    _long_paragraph(root)
    _selection_arabic(root)
    _arabic_ligature(root)
    _persian_digits(root)
    _cyrillic_case(root)
    _turkish_case(root)
    _long_title(root)
    _mixed_script(root)
    _known_text(root)


# Search --------------------------------------------------------------------

LINES_PER_PAGE = 38

# page number (one based) -> line index on that page -> the exact line drawn.
# Line breaks matter for SRCH-37 and SRCH-38, so these are placed rather than
# wrapped. Index 36 is the second to last line, which is what SRCH-51 needs.
SEARCH_TEXT_PLACED = {
    1: {2: "The quick brown fox slipped past the still water at dusk."},
    2: {2: "Chapter markers were once written in the margin by hand."},
    3: {
        2: "The fox that crossed the yard at first light was quick",
        3: "brown, and gone before the door had finished closing.",
    },
    4: {
        2: "The beacon on the point was lit at dusk by a man with one lantern.",
        36: "Sailors said the beacon could be seen twelve miles out at sea.",
    },
    5: {2: "Each chapter of the ledger was sewn in by the keeper himself."},
    6: {
        2: "The typesetter set the word in two halves with a hyphen-",
        3: "ation that the printer took out of the second edition.",
    },
    7: {
        2: "A second beacon stood on the far shore and was never once lit.",
        20: "The beacon there was a gift and no one would say from whom.",
    },
    8: {2: "The word CHAPTER was stamped in the head margin of the sheet."},
    9: {2: "The ship kalliope put in at the harbour on a still evening."},
    10: {2: "The printer preferred hyphenation to the loose spacing of a line."},
    11: {
        2: "The obsidian beacon on the headland was cut from a single stone.",
        20: "No one recalls who set the obsidian beacon there, or in which year.",
    },
    12: {
        2: "The first note reads a.b and says nothing more than that.",
        4: "A second note reads a*b in the outer margin of the sheet.",
        6: "The editor wrote (group) beside the third line of the entry.",
        8: "The formula x+y stood alone in the middle of the page.",
        10: "The index carried [bracket] where a title should have stood.",
        12: "The last note is the plain word ab and nothing else at all.",
    },
}


def _search_text(root):
    d = Document(title="Search Fixture", author="M. Fixture")
    filler = d.wrap(FILLER_A + FILLER_B, "serif", BODY_SIZE, BODY_WIDTH)

    pages = []
    for index in range(12):
        placed = SEARCH_TEXT_PLACED[index + 1]
        lines = []
        for i in range(LINES_PER_PAGE):
            if i in placed:
                lines.append(placed[i])
            else:
                lines.append(filler[(index * 7 + i) % len(filler)])
        pages.append(lines)

    # The filler is reused on every page, so one banned substring in it would
    # multiply into a wrong count twelve times over.
    for index, lines in enumerate(pages):
        placed = set(SEARCH_TEXT_PLACED[index + 1].values())
        for line in lines:
            if line in placed:
                continue
            for banned in SEARCH_TEXT_BANNED:
                if banned in line.lower():
                    raise AssertionError(f"filler line carries {banned!r}: {line!r}")

    beacon = _count_pages(pages, "beacon")
    _expect(beacon, [4, 4, 7, 7, 11, 11], "beacon hits")
    _expect(_count_pages(pages, "kalliope"), [9], "kalliope hits")
    _expect(_count_pages(pages, "chapter"), [2, 5, 8], "chapter hits")
    _expect(_count_pages(pages, "zephyrus"), [], "zephyrus hits")
    _expect(_count_pages(pages, "quick"), [1, 3], "quick hits")
    _expect(_count_pages(pages, "quick brown"), [1], "quick brown hits")
    _expect(_count_pages(pages, "hyphen"), [6, 10], "hyphen hits")
    _expect(_count_pages(pages, "hyphenation"), [10], "hyphenation hits")
    _expect(_count_pages(pages, "obsidian"), [11, 11], "obsidian hits")
    for literal in ("a.b", "a*b", "(group)", "x+y", "[bracket]"):
        _expect(_count_pages(pages, literal), [12], f"{literal} hits")
    _expect(_count_pages(pages, "ab"), [12], "plain ab hits")

    for index, lines in enumerate(pages):
        p = d.add_page()
        p.text(MARGIN, p.height - 60, f"Page {index + 1}", font="serif-bold",
               size=13)
        _draw_lines(d, p, lines)

    path = out_path(root, "search_text.pdf")
    d.save(path)
    manifest.register(
        "search_text.pdf", path,
        "12 pages of single column Latin. The search surface's counting "
        "fixture: every hit count below was counted from the drawn lines",
        pages=12,
        beacon_hits=6, beacon_pages=beacon,
        kalliope_hits=1, kalliope_page=9,
        chapter_hits=3, chapter_pages=[2, 5, 8],
        chapter_casing="Chapter page 2, chapter page 5, CHAPTER page 8",
        zephyrus_hits=0,
        quick_hits=2, quick_pages=[1, 3],
        quick_brown_hits=1, quick_brown_page=1,
        line_break_split="page 3 line 3 ends 'quick', line 4 starts 'brown'",
        hyphen_hits=2, hyphen_pages=[6, 10],
        hyphenation_hits=1, hyphenation_page=10,
        hyphen_split="page 6 line 3 ends 'hyphen-', line 4 starts 'ation'",
        literal_specimens=["a.b", "a*b", "(group)", "x+y", "[bracket]"],
        literal_page=12, plain_ab_hits=1, plain_ab_page=12,
        srch21_filter_word="obsidian",
        srch21_note="obsidian appears twice, both on page 11, one beside each "
                    "page 11 beacon, so filtering to it leaves 2 rows",
        srch51_low_hit="beacon hit 2 of 6, the second occurrence on page 4, "
                       "baseline 155 pt up from the foot of a 792 pt page, so "
                       "80 percent down it. The on screen fraction must still "
                       "be recorded per emulator profile",
        srch51_high_hit="beacon hit 1 of 6, the first occurrence on page 4, "
                        "baseline 665 pt, 16 percent down the page",
        srch53_note="not the password fixture; core.py owns protected.pdf",
        lines_per_page=LINES_PER_PAGE,
    ).tag("text_search")


def _search_accents(root):
    d = Document(title="Accent Fixture")
    page_lines = [
        [
            "The cafe on the corner opened at six and closed at noon.",
            "Its neighbour, a café with two tables, opened later and closed later.",
            "Neither of them has kept a sign, and neither is missed by anyone.",
        ],
        [
            "The clerk spoke čeština at home and German at the counter.",
            "He had come from Zürich, and wrote it Zurich on every form.",
            "The forms were filed and nobody read a single one of them.",
        ],
    ]
    _expect(_count_pages(page_lines, "café"), [1], "café hits")
    _expect(_count_pages(page_lines, "cafe"), [1], "cafe hits")
    _expect(_count_pages(page_lines, "čeština"), [2], "čeština hits")
    _expect(_count_pages(page_lines, "Zürich"), [2], "Zürich hits")
    _expect(_count_pages(page_lines, "Zurich"), [2], "Zurich hits")

    for index, lines in enumerate(page_lines):
        p = d.add_page()
        p.text(MARGIN, p.height - 60, f"Page {index + 1}", font="serif-bold",
               size=13)
        _draw_lines(d, p, lines)

    path = out_path(root, "search_accents.pdf")
    d.save(path)
    manifest.register(
        "search_accents.pdf", path,
        "2 pages. Accent folding fixture for SRCH-29 and SRCH-30",
        pages=2,
        cafe_plain_hits=1, cafe_plain_page=1,
        cafe_accented_hits=1, cafe_accented_page=1,
        cestina_hits=1, cestina_page=2,
        zurich_umlaut_hits=1, zurich_plain_hits=1, zurich_pages=[2, 2],
        accents_off="cafe finds 1, cafe finds the plain spelling only",
        accents_on="cafe finds 2, cestina finds 1, zurich finds 2",
        note="e acute is one precomposed U+00E9, not e plus a combining mark",
    ).tag("text_search")


def _search_ligature(root):
    d = Document(title="Ligature Fixture")
    # The ligature is one raw character. NFKC in getPageText expands it, which
    # is how a search for the plain spelling reaches it at all.
    lines = [
        "The clerk kept an oﬃce on the second floor above the yard.",
        "He wrote the ﬁnal draft there and left it on the desk.",
        "There was coffee on the desk as well, and it had gone cold.",
    ]
    raw = "\n".join(lines)
    _expect(raw.count("ﬃ"), 1, "ffi ligature characters")
    _expect(raw.count("ﬁ"), 1, "fi ligature characters")
    _expect(raw.count("coffee"), 1, "plain coffee")
    # No plain spelling of the two ligature words anywhere on the page, so each
    # query has exactly one place to land.
    _expect(raw.lower().count("office"), 0, "plain office spellings")
    _expect(raw.lower().count("final"), 0, "plain final spellings")
    normalized = raw.replace("ﬃ", "ffi").replace("ﬁ", "fi")
    _expect(normalized.lower().count("office"), 1, "normalized office hits")
    _expect(normalized.lower().count("final"), 1, "normalized final hits")

    p = d.add_page()
    p.text(MARGIN, p.height - 60, "Ligatures", font="serif-bold", size=13)
    _draw_lines(d, p, lines)
    path = out_path(root, "search_ligature.pdf")
    d.save(path)
    manifest.register(
        "search_ligature.pdf", path,
        "1 page. office typeset with U+FB03 as one raw character, final with "
        "U+FB01, plus the plain word coffee",
        pages=1,
        ffi_word="office, drawn as o + U+FB03 + ce",
        fi_word="final, drawn as U+FB01 + nal",
        plain_word="coffee",
        office_query_hits=1, final_query_hits=1, coffee_query_hits=1,
        note="the raw text holds the ligature characters, the searched text is "
             "NFKC normalized so the plain spelling matches",
    ).tag("text_search")


def _ligatures(root):
    """The rendering baselines fixture. Distinct from search_ligature.pdf.

    SRCH-32 asserts one hit for the ligature word, so the non ligature word on
    the page shares the letter sequence without being the same word: a second
    plain spelling of the same word would make the count 2.
    """
    d = Document(title="Ligature Baselines")
    lines = [
        "The oﬃce was lit until the last of them had gone home.",
        "The ﬁnal entry in the ledger is the one nobody ever checks.",
        "A ﬂourish of the pen, and the light on the water moved on.",
        "Set beside them, sufficient and figure and reflect read plainly.",
    ]
    raw = "\n".join(lines)
    normalized = (raw.replace("ﬃ", "ffi").replace("ﬁ", "fi")
                  .replace("ﬂ", "fl"))
    _expect(raw.count("ﬃ"), 1, "ffi ligature characters")
    _expect(raw.count("ﬁ"), 1, "fi ligature characters")
    _expect(raw.count("ﬂ"), 1, "fl ligature characters")
    for word, count in (("office", 1), ("final", 1), ("flourish", 1),
                        ("sufficient", 1), ("figure", 1), ("reflect", 1)):
        _expect(normalized.lower().count(word), count, f"normalized {word}")

    p = d.add_page()
    p.text(MARGIN, p.height - 60, "Ligatures", font="serif-bold", size=13)
    _draw_lines(d, p, lines, size=13.0, leading=20.0)
    path = out_path(root, "ligatures.pdf")
    d.save(path)
    manifest.register(
        "ligatures.pdf", path,
        "1 page, 13 pt serif. fi, fl and ffi drawn as single ligature glyphs, "
        "each with the same letter sequence spelled plainly on the same page",
        pages=1,
        ffi_ligature_word="office", ffi_plain_word="sufficient",
        fi_ligature_word="final", fi_plain_word="figure",
        fl_ligature_word="flourish", fl_plain_word="reflect",
        office_query_hits=1, final_query_hits=1, flourish_query_hits=1,
        sufficient_query_hits=1, figure_query_hits=1, reflect_query_hits=1,
        srch32_note="the ligature word and the plain word are different words "
                    "sharing a letter sequence, which is what makes the query "
                    "yield exactly 1. prerequisites.md reads as if the same "
                    "word should appear in both forms, which would yield 2",
        vis41_note="a golden is recorded against this file, so regenerating it "
                   "invalidates that golden",
    ).tag("text_search")


def _search_digits(root):
    d = Document(title="Digit Fixture")
    # No installed font has fullwidth digits, so the ASCII glyphs are drawn and
    # the ToUnicode map declares the fullwidth codepoints instead.
    ascii_digits = "12345"
    fullwidth = "１２３４５"
    arabic_indic = "١٢٣٤٥"

    p = d.add_page()
    p.text(MARGIN, p.height - 60, "Digits", font="serif-bold", size=13)
    p.text(MARGIN, 700, "Latin digits", size=BODY_SIZE)
    p.text(MARGIN + 120, 700, ascii_digits, size=BODY_SIZE)
    p.text(MARGIN, 670, "Fullwidth digits", size=BODY_SIZE)
    p.text(MARGIN + 120, 670, ascii_digits, font="serif@fullwidth",
           size=BODY_SIZE)
    p.text(MARGIN, 640, "Arabic-Indic digits", size=BODY_SIZE)
    p.text(MARGIN + 120, 640, arabic_indic, font="sans", size=BODY_SIZE)
    d.alias_unicode("serif@fullwidth", ascii_digits, fullwidth)

    extracted = [[
        "Digits", "Latin digits", ascii_digits, "Fullwidth digits", fullwidth,
        "Arabic-Indic digits", arabic_indic,
    ]]
    _expect(_count_pages(extracted, ascii_digits), [1], "ascii digit hits")
    _expect(_count_pages(extracted, fullwidth), [1], "fullwidth digit hits")
    _expect(_count_pages(extracted, arabic_indic), [1], "arabic-indic hits")

    path = out_path(root, "search_digits.pdf")
    d.save(path)
    manifest.register(
        "search_digits.pdf", path,
        "1 page. 12345 in ASCII, fullwidth and Arabic-Indic digits, one "
        "occurrence each",
        pages=1,
        ascii_hits=1, fullwidth_hits=1, arabic_indic_hits=1,
        fullwidth_string=fullwidth, arabic_indic_string=arabic_indic,
        accents_off="the ASCII query finds 1, neither other form",
        accents_on="the ASCII query finds 2, ASCII and fullwidth, because NFKD "
                   "folds fullwidth digits to ASCII, and never the Arabic-Indic",
        note="the fullwidth run draws the ASCII glyphs and declares the "
             "fullwidth codepoints in ToUnicode, because no installed font has "
             "a fullwidth glyph. The page shows 12345 twice",
    ).tag("text_search")


# Text mode -----------------------------------------------------------------

STRUCTURED_P1_A = [
    "The reading of a document is a quiet negotiation between the page and",
    "the eye, and the negotiation is settled long before the reader is aware",
    "that it has begun. Each paragraph offers a shape, and the reader either",
    "accepts that shape or refuses it and reads the page some other way,",
    "which is the reader's right and the typesetter's quiet defeat.",
]

STRUCTURED_P1_B = [
    "Where the text is dense the eye slows, and where it opens out the eye",
    "runs ahead of the understanding and has to be called back. This is the",
    "ordinary rhythm of reading, and a reader that respects it disappears",
    "into the background of the work it is doing.",
]

STRUCTURED_P3 = [
    "The binder called the finished volume an extraor-",
    "dinary piece of work, and then charged for it at the ordinary rate,",
    "which the keeper recorded in the ledger without any comment at all.",
]

STRUCTURED_P4_HEAD = [
    "The lens itself was a magniﬁcent thing, ground in three stages by a",
    "firm that closed the year after it was fitted, and the keeper wrote",
]
STRUCTURED_P4_TAIL = " that no one had ever built its equal on that coast."


def _text_structured(root):
    d = Document(title="Structured Text", author="M. Fixture")
    body = BODY_SIZE
    heading_size = 15.4          # 1.4 times body, past the H1 ratio of 1.35

    p1 = d.add_page()
    p1.text(MARGIN, 700, "Chapter One", font="serif-bold", size=heading_size)
    y = _draw_lines(d, p1, STRUCTURED_P1_A, top=660)
    # A gap wider than 1.45 median leadings, which is what stops the structured
    # formatter merging the two paragraphs into one.
    _draw_lines(d, p1, STRUCTURED_P1_B, top=y - 11)

    p2 = d.add_page()
    intro = [
        "The keeper's instructions were pinned inside the door of the store",
        "room, and they were short enough to be read at a glance:",
    ]
    y = _draw_lines(d, p2, intro, top=TOP_LINE)
    bullets = [
        "• Trim the wick before the light is lit, never after.",
        "• Enter the weather at midnight and again at four.",
        "• Never leave the lamp room while the lamp is burning.",
    ]
    y = _draw_lines(d, p2, bullets, top=y - 10)
    numbered = [
        "1. Sign the ledger at the end of every watch.",
        "2. Report a cracked lens to the board the same day.",
    ]
    y = _draw_lines(d, p2, numbered, top=y - 10)
    code_lines = [
        ("sum = 0", 0),
        ("for value in readings:", 0),
        ("sum += value", 4),
        ("print(sum)", 0),
    ]
    cell = d.font("mono").width("m", body)
    code_top = y - 20
    for offset, (text, indent) in enumerate(code_lines):
        p2.text(MARGIN + indent * cell, code_top - offset * BODY_LEADING, text,
                font="mono", size=body)

    p3 = d.add_page()
    _draw_lines(d, p3, STRUCTURED_P3, top=TOP_LINE)

    p4 = d.add_page()
    y = _draw_lines(d, p4, STRUCTURED_P4_HEAD, top=TOP_LINE)
    # The footnote marker is a smaller run raised off the baseline, which is the
    # only thing StructuredTextFormatter looks at to call it a superscript.
    right = p4.text(MARGIN, y, "in his own hand", size=body)
    right = p4.text(right, y + 4.0, "1", size=7.0)
    p4.text(right, y, STRUCTURED_P4_TAIL, size=body)
    p4.text(MARGIN, y - 40, "1 The keeper's hand is steady until the last year.",
            size=8.5)

    deepest = max(indent for _, indent in code_lines)
    _expect(deepest, 4, "deepest code indent in cells")
    _expect(len(code_lines), 4, "code block lines")
    _expect(len(bullets), 3, "bullet items")
    _expect(len(numbered), 2, "numbered items")
    page4_text = " ".join(STRUCTURED_P4_HEAD) + " in his own hand1" + STRUCTURED_P4_TAIL
    _expect(page4_text.replace("ﬁ", "fi").count("magnificent"), 1,
            "magnificent after ligature expansion")

    path = out_path(root, "text-structured.pdf")
    d.save(path, damage=_fixed_pitch_damage("mono"))
    manifest.register(
        "text-structured.pdf", path,
        "4 pages of known Latin structure: heading, two hard wrapped "
        "paragraphs, lists, a code block, a hyphen wrapped word and a "
        "superscript footnote marker",
        pages=4,
        heading="Chapter One", heading_size=heading_size, body_size=body,
        heading_ratio=round(heading_size / body, 3),
        page1_paragraph_1=" ".join(STRUCTURED_P1_A),
        page1_paragraph_2=" ".join(STRUCTURED_P1_B),
        page1_paragraph_lines=[len(STRUCTURED_P1_A), len(STRUCTURED_P1_B)],
        page2_bullets=bullets, page2_numbered=numbered,
        page2_code_lines=[text for text, _ in code_lines],
        page2_code_indent_cells=[indent for _, indent in code_lines],
        page2_code_cell_points=round(cell, 3),
        page3_hyphen_wrap="extraor- at the end of line 1, dinary at the start "
                          "of line 2, joining to extraordinary",
        page4_ligature="magnificent, drawn as magni + U+FB01 + cent",
        page4_footnote="a 7 pt '1' raised 4 pt off the 11 pt baseline",
        fixed_pitch_flag="the mono descriptor is patched to /Flags 5 after "
                         "writing, because lib/doc.py hardcodes /Flags 4 and "
                         "pdfium reads the code block detector's input from it",
        reflow_manifest="the exact reflowed strings TXT-06, TXT-07 and TXT-09 "
                        "compare against must be captured from the app's own "
                        "output on device; the strings above are the source "
                        "lines, not the reflowed result",
    ).tag("text_search")


def _text_single_page(root):
    d = Document(title="Single Page Text")
    p = d.add_page()
    lines = [
        "The store room held one lamp, two cans of oil, a coil of rope and a",
        "ledger, and that was the whole inventory of the place for nine years.",
    ]
    _draw_lines(d, p, lines, top=TOP_LINE)
    path = out_path(root, "text-single-page.pdf")
    d.save(path)
    manifest.register(
        "text-single-page.pdf", path,
        "Exactly one page holding one short paragraph, for the text mode page "
        "control on a document that cannot be paged",
        pages=1, paragraph=" ".join(lines), lines=2,
    ).tag("text_search")


# Selection -----------------------------------------------------------------

SELECTION_LINES = [
    "The quick brown fox jumps over the lazy dog.",
    "Selection, punctuation; and symbols: kept intact.",
    None,                                            # the mixed direction line
    "The council met that spring to plan a truly inter-",
    "national gathering, and argued for a week about the date.",
    "The date they settled on was the one nobody had asked for.",
    "A short line.",
    "Two further lines of ordinary body text follow it here, so that a drag",
    "can begin on one of them and finish somewhere on the other one.",
]

SELECTION_LINE_3 = "The word الكتاب means the book in Arabic."


def _selection_latin(root):
    from lib import arabic

    d = Document(title="Selection Fixture", author="M. Fixture")
    p1 = d.add_page()
    size = 12.0
    leading = 20.0
    y = TOP_LINE
    for line in SELECTION_LINES:
        if line is None:
            # Latin and Arabic as separate positioned runs. The line's first
            # strong character is Latin, so pdfium's bidi rebuilds it in
            # logical order and SEL-50 gets the string it expects.
            head, word, tail = "The word ", "الكتاب", " means the book in Arabic."
            right = p1.text(MARGIN, y, head, size=size)
            arabic_width = d.font("sans").glyphs_width(arabic.shape(word), size)
            p1.text_rtl(right + arabic_width, y, word, font="sans", size=size)
            p1.text(right + arabic_width, y, tail, size=size)
        else:
            _check_widths(d, [line], "serif", size, BODY_WIDTH)
            p1.text(MARGIN, y, line, size=size)
        y -= leading

    p2 = d.add_page()
    page2_lines = [
        "The second page carries nothing the first page carries, so a drag",
        "that runs off the foot of page one has somewhere different to land.",
        "Ships, weather, oil and rope: the whole of the keeper's vocabulary.",
    ]
    _draw_lines(d, p2, page2_lines, top=TOP_LINE, size=size, leading=leading)

    drawn = [line for line in SELECTION_LINES if line is not None]
    _expect(len(SELECTION_LINES), 9, "page 1 lines")
    _expect(SELECTION_LINES[3].endswith("inter-"), True, "line 4 ending")
    _expect(SELECTION_LINES[4].startswith("national"), True, "line 5 start")
    overlap = set(drawn) & set(page2_lines)
    _expect(overlap, set(), "page 2 lines that repeat page 1")

    path = out_path(root, "selection_latin.pdf")
    d.save(path)
    manifest.register(
        "selection_latin.pdf", path,
        "2 pages, 12 pt serif, one column. Page 1 holds nine placed lines with "
        "the exact contents the selection tests drag over",
        pages=2, font_size=size, line_leading=leading,
        title="Selection Fixture", author="M. Fixture",
        page1_line_1=SELECTION_LINES[0],
        page1_line_2=SELECTION_LINES[1],
        page1_line_3=SELECTION_LINE_3,
        page1_line_4=SELECTION_LINES[3],
        page1_line_5=SELECTION_LINES[4],
        page1_line_7=SELECTION_LINES[6],
        page1_lines=9,
        hyphen_wrap="line 4 ends inter-, line 5 begins national, so pdfium "
                    "emits its U+FFFE soft hyphen marker between them",
        mixed_direction_line="line 3, drawn as three positioned runs so the "
                             "Arabic is not reordered into the Latin",
        page2_paragraph=" ".join(page2_lines),
        page2_overlap="none, so a cross page drag lands on different words",
    ).tag("text_search")


def _selection_url(root):
    d = Document(title="URL Fixture")
    p = d.add_page()
    lines = [
        "Visit www.example.com/docs for details.",
        "See https://example.org/a for more.",
    ]
    _draw_lines(d, p, lines, top=TOP_LINE, size=12.0, leading=20.0)
    _expect(_count_pages([lines], "www.example.com/docs"), [1], "bare host hits")
    _expect(_count_pages([lines], "https://example.org/a"), [1], "scheme hits")
    path = out_path(root, "selection_url.pdf")
    d.save(path)
    manifest.register(
        "selection_url.pdf", path,
        "1 page carrying one bare host URL and one URL with a scheme, one "
        "occurrence each",
        pages=1, line_1=lines[0], line_2=lines[1],
        bare_host="www.example.com/docs", with_scheme="https://example.org/a",
    ).tag("text_search")


LONG_QUOTE = (
    "The ledger was never meant to be read by anyone but the man who kept it, "
    "which is exactly why it reads so well now, a hundred and thirty years "
    "after the last entry was made in it. There is no audience in the prose "
    "and therefore no performance, only a man setting down what the weather "
    "did and which ships went by and how long he slept, night after night, in "
    "a hand that never once hurried and never once wandered from the line he "
    "had ruled for it in pencil before he began. The result is a book with no "
    "reader in mind at all, and it is the better for it on every one of the "
    "four hundred pages the binder gave him."
)


def _selection_long_quote(root):
    d = Document(title="Long Quote")
    p = d.add_page()
    lines = d.wrap(LONG_QUOTE, "serif", 12.0, BODY_WIDTH)
    _draw_lines(d, p, lines, top=TOP_LINE, size=12.0, leading=18.0)
    length = len(LONG_QUOTE)
    if length < 600:
        raise AssertionError(f"long quote is {length} characters, needs 600")
    path = out_path(root, "selection_long_quote.pdf")
    d.save(path)
    manifest.register(
        "selection_long_quote.pdf", path,
        "1 page whose only paragraph is one block of Latin prose past the "
        "quote card length cap",
        pages=1, paragraph_characters=length, paragraph_lines=len(lines),
        paragraph=LONG_QUOTE,
        note="long-paragraph.pdf is the shorter 400 character cap and is a "
             "different file on purpose",
    ).tag("text_search")


LONG_PARAGRAPH = (
    "The lamp room was reached by a stair of forty one steps, and the keeper "
    "counted them aloud on the way up every night of his service, not because "
    "he might lose count but because the counting kept him awake at the hour "
    "when a man is least able to trust himself on a dark stair in winter. "
    "He never missed a step in all of those nine winters, and never once said "
    "so to any one who came out to that light."
)


def _long_paragraph(root):
    d = Document(title="Long Paragraph")
    p = d.add_page()
    lines = d.wrap(LONG_PARAGRAPH, "serif", 12.0, BODY_WIDTH)
    _draw_lines(d, p, lines, top=TOP_LINE, size=12.0, leading=18.0)
    _expect(len(LONG_PARAGRAPH), 400, "long paragraph characters")
    path = out_path(root, "long-paragraph.pdf")
    d.save(path)
    manifest.register(
        "long-paragraph.pdf", path,
        "1 page whose first paragraph is one selectable block for VIS-46's "
        "quote card length cap",
        pages=1, paragraph_characters=len(LONG_PARAGRAPH),
        paragraph_lines=len(lines), paragraph=LONG_PARAGRAPH,
    ).tag("text_search")


ARABIC_SELECTION_LINES = [
    "قرأ الطالب "
    "الْكِتَابُ "
    "في المكتبة "
    "الكبيرة أمس",
    "وكتب على الورق"
    " ما فهمه من الفصل"
    " الأول",
    "ثم أغلق الدفتر"
    " وخرج من الباب"
    " قبل المغرب",
]


def _selection_arabic(root):
    d = Document(title="اختيار عربي")
    p = d.add_page()
    right = p.width - MARGIN
    y = TOP_LINE
    for line in ARABIC_SELECTION_LINES:
        p.text_rtl(right, y, line, font="sans", size=14.0)
        y -= 24.0

    first_words = ARABIC_SELECTION_LINES[0].split()
    _expect(len(first_words) >= 6, True, "words on the first line")
    _expect(first_words[2], "الْكِتَابُ",
            "third word of the first line")

    path = out_path(root, "selection_arabic.pdf")
    d.save(path)
    manifest.register(
        "selection_arabic.pdf", path,
        "1 page of vocalised Arabic prose, right aligned, shaped into "
        "presentation forms the way a real Arabic PDF stores them",
        pages=1, lines=len(ARABIC_SELECTION_LINES),
        first_line=ARABIC_SELECTION_LINES[0],
        first_line_words=len(first_words),
        vocalised_word="الْكِتَابُ",
        vocalised_word_position="third word of the first line",
        unvocalised_form="الكتاب",
        reading_direction="rtl", right_margin_points=MARGIN,
        font_size=14.0,
    ).tag("text_search")


# Localization ---------------------------------------------------------------

def _arabic_ligature(root):
    """Arabic presentation forms that survive into the raw extracted text.

    lib.arabic maps a lam alef glyph back to the two characters it stands for,
    which is right for every other Arabic fixture but wrong here: LOC-38 needs
    the raw text to hold U+FEFB itself, so that one raw character normalizes to
    two and NormalizedTextMapper has something to get wrong. Aliasing the glyph
    to itself after drawing overrides what text_rtl recorded.
    """
    d = Document(title="روابط عربية")
    p = d.add_page()
    font = "sans@presentation"
    size = 18.0
    right = p.width - MARGIN

    isolated_y = 700.0
    ligature_y = 650.0
    harakat_y = 600.0
    tatweel_y = 550.0

    isolated_left = p.text_rtl(right, isolated_y, "لا", font=font, size=size)
    ligature_left = p.text_rtl(right, ligature_y,
                               "سلام", font=font, size=size)
    harakat_left = p.text_rtl(right, harakat_y,
                              "الْكِتَابُ",
                              font=font, size=size)
    tatweel_left = p.text_rtl(right, tatweel_y,
                              "كــتاب", font=font, size=size)
    body_y = 480.0
    for offset, line in enumerate(ARABIC_SELECTION_LINES):
        p.text_rtl(right, body_y - offset * 26.0, line, font=font, size=14.0)

    # After every run is drawn, so these win over what encode_glyphs recorded.
    d.alias_unicode(font, "ﻻﻼ", "ﻻﻼ")

    face = d.font(font).face
    ascent = face.ascent * size / face.units_per_em
    descent = face.descent * size / face.units_per_em

    path = out_path(root, "arabic-ligature.pdf")
    d.save(path)
    manifest.register(
        "arabic-ligature.pdf", path,
        "1 page of Arabic presentation forms whose raw extracted text keeps "
        "the lam alef ligature as one character, for the search box placement "
        "regression",
        pages=1,
        search_term="سلام",
        search_term_raw="seen, U+FEFC, meem: 3 raw characters that normalize "
                        "to the 4 character word",
        search_term_rect=[round(ligature_left, 2), round(ligature_y + descent, 2),
                          round(right, 2), round(ligature_y + ascent, 2)],
        isolated_lam_alef="U+FEFB, drawn alone",
        isolated_lam_alef_rect=[round(isolated_left, 2),
                                round(isolated_y + descent, 2), round(right, 2),
                                round(isolated_y + ascent, 2)],
        harakat_word="الْكِتَابُ",
        harakat_query="الكتاب",
        harakat_word_left_edge=round(harakat_left, 2),
        harakat_word_rect=[round(harakat_left, 2), round(harakat_y + descent, 2),
                           round(right, 2), round(harakat_y + ascent, 2)],
        tatweel_word_left_edge=round(tatweel_left, 2),
        font_size=size,
        search_term_hook_rect=[int(math.floor(ligature_left)),
                               int(math.ceil(ligature_y + ascent)),
                               int(math.floor(right)),
                               int(math.ceil(ligature_y + descent))],
        loc38_note="the rects above are PDF points from the page bottom left. "
                   "PDFView.debugSearchHighlightBounds returns those same page "
                   "points and not device pixels: FPDFText_GetRect fills page "
                   "space doubles and mainJNILib packs them as Rect(floor(left), "
                   "ceil(top), floor(right), ceil(bottom)), so top holds the "
                   "LARGER y and Android Rect.height() comes back negative. "
                   "search_term_hook_rect is that exact packing, so LOC-38 "
                   "compares against it directly and needs no per profile "
                   "recording pass",
        loc39_note="LOC-39 needs searchIgnoreAccents true, because the "
                   "unvocalised query only reaches the vocalised word through "
                   "the folding path",
    ).tag("text_search")


def _persian_digits(root):
    d = Document(title="Persian Digits")
    p = d.add_page()
    persian = "۱۴۰۳"
    arabic_indic = "٤٢"
    lines = [f"Year {persian}", f"Count {arabic_indic}"]
    y = TOP_LINE
    for line in lines:
        p.text(MARGIN, y, line, font="sans", size=14.0)
        y -= 24.0
    _expect(_count_pages([lines], persian), [1], "Persian digit hits")
    _expect(_count_pages([lines], arabic_indic), [1], "Arabic-Indic hits")
    path = out_path(root, "persian-digits.pdf")
    d.save(path)
    manifest.register(
        "persian-digits.pdf", path,
        "1 page carrying the Persian digit string and the Arabic-Indic string "
        "in extractable text, one occurrence each",
        pages=1, persian_string=persian, persian_hits=1,
        arabic_indic_string=arabic_indic, arabic_indic_hits=1,
        line_1=lines[0], line_2=lines[1],
        note="Go To Page filters these out with its DigitsKeyListener, the "
             "search dialog does not, which is what LOC-37 pins",
    ).tag("text_search")


def _cyrillic_case(root):
    d = Document(title="Cyrillic Case")
    p = d.add_page()
    lines = [
        "Привет, говорит "
        "он каждое утро.",
        "А вечером он "
        "говорит привет "
        "и уходит.",
    ]
    y = TOP_LINE
    for line in lines:
        p.text(MARGIN, y, line, font="sans", size=14.0)
        y -= 24.0
    upper = "Привет"
    lower = "привет"
    # Case sensitive counting, because LOC-50 turns on the ASCII only fold.
    joined = "\n".join(lines)
    _expect(joined.count(upper), 1, "capitalised privet")
    _expect(joined.count(lower), 1, "lowercase privet")
    path = out_path(root, "cyrillic-case.pdf")
    d.save(path)
    manifest.register(
        "cyrillic-case.pdf", path,
        "1 page carrying the same Cyrillic word capitalised once and all "
        "lowercase once, in different sentences",
        pages=1, capitalised=upper, capitalised_hits=1,
        lowercase=lower, lowercase_hits=1,
        line_1=lines[0], line_2=lines[1],
        accents_off="the lowercase query finds 1, the lowercase spelling only, "
                    "because IGNORE_CASE without UNICODE_CASE folds ASCII only",
        accents_on="the lowercase query finds 2",
    ).tag("text_search")


def _turkish_case(root):
    d = Document(title="Turkish Case")
    p = d.add_page()
    lines = [
        "İstanbul is written with a dotted capital I in Turkish.",
        "ISPARTA is written in capitals on the same road sign.",
    ]
    y = TOP_LINE
    for line in lines:
        p.text(MARGIN, y, line, font="sans", size=14.0)
        y -= 24.0
    joined = "\n".join(lines)
    _expect(joined.count("İstanbul"), 1, "dotted capital Istanbul")
    _expect(joined.count("ISPARTA"), 1, "dotless ISPARTA")
    _expect(joined.lower().count("istanbul"), 0,
            "plain istanbul spellings, which would add a second hit")
    path = out_path(root, "turkish-case.pdf")
    d.save(path)
    manifest.register(
        "turkish-case.pdf", path,
        "1 page carrying the dotted capital I word and the all capitals "
        "dotless word, one occurrence each",
        pages=1, dotted_word="İstanbul", dotted_hits=1,
        capitals_word="ISPARTA", capitals_hits=1,
        line_1=lines[0], line_2=lines[1],
        accents_on="istanbul finds 1, because NFKD splits the dot off and the "
                   "combining mark is skipped. isparta finds 1",
        note="the counts must be identical under tr-TR and en-US, which is "
             "what LOC-52 compares across its two runs",
    ).tag("text_search")


LONG_TITLE_NAME = (
    "a-document-whose-name-runs-well-past-any-sensible-width-for-a-home-"
    "screen-grid-cell-long-title.pdf"
)


def _long_title(root):
    d = Document()
    p = d.add_page()
    lines = [
        "The file this page sits in has a name long enough to be truncated in",
        "every list and grid the home screen can show it in.",
    ]
    _draw_lines(d, p, lines, top=TOP_LINE)
    if len(LONG_TITLE_NAME) < 90:
        raise AssertionError(f"file name is {len(LONG_TITLE_NAME)} characters, needs 90")
    path = out_path(root, LONG_TITLE_NAME)
    d.save(path)
    manifest.register(
        LONG_TITLE_NAME, path,
        "A PDF whose file name is long enough to be ellipsized in the home "
        "grid. prerequisites.md refers to it as long-title.pdf",
        pages=1, file_name_characters=len(LONG_TITLE_NAME),
        referred_to_as="long-title.pdf",
        title_metadata="none, so the home screen falls back to the file name",
    ).tag("text_search")


MIXED_LATIN_HEAD = "The manuscript uses the word "
MIXED_ARABIC_WORD = "الكتاب"
MIXED_LATIN_TAIL = " for a bound volume throughout."
MIXED_LATIN_REST = [
    "The translator kept it in the original script on every page, and set a",
    "note in the margin the first time a reader would meet it.",
]
MIXED_RTL_HEAD = "PDF: "
MIXED_RTL_ARABIC = (
    "الكتاب المفتوح "
    "على الطاولة "
    "يذكرني بالمكتبة"
)


def _mixed_script(root):
    """Two paragraphs that resolve to opposite base directions by the same rule.

    Both start with a strong Latin character, so TextDirection falls through to
    the count. The first has more Latin than Arabic and stays left to right; the
    second inverts that and turns right to left.
    """
    from lib import arabic

    d = Document(title="Mixed Script")
    p = d.add_page()
    size = 12.0
    sans = d.font("sans")

    y = TOP_LINE
    right = p.text(MARGIN, y, MIXED_LATIN_HEAD, size=size)
    word_width = sans.glyphs_width(arabic.shape(MIXED_ARABIC_WORD), size)
    p.text_rtl(right + word_width, y, MIXED_ARABIC_WORD, font="sans", size=size)
    p.text(right + word_width, y, MIXED_LATIN_TAIL, size=size)
    y -= 18.0
    _draw_lines(d, p, MIXED_LATIN_REST, top=y, size=size, leading=18.0)

    y -= 60.0
    right = p.text(MARGIN, y, MIXED_RTL_HEAD, size=size)
    rtl_width = sans.glyphs_width(arabic.shape(MIXED_RTL_ARABIC), size)
    p.text_rtl(right + rtl_width, y, MIXED_RTL_ARABIC, font="sans", size=size)

    first = (MIXED_LATIN_HEAD + MIXED_ARABIC_WORD + MIXED_LATIN_TAIL + " "
             + " ".join(MIXED_LATIN_REST))
    second = MIXED_RTL_HEAD + MIXED_RTL_ARABIC
    first_latin = sum(1 for c in first if "a" <= c.lower() <= "z")
    first_arabic = sum(1 for c in first if "؀" <= c <= "ۿ")
    second_latin = sum(1 for c in second if "a" <= c.lower() <= "z")
    second_arabic = sum(1 for c in second if "؀" <= c <= "ۿ")
    _expect(len(MIXED_ARABIC_WORD.split()), 1, "Arabic words in paragraph 1")
    _expect(first_latin > first_arabic, True, "paragraph 1 Latin majority")
    _expect(first_latin >= 40, True, "paragraph 1 Latin letters past 40")
    _expect(second_arabic > second_latin, True, "paragraph 2 Arabic majority")
    _expect(first[0].isalpha() and first[0].isascii(), True,
            "paragraph 1 first strong character is Latin")
    _expect(second[0].isalpha() and second[0].isascii(), True,
            "paragraph 2 first strong character is Latin")

    path = out_path(root, "mixed-script.pdf")
    d.save(path)
    manifest.register(
        "mixed-script.pdf", path,
        "1 page whose two paragraphs resolve to opposite base directions "
        "through the count branch of TextDirection, not the first strong "
        "character branch",
        pages=1,
        paragraph_1=first, paragraph_1_latin_letters=first_latin,
        paragraph_1_arabic_letters=first_arabic,
        paragraph_1_arabic_words=1,
        paragraph_1_direction="left to right",
        paragraph_2=second, paragraph_2_latin_letters=second_latin,
        paragraph_2_arabic_letters=second_arabic,
        paragraph_2_direction="right to left",
        selection_for_loc41=first,
        note="Latin and Arabic are drawn as separate positioned runs, because "
             "lib/arabic reorders by segment reversal rather than by bidi",
        vis45_note="a quote card golden is recorded against paragraph 1",
    ).tag("text_search")


KNOWN_TEXT_PAGE1 = (
    "The keeper signed the ledger at the end of every watch, without fail."
)


def _known_text(root):
    """One text run on page 1, so the extracted text has no line separator.

    ACT-33 compares the clipboard against the committed file byte for byte, and
    a second drawn line would put a separator between them whose exact form is
    pdfium's business rather than the fixture's.
    """
    d = Document(title="Known Text")
    p1 = d.add_page()
    p1.text(MARGIN, TOP_LINE, KNOWN_TEXT_PAGE1, size=BODY_SIZE)
    p2 = d.add_page()
    _draw_lines(d, p2, [
        "The second page exists so the page number in the dialog title has",
        "something to be wrong about, and carries no text page one carries.",
    ], top=TOP_LINE)

    _check_widths(d, [KNOWN_TEXT_PAGE1], "serif", BODY_SIZE, BODY_WIDTH)
    path = out_path(root, "known-text.pdf")
    d.save(path)

    truth = out_path(root, "known-text-page1.txt")
    with open(truth, "w", encoding="utf-8") as fh:
        fh.write(KNOWN_TEXT_PAGE1)

    manifest.register(
        "known-text.pdf", path,
        "2 pages. Page 1 is a single drawn line whose exact text is committed "
        "beside it, so ACT-33 compares the clipboard against ground truth",
        pages=2, page1_text=KNOWN_TEXT_PAGE1,
        page1_characters=len(KNOWN_TEXT_PAGE1),
        page1_runs=1, ground_truth="known-text-page1.txt",
    ).tag("text_search")
    manifest.register(
        "known-text-page1.txt", truth,
        "The exact page 1 text of known-text.pdf, written with no trailing "
        "newline so a byte comparison is possible",
        characters=len(KNOWN_TEXT_PAGE1), trailing_newline=False,
    ).tag("text_search")
