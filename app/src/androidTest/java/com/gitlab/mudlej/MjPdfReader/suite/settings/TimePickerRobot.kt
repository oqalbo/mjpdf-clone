// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import android.view.View
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.google.android.material.timepicker.MaterialTimePicker

object TimePickerRobot {

    fun pickerOrNull(): MaterialTimePicker? = TestEnvironment.onMainThread {
        SettingsScreen.fragmentOrNull()
            ?.childFragmentManager
            ?.findFragmentByTag(SettingsScreen.TIME_PICKER_TAG) as? MaterialTimePicker
    }

    fun picker(): MaterialTimePicker = pickerOrNull()
        ?: throw ConditionTimeoutException("No auto backup time picker is showing")

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the auto backup time picker is showing", timeoutMs) {
            pickerOrNull()?.dialog?.isShowing == true
        }
    }

    fun awaitDismissed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the auto backup time picker is dismissed", timeoutMs) { pickerOrNull() == null }
    }

    fun hour(): Int = picker().hour

    fun minute(): Int = picker().minute

    fun setTime(hour: Int, minute: Int) {
        TestEnvironment.onMainThread {
            picker().hour = hour
            picker().minute = minute
        }
        TestEnvironment.waitForIdleSync()
        Await.untilValue(
            "the time picker reads back $hour:$minute",
            Await.DEFAULT_TIMEOUT_MS,
            read = { runCatching { hour() to minute() }.getOrNull() },
        ) { it == hour to minute }
    }

    fun confirm() {
        tap(com.google.android.material.R.id.material_timepicker_ok_button)
        awaitDismissed()
    }

    fun cancel() {
        tap(com.google.android.material.R.id.material_timepicker_cancel_button)
        awaitDismissed()
    }

    private fun tap(viewId: Int) {
        val activated = TestEnvironment.onMainThread {
            val view = findViewById<View>(viewId)
                ?: throw ConditionTimeoutException("The time picker carries no view $viewId")
            if (!view.isShown || !view.isEnabled || !view.isClickable) {
                false
            } else {
                view.performClick()
                true
            }
        }
        if (!activated) {
            throw ConditionTimeoutException(
                "The time picker view $viewId was not shown, enabled, and clickable"
            )
        }
        TestEnvironment.waitForIdleSync()
    }

    private fun <T : View> findViewById(viewId: Int): T? {
        val root = picker().dialog?.window?.decorView ?: return null
        @Suppress("UNCHECKED_CAST")
        return root.findViewById<View>(viewId) as? T
    }

}
