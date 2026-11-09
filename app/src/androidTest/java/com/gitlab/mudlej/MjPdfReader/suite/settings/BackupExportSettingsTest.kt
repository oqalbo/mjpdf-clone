// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.BackupFolder
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.BackupFile
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupStrings
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupTree
import com.gitlab.mudlej.MjPdfReader.suite.backup.ExportOptions
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class BackupExportSettingsTest : SettingsTestCase() {

    private val picker = SafPickerRobot()

    @Test
    @TestId("SET-63")
    fun theExportOptionsDialogRefusesAnEmptyBackupAndTiesPasswordsToHistory() {
        launchSettingsOn(SettingsPage.BACKUP).use {
            SettingsScreen.tapRow(exportTitle)
            ExportOptions.awaitShown()

            assertTrue("settings starts checked", ExportOptions.isChecked(BackupStrings.optionSettings))
            assertTrue("history starts checked", ExportOptions.isChecked(BackupStrings.optionHistory))
            assertFalse("passwords starts unchecked", ExportOptions.isChecked(BackupStrings.optionPasswords))
            assertTrue("passwords starts usable", ExportOptions.isEnabled(BackupStrings.optionPasswords))
            assertTrue("Export starts enabled", ExportOptions.isExportEnabled())

            ExportOptions.tapRow(BackupStrings.optionHistory)
            ExportOptions.awaitChecked(BackupStrings.optionHistory, false)
            assertFalse(
                "passwords cannot be exported without history",
                ExportOptions.isEnabled(BackupStrings.optionPasswords),
            )
            assertFalse(
                "passwords is unchecked when history goes",
                ExportOptions.isChecked(BackupStrings.optionPasswords),
            )
            assertEquals(
                "the disabled passwords row is drawn at half alpha",
                DISABLED_ALPHA,
                ExportOptions.alphaOf(BackupStrings.optionPasswords),
                ALPHA_TOLERANCE,
            )
            assertTrue("settings alone is still exportable", ExportOptions.isExportEnabled())

            ExportOptions.tapRow(BackupStrings.optionSettings)
            ExportOptions.awaitChecked(BackupStrings.optionSettings, false)
            assertFalse("a backup that would contain nothing cannot be started", ExportOptions.isExportEnabled())

            ExportOptions.tapRow(BackupStrings.optionHistory)
            ExportOptions.awaitChecked(BackupStrings.optionHistory, true)
            ExportOptions.tapRow(BackupStrings.optionPasswords)
            ExportOptions.awaitChecked(BackupStrings.optionPasswords, true)
            assertTrue("history alone is exportable again", ExportOptions.isExportEnabled())

            ExportOptions.cancel()
            SettingsDialog.awaitDismissed()
        }
    }

    @Test
    @TestId("SET-64")
    fun anExportCarriesWhatTheSettingsScreenWroteAndReportsWhatItContained() {
        val tree = testDirectory()
        SettingsRoom.seedRecord("h2export00000000000000000000001")
        SettingsRoom.seedRecord("h2export00000000000000000000002")
        SettingsRoom.seedRecord("h2export00000000000000000000003")
        SettingsRoom.seedBookmark("h2export00000000000000000000001", 2)
        SettingsRoom.seedBookmark("h2export00000000000000000000002", 5)

        launchSettingsOn(SettingsPage.APPEARANCE).use {
            toggleSwitch(SettingsStrings.of(R.string.quality), true)
            toggleSwitch(SettingsStrings.of(R.string.anti_aliasing), false)
        }
        SettingsPrefs.awaitBoolean(Preferences.highQualityKey, true)
        SettingsPrefs.awaitBoolean(Preferences.antiAliasingKey, false)

        launchSettingsOn(SettingsPage.BACKUP).use {
            grantTree(tree)

            SettingsScreen.tapRow(exportTitle)
            ExportOptions.awaitShown()
            ExportOptions.export()

            val produced = BackupTree.awaitManualBackup(tree)
            assertTrue(
                "the produced file is named for the moment it was written, it reads ${produced.name}",
                BackupTree.manualNamePattern.matches(produced.name),
            )
            val keys = BackupFile(produced).settingKeys()
            assertTrue(
                "the export carries the key the High Quality row wrote, it carries $keys",
                Preferences.highQualityKey in keys,
            )
            assertTrue(
                "the export carries the key the Anti Aliasing row wrote, it carries $keys",
                Preferences.antiAliasingKey in keys,
            )

            SettingsDialog.awaitShown()
            val message = SettingsDialog.message().orEmpty()
            assertTrue(
                "the result names the file it wrote, it reads \"$message\"",
                message.contains(SettingsStrings.of(R.string.backup_export_saved_file, produced.name)),
            )
            assertTrue(
                "the result counts the settings it exported, it reads \"$message\"",
                message.contains(SettingsStrings.of(R.string.backup_export_done_settings, keys.size)),
            )
            assertTrue(
                "the result counts the three records and two bookmarks, it reads \"$message\"",
                message.contains(SettingsStrings.of(R.string.backup_export_done_history, 3, 2)),
            )
            SettingsDialog.tap(SettingsDialog.POSITIVE)
            SettingsDialog.awaitDismissed()
        }
    }

    @Test
    @TestId("SET-65")
    fun choosingABackupFolderStoresItAndShowsThePathOnTheSameRow() {
        val tree = testDirectory()

        launchSettingsOn(SettingsPage.BACKUP).use {
            assertEquals(
                "with no folder chosen the row explains what choosing one does",
                SettingsStrings.of(R.string.backup_folder_summary_unset),
                SettingsScreen.awaitRow(folderTitle).summary,
            )

            SettingsScreen.tapRow(folderTitle)
            BackupTree.pick(tree, picker, Await.LONG_TIMEOUT_MS)

            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitRowSummary(folderTitle, Await.LONG_TIMEOUT_MS) {
                it != null && it != SettingsStrings.of(R.string.backup_folder_summary_unset)
            }
            val summary = SettingsScreen.row(folderTitle).summaryOrEmpty()
            assertTrue(
                "the summary names where backups are saved, it reads \"$summary\"",
                summary.contains(BackupFolder.folderName),
            )
            assertEquals(
                "the picker returned to the Backup page rather than to the root list",
                SettingsStrings.pageTitle(SettingsPage.BACKUP),
                SettingsScreen.title(),
            )
        }
    }

    @Test
    @TestId("SET-66")
    fun backingOutOfTheFolderPickerCancelsTheExportAndTheNextAttemptAsksAgain() {
        launchSettingsOn(SettingsPage.BACKUP).use {
            SettingsScreen.tapRow(exportTitle)
            ExportOptions.awaitShown()
            ExportOptions.export()

            picker.awaitShown()
            picker.cancel()
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)

            SettingsPrefs.assertStaysUnset(Preferences.backupFolderTreeUriKey)
            SettingsDialog.assertNoneWithin()

            SettingsScreen.tapRow(exportTitle)
            ExportOptions.awaitShown()
            ExportOptions.export()
            picker.awaitShown()
            picker.cancel()
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("SET-67")
    fun autoBackupRevertsToOffWhenTheFolderPickerIsBackedOutOf() {
        launchSettingsOn(SettingsPage.BACKUP).use {
            SettingsScreen.tapRow(autoBackupTitle)

            picker.awaitShown()
            picker.cancel()
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)

            SettingsPrefs.awaitBoolean(Preferences.autoBackupEnabledKey, false)
            SettingsScreen.awaitChecked(autoBackupTitle, false)
            AutoBackupWork.assertStaysWithoutLiveWork()
            SettingsPrefs.assertUnset(Preferences.autoBackupEnabledAtKey)
        }
    }

    @Test
    @TestId("SET-68")
    fun anAlreadyChosenFolderSkipsThePickerAndGoesStraightToTheTimePicker() {
        val tree = testDirectory()

        launchSettingsOn(SettingsPage.BACKUP).use {
            grantTree(tree)

            SettingsScreen.tapRow(autoBackupTitle)

            SettingsPrefs.awaitBoolean(Preferences.autoBackupEnabledKey, true)
            TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the picker opens at the shipped default hour",
                Preferences.autoBackupHourDefault,
                TimePickerRobot.hour(),
            )
            assertEquals(
                "the picker opens at the shipped default minute",
                Preferences.autoBackupMinuteDefault,
                TimePickerRobot.minute(),
            )

            TimePickerRobot.cancel()
            SettingsScreen.awaitChecked(autoBackupTitle, true)
        }
    }

    @Test
    @TestId("SET-69")
    fun theTimePickerWritesTheHourAndMinuteTheDailyBackupRunsAt() {
        val tree = testDirectory()

        launchSettingsOn(SettingsPage.BACKUP).use {
            enableAutoBackup(tree)

            SettingsScreen.tapRow(autoBackupTimeTitle)
            TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)
            TimePickerRobot.setTime(7, 45)
            TimePickerRobot.confirm()

            SettingsPrefs.awaitInt(Preferences.autoBackupHourKey, 7)
            SettingsPrefs.awaitInt(Preferences.autoBackupMinuteKey, 45)
            SettingsScreen.awaitRowSummaryContains(
                autoBackupTimeTitle,
                SettingsStrings.of(R.string.auto_backup_time_summary, localizedTime(7, 45)),
            )
        }
    }

    @Test
    @TestId("SET-70")
    fun turningAutoBackupOffIsOneTapThatAsksNothing() {
        val tree = testDirectory()

        launchSettingsOn(SettingsPage.BACKUP).use {
            enableAutoBackup(tree)
            AutoBackupWork.awaitLiveCount(1, Await.LONG_TIMEOUT_MS)

            SettingsScreen.tapRow(autoBackupTitle)

            SettingsPrefs.awaitBoolean(Preferences.autoBackupEnabledKey, false)
            SettingsScreen.awaitChecked(autoBackupTitle, false)
            AutoBackupWork.awaitLiveCount(0, Await.LONG_TIMEOUT_MS)
            assertFalse("no folder picker appears when the switch goes off", picker.isShowing())
            assertEquals("no time picker appears when the switch goes off", null, TimePickerRobot.pickerOrNull())
        }
    }

    @Test
    @TestId("SET-84")
    fun aPendingExportSurvivesTheSettingsScreenBeingRebuiltBehindThePicker() {
        val tree = testDirectory()

        launchSettingsOn(SettingsPage.BACKUP).use {
            SettingsScreen.tapRow(exportTitle)
            ExportOptions.awaitShown()
            ExportOptions.export()
            picker.awaitShown()

            Screens.awaitStage(SettingsActivity::class.java, Stage.STOPPED, Await.LONG_TIMEOUT_MS)
            val previous = Screens.liveActivitiesOfType(SettingsActivity::class.java).single()
            TestEnvironment.onMainThread { previous.recreate() }
            Screens.awaitReplacement(previous, SettingsActivity::class.java)
            picker.awaitShown()

            BackupTree.pick(tree, picker, Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)

            SettingsPrefs.awaitPresent(Preferences.backupFolderTreeUriKey, Await.LONG_TIMEOUT_MS)
            val produced = BackupTree.awaitManualBackup(tree)
            SettingsDialog.awaitShown()
            assertTrue(
                "the restored pending export reports the file it wrote",
                SettingsDialog.message().orEmpty().contains(produced.name),
            )
            SettingsDialog.tap(SettingsDialog.POSITIVE)
            SettingsDialog.awaitDismissed()
        }
    }

    private fun grantTree(tree: File) {
        SettingsScreen.tapRow(folderTitle)
        BackupTree.pick(tree, picker, Await.LONG_TIMEOUT_MS)
        SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
        SettingsPrefs.awaitPresent(Preferences.backupFolderTreeUriKey, Await.LONG_TIMEOUT_MS)
    }

    private fun enableAutoBackup(tree: File) {
        grantTree(tree)
        SettingsScreen.tapRow(autoBackupTitle)
        SettingsPrefs.awaitBoolean(Preferences.autoBackupEnabledKey, true)
        TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)
        TimePickerRobot.confirm()
        SettingsScreen.awaitChecked(autoBackupTitle, true)
    }

    private fun localizedTime(hour: Int, minute: Int): String =
        java.time.LocalTime.of(hour, minute).format(
            java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        )

    private val exportTitle: String get() = SettingsStrings.of(R.string.backup_export_title)
    private val folderTitle: String get() = SettingsStrings.of(R.string.backup_folder_title)
    private val autoBackupTitle: String get() = SettingsStrings.of(R.string.auto_backup_title)
    private val autoBackupTimeTitle: String get() = SettingsStrings.of(R.string.auto_backup_time_title)

    private companion object {
        const val DISABLED_ALPHA = 0.5f
        const val ALPHA_TOLERANCE = 0.01f
    }
}
