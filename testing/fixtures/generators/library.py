"""Home, scan and history fixtures: the library surfaces and their edge cases.

Three naming rules from the inventory are load bearing here. `alpha.pdf`,
`beta.pdf` and `gamma.pdf` are home's single page solid colour fills, and the
history documents carry the `hist-` prefix so the two families cannot collide.
`alpha-copy.pdf` is home's byte identical copy for the relocate flow, while
`hist-alpha-copy.pdf` is history's disposable copy that HIST-19 deletes.

`Apple.pdf`, `banana.pdf` and `Cherry.pdf` draw identical page content but carry
distinct document titles. Byte identical files would share one `DocumentIdentity`
and therefore one `PdfRecord` row, and HOME-35 seeds three records with three
different last opened dates, so the three files must have three identities.

The four oversized fixtures need exact byte lengths with a controlled prefix and
a controlled tail, which is what `_sized_document` produces. Each document holds
two incompressible grey images: a head image, identical across a pair, that
covers the whole first megabyte the identity scheme reads, and a tail image with
a different seed per file that lands inside the last megabyte it reads. The
writer emits the document information dictionary as the final object, so a
filler string in `Keywords` pads the file to an exact length without moving a
single cross reference offset. `scan/twin-a.pdf` and `scan/twin-b.pdf` come out
the same length and prove the tail read matters; `shared-prefix-a.pdf` and
`shared-prefix-b.pdf` come out different lengths and prove the length does.
"""

import hashlib
import os
import shutil
import zipfile

from lib import manifest
from lib.doc import Document
from lib.registry import fixture_group, out_path

BODY = (
    "A library is a promise that a page you left will still be there. The "
    "reader keeps the place, the shelf keeps the book, and neither is any use "
    "without the other. Where the shelf forgets, the reader is left guessing, "
    "and where the reader forgets, the shelf is only a list of names. "
)

FILLER_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

MEGABYTE = 1024 * 1024


def _document_identity(path):
    """The identity `DocumentIdentity.of` computes for this file.

    Head window, then the rest of the file inside the 2 MB whole file limit or
    the tail window above it, then the eight byte big endian length, under an
    `h2` prefix. Kept here rather than described, so the pairs that must match
    and the pairs that must not are checked against the produced bytes.
    """
    size = os.path.getsize(path)
    digester = hashlib.md5()
    with open(path, "rb") as fh:
        head = fh.read(min(MEGABYTE, size))
        digester.update(head)
        if size <= 2 * MEGABYTE:
            while True:
                chunk = fh.read(MEGABYTE)
                if not chunk:
                    break
                digester.update(chunk)
        else:
            fh.seek(size - MEGABYTE)
            digester.update(fh.read(MEGABYTE))
    digester.update(size.to_bytes(8, "big"))
    return "h2" + digester.hexdigest()


def _noise(seed, count):
    """Incompressible deterministic bytes, so a padded file stays the size it is."""
    out = bytearray()
    block = 0
    while len(out) < count:
        out += hashlib.md5(f"{seed}:{block}".encode("ascii")).digest()
        block += 1
    return bytes(out[:count])


def _filler(seed, count):
    """Printable filler, one byte per character once the writer escapes it."""
    raw = _noise(seed, count)
    return "".join(FILLER_ALPHABET[b % len(FILLER_ALPHABET)] for b in raw)


def _text_page(doc, index, title, body=BODY):
    p = doc.add_page()
    p.text(72, p.height - 60, f"{title} page {index + 1}", font="serif-bold", size=13)
    doc.paragraph(p, 72, p.height - 90, body * 3, font="serif", size=11,
                  leading=15, max_width=p.width - 144)
    return p


def _small_document(title, pages=1, body=BODY):
    d = Document(title=title)
    for i in range(pages):
        _text_page(d, i, title, body=body)
    return d


def _copy(src, dst):
    """Copy the produced bytes. Regenerating would not give an identical file."""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)
    return dst


@fixture_group("library", "Home, scan and history fixtures")
def build(root):
    _home_covers(root)
    _home_names(root)
    _nested_tree(root)
    _history(root)
    _edges(root)
    _meta_rich(root)
    _dotted_folder(root)
    _scan_bulk(root)
    _scan_huge(root)
    _scan_mixed(root)
    _scan_nested(root)
    _scan_dupe(root)
    _scan_twins(root)
    _shared_prefix(root)


