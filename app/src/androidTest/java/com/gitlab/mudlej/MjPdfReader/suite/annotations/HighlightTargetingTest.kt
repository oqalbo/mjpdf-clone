// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HighlightTargetingTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-12")
    fun tappingAnAppAuthoredHighlightOpensTheActionsThatApplyToAHighlight() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)

            AnnotationCard.awaitShown()
            assertTrue(
                "a highlight card must offer delete",
                AnnotationCard.isButtonShown(R.id.textSelectionDeleteHighlightButton),
            )
            assertFalse(
                "a highlight card must not offer the selection discard button",
                AnnotationCard.isButtonShown(R.id.textSelectionDiscardButton),
            )
            listOf(
                R.id.textSelectionCopyButton,
                R.id.textSelectionShareButton,
                R.id.textSelectionSearchWebButton,
                R.id.textSelectionTranslateButton,
            ).forEach { button ->
                assertTrue(
                    "a highlight carrying a quote must offer every text action",
                    AnnotationCard.isButtonShown(button),
                )
            }
            assertEquals(
                "a highlight that already carries a note must offer to view it",
                string(R.string.view_note),
                AnnotationCard.buttonDescription(R.id.textSelectionNoteButton),
            )

            AnnotationJournalFile.assertStaysAbsent(journal)
        }
    }

    @Test
    @TestId("ANN-13")
    fun aHighlightWithNoQuoteHidesEveryActionThatWouldNeedText() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            tapPdfRect(AnnotationFixtures.noQuoteRect)

            AnnotationCard.awaitShown()
            listOf(
                R.id.textSelectionCopyButton,
                R.id.textSelectionShareButton,
                R.id.textSelectionSearchWebButton,
                R.id.textSelectionTranslateButton,
            ).forEach { button ->
                assertFalse(
                    "a quoteless highlight must not offer an action that needs text",
                    AnnotationCard.isButtonShown(button),
                )
            }
            assertTrue(
                "a quoteless highlight still offers its note",
                AnnotationCard.isButtonShown(R.id.textSelectionNoteButton),
            )
            assertTrue(
                "a quoteless highlight still offers delete",
                AnnotationCard.isButtonShown(R.id.textSelectionDeleteHighlightButton),
            )
        }
    }

    @Test
    @TestId("ANN-14")
    fun tappingEmptySpaceDismissesTheActionsAndBringsTheReadingControlsBack() {
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)

        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()

            AnnotationReaderUi.requireScrollHandleHidden()
            tapBlankSpot()

            AnnotationCard.awaitGone()
            AnnotationReaderUi.awaitScrollHandleVisible(true)
            AnnotationJournalFile.assertStaysAbsent(journal)
        }
    }

    @Test
    @TestId("ANN-15")
    fun aThirdPartyHighlightIsNotTargetedAndTheTapFallsThrough() {
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)

        openReader(AnnotationFixtures.FOREIGN) { _, _ ->
            val journal = journal()

            AnnotationReaderUi.requireScrollHandleHidden()
            tapPdfRect(AnnotationFixtures.foreignRect)

            AnnotationCard.assertStaysGone()
            AnnotationReaderUi.awaitScrollHandleVisible(true)
            AnnotationJournalFile.assertStaysAbsent(journal)
        }
    }

    @Test
    @TestId("ANN-16")
    fun selectingOverAnExistingHighlightTargetsItInsteadOfStackingASecond() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            val (group, point) = createHighlightOnNormalPage()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            longPressViewPoint(point)

            AnnotationCard.awaitShown()
            assertTrue(
                "re-selecting highlighted text must open the highlight card",
                AnnotationCard.isButtonShown(R.id.textSelectionDeleteHighlightButton),
            )
            assertFalse(
                "the selection discard button belongs to the selection card, not the highlight card",
                AnnotationCard.isButtonShown(R.id.textSelectionDiscardButton),
            )
            reader.awaitNoSelection()
            AnnotationJournalFile.assertLineCountStays(journal, 1)
            assertEquals(
                "no second group may be stacked on the first",
                listOf(group),
                AnnotationDump.highlights(0).map { it.group },
            )
        }
    }

    @Test
    @TestId("ANN-17")
    fun withDetectionOffTheSameSelectionCreatesASecondHighlight() {
        SettingsSeeder.seed(Preferences.detectExistingHighlightsKey, false)

        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            val (first, point) = createHighlightOnNormalPage()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            longPressViewPoint(point)
            AnnotationCard.awaitShown()

            assertTrue(
                "with detection off the plain selection card is what appears",
                AnnotationCard.isButtonShown(R.id.textSelectionDiscardButton),
            )
            assertFalse(
                "the highlight card must not be reached with detection off",
                AnnotationCard.isButtonShown(R.id.textSelectionDeleteHighlightButton),
            )

            AnnotationCard.tapSwatch(HighlightPalette.GREEN)

            val lines = AnnotationJournalFile.awaitLineCount(journal, 2)
            val second = lines[1]
            assertEquals("the second edit is another add", AnnotationJournalFile.OP_ADD, second.op)
            assertNotEquals("the second highlight owns a group of its own", first, second.group)
            AnnotationDump.awaitHighlightGroups(0, 2)
        }
    }

    @Test
    @TestId("ANN-18")
    fun overlappingHighlightsAreTargetedMostRecentFirst() {
        SettingsSeeder.seed(Preferences.detectExistingHighlightsKey, false)

        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            val point = findSelectionPointOnNormalPage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            longPressViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(HighlightPalette.BLUE)
            val added = AnnotationJournalFile.awaitLineCount(journal, 2)

            tapViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(HighlightPalette.GREEN)

            val lines = AnnotationJournalFile.awaitLineCount(journal, 3)
            assertEquals(
                "the journal reads add, add, recolor",
                listOf(
                    AnnotationJournalFile.OP_ADD,
                    AnnotationJournalFile.OP_ADD,
                    AnnotationJournalFile.OP_RECOLOR,
                ),
                lines.map { it.op },
            )
            assertEquals(
                "the recolour must land on the highlight that was made last",
                added[1].group,
                lines[2].group,
            )
            assertEquals(
                "the first highlight keeps the colour it was given",
                HighlightPalette.YELLOW.colorValue,
                AnnotationDump.requireHighlightGroup(0, added[0].group!!).color,
            )
            assertEquals(
                "the second highlight carries the new colour",
                HighlightPalette.GREEN.colorValue,
                AnnotationDump.requireHighlightGroup(0, added[1].group!!).color,
            )
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val LONG_HIDE_DELAY_MS = 60_000
    }
}
