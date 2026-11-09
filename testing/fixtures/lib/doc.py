"""High level document building on top of the deterministic writer.

A generator describes pages in points, adds text runs, highlights, links and
outline entries, and calls save(). Fonts are embedded and blanked automatically
from the glyphs actually drawn, so fixtures stay small while satisfying the rule
that every fixture embeds every font it uses.
"""

import hashlib
import os
import zlib

from . import ttf
from .pdfwriter import FIXED_DATE, Name, PdfWriter, Raw, Ref, Stream

FONT_DIR = "/usr/share/fonts/truetype/dejavu"
FONTS = {
    "serif": os.path.join(FONT_DIR, "DejaVuSerif.ttf"),
    "serif-bold": os.path.join(FONT_DIR, "DejaVuSerif-Bold.ttf"),
    "sans": os.path.join(FONT_DIR, "DejaVuSans.ttf"),
    "sans-bold": os.path.join(FONT_DIR, "DejaVuSans-Bold.ttf"),
    "mono": os.path.join(FONT_DIR, "DejaVuSansMono.ttf"),
}
FONT_SHA256 = {
    FONTS["serif"]: "13e61509f5c81d7c3132810f4f903e3523df89c802bf6e0674621e8f659cdfe1",
    FONTS["serif-bold"]: "e2fd85eba2de65ac270d1cdb1685e252eb827f600850cf62af2d20c41b22e945",
    FONTS["sans"]: "abdc775b21b1bc470d50c97e790d276f2054b7504e56e5bd3e64f48d68582322",
    FONTS["sans-bold"]: "0d977336a6d5fba34eab8e3199eb218327161b5143749f802982c2bc34df0c96",
    FONTS["mono"]: "0f5db4f1749979d961019838b160bec74abdf7f9eca69553fe1aa856bbff49a4",
}
_VERIFIED_FONTS = set()

LETTER = (612.0, 792.0)
A4 = (595.276, 841.89)

# MJ PDF only treats a highlight as its own if it carries these markers.
MJ_TITLE = "MJ PDF"


def _verified_font(path):
    if path not in _VERIFIED_FONTS:
        with open(path, "rb") as fh:
            actual = hashlib.sha256(fh.read()).hexdigest()
        if actual != FONT_SHA256[path]:
            raise RuntimeError(f"{path} has SHA-256 {actual}, fixtures require {FONT_SHA256[path]}")
        _VERIFIED_FONTS.add(path)
    return path


class EmbeddedFont:
    def __init__(self, key, path, index=0):
        self.key = key
        self.path = path
        self.face = ttf.load(_verified_font(path))
        self.used = set()
        # Deterministic resource name: hash() is salted per process.
        self.resource = f"F{index}"
        # gid -> codepoint overrides for ToUnicode, used when a fixture must
        # extract as a character the installed fonts have no glyph for.
        self.unicode_alias = {}

    def gid(self, ch):
        g = self.face.gid(ord(ch))
        self.used.add(g)
        return g

    def width(self, ch, size):
        g = self.face.gid(ord(ch))
        return self.face.advance_units(g) * size / self.face.units_per_em

    def text_width(self, s, size):
        return sum(self.width(c, size) for c in s)

    def encode(self, s):
        """Identity-H takes two byte glyph ids."""
        out = bytearray()
        for ch in s:
            g = self.gid(ch)
            out += bytes([(g >> 8) & 0xFF, g & 0xFF])
        return bytes(out)

    def encode_glyphs(self, glyphs):
        """Encode shaped glyphs, recording what each one extracts as."""
        out = bytearray()
        for g in glyphs:
            gid = self.face.gid(g.codepoint)
            self.used.add(gid)
            self.unicode_alias[gid] = g.text
            out += bytes([(gid >> 8) & 0xFF, gid & 0xFF])
        return bytes(out)

    def glyphs_width(self, glyphs, size):
        total = 0
        for g in glyphs:
            gid = self.face.gid(g.codepoint)
            total += self.face.advance_units(gid) * size / self.face.units_per_em
        return total


