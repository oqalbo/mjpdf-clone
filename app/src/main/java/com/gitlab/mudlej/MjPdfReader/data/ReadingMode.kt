// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

const val readingModePreferenceKey = "readingMode"

enum class ReadingMode(@StringRes val labelRes: Int, @StringRes val summaryRes: Int) {
    CONTINUOUS(R.string.reading_mode_continuous, R.string.reading_mode_continuous_summary),
    DUAL_PAGE(R.string.reading_mode_dual_page, R.string.dual_page_mode_summary),
    HORIZONTAL(R.string.reading_mode_horizontal, R.string.horizontal_scrolling_summary),
    SINGLE_PAGE(R.string.reading_mode_single_page, R.string.single_page_mode_summary),
}

fun Preferences.getReadingMode(): ReadingMode = when {
    getSinglePageMode() -> ReadingMode.SINGLE_PAGE
    getHorizontalScroll() -> ReadingMode.HORIZONTAL
    getDualPageMode() -> ReadingMode.DUAL_PAGE
    else -> ReadingMode.CONTINUOUS
}

fun Preferences.setReadingMode(mode: ReadingMode) {
    setSinglePageMode(mode == ReadingMode.SINGLE_PAGE)
    setHorizontalScroll(mode == ReadingMode.HORIZONTAL)
    setDualPageMode(mode == ReadingMode.DUAL_PAGE)
}
