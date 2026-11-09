// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.data.AutoBackupScheduler
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object AutoBackupControl {
    fun cancel() {
        AutoBackupScheduler.cancel(TestEnvironment.targetContext.applicationContext)
    }

    fun reapplyStartupSchedule() {
        val context = TestEnvironment.targetContext.applicationContext
        val preferences = Preferences(PreferenceManager.getDefaultSharedPreferences(context))
        if (preferences.getAutoBackupEnabled()) {
            AutoBackupScheduler.ensureScheduled(
                context,
                preferences.getAutoBackupHour(),
                preferences.getAutoBackupMinute(),
            )
        }
    }
}
