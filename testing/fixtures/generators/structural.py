"""Outline, link and navigation structure fixtures.

The outline tables are transcribed from the fixture inventory in the test plan.
Titles, nesting and destination page indexes are asserted literally by the
Bookmarks and table of contents tests, so the rows below are the
specification rather than a rendering of it. One table driven builder produces
both outline fixtures: toc.pdf and toc-deep.pdf differ only in the rows they
feed it.

Every count in the manifest is counted from the tree that was actually built.
The link rectangles are registered in PDF points because BMK-38, HIST-33 and
NAV-16 convert a known rectangle into a screen coordinate rather than hunting
for a link on the rendered page.
"""

from lib import manifest
from lib.doc import Document, OutlineEntry
from lib.registry import fixture_group, out_path

BODY = (
    "A structured document earns its structure from the reader's need to leave "
    "the page they are on and come back to it. A heading is a promise about "
    "what follows, and a cross reference is a promise that the thing referred "
    "to exists somewhere else in the same volume. Both promises are kept by "
    "the machinery underneath rather than by the prose. "
)

# Level, title and zero based destination page index. Three entries share page
# index 59 on purpose, so nothing can assume entries and pages are one to one,
# and Appendix and appendix notes differ only in case for BMK-31's filter.
TOC_ROWS = [
    (1, "Introduction", 0),
    (2, "Scope", 2),
    (2, "Conventions", 4),
    (2, "Notation", 6),
    (1, "Background", 8),
    (2, "Prior work", 10),
    (3, "Early results", 12),
    (3, "Later results", 14),
    (1, "Method", 18),
    (2, "Apparatus", 20),
    (2, "Procedure", 24),
    # Results is the only entry at this index, unlike the three that share 59
    # below, so HIST-45 uses page index 42 as its unambiguous chapter path.
    (1, "Results", 42),
    (2, "Primary outcome", 44),
    (2, "Secondary outcomes", 46),
    (3, "Subgroup A", 48),
    (3, "Subgroup B", 50),
    (1, "Discussion", 52),
    (2, "Limitations", 54),
    (1, "Appendix", 56),
    (2, "Appendix tables", 57),
    (2, "appendix notes", 58),
    (3, "Raw values", 59),
    (3, "Checksums", 59),
    (3, "Revision history", 59),
]
TOC_PAGES = 60

# The unresolvable entry is top level and last, so BMK-28 finds its row in the
# collapsed list without expanding anything first.
UNRESOLVED_TITLE = "Unresolved reference"

DEEP_PARTS = 8
DEEP_SECTIONS = 4
DEEP_SUBSECTIONS = 3
# Notes per subsection, one figure per part. Seven parts of three and one of
# two is what lands the total on 412 exactly.
DEEP_NOTES = (3, 3, 3, 3, 3, 3, 3, 2)
# The empty title sits at level 2 so it still has children, which is what
# BMK-30 checks the indentation of.
DEEP_EMPTY_TITLE_AT = (0, 1)

LINK_PAGES = 45
# Middle third of a 612 point page is 204 to 408. Keeping every rectangle
# inside it keeps links.pdf clear of the edge tap zones EdgeTapPager claims.
LINK_RECT = (220.0, 387.0, 392.0, 405.0)
LINK_ROWS = [
    (2, "goto", 39),
    (7, "goto", 0),
    (22, "goto", 44),
    (4, "uri", "https://example.com/alpha"),
    (11, "uri", "https://example.com/beta"),
    (18, "uri", "https://example.org/gamma"),
    (30, "uri", "https://example.org/delta"),
    (36, "uri", "mjtest://example"),
    (0, "uri", "https://example.com/alpha"),
]

EDGE_PAGES = 6
# Right 20 percent of the page, height centred. NAV-16 taps the centre of this,
# which falls inside the right quarter of the view where the turn zone lives.
EDGE_RECT = (489.6, 277.2, 612.0, 514.8)
# NAV-16 opens at the first page, waits for page=0 and asserts page=3, so the
# link it taps is the one on page index 0. The pair on page index 1 exists
# because prerequisites.md phrases the same link one based. Both readings of
# "on page 1, pointing at page 4" therefore find a link where they expect one.
EDGE_LINKS = [(0, 3), (1, 4)]

LINK_RGB = (0.0, 0.0, 0.6)


