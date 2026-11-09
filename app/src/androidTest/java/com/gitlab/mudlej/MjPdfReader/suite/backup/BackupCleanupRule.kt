// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.core.debug.BackupDebugHooks
import com.gitlab.mudlej.MjPdfReader.data.AutoBackupScheduler
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class BackupCleanupRule : TestWatcher() {
    override fun starting(description: Description) {
        BackupDebugHooks.consumePrepareImportFailure()
        BackupDebugHooks.suppressRestart = true
        runCatching { AutoBackupScheduler.cancel(TestEnvironment.targetContext.applicationContext) }
        runCatching { BackupTree.clearSafetyDirectory() }
        BackupFixtures.deleteOwnedPickerFixtures()
    }

    override fun finished(description: Description) {
        BackupDebugHooks.consumePrepareImportFailure()
        runCatching { AutoBackupScheduler.cancel(TestEnvironment.targetContext.applicationContext) }
        runCatching { BackupTree.clearSafetyDirectory() }
        runCatching { BackupFixtures.deleteOversized() }
        BackupFixtures.deleteOwnedPickerFixtures()
        BackupDebugHooks.suppressRestart = false
    }
}
