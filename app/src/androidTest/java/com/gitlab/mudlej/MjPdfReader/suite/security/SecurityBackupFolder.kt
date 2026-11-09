// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.content.Intent
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.history.ReadingHistoryScreen
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.settings.SettingsScreen
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage

object SecurityBackupFolder {
    fun chooseThroughSettings() {
        TestEnvironment.targetContext.startActivity(
            SettingsScreen.pageIntent(SettingsPage.BACKUP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)

        tapRow(string(R.string.backup_folder_title))

        val picker = SafPickerRobot()
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
        picker.navigateToDocuments()
        picker.confirmUseThisFolder()
        picker.allowAccessIfPrompted()
        picker.awaitPickerDismissed(Await.LONG_TIMEOUT_MS)

        Await.until("the backup folder preference is written", Await.LONG_TIMEOUT_MS) {
            !storedTreeUri().isNullOrEmpty()
        }

        ReadingHistoryScreen.leaveSettings()
    }

    fun storedTreeUri(): String? =
        TestEnvironment.defaultPreferences.getString(Preferences.backupFolderTreeUriKey, null)

    private fun tapRow(title: String) {
        SettingsScreen.awaitRow(title, Await.LONG_TIMEOUT_MS)
        SettingsScreen.tapRow(title)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
