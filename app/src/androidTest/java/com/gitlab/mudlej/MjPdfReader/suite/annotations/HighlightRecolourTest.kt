// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HighlightRecolourTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-19")
    fun recolouringAnExistingHighlightRecordsARecolorEdit() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(HighlightPalette.BLUE)

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("the journalled operation", AnnotationJournalFile.OP_RECOLOR, line.op)
            assertEquals("the journalled page index", 0, line.page)
            assertEquals("the journalled group", AnnotationFixtures.SINGLE_LINE_GROUP, line.group)
            assertEquals("the journalled colour", HighlightPalette.BLUE.colorValue, line.color)

            AnnotationCard.awaitGone()
            AnnotationDump.awaitHighlightGroup(0, AnnotationFixtures.SINGLE_LINE_GROUP) {
                it.color == HighlightPalette.BLUE.colorValue
            }
            AnnotationReaderUi.awaitSaveFabVisible()
        }
    }

    @Test
    @TestId("ANN-20")
    fun recolouringTwiceRecordsBothEditsInOrder() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()
            val firstFraction = 0.1f
            val secondFraction = 0.65f
            val firstPoint = viewPointOf(AnnotationFixtures.singleLineRect, firstFraction)
            val secondPoint = viewPointOf(AnnotationFixtures.singleLineRect, secondFraction)
            val doubleTapSlop = ViewConfiguration.get(TestEnvironment.targetContext).scaledDoubleTapSlop
            assertTrue("the two selections must be outside the double-tap distance", secondPoint.x - firstPoint.x > doubleTapSlop)
            val zoom = requireNotNull(reader.readState().zoom) { "the loaded reader must report its zoom" }

            tapPdfRect(AnnotationFixtures.singleLineRect, firstFraction)
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(HighlightPalette.BLUE)
            AnnotationJournalFile.awaitLineCount(journal, 1)
            AnnotationCard.awaitGone()

            tapPdfRect(AnnotationFixtures.singleLineRect, secondFraction)
            AnnotationCard.awaitShown()
            assertEquals("separate highlight selections must not trigger double-tap zoom", zoom, reader.readState().zoom)
            AnnotationCard.tapSwatch(HighlightPalette.GREEN)

            val lines = AnnotationJournalFile.awaitLineCount(journal, 2)
            assertEquals(
                "both edits are recolours",
                listOf(AnnotationJournalFile.OP_RECOLOR, AnnotationJournalFile.OP_RECOLOR),
                lines.map { it.op },
            )
            assertEquals(
                "both edits name the same group",
                listOf(AnnotationFixtures.SINGLE_LINE_GROUP, AnnotationFixtures.SINGLE_LINE_GROUP),
                lines.map { it.group },
            )
            assertEquals(
                "the colours are journalled in the order they were applied",
                listOf(HighlightPalette.BLUE.colorValue, HighlightPalette.GREEN.colorValue),
                lines.map { it.color },
            )
            AnnotationDump.awaitHighlightGroup(0, AnnotationFixtures.SINGLE_LINE_GROUP) {
                it.color == HighlightPalette.GREEN.colorValue
            }
        }
    }

    @Test
    @TestId("ANN-21")
    fun recolouringToTheColourItAlreadyHasStillRecordsAnEdit() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            val point = findSelectionPointOnNormalPage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            longPressViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)

            val lines = AnnotationJournalFile.awaitLineCount(journal, 2)
            assertEquals(
                "a recolour to the same colour is still journalled",
                listOf(AnnotationJournalFile.OP_ADD, AnnotationJournalFile.OP_RECOLOR),
                lines.map { it.op },
            )
            assertEquals(
                "both lines carry the same colour",
                listOf(HighlightPalette.YELLOW.colorValue, HighlightPalette.YELLOW.colorValue),
                lines.map { it.color },
            )
            assertEquals("both lines name one group", lines[0].group, lines[1].group)
            assertEquals(
                "the page still carries exactly one highlight",
                1,
                AnnotationDump.highlights(0).size,
            )
        }
    }
}
