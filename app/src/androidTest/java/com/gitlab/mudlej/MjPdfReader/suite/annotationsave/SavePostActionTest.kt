// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationCard
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SavePostActionTest : SaveTestCase() {

    @Test
    @TestId("SAVE-42")
    fun savingFromTheLeavePromptExitsTheReaderAfterwards() {
        grantAllFilesAccess()
        val file = place(SaveFixtures.SMALL)
        val before = SaveDigest.of(file)
        var journal: File

        launch(file).use {
            journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            answerLeavePromptWithSave()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        SaveDigest.awaitSettled(file)
        assertTrue("the file must be rewritten", SaveDigest.of(file) != before)
        AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)

        reopen(file) {
            assertEquals("the saved file carries the highlight", 1, highlightGroupsOnPage(0).size)
        }
    }

    @Test
    @TestId("SAVE-43")
    fun savingFromTheHomeButtonSavesThenGoesHome() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            SaveActions.tapHomeIndicator()
            AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
            AnnotationDialogs.pressPositive()

            SaveSheet.awaitShown()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()

            Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
            SaveDigest.awaitSettled(file)
            assertTrue("the file must be rewritten before home takes over", SaveDigest.of(file) != before)
        }
    }

    @Test
    @TestId("SAVE-44")
    fun openingADifferentDocumentFromOutsideSavesTheCurrentOneFirst() {
        grantAllFilesAccess()
        val decoy = place(SaveFixtures.DECOY)
        val decoyBefore = SaveDigest.of(decoy)
        val file = place(SaveFixtures.SMALL)
        val before = SaveDigest.of(file)

        TestEnvironment.targetContext.startActivity(
            intentFor(file).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        awaitReaderReady()
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as MainActivity
        try {
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            TestEnvironment.targetContext.startActivity(
                intentFor(decoy)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )

            AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
            AnnotationDialogs.pressPositive()

            SaveSheet.awaitShown()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()

            SaveDigest.awaitSettled(file)
            assertTrue("the first document must be written", SaveDigest.of(file) != before)
            SaveDigest.assertUnchanged("the newly opened document must not be written", decoy, decoyBefore)

            reader.awaitState("the reader shows the newly opened document", Await.LONG_TIMEOUT_MS) {
                it.documentUri?.contains(decoy.name) == true
            }
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the reader reports the newly opened document's page count",
                DECOY_PAGES,
                reader.readState().pageCount,
            )
        } finally {
            TestEnvironment.onMainThread { activity.finish() }
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        reopen(file) {
            assertEquals(
                "the first document carries the highlight saved before the second opened",
                1,
                highlightGroupsOnPage(0).size,
            )
        }
    }

    @Test
    @TestId("SAVE-45")
    fun openingTheFilePickerSavesFirst() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            SaveActions.runFromActionsDialog(ConfigurableAction.OPEN_LOCAL.titleRes)
            AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
            AnnotationDialogs.pressPositive()

            SaveSheet.awaitShown()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()

            SavePicker.awaitShown()
            SaveDigest.awaitSettled(file)
            assertTrue("the file must be written before the picker opens", SaveDigest.of(file) != before)
            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)

            SavePicker.cancel()
            reader.awaitShown(Await.LONG_TIMEOUT_MS)
            AnnotationReaderUi.assertBothFabsStayGone()
        }
    }

    @Test
    @TestId("SAVE-46")
    fun openingTheHighlightsListSavesTheFileFirst() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            val sourceUri = documentUri()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            SaveActions.runFromActionsDialog(ConfigurableAction.USER_HIGHLIGHTS.titleRes)
            AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
            AnnotationDialogs.pressPositive()

            SaveSheet.awaitShown()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()

            SaveDigest.awaitSettled(file)
            assertTrue("the file must be written before the list reads it", SaveDigest.of(file) != before)
            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)

            Screens.awaitResumed(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)

            val row = SaveDestinations.awaitRowForSource(sourceUri)
            assertEquals(
                "the remembered destination is the source file",
                android.net.Uri.fromFile(file).toString(),
                row.destinationUri,
            )
        }
    }

    @Test
    @TestId("SAVE-47")
    fun openingTheNotesListSavesFirst() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val journal = journal()
            val point = makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)
            AnnotationDialogs.awaitTitle(string(R.string.note))
            AnnotationDialogs.typeIntoEditor(NOTE_TEXT)
            AnnotationDialogs.pressPositive()
            AnnotationJournalFile.awaitLineCount(journal, 2)

            SaveActions.runFromActionsDialog(ConfigurableAction.USER_NOTES.titleRes)
            AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
            AnnotationDialogs.pressPositive()

            SaveSheet.awaitShown()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()

            Screens.awaitResumed(UserNotesActivity::class.java, Await.LONG_TIMEOUT_MS)
            assertTrue(
                "the notes screen lists the note that was saved",
                uiDevice().wait(
                    androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains(NOTE_TEXT)),
                    Await.LONG_TIMEOUT_MS,
                ) == true
            )
            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SMALL))
        reopen(file) {
            assertEquals(
                "the saved file carries the note on its highlight",
                NOTE_TEXT,
                highlightGroupsOnPage(0).single().note,
            )
        }
    }

    @Test
    @TestId("SAVE-48")
    fun discardingAtTheLeavePromptWritesNothing() {
        grantAllFilesAccess()
        val file = place(SaveFixtures.SMALL)
        val before = SaveDigest.of(file)

        launch(file).use {
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal(), 1)

            uiDevice().pressBack()
            AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
            AnnotationDialogs.pressNegative()
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        SaveDigest.assertUnchanged("discard means the file is never written", file, before)
        Await.until("the discarded journal is removed", Await.LONG_TIMEOUT_MS) {
            AnnotationJournalFile.files().isEmpty()
        }

        reopen(file) {
            assertTrue("the reopened document carries no highlights", highlightGroupsOnPage(0).isEmpty())
            AnnotationDialogs.assertTitleNeverShown(string(R.string.restore_unsaved_highlights_title))
        }
    }

    @Test
    @TestId("SAVE-49")
    fun cancellingAtTheLeavePromptStaysInTheReaderWithTheEditsIntact() {
        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)
            val pageBefore = reader.readState().page

            uiDevice().pressBack()
            AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
            AnnotationDialogs.pressNeutral()

            reader.awaitShown(Await.LONG_TIMEOUT_MS)
            assertEquals("cancelling leaves the reader on the same page", pageBefore, reader.readState().page)
            AnnotationReaderUi.awaitSaveFabVisible()
            assertTrue(
                "both buttons stay live",
                AnnotationReaderUi.saveFab().enabled && AnnotationReaderUi.discardFab().enabled,
            )
            AnnotationJournalFile.assertLineCountStays(journal, 1)
            SaveDigest.assertUnchanged("cancelling writes nothing", file, before)
        }
    }

    private fun answerLeavePromptWithSave() {
        uiDevice().pressBack()
        AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
        AnnotationDialogs.pressPositive()
        SaveSheet.awaitShown()
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val NOTE_TEXT = "read this again"
        const val DECOY_PAGES = 7
    }
}
