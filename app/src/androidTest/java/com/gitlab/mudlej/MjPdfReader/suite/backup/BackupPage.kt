// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage

object BackupPage {
    const val SCROLL_ATTEMPTS = 4

    fun intent(): Intent =
        Intent(TestEnvironment.targetContext, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(SettingsActivity.EXTRA_PAGE, SettingsPage.BACKUP.name)

    fun launch(): ActivityScenario<SettingsActivity> =
        ActivityScenario.launch<SettingsActivity>(intent()).also { awaitShown() }

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Screens.awaitResumed(SettingsActivity::class.java, timeoutMs)
        Await.until("the Backup and Restore page lists its rows", timeoutMs) {
            titleNode(BackupStrings.exportTitle) != null
        }
    }

    fun isShown(): Boolean =
        Screens.resumedName() == SettingsActivity::class.java.name &&
            titleNode(BackupStrings.exportTitle) != null

    fun rowTitles(): List<String> =
        TestEnvironment.uiDevice.findObjects(By.res("android:id/title")).mapNotNull { it.text }

    fun hasRow(title: String): Boolean = titleNode(title) != null

    fun awaitRow(title: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the Backup and Restore page shows a row titled \"$title\"", timeoutMs) {
            titleNode(title) != null
        }
    }

    fun summaryOf(title: String): String? =
        titleNode(title)?.parent?.findObject(By.res("android:id/summary"))?.text

    fun awaitSummaryContaining(title: String, fragment: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): String =
        Await.untilValue(
            "the \"$title\" row summary contains \"$fragment\"",
            timeoutMs,
            read = { summaryOf(title) },
        ) { it != null && it.contains(fragment) }!!

    fun tapRow(title: String) {
        val node = requireTitle(title)
        node.click()
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun isSwitchChecked(title: String): Boolean = switchNode(title).isChecked

    fun awaitSwitchChecked(title: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the \"$title\" switch reads checked $expected",
            timeoutMs,
            read = { runCatching { isSwitchChecked(title) }.getOrNull() },
        ) { it == expected }
    }

    fun startExport() {
        tapRow(BackupStrings.exportTitle)
        ExportOptions.awaitShown()
    }

    fun startImport() {
        tapRow(BackupStrings.importTitle)
    }

    fun tapAutoBackupSwitch() {
        tapRow(BackupStrings.autoBackupTitle)
    }

    fun tapBackupTime() {
        tapRow(BackupStrings.autoBackupTimeTitle)
    }

    fun tapRestoreSnapshot() {
        tapRow(BackupStrings.restoreSnapshotTitle)
    }

    private fun requireTitle(title: String): UiObject2 =
        titleNode(title)
            ?: throw ConditionTimeoutException(
                "The Backup and Restore page shows no row titled \"$title\", its rows are ${rowTitles()}"
            )

    private fun switchNode(title: String): UiObject2 {
        val row = requireTitle(title).parent?.parent
            ?: throw ConditionTimeoutException("The row titled \"$title\" has no container node")
        return row.findObject(By.checkable(true))
            ?: throw ConditionTimeoutException("The row titled \"$title\" exposes no switch")
    }

    private fun titleNode(title: String): UiObject2? {
        find(title)?.let { return it }
        val list = TestEnvironment.uiDevice.findObject(By.scrollable(true)) ?: return null
        repeat(SCROLL_ATTEMPTS) {
            val scrolled = runCatching { list.scroll(Direction.DOWN, 0.6f) }.getOrDefault(false)
            TestEnvironment.uiDevice.waitForIdle()
            find(title)?.let { return it }
            if (!scrolled) return null
        }
        return null
    }

    private fun find(title: String): UiObject2? =
        TestEnvironment.uiDevice.findObject(By.res("android:id/title").text(title))
}
