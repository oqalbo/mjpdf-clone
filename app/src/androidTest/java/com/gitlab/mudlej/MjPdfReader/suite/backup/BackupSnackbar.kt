// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import android.graphics.Rect
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object BackupSnackbar {
    const val SWIPE_STEPS = 20

    fun text(): String? = TestEnvironment.onMainThread {
        label()?.takeIf { it.isShown }?.text?.toString()
    }

    fun actionText(): String? = TestEnvironment.onMainThread {
        action()?.takeIf { it.isShown }?.text?.toString()
    }

    fun awaitText(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "a snackbar reading \"$expected\" is shown",
            timeoutMs,
            read = { text() },
        ) { it == expected }
    }

    fun assertNoneWithin(windowMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "no snackbar is shown",
            windowMs = windowMs,
        ) { text() == null }
    }

    fun assertNoneReading(expected: String, windowMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "no snackbar reading \"$expected\" is shown",
            windowMs = windowMs,
        ) { text() != expected }
    }

    fun tapAction() {
        val handled = TestEnvironment.onMainThread {
            action()?.takeIf { it.isShown && it.isEnabled && it.isClickable }?.performClick()
        }
        if (handled != true) {
            throw ConditionTimeoutException(
                "The snackbar action was not interactable or did not handle performClick()"
            )
        }
        TestEnvironment.waitForIdleSync()
    }

    fun swipeAway() {
        val bounds = TestEnvironment.onMainThread {
            label()?.takeIf { it.isShown }?.let { boundsOf(it) }
        } ?: throw ConditionTimeoutException("There is no snackbar on screen to swipe away")
        TestEnvironment.uiDevice.swipe(
            bounds.centerX(),
            bounds.centerY(),
            TestEnvironment.uiDevice.displayWidth - 1,
            bounds.centerY(),
            SWIPE_STEPS,
        )
        TestEnvironment.uiDevice.waitForIdle()
        Await.until("the swiped snackbar is dismissed", Await.DEFAULT_TIMEOUT_MS) { text() == null }
    }

    private fun label(): TextView? = TestEnvironment.resumedActivity()
        ?.findViewById(com.google.android.material.R.id.snackbar_text)

    private fun action(): Button? = TestEnvironment.resumedActivity()
        ?.findViewById(com.google.android.material.R.id.snackbar_action)

    private fun boundsOf(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }
}
