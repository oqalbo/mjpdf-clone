"""Structurally broken files and files that are not PDFs at all.

Everything here is a legitimate PDF construct pushed to a shape the app should
refuse rather than follow, or a non PDF wearing a .pdf name. Nothing carries an
exploit payload. The tests that read these assert a graceful failure: an error
message, a clean close, no crash, no hang, no unbounded allocation.

The `malformed` tag means "do not expect this to parse", which is what the
`--check` pass reads it as. It is not a group marker, and it is applied only
where a strict parser must refuse the file. Everything else stays in the check
deliberately, including the damaged shapes a repairing parser recovers, because
the check is the only proof that these hand assembled bytes are well formed.

`corrupt.pdf` is the shared truncation fixture and belongs to `core`, not here.
`security.py` builds the active content and link families and imports the byte
assembly helpers below.
"""

import io
import re
import struct
import zipfile
import zlib

from generators import core as core_gen
from lib import manifest
from lib.doc import Document
from lib.registry import fixture_group, out_path

LETTER_BOX = b"[0 0 612 792]"
HELVETICA = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"


def assemble_pdf(objects, trailer, version=b"1.7"):
    """Lay out numbered objects with a correct cross reference table.

    objects is a list of (number, body bytes) in ascending number order, and
    trailer is the extra trailer syntax after /Size. Hand assembly rather than a
    library, because a library that repairs its output cannot express any of
    these fixtures.
    """
    out = bytearray(b"%PDF-" + version + b"\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num, body in objects:
        offsets[num] = len(out)
        out += str(num).encode("ascii") + b" 0 obj\n" + body + b"\nendobj\n"
    xref_at = len(out)
    size = max(offsets) + 1
    out += b"xref\n0 " + str(size).encode("ascii") + b"\n"
    out += b"0000000000 65535 f \n"
    for num in range(1, size):
        out += f"{offsets.get(num, 0):010d} 00000 n \n".encode("ascii")
    out += b"trailer\n<< /Size " + str(size).encode("ascii") + b" " + trailer + b" >>"
    out += b"\nstartxref\n" + str(xref_at).encode("ascii") + b"\n%%EOF\n"
    return bytes(out)


def stream_obj(keys, data, length=None):
    """A stream object body. length overrides the declared /Length, which is how
    a fixture declares a size the stream does not have."""
    declared = str(len(data)).encode("ascii") if length is None else length
    return (b"<< " + keys + b" /Length " + declared + b" >>\nstream\n"
            + data + b"\nendstream")


def text_stream(lines, x=72, top=720, size=12, leading=18):
    """A content stream drawing one line per entry against /F1."""
    ops = []
    for i, line in enumerate(lines):
        ops.append(f"BT /F1 {size} Tf 1 0 0 1 {x} {top - i * leading} Tm "
                   f"({line}) Tj ET")
    return "\n".join(ops).encode("ascii")


def page_obj(parent, contents, resources=b"<< /Font << /F1 5 0 R >> >>",
             media=LETTER_BOX, extra=b""):
    return (b"<< /Type /Page /Parent " + parent + b" /MediaBox " + media
            + b" /Resources " + resources + b" /Contents " + contents
            + extra + b" >>")


def write(path, data):
    with open(path, "wb") as fh:
        fh.write(data)
    return path


@fixture_group("malformed", "Broken structures and files that are not PDFs")
def build(root):
    _empty(root)
    _not_a_pdf(root)
    _truncated_tail(root)
    _readonly(root)
    _decoy(root)
    _bad_xref(root)
    _dangling_ref(root)
    _circular_ref(root)
    _deep_nest(root)
    _huge_pagecount(root)
    _cyclic_pagetree(root)
    _cyclic_outline(root)
    _huge_page(root)
    _bad_font(root)
    _bad_image(root)
    _unsupported_crypt(root)
    _size_fixture(root)


def _empty(root):
    path = out_path(root, "empty.pdf")
    write(path, b"")
    manifest.register(
        "empty.pdf", path,
        "Zero bytes under a .pdf name (INTAKE, SEC-18 family)",
        bytes=0,
    ).tag("malformed")


SHELL_MARKER = "mjsec18-marker"
SHELL_MARKER_FILE = "/sdcard/mjsec18-marker.txt"


def _not_a_pdf(root):
    """Three wrong file types under one name plus two suffixes.

    SEC-18 asserts that no process is spawned and that the marker file the shell
    script would create does not exist, so the marker strings are facts.
    """
    script = (
        "#!/bin/sh\n"
        f"# inert fixture, never executed. Marker: {SHELL_MARKER}\n"
        f"echo {SHELL_MARKER} > {SHELL_MARKER_FILE}\n"
    ).encode("ascii")
    path = out_path(root, "not-a-pdf.pdf")
    write(path, script)
    manifest.register(
        "not-a-pdf.pdf", path,
        "A plain shell script beginning #!/bin/sh under a .pdf name (SEC-18)",
        variant="shell script", first_bytes="#!/bin/sh",
        marker_string=SHELL_MARKER, marker_file=SHELL_MARKER_FILE,
        push_note="push with the executable bit set where the filesystem allows",
    ).tag("malformed")

    elf = _minimal_elf()
    path = out_path(root, "not-a-pdf-elf.pdf")
    write(path, elf)
    manifest.register(
        "not-a-pdf-elf.pdf", path,
        "A valid static ELF64 x86-64 binary that exits immediately, under a "
        ".pdf name (SEC-18 second variant)",
        variant="ELF binary", bytes=len(elf), first_bytes="\\x7fELF",
        behaviour="exit(0) if it were ever run",
    ).tag("malformed")

    zipped = _minimal_zip()
    path = out_path(root, "not-a-pdf-zip.pdf")
    write(path, zipped)
    manifest.register(
        "not-a-pdf-zip.pdf", path,
        "A ZIP archive holding one text member, under a .pdf name (SEC-18 "
        "third variant)",
        variant="ZIP archive", bytes=len(zipped), first_bytes="PK\\x03\\x04",
        members=["readme.txt"],
    ).tag("malformed")


def _minimal_elf():
    """A 129 byte ET_EXEC that calls exit(0). Hand packed so it is byte stable."""
    code = bytes([0xB8, 0x3C, 0x00, 0x00, 0x00,     # mov eax, 60
                  0x31, 0xFF,                       # xor edi, edi
                  0x0F, 0x05])                      # syscall
    ehsize, phentsize, base = 64, 56, 0x400000
    entry_off = ehsize + phentsize
    total = entry_off + len(code)
    header = b"\x7fELF" + bytes([2, 1, 1, 0]) + b"\x00" * 8
    header += struct.pack("<HHI", 2, 0x3E, 1)
    header += struct.pack("<QQQ", base + entry_off, ehsize, 0)
    header += struct.pack("<IHHHHHH", 0, ehsize, phentsize, 1, 0, 0, 0)
    program = struct.pack("<IIQQQQQQ", 1, 5, 0, base, base, total, total, 0x1000)
    return header + program + code


def _minimal_zip():
    """create_system and date_time are pinned, or the archive varies per run."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        info = zipfile.ZipInfo("readme.txt", date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.create_system = 3
        z.writestr(info, "This archive is a fixture. It is not a PDF.\n")
    return buf.getvalue()


def _normal_pages(d, count):
    """The construction core uses for normal.pdf, so a copy is a real copy."""
    for i in range(count):
        core_gen._numbered_page(d, i)
    return d


def _truncated_tail(root):
    """A document the reader can open whose trailer and last object are gone.

    TXT-05 needs a file that loads but cannot be reopened for extraction. The cut
    is at the header of the final object, so the cross reference table, the
    trailer and startxref all go with it and pdfium has to rebuild by scanning.
    """
    d = _normal_pages(Document(title="Truncated Tail"), 6)
    path = out_path(root, "text-truncated-tail.pdf")
    d.save(path, damage=_cut_last_object)
    manifest.register(
        "text-truncated-tail.pdf", path,
        "6 page text document with its trailer, cross reference table and final "
        "object cut off, for TXT-05",
        pages=6, cut="from the header of the last object to end of file",
        remaining="object bodies only, no xref and no trailer",
        opens="yes. A scanning repair recovers all 6 pages, which is the "
              "precondition TXT-05 needs",
    )


def _cut_last_object(data):
    last = data.rfind(b" 0 obj\n")
    start = data.rfind(b"\n", 0, last) + 1
    return data[:start]


def _readonly(root):
    """Same construction as normal.pdf, so the two files are byte identical."""
    d = _normal_pages(Document(title="Normal Document", author="A. Writer"), 20)
    path = out_path(root, "readonly.pdf")
    d.save(path)
    manifest.register(
        "readonly.pdf", path,
        "A copy of normal.pdf for the destination that cannot be written. The "
        "444 mode is set by the runner after the push, not here",
        pages=20, mode_required="444 on device",
        identical_to="normal.pdf",
    )


DECOY_BODY = (
    "This is the decoy document. Nothing in it repeats the normal document, so "
    "a save that lands on the wrong file is visible in the first line of the "
    "first page rather than in a byte comparison. The page count differs too. "
)


def _decoy(root):
    d = Document(title="Decoy Document", author="B. Other")
    for i in range(7):
        p = d.add_page()
        p.text(72, p.height - 60, f"Decoy page {i + 1}", font="serif-bold", size=13)
        d.paragraph(p, 72, p.height - 90, DECOY_BODY * 2, size=11,
                    max_width=p.width - 144)
    path = out_path(root, "decoy.pdf")
    d.save(path)
    manifest.register(
        "decoy.pdf", path,
        "A second distinct text document, 7 pages, different bytes and a "
        "different page count from normal.pdf, so a test can prove a save "
        "landed on the right file",
        pages=len(d.pages), first_line_page1="Decoy page 1",
        title="Decoy Document", author="B. Other",
    )


def _bad_xref(root):
    """Every offset in the table is wrong but well formed.

    pdfium rebuilds a broken table by scanning for object headers, so SEC-02
    expects a repaired load rather than a refusal. The value is that the page
    count it lands on is pinned.
    """
    d = _normal_pages(Document(title="Bad Cross Reference"), 12)
    path = out_path(root, "bad-xref.pdf")
    counted = {}
    d.save(path, damage=lambda data: _wrong_offsets(data, counted))
    manifest.register(
        "bad-xref.pdf", path,
        "12 page text document whose cross reference offsets are all wrong by "
        "4096 bytes. Object bodies untouched, so a scanning repair finds every "
        "page (SEC-02)",
        pages=12, entries_rewritten=counted["n"],
        offset_shift=4096, repairable="yes, by header scan",
        repair_check="pikepdf recovers all 12 pages from the object headers, "
                     "so the bodies are intact and only the index is wrong",
    )


def _wrong_offsets(data, counted):
    head, sep, tail = data.rpartition(b"\nxref\n")

    def repl(m):
        wrong = (int(m.group(1)) + 4096) % 10 ** 10
        return f"{wrong:010d} ".encode("ascii") + m.group(2) + b" n \n"

    tail, n = re.subn(rb"(\d{10}) (\d{5}) n \n", repl, tail)
    counted["n"] = n
    return head + sep + tail


def _dangling_ref(root):
    """Page index 1 points its /Contents at object 99, which is not in the file."""
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R 6 0 R] /Count 2 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R")),
        (4, stream_obj(b"", text_stream([
            "Page one of the dangling reference fixture.",
            "Page two points at object 99, which does not exist.",
        ]))),
        (5, HELVETICA),
        (6, page_obj(b"2 0 R", b"99 0 R")),
    ]
    path = out_path(root, "dangling-ref.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "dangling-ref.pdf", path,
        "2 pages. The page at index 1 references object 99 as its content "
        "stream and no object 99 exists (SEC-03)",
        pages=2, dangling_page_index=1, dangling_object="99 0 R",
        expected="page index 1 blank, or a clean load failure",
    )


def _circular_ref(root):
    """Two /Parent nodes referencing each other, plus a self referential length.

    The content stream declares /Length 6 0 R, which is the stream's own object
    number, so resolving the length requires the object that the length is part
    of.
    """
    # No font resource, so the only unresolvable thing in the file is the cycle.
    body = b"0.2 0.2 0.2 rg 72 600 200 100 re f\n"
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"4 0 R", b"6 0 R", resources=b"<< >>")),
        (4, b"<< /Type /Pages /Parent 5 0 R /Kids [3 0 R] /Count 1 >>"),
        (5, b"<< /Type /Pages /Parent 4 0 R /Kids [4 0 R] /Count 1 >>"),
        (6, stream_obj(b"", body, length=b"6 0 R")),
    ]
    path = out_path(root, "circular-ref.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "circular-ref.pdf", path,
        "1 page. Object 4 has /Parent 5 0 R and object 5 has /Parent 4 0 R, and "
        "the content stream declares /Length 6 0 R, its own object number "
        "(SEC-04)",
        pages=1, parent_cycle="4 to 5 to 4",
        indirect_length="stream 6 declares /Length 6 0 R",
        expected="load settles within 15 seconds, no unbounded allocation",
    )


NEST_DEPTH = 50000


def _deep_nest(root):
    opened = b"[" * NEST_DEPTH
    nested = opened + b"]" * NEST_DEPTH
    if nested.count(b"[") != NEST_DEPTH or nested.count(b"]") != NEST_DEPTH:
        raise ValueError("deep-nest.pdf brackets are unbalanced")
    resources = (b"<< /Font << /F1 5 0 R >> /MJNest " + nested + b" >>")
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R", resources=resources)),
        (4, stream_obj(b"", text_stream([
            "The resource dictionary of this page nests arrays 50,000 deep.",
        ]))),
        (5, HELVETICA),
    ]
    path = out_path(root, "deep-nest.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "deep-nest.pdf", path,
        "1 page whose resource dictionary holds an array nested 50,000 levels "
        "deep under the key /MJNest (SEC-05)",
        pages=1, nesting_depth=NEST_DEPTH, nesting_key="/MJNest",
        expected="no native stack overflow, load settles within 15 seconds",
        page_may_vanish="a parser that gives up on the nesting drops the page "
                        "dictionary with it: pikepdf opens this file and "
                        "reports zero pages",
    )


def _huge_pagecount(root):
    for name, count, ident in (
        ("huge-pagecount.pdf", 50000000, "fifty million"),
        ("huge-pagecount-maxint.pdf", 2147483647, "Integer.MAX_VALUE"),
    ):
        objs = [
            (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
            (2, b"<< /Type /Pages /Kids [3 0 R] /Count "
                + str(count).encode("ascii") + b" >>"),
            (3, page_obj(b"2 0 R", b"4 0 R")),
            (4, stream_obj(b"", text_stream([
                "The page tree of this document claims " + ident + " pages.",
                "There is one.",
            ]))),
            (5, HELVETICA),
        ]
        path = out_path(root, name)
        write(path, assemble_pdf(objs, b"/Root 1 0 R"))
        manifest.register(
            name, path,
            f"Page tree declaring /Count {count} while /Kids holds one real "
            "page (SEC-08, SEC-09)",
            declared_count=count, real_pages=1,
            expected="load settles within 20 seconds and count is 1 if loaded",
        )


def _cyclic_pagetree(root):
    """The /Kids array of the pages node contains the pages node."""
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R 2 0 R] /Count 2 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R")),
        (4, stream_obj(b"", text_stream([
            "The page tree of this document contains itself.",
        ]))),
        (5, HELVETICA),
    ]
    path = out_path(root, "cyclic-pagetree.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "cyclic-pagetree.pdf", path,
        "Pages node whose /Kids array holds its own object number, so a naive "
        "walk never terminates (SEC-10)",
        real_pages=1, declared_count=2, cycle="2 0 R appears in its own /Kids",
        expected="load settles within 15 seconds and count is finite",
    ).tag("malformed")


OUTLINE_DEPTH = 500


def _cyclic_outline(root):
    """Three top level entries whose last /Next points back at the first, and a
    /First chain 500 levels deep under the first of them.

    MAX_BOOKMARK_DEPTH in PdfiumCore is 64, so SEC-11 asserts the displayed
    indentation stops there and that the visited set breaks the sibling cycle.
    """
    for name, actions in (("cyclic-outline.pdf", False),
                          ("cyclic-outline-actions.pdf", True)):
        objs, entries = _outline_objects(actions)
        path = out_path(root, name)
        write(path, assemble_pdf(objs, b"/Root 1 0 R"))
        facts = {
            "outline_entries_written": entries,
            "top_level_entries": 3,
            "sibling_cycle": "the third top level /Next points at the first",
            "first_chain_depth": OUTLINE_DEPTH,
            "max_depth_shown": "64, matching MAX_BOOKMARK_DEPTH",
        }
        if actions:
            facts["entry_actions"] = [
                "top level 1 carries /A << /S /Launch /F (/system/bin/sh) >>",
                "top level 2 carries /A << /S /URI /URI (https://example.invalid/toc) >>",
            ]
        manifest.register(
            name, path,
            "Outline tree with a sibling cycle and a 500 level /First chain"
            + (", the first two entries carrying a /Launch and a /URI action "
               "instead of a destination (SEC-12)" if actions
               else " (SEC-11)"),
            **facts,
        )


def _outline_objects(actions):
    """Objects 1 to 5 are the document, 6 is /Outlines, 7 onward are entries."""
    page = b"3 0 R"
    root_ref = b"6 0 R"
    top = [7, 8, 9]
    chain_first = 10
    chain = list(range(chain_first, chain_first + OUTLINE_DEPTH))

    entries = []
    for i, num in enumerate(top):
        d = [b"<< /Title (Top level " + str(i + 1).encode("ascii") + b")",
             b"/Parent " + root_ref]
        if i > 0:
            d.append(b"/Prev " + str(top[i - 1]).encode("ascii") + b" 0 R")
        # The last sibling points back at the first, which is the cycle.
        nxt = top[i + 1] if i + 1 < len(top) else top[0]
        d.append(b"/Next " + str(nxt).encode("ascii") + b" 0 R")
        if i == 0:
            d.append(b"/First " + str(chain[0]).encode("ascii") + b" 0 R")
            d.append(b"/Last " + str(chain[0]).encode("ascii") + b" 0 R")
            d.append(b"/Count 1")
        if actions and i == 0:
            d.append(b"/A << /S /Launch /F (/system/bin/sh) >>")
        elif actions and i == 1:
            d.append(b"/A << /S /URI /URI (https://example.invalid/toc) >>")
        else:
            d.append(b"/Dest [" + page + b" /XYZ null null null]")
        d.append(b">>")
        entries.append((num, b" ".join(d)))

    for depth, num in enumerate(chain):
        parent = top[0] if depth == 0 else chain[depth - 1]
        d = [b"<< /Title (Level " + str(depth + 1).encode("ascii") + b")",
             b"/Parent " + str(parent).encode("ascii") + b" 0 R",
             b"/Dest [" + page + b" /XYZ null null null]"]
        if depth + 1 < len(chain):
            child = chain[depth + 1]
            d.append(b"/First " + str(child).encode("ascii") + b" 0 R")
            d.append(b"/Last " + str(child).encode("ascii") + b" 0 R")
            d.append(b"/Count 1")
        d.append(b">>")
        entries.append((num, b" ".join(d)))

    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R /Outlines " + root_ref
            + b" /PageMode /UseOutlines >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R")),
        (4, stream_obj(b"", text_stream([
            "This document has a cyclic and over deep table of contents.",
        ]))),
        (5, HELVETICA),
        (6, b"<< /Type /Outlines /First 7 0 R /Last 9 0 R /Count 3 >>"),
    ]
    objs.extend(sorted(entries))
    return objs, len(entries)


def _huge_page(root):
    for name, media, note in (
        ("huge-page.pdf", b"[0 0 14400 14400]", "the PDF format maximum, square"),
        ("huge-page-aspect.pdf", b"[0 0 1 4000000]",
         "one point wide and four million tall"),
    ):
        objs = [
            (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
            (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
            (3, page_obj(b"2 0 R", b"4 0 R", media=media)),
            (4, stream_obj(b"", b"0.2 0.2 0.2 rg 0 0 50 50 re f\n"
                           + text_stream(["Huge page fixture."], top=20))),
            (5, HELVETICA),
        ]
        path = out_path(root, name)
        write(path, assemble_pdf(objs, b"/Root 1 0 R"))
        manifest.register(
            name, path,
            f"One page with /MediaBox {media.decode('ascii')}, {note} (SEC-15)",
            pages=1, media_box=media.decode("ascii"),
            expected="peak heap stays within 256 MB of the pre load value",
        )


def _bad_font(root):
    """A FontFile2 that is not a font, and a second variant that lies about it."""
    garbage = bytes((i * 37 + 11) & 0xFF for i in range(200))
    for name, length1, note in (
        ("bad-font.pdf", len(garbage), "declared length matches the garbage"),
        ("bad-font-length1.pdf", 5000000,
         "declared /Length1 of 5,000,000 against a 200 byte stream"),
    ):
        objs = [
            (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
            (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
            (3, page_obj(b"2 0 R", b"4 0 R",
                         resources=b"<< /Font << /F1 5 0 R >> >>")),
            (4, stream_obj(b"", text_stream([
                "This line is drawn with an embedded font that is not a font.",
                "The renderer is expected to fall back, not to crash.",
            ]))),
            (5, b"<< /Type /Font /Subtype /TrueType /BaseFont /MJBroken"
                b" /FirstChar 32 /LastChar 126 /Widths [" +
                b" ".join(b"500" for _ in range(95)) +
                b"] /Encoding /WinAnsiEncoding /FontDescriptor 6 0 R >>"),
            (6, b"<< /Type /FontDescriptor /FontName /MJBroken /Flags 32"
                b" /FontBBox [0 -200 1000 900] /ItalicAngle 0 /Ascent 900"
                b" /Descent -200 /CapHeight 700 /StemV 80 /FontFile2 7 0 R >>"),
            (7, stream_obj(b"/Length1 " + str(length1).encode("ascii"), garbage)),
        ]
        path = out_path(root, name)
        write(path, assemble_pdf(objs, b"/Root 1 0 R"))
        manifest.register(
            name, path,
            "One page drawing text with an embedded FontFile2 of 200 bytes of "
            f"non font data, {note} (SEC-13)",
            pages=1, font_stream_bytes=len(garbage), declared_length1=length1,
            expected="render reaches idle, no SIGSEGV, File Properties opens",
            baseline="yes, fullscreen page one",
        )


JPEG_HEAD = bytes([
    0xFF, 0xD8,                                      # SOI
    0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46,  # APP0 JFIF
    0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01,
    0x00, 0x00,
    0xFF, 0xC0, 0x00, 0x11, 0x08, 0x00, 0x40, 0x00,  # SOF0, 64 by 64, 3 comps
    0x40, 0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x01,
    0x03, 0x11, 0x01,
])


def _bad_image(root):
    """A truncated JPEG, and a second variant with an illegal bit depth.

    SEC-14 compares a screenshot against a baseline, which is what proves the
    failed region is deterministic rather than uninitialised memory.
    """
    flate_gray = zlib.compress(bytes((i * 91) & 0xFF for i in range(64 * 64)), 9)
    variants = (
        ("bad-image.pdf",
         b"/Type /XObject /Subtype /Image /Width 64 /Height 64"
         b" /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode",
         JPEG_HEAD, "JPEG cut off after the frame header, no scan data"),
        ("bad-image-bpc.pdf",
         b"/Type /XObject /Subtype /Image /Width 64 /Height 64"
         b" /ColorSpace /DeviceGray /BitsPerComponent 7 /Filter /FlateDecode",
         flate_gray, "/BitsPerComponent 7, which the format does not allow"),
    )
    for name, keys, data, note in variants:
        objs = [
            (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
            (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
            (3, page_obj(b"2 0 R", b"4 0 R",
                         resources=b"<< /Font << /F1 5 0 R >>"
                                   b" /XObject << /Im0 6 0 R >> >>")),
            (4, stream_obj(b"", text_stream([
                "The image below is deliberately malformed.",
            ]) + b"\nq 300 0 0 300 72 300 cm /Im0 Do Q\n")),
            (5, HELVETICA),
            (6, stream_obj(keys, data)),
        ]
        path = out_path(root, name)
        write(path, assemble_pdf(objs, b"/Root 1 0 R"))
        manifest.register(
            name, path,
            f"One page with a malformed image XObject: {note} (SEC-14)",
            pages=1, image_stream_bytes=len(data),
            image_rect_pdf_points="72 300 to 372 600",
            expected="render reaches idle, blank region, baseline stable",
            baseline="yes, fullscreen page one at fit width",
        )


def _unsupported_crypt(root):
    """An /Encrypt naming a handler nothing implements.

    pdfium returns FPDF_ERR_SECURITY rather than FPDF_ERR_PASSWORD for this, and
    SEC-17 pins that the password dialog is not shown.
    """
    doc_id = b"<4D4A50444620534543555249545920464958>"
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R")),
        (4, stream_obj(b"", text_stream([
            "This document declares a security handler that does not exist.",
        ]))),
        (5, HELVETICA),
        (6, b"<< /Filter /MJTestCrypt /SubFilter /MJTest.v1 /V 4 /R 4"
            b" /Length 128 /P -4 /StmF /StdCF /StrF /StdCF"
            b" /CF << /StdCF << /CFM /MJTestAES /Length 16 >> >> >>"),
    ]
    trailer = b"/Root 1 0 R /Encrypt 6 0 R /ID [" + doc_id + b" " + doc_id + b"]"
    path = out_path(root, "unsupported-crypt.pdf")
    write(path, assemble_pdf(objs, trailer))
    manifest.register(
        "unsupported-crypt.pdf", path,
        "Trailer declaring /Encrypt with /Filter /MJTestCrypt, a security "
        "handler no implementation has (SEC-17)",
        pages=1, filter_name="/MJTestCrypt", crypt_method="/MJTestAES",
        expected="file error shown, password dialog not shown",
    ).tag("malformed")


SIZE_FIXTURE_BYTES = 2411725


def _size_fixture(root):
    """Exactly 2,411,725 bytes, padded after %%EOF to hit the count.

    LOC-34 asserts the formatted size, so the byte count is the whole point. The
    image is pseudo random so it does not deflate away, and the remainder is a
    trailing comment, which every parser ignores.
    """
    d = Document(title="Size Fixture")
    p = d.add_page()
    w = h = 1520
    seed = 0x13579BDF
    px = bytearray()
    for _ in range(w * h):
        seed = (1103515245 * seed + 12345) & 0x7FFFFFFF
        px.append((seed >> 7) & 0xFF)
    p.image(36, 36, p.width - 72, p.height - 72, gray=(w, h, bytes(px)))
    path = out_path(root, "size-fixture.pdf")
    d.save(path, damage=_pad_to_size)
    with open(path, "rb") as fh:
        actual = len(fh.read())
    if actual != SIZE_FIXTURE_BYTES:
        raise ValueError(f"size-fixture.pdf is {actual} bytes, not "
                         f"{SIZE_FIXTURE_BYTES}")
    manifest.register(
        "size-fixture.pdf", path,
        "Exactly 2,411,725 bytes, so the formatted size has two non zero "
        "decimal places (LOC-34)",
        pages=1, bytes=actual,
        padding="a trailing comment after %%EOF, ignored by every parser",
    )


def _pad_to_size(data):
    pad = SIZE_FIXTURE_BYTES - len(data)
    if pad < 3:
        raise ValueError(f"size-fixture.pdf body is {len(data)} bytes, which "
                         f"leaves no room to pad to {SIZE_FIXTURE_BYTES}")
    return data + b"%" + b"P" * (pad - 2) + b"\n"
