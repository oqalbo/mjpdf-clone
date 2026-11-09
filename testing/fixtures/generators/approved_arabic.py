"""Approved source text shaped by native Pango/HarfBuzz, not lib/arabic.py.

Only manifest/registry are shared with the other fixture generators. Cairo
replays each scene to the combined PDF, a single-page PDF and a PNG. Source
expectations are not extraction goldens; previews do not rasterize the PDF.
"""

import ctypes as C
from ctypes.util import find_library
from contextlib import ExitStack, contextmanager
import hashlib
import json
from pathlib import Path
from types import SimpleNamespace

from lib import manifest
from lib.registry import fixture_group


SENTENCES = (
    "قَرَأَ الطَّالِبُ الْكِتَابَ فِي الْمَكْتَبَةِ.",
    "إِنَّ اللُّغَةَ الْعَرَبِيَّةَ جَمِيلَةٌ.",
    "لَا تُؤَجِّلْ عَمَلَ الْيَوْمِ إِلَى الْغَدِ.",
    "سَأَلَ الطِّفْلُ: «أَيْنَ أُمِّي؟»",
    "افتح الملف report.pdf، ثم انتقل إلى الصفحة ١٢.",
)
PDF_NAME = "arabic-approved-sentences.pdf"
EXPECTED_NAME = "arabic-approved-sentences.expected.json"
FONT = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
FONT_SHA256 = "abdc775b21b1bc470d50c97e790d276f2054b7504e56e5bd3e64f48d68582322"
DATE = "2024-01-01T00:00:00Z"
WIDTH, HEIGHT, MARGIN, BASELINE, FONT_SIZE = 612, 400, 36, 120, 24
SCALE, PREVIEW_SCALE = 1024, 2


class Rectangle(C.Structure):
    _fields_ = [(name, C.c_int) for name in ("x", "y", "width", "height")]


class CairoRectangle(C.Structure):
    _fields_ = [(name, C.c_double) for name in ("x", "y", "width", "height")]


