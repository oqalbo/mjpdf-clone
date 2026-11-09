// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object SearchNotices {

    const val TOO_MANY_RESULTS_THRESHOLD = PDF.TOO_MANY_RESULTS
    const val ABSENCE_WINDOW_MS = 2_000L

    fun tooManyResults(): String = string(R.string.too_many_results_may_be_slow)

    fun filteredResults(count: Int): String = string(R.string.number_of_filtered_results).format(count)

    fun unreadableDocument(): String = "Failed to read text! (file move or deleted?)"

    fun awaitTooManyResults(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Snackbars.awaitText(tooManyResults(), timeoutMs)
    }

    fun assertNoTooManyResults(windowMs: Long = ABSENCE_WINDOW_MS) {
        val expected = tooManyResults()
        Await.assertThroughout(
            condition = "no too many results snackbar is shown",
            windowMs = windowMs,
        ) { Snackbars.currentText() != expected }
    }

    fun awaitFilteredResults(count: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Snackbars.awaitText(filteredResults(count), timeoutMs)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
