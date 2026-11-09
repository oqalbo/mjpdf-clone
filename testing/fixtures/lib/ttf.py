"""TrueType reading and glyph blanking, with no third party dependencies.

Full subsetting rewrites glyph ids and every table that references them, which is
easy to get subtly wrong. Blanking keeps the table layout and every glyph id
exactly as the source font has them, and empties the outlines of glyphs the
fixture never draws. Flate then compresses the emptied region to almost nothing,
so a 760 KB source font embeds as tens of KB while staying a valid font that
pdfium parses through its normal path.
"""

import struct
from collections import OrderedDict


def _u16(b, o):
    return struct.unpack(">H", b[o:o + 2])[0]


def _i16(b, o):
    return struct.unpack(">h", b[o:o + 2])[0]


def _u32(b, o):
    return struct.unpack(">I", b[o:o + 4])[0]


class TrueTypeFont:
    def __init__(self, data: bytes):
        self.data = data
        self.tables = OrderedDict()
        num_tables = _u16(data, 4)
        for i in range(num_tables):
            rec = 12 + i * 16
            tag = data[rec:rec + 4].decode("latin-1")
            self.tables[tag] = (_u32(data, rec + 8), _u32(data, rec + 12))
        self._parse_head()
        self._parse_hmtx()
        self._parse_cmap()
        self._parse_loca()

    def table(self, tag):
        if tag not in self.tables:
            return None
        off, length = self.tables[tag]
        return self.data[off:off + length]

    def _parse_head(self):
        head = self.table("head")
        self.units_per_em = _u16(head, 18)
        self.index_to_loc_format = _i16(head, 50)
        self.x_min = _i16(head, 36)
        self.y_min = _i16(head, 38)
        self.x_max = _i16(head, 40)
        self.y_max = _i16(head, 42)
        hhea = self.table("hhea")
        self.ascent = _i16(hhea, 4)
        self.descent = _i16(hhea, 6)
        self.num_h_metrics = _u16(hhea, 34)
        maxp = self.table("maxp")
        self.num_glyphs = _u16(maxp, 4)
        os2 = self.table("OS/2")
        # sCapHeight only exists from version 2 onward.
        self.cap_height = _i16(os2, 88) if os2 and _u16(os2, 0) >= 2 else self.ascent
        post = self.table("post")
        self.italic_angle = struct.unpack(">i", post[4:8])[0] / 65536.0 if post else 0.0

    def _parse_hmtx(self):
        hmtx = self.table("hmtx")
        self.advances = []
        adv = 0
        for i in range(self.num_glyphs):
            if i < self.num_h_metrics:
                adv = _u16(hmtx, i * 4)
            self.advances.append(adv)

    def _parse_loca(self):
        loca = self.table("loca")
        self.loca = []
        if loca is None:
            return
        if self.index_to_loc_format == 0:
            for i in range(self.num_glyphs + 1):
                self.loca.append(_u16(loca, i * 2) * 2)
        else:
            for i in range(self.num_glyphs + 1):
                self.loca.append(_u32(loca, i * 4))

    def _parse_cmap(self):
        """Read a unicode subtable. Format 4 covers the BMP, format 12 covers the rest."""
        cmap = self.table("cmap")
        self.cmap = {}
        n = _u16(cmap, 2)
        best = None
        for i in range(n):
            rec = 4 + i * 8
            plat = _u16(cmap, rec)
            enc = _u16(cmap, rec + 2)
            off = _u32(cmap, rec + 4)
            fmt = _u16(cmap, off)
            score = 0
            if plat == 3 and enc == 10 and fmt == 12:
                score = 4
            elif plat == 3 and enc == 1 and fmt == 4:
                score = 3
            elif plat == 0 and fmt in (4, 12):
                score = 2
            if score and (best is None or score > best[0]):
                best = (score, off, fmt)
        if best is None:
            raise ValueError("no usable unicode cmap subtable")
        _, off, fmt = best
        if fmt == 4:
            seg_x2 = _u16(cmap, off + 6)
            segs = seg_x2 // 2
            ends = off + 14
            starts = ends + seg_x2 + 2
            deltas = starts + seg_x2
            ranges = deltas + seg_x2
            for s in range(segs):
                end = _u16(cmap, ends + s * 2)
                start = _u16(cmap, starts + s * 2)
                delta = _u16(cmap, deltas + s * 2)
                ro = _u16(cmap, ranges + s * 2)
                if start == 0xFFFF:
                    continue
                for cp in range(start, end + 1):
                    if ro == 0:
                        gid = (cp + delta) & 0xFFFF
                    else:
                        gi = ranges + s * 2 + ro + (cp - start) * 2
                        if gi + 2 > len(cmap):
                            continue
                        gid = _u16(cmap, gi)
                        if gid:
                            gid = (gid + delta) & 0xFFFF
                    if gid:
                        self.cmap[cp] = gid
        else:
            ngroups = _u32(cmap, off + 12)
            for g in range(ngroups):
                rec = off + 16 + g * 12
                sc, ec, sg = _u32(cmap, rec), _u32(cmap, rec + 4), _u32(cmap, rec + 8)
                for cp in range(sc, ec + 1):
                    self.cmap[cp] = sg + (cp - sc)

    def gid(self, codepoint: int) -> int:
        return self.cmap.get(codepoint, 0)

    def advance_units(self, gid: int) -> int:
        return self.advances[gid] if gid < len(self.advances) else 0

    def composite_components(self, gid: int):
        """Glyph ids a composite glyph references, so blanking keeps them."""
        glyf = self.table("glyf")
        if glyf is None or gid + 1 >= len(self.loca):
            return []
        start, end = self.loca[gid], self.loca[gid + 1]
        if end <= start or end - start < 10:
            return []
        g = glyf[start:end]
        if _i16(g, 0) >= 0:
            return []
        out = []
        o = 10
        while o + 4 <= len(g):
            flags = _u16(g, o)
            out.append(_u16(g, o + 2))
            o += 4
            o += 4 if (flags & 1) else 2
            if flags & 8:
                o += 2
            elif flags & 0x40:
                o += 4
            elif flags & 0x80:
                o += 8
            if not (flags & 0x20):
                break
        return out

    def blanked(self, keep_gids) -> bytes:
        """Rebuild the font keeping only the outlines of keep_gids."""
        keep = {0}
        pending = list(keep_gids) + [0]
        while pending:
            g = pending.pop()
            if g in keep and g != 0:
                continue
            keep.add(g)
            for c in self.composite_components(g):
                if c not in keep:
                    pending.append(c)

        glyf = self.table("glyf")
        new_glyf = bytearray()
        new_loca = [0]
        for g in range(self.num_glyphs):
            if g in keep and g + 1 < len(self.loca):
                start, end = self.loca[g], self.loca[g + 1]
                new_glyf += glyf[start:end]
            # A glyph with no outline is a zero length entry, which is legal.
            while len(new_glyf) % 4:
                new_glyf.append(0)
            new_loca.append(len(new_glyf))

        long_loca = new_loca[-1] > 0x1FFFF
        if long_loca:
            loca_bytes = b"".join(struct.pack(">I", o) for o in new_loca)
        else:
            loca_bytes = b"".join(struct.pack(">H", o // 2) for o in new_loca)

        head = bytearray(self.table("head"))
        struct.pack_into(">h", head, 50, 1 if long_loca else 0)
        # Zero the checksum adjustment so output does not depend on table order.
        struct.pack_into(">I", head, 8, 0)

        out_tables = OrderedDict()
        for tag in self.tables:
            if tag in ("glyf", "loca", "head"):
                continue
            out_tables[tag] = self.table(tag)
        out_tables["glyf"] = bytes(new_glyf)
        out_tables["loca"] = loca_bytes
        out_tables["head"] = bytes(head)
        # Table directory must be sorted by tag; keeps output deterministic.
        out_tables = OrderedDict(sorted(out_tables.items()))

        n = len(out_tables)
        search_range = 1
        entry_selector = 0
        while search_range * 2 <= n:
            search_range *= 2
            entry_selector += 1
        search_range *= 16

        header = struct.pack(">IHHHH", 0x00010000, n, search_range,
                             entry_selector, n * 16 - search_range)
        offset = 12 + n * 16
        dir_entries = b""
        body = b""
        for tag, data in out_tables.items():
            padded = data + b"\0" * ((4 - len(data) % 4) % 4)
            csum = 0
            for i in range(0, len(padded), 4):
                csum = (csum + _u32(padded, i)) & 0xFFFFFFFF
            dir_entries += struct.pack(">4sIII", tag.encode("latin-1"), csum,
                                       offset, len(data))
            body += padded
            offset += len(padded)
        return header + dir_entries + body


_CACHE = {}


def load(path: str) -> TrueTypeFont:
    if path not in _CACHE:
        with open(path, "rb") as fh:
            _CACHE[path] = TrueTypeFont(fh.read())
    return _CACHE[path]
