// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object SettingsRows {
    fun tap(title: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        val row = await(title, timeoutMs)
        clickableAncestorOf(row).click()
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    fun await(title: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): UiObject2 =
        Await.untilNotNull("the settings row \"$title\" is on screen", timeoutMs, pollMs = POLL_MS) {
            find(title) ?: run { scrollToward(title); find(title) }
        }

    fun find(title: String): UiObject2? = TestEnvironment.uiDevice.findObject(By.text(title))

    private fun scrollToward(title: String) {
        runCatching {
            UiScrollable(UiSelector().scrollable(true))
                .apply { setMaxSearchSwipes(MAX_SEARCH_SWIPES) }
                .scrollTextIntoView(title)
        }
    }

    private fun clickableAncestorOf(node: UiObject2): UiObject2 {
        var current = node
        repeat(ANCESTOR_DEPTH) {
            if (current.isClickable) return current
            current = current.parent ?: return current
        }
        return current
    }

    fun require(title: String): UiObject2 =
        find(title) ?: throw ConditionTimeoutException("The settings screen shows no row reading \"$title\"")

    private const val MAX_SEARCH_SWIPES = 20
    private const val ANCESTOR_DEPTH = 4
    private const val POLL_MS = 250L
}