class Native(SimpleNamespace):
    """Explicit public C ABI signatures; no Python graphics packages required."""

    def __init__(self):
        p, i, d, s = C.c_void_p, C.c_int, C.c_double, C.c_char_p
        r, ip = C.POINTER(Rectangle), C.POINTER(i)
        signatures = {
            "cairo": [
                ("cairo_version_string", s, []),
                ("cairo_pdf_surface_create", p, [s, d, d]),
                ("cairo_pdf_surface_set_metadata", None, [p, i, s]),
                ("cairo_recording_surface_create", p, [i, C.POINTER(CairoRectangle)]),
                ("cairo_image_surface_create", p, [i, i, i]),
                ("cairo_surface_write_to_png", i, [p, s]),
                ("cairo_surface_finish", None, [p]),
                ("cairo_surface_destroy", None, [p]),
                ("cairo_surface_status", i, [p]),
                ("cairo_create", p, [p]),
                ("cairo_destroy", None, [p]),
                ("cairo_status", i, [p]),
                ("cairo_status_to_string", s, [i]),
                ("cairo_set_source_rgb", None, [p, d, d, d]),
                ("cairo_set_source_surface", None, [p, p, d, d]),
                ("cairo_paint", None, [p]),
                ("cairo_move_to", None, [p, d, d]),
                ("cairo_scale", None, [p, d, d]),
                ("cairo_show_page", None, [p]),
                ("cairo_font_options_create", p, []),
                ("cairo_font_options_destroy", None, [p]),
                ("cairo_font_options_set_antialias", None, [p, i]),
                ("cairo_font_options_set_hint_style", None, [p, i]),
                ("cairo_font_options_set_hint_metrics", None, [p, i]),
                ("cairo_set_font_options", None, [p, p]),
            ],
            "pangocairo-1.0": [
                ("pango_cairo_font_map_new_for_font_type", p, [i]),
                ("pango_cairo_context_set_resolution", None, [p, d]),
                ("pango_cairo_context_set_font_options", None, [p, p]),
                ("pango_cairo_show_layout", None, [p, p]),
            ],
            "pangoft2-1.0": [
                ("pango_fc_font_map_set_config", None, [p, p]),
            ],
            "pango-1.0": [
                ("pango_version_string", s, []),
                ("pango_font_map_create_context", p, [p]),
                ("pango_context_set_base_dir", None, [p, i]),
                ("pango_context_set_language", None, [p, p]),
                ("pango_context_set_round_glyph_positions", None, [p, i]),
                ("pango_language_from_string", p, [s]),
                ("pango_font_description_from_string", p, [s]),
                ("pango_font_description_set_absolute_size", None, [p, d]),
                ("pango_font_description_free", None, [p]),
                ("pango_attr_list_new", p, []),
                ("pango_attr_fallback_new", p, [i]),
                ("pango_attr_list_insert", None, [p, p]),
                ("pango_attr_list_unref", None, [p]),
                ("pango_layout_new", p, [p]),
                ("pango_layout_set_font_description", None, [p, p]),
                ("pango_layout_set_attributes", None, [p, p]),
                ("pango_layout_set_auto_dir", None, [p, i]),
                ("pango_layout_set_text", None, [p, s, i]),
                ("pango_layout_get_line_count", i, [p]),
                ("pango_layout_get_unknown_glyphs_count", i, [p]),
                ("pango_layout_get_baseline", i, [p]),
                ("pango_layout_get_extents", None, [p, r, r]),
            ],
            "fontconfig": [
                ("FcGetVersion", i, []),
                ("FcConfigCreate", p, []),
                ("FcConfigAppFontAddFile", i, [p, s]),
                ("FcConfigBuildFonts", i, [p]),
                ("FcConfigDestroy", None, [p]),
            ],
            "gobject-2.0": [("g_object_unref", None, [p])],
            "glib-2.0": [],
            "harfbuzz": [("hb_version_string", s, [])],
            "freetype": [
                ("FT_Init_FreeType", i, [C.POINTER(p)]),
                ("FT_Library_Version", None, [p, ip, ip, ip]),
                ("FT_Done_FreeType", i, [p]),
            ],
            "fribidi": [],
            "pixman-1": [("pixman_version_string", s, [])],
            "z": [("zlibVersion", s, [])],
        }
        self.libraries = {}
        for name, functions in signatures.items():
            soname = find_library(name)
            if not soname:
                raise RuntimeError(f"approved_arabic requires native library {name}")
            library = self.libraries[name] = C.CDLL(soname)
            for name, result, args in functions:
                fn = getattr(library, name)
                fn.restype, fn.argtypes = result, args
                setattr(self, name, fn)

    def versions(self):
        ft = C.c_void_p()
        if self.FT_Init_FreeType(C.byref(ft)):
            raise RuntimeError("FreeType initialization failed")
        major, minor, patch = C.c_int(), C.c_int(), C.c_int()
        try:
            self.FT_Library_Version(ft, C.byref(major), C.byref(minor), C.byref(patch))
        finally:
            self.FT_Done_FreeType(ft)
        fc = self.FcGetVersion()
        fribidi = C.c_char_p.in_dll(self.libraries["fribidi"], "fribidi_version_info").value
        if not fribidi:
            raise RuntimeError("FriBidi did not report its version")
        return {
            "pango": self.pango_version_string().decode(),
            "harfbuzz": self.hb_version_string().decode(),
            "cairo": self.cairo_version_string().decode(),
            "freetype": f"{major.value}.{minor.value}.{patch.value}",
            "fontconfig": f"{fc // 10000}.{fc // 100 % 100}.{fc % 100}",
            "glib": ".".join(str(C.c_uint.in_dll(self.libraries["glib-2.0"],
                              f"glib_{part}_version").value)
                             for part in ("major", "minor", "micro")),
            "fribidi": fribidi.decode().splitlines()[0],
            "pixman": self.pixman_version_string().decode(),
            "zlib": self.zlibVersion().decode(),
        }

    def check(self, status):
        if status:
            raise RuntimeError(self.cairo_status_to_string(status).decode())

    @contextmanager
    def pdf(self, path):
        with ExitStack() as resources:
            surface = self.cairo_pdf_surface_create(str(path).encode(), WIDTH, HEIGHT)
            resources.callback(self.cairo_surface_destroy, surface)
            self.check(self.cairo_surface_status(surface))
            self.cairo_pdf_surface_set_metadata(surface, 4, b"approved_arabic: Pango/HarfBuzz/Cairo")
            self.cairo_pdf_surface_set_metadata(surface, 5, DATE.encode())  # CREATE_DATE
            self.cairo_pdf_surface_set_metadata(surface, 6, DATE.encode())  # MOD_DATE
            context = self.cairo_create(surface)
            resources.callback(self.cairo_destroy, context)
            yield context
            self.check(self.cairo_status(context))
            self.cairo_surface_finish(surface)
            self.check(self.cairo_surface_status(surface))

    def replay(self, context, scene):
        self.cairo_set_source_surface(context, scene, 0, 0)
        self.cairo_paint(context)
        self.check(self.cairo_status(context))