# Home and library ------------------------------------------------------

COVERS = [
    ("alpha.pdf", (1.0, 0.0, 0.0), "red", (255, 0, 0)),
    ("beta.pdf", (0.0, 1.0, 0.0), "green", (0, 255, 0)),
    ("gamma.pdf", (0.0, 0.0, 1.0), "blue", (0, 0, 255)),
]


def _home_covers(root):
    alpha_path = None
    for name, rgb, label, rgb8 in COVERS:
        d = Document(title=name[:-4].capitalize())
        p = d.add_page()
        # The fill covers the whole page so any sampled pixel is the cover colour.
        p.rect(0, 0, p.width, p.height, rgb=rgb, fill=True)
        path = out_path(root, name)
        d.save(path)
        if name == "alpha.pdf":
            alpha_path = path
        manifest.register(
            name, path,
            f"Single page filled edge to edge with {label}, so a cover bitmap is "
            "identified by sampling one pixel",
            pages=1, fill_colour=label, fill_rgb=list(rgb),
            fill_rgb_8bit=list(rgb8), identity=_document_identity(path),
            sample="any pixel of the rendered page, centre pixel used by HOME-39",
        ).tag("library")

    path = _copy(alpha_path, out_path(root, "alpha-copy.pdf"))
    assert _document_identity(path) == _document_identity(alpha_path)
    manifest.register(
        "alpha-copy.pdf", path,
        "Byte identical copy of alpha.pdf at a second path, for identity matching "
        "in the relocate flow (HOME-53)",
        pages=1, fill_colour="red", identical_to="alpha.pdf",
        identity=_document_identity(path),
        identity_note="equal to alpha.pdf's identity, which is the point",
    ).tag("library")


SORT_NAMES = ["Apple.pdf", "banana.pdf", "Cherry.pdf"]


def _home_names(root):
    identities = {}
    for name in SORT_NAMES:
        stem = name[:-4]
        # Identical drawn content, distinct titles: three files that render the
        # same but hash differently, which HOME-35's three records require.
        d = Document(title=stem)
        _text_page(d, 0, "Sorting")
        path = out_path(root, name)
        d.save(path)
        identities[name] = _document_identity(path)
        manifest.register(
            name, path,
            "Identical page content, name chosen for case insensitive sort order",
            pages=1, document_title=stem, identity=identities[name],
            content="identical to the other two, only the title metadata differs",
            case_insensitive_order="Apple, banana, Cherry",
        ).tag("library")
    assert len(set(identities.values())) == len(SORT_NAMES)

    d = Document(title="Meta Title Document")
    _text_page(d, 0, "Metadata")
    path = out_path(root, "filename-differs.pdf")
    d.save(path)
    manifest.register(
        "filename-differs.pdf", path,
        "Embedded title differs from the file name, for HOME-13 and HOME-14",
        pages=1, document_title="Meta Title Document", file_stem="filename-differs",
    ).tag("library")

    d = Document(title="Quarterly Report")
    _text_page(d, 0, "Quarterly Report")
    path = out_path(root, "report-annotated.pdf")
    d.save(path)
    manifest.register(
        "report-annotated.pdf", path,
        "File name ends in -annotated with an unrelated embedded title, for HOME-15",
        pages=1, document_title="Quarterly Report", file_stem="report-annotated",
    ).tag("library")

    same_name = {}
    for name, pages, body in (
        ("same-name-a.pdf", 2, BODY),
        ("same-name-b.pdf", 3, BODY[::-1]),
    ):
        d = _small_document(name[:-4], pages=pages, body=body)
        path = out_path(root, name)
        d.save(path)
        same_name[name] = _document_identity(path)
        manifest.register(
            name, path,
            "Distinct content pushed under the shared name report.pdf into two "
            "directories, for the duplicate import race (RES-30)",
            pages=pages, pushed_as="report.pdf",
            identity=same_name[name],
            note="the two files differ in content and page count, so a test can "
                 "tell which one landed",
        ).tag("library")
    assert same_name["same-name-a.pdf"] != same_name["same-name-b.pdf"]


