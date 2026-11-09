// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HighlightCreationTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-01")
    fun highlightingASelectionWithTheFirstSwatchStoresThatColourAndRecordsIt() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            startSelectionOnNormalPage()
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(AnnotationCard.indexOfSwatch(descriptionOf(HighlightPalette.YELLOW)))

            val lines = AnnotationJournalFile.awaitLineCount(journal, 1)
            val line = lines.single()
            assertEquals("the journalled operation", AnnotationJournalFile.OP_ADD, line.op)
            assertEquals("the journalled page index", 0, line.page)
            assertEquals(
                "the journalled colour",
                HighlightPalette.YELLOW.colorValue,
                line.color,
            )
            assertTrue("the journalled quote must not be blank", line.contents!!.isNotBlank())
            assertTrue("the journalled rectangles must not be empty", line.rects.isNotEmpty())
            line.rects.forEach { rect ->
                assertEquals("every journalled rectangle carries four values", 4, rect.size)
            }
            assertNotNull("the journalled group must parse as a UUID", UUID.fromString(line.group))

            val group = AnnotationDump.requireHighlightGroup(0, line.group!!)
            assertEquals(
                "the live highlight dump must carry the journalled quote",
                line.contents,
                group.quote,
            )
            assertEquals("the live highlight colour", HighlightPalette.YELLOW.colorValue, group.color)

            AnnotationReaderUi.awaitSaveFabVisible()
            assertTrue("the discard button must appear beside the save button", AnnotationReaderUi.discardFab().visible)
        }
    }

    @Test
    @TestId("ANN-03")
    fun theCardOffersTheSixDefaultSwatchesInTheDefaultOrder() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            startSelectionOnNormalPage()
            AnnotationCard.awaitShown()

            val descriptions = AnnotationCard.awaitSwatches(HighlightPalette.defaultSelection.size)

            assertEquals(
                "the swatch row must be the default palette in order",
                HighlightPalette.defaultSelection.map(::descriptionOf),
                descriptions,
            )
            assertFalse(
                "the reserved note colour must never be offered as a swatch",
                descriptions.contains(descriptionOf(HighlightPalette.GRAY)),
            )
        }
    }

    @Test
    @TestId("ANN-07")
    fun aSelectionRunningAcrossThreeLinesIsStoredAsOneAnnotationWithThreeRectangles() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            startSelectionOnNormalPage()
            AnnotationCard.awaitShown()

            val linePitch = AnnotationFixtures.NORMAL_BODY_LEADING * frame().viewPixelsPerPoint()
            selection.dragEndHandleBy(0f, linePitch * LINES_TO_EXTEND)
            val selectedRects = selection.selectionRects().size
            assertEquals(
                "the drag must leave a selection spanning three rendered lines",
                EXPECTED_SPAN_LINES,
                selectedRects,
            )

            AnnotationCard.tapSwatch(AnnotationCard.indexOfSwatch(descriptionOf(HighlightPalette.YELLOW)))

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("the multi line highlight is one journal line", AnnotationJournalFile.OP_ADD, line.op)
            assertEquals("one rectangle per rendered line", EXPECTED_SPAN_LINES, line.rects.size)

            val pageSize = AnnotationGeometry.pagePointSize(0)
            line.rects.forEach { rect ->
                assertEquals("every rectangle carries four values", 4, rect.size)
                rect.forEach { value ->
                    assertTrue("every rectangle value must be finite, read $rect", value.isFinite())
                }
                assertTrue("a rectangle must run left to right, read $rect", rect[0] < rect[2])
                assertTrue(
                    "the horizontal edges must sit inside the ${pageSize.x} point wide page, read $rect",
                    rect[0] >= 0f && rect[2] <= pageSize.x,
                )
                assertTrue(
                    "the vertical edges must sit inside the ${pageSize.y} point tall page, read $rect",
                    rect[1] in 0f..pageSize.y && rect[3] in 0f..pageSize.y,
                )
            }

            val group = AnnotationDump.requireHighlightGroup(0, line.group!!)
            assertEquals("one group covering three annotation rectangles", EXPECTED_SPAN_LINES, group.rects)
        }
    }

    @Test
    @TestId("ANN-09")
    @TestDuration(DurationCategory.LONG)
    fun withInlineTextSelectionOffALongPressNeitherSelectsNorPansThePage() {
        SettingsSeeder.seed(Preferences.inlineTextSelectionKey, true)
        var provenLine = -1
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            provenLine = NORMAL_SELECTION_LINES.firstOrNull { line ->
                runCatching { longPressNormalBodyLine(line) }.isSuccess
            } ?: -1
            if (provenLine >= 0) {
                AnnotationCard.awaitShown()
            }
        }
        assertTrue(
            "the control pass found no line that selects with inline text selection on, candidates were " +
                "$NORMAL_SELECTION_LINES",
            provenLine >= 0,
        )

        SettingsSeeder.seed(Preferences.inlineTextSelectionKey, false)
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            val before = reader.readState()
            val pageBefore = before.page
            val offsetBefore = before.offsetY

            runCatching { longPressNormalBodyLine(provenLine) }

            reader.awaitMotionSettled()
            val after = reader.awaitFullQualityRender()

            AnnotationCard.assertStaysGone()
            AnnotationJournalFile.assertStaysAbsent(journal)
            assertEquals("the long press must not turn the page", pageBefore, after.page)
            assertEquals(
                "the long press must not pan the page",
                offsetBefore!!,
                after.offsetY!!,
                OFFSET_TOLERANCE_PX,
            )
            assertFalse(
                "no selection may exist with inline text selection off, and line $provenLine is the line the " +
                    "control pass above proved does select when the setting is on, so a clean result here is " +
                    "the setting working rather than the gesture missing the text",
                after.hasSelection,
            )
        }
    }

    @Test
    @TestId("ANN-10")
    fun creatingAHighlightClearsTheSelectionAndClosesTheCard() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            startSelectionOnNormalPage()
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(AnnotationCard.indexOfSwatch(descriptionOf(HighlightPalette.YELLOW)))

            AnnotationCard.awaitGone()
            reader.awaitNoSelection()
            AnnotationJournalFile.awaitLineCount(journal, 1)
        }
    }

    @Test
    @TestId("ANN-11")
    @TestDuration(DurationCategory.NORMAL)
    fun aRefusedHighlightWriteSaysSoAndRecordsNothing() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            val selectionPoint = findSelectionPointOnNormalPage()
            AnnotationCard.awaitShown()

            AnnotationTestHooks.failNextHighlightWrite()
            AnnotationCard.tapSwatch(AnnotationCard.indexOfSwatch(descriptionOf(HighlightPalette.YELLOW)))

            Snackbars.awaitText(TestEnvironment.targetContext.getString(R.string.highlight_failed))
            AnnotationJournalFile.assertStaysAbsent(journal)
            assertFalse(
                "a refused highlight must not put the document into an unsaved state",
                AnnotationReaderUi.saveFab().visible,
            )
            assertTrue("no highlight may reach the document", AnnotationDump.highlights(0).isEmpty())

            AnnotationCard.clickButtonWithoutTouch(R.id.textSelectionDiscardButton)
            AnnotationCard.awaitGone()
            reader.awaitNoSelection()

            val pointOnScreen = selection.toScreen(selectionPoint)
            TestEnvironment.uiDevice.click(pointOnScreen.x.toInt(), pointOnScreen.y.toInt())
            TestEnvironment.waitForIdleSync()
            AnnotationCard.assertStaysGone()
        }
    }

    private fun descriptionOf(palette: HighlightPalette): String = AnnotationCard.descriptionOf(palette)

    private companion object {
        const val LINES_TO_EXTEND = 2f
        const val EXPECTED_SPAN_LINES = 3
        const val OFFSET_TOLERANCE_PX = 0.5f
    }
}