class Page:
    def __init__(self, doc, size):
        self.doc = doc
        self.width, self.height = size
        self.ops = []
        self.annots = []
        self.fonts_used = set()
        self.images = {}
        self._xobj_n = 0

    # Content -----------------------------------------------------------
    def text(self, x, y, s, font="serif", size=11.0, rgb=(0, 0, 0)):
        """Draw one line with its baseline at y, measured from the page bottom."""
        f = self.doc.font(font)
        self.fonts_used.add(f.resource)
        hexed = f.encode(s).hex()
        r, g, b = rgb
        self.ops.append(
            f"BT /{f.resource} {size:.2f} Tf {r:.3f} {g:.3f} {b:.3f} rg "
            f"1 0 0 1 {x:.2f} {y:.2f} Tm <{hexed}> Tj ET"
        )
        return x + f.text_width(s, size)

    def text_rtl(self, right_x, y, s, font="sans", size=12.0, rgb=(0, 0, 0)):
        """Draw shaped Arabic ending at right_x. Returns the left edge."""
        from . import arabic
        f = self.doc.font(font)
        self.fonts_used.add(f.resource)
        glyphs = arabic.shape(s)
        w = f.glyphs_width(glyphs, size)
        x = right_x - w
        hexed = f.encode_glyphs(glyphs).hex()
        r, g, b = rgb
        self.ops.append(
            f"BT /{f.resource} {size:.2f} Tf {r:.3f} {g:.3f} {b:.3f} rg "
            f"1 0 0 1 {x:.2f} {y:.2f} Tm <{hexed}> Tj ET"
        )
        return x

    def rect(self, x, y, w, h, rgb=(0, 0, 0), fill=True):
        r, g, b = rgb
        op = "f" if fill else "S"
        verb = "rg" if fill else "RG"
        self.ops.append(f"{r:.3f} {g:.3f} {b:.3f} {verb} "
                        f"{x:.2f} {y:.2f} {w:.2f} {h:.2f} re {op}")

    def line(self, x1, y1, x2, y2, rgb=(0, 0, 0), width=1.0):
        r, g, b = rgb
        self.ops.append(f"{r:.3f} {g:.3f} {b:.3f} RG {width:.2f} w "
                        f"{x1:.2f} {y1:.2f} m {x2:.2f} {y2:.2f} l S")

    def image(self, x, y, w, h, png_bytes=None, gray=None, size=None):
        """Place an image. gray is (width, height, bytes) of 8 bit grey samples."""
        self._xobj_n += 1
        name = f"Im{self._xobj_n}"
        self.images[name] = (gray, png_bytes, size)
        self.ops.append(f"q {w:.2f} 0 0 {h:.2f} {x:.2f} {y:.2f} cm /{name} Do Q")

    # Annotations -------------------------------------------------------
    def highlight(self, rects, color=(1.0, 0.945, 0.463), quote="", note=None,
                  group=None, created="D:20240102030405Z", app_owned=True):
        """A highlight annotation. app_owned adds the markers MJ PDF looks for."""
        quads = []
        x0 = min(r[0] for r in rects)
        y0 = min(r[1] for r in rects)
        x1 = max(r[2] for r in rects)
        y1 = max(r[3] for r in rects)
        for (a, b, c, d) in rects:
            quads += [a, d, c, d, a, b, c, b]
        annot = {
            "Type": Name("Annot"),
            "Subtype": Name("Highlight"),
            "Rect": [x0, y0, x1, y1],
            "QuadPoints": quads,
            "C": list(color),
            "F": 4,
        }
        if created:
            annot["CreationDate"] = created
            annot["M"] = created
        if app_owned:
            annot["T"] = MJ_TITLE
            if group:
                annot["MJGroup"] = group
            if quote:
                annot["MJQuote"] = quote
        if note is not None:
            annot["Contents"] = note
        elif quote:
            annot["Contents"] = quote
        self.annots.append(annot)
        return annot

    def link_uri(self, rect, uri):
        self.annots.append({
            "Type": Name("Annot"),
            "Subtype": Name("Link"),
            "Rect": list(rect),
            "Border": [0, 0, 0],
            "A": {"S": Name("URI"), "URI": uri},
        })

    def link_page(self, rect, target_index):
        self.annots.append({
            "Type": Name("Annot"),
            "Subtype": Name("Link"),
            "Rect": list(rect),
            "Border": [0, 0, 0],
            "_goto": target_index,
        })

    def widget(self, rect, field):
        """A form field widget. field is a dict of extra field keys."""
        annot = {
            "Type": Name("Annot"),
            "Subtype": Name("Widget"),
            "Rect": list(rect),
            "F": 4,
        }
        annot.update(field)
        self.annots.append(annot)
        return annot

    def raw_annot(self, annot):
        self.annots.append(annot)
        return annot


