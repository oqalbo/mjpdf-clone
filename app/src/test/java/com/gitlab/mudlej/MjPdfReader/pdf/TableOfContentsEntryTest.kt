// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import com.shockwave.pdfium.PdfDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableOfContentsEntryTest {

    @Test
    fun nestedPathsAndLevelsFollowSiblingPositions() {
        val bookmarks = listOf(
            bookmark("First", 1, bookmark("Introduction", 2), bookmark("Scope", 3)),
            bookmark("Second", 4, bookmark("Details", 5,
                bookmark("Alpha", 6), bookmark("Beta", 7), bookmark("Gamma", 8))),
        )
        val entries = bookmarks.mapIndexed { index, bookmark ->
            TableOfContentsEntry(bookmark, 0, index.toString())
        }

        assertEquals("0", entries[0].path)
        assertEquals(0, entries[0].level)
        assertEquals("0.1", entries[0].subEntries[1].path)
        assertEquals(1, entries[0].subEntries[1].level)
        assertEquals("1.0.2", entries[1].subEntries[0].subEntries[2].path)
        assertEquals(2, entries[1].subEntries[0].subEntries[2].level)
    }

    @Test
    fun titlesAndPagesKeepTheirSiblingOrder() {
        val entry = TableOfContentsEntry(bookmark("Chapter", 10,
            bookmark("Zulu", 30), bookmark("Alpha", 20), bookmark("Middle", 40)), 0, "0")

        assertEquals("Chapter", entry.title)
        assertEquals(10L, entry.pageIdx)
        assertEquals(listOf("Zulu", "Alpha", "Middle"), entry.subEntries.map { it.title })
        assertEquals(listOf(30L, 20L, 40L), entry.subEntries.map { it.pageIdx })
        assertTrue(entry.hasSubEntries())
    }

    @Test
    fun aLeafHasNoSubEntries() {
        val entry = TableOfContentsEntry(bookmark("Leaf", 42), 2, "1.0.2")

        assertEquals(emptyList<TableOfContentsEntry>(), entry.subEntries)
        assertFalse(entry.hasSubEntries())
    }

    private fun bookmark(
        title: String,
        page: Long,
        vararg children: PdfDocument.Bookmark,
    ): PdfDocument.Bookmark = PdfDocument.Bookmark().apply {
        this.title = title
        pageIdx = page
        setChildren(children.toList())
    }
}
