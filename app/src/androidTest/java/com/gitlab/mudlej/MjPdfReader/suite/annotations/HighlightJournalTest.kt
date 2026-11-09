// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.annotationsave.SaveDigest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HighlightJournalTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-30")
    fun deletingAHighlightRemovesItAndRecordsTheRemoval() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionDeleteHighlightButton)

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("the journalled operation", AnnotationJournalFile.OP_DELETE, line.op)
            assertEquals("the journalled page index", 0, line.page)
            assertEquals("the journalled group", AnnotationFixtures.SINGLE_LINE_GROUP, line.group)

            Await.until("the deleted group leaves the live highlight dump") {
                AnnotationDump.highlightGroup(0, AnnotationFixtures.SINGLE_LINE_GROUP) == null
            }
            AnnotationCard.awaitGone()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.assertStaysGone()
        }
    }

    @Test
    @TestId("ANN-31")
    fun theJournalLivesAtThePathDerivedFromTheDocumentUri() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val documentUri = AnnotationJournalFile.documentUriOfOpenDocument(reader)
            val journal = AnnotationJournalFile.fileFor(documentUri)

            findSelectionPointOnNormalPage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            val files = AnnotationJournalFile.files()
            assertEquals(
                "the annotation directory holds exactly one journal, read ${files.map { it.name }}",
                1,
                files.size,
            )
            assertEquals(
                "the journal is named for the document uri the reader is using",
                "${SaveDigest.sha256Hex(documentUri)}.journal",
                files.single().name,
            )

            val raw = AnnotationJournalFile.rawLines(journal)
            assertEquals("the journal holds one line", 1, raw.size)
            assertTrue("the journal line is newline terminated", AnnotationJournalFile.endsWithNewline(journal))
            val json = JSONObject(raw.single())
            assertTrue("the line carries an op", json.has("op"))
            assertTrue("the line carries a page", json.has("page"))
        }
    }

    @Test
    @TestId("ANN-32")
    fun incognitoAnnotatesWithoutLeavingARecoveryTrail() {
        openReader(AnnotationFixtures.NORMAL, incognito = true) { _, _ ->
            findSelectionPointOnNormalPage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            awaitDirty(true)

            val groups = AnnotationDump.awaitHighlightGroups(0, 1)
            assertEquals(
                "the edit still reaches the open document",
                HighlightPalette.YELLOW.colorValue,
                groups.single().color,
            )
            assertTrue(
                "incognito must leave no journal behind, found ${AnnotationJournalFile.files().map { it.name }}",
                AnnotationJournalFile.files().isEmpty(),
            )
            AnnotationJournalFile.assertStaysAbsent(AnnotationJournalFile.fileOfOpenDocument(reader))

            AnnotationReaderUi.awaitSaveFabVisible()
            assertTrue("the discard button sits beside the save button in incognito", AnnotationReaderUi.discardFab().visible)
        }
    }

    @Test
    @TestId("ANN-33")
    fun historyDisabledAnnotatesWithoutLeavingARecoveryTrail() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)

        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            findSelectionPointOnNormalPage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            awaitDirty(true)

            val groups = AnnotationDump.awaitHighlightGroups(0, 1)
            assertEquals(
                "the edit still reaches the open document",
                HighlightPalette.YELLOW.colorValue,
                groups.single().color,
            )
            assertTrue(
                "history off must leave no journal behind, found ${AnnotationJournalFile.files().map { it.name }}",
                AnnotationJournalFile.files().isEmpty(),
            )
            AnnotationJournalFile.assertStaysAbsent(AnnotationJournalFile.fileOfOpenDocument(reader))
            AnnotationReaderUi.awaitSaveFabVisible()
        }
    }

    @Test
    @TestId("ANN-34")
    fun anUnsavedHighlightSurvivesReadingOnAndComingBack() {
        openReader(AnnotationFixtures.LARGE) { _, _ ->
            val journal = journal()

            val point = findSelectionPointOnLargePage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            AnnotationGeometry.jumpToPage(DISTANT_PAGE_INDEX)
            reader.awaitPageIndex(DISTANT_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

            AnnotationGeometry.jumpToPage(0)
            reader.awaitPageIndex(0, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

            tapViewPoint(point)
            AnnotationCard.awaitShown()
            assertTrue(
                "the highlight must still be reachable after the round trip",
                AnnotationCard.isButtonShown(R.id.textSelectionDeleteHighlightButton),
            )

            AnnotationJournalFile.assertLineCountStays(journal, 1)
            val groups = AnnotationDump.awaitHighlightGroups(0, 1)
            assertEquals(
                "the highlight keeps its colour",
                HighlightPalette.YELLOW.colorValue,
                groups.single().color,
            )
        }
    }

    @Test
    @TestId("ANN-38")
    @TestDuration(DurationCategory.NORMAL)
    fun discardingUnsavedChangesFromTheReaderClearsTheJournal() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            val (_, point) = createHighlightOnNormalPage()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            AnnotationReaderUi.tapDiscardFab()

            AnnotationDialogs.awaitTitle(string(R.string.discard_unsaved_highlights_title))
            AnnotationDialogs.pressPositive()

            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)
            awaitReaderReady()

            assertTrue("the discarded highlight leaves the document", AnnotationDump.highlights(0).isEmpty())
            AnnotationReaderUi.assertBothFabsStayGone()

            tapViewPoint(point)
            AnnotationCard.assertStaysGone()
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val DISTANT_PAGE_INDEX = 39
    }
}
