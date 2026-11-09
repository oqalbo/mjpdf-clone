// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.history.NavigationHistoryActivity
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not

data class NavigationRow(
    val title: String,
    val subtitle: String,
    val path: String?,
    val pathVisible: Boolean,
    val bold: Boolean,
)

class NavigationHistoryScreen {

    fun rows(): List<NavigationRow> = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(NavigationHistoryActivity::class.java)
            ?: return@onMainThread emptyList()
        val list = activity.findViewById<RecyclerView>(R.id.navigationHistoryRecyclerView)
            ?: return@onMainThread emptyList()
        (0 until list.childCount).mapNotNull { index ->
            val child = list.getChildAt(index)
            val title = child.findViewById<TextView>(R.id.navigationHistoryTitle) ?: return@mapNotNull null
            val subtitle = child.findViewById<TextView>(R.id.navigationHistorySubtitle)
            val path = child.findViewById<TextView>(R.id.navigationHistoryTocPath)
            NavigationRow(
                title = title.text.toString(),
                subtitle = subtitle?.text?.toString().orEmpty(),
                path = path?.text?.toString(),
                pathVisible = path?.visibility == View.VISIBLE,
                bold = title.typeface?.isBold == true || title.typeface?.style == Typeface.BOLD,
            )
        }
    }

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<NavigationRow> =
        Await.untilValue(
            "the navigation history list renders $expected rows",
            timeoutMs,
            read = { rows() },
        ) { it.size == expected }

    fun awaitPathOn(title: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): NavigationRow =
        Await.untilValue(
            "the row titled \"$title\" shows its table of contents path",
            timeoutMs,
            read = { rows().firstOrNull { it.title == title } },
        ) { it != null && it.pathVisible }!!

    fun holdEveryPathHidden(windowMs: Long = PATH_ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no navigation history row shows a table of contents path",
            windowMs = windowMs,
        ) { rows().isNotEmpty() && rows().none { it.pathVisible } }
    }

    fun row(title: String): NavigationRow = rows().firstOrNull { it.title == title }
        ?: throw ConditionTimeoutException(
            "The navigation history list has no row titled \"$title\", it holds ${rows()}"
        )

    fun tapRow(title: String) {
        onView(
            allOf(withId(R.id.navigationHistoryTitle), withText(title), isDisplayed())
        ).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun assertEmptyStateHidden() {
        onView(withId(R.id.message)).check(matches(not(isDisplayed())))
    }

    fun assertStillOpen() {
        Screens.assertTopIs(NavigationHistoryActivity::class.java)
    }

    fun close() {
        TestEnvironment.uiDevice.pressBack()
        Screens.awaitGone(NavigationHistoryActivity::class.java)
    }

    companion object {

        const val PATH_ABSENCE_WINDOW_MS = 2_000L

        fun awaitOpen(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): NavigationHistoryScreen {
            Screens.awaitResumed(NavigationHistoryActivity::class.java, timeoutMs)
            return NavigationHistoryScreen()
        }

        fun pageLabel(oneBasedPage: Int): String =
            TestEnvironment.targetContext.getString(R.string.bookmark_page_label, oneBasedPage)

        fun currentPageLabel(): String = TestEnvironment.targetContext.getString(R.string.history_current_page)

        fun originLabel(resId: Int): String = TestEnvironment.targetContext.getString(resId)

        fun originPrefix(resId: Int): String = "${originLabel(resId)} · "
    }
}
