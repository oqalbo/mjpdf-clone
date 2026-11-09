// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

object BodyTextTargets {

    const val PAGE_HEIGHT_POINTS = 792f
    const val ABOVE_BASELINE_POINTS = 4f

    const val NORMAL_TOP_BASELINE = 702f
    const val NORMAL_LEADING = 15f
    const val NORMAL_FULL_LINES = 13

    const val LARGE_TOP_BASELINE = 712f
    const val LARGE_LEADING = 9.2f
    const val LARGE_FULL_LINES = 7

    const val FIRST_WORD_X = 0.30f
    const val WORD_STEP_X = 0.06f
    const val WORD_COLUMNS = 4

    fun horizontalFraction(index: Int): Float =
        FIRST_WORD_X + (index % WORD_COLUMNS) * WORD_STEP_X

    fun normalLine(index: Int): Float =
        fractionOfBaseline(NORMAL_TOP_BASELINE - NORMAL_LEADING * (index % NORMAL_FULL_LINES))

    fun largeLine(index: Int): Float =
        fractionOfBaseline(LARGE_TOP_BASELINE - LARGE_LEADING * (index % LARGE_FULL_LINES))

    private fun fractionOfBaseline(baseline: Float): Float =
        (PAGE_HEIGHT_POINTS - (baseline + ABOVE_BASELINE_POINTS)) / PAGE_HEIGHT_POINTS
}
