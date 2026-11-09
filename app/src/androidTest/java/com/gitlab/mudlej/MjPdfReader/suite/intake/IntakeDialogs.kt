// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.util.regex.Pattern

object IntakeDialogs {

    private val device get() = TestEnvironment.uiDevice

    fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    fun string(resId: Int, vararg formatArgs: Any): String =
        TestEnvironment.targetContext.getString(resId, *formatArgs)

    fun find(text: String): UiObject2? = device.findObject(By.text(caseInsensitive(text)))

    fun isShowing(text: String): Boolean = find(text) != null

    fun awaitShowing(text: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): UiObject2 {
        val found = device.wait(Until.findObject(By.text(caseInsensitive(text))), timeoutMs)
            ?: throw ConditionTimeoutException(
                "Timed out after ${timeoutMs}ms waiting for \"$text\" on screen"
            )
        device.waitForIdle()
        return found
    }

    fun awaitGone(text: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("\"$text\" leaves the screen", timeoutMs) { !isShowing(text) }
    }

    fun tap(text: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val target = awaitShowing(text, timeoutMs)
        target.click()
        device.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    fun holdAbsent(text: String, windowMs: Long = IntakeDb.QUIET_WINDOW_MS) {
        Await.assertThroughout(
            condition = "\"$text\" never appears",
            windowMs = windowMs,
        ) { !isShowing(text) }
    }

    fun messageContaining(fragment: String): Boolean =
        device.findObject(By.textContains(fragment)) != null

    fun awaitMessageContaining(fragment: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("a view containing \"$fragment\" is shown", timeoutMs) { messageContaining(fragment) }
    }

    fun tapOutside() {
        device.click(device.displayWidth / 2, device.displayHeight / OUTSIDE_TAP_FRACTION)
        device.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    private fun caseInsensitive(text: String): Pattern =
        Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)

    private const val OUTSIDE_TAP_FRACTION = 10
}
