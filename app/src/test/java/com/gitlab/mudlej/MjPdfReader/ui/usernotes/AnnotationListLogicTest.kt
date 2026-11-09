// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.usernotes

import org.junit.Assert.*
import org.junit.Test
import com.gitlab.mudlej.MjPdfReader.pdf.SweptHighlight
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import java.time.OffsetDateTime
import org.junit.Ignore

class AnnotationListLogicTest {
    private fun item(key: String, date: String? = null, quote: String = "", note: String = "", color: Int = 1) =
        SweptHighlight(0, 0, key, color, quote, note, date)

    private val labels = AnnotationExportLabels(
        author = { "By $it" }, summary = { "$it annotations" }, page = { "Page $it" },
        date = { it }, quote = "Quote", note = "Note", color = { "Color $it" },
    )

    @Test fun filteringCombinesColorWithCaseInsensitiveNoteOrQuoteMatches() {
        val quote = item("quote", quote = "A Needle", color = 1)
        val note = item("note", note = "NEEDLE in a note", color = 1)
        val otherColor = item("other", quote = "needle", color = 2)
        val noMatch = item("absent", quote = "haystack", color = 1)
        val items = listOf(quote, note, otherColor, noMatch)
        assertEquals(listOf(quote, note), filterAnnotations(items, "needle", 1))
        assertEquals(listOf(quote, note, otherColor), filterAnnotations(items, "needle", null))
        assertEquals(listOf(otherColor), filterAnnotations(items, "  ", 2))
        assertEquals(items, filterAnnotations(items, "", null))
        assertEquals(emptyList<SweptHighlight>(), filterAnnotations(items, "missing", null))
    }

    @Test fun datesSortNewestFirstWithMissingDatesLastAndStableTies() {
        val oldest = item("old", "D:20230101000000")
        val newest = item("new", "D:20240101000000")
        val tied = item("tie", newest.creationDate)
        val undated = item("undated")
        assertEquals(listOf(newest, tied, oldest, undated), listOf(oldest, undated, newest, tied).sortedWith(annotationComparator))
    }

    @Ignore("Pending ruling: raw prefixes determine date order, expected descending parsed instants")
    @Test fun mixedPdfDatePrefixesSortByParsedInstant() {
        val newer = item("newer", "20240101000000Z")
        val older = item("older", "D:20230101000000Z")
        val instants = mapOf(newer.groupKey to OffsetDateTime.parse("2024-01-01T00:00:00Z").toInstant(),
            older.groupKey to OffsetDateTime.parse("2023-01-01T00:00:00Z").toInstant())
        val items = listOf(older, newer)
        assertEquals(items.sortedByDescending { instants.getValue(it.groupKey) }, items.sortedWith(annotationComparator))
    }

    @Ignore("Pending ruling: raw wall times ignore PDF offsets, expected descending parsed instants")
    @Test fun pdfDateOffsetsDetermineChronologicalOrder() {
        val earlier = item("earlier", "D:20240101120000+02'00'")
        val later = item("later", "D:20240101113000Z")
        val instants = mapOf(earlier.groupKey to OffsetDateTime.parse("2024-01-01T12:00:00+02:00").toInstant(),
            later.groupKey to OffsetDateTime.parse("2024-01-01T11:30:00Z").toInstant())
        val items = listOf(earlier, later)
        assertEquals(items.sortedByDescending { instants.getValue(it.groupKey) }, items.sortedWith(annotationComparator))
    }

    @Test fun exportOrdersPagePathDateAndColorAndTrimsOnlyTheQuote() {
        val value = item("entry", "2024-01-01", "  quoted  ", " note ", 7).copy(pageIndex = 2)
        val expected = "Book\nBy Author\n1 annotations\n\n----------------------------------------\n" +
            "Page 3 | Chapter ▶ Section | 2024-01-01 | Color 7\n" +
            "--- Quote Start\nquoted\n--- Quote End\n--- Note Start\n note \n--- Note End\n"
        assertEquals(expected, buildAnnotationExport("Book", "Author", listOf(value), { "Chapter ▶ Section" }, labels, false, false))
    }

    @Test fun blankQuotesAndOptionalNotesAreOmitted() {
        val value = item("entry", quote = "  ", note = " ")
        val expected = "Book\n1 annotations\n\n----------------------------------------\nPage 1 | Color 1\n"
        assertEquals(expected, buildAnnotationExport("Book", "  ", listOf(value), { null }, labels, false, false))
        assertEquals(expected + "--- Note Start\n \n--- Note End\n",
            buildAnnotationExport("Book", null, listOf(value), { null }, labels, false, true))
    }

    @Test fun exportUsesAllHighlightsWhileAFilterIsActiveInCurrentBehavior() {
        val visible = item("visible", quote = "needle", color = 1)
        val hidden = item("hidden", quote = "other", color = 2)
        val all = listOf(visible, hidden)
        assertEquals(listOf(visible), filterAnnotations(all, "needle", 1))
        val text = buildAnnotationExport("Book", null, all, { null }, labels, false, false)
        assertEquals(listOf("needle", "other"), Regex("--- Quote Start\n(.*?)\n--- Quote End").findAll(text).map { it.groupValues[1] }.toList())
        assertEquals("2 annotations", text.lines()[1])
    }

    @Test fun exportCanSortAllItemsOrPreserveTheirPageOrder() {
        val old = item("old", "20230101", quote = "old")
        val recent = item("recent", "20240101", quote = "recent")
        fun quotes(sorted: Boolean): List<String> {
            val text = buildAnnotationExport("Book", null, listOf(old, recent), { null }, labels, sorted, false)
            return Regex("--- Quote Start\n(.*?)\n--- Quote End").findAll(text).map { it.groupValues[1] }.toList()
        }
        assertEquals(listOf("old", "recent"), quotes(false))
        assertEquals(listOf("recent", "old"), quotes(true))
    }

    @Test fun paletteNamesRequireAnExactArgbMatchAndOtherwiseUseRgbHex() {
        val yellow = HighlightPalette.YELLOW.colorValue
        assertEquals("Yellow", annotationColorLabel(yellow) { "Yellow" })
        assertEquals("#FFF176", annotationColorLabel(yellow and 0x00FFFFFF) { "unexpected palette" })
        assertEquals("#001234", annotationColorLabel(0x80001234.toInt()) { "unexpected palette" })
    }
}