@fixture_group("structural", "Outline, link and navigation structure fixtures")
def build(root):
    _toc(root)
    _toc_flat(root)
    _toc_deep(root)
    _toc_unresolved(root)
    _links(root)
    _no_links(root)
    _edge_link(root)


def _tree(rows):
    """Build the OutlineEntry forest from (level, title, page_index) rows."""
    roots, stack = [], []
    for level, title, page_index in rows:
        if level > len(stack) + 1:
            raise ValueError(f"level {level} has no parent: {title!r}")
        entry = OutlineEntry(title, page_index)
        del stack[level - 1:]
        if stack:
            stack[-1].children.append(entry)
        else:
            roots.append(entry)
        stack.append(entry)
    return roots


def _walk(entries, level=1):
    for e in entries:
        yield level, e
        yield from _walk(e.children, level + 1)


def _outline_facts(tree):
    walked = list(_walk(tree))
    return {
        "outline_entries": len(walked),
        "top_level_entries": len(tree),
        "max_depth": max(level for level, _ in walked),
        "first_top_level_children": len(tree[0].children),
    }


def _entry_list(tree):
    return [f"L{level} {e.title!r} -> page index {e.page_index}"
            for level, e in _walk(tree)]


def _owning_titles(rows, pages):
    """The title of the nearest entry at or before each page, forward filled."""
    owner = [None] * pages
    for level, title, page_index in rows:
        if page_index is not None:
            owner[page_index] = (level, title)
    last = None
    for i in range(pages):
        if owner[i] is None:
            owner[i] = last
        else:
            last = owner[i]
    return owner


def _text_page(doc, index, subtitle=None, body=BODY, paragraphs=3):
    p = doc.add_page()
    p.text(72, p.height - 60, f"Page {index + 1}", font="serif-bold", size=13)
    y = p.height - 88
    if subtitle:
        p.text(72, y, subtitle, font="serif-bold", size=11)
        y -= 24
    doc.paragraph(p, 72, y, body * paragraphs, font="serif", size=11,
                  leading=15, max_width=p.width - 144)
    return p


def _toc(root):
    d = Document(title="Structured Document", author="A. Writer")
    owner = _owning_titles(TOC_ROWS, TOC_PAGES)
    for i in range(TOC_PAGES):
        _text_page(d, i, subtitle=owner[i][1] if owner[i] else None)
    d.outline = _tree(TOC_ROWS)
    facts = _outline_facts(d.outline)
    path = out_path(root, "toc.pdf")
    d.save(path)
    manifest.register(
        "toc.pdf", path,
        "60 pages with a three level outline. Every page is headed with its own "
        "one based page number and the title of the entry it sits under",
        pages=TOC_PAGES, **facts,
        results_entry_page_index=42,
        case_insensitive_pair=["Appendix (page index 56)",
                               "appendix notes (page index 58)"],
        entries_sharing_page_index_59=["Raw values", "Checksums",
                                       "Revision history"],
        toc_path_example="Appendix ▶ appendix notes ▶ Revision history, page "
                         "index 59, because the resolver takes the last entry "
                         "at or below the index and three entries share 59",
        first_top_level_entry="Introduction, page index 0",
        entries=_entry_list(d.outline),
        deepest_page_used="index 59 (BMK-25 reaches index 42)",
    ).tag("structural")


def _toc_flat(root):
    d = Document(title="Flat Contents", author="A. Writer")
    titles = ["Opening", "Middle", "Closing"]
    pages = [0, 4, 8]
    for i in range(12):
        _text_page(d, i)
    d.outline = [OutlineEntry(t, p) for t, p in zip(titles, pages)]
    path = out_path(root, "toc-flat.pdf")
    d.save(path)
    manifest.register(
        "toc-flat.pdf", path,
        "12 pages with a three entry outline that has no children at any "
        "level, so configureExpandCollapseItems hides Expand All and "
        "Collapse All. No other outline fixture reaches that branch",
        pages=12, outline_entries=3, top_level_entries=3,
        max_depth=1, has_children=False,
        entries=[f"{t}, page index {p}" for t, p in zip(titles, pages)],
        used_by="BMK-24",
    ).tag("structural")