NESTED_TREE = [
    ("Fiction", "a.pdf"),
    ("Fiction/Short", "b.pdf"),
    ("Fiction/Short", "c.pdf"),
    ("Science", "d.pdf"),
]


def _nested_tree(root):
    for folder, name in NESTED_TREE:
        rel = f"library/TestBooks/{folder}/{name}"
        d = _small_document(f"{folder} {name[:-4]}")
        path = out_path(root, "library", "TestBooks", *folder.split("/"), name)
        d.save(path)
        manifest.register(
            rel, path,
            f"Nested library tree member under {folder}, for the Folders tab "
            "hierarchy tests (HOME-61 onwards)",
            pages=1, folder=folder,
            device_path=f"/sdcard/TestBooks/{folder}/{name}",
        ).tag("library")


# History ---------------------------------------------------------------

def _history(root):
    d = _small_document("Alpha", pages=20)
    alpha = out_path(root, "hist-alpha.pdf")
    d.save(alpha)
    manifest.register(
        "hist-alpha.pdf", alpha,
        "History's default document: 20 pages of text, no outline, no links",
        pages=20, outline_entries=0, link_annotations=0,
        identity=_document_identity(alpha),
        note="not interchangeable with home's alpha.pdf, which is a colour fill",
    ).tag("library")

    d = _small_document("Beta", pages=12, body=BODY[::-1])
    beta = out_path(root, "hist-beta.pdf")
    d.save(beta)
    manifest.register(
        "hist-beta.pdf", beta,
        "Second history document: 12 pages, a different name and different "
        "content, for ordering and the document switch that clears the trail",
        pages=12, outline_entries=0, link_annotations=0,
        identity=_document_identity(beta),
    ).tag("library")

    path = _copy(alpha, out_path(root, "hist-alpha-copy.pdf"))
    assert _document_identity(path) == _document_identity(alpha)
    manifest.register(
        "hist-alpha-copy.pdf", path,
        "Disposable byte identical copy of hist-alpha.pdf, deleted mid test by "
        "HIST-19 so no test destroys a fixture another test needs",
        pages=20, identical_to="hist-alpha.pdf",
        identity=_document_identity(path),
        identity_note="equal to hist-alpha.pdf's identity, so a test that opens "
                      "both sees one record",
    ).tag("library")


# Reader appearance and action edges ------------------------------------

MARGIN_FRACTION = 0.18
MARGINS_PAGES = 10
FULL_BLEED_PAGES = 6
GRID_STEP = 24.0
GRID_WEIGHT = 3.0
GRID_INK = (0.1, 0.1, 0.1)


def _grid_page(page, step, weight):
    """Dark bars across the whole page, with one flush against each edge."""
    def positions(extent):
        out = [0.0]
        x = step
        while x < extent - weight:
            out.append(x)
            x += step
        out.append(extent - weight)
        return out

    for y in positions(page.height):
        page.rect(0, y, page.width, weight, rgb=GRID_INK, fill=True)
    for x in positions(page.width):
        page.rect(x, 0, weight, page.height, rgb=GRID_INK, fill=True)


