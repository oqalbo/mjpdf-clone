// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import org.junit.Assert.assertEquals
import org.junit.Test

class TextModeTextFormatterJoinRulesTest {

    @Test
    fun lineAtSeventyPercentFullnessJoins() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyzab", "next", "abcdefghijklmnopqrstuvwxyzab next")
    }

    @Test
    fun lineJustBelowSeventyPercentFullnessKeepsItsBreak() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyza", "next", "abcdefghijklmnopqrstuvwxyza\nnext")
    }

    @Test
    fun letterHyphenationAllowsJoiningBelowSeventyPercentFullness() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyz‐", "next", "abcdefghijklmnopqrstuvwxyznext")
    }

    @Test
    fun periodAtEightyFivePercentFullnessJoinsIntoLowercase() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyzabcdefg.", "next", "abcdefghijklmnopqrstuvwxyzabcdefg. next")
    }

    @Test
    fun periodJustBelowEightyFivePercentFullnessKeepsItsBreak() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyzabcdef.", "next", "abcdefghijklmnopqrstuvwxyzabcdef.\nnext")
    }

    @Test
    fun periodAtEightyFivePercentFullnessDoesNotJoinIntoUppercaseOrDigits() {
        for (next in listOf("Next", "7 words")) {
            assertReflowPair("abcdefghijklmnopqrstuvwxyzabcdefg.", next, "abcdefghijklmnopqrstuvwxyzabcdefg.\n$next")
        }
    }

    @Test
    fun otherSentenceTerminalsAlwaysKeepTheirBreaks() {
        for (terminal in listOf("!", "?", "؟", "۔", "。", "…", ":", ";", "؛")) {
            val previous = "abcdefghijklmnopqrstuvwxyzabcdefghijklm$terminal"
            assertReflowPair(previous, "next", "$previous\nnext")
        }
    }

    @Test
    fun aSingleCloserAfterAPeriodKeepsTheSentenceBreak() {
        for (previous in listOf("abcdefghijklmnopqrstuvwxyzabcdefend.\"", "abcdefghijklmnopqrstuvwxyzabcdefend.)")) {
            assertReflowPair(previous, "next", "$previous\nnext")
        }
    }

    @Test
    fun twoClosersHideTheTerminalAndAllowJoining() {
        assertReflowPair(
            "abcdefghijklmnopqrstuvwxyzabcdefend.)\"",
            "next",
            "abcdefghijklmnopqrstuvwxyzabcdefend.)\" next",
        )
    }

    @Test
    fun listStartsKeepTheirOwnLines() {
        for (next in listOf("• item", "- item", "– item", "1) item", "(12) item", "a. item", "iv. item", "١. item")) {
            assertReflowPair("abcdefghijklmnopqrstuvwxyzab", next, "abcdefghijklmnopqrstuvwxyzab\n$next")
        }
    }

    @Test
    fun arabicIndicParenthesizedListStartNeedsNoSpace() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyzab", "١)item", "abcdefghijklmnopqrstuvwxyzab\n١)item")
    }

    @Test
    fun allCapsLinesThroughSixtyCharactersKeepTheirBreaks() {
        for (previous in listOf("ABCDEFGHIJKLMNOPQRSTUVWXYZAB", "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGH")) {
            assertReflowPair(previous, "next", "$previous\nnext")
        }
    }

    @Test
    fun allCapsLineOfSixtyOneCharactersIsNotAHeading() {
        assertReflowPair(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHI",
            "next",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHI next",
        )
    }

    @Test
    fun arabicLineWithoutCasedLettersIsNotAHeading() {
        assertReflowPair("هذا نص عربي طويل يكمل السطر هنا", "next", "هذا نص عربي طويل يكمل السطر هنا next")
    }

    @Test
    fun tabInEitherFragmentPreventsJoining() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyz\tab", "next", "abcdefghijklmnopqrstuvwxyz\tab\nnext")
        assertReflowPair("abcdefghijklmnopqrstuvwxyzab", "next\tword", "abcdefghijklmnopqrstuvwxyzab\nnext\tword")
    }

    @Test
    fun threeOrMoreInternalSpacesInEitherFragmentPreventJoining() {
        for (spaces in listOf("   ", "    ")) {
            assertReflowPair("abcdefghijklmnopqrstuvwxyz${spaces}ab", "next", "abcdefghijklmnopqrstuvwxyz${spaces}ab\nnext")
            assertReflowPair("abcdefghijklmnopqrstuvwxyzab", "next${spaces}word", "abcdefghijklmnopqrstuvwxyzab\nnext${spaces}word")
        }
    }

    @Test
    fun twoInternalSpacesStillAllowJoining() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyz  ab", "next", "abcdefghijklmnopqrstuvwxyz  ab next")
        assertReflowPair("abcdefghijklmnopqrstuvwxyzab", "next  word", "abcdefghijklmnopqrstuvwxyzab next  word")
    }

    @Test
    fun twoLeadingSpacesPreserveAnIndentedLine() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyzab", "  next", "abcdefghijklmnopqrstuvwxyzab\n  next")
    }

    @Test
    fun oneLeadingSpaceDoesNotCountAsAnIndent() {
        assertReflowPair("abcdefghijklmnopqrstuvwxyzab", " next", "abcdefghijklmnopqrstuvwxyzab next")
    }

    @Test
    fun softHyphenIsRemovedWithoutInsertingASpace() {
        assertEquals("example", TextModeTextFormatter.joinFragments("exam\u00AD", "ple"))
        assertEquals("JeanPaul", TextModeTextFormatter.joinFragments("Jean\u00AD", "Paul"))
    }

    @Test
    fun letterHyphensBeforeLowercaseAreRemovedEvenFromCompoundWords() {
        assertEquals("wellknown", TextModeTextFormatter.joinFragments("well-", "known"))
        assertEquals("wellknown", TextModeTextFormatter.joinFragments("well‐", "known"))
    }

    @Test
    fun letterHyphensBeforeUppercaseAreKeptWithoutASpace() {
        assertEquals("Jean-Paul", TextModeTextFormatter.joinFragments("Jean-", "Paul"))
        assertEquals("Jean‐Paul", TextModeTextFormatter.joinFragments("Jean‐", "Paul"))
    }

    @Test
    fun adjacentCjkFragmentsJoinWithoutASpace() {
        assertEquals("中文", TextModeTextFormatter.joinFragments("中", "文"))
    }

    @Test
    fun otherFragmentBoundariesInsertOneSpace() {
        val cases = listOf(
            Triple("hello", "world", "hello world"),
            Triple("3-", "4", "3- 4"),
            Triple("word-", "4", "word- 4"),
            Triple("3-", "word", "3- word"),
            Triple("中", "word", "中 word"),
            Triple("word", "文", "word 文"),
        )
        for ((first, second, expected) in cases) {
            assertEquals("$first / $second", expected, TextModeTextFormatter.joinFragments(first, second))
        }
    }

    @Test
    fun blankLineResetsTheJoinChainAndPreservesTheParagraphBreak() {
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzab abcdefghijklmnopqrstuvwxyzab\n\nmore\nThis anchor line has forty characters!!!\nThis anchor line has forty characters!!!",
            TextModeTextFormatter.formatJoined(
                "abcdefghijklmnopqrstuvwxyzab\nabcdefghijklmnopqrstuvwxyzab\n\nmore\nThis anchor line has forty characters!!!\nThis anchor line has forty characters!!!",
            ),
        )
    }

    @Test
    fun mixedDocumentKeepsParagraphsListsHeadingsCodeAndIndentsDistinct() {
        val input = "This opening line continues into the next\n" +
            "the same paragraph ends here.\n" +
            "\n" +
            "CHAPTER TWO\n" +
            "A second paragraph continues into a list\n" +
            "• first item\n" +
            "  An indented paragraph stays separate.\n" +
            "column   value\n" +
            "A final paragraph continues with enough\n" +
            "more words to complete this paragraph.\n" +
            "\n" +
            "Closing words."
        val expected = "This opening line continues into the next the same paragraph ends here.\n\n" +
            "CHAPTER TWO\n" +
            "A second paragraph continues into a list\n" +
            "• first item\n" +
            "  An indented paragraph stays separate.\n" +
            "column   value\n" +
            "A final paragraph continues with enough more words to complete this paragraph.\n\n" +
            "Closing words."
        assertEquals(expected, TextModeTextFormatter.formatJoined(input))
    }

    private fun assertReflowPair(previous: String, next: String, expected: String) {
        val anchors = "\nThis anchor line has forty characters!!!\nThis anchor line has forty characters!!!"
        assertEquals(
            "$previous / $next",
            expected + anchors,
            TextModeTextFormatter.formatJoined("$previous\n$next$anchors"),
        )
    }
}
