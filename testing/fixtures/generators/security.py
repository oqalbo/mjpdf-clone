"""Active content, hostile links and the provider fixtures.

Every file here is a legitimate PDF construct the app is expected to ignore: an
action it must not perform, a link it must not follow, a stream it must not
expand without bound. None contains an exploit payload, and the script bodies
are inert by construction because nothing in the app or in the vendored pdfium
has a JavaScript engine to run them. That is the property the tests prove.

Two things a reader should know before changing a fixture here.

The bomb fixtures cannot be 20 KB. Deflate's maximum ratio is 1032 to 1, so a
4 GB expansion needs about 4 MB of compressed data in a single Flate layer. They
are built in chunks with one `zlib.compressobj`, the expanded buffer is never
materialised, and one payload serves both files because 65535 by 65535 grey
samples is the same length as the content stream. They are tagged `malformed` so
the `--check` pass never opens them: nothing in a build may render these.

The two fixtures that point at the local server carry the literal port
placeholder `00000`, five digits wide. The runner patches it to the MockWebServer
port at push time, which is a same length substitution so no offset moves.
"""

import zlib

from generators.malformed import (HELVETICA, assemble_pdf, page_obj,
                                  stream_obj, text_stream, write)
from lib import manifest
from lib.doc import Document
from lib.pdfwriter import Name, Ref
from lib.registry import fixture_group, out_path

PORT_PLACEHOLDER = "00000"
PATCH_NOTE = ("the runner replaces the literal 00000 with the zero padded "
              "MockWebServer port, which is the same width so no offset moves")

# 65535 by 65535 grey samples at 8 bits, which is also the content stream length.
BOMB_EXPANDED = 65535 * 65535

BODY = (
    "This document is a security fixture. It carries a construct the reader is "
    "expected to ignore, and it is safe to open. The page exists so the "
    "document is a real document rather than a bare structure. "
)


@fixture_group("security", "Active content, hostile links and provider fixtures",
               slow=True)
def build(root):
    # Cleared per build so --verify recompresses rather than comparing a cache
    # against itself. Within one build the payload is computed once and used by
    # both bomb fixtures.
    _BOMB_PAYLOAD.clear()
    _bombs(root)
    _openaction_js(root)
    _field_js(root)
    _openaction_uri(root)
    _attachment(root)
    _launch_action(root)
    _submitform(root)
    _link_schemes(root)
    _link_destinations(root)
    _hostile_name(root)
    _huge_text(root)


def _page(d, heading, body=BODY):
    p = d.add_page()
    p.text(72, p.height - 60, heading, font="serif-bold", size=13)
    d.paragraph(p, 72, p.height - 90, body, size=11, max_width=p.width - 144)
    return p


def _rect_facts(rect):
    return [round(v, 2) for v in rect]


_BOMB_PAYLOAD = []


def _bomb_payload():
    """Deflate BOMB_EXPANDED repeated spaces without ever holding them.

    A space is the choice of byte because the same payload has to be a valid
    content stream in one fixture and image samples in the other: 4 GB of
    whitespace draws nothing at all.
    """
    if _BOMB_PAYLOAD:
        return _BOMB_PAYLOAD[0]
    c = zlib.compressobj(9)
    chunk = b" " * (1 << 22)
    parts = []
    written = 0
    while written < BOMB_EXPANDED:
        take = min(len(chunk), BOMB_EXPANDED - written)
        parts.append(c.compress(chunk[:take]))
        written += take
    parts.append(c.flush())
    _BOMB_PAYLOAD.append(b"".join(parts))
    return _BOMB_PAYLOAD[0]


