"""Arabic shaping and visual reordering for fixture generation.

A real Arabic PDF does not store logical order Unicode. It stores positional
presentation forms laid out right to left, and carries a ToUnicode map that
takes them back to the base letters. Extraction therefore yields proper logical
Arabic while the page renders as connected script.

This module does the same thing, because a fixture that renders as disconnected
letters in reversed order would not exercise anything the real world contains.
It implements the joining rules for the Arabic block plus the four lam alef
ligatures. It is not a general purpose shaper: no kashida justification, no
Indic style reordering, no OpenType feature lookups.
"""

# base -> (isolated, final, initial, medial). 0 means the form does not exist,
# which is what makes a letter right joining rather than dual joining.
FORMS = {
    0x0621: (0xFE80, 0, 0, 0),
    0x0622: (0xFE81, 0xFE82, 0, 0),
    0x0623: (0xFE83, 0xFE84, 0, 0),
    0x0624: (0xFE85, 0xFE86, 0, 0),
    0x0625: (0xFE87, 0xFE88, 0, 0),
    0x0626: (0xFE89, 0xFE8A, 0xFE8B, 0xFE8C),
    0x0627: (0xFE8D, 0xFE8E, 0, 0),
    0x0628: (0xFE8F, 0xFE90, 0xFE91, 0xFE92),
    0x0629: (0xFE93, 0xFE94, 0, 0),
    0x062A: (0xFE95, 0xFE96, 0xFE97, 0xFE98),
    0x062B: (0xFE99, 0xFE9A, 0xFE9B, 0xFE9C),
    0x062C: (0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0),
    0x062D: (0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4),
    0x062E: (0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8),
    0x062F: (0xFEA9, 0xFEAA, 0, 0),
    0x0630: (0xFEAB, 0xFEAC, 0, 0),
    0x0631: (0xFEAD, 0xFEAE, 0, 0),
    0x0632: (0xFEAF, 0xFEB0, 0, 0),
    0x0633: (0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4),
    0x0634: (0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8),
    0x0635: (0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC),
    0x0636: (0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0),
    0x0637: (0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4),
    0x0638: (0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8),
    0x0639: (0xFEC9, 0xFECA, 0xFECB, 0xFECC),
    0x063A: (0xFECD, 0xFECE, 0xFECF, 0xFED0),
    0x0641: (0xFED1, 0xFED2, 0xFED3, 0xFED4),
    0x0642: (0xFED5, 0xFED6, 0xFED7, 0xFED8),
    0x0643: (0xFED9, 0xFEDA, 0xFEDB, 0xFEDC),
    0x0644: (0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0),
    0x0645: (0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4),
    0x0646: (0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8),
    0x0647: (0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC),
    0x0648: (0xFEED, 0xFEEE, 0, 0),
    0x0649: (0xFEEF, 0xFEF0, 0, 0),
    0x064A: (0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4),
}

# lam followed by one of these alef forms becomes a single ligature glyph.
LAM_ALEF = {
    0x0622: (0xFEF5, 0xFEF6),
    0x0623: (0xFEF7, 0xFEF8),
    0x0625: (0xFEF9, 0xFEFA),
    0x0627: (0xFEFB, 0xFEFC),
}

# Combining marks are transparent to joining and carry no advance of their own.
def is_mark(cp):
    return 0x064B <= cp <= 0x0652 or cp == 0x0670 or 0x06D6 <= cp <= 0x06ED


def is_arabic(cp):
    return 0x0600 <= cp <= 0x06FF or 0xFB50 <= cp <= 0xFEFF


def _dual(cp):
    """True when the letter can join on its left, so the next letter connects."""
    f = FORMS.get(cp)
    return bool(f and f[2])


def _joinable(cp):
    """True when the letter can join on its right, so it connects to the previous."""
    return cp in FORMS or cp == 0x0640  # tatweel joins on both sides


class Glyph:
    """One drawn glyph and the characters it extracts as."""

    __slots__ = ("codepoint", "text")

    def __init__(self, codepoint, text):
        self.codepoint = codepoint
        self.text = text

    def __repr__(self):
        return f"Glyph({self.codepoint:04X}, {self.text!r})"


def shape(text: str) -> list:
    """Shape one run of text and return glyphs in visual (drawing) order.

    Arabic segments are converted to presentation forms and reversed. Latin and
    digits inside the run keep their order, which is the behaviour a mixed
    direction line needs.
    """
    clusters = []  # (base_index, [Glyph...]) in logical order
    cps = [ord(c) for c in text]

    # Pass one: pick a positional form per letter.
    shaped = []
    i = 0
    while i < len(cps):
        cp = cps[i]
        if is_mark(cp):
            shaped.append(Glyph(cp, chr(cp)))
            i += 1
            continue

        # Look back and forward past marks for the joining context.
        j = i - 1
        while j >= 0 and is_mark(cps[j]):
            j -= 1
        prev_cp = cps[j] if j >= 0 else None
        k = i + 1
        while k < len(cps) and is_mark(cps[k]):
            k += 1
        next_cp = cps[k] if k < len(cps) else None

        # Lam alef is one glyph made of two characters.
        if cp == 0x0644 and next_cp in LAM_ALEF:
            joins_prev = prev_cp is not None and (_dual(prev_cp) or prev_cp == 0x0640)
            iso, fin = LAM_ALEF[next_cp]
            shaped.append(Glyph(fin if joins_prev else iso,
                                chr(cp) + chr(next_cp)))
            i = k + 1
            continue

        if cp in FORMS:
            joins_prev = prev_cp is not None and (_dual(prev_cp) or prev_cp == 0x0640)
            joins_next = (_dual(cp) and next_cp is not None
                          and (_joinable(next_cp) or next_cp == 0x0640))
            iso, fin, ini, med = FORMS[cp]
            if joins_prev and joins_next and med:
                form = med
            elif joins_prev and fin:
                form = fin
            elif joins_next and ini:
                form = ini
            else:
                form = iso
            shaped.append(Glyph(form, chr(cp)))
        else:
            shaped.append(Glyph(cp, chr(cp)))
        i += 1

    # Pass two: group each base with its following marks, then reverse the
    # Arabic segments so the run draws right to left.
    for g in shaped:
        if is_mark(g.codepoint) and clusters:
            clusters[-1].append(g)
        else:
            clusters.append([g])

    out = []
    segment = []
    for cluster in clusters:
        lead = cluster[0].codepoint
        arabic_like = is_arabic(lead) or (lead == 0x20 and segment)
        if arabic_like:
            segment.append(cluster)
        else:
            if segment:
                out.extend(reversed(segment))
                segment = []
            out.append(cluster)
    if segment:
        out.extend(reversed(segment))

    flat = []
    for cluster in out:
        flat.extend(cluster)
    # Trailing space that led a reversed segment ends up first; harmless.
    return flat

