// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

import com.shockwave.pdfium.PdfiumCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.Normalizer

class NormalizedTextMapperTest {

    private val fixtures = listOf(
        "A diﬃcult case",
        "the ﬂow of",
        "oﬀer and ﬁnd",
        "ﬄuid ﬃrst",
        "ratio ½ of it",
        "chapter Ⅸ begins",
        "ends here… and on",
        "abc\uFFFE\r\ndef",
        "abc\uFFFE\ndef",
        "abc\uFFFEdef",
        "line one\r\nline two",
        "ﬁrst word",
        "trailing ﬁ",
    )

    @Test
    fun everyRangeMapsBackToTheSameNormalizedText() {
        var checked = 0
        for (raw in fixtures) {
            val normalized = normalize(raw)
            for (start in normalized.indices) {
                for (length in 1..normalized.length - start) {
                    val range = NormalizedTextMapper.toRawRange(raw, start, length)
                    val target = normalized.substring(start, start + length)
                    val message = "raw=${escape(raw)} start=$start length=$length target=${escape(target)}"

                    if (range == null) {
                        throw AssertionError("$message returned null")
                    }
                    val covered = normalize(raw.substring(range.first, range.last + 1))
                    if (!covered.contains(target)) {
                        throw AssertionError("$message covered ${escape(covered)}")
                    }
                    checked++
                }
            }
        }
        assertEquals(true, checked > 500)
    }

    @Test
    fun aMatchStartingInsideALigatureCoversTheLigature() {
        val raw = "A diﬃcult case"

        val range = NormalizedTextMapper.toRawRange(raw, 5, 2)

        assertEquals(4, range?.first)
        assertEquals(4, range?.last)
    }

    @Test
    fun aMatchStartingInsideALigatureIsNotTruncated() {
        val raw = "the ﬂow of"

        val range = NormalizedTextMapper.toRawRange(raw, 5, 3)

        assertEquals("ﬂow", raw.substring(range!!.first, range.last + 1))
    }

    @Test
    fun nullMeansOnlyThatTheStartIsPastTheEnd() {
        val raw = "short"

        assertNull(NormalizedTextMapper.toRawRange(raw, 5, 1))
        assertNull(NormalizedTextMapper.toRawRange(raw, 99, 1))
        assertNull(NormalizedTextMapper.toRawRange("", 0, 1))
        assertNull(NormalizedTextMapper.toRawRange(raw, -1, 1))
        assertNull(NormalizedTextMapper.toRawRange(raw, 0, 0))
    }

    private fun normalize(raw: String): String {
        return Normalizer.normalize(PdfiumCore.mapPresentationFormMarks(raw), Normalizer.Form.NFKC)
            .replace("\uFFFE\r\n", "")
            .replace("\uFFFE\n", "")
            .replace("\uFFFE\r", "")
            .replace("\uFFFE", "")
            .replace("\u200B", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    private fun escape(text: String): String {
        return text.map { if (it.code in 32..126) it.toString() else "\\u%04x".format(it.code) }
            .joinToString("")
    }
}
