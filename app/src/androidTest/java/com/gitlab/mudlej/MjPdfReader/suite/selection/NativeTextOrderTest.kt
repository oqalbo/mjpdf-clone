// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shockwave.pdfium.PdfiumCore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class NativeTextOrderTest {
    @Test
    fun logicalWordMappingKeepsTextAndMetricIndexOrder() {
        fun stream(data: String) = "<< /Length ${data.length} >>\nstream\n$data\nendstream"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type3 /FontBBox [0 0 600 800] /FontMatrix [0.001 0 0 0.001 0 0] /CharProcs << /word 6 0 R >> /Encoding << /Type /Encoding /Differences [1 /word] >> /FirstChar 1 /LastChar 1 /Widths [600] /Resources << >> /ToUnicode 7 0 R >>",
            stream("BT /F1 20 Tf 100 100 Td <01> Tj ET"),
            stream("600 0 0 0 600 800 d1 0 0 600 800 re f"),
            stream("/CIDInit /ProcSet findresource begin 12 dict begin begincmap /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def /CMapName /LogicalWord def /CMapType 2 def 1 begincodespacerange <00> <FF> endcodespacerange 1 beginbfchar <01> <0628064E062A0650> endbfchar endcmap CMapName currentdict /CMap defineresource pop end end"),
        )
        val pdf = buildString {
            append("%PDF-1.7\n")
            val offsets = objects.mapIndexed { index, obj ->
                val offset = length
                append("${index + 1} 0 obj\n$obj\nendobj\n")
                offset
            }
            val xref = length
            append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { append(String.format(Locale.US, "%010d 00000 n \n", it)) }
            append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }.toByteArray(Charsets.US_ASCII)
        val core = PdfiumCore(InstrumentationRegistry.getInstrumentation().targetContext)
        val doc = core.newDocument(pdf)
        try {
            core.openPage(doc, 0)
            assertTrue(core.openTextPage(doc, 0) != 0L)
            val expected = "\u0628\u064e\u062a\u0650"
            assertEquals(expected.length, core.textCountChars(doc, 0))
            val native = expected.indices.map { core.charUnicode(doc, 0, it).toChar() }.joinToString("")
            assertEquals("PDFium must first honor the authored mapping", expected, native)
            assertEquals("JNI text must preserve the logical mapping", expected, core.textRange(doc, 0, 0, expected.length))
            assertEquals(expected, core.getPagesText(doc, 0, 0)[0])
            val metrics = core.textCharMetrics(doc, 0, 0, expected.length)
            assertEquals(expected.length * 9, metrics.size)
            expected.indices.forEach { index ->
                assertEquals(expected[index].code.toDouble(), metrics[index * 9 + 6], 0.0)
                assertEquals(expected.substring(index, index + 1), core.textRange(doc, 0, index, 1))
                assertArrayEquals(core.textCharMetrics(doc, 0, index, 1), metrics.copyOfRange(index * 9, (index + 1) * 9), 0.0)
            }
        } finally {
            core.closeTextPage(doc, 0)
            core.closePage(doc, 0)
            core.closeDocument(doc)
        }
    }
}
