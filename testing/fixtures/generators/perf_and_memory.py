"""The performance and memory fixtures, including device-generated metadata.

These are deliberately separate from the shared set rather than reusing
`normal.pdf` and `large.pdf`. A budget is meaningless without a fixed input, and
the shared fixtures are edited whenever a functional test needs a new property,
so a shared fixture cannot carry a number a budget is calibrated against.

Three fixtures named by the test plan are not built here. `huge.pdf`,
`huge-500mb.pdf` and `oversized.json` are all above the roughly 50 MB line at
which the plan switches to on-device generation. They are registered with
`generated_on_device`; the Python runner owns their generation recipes and this
generator removes any retired `out/generate-on-device.sh`.

Sizes are hit by tuning, not by guessing: a document that has to land on a byte
target is saved, measured, and rebuilt with its figure padding corrected, until
the measurement lands within one figure row of the target. The padding is
incompressible, so the correction is linear and converges in two or three
passes. Every count a test reads is counted in the text that is actually drawn,
never asserted from intent.
"""

import hashlib
import os

from lib import manifest
from lib.doc import Document, OutlineEntry
from lib.registry import fixture_group, out_path

BODY = (
    "A reader measures a document by the time between wanting a page and seeing "
    "it. Everything else is bookkeeping. The pages here carry ordinary prose at "
    "an ordinary measure so that a render budget is taken against something a "
    "real book would contain, rather than against a page of test patterns. "
)

KNOWN_FIRST_LINE = "The proportional guard begins on this line."
KNOWN_WORD = "proportional"

CONSONANTS = "bcdfghjklmnprstvwz"
VOWELS = "aeiou"

# The planted search terms for perf-500p.pdf. Every one of them is impossible to
# form from the generated vocabulary, which alternates consonant and vowel, so a
# count of the drawn text is a count of the whole document.
EARLY_TERMS = [f"beacon{n:02d}" for n in range(1, 7)]
SPREAD_TERMS = [f"harrow{n:02d}" for n in range(1, 7)]
COMMON_TERM = "common"
COMMON_PER_PAGE = 12


def _noise(tag, length):
    """Deterministic incompressible bytes, which is what makes a file large."""
    return hashlib.shake_256(tag.encode("ascii")).digest(length)


def _vocabulary(count, seed):
    """A large pseudo word list.

    A small vocabulary compresses to nothing, and text only fixtures have to
    reach 6 MB and beyond, so the vocabulary is the size control.
    """
    words = []
    state = seed
    for _ in range(count):
        state = (1103515245 * state + 12345) & 0x7FFFFFFF
        syllables = 3 + ((state >> 9) % 2)
        w = ""
        for _ in range(syllables):
            state = (1103515245 * state + 12345) & 0x7FFFFFFF
            w += CONSONANTS[(state >> 7) % len(CONSONANTS)]
            w += VOWELS[(state >> 13) % len(VOWELS)]
        words.append(w)
    return words


class _Stream:
    """A deterministic word source shared by every dense page."""

    def __init__(self, vocabulary, seed):
        self.vocabulary = vocabulary
        self.state = seed

    def word(self):
        self.state = (1103515245 * self.state + 12345) & 0x7FFFFFFF
        return self.vocabulary[(self.state >> 7) % len(self.vocabulary)]

    def line(self, budget):
        parts, used = [], 0
        while used < budget:
            w = self.word()
            parts.append(w)
            used += len(w) + 1
        return " ".join(parts)


def _body_page(doc, index, label="Page", size=9.0, leading=12.0, body=BODY,
               repeat=4, lead_line=None):
    """One readable page of prose with its number at the top."""
    p = doc.add_page()
    p.text(56, p.height - 52, f"{label} {index + 1}", font="serif-bold", size=11)
    y = p.height - 76
    if lead_line:
        p.text(56, y, lead_line, font="serif", size=11)
        y -= 20
    doc.paragraph(p, 56, y, body * repeat, font="serif", size=size,
                  leading=leading, max_width=p.width - 112)
    return p