def _edges(root):
    d = Document(title="Margins")
    for i in range(MARGINS_PAGES):
        p = d.add_page()
        bx = p.width * MARGIN_FRACTION
        by = p.height * MARGIN_FRACTION
        bw = p.width - 2 * bx
        bh = p.height - 2 * by
        # Every page carries the same block, so crop detection resolves to one
        # stable rectangle rather than the union of ten different ones.
        p.rect(bx, by, bw, bh, rgb=(0.75, 0.75, 0.75), fill=True)
        p.text(bx + 24, by + bh - 40, f"Body block page {i + 1}",
               font="serif-bold", size=14)
    path = out_path(root, "margins.pdf")
    d.save(path)
    manifest.register(
        "margins.pdf", path,
        "10 pages, each an identical body block inside a pure white border of 18 "
        "percent of the page width and height, so crop detection finds a stable "
        "non full page crop",
        pages=MARGINS_PAGES,
        border_fraction=MARGIN_FRACTION,
        body_rect_fraction="x from 0.18 to 0.82, y from 0.18 to 0.82 of the page",
        crop_expectation="a non full page crop, identical on every page",
        pairs_with="full-bleed.pdf, which is the other crop branch",
    ).tag("library")

    d = Document(title="Full Bleed")
    for i in range(FULL_BLEED_PAGES):
        p = d.add_page()
        # A grid rather than a solid fill. The detector measures each pixel
        # against the median luminance of the page border, so a page of one flat
        # colour carries no ink at all and says nothing about the branch. Ink has
        # to reach all four edges, which is what puts the detected content area
        # above the 0.98 ceiling and yields a full page result.
        _grid_page(p, GRID_STEP, GRID_WEIGHT)
        p.text(40, p.height / 2, f"Edge to edge page {i + 1}",
               font="serif-bold", size=14)
    path = out_path(root, "full-bleed.pdf")
    d.save(path)
    manifest.register(
        "full-bleed.pdf", path,
        "6 pages of edge to edge content with no white border, so crop detection "
        "returns a full page result",
        pages=FULL_BLEED_PAGES, white_border="none",
        content=f"dark grid of {GRID_WEIGHT} point bars every {GRID_STEP} points, "
                "with a bar flush against each of the four edges",
        crop_expectation="full page: the content area reaches 1.0, above the "
                         "detector's 0.98 ceiling, so no page yields a crop",
        pairs_with="margins.pdf, which is the other crop branch",
    ).tag("library")

    d = Document(title="Landscape", page_size=(792.0, 612.0))
    for i in range(2):
        _text_page(d, i, "Landscape")
    path = out_path(root, "landscape-two-page.pdf")
    d.save(path)
    manifest.register(
        "landscape-two-page.pdf", path,
        "Two pages wider than tall, for ZOOM-41 and ZOOM-44 where the document "
        "already fits the viewport height at zoom 1.0",
        pages=2, page_size="792 by 612 points, landscape",
    ).tag("library")

    d = _small_document("Eight Pages", pages=8)
    path = out_path(root, "eight-page.pdf")
    d.save(path)
    manifest.register(
        "eight-page.pdf", path,
        "Exactly 8 pages, for the print preview page count (ACT-37)",
        pages=8,
    ).tag("library")


META_RICH = {
    "title": "Meta Rich Title",
    "author": "Meta Rich Author",
    "subject": "Meta Rich Subject",
    "keywords": "alpha, beta, gamma",
    "creator": "Meta Rich Creator",
    "producer": "Meta Rich Producer",
    "creation_date": "D:20240607081533Z",
}


def _meta_rich(root):
    d = Document(title=META_RICH["title"], author=META_RICH["author"])
    d.subject = META_RICH["subject"]
    d.keywords = META_RICH["keywords"]
    d.creator = META_RICH["creator"]
    d.producer = META_RICH["producer"]
    d.creation_date = META_RICH["creation_date"]
    _text_page(d, 0, "Metadata")
    path = out_path(root, "meta-rich.pdf")
    d.save(path)
    manifest.register(
        "meta-rich.pdf", path,
        "Every document information field populated with a distinct known value, "
        "compared literally by the file metadata tests",
        pages=1,
        Title=META_RICH["title"], Author=META_RICH["author"],
        Subject=META_RICH["subject"], Keywords=META_RICH["keywords"],
        Creator=META_RICH["creator"], Producer=META_RICH["producer"],
        CreationDate=META_RICH["creation_date"],
        ModDate=META_RICH["creation_date"],
    ).tag("library")


def _dotted_folder(root):
    d = _small_document("Dotted Folder", pages=3)
    path = out_path(root, "dotted.folder", "normal.pdf")
    d.save(path)
    manifest.register(
        "dotted.folder/normal.pdf", path,
        "A plain document inside a directory whose name contains a dot, for the "
        "intake pathPattern workaround filters",
        pages=3, directory="dotted.folder",
    ).tag("library")


# Scan ------------------------------------------------------------------

BULK_COUNT = 40
BULK_CYCLE = 8
HUGE_COUNT = 500


def _bulk_pages(index):
    """One based file index to page count, cycling 1 to 8."""
    return ((index - 1) % BULK_CYCLE) + 1


