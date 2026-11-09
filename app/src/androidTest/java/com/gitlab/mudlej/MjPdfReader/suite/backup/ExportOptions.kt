// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import android.view.View
import android.widget.TextView
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object ExportOptions {
    fun isShowing(): Boolean =
        Dialogs.isAnyShowing() &&
            BackupDialog.title() == BackupStrings.exportTitle &&
            TestEnvironment.uiDevice.hasObject(By.text(BackupStrings.optionSettings))

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the export options dialog is showing", timeoutMs) { isShowing() }
    }

    fun isChecked(label: String): Boolean = checkbox(label).isChecked

    fun isEnabled(label: String): Boolean = checkbox(label).isEnabled

    fun alphaOf(label: String): Float {
        if (!isShowing()) {
            throw ConditionTimeoutException("The export options dialog is not on screen")
        }
        val root = Dialogs.dialogRoot()
        return TestEnvironment.onMainThread { rowView(root, label).alpha }
    }

    fun awaitChecked(label: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the export option \"$label\" reads checked $expected",
            timeoutMs,
            read = { runCatching { isChecked(label) }.getOrNull() },
        ) { it == expected }
    }

    fun tapRow(label: String) {
        val row = row(label)
        row.click()
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    fun toggle(label: String, expected: Boolean) {
        tapRow(label)
        awaitChecked(label, expected)
    }

    fun isExportEnabled(): Boolean = BackupDialog.isButtonEnabled(BackupDialog.POSITIVE)

    fun awaitExportEnabled(expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the Export button reads enabled $expected",
            timeoutMs,
            read = { runCatching { isExportEnabled() }.getOrNull() },
        ) { it == expected }
    }

    fun export() {
        BackupDialog.tap(BackupDialog.POSITIVE)
        TestEnvironment.waitForIdleSync()
    }

    fun cancel() {
        BackupDialog.tap(BackupDialog.NEGATIVE)
        TestEnvironment.waitForIdleSync()
        BackupDialog.awaitGone()
    }

    private fun label(text: String): UiObject2 =
        TestEnvironment.uiDevice.findObject(By.text(text))
            ?: throw ConditionTimeoutException(
                "The export options dialog shows no row labelled \"$text\", visible texts are " +
                    BackupDialog.visibleTexts()
            )

    private fun row(text: String): UiObject2 =
        label(text).parent
            ?: throw ConditionTimeoutException("The row labelled \"$text\" has no container node")

    private fun checkbox(text: String): UiObject2 =
        row(text).findObject(By.checkable(true))
            ?: throw ConditionTimeoutException(
                "The export options row labelled \"$text\" exposes no checkable node. The checkboxes are " +
                    "neither clickable nor focusable, so they are only visible to a reader that sets " +
                    "FLAG_INCLUDE_NOT_IMPORTANT_VIEWS."
            )

    private fun rowView(root: View, text: String): View {
        val label = Dialogs.firstDescendant(root, TextView::class.java) { it.isShown && it.text?.toString() == text }
            ?: throw ConditionTimeoutException("The export options dialog shows no row labelled \"$text\"")
        return label.parent as? View
            ?: throw ConditionTimeoutException("The row labelled \"$text\" has no container")
    }
}
