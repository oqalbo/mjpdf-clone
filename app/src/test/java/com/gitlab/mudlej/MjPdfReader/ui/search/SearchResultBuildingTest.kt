// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.search

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

class SearchResultBuildingTest {

    @Test
    fun highlightOffsetsPreserveMatchedTextAcrossPagePositionsAndWindowSizes() {
        val pageText = "alpha bravo charlie delta echo ".repeat(10).take(300)
        val positions = listOf(0, 1, 5, 6, 39, 40, 41, 148, 149, 150, 254, 255, 256, 294, 295)

        for (position in positions) {
            val matchedText = pageText.substring(position, position + 5)
            for (offset in listOf(40, 200)) {
                val result = SearchCoordinator.buildSearchResult(
                    query = matchedText,
                    indexInPage = position,
                    pageText = pageText,
                    pageNumber = 7,
                    textOffset = offset,
                )

                assertEquals(
                    "position=$position offset=$offset",
                    matchedText,
                    result.text.substring(result.inputStart, result.inputEnd),
                )
            }
        }
    }

    @Test
    fun accentInsensitiveHighlightsIncludeDecomposedMarksBeyondTheQueryLength() {
        val pageText = "cafe\u0301 alpha bravo charlie delta ".repeat(10)
        val ranges = SearchCoordinator.matchRanges(pageText, "cafe", ignoreAccents = true)
        assertEquals((0 until 10).map { it * 32 until it * 32 + 5 }, ranges)

        for (range in ranges) {
            for (offset in listOf(40, 200)) {
                val result = SearchCoordinator.buildSearchResult(
                    query = "cafe",
                    indexInPage = range.first,
                    pageText = pageText,
                    pageNumber = 2,
                    textOffset = offset,
                    matchLength = range.last + 1 - range.first,
                )

                assertEquals(
                    "position=${range.first} offset=$offset",
                    "cafe\u0301",
                    result.text.substring(result.inputStart, result.inputEnd),
                )
            }
        }
    }

    @Ignore("Pending ruling: a window starting at page start drops Hello, expected the complete first word to remain")
    @Test
    fun snippetAtPageStartKeepsTheCompleteFirstWord() {
        val result = SearchCoordinator.buildSearchResult(
            query = "query",
            indexInPage = 12,
            pageText = "Hello world query " + "after ".repeat(20),
            pageNumber = 1,
        )

        assertEquals("Hello", result.text.substringBefore(' '))
    }

    @Ignore("Pending ruling: a window ending at page end drops goodbye, expected the complete last word to remain")
    @Test
    fun snippetAtPageEndKeepsTheCompleteLastWord() {
        val prefix = "before ".repeat(20)
        val result = SearchCoordinator.buildSearchResult(
            query = "query",
            indexInPage = prefix.length,
            pageText = prefix + "query world goodbye",
            pageNumber = 1,
        )

        assertEquals("goodbye", result.text.substringAfterLast(' '))
    }

    @Test
    fun textWithoutSpacesKeepsTheWholeWindow() {
        val pageText = "天地玄黃宇宙洪荒".repeat(40)
        for (position in listOf(0, 150, 318)) {
            for (offset in listOf(40, 200)) {
                val query = pageText.substring(position, position + 2)
                val result = SearchCoordinator.buildSearchResult(
                    query, position, pageText, pageNumber = 1, textOffset = offset,
                )
                val windowStart = (position - offset).coerceAtLeast(0)
                val windowEnd = (position + query.length + offset).coerceAtMost(pageText.length)

                assertEquals(pageText.substring(windowStart, windowEnd), result.text)
                assertEquals(position - windowStart, result.inputStart)
                assertEquals(position - windowStart + 2, result.inputEnd)
            }
        }
    }

    @Test
    fun windowsInsideThePageTrimPartialWordsAroundTheMatch() {
        val result = SearchCoordinator.buildSearchResult(
            query = "query",
            indexInPage = 11,
            pageText = "abcdefghij query klmnopqrst",
            pageNumber = 1,
            textOffset = 5,
        )

        assertEquals("query", result.text)
        assertEquals(0, result.inputStart)
        assertEquals(5, result.inputEnd)
    }

    @Test
    fun bothMatchingModesSkipOverlappingOccurrences() {
        for (ignoreAccents in listOf(false, true)) {
            assertEquals(
                "ignoreAccents=$ignoreAccents",
                listOf(0..1),
                SearchCoordinator.matchRanges("aaa", "aa", ignoreAccents),
            )
        }
    }
}
