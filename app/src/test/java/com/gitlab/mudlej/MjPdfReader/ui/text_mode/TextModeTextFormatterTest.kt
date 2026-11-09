// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import org.junit.Assert.assertEquals
import org.junit.Test

class TextModeTextFormatterTest {

    @Test
    fun letterHyphenationAcrossLinesIsRemoved() {
        assertEquals("example", TextModeTextFormatter.format("exam-\nple"))
    }

    @Test
    fun hyphenationRequiresLettersOnBothSides() {
        for (input in listOf("3-\n4", "word-\n4", "3-\nword")) {
            assertEquals(input, input, TextModeTextFormatter.format(input))
        }
    }

    @Test
    fun carriageReturnsBecomeLineFeeds() {
        assertEquals("one\ntwo\nthree", TextModeTextFormatter.format("one\r\ntwo\rthree"))
    }

    @Test
    fun spacesAndTabsBeforeLineBreaksAreDropped() {
        assertEquals("one\ntwo\nthree", TextModeTextFormatter.format("one \t\ntwo\t \nthree"))
    }

    @Test
    fun excessNewlinesCollapseToOneParagraphBreak() {
        assertEquals("one\n\ntwo\n\nthree", TextModeTextFormatter.format("one\n\n\ntwo\n\n\n\nthree"))
    }

    @Test
    fun outerWhitespaceIsTrimmed() {
        assertEquals("one two", TextModeTextFormatter.format(" \t\none two\n\t "))
    }

    @Test
    fun whitespaceOnlyInputBecomesEmpty() {
        assertEquals("", TextModeTextFormatter.format(" \t\r\n\r "))
        assertEquals("", TextModeTextFormatter.formatJoined(" \t\r\n\r "))
    }

    @Test
    fun fewerThanFourNonEmptyLinesKeepTheirBreaks() {
        val cases = listOf(
            " \tA long opening line that could otherwise join \r\n" to
                "A long opening line that could otherwise join",
            "A long opening line that could otherwise join \t\r\nnext" to
                "A long opening line that could otherwise join\nnext",
            "A long opening line that could otherwise join\r\n\r\n\r\nnext\rlast\t " to
                "A long opening line that could otherwise join\n\nnext\nlast",
        )
        for ((input, expected) in cases) {
            assertEquals(input, expected, TextModeTextFormatter.formatJoined(input))
        }
    }

    @Test
    fun earlyReturnStillRemovesLetterHyphenation() {
        assertEquals("example", TextModeTextFormatter.formatJoined("exam-\r\nple"))
    }

    @Test
    fun shortPoemLinesKeepTheirBreaks() {
        assertEquals(
            "rain falls\nleaves turn\nwinds rise\nnight comes",
            TextModeTextFormatter.formatJoined("rain falls\nleaves turn\nwinds rise\nnight comes"),
        )
    }

    @Test
    fun fourLinePercentileUsesTheThirdSortedLength() {
        val belowThreshold = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGH\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHI"
        val atThreshold = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHI\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJ"
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzab\nnext\n$belowThreshold",
            TextModeTextFormatter.formatJoined("abcdefghijklmnopqrstuvwxyzab\nnext\n$belowThreshold"),
        )
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzab next\n$atThreshold",
            TextModeTextFormatter.formatJoined("abcdefghijklmnopqrstuvwxyzab\nnext\n$atThreshold"),
        )
    }

    @Test
    fun fiveLinePercentileUsesTheFourthSortedLength() {
        val belowThreshold = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHI\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFG\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGH"
        val atThreshold = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJ\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGH\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHI"
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzab\nnext\n$belowThreshold",
            TextModeTextFormatter.formatJoined("abcdefghijklmnopqrstuvwxyzab\nnext\n$belowThreshold"),
        )
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzab next\n$atThreshold",
            TextModeTextFormatter.formatJoined("abcdefghijklmnopqrstuvwxyzab\nnext\n$atThreshold"),
        )
    }

    @Test
    fun tenLinePercentileUsesTheEighthSortedLength() {
        val shorterLines = "ABCDEFGHIJKLMNOPQRSTUVWXYZABC\nABCDEFGHIJKLMNOPQRSTUVWXYZABCD\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDE\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEF\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFG"
        val belowThreshold = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJ\n$shorterLines\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHI\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGH"
        val atThreshold = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJ\n$shorterLines\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHI\nABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJK"
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzab\nnext\n$belowThreshold",
            TextModeTextFormatter.formatJoined("abcdefghijklmnopqrstuvwxyzab\nnext\n$belowThreshold"),
        )
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzab next\n$atThreshold",
            TextModeTextFormatter.formatJoined("abcdefghijklmnopqrstuvwxyzab\nnext\n$atThreshold"),
        )
    }
}
