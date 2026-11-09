// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class BackupNoticesTest : BackupTestCase() {
    @Test
    @TestId("BKP-54")
    @TestDuration(DurationCategory.NORMAL)
    fun theFailureNoticeAppearsOnHomeAndStopsAfterAcknowledgement() {
        val failedAt = System.currentTimeMillis()
        seedFailedRun(failedAt)

        val home = BackupNavigation.openHome()
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        BackupSnackbar.awaitText(BackupStrings.failureNotice, Await.LONG_TIMEOUT_MS)
        assertEquals(BackupStrings.noticeAction, BackupSnackbar.actionText())

        BackupSnackbar.tapAction()
        BackupSeed.awaitPreference(Preferences.autoBackupErrorAcknowledgedRunKey, failedAt)
        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        BackupPage.awaitShown(Await.LONG_TIMEOUT_MS)

        val relaunchedHome = BackupNavigation.relaunchIntoHome()
        relaunchedHome.awaitRendered(Await.LONG_TIMEOUT_MS)
        BackupSnackbar.assertNoneReading(BackupStrings.failureNotice)
    }

    @Test
    @TestId("BKP-55")
    fun theNoticeActionOpensTheBackupSettingsPage() {
        val failedAt = System.currentTimeMillis()
        seedFailedRun(failedAt)

        val home = BackupNavigation.openHome()
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        BackupSnackbar.awaitText(BackupStrings.failureNotice, Await.LONG_TIMEOUT_MS)

        BackupSnackbar.tapAction()

        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        BackupPage.awaitShown(Await.LONG_TIMEOUT_MS)
        Screens.awaitTitle(BackupStrings.backupPageTitle, Await.LONG_TIMEOUT_MS)
        listOf(
            BackupStrings.backupFolderTitle,
            BackupStrings.autoBackupTitle,
            BackupStrings.autoBackupTimeTitle,
            BackupStrings.exportTitle,
            BackupStrings.importTitle,
        ).forEach { title ->
            assertTrue(
                "The backup page reached from the notice shows no row titled \"$title\", it lists " +
                    BackupPage.rowTitles(),
                BackupPage.hasRow(title),
            )
        }
        BackupSeed.awaitPreference(Preferences.autoBackupErrorAcknowledgedRunKey, failedAt)
        BackupNavigation.finishAppTask()
    }

    @Test
    @TestId("BKP-56")
    fun theStaleNoticeRespectsAFreshlyEnabledFeature() {
        BackupSeed.settings(mapOf(Preferences.autoBackupEnabledKey to true))
        BackupSeed.removeSettings(
            Preferences.autoBackupEnabledAtKey,
            Preferences.autoBackupLastErrorKey,
        )
        BackupSeed.autoBackupLastResult(runAt = 0L, error = null)
        val before = System.currentTimeMillis()

        val home = BackupNavigation.openHome()
        home.awaitRendered(Await.LONG_TIMEOUT_MS)

        BackupSnackbar.assertNoneReading(BackupStrings.staleNotice)
        val anchor = BackupSeed.long(Preferences.autoBackupEnabledAtKey)
        assertNotNull(
            "An install that predates the anchor must have one stamped on the first check",
            anchor,
        )
        assertTrue(
            "The anchor $anchor was not stamped at roughly now, which was $before",
            anchor!! >= before && anchor <= System.currentTimeMillis() + ANCHOR_SLACK_MS,
        )
    }

    @Test
    @TestId("BKP-57")
    fun theStaleNoticeAppearsAfterADayAndAHalfWithoutARun() {
        BackupSeed.autoBackupEnabled(enabledAt = BackupClock.hoursAgo(STALE_AGE_HOURS))
        BackupSeed.removeSettings(Preferences.autoBackupLastErrorKey)
        BackupSeed.autoBackupLastResult(runAt = 0L, error = null)
        BackupSeed.settings(mapOf(Preferences.homeDisabledKey to false))

        val home = BackupNavigation.openHome()
        home.awaitRendered(Await.LONG_TIMEOUT_MS)

        BackupSnackbar.awaitText(BackupStrings.staleNotice, Await.LONG_TIMEOUT_MS)
        BackupDialog.assertNoDialogTitled(BackupStrings.importTitle)
    }

    private fun seedFailedRun(failedAt: Long) {
        BackupSeed.autoBackupEnabled(enabledAt = failedAt)
        BackupSeed.autoBackupLastResult(runAt = failedAt, error = FAILURE_REASON, acknowledgedRun = 0L)
        BackupSeed.settings(mapOf(Preferences.homeDisabledKey to false))
    }

    private companion object {
        const val FAILURE_REASON = "boom"
        const val STALE_AGE_HOURS = 48L
        const val ANCHOR_SLACK_MS = 5_000L
    }
}