class Document:
    def __init__(self, title=None, author=None, page_size=LETTER):
        self.pages = []
        self.page_size = page_size
        self.title = title
        self.author = author
        self._fonts = {}
        self.outline = []          # list of OutlineEntry
        self.acroform_fields = []
        self.extra_catalog = {}
        self.subject = None
        self.keywords = None
        self.creator = "MJ PDF fixture generator"
        self.producer = "MJ PDF fixture generator"
        self.creation_date = FIXED_DATE

    def font(self, key) -> EmbeddedFont:
        """Fonts are keyed by name, or by "name@variant" for an aliased copy.

        A variant is a separate embedded font sharing the same face. It exists so
        one glyph can extract as different characters in different places, which
        is how a fixture produces text no installed font has a glyph for.
        """
        if key not in self._fonts:
            base = key.split("@", 1)[0]
            if base not in FONTS:
                raise KeyError(f"unknown font {base}")
            self._fonts[key] = EmbeddedFont(key, FONTS[base], index=len(self._fonts))
        return self._fonts[key]

    def alias_unicode(self, font_key, drawn: str, extracts_as: str):
        """Make `drawn` extract as `extracts_as` for this font variant only."""
        if len(drawn) != len(extracts_as):
            raise ValueError("alias needs one replacement character per glyph")
        f = self.font(font_key)
        if "@" not in font_key:
            raise ValueError("alias only on a variant font, use name@variant")
        for src, dst in zip(drawn, extracts_as):
            f.unicode_alias[f.face.gid(ord(src))] = ord(dst)

    def add_page(self, size=None) -> Page:
        p = Page(self, size or self.page_size)
        self.pages.append(p)
        return p

    # Text helpers ------------------------------------------------------
    def wrap(self, text, font, size, max_width):
        f = self.font(font)
        words = text.split()
        lines, cur = [], ""
        for w in words:
            trial = (cur + " " + w).strip()
            if cur and f.text_width(trial, size) > max_width:
                lines.append(cur)
                cur = w
            else:
                cur = trial
        if cur:
            lines.append(cur)
        return lines

    def paragraph(self, page, x, y, text, font="serif", size=11.0,
                  leading=15.0, max_width=None):
        """Draw wrapped text downward from y. Returns the next free y."""
        max_width = max_width or (page.width - 2 * x)
        for line in self.wrap(text, font, size, max_width):
            page.text(x, y, line, font=font, size=size)
            y -= leading
        return y

    def line_rect(self, page, x, y, s, font="serif", size=11.0, pad=2.0):
        """The PDF rectangle a drawn line occupies, for highlighting it."""
        f = self.font(font)
        w = f.text_width(s, size)
        asc = f.face.ascent * size / f.face.units_per_em
        desc = f.face.descent * size / f.face.units_per_em
        return (x, y + desc - pad, x + w, y + asc + pad)

    # Output ------------------------------------------------------------
    def _font_objects(self, w):
        out = {}
        for key, f in self._fonts.items():
            face = f.face
            scale = 1000.0 / face.units_per_em
            sub = face.blanked(f.used)
            ff = w.add(Stream(sub, {"Length1": len(sub)}))
            desc = w.add({
                "Type": Name("FontDescriptor"),
                "FontName": Name("MJFixture+" + key.replace("-", "")),
                "Flags": 4,
                "FontBBox": [int(face.x_min * scale), int(face.y_min * scale),
                             int(face.x_max * scale), int(face.y_max * scale)],
                "ItalicAngle": face.italic_angle,
                "Ascent": int(face.ascent * scale),
                "Descent": int(face.descent * scale),
                "CapHeight": int(face.cap_height * scale),
                "StemV": 80,
                "FontFile2": ff,
            })
            # W array covers only the glyphs the fixture draws.
            widths = []
            for g in sorted(f.used):
                if g == 0:
                    continue
                widths.append(g)
                widths.append([int(round(face.advance_units(g) * scale))])
            cid = w.add({
                "Type": Name("Font"),
                "Subtype": Name("CIDFontType2"),
                "BaseFont": Name("MJFixture+" + key.replace("-", "")),
                "CIDSystemInfo": {"Registry": "Adobe", "Ordering": "Identity",
                                  "Supplement": 0},
                "FontDescriptor": desc,
                "DW": 1000,
                "W": widths,
                "CIDToGIDMap": Name("Identity"),
            })
            # ToUnicode makes extracted text real text, which most tests assert on.
            seen = set()
            pairs = []
            for g, val in sorted(f.unicode_alias.items()):
                if g in f.used:
                    text = val if isinstance(val, str) else chr(val)
                    pairs.append((g, text))
                    seen.add(g)
            for cp, g in sorted(face.cmap.items()):
                if g in f.used and cp <= 0xFFFF and g not in seen:
                    pairs.append((g, chr(cp)))
                    seen.add(g)
            pairs.sort()
            cmap_lines = [
                "/CIDInit /ProcSet findresource begin",
                "12 dict begin begincmap",
                "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def",
                "/CMapName /Adobe-Identity-UCS def", "/CMapType 2 def",
                "1 begincodespacerange <0000> <FFFF> endcodespacerange",
            ]
            for i in range(0, len(pairs), 100):
                chunk = pairs[i:i + 100]
                cmap_lines.append(f"{len(chunk)} beginbfchar")
                for g, text in chunk:
                    # A ligature glyph extracts as more than one character.
                    dst = "".join(f"{ord(c):04X}" for c in text)
                    cmap_lines.append(f"<{g:04X}> <{dst}>")
                cmap_lines.append("endbfchar")
            cmap_lines += ["endcmap", "CMapName currentdict /CMap defineresource pop",
                           "end", "end"]
            tou = w.add(Stream("\n".join(cmap_lines).encode("ascii")))
            out[f.resource] = w.add({
                "Type": Name("Font"),
                "Subtype": Name("Type0"),
                "BaseFont": Name("MJFixture+" + key.replace("-", "")),
                "Encoding": Name("Identity-H"),
                "DescendantFonts": [cid],
                "ToUnicode": tou,
            })
        return out

    def save(self, path, encrypt_password=None, damage=None):
        w = PdfWriter()
        catalog_ref = w.reserve()
        pages_ref = w.reserve()
        page_refs = [w.reserve() for _ in self.pages]

        font_objs = self._font_objects(w)

        for page, pref in zip(self.pages, page_refs):
            content = "\n".join(page.ops).encode("latin-1")
            cref = w.add(Stream(content))
            xobjects = {}
            for name, (gray, png, size) in page.images.items():
                if gray is not None:
                    gw, gh, data = gray
                    xobjects[name] = w.add(Stream(data, {
                        "Type": Name("XObject"), "Subtype": Name("Image"),
                        "Width": gw, "Height": gh,
                        "ColorSpace": Name("DeviceGray"),
                        "BitsPerComponent": 8,
                    }))
                else:
                    iw, ih = size
                    xobjects[name] = w.add(Stream(png, {
                        "Type": Name("XObject"), "Subtype": Name("Image"),
                        "Width": iw, "Height": ih,
                        "ColorSpace": Name("DeviceRGB"),
                        "BitsPerComponent": 8,
                        "Filter": Name("DCTDecode"),
                    }, compress=False))
            resources = {"ProcSet": [Name("PDF"), Name("Text"), Name("ImageB"),
                                     Name("ImageC")]}
            if page.fonts_used:
                resources["Font"] = {r: font_objs[r] for r in sorted(page.fonts_used)}
            if xobjects:
                resources["XObject"] = xobjects

            annot_refs = []
            for a in page.annots:
                a = dict(a)
                if "_goto" in a:
                    target = a.pop("_goto")
                    a["A"] = {"S": Name("GoTo"),
                              "D": [page_refs[target], Name("XYZ"), None, None, None]}
                a["P"] = pref
                annot_refs.append(w.add(a))

            pdict = {
                "Type": Name("Page"),
                "Parent": pages_ref,
                "MediaBox": [0, 0, page.width, page.height],
                "Resources": resources,
                "Contents": cref,
            }
            if annot_refs:
                pdict["Annots"] = annot_refs
            w.add(pdict, pref)

        w.add({"Type": Name("Pages"), "Kids": page_refs,
               "Count": len(page_refs)}, pages_ref)

        catalog = {"Type": Name("Catalog"), "Pages": pages_ref}
        if self.outline:
            catalog["Outlines"] = _build_outline(w, self.outline, page_refs)
            catalog["PageMode"] = Name("UseOutlines")
        if self.acroform_fields:
            catalog["AcroForm"] = {"Fields": self.acroform_fields,
                                   "DA": "/Helv 0 Tf 0 g",
                                   "NeedAppearances": True}
        catalog.update(self.extra_catalog)
        w.add(catalog, catalog_ref)

        info = {"Producer": self.producer, "Creator": self.creator,
                "CreationDate": self.creation_date, "ModDate": self.creation_date}
        if self.title:
            info["Title"] = self.title
        if self.author:
            info["Author"] = self.author
        if self.subject:
            info["Subject"] = self.subject
        if self.keywords:
            info["Keywords"] = self.keywords
        info_ref = w.add(info)

        w.trailer_extra["Root"] = catalog_ref
        w.trailer_extra["Info"] = info_ref
        data = w.build()

        if encrypt_password:
            data = _encrypt_rc4_40(data, encrypt_password)
        if damage:
            data = damage(data)

        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(data)
        return path


