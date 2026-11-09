// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import android.graphics.Rect
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.links.LinksActivity

class LinksRobot {

    fun openFromReader() {
        BookmarksReader.openAction(R.string.links_in_file)
        awaitScanFinished()
    }

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Screens.awaitResumed(LinksActivity::class.java, timeoutMs)
    }

    fun awaitScanFinished(timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        awaitShown()
        Await.until("the links scan progress bar is gone", timeoutMs) {
            visibility(R.id.linksProgressBar) != View.VISIBLE
        }
    }

    fun awaitClosed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Screens.awaitGone(LinksActivity::class.java, timeoutMs)
    }

    fun isShown(): Boolean = TestEnvironment.resumedActivityOfType(LinksActivity::class.java) != null

    fun activity(): LinksActivity =
        TestEnvironment.resumedActivityOfType(LinksActivity::class.java) as? LinksActivity
            ?: throw ConditionTimeoutException("No resumed links activity")

    fun title(): String? = TestEnvironment.onMainThread { activity().title?.toString() }

    fun titleFor(count: Int): String =
        "%,d".format(count) + " " + TestEnvironment.targetContext.getString(R.string.links_in_document)

    fun awaitTitle(expected: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the links screen is titled \"$expected\"",
            timeoutMs = timeoutMs,
            read = { title() },
        ) { it == expected }
    }

    fun messageShown(): Boolean = visibility(R.id.message) == View.VISIBLE

    fun messageText(): String? = TestEnvironment.onMainThread {
        activity().findViewById<TextView>(R.id.message)?.text?.toString()
    }

    fun rowCount(): Int = BookmarksRows.itemCount(activity(), R.id.linkRecyclerView)

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the links list shows $expected rows",
            timeoutMs = timeoutMs,
            read = { rowCount() },
        ) { it == expected }
    }

    fun scrollTo(position: Int) {
        BookmarksRows.scrollTo(activity(), R.id.linkRecyclerView, position)
        Await.until("link row $position is bound after scrolling to it") { itemView(position) != null }
    }

    fun rowUrl(position: Int): String? = TestEnvironment.onMainThread {
        rowChild<TextView>(position, R.id.linkUri)?.text?.toString()
    }

    fun rowPageNumber(position: Int): String? = TestEnvironment.onMainThread {
        rowChild<TextView>(position, R.id.linkPageNumber)?.text?.toString()
    }

    fun rowUrls(): List<String> = (0 until rowCount()).map { position ->
        scrollTo(position)
        rowUrl(position).orEmpty()
    }

    fun positionOfUrl(url: String): Int {
        val count = rowCount()
        for (position in 0 until count) {
            scrollTo(position)
            if (rowUrl(position) == url) return position
        }
        throw ConditionTimeoutException("No link row carries the url \"$url\". The listed urls are ${rowUrls()}")
    }

    fun tapUrl(position: Int) = tapRowChild(position, R.id.linkTextsLayout)

    fun tapPageNumber(position: Int) = tapRowChild(position, R.id.linkPageNumber)

    fun tapCopy(position: Int) = tapRowChild(position, R.id.copyButton)

    fun isSearchShown(): Boolean = runCatching {
        onView(withId(R.id.search_in_search_activity)).check(matches(isDisplayed()))
    }.isSuccess

    fun openSearch() {
        onView(withId(R.id.search_in_search_activity)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun typeQuery(query: String) {
        onView(withId(androidx.appcompat.R.id.search_src_text))
            .perform(replaceText(query), closeSoftKeyboard())
        TestEnvironment.waitForIdleSync()
    }

    fun closeSearch() {
        onView(withId(androidx.appcompat.R.id.search_close_btn)).perform(click())
        TestEnvironment.waitForIdleSync()
        if (isSearchExpanded()) {
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.waitForIdleSync()
        }
    }

    fun isSearchExpanded(): Boolean = runCatching {
        onView(withId(androidx.appcompat.R.id.search_src_text)).check(matches(isDisplayed()))
    }.isSuccess

    fun filteredResultsText(count: Int): String =
        TestEnvironment.targetContext.getString(R.string.number_of_filtered_results).format(count)

    fun pressBack() {
        TestEnvironment.uiDevice.pressBack()
    }

    private fun visibility(@IdRes viewId: Int): Int? = TestEnvironment.onMainThread {
        activity().findViewById<View>(viewId)?.visibility
    }

    private fun itemView(position: Int): View? =
        BookmarksRows.itemView(activity(), R.id.linkRecyclerView, position)

    private fun <T : View> rowChild(position: Int, @IdRes viewId: Int): T? =
        itemView(position)?.findViewById<T>(viewId)

    private fun tapRowChild(position: Int, @IdRes viewId: Int) {
        val bounds = TestEnvironment.onMainThread {
            val view = rowChild<View>(position, viewId)
                ?: throw ConditionTimeoutException(
                    "Link row $position has no ${BookmarksRows.resourceName(viewId)} on screen"
                )
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }
}
