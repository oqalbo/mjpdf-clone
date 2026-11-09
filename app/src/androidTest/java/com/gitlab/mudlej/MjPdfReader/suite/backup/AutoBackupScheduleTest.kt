// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.work.WorkInfo
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class AutoBackupScheduleTest : BackupTestCase() {
    @Test
    @TestId("BKP-44")
    fun enablingAutoBackupWithNoFolderWalksThePickerThenTheTimePicker() {
        val tree = tree()
        launchBackupPage().use {
            BackupPage.tapAutoBackupSwitch()
            BackupTree.pick(tree)

            TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)
            TimePickerRobot.setTime(CHOSEN_HOUR, CHOSEN_MINUTE)
            TimePickerRobot.confirm()

            assertEquals(true, BackupSeed.boolean(Preferences.autoBackupEnabledKey))
            assertEquals(CHOSEN_HOUR, BackupSeed.int(Preferences.autoBackupHourKey))
            assertEquals(CHOSEN_MINUTE, BackupSeed.int(Preferences.autoBackupMinuteKey))

            val treeUri = BackupSeed.string(Preferences.backupFolderTreeUriKey)
            assertNotNull("Turning the switch on did not record a backup folder", treeUri)
            val granted = TestEnvironment.targetContext.contentResolver.persistedUriPermissions
                .singleOrNull { permission -> permission.uri.toString() == treeUri }
            assertNotNull("No persisted grant was taken on $treeUri", granted)
            assertTrue(granted!!.isReadPermission && granted.isWritePermission)

            val enabledAt = BackupSeed.long(Preferences.autoBackupEnabledAtKey) ?: 0L
            assertTrue(
                "autoBackupEnabledAt was not stamped, so the stale notice has no anchor",
                enabledAt > 0L,
            )

            val info = BackupWork.awaitSingleLive()
            assertEquals(WorkInfo.State.ENQUEUED, info.state)
            BackupPage.awaitSummaryContaining(
                BackupStrings.autoBackupTimeTitle,
                BackupStrings.autoBackupTimeSummary(renderedTime(CHOSEN_HOUR, CHOSEN_MINUTE)),
            )
        }
    }

    @Test
    @TestId("BKP-45")
    fun cancellingTheFolderPickerTurnsTheSwitchBackOff() {
        launchBackupPage().use {
            BackupPage.tapAutoBackupSwitch()
            val picker = SafPickerRobot()
            picker.awaitShown()
            picker.cancel()

            BackupPage.awaitShown()
            assertEquals(false, autoBackupEnabledOrDefault())
            BackupPage.awaitSwitchChecked(BackupStrings.autoBackupTitle, expected = false)
            BackupWork.assertStaysWithoutLiveWork()
            assertFalse(
                "Backing out of the picker must leave no backup folder behind",
                BackupSeed.contains(Preferences.backupFolderTreeUriKey),
            )
        }
    }

    @Test
    @TestId("BKP-46")
    fun rotatingDuringTheFolderPickerStillTurnsTheSwitchBackOffOnCancel() {
        launchBackupPage().use {
            BackupPage.tapAutoBackupSwitch()
            val picker = SafPickerRobot()
            picker.awaitShown()
            TestEnvironment.uiDevice.setOrientationLeft()
            TestEnvironment.uiDevice.waitForIdle()
            picker.awaitShown()
            picker.cancel()

            BackupPage.awaitShown(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "The recreated settings fragment lost the pending enable flag and left the " +
                    "preference on while nothing was scheduled",
                false,
                autoBackupEnabledOrDefault(),
            )
            BackupPage.awaitSwitchChecked(BackupStrings.autoBackupTitle, expected = false)
            BackupWork.assertStaysWithoutLiveWork()
        }
    }

    @Test
    @TestId("BKP-47")
    fun turningAutoBackupOffCancelsTheScheduledWork() {
        val tree = tree()
        launchBackupPage().use {
            enableAutoBackup(tree)
            val treeUri = BackupSeed.string(Preferences.backupFolderTreeUriKey)
            BackupWork.awaitSingleLive()

            BackupPage.tapAutoBackupSwitch()
            BackupPage.awaitSwitchChecked(BackupStrings.autoBackupTitle, expected = false)

            assertEquals(false, autoBackupEnabledOrDefault())
            BackupWork.awaitCancelled()
            assertEquals(
                "Turning the feature off must not forget the folder the user chose",
                treeUri,
                BackupSeed.string(Preferences.backupFolderTreeUriKey),
            )
        }
    }

    @Test
    @TestId("BKP-48")
    fun changingTheBackupTimeReschedulesTheWork() {
        val tree = tree()
        launchBackupPage().use {
            enableAutoBackup(tree)
            val before = BackupWork.awaitSingleLive()

            BackupPage.tapBackupTime()
            TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)
            TimePickerRobot.setTime(LATE_HOUR, LATE_MINUTE)
            TimePickerRobot.confirm()

            assertEquals(LATE_HOUR, BackupSeed.int(Preferences.autoBackupHourKey))
            assertEquals(LATE_MINUTE, BackupSeed.int(Preferences.autoBackupMinuteKey))

            val after = BackupWork.awaitDifferentId(before.id)
            assertEquals(WorkInfo.State.ENQUEUED, after.state)
            assertEquals(1, BackupWork.liveInfos().size)
            BackupPage.awaitSummaryContaining(
                BackupStrings.autoBackupTimeTitle,
                BackupStrings.autoBackupTimeSummary(renderedTime(LATE_HOUR, LATE_MINUTE)),
            )
        }
    }

    @Test
    @TestId("BKP-49")
    fun reapplyingTheStartupScheduleDoesNotPushItOut() {
        val tree = tree()
        val recorded = launchBackupPage().use {
            enableAutoBackup(tree, hour = laterToday(), minute = CHOSEN_MINUTE)
            BackupWork.awaitSingleLive()
        }
        val recordedId = recorded.id
        val recordedNextRun = BackupWork.nextScheduleTimeMillis(recorded)

        repeat(RELAUNCH_COUNT) {
            BackupNavigation.relaunchIntoHome()
            AutoBackupControl.reapplyStartupSchedule()
            BackupWork.awaitSameSchedule(recordedId, recordedNextRun)
        }

        val after = BackupWork.awaitSingleLive()
        assertEquals("A relaunch replaced the scheduled job", recordedId, after.id)
        assertEquals(
            "A relaunch pushed the daily backup out to a later slot",
            recordedNextRun,
            BackupWork.nextScheduleTimeMillis(after),
        )
    }

    private fun enableAutoBackup(
        directory: File,
        hour: Int = CHOSEN_HOUR,
        minute: Int = CHOSEN_MINUTE,
    ) {
        BackupPage.tapAutoBackupSwitch()
        BackupTree.pick(directory)
        TimePickerRobot.awaitShown(Await.LONG_TIMEOUT_MS)
        TimePickerRobot.setTime(hour, minute)
        TimePickerRobot.confirm()
        BackupPage.awaitSwitchChecked(BackupStrings.autoBackupTitle, expected = true)
    }

    private fun autoBackupEnabledOrDefault(): Boolean =
        BackupSeed.boolean(Preferences.autoBackupEnabledKey) ?: Preferences.autoBackupEnabledDefault

    private fun renderedTime(hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun laterToday(): Int {
        val now = LocalTime.now().hour
        return if (now < LAST_USEFUL_HOUR) now + 1 else 0
    }

    private companion object {
        const val CHOSEN_HOUR = 7
        const val CHOSEN_MINUTE = 30
        const val LATE_HOUR = 23
        const val LATE_MINUTE = 15
        const val RELAUNCH_COUNT = 2
        const val LAST_USEFUL_HOUR = 22
    }
}
