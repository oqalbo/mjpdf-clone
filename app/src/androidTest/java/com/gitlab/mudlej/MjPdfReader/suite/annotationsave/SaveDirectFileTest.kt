// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
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
class SaveDirectFileTest : SaveTestCase() {

    @Test
    @TestId("SAVE-02")
    fun updatingTheSameFileWritesDirectlyWhenAllFilesAccessIsGranted() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            val sourceUri = documentUri()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            Snackbars.awaitText(string(R.string.highlights_saved), Await.LONG_TIMEOUT_MS)
            SavePicker.assertNeverShown()
            awaitSaveCompleted(journal)

            val after = SaveDigest.awaitSettled(file)
            assertTrue("the source file must be rewritten in place, read $before then $after", after != before)
            assertFalse(
                "the temporary sibling must be gone once the rename lands",
                savingSibling(file).exists(),
            )
            assertTrue("the saved file must still be a pdf", SaveDigest.startsWithPdfMagic(file))

            val row = SaveDestinations.awaitRowForSource(sourceUri)
            assertEquals(
                "the remembered destination is the file itself",
                android.net.Uri.fromFile(file).toString(),
                row.destinationUri,
            )
        }
    }

    @Test
    @TestId("SAVE-12")
    fun savingIntoTheSameFileProducesAReadableFileCarryingTheHighlight() {
        grantAllFilesAccess()

        var pagesBefore = 0
        openReader(SaveFixtures.SMALL) { _, file ->
            pagesBefore = reader.readState().pageCount!!
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            awaitSaveCompleted(journal)
            SaveDigest.awaitSettled(file)
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SMALL))
        assertTrue("the saved file must start with the pdf magic", SaveDigest.startsWithPdfMagic(file))
        assertTrue("the saved file must not be empty", file.length() > 0)

        reopen(file) {
            assertEquals("the reopened document keeps every page", pagesBefore, reader.readState().pageCount)
            AnnotationReaderUi.assertBothFabsStayGone()
            assertEquals(
                "the reopened document carries exactly the one highlight that was saved",
                1,
                highlightGroupsOnPage(0).size,
            )
            assertTrue(
                "the saved highlight keeps its quote",
                highlightGroupsOnPage(0).single().quote.isNotBlank(),
            )
        }
    }

    @Test
    @TestId("SAVE-17")
    fun aSuccessfulSaveDeletesTheAnnotationJournal() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, _ ->
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            awaitSaveCompleted(journal)

            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)
            AnnotationReaderUi.assertBothFabsStayGone()
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SMALL))
        reopen(file) {
            AnnotationDialogs.assertTitleNeverShown(string(R.string.restore_unsaved_highlights_title))
        }
    }

    @Test
    @TestId("SAVE-33")
    fun aFailedDirectWriteLeavesNoSiblingAndDoesNotTouchTheTarget() {
        grantAllFilesAccess()
        SaveFaults.armCorruptWrite()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_failed_title), Await.LONG_TIMEOUT_MS)
            assertFalse(
                "the rename never happened, so the incomplete wording must not be used",
                AnnotationDialogs.isTitleShown(string(R.string.annotation_save_incomplete_title)),
            )
            AnnotationDialogs.pressNegative()

            assertFalse("no temporary sibling may be left behind", savingSibling(file).exists())
            SaveDigest.assertUnchanged("a failed write must not touch the target", file, before)
            assertTrue(
                "a failed write keeps a rescue copy",
                SaveFaults.awaitRescueFileCount(1).single().length() > 0,
            )
            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationJournalFile.assertLineCountStays(journal, 1)
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
