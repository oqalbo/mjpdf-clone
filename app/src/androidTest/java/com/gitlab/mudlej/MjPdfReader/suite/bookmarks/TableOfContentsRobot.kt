// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.widget.Toolbar
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.google.android.material.card.MaterialCardView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity


class TableOfContentsRobot {
    data class RowActionState(
        val clickable: Boolean,
        val focusable: Boolean,
        val hasAccessibilityClickAction: Boolean,
    )

    fun openFromReader() {
        BookmarksReader.openAction(R.string.table_of_contents)
        awaitLoaded()
    }

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Screens.awaitResumed(TableOfContentsActivity::class.java, timeoutMs)
    }

    fun awaitLoaded(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        awaitShown(timeoutMs)
        Await.until("the table of contents progress bar is gone", timeoutMs) {
            progressVisible() == false
        }
        Await.untilValue(
            condition = "the table of contents screen is titled \"${string(R.string.table_of_contents)}\"",
            timeoutMs = timeoutMs,
            read = { title() },
        ) { it == string(R.string.table_of_contents) }
    }

    fun awaitClosed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Screens.awaitGone(TableOfContentsActivity::class.java, timeoutMs)
    }

    fun isShown(): Boolean =
        TestEnvironment.resumedActivityOfType(TableOfContentsActivity::class.java) != null

    fun activity(): TableOfContentsActivity =
        TestEnvironment.resumedActivityOfType(TableOfContentsActivity::class.java) as? TableOfContentsActivity
            ?: throw ConditionTimeoutException("No resumed table of contents activity")

    fun title(): String? = TestEnvironment.onMainThread { activity().title?.toString() }

    fun progressVisible(): Boolean? = TestEnvironment.onMainThread {
        activity().findViewById<View>(R.id.progressBar)?.visibility == View.VISIBLE
    }

    fun messageShown(): Boolean = TestEnvironment.onMainThread {
        activity().findViewById<View>(R.id.message)?.visibility == View.VISIBLE
    }

    fun messageText(): String? = TestEnvironment.onMainThread {
        activity().findViewById<TextView>(R.id.message)?.text?.toString()
    }

    fun rowCount(): Int = BookmarksRows.itemCount(activity(), R.id.bookmarksRecyclerView)

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the table of contents lists $expected rows",
            timeoutMs = timeoutMs,
            read = { rowCount() },
        ) { it == expected }
    }

    fun scrollTo(position: Int) {
        BookmarksRows.scrollTo(activity(), R.id.bookmarksRecyclerView, position)
        Await.until("row $position is bound after scrolling to it") { itemView(position) != null }
    }

    fun rowTitle(position: Int): String? = TestEnvironment.onMainThread {
        rowChild<TextView>(position, R.id.bookmarkText)?.text?.toString()
    }

    fun rowPageNumber(position: Int): String? = TestEnvironment.onMainThread {
        rowChild<TextView>(position, R.id.bookmarkPageNumber)?.text?.toString()
    }

    fun rowTitleAlpha(position: Int): Float = TestEnvironment.onMainThread {
        rowChild<TextView>(position, R.id.bookmarkText)?.alpha
            ?: throw ConditionTimeoutException("Row $position has no title view on screen")
    }

    fun rowTitleTextSize(position: Int): Float = TestEnvironment.onMainThread {
        rowChild<TextView>(position, R.id.bookmarkText)?.textSize
            ?: throw ConditionTimeoutException("Row $position has no title view on screen")
    }

    fun rowIndentMarginStart(position: Int): Int = TestEnvironment.onMainThread {
        val card = rowChild<View>(position, R.id.bookmarkCard)
            ?: throw ConditionTimeoutException("Row $position has no card on screen")
        (card.layoutParams as ViewGroup.MarginLayoutParams).marginStart
    }

    fun rowCardColor(position: Int): Int = TestEnvironment.onMainThread {
        val card = rowChild<MaterialCardView>(position, R.id.bookmarkCard)
            ?: throw ConditionTimeoutException("Row $position has no card on screen")
        card.cardBackgroundColor.defaultColor
    }

    fun rowActionState(position: Int, @IdRes viewId: Int): RowActionState =
        TestEnvironment.onMainThread {
            val view = rowChild<View>(position, viewId)
                ?: throw ConditionTimeoutException(
                    "Row $position has no ${BookmarksRows.resourceName(viewId)} bound on screen"
                )
            val node = view.createAccessibilityNodeInfo()
            try {
                RowActionState(
                    clickable = view.isClickable,
                    focusable = view.isFocusable,
                    hasAccessibilityClickAction = node.actionList.any {
                        it.id == AccessibilityNodeInfo.ACTION_CLICK
                    },
                )
            } finally {
                recycleNode(node)
            }
        }

    fun awaitRowUnbound(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("row $position is recycled off screen", timeoutMs) {
            TestEnvironment.onMainThread { itemView(position) == null }
        }
    }

    fun rowTitles(): List<String> = (0 until rowCount()).map { position ->
        scrollTo(position)
        rowTitle(position).orEmpty()
    }

    fun adapterTitles(): List<String> = TestEnvironment.onMainThread {
        val adapter = BookmarksRows.recycler(activity(), R.id.bookmarksRecyclerView).adapter
            as com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsAdapter
        adapter.currentList.map { it.entry.title.orEmpty() }
    }

    fun positionOfTitle(title: String): Int {
        val count = rowCount()
        for (position in 0 until count) {
            scrollTo(position)
            if (rowTitle(position) == title) return position
        }
        throw ConditionTimeoutException(
            "No table of contents row is titled \"$title\". The listed titles are ${rowTitles()}"
        )
    }

    fun firstVisiblePosition(): Int = TestEnvironment.onMainThread {
        layoutManager().findFirstVisibleItemPosition()
    }

    fun firstCompletelyVisiblePosition(): Int = TestEnvironment.onMainThread {
        layoutManager().findFirstCompletelyVisibleItemPosition()
    }

    fun lastVisiblePosition(): Int = TestEnvironment.onMainThread {
        layoutManager().findLastVisibleItemPosition()
    }

    fun isVisible(position: Int): Boolean = position in firstVisiblePosition()..lastVisiblePosition()

    fun tapRow(position: Int) = tapRowChild(position, R.id.bookmarkCard)

    fun tapRowTitle(position: Int) = tapRowChild(position, R.id.bookmarkText)

    fun tapToggle(position: Int) {
        tapRowChild(position, R.id.toggleButton)
    }

    fun expandAll() = tapOverflowItem(R.string.expand_all)

    fun collapseAll() = tapOverflowItem(R.string.collapse_all)

    fun locateMe() {
        onView(withId(R.id.locate_current_entry)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun isLocateMeShown(): Boolean = isActionShown(R.id.locate_current_entry)

    fun isSearchShown(): Boolean = isActionShown(R.id.search_in_search_activity)

    fun isOverflowItemShown(@StringRes titleRes: Int): Boolean {
        val opened = runCatching {
            openActionBarOverflowOrOptionsMenu(TestEnvironment.targetContext)
        }.isSuccess
        TestEnvironment.waitForIdleSync()
        if (!opened) {
            return false
        }
        val shown = runCatching {
            onView(withText(string(titleRes))).check(matches(isDisplayed()))
        }.isSuccess
        TestEnvironment.uiDevice.pressBack()
        TestEnvironment.waitForIdleSync()
        return shown
    }

    fun openSearch() {
        onView(withId(R.id.search_in_search_activity)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun typeQuery(query: String) {
        onView(withId(androidx.appcompat.R.id.search_src_text))
            .perform(replaceText(query), closeSoftKeyboard())
        TestEnvironment.waitForIdleSync()
    }

    fun queryText(): String? = TestEnvironment.onMainThread {
        searchViewOrNull()?.query?.toString()
    }

    fun isSearchExpanded(): Boolean = TestEnvironment.onMainThread {
        searchViewOrNull()
            ?.takeIf { it.isAttachedToWindow }
            ?.isIconified == false
    }

    fun isImeVisible(): Boolean = TestEnvironment.onMainThread {
        ViewCompat.getRootWindowInsets(activity().window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    fun awaitImeHidden(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the contents search keyboard is hidden", timeoutMs) { !isImeVisible() }
    }

    fun closeSearch() {
        if (!queryText().isNullOrEmpty()) {
            onView(withId(androidx.appcompat.R.id.search_close_btn)).perform(click())
            TestEnvironment.waitForIdleSync()
        }
        Await.untilValue(
            condition = "the contents search query is empty",
            read = { queryText() },
        ) { it == "" }
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(closeSoftKeyboard())
        awaitImeHidden()
        onView(
            withContentDescription(
                string(androidx.appcompat.R.string.abc_toolbar_collapse_description)
            )
        ).check(matches(isDisplayed())).perform(click())
        TestEnvironment.waitForIdleSync()
        Await.until("the contents search view collapses") { !isSearchExpanded() }
    }

    fun pressBack() {
        TestEnvironment.uiDevice.pressBack()
    }

    private fun tapOverflowItem(@StringRes titleRes: Int) {
        openActionBarOverflowOrOptionsMenu(TestEnvironment.targetContext)
        TestEnvironment.waitForIdleSync()
        onView(withText(string(titleRes))).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun isActionShown(@IdRes viewId: Int): Boolean = runCatching {
        onView(withId(viewId)).check(matches(isDisplayed()))
    }.isSuccess

    private fun layoutManager(): LinearLayoutManager =
        BookmarksRows.recycler(activity(), R.id.bookmarksRecyclerView).layoutManager as? LinearLayoutManager
            ?: throw ConditionTimeoutException("The table of contents list has no linear layout manager")

    private fun itemView(position: Int): View? =
        BookmarksRows.itemView(activity(), R.id.bookmarksRecyclerView, position)
    private fun searchViewOrNull(): SearchView? =
        actionBarToolbar().menu.findItem(R.id.search_in_search_activity)?.actionView as? SearchView

    private fun actionBarToolbar(): Toolbar =
        Dialogs.firstDescendant(activity().window.decorView, Toolbar::class.java)
            ?: throw ConditionTimeoutException(
                "The table of contents window has no AppCompat Toolbar containing its search action"
            )


    private fun <T : View> rowChild(position: Int, @IdRes viewId: Int): T? =
        itemView(position)?.findViewById<T>(viewId)

    private fun tapRowChild(position: Int, @IdRes viewId: Int) {
        Await.until("contents row $position is ready for input") {
            TestEnvironment.onMainThread {
                val recycler = BookmarksRows.recycler(activity(), R.id.bookmarksRecyclerView)
                recycler.hasWindowFocus() && !recycler.isComputingLayout &&
                    !recycler.hasPendingAdapterUpdates() && recycler.itemAnimator?.isRunning != true
            }
        }
        onView(object : org.hamcrest.TypeSafeMatcher<View>() {
            override fun describeTo(description: org.hamcrest.Description) {
                description.appendText("${BookmarksRows.resourceName(viewId)} in current contents row $position")
            }

            override fun matchesSafely(view: View): Boolean {
                if (view.id != viewId) return false
                val recycler = BookmarksRows.recycler(activity(), R.id.bookmarksRecyclerView)
                val holder = recycler.findViewHolderForAdapterPosition(position) ?: return false
                return holder.itemView.findViewById<View>(viewId) === view
            }
        }).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    @Suppress("DEPRECATION")
    private fun recycleNode(node: AccessibilityNodeInfo) {
        node.recycle()
    }

    private fun string(@StringRes resId: Int): String = TestEnvironment.targetContext.getString(resId)

}
