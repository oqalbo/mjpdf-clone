// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.os.SystemClock
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.core.debug.ToastRecorder

object Toasts {

    const val TOAST_TIMEOUT_MS = 5_000L
    const val POLL_INTERVAL_MS = 100L

    fun awaitText(expected: String, timeoutMs: Long = TOAST_TIMEOUT_MS) {
        if (waitForToast(expected, timeoutMs)) {
            return
        }
        throw AssertionError(
            "No toast reading \"$expected\" appeared within ${timeoutMs}ms. Three sources were polled: the " +
                "app side recorder every toast shim call feeds, the accessibility events a toast raises, " +
                "and the UiAutomator node tree. The app side recorder held ${ToastRecorder.snapshot()} " +
                "and the accessibility events seen during this test were ${AccessibilityToasts.texts()}. An empty " +
                "result from all three means the branch that shows the toast did not run."
        )
    }

    fun assertNoneWithin(expected: String, windowMs: Long = Await.SHORT_TIMEOUT_MS) {
        if (waitForToast(expected, windowMs)) {
            throw AssertionError("A toast reading \"$expected\" appeared while none was expected")
        }
    }

    private fun waitForToast(expected: String, timeoutMs: Long): Boolean {
        val deadlineUptimeMs = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            if (observedToast(expected)) {
                return true
            }
            val remainingMs = deadlineUptimeMs - SystemClock.uptimeMillis()
            if (remainingMs <= 0L) {
                return false
            }
            Thread.sleep(minOf(POLL_INTERVAL_MS, remainingMs))
        }
    }
    private fun observedToast(expected: String): Boolean =
        ToastRecorder.snapshot().any { it == expected || it.contains(expected) } ||
            AccessibilityToasts.texts().any { it == expected || it.contains(expected) } ||
            TestEnvironment.uiDevice.hasObject(By.text(expected))

}