class OutlineEntry:
    def __init__(self, title, page_index, children=None):
        self.title = title
        self.page_index = page_index
        self.children = children or []


def _build_outline(w, entries, page_refs, parent=None):
    """Write an /Outlines tree with the Prev, Next, First, Last and Count links."""
    root = parent or w.reserve()
    refs = [w.reserve() for _ in entries]
    total = 0
    for i, (entry, ref) in enumerate(zip(entries, refs)):
        d = {"Title": entry.title, "Parent": root}
        if i > 0:
            d["Prev"] = refs[i - 1]
        if i < len(refs) - 1:
            d["Next"] = refs[i + 1]
        if entry.page_index is None:
            # An unresolvable destination, on purpose.
            d["Dest"] = [w.objects and Ref(1) or None, Name("XYZ"), None, None, None]
        else:
            d["Dest"] = [page_refs[entry.page_index], Name("XYZ"), None, None, None]
        count = 1
        if entry.children:
            child_root = _build_outline(w, entry.children, page_refs, parent=ref)
            sub = w.objects[child_root.num]
            d["First"] = sub["First"]
            d["Last"] = sub["Last"]
            d["Count"] = sub["Count"]
            count += sub["Count"]
        w.add(d, ref)
        total += count
    node = {"Type": Name("Outlines"), "Count": total}
    if refs:
        node["First"] = refs[0]
        node["Last"] = refs[-1]
    w.add(node, root)
    return root


