// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HighlightNoteTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-22")
    fun aNoteWrittenOnAHighlightIsAttachedToThatHighlightAndKept() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            val (group, point) = createHighlightOnNormalPage()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapViewPoint(point)
            AnnotationCard.awaitShown()
            assertEquals(
                "a highlight with no note must offer to add one",
                string(R.string.add_note),
                AnnotationCard.buttonDescription(R.id.textSelectionNoteButton),
            )
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)

            AnnotationDialogs.awaitTitle(string(R.string.note))
            AnnotationDialogs.typeIntoEditor(NOTE_TEXT)
            AnnotationDialogs.pressPositive()

            val lines = AnnotationJournalFile.awaitLineCount(journal, 2)
            val setNote = lines[1]
            assertEquals("the second edit records the note", AnnotationJournalFile.OP_SET_NOTE, setNote.op)
            assertEquals("the note belongs to the highlight that was open", group, setNote.group)
            assertEquals("the note text is stored as typed", NOTE_TEXT, setNote.note)
            assertTrue(
                "a note edit carries the moment it was modified",
                !setNote.modified.isNullOrBlank(),
            )

            AnnotationDump.awaitHighlightGroup(0, group) { it.note == NOTE_TEXT }
            AnnotationCard.awaitGone()
        }
    }

    @Test
    @TestId("ANN-23")
    fun aNoteOnABareSelectionCreatesAGrayHighlightToAnchorIt() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            findSelectionPointOnNormalPage()
            assertEquals(
                "a bare selection offers to add a note",
                string(R.string.add_note),
                AnnotationCard.buttonDescription(R.id.textSelectionNoteButton),
            )
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)

            AnnotationDialogs.awaitTitle(string(R.string.note))
            AnnotationDialogs.typeIntoEditor(SOURCE_NOTE_TEXT)
            AnnotationDialogs.pressPositive()

            val lines = AnnotationJournalFile.awaitLineCount(journal, 2)
            assertEquals(
                "the note is anchored by an add followed by a setNote",
                listOf(AnnotationJournalFile.OP_ADD, AnnotationJournalFile.OP_SET_NOTE),
                lines.map { it.op },
            )
            assertEquals(
                "the anchor highlight uses the reserved note colour",
                HighlightPalette.GRAY.colorValue,
                lines[0].color,
            )
            assertTrue("the anchor highlight stores the selected text", lines[0].contents!!.isNotBlank())
            assertEquals("both lines name one group", lines[0].group, lines[1].group)
            assertEquals("the note text is stored as typed", SOURCE_NOTE_TEXT, lines[1].note)

            val groups = AnnotationDump.awaitHighlightGroups(0, 1)
            assertEquals("the anchor highlight is gray", HighlightPalette.GRAY.colorValue, groups.single().color)
            assertEquals("the anchor highlight carries the note", SOURCE_NOTE_TEXT, groups.single().note)
        }
    }

    @Test
    @TestId("ANN-24")
    @TestDuration(DurationCategory.NORMAL)
    fun cancellingTheNoteEditorRecordsNothing() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()
            assertEquals(
                "the fixture highlight already carries a note",
                string(R.string.view_note),
                AnnotationCard.buttonDescription(R.id.textSelectionNoteButton),
            )
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)

            AnnotationDialogs.awaitTextShown(AnnotationFixtures.SINGLE_LINE_NOTE)
            AnnotationDialogs.pressPositive()

            AnnotationDialogs.awaitTitle(string(R.string.note))
            AnnotationDialogs.typeIntoEditor(AnnotationFixtures.SINGLE_LINE_NOTE + ABANDONED_SUFFIX)
            AnnotationDialogs.pressNegative()

            AnnotationJournalFile.assertStaysAbsent(journal)
            assertEquals(
                "the highlight keeps the note the fixture carried",
                AnnotationFixtures.SINGLE_LINE_NOTE,
                AnnotationDump.requireHighlightGroup(0, AnnotationFixtures.SINGLE_LINE_GROUP).note,
            )
            assertFalse(
                "an abandoned note must not put the document into an unsaved state",
                AnnotationReaderUi.saveFab().visible,
            )
        }
    }

    @Test
    @TestId("ANN-25")
    @TestDuration(DurationCategory.NORMAL)
    fun anEmptyNoteOnANoteFreeHighlightRecordsNothing() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            val (_, point) = createHighlightOnNormalPage()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)

            AnnotationDialogs.awaitTitle(string(R.string.note))
            AnnotationDialogs.typeIntoEditor("")
            AnnotationDialogs.pressPositive()

            AnnotationJournalFile.assertLineCountStays(journal, 1)
            assertTrue(
                "the blank on blank path returns before the card is hidden",
                AnnotationCard.isShown(),
            )
        }
    }

    @Test
    @TestId("ANN-26")
    fun aNoteIsTrimmedBeforeItIsStored() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            val (_, point) = createHighlightOnNormalPage()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)

            AnnotationDialogs.awaitTitle(string(R.string.note))
            AnnotationDialogs.typeIntoEditor(PADDED_NOTE_TEXT)
            AnnotationDialogs.pressPositive()

            val lines = AnnotationJournalFile.awaitLineCount(journal, 2)
            assertEquals("the note is stored without its padding", TRIMMED_NOTE_TEXT, lines[1].note)
        }
    }

    @Test
    @TestId("ANN-27")
    fun viewingANoteShowsItAndEditReopensItPrefilled() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)

            AnnotationDialogs.awaitTextShown(AnnotationFixtures.SINGLE_LINE_NOTE)
            assertEquals(
                "the view dialog shows the stored note",
                AnnotationFixtures.SINGLE_LINE_NOTE,
                AnnotationDialogs.messageText(),
            )

            AnnotationDialogs.pressPositive()
            AnnotationDialogs.awaitTitle(string(R.string.note))

            assertEquals(
                "the editor opens prefilled with the stored note",
                AnnotationFixtures.SINGLE_LINE_NOTE,
                AnnotationDialogs.editorText(),
            )
            assertEquals(
                "the caret sits at the end of the note",
                AnnotationFixtures.SINGLE_LINE_NOTE.length,
                AnnotationDialogs.editorSelectionStart(),
            )
            assertEquals(
                "nothing is selected, the caret is collapsed",
                AnnotationDialogs.editorSelectionStart(),
                AnnotationDialogs.editorSelectionEnd(),
            )

            AnnotationJournalFile.assertStaysAbsent(journal)
        }
    }

    @Test
    @TestId("ANN-28")
    fun deletingANoteThroughTheViewDialogKeepsTheHighlight() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)

            AnnotationDialogs.awaitTextShown(AnnotationFixtures.SINGLE_LINE_NOTE)
            AnnotationDialogs.pressNeutral()

            AnnotationDialogs.awaitTitle(string(R.string.delete_note_title))
            AnnotationDialogs.pressPositive()

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("removing a note is a setNote edit", AnnotationJournalFile.OP_SET_NOTE, line.op)
            assertEquals("the edit names the fixture group", AnnotationFixtures.SINGLE_LINE_GROUP, line.group)
            assertEquals("the note is cleared rather than deleted", "", line.note)

            AnnotationDump.awaitHighlightGroup(0, AnnotationFixtures.SINGLE_LINE_GROUP) { it.note.isEmpty() }

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()
            assertEquals(
                "the highlight now offers to add a note",
                string(R.string.add_note),
                AnnotationCard.buttonDescription(R.id.textSelectionNoteButton),
            )
        }
    }

    @Test
    @TestId("ANN-29")
    @TestDuration(DurationCategory.NORMAL)
    fun cancellingTheNoteDeleteConfirmationRecordsNothing() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)

            AnnotationDialogs.awaitTextShown(AnnotationFixtures.SINGLE_LINE_NOTE)
            AnnotationDialogs.pressNeutral()

            AnnotationDialogs.awaitTitle(string(R.string.delete_note_title))
            AnnotationDialogs.pressNegative()

            AnnotationJournalFile.assertStaysAbsent(journal)
            assertEquals(
                "the note is only lost when the user confirms losing it",
                AnnotationFixtures.SINGLE_LINE_NOTE,
                AnnotationDump.requireHighlightGroup(0, AnnotationFixtures.SINGLE_LINE_GROUP).note,
            )
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val NOTE_TEXT = "Check this claim"
        const val SOURCE_NOTE_TEXT = "Look up the source"
        const val PADDED_NOTE_TEXT = "   spaced note   "
        const val TRIMMED_NOTE_TEXT = "spaced note"
        const val ABANDONED_SUFFIX = " and more"
    }
}
