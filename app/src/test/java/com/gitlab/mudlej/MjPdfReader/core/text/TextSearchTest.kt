// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSearchTest {

    @Test
    fun composedAccentsMatchTheirBareLetters() {
        assertEquals(listOf(0..3), "café".accentInsensitiveRanges("cafe"))
    }

    @Test
    fun decomposedAccentsAreIncludedInTheMatchedRange() {
        assertEquals(listOf(0..4), "cafe\u0301!".accentInsensitiveRanges("cafe"))
    }

    @Test
    fun aMatchInsideALigatureCoversTheOriginalCharacter() {
        assertEquals(listOf(0..0), "ﬁle".accentInsensitiveRanges("i"))
    }

    @Test
    fun arabicMatchesIgnoreTatweelAndHarakat() {
        assertEquals(listOf(0..7), "كِـتَابٌ!".accentInsensitiveRanges("كتاب"))
    }

    @Test
    fun aPatternMadeOnlyOfCombiningMarksHasNoMatches() {
        assertEquals(emptyList<IntRange>(), "cafe\u0301".accentInsensitiveRanges("\u0301\u0308"))
    }

    @Test
    fun repeatedMatchesDoNotOverlap() {
        assertEquals(listOf(0..1), "aaa".accentInsensitiveRanges("aa"))
        assertEquals(listOf(0), "aaa".indexesOf("aa"))
    }

    @Test
    fun plainSearchIgnoresTheCaseOfAccentedLetters() {
        assertEquals(listOf(0), "ÉCOLE".indexesOf("école"))
    }
}
