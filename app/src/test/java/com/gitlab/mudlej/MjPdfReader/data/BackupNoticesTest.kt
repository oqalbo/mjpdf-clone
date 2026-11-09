// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.SharedPreferences
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNoticesTest {

    private val preferences = Preferences(InMemorySharedPreferences())
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60L * 1000L

    @Test
    fun onlyFailuresNewerThanTheAcknowledgedRunAreShown() {
        preferences.setAutoBackupErrorAcknowledgedRun(200L)
        for ((run, expected) in listOf(199L to false, 200L to false, 201L to true)) {
            preferences.setAutoBackupLastResult(run, "Backup failed")
            assertEquals("Last run $run", expected, BackupNotices.shouldShowFailureNotice(preferences))
        }
    }

    @Test
    fun successfulRunDoesNotShowAFailureNotice() {
        preferences.setAutoBackupLastResult(201L, null)
        preferences.setAutoBackupErrorAcknowledgedRun(200L)

        assertFalse(BackupNotices.shouldShowFailureNotice(preferences))
    }

    @Test
    fun acknowledgmentHidesTheCurrentFailureButNotTheNextOne() {
        preferences.setAutoBackupLastResult(201L, "Backup failed")
        assertTrue(BackupNotices.shouldShowFailureNotice(preferences))

        BackupNotices.acknowledge(preferences)

        assertEquals(201L, preferences.getAutoBackupErrorAcknowledgedRun())
        assertFalse(BackupNotices.shouldShowFailureNotice(preferences))
        preferences.setAutoBackupLastResult(202L, "Backup failed again")
        assertTrue(BackupNotices.shouldShowFailureNotice(preferences))
    }

    @Test
    fun staleNoticeStartsOnlyAfterThirtySixHours() {
        preferences.setAutoBackupEnabled(true)
        preferences.setAutoBackupEnabledAt(now - 72L * hour)
        for ((age, expected) in listOf(
            35L * hour + 59L * 60_000L to false,
            36L * hour to false,
            36L * hour + 1L to true,
        )) {
            preferences.setAutoBackupLastResult(now - age, null)
            assertEquals("Run age $age", expected, BackupNotices.shouldShowStaleNotice(preferences) { now })
        }
    }

    @Test
    fun recentEnableTimeOverridesAnOldRun() {
        preferences.setAutoBackupEnabled(true)
        preferences.setAutoBackupEnabledAt(now - hour)
        preferences.setAutoBackupLastResult(now - 72L * hour, null)

        assertFalse(BackupNotices.shouldShowStaleNotice(preferences) { now })
    }

    @Test
    fun enabledBackupWithNoRunsBecomesStale() {
        preferences.setAutoBackupEnabled(true)
        preferences.setAutoBackupEnabledAt(now - 36L * hour - 1L)

        assertTrue(BackupNotices.shouldShowStaleNotice(preferences) { now })
    }

    @Test
    fun missingEnableTimeDoesNotShowStaleOnTheFirstCheck() {
        preferences.setAutoBackupEnabled(true)
        preferences.setAutoBackupEnabledAt(0L)

        val stale = BackupNotices.shouldShowStaleNotice(preferences) {
            preferences.getAutoBackupEnabledAt().also { assertTrue(it > 0L) }
        }

        assertFalse(stale)
    }

    @Test
    fun disabledBackupNeverShowsStale() {
        preferences.setAutoBackupEnabled(false)
        preferences.setAutoBackupEnabledAt(now - 72L * hour)
        preferences.setAutoBackupLastResult(now - 72L * hour, null)

        assertFalse(BackupNotices.shouldShowStaleNotice(preferences) { now })
    }
}
