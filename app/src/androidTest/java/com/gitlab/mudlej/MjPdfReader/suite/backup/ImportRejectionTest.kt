// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.FixtureResource
import com.gitlab.mudlej.MjPdfReader.suite.core.RequiresFixture
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

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ImportRejectionTest : BackupTestCase() {
    @Test
    @TestId("BKP-26")
    fun aMinifiedFileThatCannotBeRecognisedIsRejected() {
        assertRefused(
            fixture = BackupFixtures.LEGACY_MINIFIED_H2_ONLY,
            expectedError = BackupStrings.errorNotValid,
            seededRecords = 2,
        )
    }

    @Test
    @TestId("BKP-27")
    fun aNewerSchemaVersionIsRefused() {
        BackupSeed.settings(mapOf(Preferences.highQualityKey to true))
        assertRefused(
            fixture = BackupFixtures.SCHEMA_FUTURE,
            expectedError = BackupStrings.errorNewerVersion,
            seededRecords = 2,
        )
        assertEquals(
            "A refused file must not touch the preference store",
            true,
            BackupSeed.boolean(Preferences.highQualityKey),
        )
    }

    @Test
    @TestId("BKP-28")
    fun schemaVersionZeroIsRefused() {
        launchBackupPage().use {
            refuse(BackupFixtures.SCHEMA_ZERO, BackupStrings.errorNotValid)

            assertEquals("A refused file must leave the database empty", 0, BackupSeed.recordCount())
            BackupFixtures.json(BackupFixtures.SCHEMA_ZERO).settingKeys().forEach { key ->
                assertFalse(
                    "The refused file wrote its setting $key anyway",
                    BackupSeed.contains(key),
                )
            }
        }
    }

    @Test
    @TestId("BKP-29")
    fun aTruncatedBackupIsRefused() {
        BackupSeed.bookmark(BackupHashes.ONE, 1)
        BackupSeed.bookmark(BackupHashes.ONE, 2)
        BackupSeed.bookmark(BackupHashes.TWO, 3)
        assertRefused(
            fixture = BackupFixtures.TRUNCATED,
            expectedError = BackupStrings.errorNotValid,
            seededRecords = 2,
        )
        assertEquals("A refused file must not touch the bookmarks", 3, BackupSeed.bookmarkCount())
    }

    @Test
    @TestId("BKP-30")
    fun aFileThatIsNotABackupIsRefused() {
        assertRefused(
            fixture = BackupFixtures.NOT_A_BACKUP,
            expectedError = BackupStrings.errorNotValid,
            seededRecords = 2,
        )
    }

    @Test
    @TestId("BKP-31")
    @TestDuration(DurationCategory.LONG)
    fun aBackupWithNoSettingsAndNoHistoryIsRefused() {
        launchBackupPage().use {
            refuse(BackupFixtures.EMPTY_SECTIONS, BackupStrings.errorNothingToImport)
            BackupDialog.assertNoDialogMessageContaining(
                BackupStrings.wipeWarning.take(WIPE_WARNING_PREFIX)
            )
        }
    }

    @Test
    @TestId("BKP-32")
    @TestDuration(DurationCategory.LONG)
    @RequiresFixture(FixtureResource.OVERSIZED_BACKUP)
    fun aFileAboveTheSizeCeilingIsRefused() {
        BackupFixtures.generateOversized()
        BackupSeed.record(hash = BackupHashes.ONE)
        BackupSeed.record(hash = BackupHashes.TWO)
        BackupSeed.settings(mapOf(Preferences.highQualityKey to true))

        launchBackupPage().use {
            refuse(BackupFixtures.OVERSIZED, BackupStrings.errorTooLarge, Await.LONG_TIMEOUT_MS)

            assertEquals(
                listOf(BackupHashes.ONE, BackupHashes.TWO).sorted(),
                BackupSeed.recordHashes(),
            )
            assertEquals(true, BackupSeed.boolean(Preferences.highQualityKey))
            BackupTree.assertNoSafetyDirectory()
        }
    }

    @Test
    @TestId("BKP-35")
    fun aFailureBeforeAnyChangeReportsItAndLeavesTheDatabaseAlone() {
        BackupSeed.record(hash = BackupHashes.ONE)
        BackupSeed.record(hash = BackupHashes.TWO)

        launchBackupPage().use {
            ImportSignals.failNextPrepareImport()

            val outcome = ImportSignals.awaitImport {
                BackupPage.startImport()
                BackupFixtures.open(BackupFixtures.VALID_FULL)
                confirmWipeWarning()
            }
            assertEquals(ImportSignals.OUTCOME_REJECTED, outcome)

            val message = awaitFailureMessage()
            assertTrue(
                "The rejection does not tell the user nothing was changed, it read \"$message\"",
                message.contains(BackupStrings.importFailedBeforeChangesPrefix),
            )
            assertEquals(
                "A rejection before the wipe must leave every row in place",
                listOf(BackupHashes.ONE, BackupHashes.TWO).sorted(),
                BackupSeed.recordHashes(),
            )
            BackupTree.assertNoSafetyDirectory()
        }
    }

    private fun assertRefused(fixture: String, expectedError: String, seededRecords: Int) {
        val seeded = BackupHashes.ALL.take(seededRecords)
        seeded.forEach { hash -> BackupSeed.record(hash = hash) }

        launchBackupPage().use {
            refuse(fixture, expectedError)
            assertEquals(
                "A refused file changed the record table",
                seeded.sorted(),
                BackupSeed.recordHashes(),
            )
        }
    }

    private fun refuse(
        fixture: String,
        expectedError: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ) {
        BackupPage.startImport()
        BackupFixtures.open(fixture)

        BackupDialog.awaitTitle(BackupStrings.importTitle, timeoutMs)
        val message = awaitFailureMessage(timeoutMs)
        assertTrue(
            "The refusal of $fixture does not carry \"$expectedError\", it read \"$message\"",
            message.contains(expectedError),
        )
        assertTrue(
            "The refusal of $fixture is not phrased as an import failure, it read \"$message\"",
            message.contains(BackupStrings.importFailedPrefix),
        )

        ImportSignals.assertNoImportWithin()
        BackupTree.assertNoSafetyDirectory()
    }

    private fun awaitFailureMessage(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): String =
        Await.untilNotNull("the import result dialog carries a message", timeoutMs) {
            BackupDialog.message()
        }
}
