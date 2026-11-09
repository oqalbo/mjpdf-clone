// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupTree
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SettingsRotationTest : SettingsTestCase() {

    private val picker = SafPickerRobot()

    @Test
    @TestId("SET-82")
    @TestDuration(DurationCategory.SHORT)
    fun rotatingWithAChoiceDialogOpenCommitsNothing() {
        launchSettingsOn(SettingsPage.READING).use {
            openChoiceDialog(SettingsStrings.of(R.string.reading_mode_title))

            Rotation.toLandscape(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)

            SettingsDialog.awaitDismissed(Await.LONG_TIMEOUT_MS)
            SettingsPrefs.assertUnset(
                Preferences.singlePageModeKey,
                Preferences.horizontalScrollKey,
                Preferences.dualPageModeKey,
            )
            assertEquals(
                "the Reading page is still the one on screen",
                SettingsStrings.pageTitle(SettingsPage.READING),
                SettingsScreen.title(),
            )
            assertTrue(
                "the row summary is unchanged at Continuous",
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.reading_mode_title)).summaryOrEmpty()
                    .startsWith(SettingsStrings.of(R.string.reading_mode_continuous)),
            )

            openChoiceDialog(SettingsStrings.of(R.string.reading_mode_title))
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
        }
    }

    @Test
    @TestId("SET-83")
    fun theAutoBackupTimePickerSurvivesRotationAndStillWritesItsTime() {
        val tree = testDirectory()

        launchSettingsOn(SettingsPage.BACKUP).use {
            SettingsScreen.tapRow(SettingsStrings.of(R.string.backup_folder_title))
            BackupTree.pick(tree, picker, Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
            SettingsPrefs.awaitPresent(Preferences.backupFolderTreeUriKey, Await.LONG_TIMEOUT_MS)

            SettingsScreen.tapRow(SettingsStrings.of(R.string.auto_backup_title))
            SettingsPrefs.awaitBoolean(Preferences.autoBackupEnabledKey, true)
            TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)
            TimePickerRobot.confirm()

            SettingsScreen.tapRow(SettingsStrings.of(R.string.auto_backup_time_title))
            TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)

            Rotation.toLandscape(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)

            TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)
            TimePickerRobot.setTime(6, 30)
            TimePickerRobot.confirm()

            SettingsPrefs.awaitInt(Preferences.autoBackupHourKey, 6)
            SettingsPrefs.awaitInt(Preferences.autoBackupMinuteKey, 30)
        }
    }
}