def _bombs(root):
    payload = _bomb_payload()

    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R", resources=b"<< >>")),
        (4, stream_obj(b"/Filter /FlateDecode", payload)),
    ]
    path = out_path(root, "bomb.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "bomb.pdf", path,
        "One page whose content stream is a single FlateDecode layer expanding "
        "to 4,294,836,225 bytes of whitespace. It expands only when the page is "
        "rendered, which is the point (SEC-06)",
        pages=1, compressed_bytes=len(payload), expanded_bytes=BOMB_EXPANDED,
        expansion_ratio=round(BOMB_EXPANDED / len(payload)),
        expanded_content="spaces, so the stream draws nothing when it is decoded",
        size_note="the plan says 20 KB. Deflate's ceiling is 1032 to 1, so a "
                  "4 GB expansion needs about 4 MB in a single Flate layer",
        ci_note="no build or CI step may open this file",
        expected="peak heap stays within 256 MB of the pre load value",
    ).tag("malformed", "slow")

    image_keys = (b"/Type /XObject /Subtype /Image /Width 65535 /Height 65535"
                  b" /ColorSpace /DeviceGray /BitsPerComponent 8"
                  b" /Filter /FlateDecode")
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R",
                     resources=b"<< /XObject << /Im0 5 0 R >> >>")),
        (4, stream_obj(b"", b"q 468 0 0 468 72 200 cm /Im0 Do Q\n")),
        (5, stream_obj(image_keys, payload)),
    ]
    path = out_path(root, "bomb-image.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "bomb-image.pdf", path,
        "One page holding a 65535 by 65535 grey FlateDecode image whose "
        "compressed data is the same payload as bomb.pdf (SEC-07)",
        pages=1, compressed_bytes=len(payload), expanded_bytes=BOMB_EXPANDED,
        image_size="65535 by 65535, DeviceGray, 8 bits per component",
        image_rect_pdf_points="72 200 to 540 668",
        ci_note="no build or CI step may open this file",
        expected="render reaches idle, peak heap within 256 MB of pre load",
    ).tag("malformed", "slow")


JS_MARKER = "mjsec19-marker"
JS_MARKER_FILE = f"/sdcard/{JS_MARKER}.txt"
JS_BEACON = f"http://127.0.0.1:{PORT_PLACEHOLDER}/beacon"

DOC_JS = (
    f"var marker = '{JS_MARKER_FILE}';\n"
    "this.exportDataObject({ cName: 'mjmarker', nLaunch: 0 });\n"
    f"app.launchURL('{JS_BEACON}', true);\n"
)


def _openaction_js(root):
    """A document that would run script on open, if anything could run script.

    The build sets pdf_enable_v8 false and the form fill environment is created
    with a zeroed m_pJsPlatform, so this is inert structurally rather than by a
    check the app makes. SEC-19 pins both halves.
    """
    d = Document(title="OpenAction JavaScript")
    _page(d, "Document level JavaScript")
    d.extra_catalog["OpenAction"] = {"S": Name("JavaScript"), "JS": DOC_JS}
    d.extra_catalog["Names"] = {
        "JavaScript": {"Names": ["MJDocLevel",
                                 {"S": Name("JavaScript"), "JS": DOC_JS}]},
    }
    path = out_path(root, "openaction-js.pdf")
    d.save(path)
    manifest.register(
        "openaction-js.pdf", path,
        "Catalog carrying /OpenAction with a /JavaScript action and a document "
        "level /Names /JavaScript name tree. The script body is inert because "
        "nothing executes it, which is what SEC-19 proves",
        pages=1, marker_file=JS_MARKER_FILE, beacon_url=JS_BEACON,
        port_placeholder=PORT_PLACEHOLDER, port_patch=PATCH_NOTE,
        name_tree_key="MJDocLevel",
        expected="page renders normally, no request, no marker file",
    )


FIELD_JS_MARKER = "mjsec21-marker"
FIELD_JS_BEACON = f"http://127.0.0.1:{PORT_PLACEHOLDER}/field"
FIELD_NAME = "jsfield"


