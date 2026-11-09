// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ExportFolderTest : BackupTestCase() {
    @Test
    @TestId("BKP-11")
    fun cancellingTheFolderPickerCancelsTheExport() {
        val tree = tree()
        val picker = SafPickerRobot()
        launchBackupPage().use {
            BackupPage.startExport()
            ExportOptions.export()
            picker.awaitShown()
            picker.cancel()

            assertFalse(
                "Backing out of the picker must leave backupFolderTreeUri unset",
                BackupSeed.contains(Preferences.backupFolderTreeUriKey),
            )
            BackupTree.assertNoFolderCreated(tree)
            BackupDialog.assertNoDialogTitled(BackupStrings.exportTitle)
            BackupPage.awaitShown()
        }
    }

    @Test
    @TestId("BKP-12")
    fun exportOptionsSurviveThePickerAndARotation() {
        val tree = tree()
        BackupSeed.record(hash = BackupHashes.ONE, password = BackupFixtures.SAVED_PASSWORD)
        BackupSeed.record(hash = BackupHashes.TWO)
        BackupSeed.bookmark(BackupHashes.ONE, 1)
        BackupSeed.bookmark(BackupHashes.TWO, 2)

        launchBackupPage().use {
            BackupPage.startExport()
            ExportOptions.toggle(BackupStrings.optionSettings, expected = false)
            ExportOptions.toggle(BackupStrings.optionPasswords, expected = true)
            ExportOptions.export()

            val picker = SafPickerRobot()
            picker.awaitShown()
            TestEnvironment.uiDevice.setOrientationLeft()
            TestEnvironment.uiDevice.waitForIdle()
            picker.awaitShown()
            BackupTree.pick(tree, picker)

            val json = BackupJson.of(BackupTree.awaitManualBackup(tree))
            assertFalse(
                "The export that unchecked Settings and Preferences wrote a settings member anyway",
                json.has("settings"),
            )
            assertEquals(2, json.records!!.size)
            assertEquals(
                "The export that checked Include saved PDF passwords dropped the password",
                BackupFixtures.SAVED_PASSWORD,
                json.record(BackupHashes.ONE)!!.getString("password"),
            )
            assertEquals(
                "The MJ PDF folder should hold exactly one JSON file",
                1,
                BackupTree.manualBackups(tree).size,
            )
            BackupTree.awaitNoTemporaryFiles(tree)
        }
    }

    @Test
    @TestId("BKP-13")
    fun exportReportsAnUnavailableFolderAndReopensThePicker() {
        val tree = tree()
        val target = File(tree, NESTED_TARGET_NAME).also { it.mkdirs() }

        launchBackupPage().use {
            grantFolder(target)
            val grantedUri = BackupSeed.string(Preferences.backupFolderTreeUriKey)

            Shell.execute("rm -rf ${target.absolutePath}")
            Await.until("the granted directory ${target.absolutePath} is gone", Await.DEFAULT_TIMEOUT_MS) {
                !target.exists()
            }

            BackupPage.startExport()
            ExportOptions.export()

            Toasts.awaitText(BackupStrings.folderUnavailable)
            SafPickerRobot().awaitShown()
            assertEquals(
                "The old tree uri must survive until a new folder is actually picked",
                grantedUri,
                BackupSeed.string(Preferences.backupFolderTreeUriKey),
            )
            assertTrue(
                "The test tree itself must survive so its teardown can sweep it",
                tree.isDirectory,
            )
            SafPickerRobot().cancel()
        }
    }

    private companion object {
        const val NESTED_TARGET_NAME = "BackupTarget"
    }
}
