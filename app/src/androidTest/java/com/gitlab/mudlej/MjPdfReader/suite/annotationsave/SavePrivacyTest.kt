// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SavePrivacyTest : SaveTestCase() {

    @Test
    @TestId("SAVE-50")
    fun incognitoWritesTheFileAndNothingElse() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL, incognito = true) { _, file ->
            val before = SaveDigest.of(file)
            makeOneHighlight(SaveFixtures.SMALL)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            Snackbars.awaitText(string(R.string.highlights_saved), Await.VERY_LONG_TIMEOUT_MS)
            awaitSaveSettled()
            AnnotationReaderUi.awaitSaveFabGone(Await.LONG_TIMEOUT_MS)

            SaveDigest.awaitSettled(file)
            assertTrue("the file must be written", SaveDigest.of(file) != before)

            assertTrue(
                "incognito must leave no journal behind, found ${AnnotationJournalFile.files().map { it.name }}",
                AnnotationJournalFile.files().isEmpty(),
            )
            Records.assertRowCountStays(0)
            SaveDestinations.assertRowCountStays(0)
            assertTrue(
                "incognito must leave no preview cache behind",
                previewCacheEntries().isEmpty(),
            )
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SMALL))
        reopen(file) {
            assertEquals("the file carries the highlight that was saved", 1, highlightGroupsOnPage(0).size)
        }
    }

    @Test
    @TestId("SAVE-51")
    fun anIncognitoSaveFailureWritesNoRescueCopy() {
        grantAllFilesAccess()
        SaveFaults.armFailWrite()

        openReader(SaveFixtures.SMALL, incognito = true) { _, file ->
            val before = SaveDigest.of(file)
            makeOneHighlight(SaveFixtures.SMALL)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(SaveFixtures.annotatedNameOf(file.name))
            awaitSaveSettled()

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_failed_title), Await.LONG_TIMEOUT_MS)
            AnnotationDialogs.awaitTextShown(string(R.string.annotation_save_failed_message))

            SaveFaults.assertNoRescueDirectory()
            SaveDigest.assertUnchanged("a failed save must not touch the source", file, before)
            AnnotationReaderUi.awaitSaveFabVisible()

            AnnotationDialogs.pressNegative()
        }
    }

    @Test
    @TestId("SAVE-52")
    @TestDuration(DurationCategory.LONG)
    fun historyTurnedOffWritesTheFileAndRecordsNothing() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            makeOneHighlight(SaveFixtures.SMALL)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()
            AnnotationReaderUi.awaitSaveFabGone(Await.LONG_TIMEOUT_MS)
            SaveDigest.awaitSettled(file)

            assertTrue("the file must be written", SaveDigest.of(file) != before)
            assertTrue(
                "history off must leave no journal behind",
                AnnotationJournalFile.files().isEmpty(),
            )
            Records.assertRowCountStays(0)
            SaveDestinations.assertRowCountStays(0)

            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationReaderUi.tapSaveFab()

            SaveSheet.assertNeverShown()
            SavePicker.assertNeverShown()
            awaitSaveSettled()
            AnnotationReaderUi.awaitSaveFabGone(Await.LONG_TIMEOUT_MS)
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SMALL))
        reopen(file) {
            assertEquals("both saves landed on the file", 2, highlightGroupsOnPage(0).size)
        }
    }

    @Test
    @TestId("SAVE-53")
    fun aDurableCopyOfASharedFileIsNeverOverwrittenImplicitly() {
        grantAllFilesAccess()
        SettingsSeeder.seed(Preferences.keepSharedCopiesKey, true)

        val source = place(SaveFixtures.NORMAL)
        val name = ownedName(SaveFixtures.NORMAL)
        val shared = StubSharerProvider.registerPath(name, source.absolutePath)
        StubSharerProvider.blockFirstOpenMatching(name) {
            rawDurableCopies().any { it.name.contains(name) }
        }

        ActivityScenario.launch<MainActivity>(intentFor(shared)).use {
            awaitReaderReady()
            val fileHash = reader.awaitState("the shared reader has a document identity") {
                !it.fileHash.isNullOrEmpty()
            }.fileHash!!
            try {
                assertTrue("the durable copy is held before its source is read", StubSharerProvider.awaitOpenBlocked(name, Await.LONG_TIMEOUT_MS))
                assertTrue("an unfinished copy cannot be the durable-copy baseline", durableCopies().isEmpty())
            } finally {
                StubSharerProvider.releaseBlockedOpen(name)
                Records.awaitRow(fileHash, "the durable copy is published in its record", Await.LONG_TIMEOUT_MS) {
                    it?.sourceUri == shared.toString() && it.uri != shared
                }
            }

            val durable = Await.untilNotNull("a durable copy of the shared document appears", Await.LONG_TIMEOUT_MS) {
                durableCopies().singleOrNull()
            }
            val durableBefore = SaveDigest.of(durable)
            assertEquals("the published durable copy contains the complete source", SaveDigest.of(source), durableBefore)

            makeOneHighlight(SaveFixtures.NORMAL)
            tapSaveAndAwaitSheet()

            assertTrue("the app must ask where to save rather than writing its own copy", SaveSheet.isShown())
            SaveDigest.assertUnchanged("the durable copy must not be written", durable, durableBefore)

            SaveSheet.dismissWithBack()
            SaveDigest.assertStaysUnchanged(
                "the durable copy must still not be written after backing out",
                durable,
                durableBefore,
            )
        }
    }

    private fun durableCopies(): List<File> = rawDurableCopies().filter {
        it.name.startsWith(cleanup.prefix()) && it.name.endsWith(".pdf", ignoreCase = true)
    }

    private fun rawDurableCopies(): List<File> {
        val imported = File(TestEnvironment.targetContext.filesDir, "imported").listFiles()?.toList().orEmpty()
        val documents = File(Fixtures.externalRoot, "Documents/MJ PDF").listFiles()?.toList().orEmpty()
        return (imported + documents).filter { it.isFile }
    }

    private fun previewCacheEntries(): List<File> =
        File(TestEnvironment.targetContext.cacheDir, "previews").listFiles()?.toList().orEmpty()

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