def _scan_raster(width, height, tag, noise_length):
    """A scan like grey page: pale paper, ink bands, and one noisy patch.

    The patch is where the size comes from. Deflate passes it through almost
    unchanged, so its length is the page's compressed length, and the rest of
    the page costs nothing.
    """
    paper = bytes([238]) * width
    left = width // 8
    run = width - 2 * left
    text_row = bytes([238]) * left + bytes([64]) * run + bytes([238]) * (width - left - run)
    head_row = bytes([238]) * left + bytes([32]) * (run // 2) + \
        bytes([238]) * (width - left - run // 2)
    rows = []
    for y in range(height):
        band = y * 60 // height
        if band == 1:
            rows.append(head_row)
        elif band >= 3 and (y // 3) % 8 < 5:
            rows.append(text_row)
        else:
            rows.append(paper)
    data = bytearray(b"".join(rows))
    if noise_length:
        start = (height // 3) * width
        noise_length = min(noise_length, len(data) - start)
        data[start:start + noise_length] = _noise(tag, noise_length)
    return bytes(data)


def _save_to_size(make, target, path, granularity):
    """Save, measure, correct the padding, repeat.

    `make(rows)` returns a Document carrying `rows` rows of incompressible
    figure padding. Padding passes through deflate at roughly one to one, so the
    correction is a division, and two or three passes land within one row.
    """
    rows = max(target // (granularity * 2), 1)
    best_rows, best_size = None, None
    for _ in range(6):
        make(rows).save(path)
        size = os.path.getsize(path)
        if best_size is None or abs(size - target) < abs(best_size - target):
            best_rows, best_size = rows, size
        if abs(size - target) < granularity:
            return size
        rows = max(rows + (target - size) // granularity, 1)
    if best_rows != rows:
        make(best_rows).save(path)
    return os.path.getsize(path)


@fixture_group("perf_memory", "Performance budgets and memory growth fixtures")
def build(root):
    _perf_small(root)
    _perf_scanned_40(root)
    _perf_500p(root)
    _perf_vector(root)
    _perf_protected(root)
    _perf_highlights(root)
    _perf_library(root)
    _big_images(root)
    _three_thousand_pages(root)
    _scanned_long(root)
    _many_highlights(root)
    _fifty_distinct(root)
    _size_ladder(root)
    _on_device_script(root)


def _perf_small(root):
    d = Document(title="Performance Small", author="A. Writer")
    for i in range(12):
        _body_page(d, i, lead_line=KNOWN_FIRST_LINE if i == 0 else None)
    path = out_path(root, "perf-small.pdf")
    d.save(path)
    manifest.register(
        "perf-small.pdf", path,
        "12 pages of text, no images, no outline, US Letter. The launch, first "
        "render, zoom and tile budgets are all calibrated against this file",
        pages=12, page_size="612 by 792 points, US Letter", outline_entries=0,
        images=0, link_annotations=0, highlight_annotations=0,
        bytes=os.path.getsize(path), known_line_page_index=0,
        known_line=KNOWN_FIRST_LINE,
        used_by='PERF-04, PERF-21, PERF-24, PERF-28; archived: PERF-07, PERF-14, PERF-18, PERF-19, PERF-29',
    ).tag("perf_memory")


def _perf_scanned_40(root):
    """Forty full page rasters at 300 dpi, no text layer at all."""
    width, height = 2550, 3300
    d = Document(title="Performance Scanned")
    for i in range(40):
        p = d.add_page()
        p.image(0, 0, p.width, p.height,
                gray=(width, height, _scan_raster(width, height,
                                                  f"perf-scan-{i}", 610000)))
    path = out_path(root, "perf-scanned-40.pdf")
    d.save(path)
    size = os.path.getsize(path)
    if not 20_000_000 <= size <= 30_000_000:
        raise ValueError(f"perf-scanned-40.pdf is {size} bytes, outside 20 to 30 MB")
    manifest.register(
        "perf-scanned-40.pdf", path,
        "40 pages, one full page 300 dpi raster each, zero extractable "
        "characters. The raster penalty and warm reopen budgets read this file",
        pages=40, extractable_characters=0, image_pixels="2550 by 3300, 300 dpi",
        images_per_page=1, bytes=size,
        image_encoding="8 bit DeviceGray, FlateDecode, not DCTDecode",
        deviation="the plan says baseline JPEG. The fixture library has no JPEG "
                  "encoder, and the properties the budgets read are "
                  "the pixel count, the page count and the file size",
        used_by='PERF-12, PERF-15, PERF-17, PERF-26; archived: PERF-08',
    ).tag("perf_memory", "slow")


def _perf_500p(root):
    """500 dense text pages with planted search terms of counted frequency."""
    vocabulary = _vocabulary(20000, 0x1234567)
    stream = _Stream(vocabulary, 999)
    d = Document(title="Performance Five Hundred", author="A. Writer")
    counts = {t: 0 for t in EARLY_TERMS + SPREAD_TERMS + [COMMON_TERM]}
    term_pages = {t: [] for t in EARLY_TERMS}
    for i in range(500):
        lines = [stream.line(195) for _ in range(100)]
        planted = [COMMON_TERM] * COMMON_PER_PAGE
        if i % 2 == 0:
            planted += SPREAD_TERMS
        if i < 3:
            planted += EARLY_TERMS[i * 2:i * 2 + 2]
        for j, token in enumerate(planted):
            li = (j * 7 + 3) % len(lines)
            parts = lines[li].split()
            parts[(j * 5 + 2) % len(parts)] = token
            lines[li] = " ".join(parts)
        p = d.add_page()
        y = p.height - 40
        for line in lines:
            p.text(28, y, line, font="serif", size=5.0)
            y -= 7.2
        for term in counts:
            hits = sum(line.count(term) for line in lines)
            counts[term] += hits
            if term in term_pages and hits:
                term_pages[term].append(i)
    d.outline = [
        OutlineEntry(f"Chapter {c + 1}", c * 20, children=[
            OutlineEntry(f"Chapter {c + 1} section {s + 1}", c * 20 + 7 * s + 3)
            for s in range(2)
        ])
        for c in range(25)
    ]
    path = out_path(root, "perf-500p.pdf")
    d.save(path)
    size = os.path.getsize(path)
    if not 6_000_000 <= size <= 10_000_000:
        raise ValueError(f"perf-500p.pdf is {size} bytes, outside 6 to 10 MB")
    for term in EARLY_TERMS:
        if counts[term] != 1 or len(term_pages[term]) != 1 or term_pages[term][0] > 2:
            raise ValueError(f"{term} is not a single early occurrence: "
                             f"{counts[term]} on {term_pages[term]}")
    for term in SPREAD_TERMS:
        if not 200 <= counts[term] <= 5000:
            raise ValueError(f"{term} has {counts[term]} hits, outside 200 to 5000")
    if counts[COMMON_TERM] <= 5000:
        raise ValueError(f"{COMMON_TERM} has {counts[COMMON_TERM]} hits, which "
                         f"does not exceed the 5000 entry cache limit")
    manifest.register(
        "perf-500p.pdf", path,
        "500 pages of dense text, uniform US Letter, with a two level outline. "
        "The search, scroll and page jump budgets read this file",
        pages=500, page_size="612 by 792 points, US Letter", images=0,
        bytes=size, outline_entries=75,
        outline_shape="25 top level chapters, each with 2 children",
        outline_destinations="chapter n at page index (n-1)*20",
        early_terms={t: term_pages[t][0] for t in EARLY_TERMS},
        early_term_note="each occurs exactly once, on the recorded page index, "
                        "so a first hit latency test has six distinct queries",
        spread_term_hits={t: counts[t] for t in SPREAD_TERMS},
        spread_term_note="each spread over the 250 even numbered pages, above "
                         "the 200 hit floor and below the 5000 entry "
                         "SearchSessionCache limit so the session caches",
        common_term=COMMON_TERM, common_term_hits=counts[COMMON_TERM],
        common_term_note="above PDF.TOO_MANY_RESULTS (3500) so the too many "
                         "results notice appears, and above MAX_CACHED_HITS "
                         "(5000) so the session is not cached",
        used_by='PERF-25, PERF-30, PERF-32, PERF-33, PERF-34; archived: PERF-09, PERF-13, PERF-22, PERF-23, PERF-31',
    ).tag("perf_memory", "slow")


def _perf_vector(root):
    """Dense path drawing, counted operation by operation."""
    d = Document(title="Performance Vector")
    per_page = 30000
    drawn = 0
    for i in range(10):
        p = d.add_page()
        state = 0x51ED + i
        ops = 0
        while ops < per_page:
            state = (1103515245 * state + 12345) & 0x7FFFFFFF
            x = 36 + (state >> 7) % 540
            y = 36 + (state >> 11) % 720
            if ops % 3 == 2:
                p.rect(x, y, 3 + (state >> 5) % 40, 3 + (state >> 9) % 30,
                       rgb=((state >> 3) % 7 / 7, (state >> 6) % 5 / 5,
                            (state >> 12) % 3 / 3), fill=False)
            else:
                p.line(x, y, x + ((state >> 13) % 60) - 30,
                       y + ((state >> 17) % 60) - 30,
                       rgb=((state >> 4) % 9 / 9, 0.2, 0.6), width=0.4)
            ops += 1
        if len(p.ops) != ops:
            raise ValueError(f"page {i} drew {len(p.ops)} ops, expected {ops}")
        drawn += ops
    path = out_path(root, "perf-vector.pdf")
    d.save(path)
    size = os.path.getsize(path)
    if size >= 5_000_000:
        raise ValueError(f"perf-vector.pdf is {size} bytes, over the 5 MB limit")
    manifest.register(
        "perf-vector.pdf", path,
        "10 pages of dense vector drawing, no raster images and no text. The "
        "vector render budget reads this file",
        pages=10, images=0, path_operations_per_page=per_page,
        path_operations_total=drawn, bytes=size,
        operation_mix="two thirds stroked lines, one third stroked rectangles",
        counted="len(page.ops) is compared against the loop count for every page",
        used_by='archived: PERF-16',
    ).tag("perf_memory")


def _perf_protected(root):
    d = Document(title="Performance Protected")
    for i in range(20):
        _body_page(d, i)
    path = out_path(root, "perf-protected.pdf")
    d.save(path, encrypt_password="test123")
    manifest.register(
        "perf-protected.pdf", path,
        "20 pages of text behind a user password. The password prompt and "
        "password to first page budgets read this file",
        pages=20, password="test123", owner_password=None,
        encryption="RC4 40 bit, revision 2", bytes=os.path.getsize(path),
        note="PreviewDiskCoordinator.attach refuses the preview cache for a "
             "document with a password, so every open of this file is cold",
        used_by='archived: PERF-10, PERF-11',
    ).tag("perf_memory")


def _highlight_line(doc, page, x, y, text, group, note=""):
    """One app owned highlight over a line of text that is really drawn."""
    page.text(x, y, text, font="serif", size=10)
    rect = doc.line_rect(page, x, y, text, size=10)
    page.highlight([rect], quote=text, note=note, group=group)


def _perf_highlights(root):
    d = Document(title="Performance Highlights")
    total = 0
    for i in range(100):
        p = _body_page(d, i, size=10, leading=14, repeat=2)
        for n in range(5):
            _highlight_line(d, p, 56, p.height - 300 - n * 22,
                            f"Highlighted line {n + 1} on page {i + 1}",
                            f"p{i}h{n}")
            total += 1
    path = out_path(root, "perf-500-highlights.pdf")
    d.save(path)
    if total != 500:
        raise ValueError(f"wrote {total} highlights, expected 500")
    manifest.register(
        "perf-500-highlights.pdf", path,
        "500 app owned highlight annotations spread evenly across 100 pages, 5 "
        "per page, each its own group, none carrying a note",
        pages=100, highlight_annotations=total, highlights_per_page=5,
        highlight_groups=total, groups_with_notes=0, app_owned=True,
        notes_note="Contents is present and empty on every annotation, and the "
                   "app treats a note as absent when it is blank",
        colour="palette yellow, the highlight default",
        bytes=os.path.getsize(path),
        used_by="PERF-35, PERF-36",
    ).tag("perf_memory")

    d = Document(title="Performance Highlights One Page")
    p = d.add_page()
    p.text(56, p.height - 52, "One page carrying five hundred highlights",
           font="serif-bold", size=11)
    shared, singles, groups = 0, 0, {}
    for n in range(500):
        # 250 annotations share 50 group keys, five lines each, which is the
        # multi line shape the regrouping draw path walks on every frame.
        group = f"shared{n // 5:02d}" if n < 250 else f"solo{n:03d}"
        y = p.height - 80 - (n % 60) * 11.5
        x = 40 + (n // 60) * 62
        text = f"h{n:03d}"
        p.text(x, y, text, font="serif", size=8)
        rect = d.line_rect(p, x, y, text, size=8)
        p.highlight([rect], quote=text, note="", group=group)
        groups[group] = groups.get(group, 0) + 1
        if n < 250:
            shared += 1
        else:
            singles += 1
    path = out_path(root, "perf-500-highlights-one-page.pdf")
    d.save(path)
    if len(p.annots) != 500 or shared != 250:
        raise ValueError(f"one page fixture has {len(p.annots)} annotations, "
                         f"{shared} of them grouped")
    manifest.register(
        "perf-500-highlights-one-page.pdf", path,
        "500 app owned highlight annotations on page index 0, of which 250 "
        "share 50 group keys at five annotations each. This is the fixture for "
        "the highlight regrouping draw path",
        pages=1, highlight_annotations=500, annotations_on_page_0=500,
        grouped_annotations=shared, distinct_groups=len(groups),
        annotations_sharing_a_group=shared, single_annotation_groups=singles,
        groups_with_notes=0, app_owned=True, bytes=os.path.getsize(path),
        used_by="PERF-35, PERF-36",
    ).tag("perf_memory")


def _tiny_vector_pdf(path, index):
    """A five kilobyte real PDF.

    No text: every fixture that draws text embeds a font, and a blanked DejaVu
    face still costs about 45 KB, which is nine times the size this fixture is
    specified at. Vector content keeps the file real, distinct and small.
    """
    d = Document(title=f"Library item {index + 1}")
    p = d.add_page()
    state = 0x9E3779B1 ^ (index * 2654435761)
    for _ in range(430):
        state = (1103515245 * state + 12345) & 0x7FFFFFFF
        x = 20 + (state >> 7) % 560
        y = 20 + (state >> 11) % 740
        if (state >> 3) % 4 == 0:
            p.rect(x, y, 4 + (state >> 5) % 60, 4 + (state >> 9) % 40,
                   rgb=((state >> 2) % 8 / 8, (state >> 6) % 6 / 6, 0.4),
                   fill=False)
        else:
            p.line(x, y, x + ((state >> 13) % 80) - 40,
                   y + ((state >> 17) % 80) - 40,
                   rgb=(0.2, (state >> 8) % 7 / 7, (state >> 14) % 5 / 5),
                   width=0.5)
    d.save(path)
    return os.path.getsize(path)


def _library_directories():
    """Twenty directories, the deepest four levels below the tree root."""
    dirs = []
    for a in ("fiction", "science", "history", "reference"):
        dirs.append((a,))
        for b in ("recent", "archive"):
            dirs.append((a, b))
            dirs.append((a, b, "boxed"))
    return sorted(dirs)


def _perf_library(root):
    dirs = _library_directories()
    if len(dirs) != 20 or max(len(d) for d in dirs) != 3:
        raise ValueError(f"{len(dirs)} directories, deepest {max(len(d) for d in dirs)}")
    total_bytes = 0
    smallest = largest = None
    for i in range(100000):
        parts = dirs[i % len(dirs)]
        path = out_path(root, "perf-library-100k", *parts, f"book-{i + 1:06d}.pdf")
        size = _tiny_vector_pdf(path, i)
        total_bytes += size
        smallest = size if smallest is None else min(smallest, size)
        largest = size if largest is None else max(largest, size)
    manifest.register(
        "perf-library-100k/", None,
        "100000 real single page PDFs across 20 directories, the deepest four "
        "levels below the fixture root counting the tree root itself. The files "
        "have to exist or AvailabilityProbe marks the seeded rows unavailable "
        "and the relink path runs instead of the path being measured",
        files=100000, directories=len(dirs), max_depth="4 levels including the root",
        bytes_on_disk=total_bytes, smallest_file=smallest, largest_file=largest,
        naming="book-000001.pdf through book-100000.pdf, distributed round robin",
        content="vector drawing, no text, so each file stays near 5 KB",
        device_path="/sdcard/Documents/perf-library/",
        push="once per run by the Python E2E runner, four tars extracted in "
             "parallel, about 5 minutes; the /sdcard FUSE layer caps file "
             "creation near 330 per second no matter how the bytes arrive",
        companion="perf-library-100k-missing is the same 100000 seeded PdfRecord "
                  "rows with no files on storage, so it is a Room seed rather "
                  "than a directory and nothing is built for it here",
        used_by="PERF-04, PERF-35, PERF-36, PERF-37, PERF-38, PERF-40, PERF-41",
    ).tag("perf_memory", "slow")


def _big_images(root):
    """Twelve pages whose decoded bitmap is the point, not the file size."""
    width, height = 4000, 5000
    d = Document(title="Big Images")
    for i in range(12):
        p = d.add_page()
        p.image(0, 0, p.width, p.height,
                gray=(width, height, _scan_raster(width, height,
                                                  f"big-image-{i}", 120000)))
    path = out_path(root, "big-images.pdf")
    d.save(path)
    manifest.register(
        "big-images.pdf", path,
        "12 pages, one 4000 by 5000 image on each. Twenty million samples per "
        "page, so the decoded bitmap is the pressure and the compressed file "
        "stays small",
        pages=12, images_per_page=1, image_pixels="4000 by 5000",
        samples_per_page=width * height, extractable_characters=0,
        bytes=os.path.getsize(path),
        image_encoding="8 bit DeviceGray, FlateDecode",
        used_by="MEM-07 and the per page bitmap pressure tests",
    ).tag("perf_memory", "slow")


def _three_thousand_pages(root):
    d = Document(title="Three Thousand Pages")
    for i in range(3000):
        p = d.add_page()
        p.text(56, p.height - 60, f"Page {i + 1} of 3000", font="serif-bold",
               size=12)
        p.text(56, p.height - 90,
               f"Short body line {i + 1}, kept short so the page count is what "
               f"grows.", font="serif", size=11)
    path = out_path(root, "three-thousand-pages.pdf")
    d.save(path)
    manifest.register(
        "three-thousand-pages.pdf", path,
        "3000 pages of two short lines each, for per page bookkeeping growth",
        pages=3000, lines_per_page=2, images=0,
        bytes=os.path.getsize(path),
        used_by="the per page bookkeeping growth tests in memory-and-leaks.md",
    ).tag("perf_memory", "slow")


def _scanned_long(root):
    """300 scan like pages, which the 8 page shared scanned.pdf cannot show."""
    width, height = 850, 1100
    d = Document(title="Scanned Long")
    for i in range(300):
        p = d.add_page()
        p.image(18, 18, p.width - 36, p.height - 36,
                gray=(width, height, _scan_raster(width, height,
                                                  f"scanned-long-{i}", 24000)))
    path = out_path(root, "scanned-long.pdf")
    d.save(path)
    manifest.register(
        "scanned-long.pdf", path,
        "300 image only pages, zero extractable characters, for the long scroll "
        "memory bound. The shared scanned.pdf is 8 pages and cannot show growth",
        pages=300, extractable_characters=0, images_per_page=1,
        image_pixels="850 by 1100, about 100 dpi on US Letter",
        resolution_note="300 pages at a true 300 dpi would be 2.5 GB of raw "
                        "samples in the build, and the test reads page count "
                        "and scroll distance rather than pixel density",
        bytes=os.path.getsize(path),
        used_by="the long scroll bound tests in memory-and-leaks.md",
    ).tag("perf_memory", "slow")


def _many_highlights(root):
    d = Document(title="Many Highlights")
    total, with_notes = 0, 0
    for i in range(200):
        p = _body_page(d, i, size=10, leading=14, repeat=2)
        for n in range(3):
            note = "worth revisiting" if (i * 3 + n) % 20 == 0 else ""
            _highlight_line(d, p, 56, p.height - 300 - n * 22,
                            f"Swept line {n + 1} on page {i + 1}",
                            f"m{i}h{n}", note=note)
            total += 1
            if note:
                with_notes += 1
    path = out_path(root, "many-highlights.pdf")
    d.save(path)
    if total != 600:
        raise ValueError(f"wrote {total} highlights, expected 600")
    manifest.register(
        "many-highlights.pdf", path,
        "200 pages carrying 600 app owned highlight annotations, 3 per page, "
        "for the highlights sweeper that walks every page",
        pages=200, highlight_annotations=total, highlights_per_page=3,
        highlight_groups=total, groups_with_notes=with_notes, app_owned=True,
        bytes=os.path.getsize(path),
        used_by="MEM-25 and the sweepPageHighlights tests",
    ).tag("perf_memory")


def _fifty_distinct(root):
    """Fifty documents no two of which can share a cache entry."""
    total = 0
    for i in range(50):
        d = Document(title=f"Distinct {i + 1}", author=f"Author {i + 1}")
        for page in range(3):
            p = d.add_page()
            # One font, not two, because a second embedded face doubles a file
            # that is meant to stay small and there are fifty of them.
            p.text(56, p.height - 52, f"Document {i + 1}, page {page + 1}",
                   font="serif", size=13)
            d.paragraph(p, 56, p.height - 80,
                        f"Distinct document number {i + 1}. " + BODY * 2,
                        font="serif", size=11, leading=15,
                        max_width=p.width - 112)
        path = out_path(root, "fifty-distinct", f"distinct-{i + 1:02d}.pdf")
        d.save(path)
        total += os.path.getsize(path)
    manifest.register(
        "fifty-distinct/", None,
        "50 three page PDFs whose text, title and author all differ, so every "
        "one has a distinct DocumentIdentity and none can be served from "
        "another's cache entry",
        files=50, pages_each=3, bytes_on_disk=total,
        naming="distinct-01.pdf through distinct-50.pdf",
        identity_path="whole file, every one is far under 2 MB",
        used_by="MEM-06 and the cache eviction tests",
    ).tag("perf_memory")


def _text_and_figures(pages, figure_every, figure_width, pad_rows, title,
                      known_line=None):
    """Readable pages with figures, the last figure absorbing the padding.

    Padding is spread over the figures rather than dumped in one place, so the
    document reads as a book with illustrations rather than as a file with junk
    stapled to it.
    """
    d = Document(title=title, author="A. Writer")
    figures = [i for i in range(pages) if i % figure_every == 0]
    per_figure = pad_rows // len(figures)
    remainder = pad_rows - per_figure * (len(figures) - 1)
    for i in range(pages):
        p = _body_page(d, i, size=9, leading=12, repeat=3,
                       lead_line=known_line if i == 0 else None)
        if i in figures:
            rows = remainder if i == figures[-1] else per_figure
            if rows <= 0:
                continue
            data = _noise(f"{title}-figure-{i}", figure_width * rows)
            p.image(56, 90, 260, 190, gray=(figure_width, rows, data))
    return d


def _size_ladder(root):
    """The three size steps the save growth and proportionality tests read."""
    if KNOWN_WORD in BODY:
        raise ValueError("the known word must occur only in the known line")
    target = 300_000
    path = out_path(root, "small-300kb.pdf")
    size = _save_to_size(
        lambda rows: _text_and_figures(20, 7, 300, rows, "Small Three Hundred",
                                       known_line=KNOWN_FIRST_LINE),
        target, path, 300)
    manifest.register(
        "small-300kb.pdf", path,
        "20 pages of readable text with three figures, sized to land on 300 KB. "
        "Under the 2,097,152 byte limit, so DocumentIdentity hashes the whole "
        "file for it",
        pages=20, bytes=size, target_bytes=target, figures=3,
        identity_path="whole file, under the 2 MB limit",
        known_line_page_index=0, known_line=KNOWN_FIRST_LINE,
        known_word=KNOWN_WORD, known_word_occurrences=1,
        known_word_note="the body prose does not contain it, checked in the "
                        "generator, so a clipboard or quote assertion has "
                        "ground truth",
        highlight_annotations=0,
        used_by="ART-01, ART-02, ART-05, ART-06 and the save growth bound",
    ).tag("perf_memory")

    target = 2_400_000
    path = out_path(root, "medium-2.3mb.pdf")
    size = _save_to_size(
        lambda rows: _text_and_figures(160, 10, 360, rows, "Medium Two Point Three"),
        target, path, 360)
    if size <= 2_097_152:
        raise ValueError(f"medium-2.3mb.pdf is {size} bytes, which is not above "
                         f"the 2 MB whole file identity limit")
    manifest.register(
        "medium-2.3mb.pdf", path,
        "160 pages of readable text with 16 figures, sized to land on 2.29 MiB. "
        "This is the size the save growth bug was reported against, and it sits "
        "above the 2,097,152 byte limit where DocumentIdentity switches to head "
        "plus tail plus length",
        pages=160, bytes=size, target_bytes=target, figures=16,
        size_note="2,400,000 bytes is 2.29 MiB and 2.4 MB decimal, which is the "
                  "roughly 2.3 MB the report named",
        identity_path="head plus tail plus length, over 2 MB",
        highlight_annotations=0,
        used_by="ART-04, ART-17 and the save growth regression guard",
    ).tag("perf_memory")

    target = 20_000_000
    path = out_path(root, "large-20mb.pdf")
    size = _save_to_size(
        lambda rows: _text_and_figures(120, 1, 480, rows, "Large Twenty"),
        target, path, 480)
    manifest.register(
        "large-20mb.pdf", path,
        "120 pages of readable text with a figure on every page, sized to land "
        "on 20 MB. Image heavy on purpose, for the size proportionality tests",
        pages=120, bytes=size, target_bytes=target, figures=120,
        identity_path="head plus tail plus length, over 2 MB",
        highlight_annotations=0,
        used_by="the size proportionality tests in artifacts.md",
    ).tag("perf_memory", "slow")


def _on_device_script(root):
    """Discard the retired shell recipe; the Python runner owns device generation."""
    path = out_path(root, "generate-on-device.sh")
    if os.path.exists(path):
        os.unlink(path)

    manifest.register(
        "huge.pdf", None,
        "Generated on device. A valid PDF padded past 250 MB, the "
        "TemporaryCopyImporter.LARGE_FILE_BYTES threshold, for the large file "
        "consent path",
        generated_on_device=True, recipe="python3 testing/runner/run.py",
        bytes=268_539_041, pad_bytes=268_435_456,
        bytes_note="perf-small.pdf as built, plus the 256 MiB comment, plus "
                   "about 190 bytes of trailer. The Python runner computes the "
                   "cross-reference offset before writing",
        construction="perf-small.pdf, then a 256 MiB comment, then an "
                     "incremental update that adds no objects and chains to the "
                     "original cross reference table with /Prev",
        pages="the page count of perf-small.pdf, which is 12",
        device_path="/sdcard/Download/huge.pdf",
        teardown="delete it after the test, the Orchestrator clears package "
                 "data but not shared storage",
        shared_by="launch and intake, and the settings matrix",
    ).tag("perf_memory")

    manifest.register(
        "huge-500mb.pdf", None,
        "Generated on device. Roughly 200 pages, each with its own 2 MiB "
        "uncompressed DeviceGray image, so the file is over 400 MB of real page "
        "content",
        generated_on_device=True, recipe="python3 testing/runner/run.py",
        approximate_bytes=419_527_000, pages=200,
        bytes_note="200 pages of 2 MiB each plus about 480 bytes of objects per "
                   "page. The Python runner builds the assembly plan",
        image_pixels="1024 by 2048 per page, uncompressed, no filter",
        construction="the host writes every small object as a piece plus an "
                     "assembly plan, the device concatenates the pieces and a "
                     "repeated 1 MiB block",
        device_path="/sdcard/Download/huge-500mb.pdf",
        teardown="delete it after the test",
    ).tag("perf_memory")

    manifest.register(
        "oversized.json", None,
        "Generated on device. A backup file over 32 MB, for the oversized "
        "import rejection",
        generated_on_device=True, recipe="python3 testing/runner/run.py",
        approximate_bytes=34_600_000,
        construction="a canonical schemaVersion 1 object whose pad member is 33 "
                     "MiB of the letter A",
        device_path="/sdcard/Documents/backup-fixtures/oversized.json",
        teardown="delete it after the test",
    ).tag("perf_memory")
