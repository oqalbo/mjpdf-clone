// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SafetySnapshotTest : BackupTestCase() {
    @Test
    @TestId("BKP-36")
    fun aSafetySnapshotIsWrittenAndRestorePreImportSnapshotAppears() {
        seedThreeRecordsAndTwoBookmarks()
        BackupTree.assertNoSafetyDirectory()

        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.VALID_FULL)
        }

        val snapshot = BackupTree.awaitSafetySnapshotCount(1).single()
        assertEquals(
            "Exactly one import ran, so exactly one snapshot file may exist",
            listOf(snapshot.name),
            BackupTree.safetyFiles().map { it.name },
        )
        val json = BackupJson.of(snapshot)
        assertEquals(
            "The snapshot must hold the database as it was before the import, not the imported file",
            3,
            json.records!!.size,
        )
        assertEquals(2, json.bookmarks!!.size)
        assertEquals(BackupHashes.ALL.take(3).sorted(), json.recordHashes().sorted())

        BackupNavigation.finishAppTask()
        launchBackupPage().use {
            BackupPage.awaitRow(BackupStrings.restoreSnapshotTitle)
            BackupPage.awaitSummaryContaining(
                BackupStrings.restoreSnapshotTitle,
                BackupStrings.restoreSnapshotSummaryPrefix,
            )
        }
    }

    @Test
    @TestId("BKP-37")
    fun theSnapshotIncludesPasswordsTheExportDialogWouldHaveWithheld() {
        BackupSeed.record(hash = BackupHashes.ONE, password = BackupFixtures.SAVED_PASSWORD)

        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.VALID_FULL)
        }

        val snapshot = BackupTree.awaitSafetySnapshotCount(1).single()
        val record = BackupJson.of(snapshot).records!!.single()
        assertEquals(
            "The way back after an import must carry the saved passwords or they are lost for good",
            BackupFixtures.SAVED_PASSWORD,
            record.getString("password"),
        )
        assertTrue(
            "The imported rows must not carry the password from the wiped ones",
            BackupSeed.records().none { it.password == BackupFixtures.SAVED_PASSWORD },
        )
    }

    @Test
    @TestId("BKP-38")
    @TestDuration(DurationCategory.LONG)
    fun restoringThePreImportSnapshotBringsTheOldDataBack() {
        seedThreeRecordsAndTwoBookmarks()

        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.VALID_FULL)
        }
        BackupTree.awaitSafetySnapshotCount(1)
        BackupNavigation.finishAppTask()

        launchBackupPage().use {
            BackupPage.awaitRow(BackupStrings.restoreSnapshotTitle)
            ImportSignals.awaitImport {
                BackupPage.tapRestoreSnapshot()
                BackupDialog.awaitTitle(BackupStrings.restoreSnapshotTitle)
                BackupDialog.tap(BackupDialog.POSITIVE)
            }
        }

        assertEquals(
            "The undo path did not bring the pre import records back",
            BackupHashes.ALL.take(3).sorted(),
            BackupSeed.recordHashes(),
        )
        assertEquals(2, BackupSeed.bookmarkCount())
        BackupFixtures.VALID_FULL_HASHES.forEach { hash ->
            assertFalse(
                "The imported record $hash survived the restore",
                BackupSeed.recordHashes().contains(hash),
            )
        }
        assertEquals(
            "Restoring a snapshot must not write a second one",
            1,
            BackupTree.safetySnapshots().size,
        )
    }

    @Test
    @TestId("BKP-39")
    fun snapshotRetentionKeepsTheThreeNewest() {
        val seeded = seedFourSnapshotsAndTwoStrayFiles()

        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.VALID_FULL)
        }

        val remaining = Await.untilValue(
            "snapshot retention leaves three files behind",
            Await.LONG_TIMEOUT_MS,
            read = { BackupTree.safetySnapshots().map { it.name } },
        ) { it.size == RETAINED_SNAPSHOTS }

        val survivors = seeded.filter { remaining.contains(it) }
        assertEquals(
            "Retention kept the wrong two of the four seeded snapshots",
            listOf(SNAPSHOT_NAMES[3], SNAPSHOT_NAMES[2]).sorted(),
            survivors.sorted(),
        )
        assertEquals(
            "The third survivor must be the snapshot this import wrote",
            1,
            remaining.count { !seeded.contains(it) },
        )
        assertTrue(
            "The stray files in the snapshot directory were not swept, it still holds " +
                BackupTree.safetyFiles().map { it.name },
            BackupTree.safetyFiles().none { it.name == LEFTOVER_TMP || it.name == STRAY_JSON },
        )
    }

    @Test
    @TestId("BKP-40")
    fun anImportAbortsUntouchedWhenTheSnapshotCannotBeWritten() {
        seedThreeRecordsAndTwoBookmarks()
        BackupSeed.settings(SIX_SETTINGS)
        val blocker = BackupTree.safetyDirectory()
        blocker.parentFile?.mkdirs()
        blocker.writeText("not a directory", Charsets.UTF_8)
        assertTrue("The blocking file was not created as a regular file", blocker.isFile)

        launchBackupPage().use {
            val outcome = importHistoryFixture(BackupFixtures.VALID_FULL)
            assertEquals(ImportSignals.OUTCOME_SAFETY_ABORTED, outcome)

            BackupDialog.awaitTitle(BackupStrings.importTitle, Await.LONG_TIMEOUT_MS)
            val message = Await.untilNotNull("the aborted import dialog carries a message") {
                BackupDialog.message()
            }
            assertEquals(
                BackupStrings.importSafetyAborted(BackupStrings.errorCreateFile),
                message,
            )
        }

        assertEquals(
            "The app wiped the library even though it could not save a copy of it first",
            BackupHashes.ALL.take(3).sorted(),
            BackupSeed.recordHashes(),
        )
        assertEquals(2, BackupSeed.bookmarkCount())
        SIX_SETTINGS.forEach { (key, value) ->
            assertEquals("The aborted import overwrote the setting $key", value, BackupSeed.raw(key))
        }
        assertTrue(
            "The blocking regular file at the snapshot path was replaced",
            blocker.isFile,
        )
    }

    @Test
    @TestId("BKP-41")
    @TestDuration(DurationCategory.SHORT)
    fun aSnapshotNamedWithArabicIndicDigitsIsStillFound() {
        val directory = BackupTree.safetyDirectory()
        directory.mkdirs()
        File(directory, ARABIC_INDIC_SNAPSHOT_NAME)
            .writeText(twoRecordSnapshotJson(), Charsets.UTF_8)

        launchBackupPage().use {
            BackupPage.awaitRow(BackupStrings.restoreSnapshotTitle)
            ImportSignals.awaitImport {
                BackupPage.tapRestoreSnapshot()
                BackupDialog.awaitTitle(BackupStrings.restoreSnapshotTitle)
                BackupDialog.tap(BackupDialog.POSITIVE)
            }
        }

        assertEquals(
            "A snapshot written by a build that formatted its name with Arabic-Indic digits could not " +
                "be restored, which is the regression this test exists for",
            listOf(BackupHashes.ONE, BackupHashes.TWO).sorted(),
            BackupSeed.recordHashes(),
        )
    }

    private fun seedThreeRecordsAndTwoBookmarks() {
        BackupHashes.ALL.take(3).forEachIndexed { index, hash ->
            BackupSeed.record(hash = hash, pageNumber = index)
        }
        BackupSeed.bookmark(BackupHashes.ONE, 1)
        BackupSeed.bookmark(BackupHashes.TWO, 2)
    }

    private fun seedFourSnapshotsAndTwoStrayFiles(): List<String> {
        val directory = BackupTree.safetyDirectory()
        directory.mkdirs()
        val base = System.currentTimeMillis() - SNAPSHOT_NAMES.size * MINUTE_MILLIS * 2
        SNAPSHOT_NAMES.forEachIndexed { index, name ->
            File(directory, name).apply {
                writeText(twoRecordSnapshotJson(), Charsets.UTF_8)
                setLastModified(base + index * MINUTE_MILLIS)
            }
        }
        File(directory, LEFTOVER_TMP).writeText("partial", Charsets.UTF_8)
        File(directory, STRAY_JSON).apply {
            writeText("{}", Charsets.UTF_8)
            setLastModified(BackupClock.hoursAgo(STRAY_AGE_HOURS))
        }
        return SNAPSHOT_NAMES
    }

    private fun twoRecordSnapshotJson(): String =
        "{\"schemaVersion\":1,\"appVersionCode\":1,\"exportedAt\":\"2026-01-01T00:00:00Z\"," +
            "\"pdfRecords\":[" +
            recordJson(BackupHashes.ONE, "snapshot-one.pdf", 4) + "," +
            recordJson(BackupHashes.TWO, "snapshot-two.pdf", 9) +
            "],\"userBookmarks\":[]}"

    private fun recordJson(hash: String, fileName: String, pageNumber: Int): String =
        "{\"hash\":\"$hash\",\"pageNumber\":$pageNumber,\"length\":0,\"fileName\":\"$fileName\"," +
            "\"lastOpened\":\"2026-01-01T00:00:00\",\"reading\":\"UNSET\",\"favorite\":false," +
            "\"cropMarginsVersion\":0,\"hidden\":false," +
            "\"uri\":\"file:///sdcard/Download/$fileName\"}"

    private companion object {
        const val RETAINED_SNAPSHOTS = 3
        const val MINUTE_MILLIS = 60_000L
        const val STRAY_AGE_HOURS = 48L
        const val LEFTOVER_TMP = "leftover.json.tmp"
        const val STRAY_JSON = "stray.json"

        const val ARABIC_INDIC_SNAPSHOT_NAME = "mj-pdf-pre-import-٢٠٢٦٠١" +
            "٠١-١٢٣٤٥٦.json"

        val SNAPSHOT_NAMES = listOf(
            "mj-pdf-pre-import-20260101-000000.json",
            "mj-pdf-pre-import-20260102-000000.json",
            "mj-pdf-pre-import-20260103-000000.json",
            "mj-pdf-pre-import-20260104-000000.json",
        )

        val SIX_SETTINGS: Map<String, Any> = mapOf(
            Preferences.highQualityKey to true,
            Preferences.screenOnKey to true,
            Preferences.pageFlingKey to true,
            Preferences.hideDelayKey to 4000,
            Preferences.maxZoomKey to 20.0f,
            Preferences.homeViewModeKey to "LIST",
        )
    }
}
