// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object ListScreens {

    fun recyclerIn(activity: Activity, viewId: Int): RecyclerView {
        val candidates = collect(activity.window.decorView, viewId)
        if (candidates.isEmpty()) {
            throw ConditionTimeoutException(
                "No RecyclerView with id $viewId is attached to ${activity.javaClass.simpleName}"
            )
        }
        return candidates.maxByOrNull { visibleArea(it) }
            ?: throw ConditionTimeoutException("No RecyclerView with id $viewId is on screen")
    }

    fun onRecycler(viewId: Int, read: (RecyclerView) -> Any?): Any? = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed while reading list $viewId")
        read(recyclerIn(activity, viewId))
    }

    fun itemCount(viewId: Int): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity() ?: return@onMainThread 0
        runCatching { recyclerIn(activity, viewId).adapter?.itemCount ?: 0 }.getOrDefault(0)
    }

    fun firstVisiblePosition(viewId: Int): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed while reading list $viewId")
        firstVisibleOf(recyclerIn(activity, viewId))
    }

    fun lastVisiblePosition(viewId: Int): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed while reading list $viewId")
        lastVisibleOf(recyclerIn(activity, viewId))
    }

    fun spanCount(viewId: Int): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed while reading list $viewId")
        (recyclerIn(activity, viewId).layoutManager as? GridLayoutManager)?.spanCount
            ?: throw ConditionTimeoutException("List $viewId is not laid out by a GridLayoutManager")
    }

    fun awaitItemsAtLeast(viewId: Int, expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int =
        Await.untilValue(
            condition = "list $viewId is populated with at least $expected rows",
            timeoutMs = timeoutMs,
            read = { itemCount(viewId) },
        ) { it >= expected }

    fun scrollToPosition(viewId: Int, position: Int) {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivity()
                ?: throw ConditionTimeoutException("No activity is resumed while scrolling list $viewId")
            val recycler = recyclerIn(activity, viewId)
            when (val manager = recycler.layoutManager) {
                is LinearLayoutManager -> manager.scrollToPositionWithOffset(position, 0)
                else -> recycler.scrollToPosition(position)
            }
        }
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
        Await.until("list $viewId settles with position $position at the top") {
            firstVisiblePosition(viewId) == position
        }
    }

    fun awaitLaidOut(viewId: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("list $viewId has laid out at least one row", timeoutMs) {
            runCatching { firstVisiblePosition(viewId) >= 0 }.getOrDefault(false)
        }
    }

    fun awaitLayoutSettled(
        viewId: Int,
        stableForMs: Long = LAYOUT_STABLE_MS,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ) {
        Await.untilTrueContinuously(
            condition = "list $viewId finishes restoring and keeps an idle layout",
            stableForMs = stableForMs,
            timeoutMs = timeoutMs,
        ) {
            TestEnvironment.onMainThread {
                val activity = TestEnvironment.resumedActivity() ?: return@onMainThread false
                val recycler = runCatching { recyclerIn(activity, viewId) }.getOrNull()
                    ?: return@onMainThread false
                recycler.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
                    !recycler.isComputingLayout &&
                    !recycler.isLayoutRequested &&
                    recycler.layoutManager?.isSmoothScrolling != true
            }
        }
    }

    private fun firstVisibleOf(recycler: RecyclerView): Int =
        when (val manager = recycler.layoutManager) {
            is LinearLayoutManager -> manager.findFirstVisibleItemPosition()
            else -> throw ConditionTimeoutException(
                "List ${recycler.id} uses ${manager?.javaClass?.simpleName}, which reports no visible range"
            )
        }

    private fun lastVisibleOf(recycler: RecyclerView): Int =
        when (val manager = recycler.layoutManager) {
            is LinearLayoutManager -> manager.findLastVisibleItemPosition()
            else -> throw ConditionTimeoutException(
                "List ${recycler.id} uses ${manager?.javaClass?.simpleName}, which reports no visible range"
            )
        }

    private fun collect(root: View, viewId: Int): List<RecyclerView> {
        val found = mutableListOf<RecyclerView>()
        fun walk(view: View) {
            if (view is RecyclerView && view.id == viewId) found += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(root)
        return found.filter { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
    }

    private fun visibleArea(view: View): Int {
        val bounds = Rect()
        return if (view.getGlobalVisibleRect(bounds)) bounds.width() * bounds.height() else 0
    }

    private const val LAYOUT_STABLE_MS = 300L
}
