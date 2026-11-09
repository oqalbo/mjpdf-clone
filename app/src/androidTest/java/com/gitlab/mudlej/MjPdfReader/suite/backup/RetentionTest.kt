// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class RetentionTest : BackupTestCase() {
    @Test
    @TestId("BKP-14")
    fun autoBackupRetentionKeepsTheTenNewestByModificationTime() {
        val tree = tree()
        val seeded = seedAutoBackupsWithReversedTimes(tree)
        val newestTen = seeded.sortedByDescending { it.second }.take(RETAINED_AUTO_BACKUPS).map { it.first }
        val oldestThree = seeded.sortedBy { it.second }.take(seeded.size - RETAINED_AUTO_BACKUPS).map { it.first }

        launchBackupPage().use {
            grantFolder(tree)
            BackupPage.startExport()
            ExportOptions.export()
            BackupTree.awaitManualBackup(tree)

            val remaining = Await.untilValue(
                "retention leaves $RETAINED_AUTO_BACKUPS auto backup files behind",
                Await.LONG_TIMEOUT_MS,
                read = { BackupTree.matching(tree, BackupTree.autoRetentionPattern).map { file -> file.name } },
            ) { it.size == RETAINED_AUTO_BACKUPS }

            assertEquals(
                "Retention kept the wrong ten files",
                newestTen.sorted(),
                remaining.sorted(),
            )
            oldestThree.forEach { name ->
                assertTrue(
                    "Retention left the old file $name in place",
                    !remaining.contains(name),
                )
            }
            assertTrue(
                "The parenthesised variant must take part in retention by its modification time rank, " +
                    "and this run kept ${remaining.filter { name -> name.contains("(") }}",
                remaining.contains(PARENTHESISED_NAME) == newestTen.contains(PARENTHESISED_NAME),
            )
        }
    }

    @Test
    @TestId("BKP-15")
    fun manualBackupsAreNeverPrunedByRetention() {
        val tree = tree()
        val seeded = (1..MANUAL_SEED_COUNT).map { index ->
            val name = "mj-pdf-backup-2026%02d%02d-000000.json".format(1, index)
            BackupTree.seed(tree, name, VALID_BACKUP_JSON.toByteArray(Charsets.UTF_8))
            name
        }

        launchBackupPage().use {
            grantFolder(tree)
            BackupPage.startExport()
            ExportOptions.export()

            val all = Await.untilValue(
                "the folder holds the ${MANUAL_SEED_COUNT + 1} manual backups",
                Await.LONG_TIMEOUT_MS,
                read = { BackupTree.manualBackups(tree).map { file -> file.name } },
            ) { it.size == MANUAL_SEED_COUNT + 1 }

            seeded.forEach { name ->
                assertTrue("Retention deleted the hand made backup $name", all.contains(name))
            }
        }
    }

    private fun seedAutoBackupsWithReversedTimes(tree: File): List<Pair<String, Long>> {
        val names = (1..AUTO_SEED_COUNT).map { index ->
            "mj-pdf-auto-backup-2026%02d%02d-000000.json".format(1, index)
        } + PARENTHESISED_NAME

        val base = System.currentTimeMillis() - AUTO_SEED_COUNT * MINUTE_MILLIS * 2
        val plan = names.mapIndexed { index, name ->
            val rank = if (name == PARENTHESISED_NAME) AUTO_SEED_COUNT / 2 else AUTO_SEED_COUNT - index
            name to base + rank * MINUTE_MILLIS
        }
        plan.forEach { (name, modifiedAt) ->
            val file = BackupTree.seed(tree, name, VALID_BACKUP_JSON.toByteArray(Charsets.UTF_8))
            setModificationTime(file, modifiedAt)
        }
        return plan
    }

    private fun setModificationTime(file: File, epochMillis: Long) {
        file.setLastModified(epochMillis)
        Await.untilValue(
            "the modification time of ${file.name} reads back as $epochMillis",
            Await.SHORT_TIMEOUT_MS,
            read = { file.lastModified() / 1000L },
        ) { it == epochMillis / 1000L }
    }

    private companion object {
        const val RETAINED_AUTO_BACKUPS = 10
        const val AUTO_SEED_COUNT = 12
        const val MANUAL_SEED_COUNT = 15
        const val MINUTE_MILLIS = 60_000L

        const val PARENTHESISED_NAME = "mj-pdf-auto-backup-20260113-000000 (1).json"

        const val VALID_BACKUP_JSON =
            "{\"schemaVersion\":1,\"appVersionCode\":1,\"exportedAt\":\"2026-01-01T00:00:00Z\"," +
                "\"settings\":[{\"key\":\"screenOn\",\"type\":\"boolean\",\"value\":\"true\"}]}"
    }
}
