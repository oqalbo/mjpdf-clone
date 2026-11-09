// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class AnnotationRestoreTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-36")
    fun aColdStartOffersToRestoreAndRestoreReplaysEveryEdit() {
        val file = fixture(AnnotationFixtures.NORMAL)
        val journal = makeThreeEdits(file)
        val journalBefore = journal.readBytes()

        val reopened = ActivityScenario.launch<MainActivity>(intentFor(file))
        reopened.use {
            AnnotationDialogs.awaitTitle(string(R.string.restore_unsaved_highlights_title), Await.LONG_TIMEOUT_MS)
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.waitForIdleSync()
            assertTrue(
                "the restore prompt must not be dismissable by a back press",
                AnnotationDialogs.isTitleShown(string(R.string.restore_unsaved_highlights_title)),
            )

            AnnotationDialogs.pressPositive()
            awaitReaderReady()

            AnnotationJournalFile.assertLineCountStays(journal, 3)
            assertTrue(
                "restoring must not rewrite the journal",
                journalBefore.contentEquals(journal.readBytes()),
            )
            val group = AnnotationDump.awaitHighlightGroups(0, 1).single()
            assertEquals("the replayed colour is the last one applied", HighlightPalette.GREEN.colorValue, group.color)
            assertEquals("the replayed note comes back with it", RESTORED_NOTE, group.note)
            AnnotationReaderUi.awaitSaveFabVisible()
        }
    }

    @Test
    @TestId("ANN-37")
    fun discardingAtTheRestorePromptDeletesTheJournal() {
        val file = fixture(AnnotationFixtures.NORMAL)
        val journal = makeOneEdit(file)

        ActivityScenario.launch<MainActivity>(intentFor(file)).use {
            AnnotationDialogs.awaitTitle(string(R.string.restore_unsaved_highlights_title), Await.LONG_TIMEOUT_MS)

            AnnotationDialogs.pressNegative()
            awaitReaderReady()

            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)
            assertTrue("the discarded edit must not reach the document", AnnotationDump.highlights(0).isEmpty())
            AnnotationReaderUi.assertBothFabsStayGone()
        }

        ActivityScenario.launch<MainActivity>(intentFor(file)).use {
            awaitReaderReady()
            AnnotationDialogs.assertTitleNeverShown(string(R.string.restore_unsaved_highlights_title))
        }
    }

    private fun makeOneEdit(file: File): File {
        var journal: File? = null
        ActivityScenario.launch<MainActivity>(intentFor(file)).use {
            awaitReaderReady()
            journal = journal()
            findSelectionPointOnNormalPage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            AnnotationJournalFile.awaitLineCount(journal, 1)
        }
        return journal!!
    }

    private fun makeThreeEdits(file: File): File {
        var journal: File? = null
        ActivityScenario.launch<MainActivity>(intentFor(file)).use {
            awaitReaderReady()
            journal = journal()

            val point = findSelectionPointOnNormalPage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            longPressViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(HighlightPalette.GREEN)
            AnnotationJournalFile.awaitLineCount(journal, 2)

            tapViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)
            AnnotationDialogs.awaitTitle(string(R.string.note))
            AnnotationDialogs.typeIntoEditor(RESTORED_NOTE)
            AnnotationDialogs.pressPositive()
            AnnotationJournalFile.awaitLineCount(journal, 3)
        }
        return journal!!
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val RESTORED_NOTE = "keep me"
    }
}
