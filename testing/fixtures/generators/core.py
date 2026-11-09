"""The shared core fixtures, used by nearly every surface file.

Page counts here are exact and are asserted literally by tests. The deepest page
any test reaches is recorded in the manifest, so a regeneration that changes a
count can be checked against it.
"""

from lib import manifest
from lib.doc import Document, OutlineEntry
from lib.registry import fixture_group, out_path

BODY = (
    "The reading of a document is a quiet negotiation between the page and the "
    "eye. Each paragraph offers a shape, and the reader accepts or refuses it. "
    "Where the text is dense the eye slows, and where it opens out the eye "
    "moves ahead of the understanding. This is the ordinary rhythm of reading, "
    "and a reader that respects it disappears into the background. "
)

ARABIC = (
    "القراءة رحلة هادئة بين الصفحة والعين. كل فقرة تقدم شكلا، والقارئ يقبله أو "
    "يرفضه. حيث يكون النص كثيفا تتباطأ العين، وحيث ينفتح تسبق العين الفهم. "
)


def _wrap_rtl(doc, text, font, size, max_width):
    """Wrap on logical words, measuring the shaped width of each candidate line."""
    from lib import arabic
    f = doc.font(font)
    words = text.split()
    lines, cur = [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if cur and f.glyphs_width(arabic.shape(trial), size) > max_width:
            lines.append(cur)
            cur = w
        else:
            cur = trial
    if cur:
        lines.append(cur)
    return lines


def _numbered_page(doc, index, body=BODY, font="serif", rtl=False):
    p = doc.add_page()
    if rtl:
        # Shaped and right aligned, which is what a real Arabic PDF contains.
        # The word and the number are separate runs so the number sits to the
        # left of the word, rather than being reordered into the Arabic segment.
        left = p.text_rtl(p.width - 72, p.height - 60, "صفحة", font=font, size=12)
        p.text(left - 8 - doc.font(font).text_width(str(index + 1), 12),
               p.height - 60, str(index + 1), font=font, size=12)
        y = p.height - 100
        for line in _wrap_rtl(doc, body * 3, font, 12, p.width - 144):
            p.text_rtl(p.width - 72, y, line, font=font, size=12)
            y -= 18
    else:
        p.text(72, p.height - 60, f"Page {index + 1}", font="serif-bold", size=13)
        doc.paragraph(p, 72, p.height - 90, body * 3, font=font, size=11,
                      leading=15, max_width=p.width - 144)
    return p


@fixture_group("core", "The shared fixtures every surface depends on")
def build(root):
    _normal(root)
    _scanned(root)
    _protected(root)
    _arabic(root)
    _large(root)
    _forms(root)
    _highlights(root)
    _single_and_small(root)
    _tall_page(root)
    _corrupt(root)


def _tall_page(root):
    d = Document(title="Tall Page")
    for i in range(2):
        p = d.add_page(size=(300.0, 900.0))
        p.text(40, 850, f"Tall page {i + 1}", font="serif-bold", size=14.0)
        y = 810.0
        while y > 60.0:
            p.text(40, y, "A narrow column line to give the page body.",
                   font="serif", size=9.0)
            y -= 18.0
    path = out_path(root, "tall-page.pdf")
    d.save(path)
    manifest.register(
        "tall-page.pdf", path,
        "Two pages at 300 by 900 points, aspect ratio 3.0, so the fit height "
        "zoom at fit width policy computes well below 1.0 and "
        "resetZoomToFitPageWithAnimation's horizontal branch is "
        "distinguishable from the vertical clamp to 1.0",
        pages=2, page_size="300 by 900 points", aspect_ratio=3.0,
        used_by="ZOOM-41",
    ).tag("core")


def _corrupt(root):
    seed = 0x0BADF00D
    junk = bytearray()
    for _ in range(65536):
        seed = (1103515245 * seed + 12345) & 0x7FFFFFFF
        junk.append((seed >> 9) & 0xFF)
    path = out_path(root, "corrupt.pdf")
    with open(path, "wb") as fh:
        fh.write(b"%PDF-1.7\n" + bytes(junk))
    manifest.register(
        "corrupt.pdf", path,
        "A PDF header followed by 64 KB of noise: no objects, no xref, no "
        "trailer, nothing for a repairing parser to reconstruct, so pdfium "
        "refuses it outright. The shared cannot-open fixture. A byte slice "
        "of a real file is not used because pdfium rebuilds the xref by "
        "scanning for object markers and often opens such a slice",
        bytes=9 + 65536, opens=False,
    ).tag("core", "malformed")


def _normal(root):
    d = Document(title="Normal Document", author="A. Writer")
    for i in range(20):
        _numbered_page(d, i)
    path = out_path(root, "normal.pdf")
    d.save(path)
    manifest.register(
        "normal.pdf", path,
        "Plain 20 page text document, no outline, no links, under 2 MB",
        pages=20, outline_entries=0, link_annotations=0,
        identity_path="whole file, under the 2 MB limit",
        deepest_page_used="index 19 (TXT-36, TXT-48)",
    ).tag("core")


def _scanned(root):
    """Image only pages, so no text can be extracted at all."""
    d = Document(title="Scanned Document")
    for i in range(8):
        p = d.add_page()
        w, h = 620, 800
        # A deterministic pseudo scan: grey paper, darker ink blocks, no text.
        # Raw samples go in; the writer deflates the stream.
        rows = bytearray()
        for y in range(h):
            for x in range(w):
                v = 238
                if 60 < y < 90 and 60 < x < 380:
                    v = 40
                elif 130 < y % 220 < 150 and 60 < x < 520:
                    v = 90
                elif (x + y * 7 + i * 13) % 997 == 0:
                    v = 160
                rows.append(v)
        p.image(36, 36, p.width - 72, p.height - 72, gray=(w, h, bytes(rows)))
    path = out_path(root, "scanned.pdf")
    d.save(path)
    manifest.register(
        "scanned.pdf", path,
        "Image only, 8 pages, zero extractable characters on every page",
        pages=8, extractable_characters=0,
        deepest_page_used="index 6 (VIS-06)",
    ).tag("core")


def _protected(root):
    d = Document(title="Protected Document")
    for i in range(6):
        _numbered_page(d, i)
    path = out_path(root, "protected.pdf")
    d.save(path, encrypt_password="mjpdf")
    manifest.register(
        "protected.pdf", path,
        "User password mjpdf, no owner password, 6 pages",
        pages=6, password="mjpdf", encryption="RC4 40 bit, revision 2",
        deepest_page_used="index 0 only",
    ).tag("core")


def _arabic(root):
    d = Document(title="وثيقة عربية", author="كاتب")
    for i in range(12):
        _numbered_page(d, i, body=ARABIC, font="sans", rtl=True)
    # Page 3 carries the shaping specimens the search and localization tests need.
    p = d.pages[2]
    y = 220
    # Shaping produces the lam alef ligature from lam plus alef, which is how a
    # real document contains U+FEFB rather than by typing the ligature directly.
    p.text_rtl(p.width - 120, y, "لا لأ", font="sans", size=16)
    p.text_rtl(p.width - 120, y - 30, "كـــتاب", font="sans", size=16)
    p.text_rtl(p.width - 120, y - 60, "كِتَابُ", font="sans", size=16)
    p.text_rtl(p.width - 120, y - 90, "كتاب", font="sans", size=16)
    for extra in (5, 7):
        d.pages[extra].text_rtl(d.pages[extra].width - 120, 160, "كتاب",
                                font="sans", size=14)
    path = out_path(root, "arabic-rtl.pdf")
    d.save(path)
    manifest.register(
        "arabic-rtl.pdf", path,
        "RTL Arabic, 12 pages, right aligned, with shaping specimens on page index 2",
        pages=12, reading_direction="rtl",
        kitab_occurrences=4, kitab_pages=[2, 2, 5, 7],
        lam_alef_forms="U+FEFB and U+FEFC on page index 2",
        tatweel="U+0640 on page index 2", harakat="page index 2",
        deepest_page_used="index 8 (VIS-04)",
    ).tag("core")


def _large(root):
    d = Document(title="Large Document")
    # The fixture must exceed 2 MB so the identity path switches from whole file
    # hashing to head plus tail plus length. Text alone deflates far below that,
    # so the document carries a figure every tenth page, which is also what makes
    # real documents large. The figure pattern is deterministic but noisy enough
    # not to compress away.
    for i in range(500):
        p = d.add_page()
        p.text(72, p.height - 60, f"Page {i + 1}", font="serif-bold", size=13)
        # "the" must appear more than 5000 times across the document.
        doc_body = BODY * 3 + "the the the the the the the the the the the the "
        y = d.paragraph(p, 40, p.height - 80, doc_body, font="serif", size=8,
                        leading=9.2, max_width=p.width - 80)
        if i % 10 == 0:
            fw, fh = 250, 160
            seed = 0x2545F491 ^ (i * 2654435761)
            px = bytearray()
            for _ in range(fw * fh):
                seed = (1103515245 * seed + 12345) & 0x7FFFFFFF
                px.append((seed >> 7) & 0xFF)
            p.image(40, max(y - 180, 40), fw, fh, gray=(fw, fh, bytes(px)))
    d.pages[499].text(72, 60, "orinoco", font="serif", size=11)
    path = out_path(root, "large.pdf")
    d.save(path)
    manifest.register(
        "large.pdf", path,
        "500 pages, over 2 MB so the head plus tail identity path is used",
        pages=500, the_occurrences="more than 5000",
        orinoco="exactly once, page index 499",
        identity_path="head plus tail plus length, over 2 MB",
        deepest_page_used="index 499 (memory scroll traversal)",
    ).tag("core", "slow")


def _forms(root):
    d = Document(title="Form Document")
    p1 = d.add_page()
    p1.text(72, p1.height - 60, "Application", font="serif-bold", size=14)
    p1.text(72, p1.height - 110, "Full name", size=11)
    text_field = p1.widget((200, p1.height - 126, 460, p1.height - 104), {
        "FT": __import__("lib.pdfwriter", fromlist=["Name"]).Name("Tx"),
        "T": "fullname", "TU": "Full name", "V": "", "DA": "/Helv 11 Tf 0 g",
    })
    p1.text(72, p1.height - 160, "Subscribe", size=11)
    check = p1.widget((200, p1.height - 176, 216, p1.height - 160), {
        "FT": __import__("lib.pdfwriter", fromlist=["Name"]).Name("Btn"),
        "T": "subscribe", "V": __import__("lib.pdfwriter", fromlist=["Name"]).Name("Off"),
        "AS": __import__("lib.pdfwriter", fromlist=["Name"]).Name("Off"),
    })
    p1.text(72, p1.height - 210, "Plan", size=11)
    Name = __import__("lib.pdfwriter", fromlist=["Name"]).Name
    radio_a = p1.widget((200, p1.height - 226, 216, p1.height - 210), {
        "FT": Name("Btn"), "T": "plan", "Ff": 32768,
        "V": Name("Off"), "AS": Name("Off"),
    })
    radio_b = p1.widget((260, p1.height - 226, 276, p1.height - 210), {
        "FT": Name("Btn"), "T": "plan2", "Ff": 32768,
        "V": Name("Off"), "AS": Name("Off"),
    })
    p2 = d.add_page()
    p2.text(72, p2.height - 60, "Second page", font="serif-bold", size=13)
    p2.text(72, p2.height - 110, "Reference", size=11)
    ref_field = p2.widget((200, p2.height - 126, 460, p2.height - 104), {
        "FT": Name("Tx"), "T": "reference", "V": "", "DA": "/Helv 11 Tf 0 g",
    })
    path = out_path(root, "forms.pdf")
    # AcroForm needs the field objects; they are the widget annotations here.
    d.acroform_fields = []
    d.save(path)
    manifest.register(
        "forms.pdf", path,
        "2 pages. Page 1 has a text field, a checkbox and a two option radio "
        "group, all empty. Page 2 has one text field (ANN-47)",
        pages=2, page1_fields=["fullname (text)", "subscribe (checkbox)",
                               "plan/plan2 (radio)"],
        page2_fields=["reference (text)"],
        note="Only text, checkbox and radio are reachable in the app",
    ).tag("core")


def _highlights(root):
    d = Document(title="Highlighted Document", author="A. Writer")
    groups = []
    for i in range(6):
        p = _numbered_page(d, i)
        if i in (0, 1, 2, 4):
            line = "The reading of a document is a quiet negotiation"
            rect = d.line_rect(p, 72, p.height - 90, line)
            note = "a note" if i in (0, 2) else None
            g = f"g{i}"
            p.highlight([rect], quote=line, note=note, group=g)
            groups.append({"page": i, "group": g, "note": bool(note),
                           "colour": "yellow"})
    # One group spanning three lines on page index 4, one annotation per line.
    p = d.pages[4]
    spans = []
    for n in range(3):
        y = p.height - 120 - n * 15
        spans.append(d.line_rect(p, 72, y, "and the eye. Each paragraph offers a shape"))
    p.highlight(spans, quote="and the eye. Each paragraph offers a shape",
                group="span3")
    groups.append({"page": 4, "group": "span3", "note": False,
                   "colour": "yellow", "lines": 3})
    path = out_path(root, "highlights.pdf")
    d.save(path)
    manifest.register(
        "highlights.pdf", path,
        "6 pages carrying five app authored highlight groups, all with T = "
        "'MJ PDF' and an MJGroup key so they are tappable. Two carry a note, "
        "one spans three lines",
        pages=6, highlight_groups=5, groups_with_notes=2,
        multi_line_group="span3 on page index 4, three annotations",
        app_owned=True, deepest_page_used="index 4",
    ).tag("core")


def _single_and_small(root):
    d = Document(title="Single Page")
    _numbered_page(d, 0)
    path = out_path(root, "single-page.pdf")
    d.save(path)
    manifest.register("single-page.pdf", path,
                      "Exactly one page, for the permanently hidden scroll handle",
                      pages=1).tag("core")

    d = Document(title="Three Pages")
    for i in range(3):
        _numbered_page(d, i)
    path = out_path(root, "three-page.pdf")
    d.save(path)
    manifest.register("three-page.pdf", path,
                      "Exactly 3 pages, so autoscroll reaches the end in one test",
                      pages=3).tag("core")
