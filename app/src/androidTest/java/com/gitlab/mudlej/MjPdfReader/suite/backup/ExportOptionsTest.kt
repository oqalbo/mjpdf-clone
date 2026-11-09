// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ExportOptionsTest : BackupTestCase() {
    @Test
    @TestId("BKP-06")
    fun passwordsRowIsGatedByTheHistoryRow() {
        val tree = tree()
        launchBackupPage().use {
            grantFolder(tree)
            BackupPage.startExport()

            ExportOptions.toggle(BackupStrings.optionPasswords, expected = true)
            assertTrue(
                "The passwords checkbox must be enabled while Reading History is on",
                ExportOptions.isEnabled(BackupStrings.optionPasswords),
            )

            ExportOptions.toggle(BackupStrings.optionHistory, expected = false)
            ExportOptions.awaitChecked(BackupStrings.optionPasswords, expected = false)
            assertFalse(
                "The passwords checkbox must be disabled once Reading History is off",
                ExportOptions.isEnabled(BackupStrings.optionPasswords),
            )

            ExportOptions.tapRow(BackupStrings.optionPasswords)
            assertFalse(
                "Tapping the disabled passwords row must do nothing",
                ExportOptions.isChecked(BackupStrings.optionPasswords),
            )

            ExportOptions.toggle(BackupStrings.optionHistory, expected = true)
            assertTrue(
                "The passwords checkbox must be enabled again once Reading History is back on",
                ExportOptions.isEnabled(BackupStrings.optionPasswords),
            )
            assertFalse(
                "Re-enabling Reading History must not silently re-check the passwords row",
                ExportOptions.isChecked(BackupStrings.optionPasswords),
            )

            ExportOptions.cancel()
        }
    }

    @Test
    @TestId("BKP-07")
    fun exportButtonIsDisabledWhenBothSectionsAreOff() {
        val tree = tree()
        launchBackupPage().use {
            grantFolder(tree)
            BackupPage.startExport()

            ExportOptions.toggle(BackupStrings.optionSettings, expected = false)
            ExportOptions.awaitExportEnabled(expected = true)

            ExportOptions.toggle(BackupStrings.optionHistory, expected = false)
            ExportOptions.awaitExportEnabled(expected = false)

            ExportOptions.toggle(BackupStrings.optionSettings, expected = true)
            ExportOptions.awaitExportEnabled(expected = true)

            ExportOptions.cancel()
            BackupTree.assertNothingWritten(tree)
        }
    }

    @Test
    @TestId("BKP-08")
    fun cancellingTheOptionsDialogWritesNothing() {
        val tree = tree()
        launchBackupPage().use {
            grantFolder(tree)
            BackupPage.startExport()
            ExportOptions.cancel()

            BackupTree.assertNothingWritten(tree)
            BackupDialog.assertNoDialogTitled(BackupStrings.exportTitle)
        }
    }
}