def _field_js(root):
    """A text field with all four additional actions, plus a locked variant.

    The format action would rewrite the typed value to TAMPERED. SEC-21 asserts
    the rendered value is exactly what was typed, so a format action that ever
    started running would be visible rather than silent.
    """
    def js(kind):
        return (b"<< /Type /Action /S /JavaScript /JS (event.value = 'TAMPERED';"
                b" app.launchURL('" + FIELD_JS_BEACON.encode("ascii") + b"?"
                + kind + b"', true);) >>")

    rect = b"[200 640 460 664]"
    acroform = (b"/AcroForm << /Fields [6 0 R] /DA (/Helv 0 Tf 0 g)"
                b" /DR << /Font << /Helv 5 0 R >> >> /NeedAppearances true >>")
    names = b"/Names << /JavaScript << /Names [(MJFieldDoc) 7 0 R] >> >>"
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R " + acroform + b" " + names + b" >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R", extra=b" /Annots [6 0 R]")),
        (4, stream_obj(b"", text_stream([
            "Field level JavaScript fixture.",
            "The field below carries format, validate, calculate and keystroke",
            "additional actions. None of them may run.",
        ]))),
        (5, HELVETICA),
        (6, b"<< /Type /Annot /Subtype /Widget /FT /Tx /T (" +
            FIELD_NAME.encode("ascii") + b") /TU (JavaScript field) /V ()"
            b" /DA (/Helv 11 Tf 0 g) /Rect " + rect + b" /F 4 /P 3 0 R"
            b" /AA << /F 8 0 R /V 9 0 R /C 10 0 R /K 11 0 R >> >>"),
        (7, js(b"doc")),
        (8, js(b"format")),
        (9, js(b"validate")),
        (10, js(b"calculate")),
        (11, js(b"keystroke")),
    ]
    path = out_path(root, "field-js.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "field-js.pdf", path,
        "One page AcroForm with a single text field carrying /AA format, "
        "validate, calculate and keystroke JavaScript actions, plus a document "
        "level /Names /JavaScript tree (SEC-21)",
        pages=1, field_name=FIELD_NAME, field_type="text, empty",
        field_rect_pdf_points=[200, 640, 460, 664],
        would_rewrite_value_to="TAMPERED",
        beacon_url=FIELD_JS_BEACON, port_placeholder=PORT_PLACEHOLDER,
        port_patch=PATCH_NOTE,
        expected="typed value stays exactly what was typed, no request",
        baseline="yes, fullscreen page one after the field is filled",
    )

    # Ff bit 1 is read only, and annotation flag bit 2 is hidden. isEditableFormField
    # filters both natively, so neither tap may reach the form field dialog.
    ro_rect = b"[200 640 460 664]"
    hidden_rect = b"[200 580 460 604]"
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [6 0 R 7 0 R]"
            b" /DA (/Helv 0 Tf 0 g) /DR << /Font << /Helv 5 0 R >> >>"
            b" /NeedAppearances true >> >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R", extra=b" /Annots [6 0 R 7 0 R]")),
        (4, stream_obj(b"", text_stream([
            "Read only and hidden field fixture.",
            "The first field is read only. The second is hidden.",
        ]))),
        (5, HELVETICA),
        (6, b"<< /Type /Annot /Subtype /Widget /FT /Tx /T (readonlyfield)"
            b" /V (locked value) /Ff 1 /DA (/Helv 11 Tf 0 g) /Rect " + ro_rect
            + b" /F 4 /P 3 0 R >>"),
        (7, b"<< /Type /Annot /Subtype /Widget /FT /Tx /T (hiddenfield)"
            b" /V (hidden value) /DA (/Helv 11 Tf 0 g) /Rect " + hidden_rect
            + b" /F 2 /P 3 0 R >>"),
    ]
    path = out_path(root, "field-js-readonly.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "field-js-readonly.pdf", path,
        "One page with a read only text field (/Ff 1) and a hidden text field "
        "(annotation flag 2), both carrying a value (SEC-22)",
        pages=1,
        readonly_field="readonlyfield, value 'locked value'",
        readonly_rect_pdf_points=[200, 640, 460, 664],
        hidden_field="hiddenfield, value 'hidden value'",
        hidden_rect_pdf_points=[200, 580, 460, 604],
        expected="no form field dialog for either tap, no journal file",
    )


def _openaction_uri(root):
    d = Document(title="OpenAction URI")
    _page(d, "OpenAction URI")
    d.extra_catalog["OpenAction"] = {"S": Name("URI"), "URI": JS_BEACON}
    path = out_path(root, "openaction-uri.pdf")
    d.save(path)
    manifest.register(
        "openaction-uri.pdf", path,
        "Catalog whose /OpenAction is a /URI action pointing at the local "
        "server. Nothing in the app or the vendored library reads /OpenAction, "
        "and SEC-23 pins that",
        pages=1, open_action_uri=JS_BEACON,
        port_placeholder=PORT_PLACEHOLDER, port_patch=PATCH_NOTE,
        expected="no request, foreground stays in the app package",
    )


ATTACHMENT_MARKER = "mjsec24-payload"
ATTACHMENT_BODY = (f"MJ PDF fixture attachment. Marker: {ATTACHMENT_MARKER}\n"
                   "If this text ever appears in a file on the device, the "
                   "attachment escaped.\n")


