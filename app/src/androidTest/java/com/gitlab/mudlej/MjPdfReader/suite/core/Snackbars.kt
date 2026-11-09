// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.widget.TextView

object Snackbars {

    const val ABSENCE_WINDOW_MS = 1_500L

    fun currentText(): String? = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity() ?: return@onMainThread null
        val label = activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        if (label == null || !label.isShown) null else label.text.toString()
    }

    fun awaitText(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "a snackbar reading \"$expected\" is shown",
            timeoutMs,
            read = { currentText() },
        ) { it == expected }
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the snackbar leaves the screen",
            timeoutMs,
            read = { currentText() },
        ) { it == null }
    }

    fun assertNoneWithin(windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no snackbar is shown",
            windowMs = windowMs,
        ) { currentText() == null }
    }
}
