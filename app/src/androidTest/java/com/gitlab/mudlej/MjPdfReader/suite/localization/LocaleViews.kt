// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

data class TextMetrics(
    val text: String,
    val lineCount: Int,
    val maxLines: Int,
    val viewHeight: Int,
    val lineBottom: Int,
    val ellipsisLine: Int,
    val ellipsisStart: Int,
    val ellipsisCount: Int,
    val lineCharCount: Int,
) {
    val isEllipsized: Boolean get() = ellipsisCount > 0

    val isVerticallyClipped: Boolean get() = lineBottom > viewHeight
}

object LocaleViews {

    fun metricsOf(view: TextView): TextMetrics = TestEnvironment.onMainThread {
        val layout = view.layout
            ?: throw ConditionTimeoutException(
                "The text view reading \"${view.text}\" has no Layout yet, so it has not been measured. " +
                    "Wait for the screen to settle before reading its line metrics."
            )
        val lines = layout.lineCount
        val ellipsisLine = (0 until lines).firstOrNull { layout.getEllipsisCount(it) > 0 } ?: -1
        val lineCharCount = if (ellipsisLine >= 0) {
            layout.getLineEnd(ellipsisLine) - layout.getLineStart(ellipsisLine)
        } else {
            0
        }
        TextMetrics(
            text = view.text?.toString().orEmpty(),
            lineCount = lines,
            maxLines = view.maxLines,
            viewHeight = view.height - view.paddingTop - view.paddingBottom,
            lineBottom = if (lines == 0) 0 else layout.getLineBottom(lines - 1),
            ellipsisLine = ellipsisLine,
            ellipsisStart = if (ellipsisLine >= 0) layout.getEllipsisStart(ellipsisLine) else -1,
            ellipsisCount = if (ellipsisLine >= 0) layout.getEllipsisCount(ellipsisLine) else 0,
            lineCharCount = lineCharCount,
        )
    }

    fun textViewWithText(text: String): TextView = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed to read a text view on")
        Dialogs.firstDescendant(activity.window.decorView, TextView::class.java) {
            it.isShown && it.text?.toString() == text
        }
            ?: throw ConditionTimeoutException(
                "No visible text view on ${activity.javaClass.simpleName} carries the text \"$text\""
            )
    }

    fun preferenceSummaries(): List<TextView> = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed to read preference summaries on")
        val found = mutableListOf<TextView>()
        collectById(activity.window.decorView, android.R.id.summary, found)
        found.filter { it.isShown && !it.text.isNullOrBlank() }
    }

    fun awaitPreferenceSummaries(atLeast: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<TextView> =
        Await.untilValue(
            "the settings page shows at least $atLeast summary lines",
            timeoutMs,
            read = { runCatching { preferenceSummaries() }.getOrDefault(emptyList()) },
        ) { it.size >= atLeast }

    fun dialogText(@IdRes viewId: Int): String =
        AnnotationDialogs.capture(withId(viewId)) { view -> (view as TextView).text?.toString().orEmpty() }

    fun recordOptionsLastOpened(): String = dialogText(R.id.optionsLastOpened)

    fun recordOptionsInfo(): String = dialogText(R.id.optionsInfo)

    fun awaitRecordOptionsInfo(predicate: (String) -> Boolean, timeoutMs: Long = Await.LONG_TIMEOUT_MS): String =
        Await.untilValue(
            "the record options dialog settles on an information line",
            timeoutMs,
            read = { runCatching { recordOptionsInfo() }.getOrNull() },
        ) { it != null && predicate(it) }!!

    private fun collectById(view: View, @IdRes viewId: Int, into: MutableList<TextView>) {
        if (view is TextView && view.id == viewId) into.add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectById(view.getChildAt(index), viewId, into)
            }
        }
    }
}