def _attachment(root):
    payload = ATTACHMENT_BODY.encode("ascii")
    annot_rect = b"[80 600 104 624]"
    names = (b"/Names << /EmbeddedFiles << /Names [(payload.txt) 7 0 R] >> >>")
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R " + names + b" >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R", extra=b" /Annots [8 0 R]")),
        (4, stream_obj(b"", text_stream([
            "This document carries an embedded file attachment.",
            "The attachment must stay inside it.",
        ]))),
        (5, HELVETICA),
        (6, stream_obj(b"/Type /EmbeddedFile /Subtype /text#2Fplain"
                       b" /Params << /Size " + str(len(payload)).encode("ascii")
                       + b" >>", payload)),
        (7, b"<< /Type /Filespec /F (payload.txt) /UF (payload.txt)"
            b" /EF << /F 6 0 R >> /Desc (fixture attachment) >>"),
        (8, b"<< /Type /Annot /Subtype /FileAttachment /Rect " + annot_rect
            + b" /FS 7 0 R /Name /PushPin /Contents (fixture attachment)"
            b" /F 4 /P 3 0 R >>"),
    ]
    path = out_path(root, "attachment.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "attachment.pdf", path,
        "One page with an /EmbeddedFiles name tree holding one text file, plus "
        "a /FileAttachment annotation over it (SEC-24)",
        pages=1, attachment_name="payload.txt",
        attachment_bytes=len(payload), attachment_marker=ATTACHMENT_MARKER,
        annotation_rect_pdf_points=[80, 600, 104, 624],
        expected="no menu item mentions attachments, nothing is extracted",
    )


LAUNCH_TARGETS = (
    ("launch-action.pdf", "/system/bin/sh", "a shell binary"),
    ("launch-action-lib.pdf",
     "/data/data/com.gitlab.mudlej.MjPdfReader/lib/libjniPdfium.so",
     "the app's own native library"),
)


def _launch_action(root):
    """A link whose action is /Launch rather than /URI.

    FPDFAction_GetURIPath returns nothing for a non URI action, so both link
    handlers see an empty string and treat it as absent. The rectangle still
    swallows the tap, which is why SEC-25 asserts the page did not change.
    """
    for name, target, note in LAUNCH_TARGETS:
        d = Document(title="Launch Action")
        p = _page(d, "Launch action")
        label = "Tap here to run a program, which must do nothing."
        rect = d.line_rect(p, 72, p.height - 200, label)
        p.text(72, p.height - 200, label)
        p.raw_annot({
            "Type": Name("Annot"), "Subtype": Name("Link"),
            "Rect": list(rect), "Border": [0, 0, 0],
            "A": {"S": Name("Launch"), "F": target},
        })
        path = out_path(root, name)
        d.save(path)
        manifest.register(
            name, path,
            f"One page with a link annotation whose action is /Launch at "
            f"{note} (SEC-25)",
            pages=1, launch_target=target,
            link_rect_pdf_points=_rect_facts(rect),
            expected="no process spawned, page unchanged, not listed on Links",
        )


SUBMIT_URL = f"http://127.0.0.1:{PORT_PLACEHOLDER}/collect"


def _submitform(root):
    text_rect = b"[200 640 460 664]"
    button_rect = b"[200 580 300 610]"
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [6 0 R 7 0 R]"
            b" /DA (/Helv 0 Tf 0 g) /DR << /Font << /Helv 5 0 R >> >>"
            b" /NeedAppearances true >> >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R", extra=b" /Annots [6 0 R 7 0 R]")),
        (4, stream_obj(b"", text_stream([
            "Submit form fixture. Type into the field, then press the button.",
            "Nothing may leave the device.",
        ]))),
        (5, HELVETICA),
        (6, b"<< /Type /Annot /Subtype /Widget /FT /Tx /T (payload)"
            b" /TU (Payload) /V () /DA (/Helv 11 Tf 0 g) /Rect " + text_rect
            + b" /F 4 /P 3 0 R >>"),
        (7, b"<< /Type /Annot /Subtype /Widget /FT /Btn /Ff 65536 /T (submit)"
            b" /Rect " + button_rect + b" /F 4 /P 3 0 R"
            b" /MK << /CA (Submit) /BC [0 0 0] /BG [0.9 0.9 0.9] >>"
            b" /A << /S /SubmitForm /F (" + SUBMIT_URL.encode("ascii")
            + b") /Flags 4 >> >>"),
    ]
    path = out_path(root, "submitform.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "submitform.pdf", path,
        "One page AcroForm with a text field and a push button whose action is "
        "/SubmitForm at the local server (SEC-26)",
        pages=1, text_field="payload",
        text_field_rect_pdf_points=[200, 640, 460, 664],
        button_field="submit",
        button_rect_pdf_points=[200, 580, 300, 610],
        submit_url=SUBMIT_URL, submit_flags=4,
        port_placeholder=PORT_PLACEHOLDER, port_patch=PATCH_NOTE,
        expected="no request, no traffic, marker string absent from logcat",
    )