def _deep_rows():
    rows = []
    for part in range(DEEP_PARTS):
        rows.append((1, f"Part {part + 1}"))
        for section in range(DEEP_SECTIONS):
            title = "" if (part, section) == DEEP_EMPTY_TITLE_AT else \
                f"Section {part + 1}.{section + 1}"
            rows.append((2, title))
            for sub in range(DEEP_SUBSECTIONS):
                rows.append((3, f"Subsection {part + 1}.{section + 1}.{sub + 1}"))
                for note in range(DEEP_NOTES[part]):
                    rows.append((4, f"Note {part + 1}.{section + 1}."
                                    f"{sub + 1}.{note + 1}"))
    return rows


def _deep_pages(rows):
    """One page per entry, except that notes under one subsection share a page.

    A level three entry therefore owns the page it points at outright, which is
    the page BMK-29 needs to sit on.
    """
    pages, cursor = [], 0
    for i, (level, _title) in enumerate(rows):
        pages.append(cursor)
        next_level = rows[i + 1][0] if i + 1 < len(rows) else 0
        if level < 4 or next_level != 4:
            cursor += 1
    return pages, cursor


def _toc_deep(root):
    shape = _deep_rows()
    page_indexes, page_count = _deep_pages(shape)
    rows = [(level, title, page_index)
            for (level, title), page_index in zip(shape, page_indexes)]
    owner = _owning_titles(rows, page_count)
    level_three_pages = [i for i, o in enumerate(owner) if o and o[0] == 3]
    empty_title_rows = [i for i, (_l, t, _p) in enumerate(rows) if t == ""]

    d = Document(title="Deep Structure", author="A. Writer")
    for i in range(page_count):
        label = owner[i][1] if owner[i] else None
        _text_page(d, i, subtitle=label or None, paragraphs=1)
    d.outline = _tree(rows)
    facts = _outline_facts(d.outline)
    path = out_path(root, "toc-deep.pdf")
    d.save(path)
    manifest.register(
        "toc-deep.pdf", path,
        "The outline stress fixture. Four levels deep, one entry with an empty "
        "title that still has children, and notes sharing a page so entries "
        "outnumber pages",
        pages=page_count, **facts,
        last_entry=f"{rows[-1][1]!r} at page index {rows[-1][2]}, adapter "
                   f"position {len(rows) - 1} once Expand All has run",
        last_entry_page_number_cell=rows[-1][2] + 1,
        empty_title_entry=f"level 2, adapter position {empty_title_rows[0]} once "
                          f"Expand All has run, with "
                          f"{len(d.outline[0].children[1].children)} children",
        empty_title_count=len(empty_title_rows),
        pages_under_a_level_three_heading=len(level_three_pages),
        example_level_three_page=level_three_pages[0],
        example_level_three_path="Part 1 ▶ Section 1.1 ▶ Subsection 1.1.1",
        entries=_entry_list(d.outline),
        deepest_page_used=f"index {page_count - 1} (BMK-30 flings to the end)",
    ).tag("structural")


def _toc_unresolved(root):
    rows = TOC_ROWS + [(1, UNRESOLVED_TITLE, None)]
    d = Document(title="Structured Document With A Broken Reference")
    owner = _owning_titles(rows, TOC_PAGES)
    for i in range(TOC_PAGES):
        _text_page(d, i, subtitle=owner[i][1] if owner[i] else None)
    d.outline = _tree(rows)
    facts = _outline_facts(d.outline)
    unresolved = [t for _l, t, p in rows if p is None]
    path = out_path(root, "toc-unresolved.pdf")
    d.save(path)
    manifest.register(
        "toc-unresolved.pdf", path,
        "toc.pdf's outline plus one top level entry whose destination points at "
        "the catalog rather than a page, so pdfium reports a negative pageIdx. "
        "The fixture for the commit 03b8a4b regression",
        pages=TOC_PAGES, **facts,
        unresolved_entries=unresolved,
        unresolved_entry_position="last top level row, visible while collapsed",
        expected_page_cell="empty string, not 0 and not 1",
        deepest_page_used="index 4 (BMK-28 opens at page 5)",
    ).tag("structural")