def _scan_bulk(root):
    counts = {}
    total = 0
    for i in range(1, BULK_COUNT + 1):
        name = f"bulk-{i:02d}.pdf"
        pages = _bulk_pages(i)
        d = Document(title=f"Bulk {i:02d}")
        for page_index in range(pages):
            _text_page(d, page_index, f"Bulk {i:02d}")
        path = out_path(root, "scan", "bulk", name)
        d.save(path)
        counts[name] = len(d.pages)
        total += len(d.pages)
    manifest.register(
        "scan/bulk/", None,
        "40 distinct small text documents, bulk-01.pdf through bulk-40.pdf, page "
        "counts cycling 1 to 8. Individual members are named by tests but are not "
        "separate fixtures",
        files=BULK_COUNT,
        page_count_rule="bulk-NN.pdf has ((NN - 1) mod 8) + 1 pages",
        first_eight="bulk-01 1, bulk-02 2, bulk-03 3, bulk-04 4, bulk-05 5, "
                    "bulk-06 6, bulk-07 7, bulk-08 8, then repeating",
        pages_by_name=counts,
        pages_total=total,
        device_path="/sdcard/scan/bulk/",
    ).tag("library")


def _scan_huge(root):
    for i in range(1, HUGE_COUNT + 1):
        # One font and one short paragraph: the embedded subset is most of each
        # file, and 500 of them are pushed to the device before every test class.
        d = Document(title=f"Huge {i:03d}")
        p = d.add_page()
        p.text(72, p.height - 60, f"Document {i:03d}", font="serif", size=13)
        d.paragraph(p, 72, p.height - 90, BODY, font="serif", size=11,
                    leading=15, max_width=p.width - 144)
        d.save(out_path(root, "scan", "huge", f"huge-{i:03d}.pdf"))
    manifest.register(
        "scan/huge/", None,
        "500 distinct one page documents, huge-001.pdf through huge-500.pdf, so a "
        "scan pass cannot finish before the first poll. Grow it rather than adding "
        "a sleep if it ever completes too fast",
        files=HUGE_COUNT, pages_each=1,
        name_pattern="huge-NNN.pdf, zero padded to three digits",
        device_path="/sdcard/scan/huge/",
    ).tag("library", "slow")


NOTES_TXT = (
    "Reading notes, kept outside the library on purpose.\n"
    "Nothing here is a PDF and the scanner must not index it.\n"
)

TRAP_TXT = (
    "This file is named trap.pdf.txt. The extension test must read the whole\n"
    "suffix and not stop at the first dot it finds.\n"
)

# A JFIF header and a short body. The scanner filters on name and MIME type and
# never decodes it, so a decodable image would buy nothing.
JPEG_HEADER = bytes([
    0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
    0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
])
JPEG_END = bytes([0xFF, 0xD9])


def _epub_bytes():
    """A real zip with the EPUB member layout, written with a fixed timestamp."""
    import io
    buf = io.BytesIO()
    members = [
        ("mimetype", "application/epub+zip", zipfile.ZIP_STORED),
        ("META-INF/container.xml",
         '<?xml version="1.0"?>\n'
         '<container version="1.0" '
         'xmlns="urn:oasis:names:tc:opendocument:xmlns:container">\n'
         '  <rootfiles>\n'
         '    <rootfile full-path="content.opf" '
         'media-type="application/oebps-package+xml"/>\n'
         '  </rootfiles>\n'
         '</container>\n', zipfile.ZIP_DEFLATED),
        ("content.opf",
         '<?xml version="1.0"?>\n'
         '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" '
         'unique-identifier="id">\n'
         '  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">\n'
         '    <dc:identifier id="id">mj-pdf-fixture-book</dc:identifier>\n'
         '    <dc:title>Not A PDF</dc:title>\n'
         '    <dc:language>en</dc:language>\n'
         '  </metadata>\n'
         '  <manifest/>\n'
         '  <spine/>\n'
         '</package>\n', zipfile.ZIP_DEFLATED),
    ]
    with zipfile.ZipFile(buf, "w") as zf:
        for name, text, method in members:
            info = zipfile.ZipInfo(name, date_time=(2024, 1, 1, 0, 0, 0))
            info.compress_type = method
            info.external_attr = 0o644 << 16
            zf.writestr(info, text)
    return buf.getvalue()


