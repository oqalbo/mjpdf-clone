// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import org.junit.Assert.*
import org.junit.Test
import java.text.BreakIterator
import java.util.Locale

class FilenameShorteningTest {
    private fun boundaries(text: String): IntArray {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val result = mutableListOf<Int>()
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            result.add(boundary)
            boundary = iterator.next()
        }
        return result.toIntArray()
    }

    private fun shorten(text: String, middle: Boolean, limit: Int) =
        shortenFilename(text, boundaries(text), middle) { it.length <= limit }

    @Test fun middlePlacesTheExtraKeptCharacterBeforeTheEllipsis() {
        assertEquals("ab…h", shorten("abcdefgh", true, 4))
        assertEquals("ab…gh", shorten("abcdefgh", true, 5))
    }

    @Test fun startKeepsOnlyTheLongestSuffixThatFits() {
        assertEquals("…fgh", shorten("abcdefgh", false, 4))
    }

    @Test fun everyLengthBudgetKeepsTheLongestCandidate() {
        val text = "abcdefghij"
        for (middle in listOf(false, true)) {
            for (limit in 1 until text.length) {
                val result = shorten(text, middle, limit)
                val kept = limit - 1
                val prefix = if (middle) (kept + 1) / 2 else 0
                assertEquals(text.take(prefix) + "…" + text.takeLast(kept - prefix), result)
                assertEquals(limit, result.length)
            }
        }
    }

    @Test fun arabicMarksAndEmojiRemainWholeAtBothCutBoundaries() {
        val text = "بَ😀تُ😀جِ"
        assertEquals("بَ…جِ", shorten(text, true, 5))
        assertEquals("بَ😀…جِ", shorten(text, true, 7))
        assertEquals("…😀جِ", shorten(text, false, 5))
        for (middle in listOf(false, true)) {
            for (limit in 1 until text.length) {
                val result = shorten(text, middle, limit)
                val parts = result.split("…")
                val edges = boundaries(text).toSet()
                assertTrue(edges.contains(parts.first().length))
                assertTrue(edges.contains(text.length - parts.last().length))
            }
        }
    }

    @Test fun unfitSingleCharacterAndEmptyInputBothBecomeTheEllipsis() {
        assertEquals("…", shorten("a", true, 0))
        assertEquals("…", shorten("", true, 0))
        assertEquals("…", shorten("", false, 0))
    }
}
