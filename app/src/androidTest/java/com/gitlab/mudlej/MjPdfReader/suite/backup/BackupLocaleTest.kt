// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.BackupManager
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class BackupLocaleTest : BackupTestCase() {
    @Test
    @TestId("BKP-42")
    fun backupFileNamesUseAsciiDigitsOnAPersianLocaleDevice() {
        val tree = tree()
        launchBackupPage().use { grantFolder(tree) }
        leaveBackupPage()

        BackupSeed.record(hash = BackupHashes.ONE)
        locale.set(PERSIAN)

        launchBackupPage().use {
            Await.until("the backup page actually uses the Persian locale") {
                TestEnvironment.onMainThread {
                    TestEnvironment.resumedActivityOfType(SettingsActivity::class.java)
                        ?.resources?.configuration?.locales?.get(0)?.toLanguageTag() == PERSIAN
                }
            }
            BackupPage.startExport()
            ExportOptions.export()
            val written = BackupTree.awaitManualBackup(tree)

            assertTrue(
                "The exported file is named \"${written.name}\", which the app's own manual backup " +
                    "pattern does not match",
                BackupTree.manualNamePattern.matches(written.name),
            )
            assertFalse(
                "The file name \"${written.name}\" carries a non ASCII digit",
                written.name.any { it.isDigit() && it !in '0'..'9' },
            )

            val json = BackupJson.of(written)
            assertEquals(BackupManager.SCHEMA_VERSION, json.schemaVersion)
            assertEquals(1, json.records!!.size)
        }
    }

    private companion object {
        const val PERSIAN = "fa-IR"
    }
}
