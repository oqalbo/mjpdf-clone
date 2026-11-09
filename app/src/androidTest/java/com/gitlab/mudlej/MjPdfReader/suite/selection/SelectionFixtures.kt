// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object SelectionFixtures {

    const val LATIN = "selection_latin.pdf"
    const val ARABIC = "selection_arabic.pdf"
    const val URL = "selection_url.pdf"
    const val LONG_QUOTE = "selection_long_quote.pdf"
    const val SCANNED = "scanned.pdf"

    const val PAGE_WIDTH_POINTS = 612f
    const val PAGE_HEIGHT_POINTS = 792f

    const val LATIN_TITLE = "Selection Fixture"
    const val LATIN_AUTHOR = "M. Fixture"

    val LATIN_LINES = listOf(
        "The quick brown fox jumps over the lazy dog.",
        "Selection, punctuation; and symbols: kept intact.",
        "The word الكتاب means the book in Arabic.",
        "The council met that spring to plan a truly inter-",
        "national gathering, and argued for a week about the date.",
        "The date they settled on was the one nobody had asked for.",
        "A short line.",
        "Two further lines of ordinary body text follow it here, so that a drag",
        "can begin on one of them and finish somewhere on the other one.",
    )

    val LATIN_PAGE_2_LINES = listOf(
        "The second page carries nothing the first page carries, so a drag",
        "that runs off the foot of page one has somewhere different to land.",
        "Ships, weather, oil and rope: the whole of the keeper's vocabulary.",
    )

    const val ARABIC_VOCALISED_WORD = "الْكِتَابُ"

    val ARABIC_FIRST_LINE_WORDS = listOf("قرأ", "الطالب", ARABIC_VOCALISED_WORD, "في", "المكتبة", "الكبيرة", "أمس")

    const val URL_BARE_HOST = "www.example.com/docs"

    fun place(name: String, targetDirectory: File): File =
        Fixtures.copyDeviceFixture(name, targetDirectory)
}
