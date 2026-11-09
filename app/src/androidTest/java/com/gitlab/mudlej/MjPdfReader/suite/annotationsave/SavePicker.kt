// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot

object SavePicker {

    const val FILE_NAME_FIELD_ID = SafPickerRobot.FILE_NAME_FIELD_ID
    const val EDIT_TEXT_CLASS = "android.widget.EditText"

    fun robot(): SafPickerRobot = SafPickerRobot()

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS): SafPickerRobot {
        val picker = robot()
        picker.awaitShown(timeoutMs)
        return picker
    }

    fun isShowing(): Boolean = robot().isShowing()

    fun assertNeverShown(windowMs: Long = SaveSheet.ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the system document picker never comes to the foreground",
            windowMs = windowMs,
        ) { !isShowing() }
    }

    private fun fileNameFieldSelector() =
        By.pkg(
            robot().activePackageName()
                ?: throw ConditionTimeoutException("The system document picker is not showing")
        ).res(FILE_NAME_FIELD_ID).clazz(EDIT_TEXT_CLASS)

    fun prefilledFileName(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): String {
        val field = TestEnvironment.uiDevice.wait(
            Until.findObject(fileNameFieldSelector()),
            timeoutMs,
        ) ?: throw ConditionTimeoutException(
            "The create document picker shows no file name field, so this is not the create picker"
        )
        return field.text.orEmpty()
    }

    fun createInDownloads(name: String) {
        val picker = awaitShown()
        picker.navigateToDownloads()
        picker.typeFileNameAndSave(name)
    }

    fun chooseInDownloads(name: String) {
        val picker = awaitShown()
        picker.navigateToDownloads()
        picker.chooseFileNamed(name)
    }

    fun cancel() {
        robot().cancel()
    }
}