def _links(root):
    d = Document(title="Cross Referenced Document", author="A. Writer")
    for i in range(LINK_PAGES):
        _text_page(d, i)
    x0, y0, x1, y1 = LINK_RECT
    for page_index, kind, target in LINK_ROWS:
        p = d.pages[page_index]
        p.rect(x0, y0, x1 - x0, y1 - y0, rgb=LINK_RGB, fill=False)
        label = f"Continue on page {target + 1}" if kind == "goto" else target
        right = p.text(x0 + 5, y0 + 5, label, font="serif", size=10, rgb=LINK_RGB)
        if right > x1:
            raise ValueError(f"link label overflows its rectangle: {label!r}")
        if kind == "goto":
            p.link_page(LINK_RECT, target)
        else:
            p.link_uri(LINK_RECT, target)

    counted = sum(1 for p in d.pages for a in p.annots if a["Subtype"] == "Link")
    internal = [(pi, t) for pi, k, t in LINK_ROWS if k == "goto"]
    external = [(pi, t) for pi, k, t in LINK_ROWS if k == "uri"]
    listed = sorted({t for _pi, t in external})
    path = out_path(root, "links.pdf")
    d.save(path)
    manifest.register(
        "links.pdf", path,
        "45 pages carrying 9 link annotations. Internal links carry a GoTo "
        "destination and no URI at all, so LinksActivity never lists them. "
        "Every rectangle is identical and sits in the middle third of the page, "
        "clear of the edge tap zones",
        pages=LINK_PAGES, link_annotations=counted,
        internal_links=[f"page index {pi} -> page index {t}" for pi, t in internal],
        external_links=[f"page index {pi} -> {t}" for pi, t in external],
        distinct_external_urls=listed,
        duplicate_url="https://example.com/alpha on page index 0 and page index 4",
        hosts=["example.com", "example.org", "mjtest scheme with no handler"],
        link_rect_pdf_points=list(LINK_RECT),
        page_size_points=[612.0, 792.0],
        rect_note="x 220 to 392 of a 612 wide page, y 387 to 405 of 792, "
                  "the same on every page carrying a link",
        deepest_page_used="index 44 (the page index 22 link's destination)",
    ).tag("structural")


def _no_links(root):
    d = Document(title="Unlinked Document", author="A. Writer")
    for i in range(20):
        _text_page(d, i)
    annots = sum(len(p.annots) for p in d.pages)
    if annots:
        raise ValueError(f"no-links.pdf must carry no annotations, found {annots}")
    path = out_path(root, "no-links.pdf")
    d.save(path)
    manifest.register(
        "no-links.pdf", path,
        "20 plain text pages with no annotations of any kind, counted before "
        "the file was written. Its own file rather than a copy of normal.pdf, "
        "so a change there cannot give the no links test something to find",
        pages=20, link_annotations=annots, annotations_total=annots,
        outline_entries=0,
        deepest_page_used="index 0 (BMK-37, ACT-42)",
    ).tag("structural")


def _edge_link(root):
    d = Document(title="Edge Link Document")
    for i in range(EDGE_PAGES):
        _text_page(d, i)
    x0, y0, x1, y1 = EDGE_RECT
    for page_index, target in EDGE_LINKS:
        p = d.pages[page_index]
        p.rect(x0, y0, x1 - x0, y1 - y0, rgb=LINK_RGB, fill=False)
        p.text(x0 + 8, (y0 + y1) / 2, f"To page {target + 1}", font="serif",
               size=10, rgb=LINK_RGB)
        p.link_page(EDGE_RECT, target)
    counted = sum(1 for p in d.pages for a in p.annots if a["Subtype"] == "Link")
    path = out_path(root, "edge-link.pdf")
    d.save(path)
    manifest.register(
        "edge-link.pdf", path,
        "6 pages with an internal link covering the right 20 percent of the "
        "page, height centred, so the link overlaps the tap to turn zone",
        pages=EDGE_PAGES, link_annotations=counted,
        internal_links=[f"page index {pi} -> page index {t}" for pi, t in EDGE_LINKS],
        nav16_link="page index 0 -> page index 3, tapped with the reader on "
                   "page index 0, snackbar names page 1",
        link_rect_pdf_points=list(EDGE_RECT),
        page_size_points=[612.0, 792.0],
        rect_note="x 489.6 to 612 of a 612 wide page, so the rectangle centre "
                  "sits at 90 percent width, inside the right quarter",
        deepest_page_used="index 4",
    ).tag("structural")