LONG_URI_LENGTH = 65536
CONTROL_HOST = "https://example.invalid"
# Escapes rather than literal control characters, so the source stays printable
# and the writer emits them as \r \n and \000 inside the PDF string.
CONTROL_URI = CONTROL_HOST + "\r\n\x00" + "/smuggled"


def _long_uri():
    prefix = "https://example.invalid/long?q="
    return prefix + "a" * (LONG_URI_LENGTH - len(prefix))


LINK_SCHEMES = (
    ("file", "file:///data/data/com.gitlab.mudlej.MjPdfReader/databases/app-db.db",
     "the app's own Room database (SEC-28)"),
    ("content storage",
     "content://com.android.externalstorage.documents/document/primary%3A",
     "another app's documents provider (SEC-29)"),
    ("content sms", "content://sms/inbox", "the SMS provider (SEC-29)"),
    ("intent", "intent://scan/#Intent;scheme=zxing;end",
     "an intent URI, which must be parsed with Uri.parse and never "
     "Intent.parseUri (SEC-30)"),
    ("javascript", "javascript:1", "a script URI with no WebView to reach (SEC-31)"),
    ("unhandled scheme", "market://details?id=mjsec60token",
     "a scheme no package on the emulator handles, carrying a distinctive "
     "token for the logcat assertion (SEC-35, SEC-60)"),
    ("tel", "tel:+10000000000", "a dialer URI"),
    ("long", _long_uri(), "an https URI of 65,536 characters (SEC-32)"),
    ("control characters", CONTROL_URI,
     "carriage return, newline and a null byte between the host and the path "
     "(SEC-33)"),
    ("control", "https://example.invalid/ok",
     "the ordinary https control, which must behave like any link"),
)


def _link_schemes(root):
    """Ten pages, one link annotation per page, one scheme each.

    SEC-35 asserts the Links screen shows exactly ten rows, so the count is the
    fixture's contract and an eleventh link would break it.
    """
    if len(LINK_SCHEMES) != 10:
        raise ValueError(f"link-schemes.pdf needs 10 links, has "
                         f"{len(LINK_SCHEMES)}")
    d = Document(title="Link Schemes")
    rows = []
    for i, (kind, uri, note) in enumerate(LINK_SCHEMES):
        p = _page(d, f"Link scheme {i + 1}: {kind}")
        label = f"Tap this line to follow the {kind} link."
        rect = d.line_rect(p, 72, p.height - 200, label)
        p.text(72, p.height - 200, label)
        p.link_uri(rect, uri)
        rows.append({"page_index": i, "kind": kind,
                     "uri": uri if len(uri) <= 120 else
                     f"{uri[:60]}... ({len(uri)} characters)",
                     "rect_pdf_points": _rect_facts(rect), "note": note})
    path = out_path(root, "link-schemes.pdf")
    d.save(path)
    manifest.register(
        "link-schemes.pdf", path,
        "10 pages, one /URI link annotation per page, one hostile or control "
        "scheme each. Page index equals the row order below (SEC-28 to SEC-35)",
        pages=len(d.pages), link_annotations=len(LINK_SCHEMES), links=rows,
        long_uri_characters=LONG_URI_LENGTH, long_uri_page_index=7,
        control_character_uri="host, then CR LF NUL, then /smuggled, on page "
                              "index 8",
        control_character_host="example.invalid",
        unhandled_scheme_token="mjsec60token",
        expected="ten rows on the Links screen, no grant flags on any intent",
    )


