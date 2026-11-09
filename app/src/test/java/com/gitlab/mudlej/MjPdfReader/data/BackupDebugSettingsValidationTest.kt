// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.core.debug.BackupDebugHooks
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDebugSettingsValidationTest {
    @Test
    fun restartSuppressionIsNeitherImportedNorCountedAsSkipped() {
        for (value in listOf("true", "false")) {
            val (puts, skipped) = BackupManager.validateSettings(
                listOf(BackupSetting(BackupDebugHooks.SUPPRESS_RESTART_KEY, Preferences.kindBoolean, value)),
            )

            assertEquals(0, puts.size)
            assertEquals(0, skipped)
        }
    }
}