def _rc4(key: bytes, data: bytes) -> bytes:
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + key[i % len(key)]) & 0xFF
        s[i], s[j] = s[j], s[i]
    out = bytearray()
    i = j = 0
    for b in data:
        i = (i + 1) & 0xFF
        j = (j + s[i]) & 0xFF
        s[i], s[j] = s[j], s[i]
        out.append(b ^ s[(s[i] + s[j]) & 0xFF])
    return bytes(out)


PAD = bytes([
    0x28, 0xBF, 0x4E, 0x5E, 0x4E, 0x75, 0x8A, 0x41, 0x64, 0x00, 0x4E, 0x56,
    0xFF, 0xFA, 0x01, 0x08, 0x2E, 0x2E, 0x00, 0xB6, 0xD0, 0x68, 0x3E, 0x80,
    0x2F, 0x0C, 0xA9, 0xFE, 0x64, 0x53, 0x69, 0x7A])


def _encrypt_rc4_40(data: bytes, password: str) -> bytes:
    """Standard security handler revision 2, which pdfium reads.

    Re-parses the finished file, encrypts every string and stream, and appends
    an /Encrypt dictionary. Kept deliberately simple: it handles the shape this
    generator emits, not arbitrary PDFs.
    """
    import re

    pwd = (password.encode("latin-1") + PAD)[:32]
    owner_key = _rc4(hashlib.md5((password.encode("latin-1") + PAD)[:32]).digest()[:5], PAD)
    perms = -4  # allow printing and copying
    doc_id = bytes.fromhex(re.search(rb"/ID\s*\[\s*<([0-9A-Fa-f]+)>", data).group(1).decode())
    h = hashlib.md5()
    h.update(pwd)
    h.update(owner_key)
    h.update(perms.to_bytes(4, "little", signed=True))
    h.update(doc_id)
    key = h.digest()[:5]
    user_key = _rc4(key, PAD)

    def obj_key(num, gen=0):
        return hashlib.md5(key + num.to_bytes(3, "little")
                           + gen.to_bytes(2, "little")).digest()[:10]

    out = bytearray()
    pos = 0
    obj_re = re.compile(rb"(\d+) 0 obj\n", re.S)
    pieces = []
    for m in obj_re.finditer(data):
        pieces.append((int(m.group(1)), m.end()))
    pieces.append((None, len(data)))

    out += data[:pieces[0][1]] if pieces else data
    for idx in range(len(pieces) - 1):
        num, start = pieces[idx]
        end = pieces[idx + 1][1]
        end = data.find(b"\nendobj\n", start)
        body = data[start:end]
        k = obj_key(num)
        sm = re.search(rb"stream\n", body)
        if sm:
            se = body.rfind(b"\nendstream")
            head, payload = body[:sm.end()], body[sm.end():se]
            body = head + _rc4(k, payload) + body[se:]
        out += body
        out += data[end:pieces[idx + 1][1]] if idx + 1 < len(pieces) - 1 else b""
    # Simpler and safer: rebuild wholesale below rather than patch in place.
    return _encrypt_rebuild(data, key, obj_key, owner_key, user_key, perms)