def _link_destinations(root):
    """Three internal destinations that are all outside the page range."""
    d = Document(title="Link Destinations")
    targets = (
        (-1, "a negative page index"),
        (2147483647, "one past the end, at Integer.MAX_VALUE"),
        (None, "a page object that is not in the page tree"),
    )
    rows = []
    for i, (target, note) in enumerate(targets):
        p = _page(d, f"Destination {i + 1}")
        label = f"Tap this line to jump to {note}."
        rect = d.line_rect(p, 72, p.height - 200, label)
        p.text(72, p.height - 200, label)
        # Ref(1) is the catalog: the writer reserves it first, so an off tree
        # destination is a reference to a known object that is not a page.
        dest = Ref(1) if target is None else target
        p.raw_annot({
            "Type": Name("Annot"), "Subtype": Name("Link"),
            "Rect": list(rect), "Border": [0, 0, 0],
            "A": {"S": Name("GoTo"),
                  "D": [dest, Name("XYZ"), None, None, None]},
        })
        rows.append({"page_index": i,
                     "destination": "catalog object, off tree" if target is None
                                    else target,
                     "rect_pdf_points": _rect_facts(rect), "note": note})
    path = out_path(root, "link-destinations.pdf")
    d.save(path)
    manifest.register(
        "link-destinations.pdf", path,
        "3 pages, one internal /GoTo link each, all three destinations outside "
        "the page range (SEC-34)",
        pages=len(d.pages), link_annotations=len(targets), links=rows,
        expected="page 1 after the negative tap, the last page after the "
                 "MAX_VALUE tap, and a page within range after the off tree tap",
        clamped_by="PdfFile.determineValidPageNumberFrom in the vendored library",
    )


def _hostile_name(root):
    """An ordinary document. The hostility is the name it is served under."""
    d = Document(title="Ordinary Document", author="A. Writer")
    for i in range(3):
        _page(d, f"Page {i + 1}")
    path = out_path(root, "hostile-name.pdf")
    d.save(path)
    manifest.register(
        "hostile-name.pdf", path,
        "A perfectly ordinary 3 page document. Nothing about the bytes is "
        "hostile: the stub content provider in the test APK reports an attacker "
        "chosen OpenableColumns.DISPLAY_NAME for it, and that is the fixture "
        "(SEC-40 to SEC-43, SEC-45, PRIV-03)",
        pages=3,
        served_by="the stub content provider on authority mjpdf.test.sharer",
        display_names_used_by_tests=[
            "../../../../data/data/com.gitlab.mudlej.MjPdfReader/databases/app-db.pdf",
            "..%2F..%2Fescape.pdf",
            "SMB%2F..%2F..%2Fescape.pdf",
            "../../../../sdcard/escape.pdf",
            "../../databases/app-db.pdf",
            "4000 characters",
            "a name containing a null byte and U+202E",
            "a blank name",
            "a name that is only dots",
        ],
        note="the names are provider configuration, not files. Nothing here "
             "needs to be created on disk",
    )


HUGE_TEXT_LINES = 50000
HUGE_TEXT_LINE_CHARS = 1000


def _huge_text(root):
    """One page drawing fifty million characters.

    nativeGetPageText allocates two bytes per character with only a null check,
    so SEC-16 is about a cap that does not exist yet. The per line counter keeps
    the stream from deflating to nothing, which would make the fixture
    unrepresentative of a real page.
    """
    filler = ("the quick brown fox jumps over the lazy dog and then reads a "
              "page of ordinary prose without stopping for breath at all ")
    base = (filler * 20)[:HUGE_TEXT_LINE_CHARS - 8]
    ops = ["BT /F1 6 Tf 1 0 0 1 10 780 Tm"]
    drawn = 0
    for i in range(HUGE_TEXT_LINES):
        line = f"{i:07d} {base}"
        drawn += len(line)
        ops.append(f"({line}) Tj 0 -0.015 Td")
    ops.append("ET")
    if drawn != HUGE_TEXT_LINES * HUGE_TEXT_LINE_CHARS:
        raise ValueError(f"huge-text.pdf drew {drawn} characters, not "
                         f"{HUGE_TEXT_LINES * HUGE_TEXT_LINE_CHARS}")
    body = zlib.compress("\n".join(ops).encode("ascii"), 9)
    objs = [
        (1, b"<< /Type /Catalog /Pages 2 0 R >>"),
        (2, b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
        (3, page_obj(b"2 0 R", b"4 0 R")),
        (4, stream_obj(b"/Filter /FlateDecode", body)),
        (5, HELVETICA),
    ]
    path = out_path(root, "huge-text.pdf")
    write(path, assemble_pdf(objs, b"/Root 1 0 R"))
    manifest.register(
        "huge-text.pdf", path,
        "One page whose content stream draws 50,000,000 characters with Tj "
        "operators against the standard Helvetica font (SEC-16)",
        pages=1, characters_drawn=drawn,
        lines=HUGE_TEXT_LINES, characters_per_line=HUGE_TEXT_LINE_CHARS,
        content_stream_compressed_bytes=len(body),
        expected="page text dialog or an error within 20 seconds, peak heap "
                 "within 512 MB of the pre load value",
    ).tag("slow")
