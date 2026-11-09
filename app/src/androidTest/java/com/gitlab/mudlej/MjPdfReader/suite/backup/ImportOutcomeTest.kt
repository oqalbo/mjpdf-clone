// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ImportOutcomeTest : BackupTestCase() {
    @Test
    @TestId("BKP-33")
    fun recordsForFilesThatAreGoneAreStillRestored() {
        BackupSeed.settings(mapOf(Preferences.homeShowPdfTitleKey to false))

        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.MISSING_FILES)
        }

        val restored = Records.all().associateBy { it.hash }
        assertEquals(
            "Both rows whose files resolve to nothing must still be restored",
            BackupFixtures.MISSING_FILE_HASHES.sorted(),
            restored.keys.sorted(),
        )
        val fixture = BackupFixtures.json(BackupFixtures.MISSING_FILES)
        fixture.records!!.forEach { expected ->
            val row = restored.getValue(expected.getString("hash"))
            assertEquals(expected.getString("uri"), row.uri.toString())
            assertEquals(expected.getString("fileName"), row.fileName)
            assertEquals(expected.getInt("pageNumber"), row.pageNumber)
        }

        val home = BackupNavigation.relaunchIntoHome {
            BackupDialog.awaitTitle(BackupStrings.importTitle, Await.LONG_TIMEOUT_MS)
            BackupDialog.dismissWithOk()
        }
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        val expectedNames = fixture.records!!.map { it.getString("fileName") }
        expectedNames.forEach { name ->
            home.library().awaitAbsent(name, Await.LONG_TIMEOUT_MS)
        }
        assertEquals(
            "rendering Home must filter missing files without deleting their restored history rows",
            BackupFixtures.MISSING_FILE_HASHES.sorted(),
            Records.all().map { it.hash }.sorted(),
        )
    }

    @Test
    @TestId("BKP-34")
    fun theImportSummaryIsShownOnceOnTheNextHomeResume() {
        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.VALID_FULL)
        }

        val home = BackupNavigation.relaunchIntoHome {
            BackupDialog.awaitTitle(BackupStrings.importTitle, Await.LONG_TIMEOUT_MS)
            val summary = Await.untilNotNull("the import summary dialog carries a message") {
                BackupDialog.message()
            }
            assertTrue(
                "The summary omits the finished line, it read \"$summary\"",
                summary.contains(BackupStrings.importFinished),
            )
            assertTrue(
                "The summary omits the settings line, it read \"$summary\"",
                summary.contains(BackupStrings.importedSettings(BackupFixtures.VALID_FULL_SETTINGS_COUNT)),
            )
            assertTrue(
                "The summary omits the history line, it read \"$summary\"",
                summary.contains(
                    BackupStrings.importedHistory(
                        BackupFixtures.VALID_FULL_RECORD_COUNT,
                        BackupFixtures.VALID_FULL_BOOKMARK_COUNT,
                    )
                ),
            )
            assertNull(
                "importResultPending must be cleared the moment the summary is shown, or a later resume repeats it",
                BackupSeed.importResultPending(),
            )
            BackupDialog.dismissWithOk()
        }
        home.awaitRendered(Await.LONG_TIMEOUT_MS)

        launchBackupPage().use { BackupPage.awaitShown() }
        BackupNavigation.openHome()
        BackupDialog.assertNoDialogTitled(BackupStrings.importTitle)
    }
}
