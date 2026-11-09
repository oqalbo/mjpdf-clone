// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class UnsavedHighlightsGateTest : ListsTestCase() {

    @Test
    @TestId("LIST-41")
    fun unsavedHighlightsAreSavedBeforeTheListOpens() {
        Fixtures.ensureAllFilesAccess()
        val file = fixtureNamed(ListsFixtures.PLAIN, "list-41-plain.pdf")
        openReaderOn(file) { _, _ ->
            val created = createUnsavedHighlight()
            val journal = journalOfOpenDocument()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            chooseReaderAction(R.string.user_highlights_title)
            UnsavedDialog.awaitShown()
            assertFalse(
                "the destination chooser was shown before the unsaved highlights dialog",
                DestinationChooser.isShown(),
            )

            UnsavedDialog.tapSaveChanges()
            assertEquals(
                "the destination chooser offers the update and copy options in that order",
                listOf(DestinationChooser.updateExistingLabel(), DestinationChooser.saveAsNewCopyLabel()),
                DestinationChooser.labelsInScreenOrder(),
            )
            DestinationChooser.chooseUpdateExisting()
            DestinationChooser.assertNoSystemPickerWhile()

            val list = highlights
            list.awaitShown(Await.VERY_LONG_TIMEOUT_MS)
            list.awaitScanFinished()

            list.awaitItemCount(PLAIN_GROUPS + 1)
            assertTrue(
                "the new highlight's quote is missing from the list, whose rows read ${list.allPrimaryTexts()}",
                list.groupKeys().contains(created.group),
            )
            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("LIST-42")
    fun discardingUnsavedHighlightsKeepsThemOutOfTheList() {
        val file = fixtureNamed(ListsFixtures.PLAIN, "list-42-plain.pdf")
        val digestBefore = FileDigest.md5(file)
        openReaderOn(file) { _, _ ->
            val created = createUnsavedHighlight()
            val journal = journalOfOpenDocument()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            chooseReaderAction(R.string.user_highlights_title)
            UnsavedDialog.awaitShown()
            UnsavedDialog.tapDiscard()
            DestinationChooser.assertNeverShown()

            val list = highlights
            list.awaitShown(Await.VERY_LONG_TIMEOUT_MS)
            list.awaitScanFinished()

            list.awaitItemCount(PLAIN_GROUPS)
            assertFalse(
                "the discarded highlight is listed",
                list.groupKeys().contains(created.group),
            )
            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)
            assertEquals("the document bytes changed although nothing was saved", digestBefore, FileDigest.md5(file))
        }
    }

    @Test
    @TestId("LIST-43")
    fun cancellingTheUnsavedPromptKeepsTheUserInTheReader() {
        val file = fixtureNamed(ListsFixtures.PLAIN, "list-43-plain.pdf")
        openReaderOn(file) { _, _ ->
            val created = createUnsavedHighlight()
            val journal = journalOfOpenDocument()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            chooseReaderAction(R.string.user_highlights_title)
            UnsavedDialog.awaitShown()
            UnsavedDialog.tapCancel()
            UnsavedDialog.awaitGone()

            DestinationChooser.assertNeverShown()
            assertEquals("the resumed activity", MainActivity::class.java.name, Screens.resumedName())
            assertFalse(
                "a highlights list was created even though the prompt was cancelled",
                Screens.isInTask(UserHighlightsActivity::class.java),
            )
            assertTrue("the save annotations action is no longer offered", AnnotationReaderUi.saveFab().visible)

            val lines = AnnotationJournalFile.awaitLineCount(journal, 1)
            assertEquals("the pending edit was dropped", AnnotationJournalFile.OP_ADD, lines.single().op)
            assertEquals("the pending edit lost its group key", created.group, lines.single().group)
        }
    }

    @Test
    @TestId("LIST-58")
    fun historyDisabledStillListsHighlightsWithoutRecordingTheDocument() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        Fixtures.ensureAllFilesAccess()
        val file = fixtureNamed(ListsFixtures.PLAIN, "list-58-plain.pdf")

        openReaderOn(file) { _, _ ->
            val identity = reader.awaitFileHashResolved()
            val created = createUnsavedHighlight()
            val journal = journalOfOpenDocument()
            AnnotationJournalFile.assertStaysAbsent(journal)

            chooseReaderAction(R.string.user_highlights_title)
            UnsavedDialog.awaitShown()
            UnsavedDialog.tapSaveChanges()
            DestinationChooser.chooseUpdateExisting()
            DestinationChooser.assertNoSystemPickerWhile()

            val list = highlights
            list.awaitShown(Await.VERY_LONG_TIMEOUT_MS)
            list.awaitScanFinished()
            list.awaitItemCount(PLAIN_GROUPS + 1)

            assertTrue(
                "the saved highlight is missing from the list, whose groups are ${list.groupKeys()}",
                list.groupKeys().contains(created.group),
            )
            AnnotationJournalFile.assertStaysAbsent(journal)
            Records.assertNoRowFor(identity)
        }
    }

    private companion object {
        const val PLAIN_GROUPS = 2
    }
}
