"""A deterministic PDF writer.

Determinism is the whole point: the same generator run twice, on any machine,
must produce byte identical output, because every screenshot golden in the test
plan is downstream of these files. So there are no timestamps unless a fixture
asks for a fixed one, the document id is derived from the content rather than
random, objects are written in creation order, and dictionary keys are emitted
in insertion order.

The writer emits a plain cross reference table rather than a cross reference
stream, so a fixture can be inspected with a text editor when a test disagrees
with it.
"""

import hashlib
import zlib

FIXED_DATE = "D:20240101000000Z"


class Name(str):
    """A PDF name, so a plain string can stay a plain string."""


class Raw(str):
    """Pre-serialised PDF syntax, inserted verbatim."""


class Ref:
    __slots__ = ("num",)

    def __init__(self, num):
        self.num = num

    def __repr__(self):
        return f"{self.num} 0 R"


class Stream:
    def __init__(self, data: bytes, extra=None, compress=True):
        self.data = data
        self.extra = extra or {}
        self.compress = compress


def _escape_string(s: bytes) -> bytes:
    out = bytearray(b"(")
    for b in s:
        if b in (0x28, 0x29, 0x5C):
            out += b"\\" + bytes([b])
        elif b == 0x0A:
            out += b"\\n"
        elif b == 0x0D:
            out += b"\\r"
        elif b < 32 or b > 126:
            out += f"\\{b:03o}".encode("ascii")
        else:
            out.append(b)
    out += b")"
    return bytes(out)


def _serialise(obj) -> bytes:
    if obj is None:
        return b"null"
    if isinstance(obj, Raw):
        return str(obj).encode("latin-1")
    if isinstance(obj, Name):
        return b"/" + str(obj).encode("latin-1")
    if isinstance(obj, Ref):
        return f"{obj.num} 0 R".encode("ascii")
    if isinstance(obj, bool):
        return b"true" if obj else b"false"
    if isinstance(obj, int):
        return str(obj).encode("ascii")
    if isinstance(obj, float):
        # Fixed formatting so a float never varies by platform repr.
        s = f"{obj:.4f}".rstrip("0").rstrip(".")
        return (s if s else "0").encode("ascii")
    if isinstance(obj, bytes):
        return _escape_string(obj)
    if isinstance(obj, str):
        return _escape_string(obj.encode("utf-8"))
    if isinstance(obj, list):
        return b"[" + b" ".join(_serialise(x) for x in obj) + b"]"
    if isinstance(obj, dict):
        parts = [b"<<"]
        for k, v in obj.items():
            parts.append(b"/" + str(k).encode("latin-1") + b" " + _serialise(v))
        parts.append(b">>")
        return b" ".join(parts)
    raise TypeError(f"cannot serialise {type(obj)}")


class PdfWriter:
    def __init__(self, version="1.7"):
        self.version = version
        self.objects = [None]  # index 0 is the free head
        self.trailer_extra = {}

    def reserve(self) -> Ref:
        self.objects.append(None)
        return Ref(len(self.objects) - 1)

    def add(self, obj, ref: Ref = None) -> Ref:
        if ref is None:
            ref = self.reserve()
        self.objects[ref.num] = obj
        return ref

    def _body(self, obj) -> bytes:
        if isinstance(obj, Stream):
            data = obj.data
            d = dict(obj.extra)
            if obj.compress and "Filter" not in d:
                # Fixed level and no zlib header variation keeps bytes stable.
                data = zlib.compress(data, 9)
                d["Filter"] = Name("FlateDecode")
            d["Length"] = len(data)
            return _serialise(d) + b"\nstream\n" + data + b"\nendstream"
        return _serialise(obj)

    def build(self) -> bytes:
        out = bytearray()
        out += f"%PDF-{self.version}\n".encode("ascii")
        # Binary comment marks the file as binary for transfer tools.
        out += b"%\xe2\xe3\xcf\xd3\n"
        offsets = [0] * len(self.objects)
        for num in range(1, len(self.objects)):
            obj = self.objects[num]
            if obj is None:
                raise ValueError(f"object {num} was reserved but never written")
            offsets[num] = len(out)
            out += f"{num} 0 obj\n".encode("ascii")
            out += self._body(obj)
            out += b"\nendobj\n"

        xref_at = len(out)
        n = len(self.objects)
        out += f"xref\n0 {n}\n".encode("ascii")
        out += b"0000000000 65535 f \n"
        for num in range(1, n):
            out += f"{offsets[num]:010d} 00000 n \n".encode("ascii")

        trailer = {"Size": n}
        trailer.update(self.trailer_extra)
        digest = hashlib.md5(bytes(out)).hexdigest().upper()
        trailer["ID"] = [Raw(f"<{digest}>"), Raw(f"<{digest}>")]
        out += b"trailer\n" + _serialise(trailer)
        out += f"\nstartxref\n{xref_at}\n%%EOF\n".encode("ascii")
        return bytes(out)
