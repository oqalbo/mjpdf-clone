// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.Await

object TimePickerRobot {
    fun isShowing(): Boolean =
        com.gitlab.mudlej.MjPdfReader.suite.settings.TimePickerRobot.pickerOrNull()
            ?.dialog?.isShowing == true

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) =
        com.gitlab.mudlej.MjPdfReader.suite.settings.TimePickerRobot.awaitShown(timeoutMs)

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) =
        com.gitlab.mudlej.MjPdfReader.suite.settings.TimePickerRobot.awaitDismissed(timeoutMs)

    fun setTime(hour: Int, minute: Int) =
        com.gitlab.mudlej.MjPdfReader.suite.settings.TimePickerRobot.setTime(hour, minute)

    fun confirm() =
        com.gitlab.mudlej.MjPdfReader.suite.settings.TimePickerRobot.confirm()

    fun cancel() =
        com.gitlab.mudlej.MjPdfReader.suite.settings.TimePickerRobot.cancel()
}
