// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SaveCopyTest : SaveTestCase() {

    @Test
    @TestId("SAVE-13")
    fun savingAsANewFileLeavesTheOriginalByteIdentical() {
        openReader(SaveFixtures.MEDIUM) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            val copyName = SaveFixtures.annotatedNameOf(file.name)

            makeOneHighlight(SaveFixtures.MEDIUM)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(copyName)

            Snackbars.awaitText(string(R.string.highlights_saved), Await.VERY_LONG_TIMEOUT_MS)
            awaitSaveCompleted(journal)

            SaveDigest.assertUnchanged("saving a copy must never touch the original", file, before)
            val copy = File(SaveFixtures.downloads, copyName)
            SaveDigest.awaitSettled(copy)
            assertTrue("the copy must exist and carry bytes", copy.isFile && copy.length() > 0)
            assertTrue("the copy must be a pdf", SaveDigest.startsWithPdfMagic(copy))
        }
    }

    @Test
    @TestId("SAVE-14")
    fun theSavedCopyReopensWithTheHighlightPresent() {
        grantAllFilesAccess()
        var originalPages = 0
        var savedQuote = ""

        val saved = saveOneHighlightAsNewCopy(SaveFixtures.SMALL)
        reopen(saved.source) {
            originalPages = reader.readState().pageCount!!
        }
        SaveDigest.awaitSettled(saved.copy)

        reopen(saved.copy) {
            assertEquals("the copy keeps every page of the original", originalPages, reader.readState().pageCount)
            val groups = highlightGroupsOnPage(0)
            assertEquals("the copy carries exactly the one highlight that was made", 1, groups.size)
            savedQuote = groups.single().quote
            assertTrue("the copied highlight keeps its quote", savedQuote.isNotBlank())
            assertTrue(
                "the reader titles itself with the copy's name",
                readerTitleText().contains(saved.copy.name.removeSuffix(".pdf")),
            )
        }
    }

    @Test
    @TestId("SAVE-15")
    fun aLiveSearchResultHighlightIsNeverWrittenIntoTheSavedFile() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            SaveSearch.runSearchOnCurrentDocument(SEARCH_TERM)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            awaitSaveCompleted(journal)
            SaveDigest.awaitSettled(file)
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SMALL))
        reopen(file) {
            assertEquals(
                "only the highlight the user made may reach the file, search shading must not",
                1,
                highlightGroupsOnPage(0).size,
            )
        }
    }

    @Test
    @TestId("SAVE-16")
    fun savingACopyDoesNotPutHighlightsIntoTheOriginal() {
        grantAllFilesAccess()

        val saved = saveOneHighlightAsNewCopy(SaveFixtures.SMALL)
        val originalAfterCopy = SaveDigest.of(saved.source)

        reopen(saved.source) {
            assertTrue(
                "the untouched original must carry no highlights",
                highlightGroupsOnPage(0).isEmpty(),
            )
            AnnotationReaderUi.assertBothFabsStayGone()
        }

        SaveDigest.assertUnchanged(
            "reopening the original must not change it either",
            saved.source,
            originalAfterCopy,
        )
    }

    @Test
    @TestId("SAVE-20")
    fun aPasswordProtectedDocumentSavesAndStillAsksForItsPassword() {
        grantAllFilesAccess()
        val file = place(SaveFixtures.PROTECTED)
        val before = SaveDigest.of(file)
        val copyName = SaveFixtures.annotatedNameOf(file.name)
        val copy = File(SaveFixtures.downloads, copyName)

        ActivityScenario.launch<MainActivity>(intentFor(file)).use {
            SavePassword.enter(SaveFixtures.PROTECTED_PASSWORD)
            awaitReaderReady()

            val journal = journal()
            makeOneHighlight(SaveFixtures.PROTECTED)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(copyName)
            awaitSaveCompleted(journal)
        }

        SaveDigest.awaitSettled(copy)
        SaveDigest.assertUnchanged("the protected original must be untouched", file, before)

        ActivityScenario.launch<MainActivity>(intentFor(copy)).use {
            SavePassword.awaitPrompt()
            SavePassword.enter(SaveFixtures.PROTECTED_PASSWORD)
            awaitReaderReady()

            assertEquals(
                "the saved copy carries the highlight",
                1,
                highlightGroupsOnPage(0).size,
            )
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun readerTitleText(): String = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: return@onMainThread ""
        activity.findViewById<TextView>(R.id.actionbarTitle)?.text?.toString().orEmpty()
    }

    private companion object {
        const val SEARCH_TERM = "guard"
    }
}
