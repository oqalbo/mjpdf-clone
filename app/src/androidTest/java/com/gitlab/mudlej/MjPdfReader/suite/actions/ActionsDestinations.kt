// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity

object ActionsDestinations {

    fun awaitResumed(type: Class<out Activity>, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Activity =
        Await.untilNotNull("${type.simpleName} is resumed", timeoutMs) {
            TestEnvironment.resumedActivityOfType(type)
        }

    fun stringExtra(activity: Activity, key: String): String? =
        TestEnvironment.onMainThread { activity.intent?.getStringExtra(key) }

    fun intExtra(activity: Activity, key: String, fallback: Int = Int.MIN_VALUE): Int =
        TestEnvironment.onMainThread { activity.intent?.getIntExtra(key, fallback) ?: fallback }

    fun titleOf(activity: Activity): String? = TestEnvironment.onMainThread { activity.title?.toString() }

    fun visibleTexts(activity: Activity): List<String> = TestEnvironment.onMainThread {
        val collected = mutableListOf<String>()
        collectTexts(activity.window.decorView, collected)
        collected
    }

    fun awaitVisibleText(activity: Activity, text: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "${activity.javaClass.simpleName} shows \"$text\"",
            timeoutMs,
            read = { visibleTexts(activity) },
        ) { it.contains(text) }
    }

    fun tapVisibleText(activity: Activity, text: String) {
        val point = TestEnvironment.onMainThread {
            val target = Dialogs.firstDescendant(activity.window.decorView, TextView::class.java) {
                it.isShown && it.text?.toString() == text
            }
                ?: throw ConditionTimeoutException(
                    "${activity.javaClass.simpleName} shows no row reading \"$text\""
                )
            val clickable = clickableAncestorOf(target)
            val location = IntArray(2)
            clickable.getLocationOnScreen(location)
            (location[0] + clickable.width / 2) to (location[1] + clickable.height / 2)
        }
        TestEnvironment.uiDevice.click(point.first, point.second)
        TestEnvironment.uiDevice.waitForIdle()
    }

    private fun collectTexts(root: View, into: MutableList<String>) {
        if (!root.isShown) return
        if (root is TextView) {
            root.text?.toString()?.takeIf { it.isNotEmpty() }?.let { into.add(it) }
            return
        }
        if (root !is ViewGroup) return
        for (index in 0 until root.childCount) {
            collectTexts(root.getChildAt(index), into)
        }
    }

    private fun clickableAncestorOf(view: View): View {
        var current: View = view
        repeat(ANCESTOR_DEPTH) {
            if (current.isClickable) return current
            current = current.parent as? View ?: return current
        }
        return current
    }

    private const val ANCESTOR_DEPTH = 5
}

object ActionsTableOfContents {

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Activity =
        ActionsDestinations.awaitResumed(TableOfContentsActivity::class.java, timeoutMs)

    fun tapEntry(title: String) {
        val activity = awaitShown()
        ActionsDestinations.awaitVisibleText(activity, title)
        ActionsDestinations.tapVisibleText(activity, title)
    }
}
