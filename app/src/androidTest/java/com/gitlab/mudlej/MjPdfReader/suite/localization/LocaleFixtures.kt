// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object LocaleFixtures {

    const val NORMAL = "normal.pdf"
    const val LARGE = "large.pdf"
    const val ARABIC = "arabic-rtl.pdf"
    const val ARABIC_LIGATURE = "arabic-ligature.pdf"
    const val PERSIAN_DIGITS = "persian-digits.pdf"
    const val CYRILLIC_CASE = "cyrillic-case.pdf"
    const val TURKISH_CASE = "turkish-case.pdf"
    const val SIZE_FIXTURE = "size-fixture.pdf"
    const val HIGHLIGHTS_RICH = "highlights-rich.pdf"

    const val LONG_TITLE =
        "a-document-whose-name-runs-well-past-any-sensible-width-for-a-home-screen-grid-cell-long-title.pdf"

    const val NORMAL_PAGES = 20
    const val LARGE_PAGES = 500
    const val ARABIC_PAGES = 12
    const val SIZE_FIXTURE_PAGES = 1
    const val SIZE_FIXTURE_BYTES = 2_411_725L
    const val LONG_TITLE_NAME_CHARACTERS = 98

    const val LIGATURE_SEARCH_TERM = "سلام"
    const val HARAKAT_QUERY = "الكتاب"
    const val HARAKAT_WORD_LEFT_EDGE_POINTS = 491.71f
    const val HARAKAT_WORD_RIGHT_EDGE_POINTS = 540f
    const val HARAKAT_WORD_BOTTOM_POINTS = 595.75f
    const val HARAKAT_WORD_TOP_POINTS = 616.71f

    const val LIGATURE_TERM_LEFT_POINTS = 503
    const val LIGATURE_TERM_RIGHT_POINTS = 540
    const val LIGATURE_TERM_TOP_POINTS = 667
    const val LIGATURE_TERM_BOTTOM_POINTS = 646

    const val PERSIAN_DIGIT_STRING = "۱۴۰۳"
    const val ARABIC_INDIC_STRING = "٤٢"

    const val CYRILLIC_LOWERCASE = "привет"
    const val CYRILLIC_CAPITALISED = "Привет"

    const val TURKISH_DOTTED_QUERY = "istanbul"
    const val TURKISH_CAPITALS_QUERY = "isparta"

    const val MIXED_LATIN_LEADING_PASSAGE =
        "The manuscript uses the word الكتاب for a bound volume throughout."
    const val MIXED_ARABIC_LEADING_PASSAGE = "PDF: الكتاب المفتوح على الطاولة يذكرني بالمكتبة"
    const val ARABIC_ONLY_PASSAGE = "الكتاب المفتوح على الطاولة يذكرني بالمكتبة الكبيرة"
    const val DIGIT_LEADING_ARABIC_PASSAGE = "2026 الكتاب المفتوح على الطاولة يذكرني بالمكتبة"
    const val NUMERIC_ONLY_PASSAGE = "2026 - 1447 (12.5%)"

    val ARABIC_INDIC_DIGITS = '٠'..'٩'
    val PERSIAN_DIGITS_RANGE = '۰'..'۹'

    fun place(name: String, targetDirectory: File): File =
        Fixtures.copyDeviceFixture(name, targetDirectory)

    fun seeded(name: String): File = File(Fixtures.seededRoot, name)

    fun containsArabicIndicDigit(value: String): Boolean = value.any { it in ARABIC_INDIC_DIGITS }

    fun containsPersianDigit(value: String): Boolean = value.any { it in PERSIAN_DIGITS_RANGE }

    fun asciiDigitsOf(value: String): String =
        value.filter { Character.digit(it, 10) >= 0 }
            .map { Character.digit(it, 10).toString() }
            .joinToString(separator = "")
}