def _scan_mixed(root):
    for name, title in (("report.pdf", "Report"),
                        ("REPORT-UPPER.PDF", "Report Upper")):
        d = _small_document(title, pages=2)
        path = out_path(root, "scan", "mixed", name)
        d.save(path)
        manifest.register(
            f"scan/mixed/{name}", path,
            "PDF in the mixed media folder, indexed by the scanner. The pair "
            "differs only in the case of the name and the extension",
            pages=2, device_path=f"/sdcard/scan/mixed/{name}",
        ).tag("library")

    for name, payload, description in (
        ("notes.txt", NOTES_TXT.encode("ascii"),
         "Plain text beside the PDFs, never indexed"),
        ("cover.jpg", JPEG_HEADER + _noise(9101, 512) + JPEG_END,
         "JFIF header and a short body, never indexed and never decoded"),
        ("book.epub", _epub_bytes(),
         "A real zip with the EPUB member layout, never indexed"),
        ("trap.pdf.txt", TRAP_TXT.encode("ascii"),
         "Name contains .pdf but the extension is .txt, so it must not be indexed"),
    ):
        path = out_path(root, "scan", "mixed", name)
        with open(path, "wb") as fh:
            fh.write(payload)
        manifest.register(
            f"scan/mixed/{name}", path, description,
            is_pdf=False, bytes_written=len(payload),
            device_path=f"/sdcard/scan/mixed/{name}",
        ).tag("library")


def _scan_nested(root):
    d = _small_document("Deep", pages=2)
    path = out_path(root, "scan", "nested", "a", "b", "c", "deep.pdf")
    d.save(path)
    manifest.register(
        "scan/nested/a/b/c/deep.pdf", path,
        "Three directory levels below the scan root, for the depth walk tests",
        pages=2, depth=3, device_path="/sdcard/scan/nested/a/b/c/deep.pdf",
    ).tag("library")


def _scan_dupe(root):
    d = _small_document("Same", pages=2)
    left = out_path(root, "scan", "dupe", "left", "same.pdf")
    d.save(left)
    right = _copy(left, out_path(root, "scan", "dupe", "right", "same.pdf"))
    identity = _document_identity(left)
    assert identity == _document_identity(right)
    for name, path in (("scan/dupe/left/same.pdf", left),
                       ("scan/dupe/right/same.pdf", right)):
        manifest.register(
            name, path,
            "Byte identical copies of one document under two directories, so two "
            "index rows share one DocumentIdentity",
            pages=2, identical_to="the other member of the pair",
            identity=identity, device_path=f"/sdcard/{name}",
        ).tag("library")


# Oversized fixtures with controlled prefixes and tails ------------------

def _sized_document(path, target, head_image, tail_image, filler_seed, title,
                    label):
    """Write a document of exactly `target` bytes with a fixed head and tail.

    The head image is written before the first megabyte ends and the tail image
    inside the last megabyte, which are the two windows `DocumentIdentity` reads
    for a file over its 2 MB whole file limit. Padding goes into `Keywords`,
    which the writer emits last, so no cross reference offset moves and the file
    length changes by exactly the length of the filler.

    `label` is the text drawn on the pages and must be the same for both members
    of a pair: a different string embeds a different font subset and a different
    content stream, and the shared prefix would end there rather than inside the
    head image.
    """
    head_w, head_h, head_seed = head_image
    tail_w, tail_h, tail_seed = tail_image
    head = _noise(head_seed, head_w * head_h)
    tail = _noise(tail_seed, tail_w * tail_h)

    pad_len = 64
    for _ in range(6):
        d = Document(title=title)
        p1 = d.add_page()
        p1.text(72, p1.height - 60, f"{label} head", font="serif-bold", size=13)
        p1.image(36, 120, p1.width - 72, 560, gray=(head_w, head_h, head))
        p2 = d.add_page()
        p2.text(72, p2.height - 60, f"{label} tail", font="serif-bold", size=13)
        p2.image(36, 120, p2.width - 72, 560, gray=(tail_w, tail_h, tail))
        d.keywords = _filler(filler_seed, pad_len)
        d.save(path)
        size = os.path.getsize(path)
        if size == target:
            return size
        pad_len += target - size
        if pad_len < 0:
            raise ValueError(f"{path} overshoots {target} bytes by {-pad_len}")
    raise ValueError(f"{path} did not converge on {target} bytes")