def _rect(rect, x, y):
    left, top = x + rect.x / SCALE, y + rect.y / SCALE
    right, bottom = left + rect.width / SCALE, top + rect.height / SCALE
    return [min(left, right), min(top, bottom), max(left, right), max(top, bottom)]


@fixture_group("approved_arabic", "Five approved Arabic sentences, native Pango/HarfBuzz/Cairo")
def build(root):
    root = Path(root).resolve()
    previews = root.with_name(root.name + "-previews") / "approved_arabic"
    if hashlib.sha256(FONT.read_bytes()).hexdigest() != FONT_SHA256:
        raise RuntimeError(f"{FONT} does not match the approved SHA-256 {FONT_SHA256}")
    n = Native()
    provenance = {
        "generator": "testing/fixtures/generators/approved_arabic.py",
        "pipeline": "UTF-8 source -> Pango/HarfBuzz/FriBidi -> Cairo scene -> combined PDF, single-page PDF, PNG",
        "native_versions": n.versions(),
        "font": {"file": str(FONT), "sha256": FONT_SHA256, "family": "DejaVu Sans",
                 "face": "Book", "fallback": False, "fontconfig": "isolated single-font config"},
        "created": DATE, "modified": DATE,
        "preview": {
            "method": "Cairo image surface replay of the same recording used for each PDF page",
            "not_a_pdf_rasterization": True,
            "directory": "<out>-previews/approved_arabic (outside device fixture output)",
            "scale_pixels_per_point": PREVIEW_SCALE, "dpi": 72 * PREVIEW_SCALE,
            "size_pixels": [WIDTH * PREVIEW_SCALE, HEIGHT * PREVIEW_SCALE],
        },
        "layout": {"font_size_points": FONT_SIZE, "resolution_dpi": 72,
                   "base_direction": "rtl", "language": "ar", "auto_direction": False,
                   "alignment": "right", "margin_points": MARGIN,
                   "baseline_from_top_points": BASELINE, "wrap": False,
                   "antialias": "gray", "hint_style": "none", "hint_metrics": "off",
                   "round_glyph_positions": False},
    }
    root.mkdir(parents=True, exist_ok=True)
    previews.mkdir(parents=True, exist_ok=True)
    pages = []
    with ExitStack() as resources:
        config = n.FcConfigCreate()
        resources.callback(n.FcConfigDestroy, config)
        if not n.FcConfigAppFontAddFile(config, str(FONT).encode()) or not n.FcConfigBuildFonts(config):
            raise RuntimeError("Could not configure the pinned font")
        font_map = n.pango_cairo_font_map_new_for_font_type(1)  # CAIRO_FONT_TYPE_FT
        if not font_map:
            raise RuntimeError("PangoCairo has no FreeType font backend")
        resources.callback(n.g_object_unref, font_map)
        n.pango_fc_font_map_set_config(font_map, config)
        context = n.pango_font_map_create_context(font_map)
        resources.callback(n.g_object_unref, context)
        n.pango_cairo_context_set_resolution(context, 72)
        n.pango_context_set_base_dir(context, 1)  # PANGO_DIRECTION_RTL
        n.pango_context_set_language(context, n.pango_language_from_string(b"ar"))
        n.pango_context_set_round_glyph_positions(context, False)
        options = n.cairo_font_options_create()
        resources.callback(n.cairo_font_options_destroy, options)
        n.cairo_font_options_set_antialias(options, 2)  # GRAY
        n.cairo_font_options_set_hint_style(options, 1)  # NONE
        n.cairo_font_options_set_hint_metrics(options, 1)  # OFF
        n.pango_cairo_context_set_font_options(context, options)
        description = n.pango_font_description_from_string(b"DejaVu Sans Book")
        resources.callback(n.pango_font_description_free, description)
        n.pango_font_description_set_absolute_size(description, FONT_SIZE * SCALE)
        attributes = n.pango_attr_list_new()
        resources.callback(n.pango_attr_list_unref, attributes)
        n.pango_attr_list_insert(attributes, n.pango_attr_fallback_new(False))

        pdf_context = resources.enter_context(n.pdf(root / PDF_NAME))
        for page_number, text in enumerate(SENTENCES, 1):
            with ExitStack() as page_resources:
                layout = n.pango_layout_new(context)
                page_resources.callback(n.g_object_unref, layout)
                n.pango_layout_set_font_description(layout, description)
                n.pango_layout_set_attributes(layout, attributes)
                n.pango_layout_set_auto_dir(layout, False)
                encoded = text.encode("utf-8")
                n.pango_layout_set_text(layout, encoded, len(encoded))
                if n.pango_layout_get_line_count(layout) != 1:
                    raise RuntimeError(f"Page {page_number} is not a single line")
                if n.pango_layout_get_unknown_glyphs_count(layout):
                    raise RuntimeError(f"Page {page_number} has unsupported characters")
                ink, logical = Rectangle(), Rectangle()
                n.pango_layout_get_extents(layout, C.byref(ink), C.byref(logical))
                x = WIDTH - MARGIN - max(logical.x + logical.width,
                                         ink.x + ink.width) / SCALE
                y = BASELINE - n.pango_layout_get_baseline(layout) / SCALE
                ink_rect = _rect(ink, x, y)
                for rect in (ink_rect, _rect(logical, x, y)):
                    if rect[0] < MARGIN or rect[2] > WIDTH - MARGIN or rect[1] < 0 or rect[3] > HEIGHT:
                        raise RuntimeError(f"Page {page_number} overflows its margins: {rect}")

                extent = CairoRectangle(0, 0, WIDTH, HEIGHT)
                scene = n.cairo_recording_surface_create(0x3000, C.byref(extent))
                page_resources.callback(n.cairo_surface_destroy, scene)
                cr = n.cairo_create(scene)
                page_resources.callback(n.cairo_destroy, cr)
                n.cairo_set_font_options(cr, options)
                n.cairo_set_source_rgb(cr, 1, 1, 1)
                n.cairo_paint(cr)
                n.cairo_set_source_rgb(cr, 0, 0, 0)
                n.cairo_move_to(cr, x, y)
                n.pango_cairo_show_layout(cr, layout)
                n.check(n.cairo_status(cr))
                n.replay(pdf_context, scene)
                n.cairo_show_page(pdf_context)

                single_name = f"arabic-approved-sentence-{page_number}.pdf"
                with n.pdf(root / single_name) as single_context:
                    n.replay(single_context, scene)
                    n.cairo_show_page(single_context)

                image = n.cairo_image_surface_create(0, WIDTH * PREVIEW_SCALE, HEIGHT * PREVIEW_SCALE)
                page_resources.callback(n.cairo_surface_destroy, image)
                image_context = n.cairo_create(image)
                page_resources.callback(n.cairo_destroy, image_context)
                n.cairo_scale(image_context, PREVIEW_SCALE, PREVIEW_SCALE)
                n.replay(image_context, scene)
                preview_name = f"arabic-approved-sentence-{page_number}.png"
                n.check(n.cairo_surface_write_to_png(image, str(previews / preview_name).encode()))
                pages.append({
                    "page": page_number, "text": text,
                    "utf8_sha256": hashlib.sha256(encoded).hexdigest(),
                    "ink_rect": ink_rect,
                    "single_page_pdf": {"filename": single_name,
                                        "sha256": hashlib.sha256((root / single_name).read_bytes()).hexdigest()},
                    "preview": {"filename": preview_name,
                                "sha256": hashlib.sha256((previews / preview_name).read_bytes()).hexdigest()},
                })

    expected = {
        "schema_version": 2, "pdf": PDF_NAME,
        "pdf_sha256": hashlib.sha256((root / PDF_NAME).read_bytes()).hexdigest(),
        "page_size_points": [WIDTH, HEIGHT], "provenance": provenance,
        "coordinates": "Pango page points, origin top-left, x right, y down; ink_rect [left, top, right, bottom]",
        "pages": pages,
    }
    (root / EXPECTED_NAME).write_text(json.dumps(expected, ensure_ascii=False, indent=2,
                                                sort_keys=True) + "\n", encoding="utf-8")
    manifest.register(
        PDF_NAME, str(root / PDF_NAME),
        "Five approved Arabic source sentences, one per page; not extraction goldens",
        pages=len(pages), sentences=list(SENTENCES), page_size_points=[WIDTH, HEIGHT],
        font_size_points=FONT_SIZE, baseline_from_top_points=BASELINE,
        expected_source=EXPECTED_NAME, provenance=provenance,
    ).tag("approved_arabic")
    for page in pages:
        name = page["single_page_pdf"]["filename"]
        manifest.register(
            name, str(root / name), "One approved Arabic sentence, same Cairo scene as the combined PDF",
            pages=1, text=page["text"], source_page=page["page"], expected_source=EXPECTED_NAME,
        ).tag("approved_arabic")
    manifest.register(
        EXPECTED_NAME, str(root / EXPECTED_NAME),
        "Exact approved Unicode source, source/file hashes, page/ink bounds and native rendering provenance",
        pages=len(pages), sentences=list(SENTENCES),
    ).tag("approved_arabic")