def _encrypt_rebuild(data, key, obj_key, owner_key, user_key, perms):
    import re
    objs = {}
    for m in re.finditer(rb"(\d+) 0 obj\n(.*?)\nendobj\n", data, re.S):
        objs[int(m.group(1))] = m.group(2)
    root = re.search(rb"/Root (\d+) 0 R", data).group(1).decode()
    info = re.search(rb"/Info (\d+) 0 R", data).group(1).decode()
    doc_id = re.search(rb"/ID\s*\[\s*<([0-9A-Fa-f]+)>", data).group(1).decode()

    new = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    maxnum = max(objs)
    for num in range(1, maxnum + 1):
        body = objs.get(num)
        if body is None:
            continue
        k = obj_key(num)
        sm = re.search(rb"stream\n", body)
        if sm:
            se = body.rfind(b"\nendstream")
            head, payload = body[:sm.end()], body[sm.end():se]
            enc = _rc4(k, payload)
            head = re.sub(rb"/Length \d+", b"/Length " + str(len(enc)).encode(), head)
            body = head + enc + body[se:]
        else:
            def enc_str(m):
                return b"(" + _rc4(k, m.group(1)).replace(b"\\", b"\\\\") \
                    .replace(b"(", b"\\(").replace(b")", b"\\)") + b")"
            body = re.sub(rb"\(((?:[^()\\]|\\.)*)\)", enc_str, body)
        offsets[num] = len(new)
        new += f"{num} 0 obj\n".encode() + body + b"\nendobj\n"

    enc_num = maxnum + 1
    offsets[enc_num] = len(new)
    new += f"{enc_num} 0 obj\n".encode()
    new += (b"<< /Filter /Standard /V 1 /R 2 /Length 40 /P " + str(perms).encode()
            + b" /O <" + owner_key.hex().upper().encode() + b"> /U <"
            + user_key.hex().upper().encode() + b"> >>")
    new += b"\nendobj\n"

    xref_at = len(new)
    n = enc_num + 1
    new += f"xref\n0 {n}\n".encode()
    new += b"0000000000 65535 f \n"
    for num in range(1, n):
        new += f"{offsets.get(num, 0):010d} 00000 n \n".encode()
    new += (b"trailer\n<< /Size " + str(n).encode()
            + b" /Root " + root.encode() + b" 0 R"
            + b" /Info " + info.encode() + b" 0 R"
            + b" /Encrypt " + str(enc_num).encode() + b" 0 R"
            + b" /ID [<" + doc_id.encode() + b"> <" + doc_id.encode() + b">] >>")
    new += f"\nstartxref\n{xref_at}\n%%EOF\n".encode()
    return bytes(new)
