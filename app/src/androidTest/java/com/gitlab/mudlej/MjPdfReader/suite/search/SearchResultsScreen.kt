// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity

class SearchResultsScreen {

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Activity =
        Screens.awaitResumed(SearchActivity::class.java, timeoutMs)

    fun awaitGone(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Screens.awaitGone(SearchActivity::class.java, timeoutMs)
    }

    fun isShown(): Boolean = TestEnvironment.resumedActivityOfType(SearchActivity::class.java) != null

    fun activity(): SearchActivity =
        TestEnvironment.resumedActivityOfType(SearchActivity::class.java) as? SearchActivity
            ?: throw ConditionTimeoutException("No resumed search results activity")

    fun activityOrNull(): SearchActivity? =
        TestEnvironment.resumedActivityOfType(SearchActivity::class.java) as? SearchActivity

    fun <T> onActivity(read: (SearchActivity) -> T): T = TestEnvironment.onMainThread { read(activity()) }

    fun title(): String = TestEnvironment.onMainThread { activity().title?.toString().orEmpty() }

    fun titleCount(): Int? = parseGroupedCount(title())

    fun awaitTitleCount(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int =
        Await.untilNotNull("the results title carries a parseable count", timeoutMs) { titleCount() }

    fun itemCount(): Int = onActivity { it.findViewById<RecyclerView>(R.id.searchRecyclerView).adapter?.itemCount ?: 0 }

    fun awaitItemCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the results list holds $expected rows",
            timeoutMs,
            read = { itemCount() },
        ) { it == expected }
    }

    fun awaitAtLeastOneRow(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int =
        Await.untilValue(
            "the results list holds at least one row",
            timeoutMs,
            read = { itemCount() },
        ) { it >= 1 }

    fun determinateProgressVisibility(): Int =
        onActivity { it.findViewById<ProgressBar>(R.id.searchProgressBar).visibility }

    fun determinateProgress(): Int = onActivity { it.findViewById<ProgressBar>(R.id.searchProgressBar).progress }

    fun awaitDeterminateProgressGone(timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        Await.until("the determinate search progress bar is gone", timeoutMs) {
            determinateProgressVisibility() == View.GONE
        }
    }

    fun awaitDeterminateProgressVisible(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the determinate search progress bar is visible", timeoutMs) {
            determinateProgressVisibility() == View.VISIBLE
        }
    }

    fun awaitSearchFinished(timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        awaitDeterminateProgressGone(timeoutMs)
    }

    fun awaitItemCountMatchesTitle(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int =
        Await.untilValue(
            "the results list has applied every submitted row, which lags the title because the adapter " +
                "diffs off the main thread and the title is assigned inside the same callback that submits",
            timeoutMs,
            read = { itemCount() },
        ) { it == titleCount() }

    fun firstVisiblePosition(): Int = onActivity {
        (it.findViewById<RecyclerView>(R.id.searchRecyclerView).layoutManager as LinearLayoutManager)
            .findFirstVisibleItemPosition()
    }

    fun firstVisibleOffset(): Int = onActivity {
        val list = it.findViewById<RecyclerView>(R.id.searchRecyclerView)
        val layout = list.layoutManager as LinearLayoutManager
        val position = layout.findFirstVisibleItemPosition()
        (layout.findViewByPosition(position)?.top ?: 0) - list.paddingTop
    }

    fun rowView(position: Int): View? = onActivity {
        it.findViewById<RecyclerView>(R.id.searchRecyclerView)
            .findViewHolderForAdapterPosition(position)?.itemView
    }

    fun pageLabel(position: Int): String? = TestEnvironment.onMainThread {
        rowChild<TextView>(position, R.id.resultPageNumber)?.text?.toString()
    }

    fun rowText(position: Int): String? = TestEnvironment.onMainThread {
        rowChild<TextView>(position, R.id.resultText)?.text?.toString()
    }

    fun showMoreVisibility(position: Int): Int? = TestEnvironment.onMainThread {
        rowChild<ImageView>(position, R.id.showMoreButton)?.visibility
    }

    fun awaitRowBound(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the results row at position $position is bound", timeoutMs) { rowView(position) != null }
    }

    fun awaitRowText(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): String =
        Await.untilValue(
            "the results row at position $position shows text",
            timeoutMs,
            read = { rowText(position) },
        ) { !it.isNullOrBlank() }!!

    fun pageLabelFor(pageNumber: Int): String = TestEnvironment.appString(R.string.search_result_page_label, pageNumber)

    fun scrollToPosition(position: Int) {
        TestEnvironment.onMainThread {
            activity().findViewById<RecyclerView>(R.id.searchRecyclerView).scrollToPosition(position)
        }
        awaitScrollIdle()
        awaitRowBound(position)
    }

    fun scrollState(): Int = onActivity { it.findViewById<RecyclerView>(R.id.searchRecyclerView).scrollState }

    fun awaitScrollIdle(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the results list stops scrolling", timeoutMs) {
            scrollState() == RecyclerView.SCROLL_STATE_IDLE
        }
    }

    fun tapRow(position: Int) {
        awaitRowBound(position)
        val clicked = TestEnvironment.onMainThread {
            rowView(position)?.performClick() ?: false
        }
        if (!clicked) throw ConditionTimeoutException("Row $position did not consume its click")
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun tapShowMore(position: Int) {
        awaitRowBound(position)
        val button = TestEnvironment.onMainThread { rowChild<ImageView>(position, R.id.showMoreButton) }
            ?: throw ConditionTimeoutException("Row $position carries no show more button")
        val bounds = boundsOf(button)
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun openFilter(query: String) {
        val icon = Await.untilNotNull("the results screen action bar carries a search icon") {
            TestEnvironment.uiDevice.findObject(By.desc(string(R.string.search)))
        }
        icon.click()
        val field = Await.untilNotNull("the results filter field opens") {
            TestEnvironment.uiDevice.findObject(By.res(TestEnvironment.targetPackage, SEARCH_SRC_TEXT))
                ?: TestEnvironment.uiDevice.findObject(By.res(ANDROIDX_SEARCH_SRC_TEXT))
        }
        field.text = query
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun boundsOf(view: View): Rect = TestEnvironment.onMainThread {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun nightMode(): Int = onActivity { it.delegate.localNightMode }

    fun resolvedColorSurface(): Int = onActivity { activity ->
        val typed = android.util.TypedValue()
        activity.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typed, true)
        if (typed.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(activity, typed.resourceId)
        } else {
            typed.data
        }
    }

    fun incognitoExtra(): Boolean =
        onActivity { it.intent.getBooleanExtra(com.gitlab.mudlej.MjPdfReader.pdf.PDF.incognitoKey, false) }

    fun titleFor(count: Int): String = "${"%,d".format(count)} ${string(R.string.search_results)}"

    fun string(resId: Int): String = TestEnvironment.appString(resId)

    private fun <T : View> rowChild(position: Int, viewId: Int): T? {
        val row = activity().findViewById<RecyclerView>(R.id.searchRecyclerView)
            .findViewHolderForAdapterPosition(position)?.itemView ?: return null
        return row.findViewById(viewId)
    }

    companion object {

        const val SEARCH_SRC_TEXT = "search_src_text"
        const val ANDROIDX_SEARCH_SRC_TEXT = "androidx.appcompat:id/search_src_text"

        fun parseGroupedCount(title: String): Int? {
            val digits = StringBuilder()
            for (character in title) {
                if (character.isLetter()) break
                val value = Character.digit(character, RADIX)
                if (value >= 0) digits.append(value)
            }
            return digits.toString().takeIf { it.isNotEmpty() }?.toIntOrNull()
        }

        private const val RADIX = 10
    }
}
