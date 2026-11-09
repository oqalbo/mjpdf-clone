// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.BackupDebugHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ImportContentTest : BackupTestCase() {
    @Test
    @TestId("BKP-20")
    @TestDuration(DurationCategory.LONG)
    fun theWipeWarningArmsAfterThreeSecondsAndCancelsCleanly() {
        BackupSeed.record(hash = BackupHashes.ONE)
        BackupSeed.record(hash = BackupHashes.TWO)

        launchBackupPage().use {
            val warningCount = BackupDebugHooks.wipeWarnings()
            startImportOf(BackupFixtures.VALID_FULL)
            Await.until("the wipe warning onShow hook runs") {
                BackupDebugHooks.wipeWarnings() > warningCount
            }

            assertEquals(
                "The disabled confirm button must begin at the three-second arm interval",
                "${BackupStrings.wipeAction} (3)",
                BackupDebugHooks.wipeWarningInitialLabel(),
            )
            assertTrue(
                "the destructive import action must be disabled when the warning is shown",
                BackupDebugHooks.wipeWarningInitiallyDisabled(),
            )

            BackupDialog.awaitButtonEnabled(BackupDialog.POSITIVE, WIPE_COUNTDOWN_TIMEOUT_MS)
            val armedAt = Await.untilValue(
                "the wipe warning records when its confirm action arms",
                WIPE_COUNTDOWN_TIMEOUT_MS,
                read = { BackupDebugHooks.wipeWarningArmedAtUptimeMillis() },
            ) { it > 0L }
            assertTrue(
                "The confirm button armed too soon to enforce its three-second safety interval",
                armedAt - BackupDebugHooks.wipeWarningShownAtUptimeMillis() >= MIN_OBSERVED_ARM_MS,
            )
            val armedLabel = BackupDialog.buttonText(BackupDialog.POSITIVE).orEmpty()
            assertTrue(
                "The armed confirm button must drop the counter and read " +
                    "\"${BackupStrings.wipeAction}\", it read \"$armedLabel\"",
                armedLabel.equals(BackupStrings.wipeAction, ignoreCase = true),
            )

            BackupDialog.tapAndAwaitGone(BackupDialog.NEGATIVE)
            ImportSignals.assertNoImportWithin()
            assertEquals(
                "Cancelling the wipe warning must leave the library exactly as it was",
                listOf(BackupHashes.ONE, BackupHashes.TWO).sorted(),
                BackupSeed.recordHashes(),
            )
            BackupTree.assertNoSafetyDirectory()
        }
    }

    @Test
    @TestId("BKP-24")
    fun invalidRecordsAndBookmarksAreDroppedAndValidNeighboursSurvive() {
        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.DIRTY_RECORDS)

            val hashes = BackupSeed.recordHashes()
            assertFalse(
                "A record with an empty hash was written to the database",
                hashes.any { it.isBlank() },
            )
            assertEquals(
                "Both well formed records should survive their malformed neighbour",
                listOf(
                    BackupFixtures.DIRTY_UNPARSEABLE_DATE_HASH,
                    BackupFixtures.DIRTY_VALID_RECORD_HASH,
                ).sorted(),
                hashes,
            )

            val badDate = BackupSeed.recordOf(BackupFixtures.DIRTY_UNPARSEABLE_DATE_HASH)
            assertNotNull("The record with an unparseable date was dropped entirely", badDate)
            assertEquals(
                "An unparseable lastOpened must fall back to LocalDateTime.MIN",
                LocalDateTime.MIN,
                badDate!!.lastOpened,
            )

            val bookmarks = BackupSeed.allBookmarks()
            assertFalse(
                "A bookmark with a negative pageIndex was written to the database",
                bookmarks.any { it.pageIndex < 0 },
            )
            val negativeSort = bookmarks.singleOrNull {
                it.pageIndex == BackupFixtures.DIRTY_NEGATIVE_SORT_PAGE_INDEX
            }
            assertNotNull(
                "The bookmark whose sortOrder was -1 was dropped rather than repaired",
                negativeSort,
            )
            assertEquals(
                "A negative sortOrder must fall back to the bookmark's own page index",
                BackupFixtures.DIRTY_NEGATIVE_SORT_PAGE_INDEX,
                negativeSort!!.sortOrder,
            )

            val validRecord = BackupSeed.recordOf(BackupFixtures.DIRTY_VALID_RECORD_HASH)
            assertNotNull("The fully valid record did not survive the import", validRecord)
            val fromFile = BackupFixtures.json(BackupFixtures.DIRTY_RECORDS)
                .record(BackupFixtures.DIRTY_VALID_RECORD_HASH)!!
            assertEquals(fromFile.getInt("pageNumber"), validRecord!!.pageNumber)
            assertEquals(fromFile.getString("fileName"), validRecord.fileName)
        }
    }

    @Test
    @TestId("BKP-25")
    fun aLegacyMinifiedBackupImports() {
        launchBackupPage().use {
            val outcome = importHistoryFixture(BackupFixtures.LEGACY_MINIFIED)
            assertEquals(ImportSignals.OUTCOME_IMPORTED, outcome)

            assertEquals(
                "The legacy record hashes did not all restore",
                BackupFixtures.LEGACY_HASHES.sorted(),
                BackupSeed.recordHashes(),
            )
            assertEquals(
                BackupFixtures.LEGACY_BOOKMARK_COUNT,
                BackupSeed.bookmarkCount(),
            )
            LEGACY_SETTING_KEYS.forEach { key ->
                assertTrue(
                    "The legacy settings entry $key was not applied",
                    BackupSeed.contains(key),
                )
            }
        }
    }

    private companion object {
        const val MIN_OBSERVED_ARM_MS = 2_500L

        val LEGACY_SETTING_KEYS = listOf("highQuality", "hideDelay", "shortcutBarActions")
    }
}
