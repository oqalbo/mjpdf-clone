// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class AutoBackupWorkerTest : BackupTestCase() {
    @Test
    @TestId("BKP-50")
    fun theAutoBackupWorkerWritesAFileWithNoPasswords() {
        val tree = tree()
        launchBackupPage().use { grantFolder(tree) }
        leaveBackupPage()

        BackupSeed.autoBackupEnabled()
        BackupSeed.record(hash = BackupHashes.ONE, password = BackupFixtures.SAVED_PASSWORD)
        BackupSeed.record(hash = BackupHashes.TWO)
        BackupSeed.bookmark(BackupHashes.ONE, 1)
        BackupSeed.bookmark(BackupHashes.ONE, 2)
        BackupSeed.bookmark(BackupHashes.TWO, 3)
        BackupSeed.settings(FOUR_SETTINGS)

        val result = BackupWork.runWorker()
        assertTrue("The worker did not report success, it returned $result", BackupWork.isSuccess(result))

        val written = BackupTree.awaitAutoBackup(tree)
        assertEquals(
            "The scheduled backup must produce exactly one file",
            listOf(written.name),
            BackupTree.fileNames(tree),
        )
        assertTrue(
            "The scheduled backup is named \"${written.name}\", which is not the auto backup shape",
            BackupTree.autoNamePattern.matches(written.name),
        )

        val json = BackupJson.of(written)
        assertTrue("The scheduled backup carries no settings", json.has("settings"))
        assertEquals(2, json.records!!.size)
        assertEquals(3, json.bookmarks!!.size)
        json.records!!.forEach { record ->
            assertFalse(
                "The scheduled backup wrote a saved password, which its own summary promises it never does",
                record.has("password"),
            )
        }
        assertFalse(
            "The scheduled backup's raw bytes contain the saved password",
            json.containsText(BackupFixtures.SAVED_PASSWORD),
        )

        assertTrue(
            "autoBackupLastRun was not stamped by a successful run",
            (BackupSeed.long(Preferences.autoBackupLastRunKey) ?: 0L) > 0L,
        )
        assertNull(
            "A successful run must clear the last error",
            BackupSeed.string(Preferences.autoBackupLastErrorKey),
        )
        BackupTree.awaitNoTemporaryFiles(tree)
    }

    @Test
    @TestId("BKP-51")
    fun theWorkerRecordsAnErrorWhenTheFolderIsGone() {
        val tree = tree()
        val target = File(tree, NESTED_TARGET_NAME).also { it.mkdirs() }
        launchBackupPage().use { grantFolder(target) }
        leaveBackupPage()

        BackupSeed.autoBackupEnabled()
        BackupSeed.record(hash = BackupHashes.ONE)

        Shell.execute("rm -rf ${target.absolutePath}")
        Await.until("the granted directory is gone", Await.DEFAULT_TIMEOUT_MS) { !target.exists() }

        val result = BackupWork.runWorker()
        assertTrue(
            "A permanently unavailable folder must not be retried, the worker returned $result",
            BackupWork.isSuccess(result),
        )
        assertEquals(
            BackupStrings.errorFolderUnavailable,
            BackupSeed.string(Preferences.autoBackupLastErrorKey),
        )
        assertTrue(
            "autoBackupLastRun was not stamped by a failed attempt, so the failure notice has no anchor",
            (BackupSeed.long(Preferences.autoBackupLastRunKey) ?: 0L) > 0L,
        )
        assertTrue(
            "The worker wrote a backup somewhere inside this test's own tree even though its " +
                "granted folder was gone, the tree now holds ${tree.walkTopDown().map { it.name }.toList()}",
            tree.walkTopDown().none { it.isFile && it.name.endsWith(".json") },
        )

        launchBackupPage().use {
            BackupPage.awaitSummaryContaining(
                BackupStrings.autoBackupTimeTitle,
                BackupStrings.autoBackupLastFailed(BackupStrings.errorFolderUnavailable),
            )
        }
    }

    @Test
    @TestId("BKP-52")
    fun theDisabledWorkerDoesNothing() {
        val tree = tree()
        launchBackupPage().use { grantFolder(tree) }
        leaveBackupPage()

        BackupSeed.settings(mapOf(Preferences.autoBackupEnabledKey to false))
        BackupSeed.record(hash = BackupHashes.ONE)

        val result = BackupWork.runWorker()
        assertTrue(BackupWork.isSuccess(result))

        BackupTree.assertNothingWritten(tree)
        assertEquals(
            "A disabled worker must not stamp a run time",
            0L,
            BackupSeed.long(Preferences.autoBackupLastRunKey) ?: 0L,
        )
        assertNull(
            "A disabled worker must not record an error either",
            BackupSeed.string(Preferences.autoBackupLastErrorKey),
        )
    }

    @Test
    @TestId("BKP-53")
    fun theWorkerSweepsStaleTemporaryFilesAndKeepsFreshOnes() {
        val tree = tree()
        launchBackupPage().use { grantFolder(tree) }
        leaveBackupPage()

        BackupSeed.autoBackupEnabled()
        BackupSeed.record(hash = BackupHashes.ONE)

        val stale = BackupTree.seed(tree, STALE_TMP_NAME, "partial".toByteArray(Charsets.UTF_8))
        val fresh = BackupTree.seed(tree, FRESH_TMP_NAME, "partial".toByteArray(Charsets.UTF_8))
        stale.setLastModified(BackupClock.hoursAgo(STALE_AGE_HOURS))
        fresh.setLastModified(BackupClock.minutesAgo(FRESH_AGE_MINUTES))

        val result = BackupWork.runWorker()
        assertTrue(BackupWork.isSuccess(result))

        assertFalse(
            "A two hour old leftover from an interrupted export was not swept",
            stale.exists(),
        )
        assertTrue(
            "A one minute old temporary file was deleted, which would truncate a write still in flight",
            fresh.exists(),
        )
        BackupTree.awaitAutoBackup(tree)
    }

    private companion object {
        const val NESTED_TARGET_NAME = "BackupTarget"
        const val STALE_TMP_NAME = "mj-pdf-backup-20260101-000000.json.tmp"
        const val FRESH_TMP_NAME = "mj-pdf-backup-20260102-000000.json.tmp"
        const val STALE_AGE_HOURS = 2L
        const val FRESH_AGE_MINUTES = 1L
        val FOUR_SETTINGS: Map<String, Any> = mapOf(
            Preferences.highQualityKey to true,
            Preferences.screenOnKey to true,
            Preferences.hideDelayKey to 4000,
            Preferences.maxZoomKey to 20.0f,
        )
    }
}
