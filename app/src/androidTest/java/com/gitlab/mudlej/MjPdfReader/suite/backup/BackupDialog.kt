// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object BackupDialog {
    const val POSITIVE = "button1"
    const val NEGATIVE = "button2"

    private val titleSelector: BySelector get() = By.res(TestEnvironment.targetPackage, "alertTitle")

    private val messageSelector: BySelector get() = By.res("android:id/message")

    data class DisabledButtonObservation(
        val label: String,
        val observedAtUptimeMillis: Long,
    )

    fun isShowing(): Boolean =
        TestEnvironment.uiDevice.hasObject(titleSelector) ||
            TestEnvironment.uiDevice.hasObject(messageSelector)

    fun title(): String? = TestEnvironment.uiDevice.findObject(titleSelector)?.text

    fun message(): String? = TestEnvironment.uiDevice.findObject(messageSelector)?.text

    fun awaitTitle(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "a dialog titled \"$expected\" is showing",
            timeoutMs,
            read = { title() },
        ) { it == expected }
    }

    fun awaitMessageContaining(fragment: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): String =
        Await.untilValue(
            "a dialog whose message contains \"$fragment\" is showing",
            timeoutMs,
            read = { message() },
        ) { it != null && it.contains(fragment) }!!

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("no dialog is showing", timeoutMs) { !isShowing() }
    }

    fun assertNoneWithin(timeoutMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "no dialog appears",
            windowMs = timeoutMs,
        ) { !isShowing() }
    }

    fun assertNoDialogTitled(expected: String, timeoutMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "no dialog titled \"$expected\" appears",
            windowMs = timeoutMs,
        ) { title() != expected }
    }

    fun assertNoDialogMessageContaining(fragment: String, timeoutMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "no dialog whose message contains \"$fragment\" appears",
            windowMs = timeoutMs,
        ) { message()?.contains(fragment) != true }
    }

    fun button(which: String): UiObject2? =
        TestEnvironment.uiDevice.findObject(By.res("android:id/$which"))

    fun buttonText(which: String): String? = button(which)?.text

    fun isButtonEnabled(which: String): Boolean = button(which)?.isEnabled == true

    fun awaitButton(which: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): UiObject2 =
        Await.untilNotNull("the dialog shows its $which button", timeoutMs) { button(which) }

    fun awaitButtonEnabled(which: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the dialog $which button becomes enabled", timeoutMs) { isButtonEnabled(which) }
    }

    fun awaitDisabledButtonForMessage(
        messageFragment: String,
        which: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): DisabledButtonObservation = Await.untilNotNull(
        "the dialog containing \"$messageFragment\" shows a disabled $which button",
        timeoutMs,
    ) {
        if (message()?.contains(messageFragment) != true) return@untilNotNull null
        val button = button(which) ?: return@untilNotNull null
        if (button.isEnabled) return@untilNotNull null
        DisabledButtonObservation(button.text.orEmpty(), SystemClock.uptimeMillis())
    }

    fun tap(which: String) {
        val target = awaitButton(which)
        if (!target.isEnabled) {
            throw ConditionTimeoutException(
                "The dialog $which button reading \"${target.text}\" is disabled and cannot be tapped"
            )
        }
        target.click()
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun tapAndAwaitGone(which: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        tap(which)
        awaitGone(timeoutMs)
    }

    fun dismissWithOk(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        tap(POSITIVE)
        awaitGone(timeoutMs)
    }

    fun visibleTexts(): List<String> =
        TestEnvironment.uiDevice.findObjects(By.pkg(TestEnvironment.targetPackage))
            .mapNotNull { it.text }
            .filter { it.isNotBlank() }
            .distinct()
}