TWIN_TARGET = 3 * MEGABYTE
TWIN_HEAD = (1600, 1000, 41001)
TWIN_TAIL_W, TWIN_TAIL_H = 1400, 1000


def _scan_twins(root):
    sizes = {}
    for name, title, tail_seed, filler_seed in (
        ("twin-a.pdf", "Twin A", 42001, 43001),
        ("twin-b.pdf", "Twin B", 42002, 43002),
    ):
        path = out_path(root, "scan", name)
        sizes[name] = _sized_document(
            path, TWIN_TARGET, TWIN_HEAD,
            (TWIN_TAIL_W, TWIN_TAIL_H, tail_seed), filler_seed, title, "Twin")
    a = open(out_path(root, "scan", "twin-a.pdf"), "rb").read()
    b = open(out_path(root, "scan", "twin-b.pdf"), "rb").read()
    shared = _shared_prefix_length(a, b)
    identities = {name: _document_identity(out_path(root, "scan", name))
                  for name in ("twin-a.pdf", "twin-b.pdf")}
    assert sizes["twin-a.pdf"] == sizes["twin-b.pdf"] == TWIN_TARGET
    assert shared >= MEGABYTE
    assert a[-MEGABYTE:] != b[-MEGABYTE:]
    assert a[:MEGABYTE] == b[:MEGABYTE]
    assert identities["twin-a.pdf"] != identities["twin-b.pdf"]
    for name in ("twin-a.pdf", "twin-b.pdf"):
        manifest.register(
            f"scan/{name}", out_path(root, "scan", name),
            "Exactly 3 MB, above the 2 MB whole file limit. The pair shares its "
            "first megabyte byte for byte and has the same total length, and "
            "differs inside the last megabyte, so only the tail read tells the "
            "two apart",
            pages=2, bytes=TWIN_TARGET,
            shared_prefix_bytes=shared,
            identity=identities[name],
            equal_length=True,
            tail_differs_within="the final 1 MB the identity scheme reads",
            identity_path="head plus tail plus length",
            device_path=f"/sdcard/scan/{name}",
        ).tag("library")


SHARED_PREFIX_TARGETS = {
    "shared-prefix-a.pdf": (5 * MEGABYTE) // 2,
    "shared-prefix-b.pdf": (11 * MEGABYTE) // 4,
}
SHARED_HEAD = (1400, 1000, 51001)
SHARED_TAIL_W, SHARED_TAIL_H = 1000, 1000


def _shared_prefix(root):
    paths = {}
    for name, title, tail_seed, filler_seed in (
        ("shared-prefix-a.pdf", "Shared Prefix A", 52001, 53001),
        ("shared-prefix-b.pdf", "Shared Prefix B", 52002, 53002),
    ):
        path = out_path(root, name)
        _sized_document(path, SHARED_PREFIX_TARGETS[name], SHARED_HEAD,
                        (SHARED_TAIL_W, SHARED_TAIL_H, tail_seed), filler_seed,
                        title, "Shared prefix")
        paths[name] = path
    a = open(paths["shared-prefix-a.pdf"], "rb").read()
    b = open(paths["shared-prefix-b.pdf"], "rb").read()
    shared = _shared_prefix_length(a, b)
    identities = {name: _document_identity(path) for name, path in paths.items()}
    assert shared >= MEGABYTE
    assert a[:MEGABYTE] == b[:MEGABYTE]
    assert len(a) != len(b)
    assert identities["shared-prefix-a.pdf"] != identities["shared-prefix-b.pdf"]
    for name, path in paths.items():
        manifest.register(
            name, path,
            "Over 2 MB, byte identical with its partner for the first megabyte "
            "the identity scheme hashes, different after it, and a different "
            "total length. Distinct from scan/twin-a.pdf and scan/twin-b.pdf, "
            "which are the equal length pair",
            pages=2, bytes=SHARED_PREFIX_TARGETS[name],
            shared_prefix_bytes=shared,
            identity=identities[name],
            equal_length=False,
            identity_path="head plus tail plus length",
            renamed_from="twins-a.pdf and twins-b.pdf, which must not be used",
        ).tag("library")


def _shared_prefix_length(a, b):
    limit = min(len(a), len(b))
    for i in range(limit):
        if a[i] != b[i]:
            return i
    return limit
