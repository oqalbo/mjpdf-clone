// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import androidx.appcompat.app.AppCompatDelegate
import com.gitlab.mudlej.MjPdfReader.core.debug.BackupDebugHooks
import com.gitlab.mudlej.MjPdfReader.data.AutoBackupScheduler
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryDebugConfig
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class SettingsCleanupRule : TestWatcher() {

    override fun starting(description: Description) {
        BackupDebugHooks.consumePrepareImportFailure()
        BackupDebugHooks.suppressRestart = true
        runCatching { DictionaryDebugConfig.clear() }
        runCatching { SettingsFixtures.clearSafetyDirectory() }
        runCatching { SettingsFixtures.removeDictionary() }
        SettingsFixtures.deleteOwnedPickerFixtures()
    }

    override fun finished(description: Description) {
        BackupDebugHooks.consumePrepareImportFailure()
        runCatching {
            TestEnvironment.onMainThread {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)
            }
        }
        runCatching { AutoBackupScheduler.cancel(TestEnvironment.targetContext.applicationContext) }
        runCatching { DictionaryDebugConfig.clear() }
        runCatching { SettingsFixtures.clearSafetyDirectory() }
        runCatching { SettingsFixtures.removeDictionary() }
        SettingsFixtures.deleteOwnedPickerFixtures()
        BackupDebugHooks.suppressRestart = false
    }
}
