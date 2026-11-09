// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object SearchFixtures {

    const val TEXT = "search_text.pdf"
    const val ACCENTS = "search_accents.pdf"
    const val DIGITS = "search_digits.pdf"
    const val LIGATURES = "ligatures.pdf"
    const val ARABIC = "arabic-rtl.pdf"
    const val ARABIC_LIGATURE = "arabic-ligature.pdf"
    const val LARGE = "large.pdf"
    const val SCANNED = "scanned.pdf"
    const val PROTECTED = "protected.pdf"

    const val TEXT_PAGES = 12
    const val ARABIC_PAGES = 12
    const val LARGE_PAGES = 500
    const val SCANNED_PAGES = 8
    const val PROTECTED_PAGES = 6

    const val PROTECTED_PASSWORD = "mjpdf"

    const val BEACON = "beacon"
    const val BEACON_HITS = 6
    val BEACON_PAGE_NUMBERS = listOf(4, 4, 7, 7, 11, 11)
    val BEACON_PAGE_INDEXES = BEACON_PAGE_NUMBERS.map { it - 1 }

    const val KALLIOPE = "kalliope"
    const val KALLIOPE_PAGE_INDEX = 8

    const val CHAPTER = "chapter"
    const val CHAPTER_UPPERCASE = "CHAPTER"
    const val CHAPTER_HITS = 3
    val CHAPTER_PAGE_NUMBERS = listOf(2, 5, 8)

    const val ZEPHYRUS = "zephyrus"

    const val QUICK = "quick"
    const val QUICK_BROWN = "quick brown"
    val QUICK_PAGE_NUMBERS = listOf(1, 3)
    const val QUICK_BROWN_PAGE_NUMBER = 1

    const val HYPHEN = "hyphen"
    val HYPHEN_PAGE_NUMBERS = listOf(6, 10)
    const val HYPHENATION = "hyphenation"
    const val HYPHENATION_HITS = 2
    val HYPHENATION_PAGE_NUMBERS = listOf(6, 10)
    const val HYPHENATION_GROUND_TRUTH =
        "extraction drops the soft hyphen marker, so page 6 reads the word whole"

    const val LITERAL_PAGE_NUMBER = 12
    val LITERAL_SPECIMENS = listOf("a.b", "a*b", "(group)", "x+y", "[bracket]")
    const val PLAIN_AB = "ab"

    const val FILTER_WORD = "obsidian"
    const val FILTER_REMAINING_ROWS = 2
    const val FILTER_REMAINING_PAGE_NUMBER = 11

    const val LOW_HIT_INDEX = 1
    const val HIGH_HIT_INDEX = 0

    const val CAFE_PLAIN = "cafe"
    const val CAFE_ACCENTED = "café"
    const val CAFE_PAGE_NUMBER = 1
    const val CESTINA_PLAIN = "cestina"
    const val CESTINA_PAGE_NUMBER = 2
    const val ZURICH_PLAIN = "zurich"
    const val ZURICH_PAGE_NUMBER = 2

    const val DIGITS_GROUND_TRUTH =
        "extracted page text is NFKC folded, so the fullwidth occurrence also matches"

    const val ASCII_DIGITS = "12345"
    const val FULLWIDTH_DIGITS = "１２３４５"
    const val ARABIC_INDIC_DIGITS = "١٢٣٤٥"

    const val FFI_LIGATURE_WORD = "office"
    const val FI_LIGATURE_WORD = "final"

    const val KITAB = "كتاب"
    const val KITAB_HITS = 3
    val KITAB_PAGE_INDEXES = listOf(2, 5, 7)
    const val ARABIC_SHAPING_PAGE_INDEX = 2

    const val KITAB_GROUND_TRUTH = "3 literal occurrences on page indexes 2, 5 and 7, the tatweel and " +
        "harakat forms do not match with the setting off"

    const val LARGE_COMMON_WORD = "the"

    const val PROTECTED_TOKEN = "negotiation"
    const val PROTECTED_TOKEN_HITS = 18
    const val PROTECTED_TOKEN_GROUND_TRUTH = "three occurrences per page over six pages"

    const val SHORT_QUERY = "be"
    const val LONG_QUERY_LENGTH = 2000

    fun place(name: String, targetDirectory: File): File =
        Fixtures.copyDeviceFixture(name, targetDirectory)

    fun longQuery(): String = "a".repeat(LONG_QUERY_LENGTH)
}
