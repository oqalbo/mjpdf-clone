// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationCard
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ListReflectsReaderEditsTest : ListsTestCase() {

    @Test
    @TestId("LIST-44")
    fun deletingAHighlightInTheReaderRemovesItFromTheList() {
        Fixtures.ensureAllFilesAccess()
        val file = fixtureNamed(ListsFixtures.RICH, "list-44-rich.pdf")
        openReaderOn(file) { _, _ ->
            val list = openHighlightsAndScan()
            val position = list.positionOfPagePill(PAGE_NINE)
            list.scrollTo(position)
            val doomedQuote = list.rowPrimaryText(position)
            val target = list.items()[position]
            val doomedGroup = target.groupKey
            val targetPage = target.pageIndex

            list.tapRow(position)
            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReaderSettledOn(targetPage)

            tapFocusedHighlight(target)
            AnnotationCard.awaitButtonShown(R.id.textSelectionDeleteHighlightButton)
            AnnotationCard.tapButton(R.id.textSelectionDeleteHighlightButton)

            val journal = journalOfOpenDocument()
            val lines = AnnotationJournalFile.awaitLineCount(journal, 1)
            assertEquals("the journal records a delete", AnnotationJournalFile.OP_DELETE, lines.single().op)
            assertEquals("the deleted group", doomedGroup, lines.single().group)

            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationReaderUi.tapSaveFab()
            DestinationChooser.awaitShown()
            DestinationChooser.chooseUpdateExisting()
            DestinationChooser.assertNoSystemPickerWhile()
            AnnotationJournalFile.awaitAbsent(journal, Await.VERY_LONG_TIMEOUT_MS)

            val reopened = openHighlightsAndScan()
            reopened.awaitItemCount(ListsFixtures.richGroups.size - 1)
            assertFalse("the deleted highlight is still listed", reopened.groupKeys().contains(doomedGroup))
            assertFalse("the deleted quote is still listed", reopened.allPrimaryTexts().contains(doomedQuote))
            assertFalse("a row still reads $PAGE_NINE", reopened.allPagePills().contains(PAGE_NINE))
        }
    }

    @Test
    @TestId("LIST-45")
    fun editingANoteInTheReaderUpdatesTheNotesList() {
        Fixtures.ensureAllFilesAccess()
        val file = fixtureNamed(ListsFixtures.RICH, "list-45-rich.pdf")
        openReaderOn(file) { _, _ ->
            val list = openNotesAndScan()
            list.scrollTo(0)
            val originalNote = list.rowPrimaryText(0)
            val target = list.items()[0]
            val group = target.groupKey
            val targetPage = target.pageIndex

            list.tapRow(0)
            Screens.awaitGone(UserNotesActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReaderSettledOn(targetPage)

            tapFocusedHighlight(target)
            AnnotationCard.awaitButtonShown(R.id.textSelectionNoteButton)
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)
            NoteDialog.tapEdit()
            NoteDialog.replaceNoteText(REVISED_NOTE)
            NoteDialog.tapSave()

            val journal = journalOfOpenDocument()
            val lines = AnnotationJournalFile.awaitLineCount(journal, 1)
            assertEquals(
                "the journal records a setNote op",
                AnnotationJournalFile.OP_SET_NOTE,
                lines.single().op,
            )
            assertEquals("the edited group", group, lines.single().group)
            assertEquals("the written note", REVISED_NOTE, lines.single().note)

            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationReaderUi.tapSaveFab()
            DestinationChooser.awaitShown()
            DestinationChooser.chooseUpdateExisting()
            DestinationChooser.assertNoSystemPickerWhile()
            AnnotationJournalFile.awaitAbsent(journal, Await.VERY_LONG_TIMEOUT_MS)

            val reopened = openNotesAndScan()
            reopened.awaitItemCount(ListsFixtures.richNotedGroups.size)
            assertTrue(
                "no row reads the revised note, the rows read ${reopened.allPrimaryTexts()}",
                reopened.allPrimaryTexts().contains(REVISED_NOTE),
            )
            assertFalse(
                "the old note text is still listed",
                reopened.allPrimaryTexts().contains(originalNote),
            )
        }
    }

    private companion object {
        const val PAGE_NINE = "Page 9"
        const val REVISED_NOTE = "revised marginal note"
    }
}
