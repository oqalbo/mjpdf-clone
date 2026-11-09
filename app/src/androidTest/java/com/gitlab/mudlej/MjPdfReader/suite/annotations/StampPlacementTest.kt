// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class StampPlacementTest : AnnotationTestCase() {

    private val stamp = StampRobot()

    @Test
    @TestId("ANN-53")
    fun draggingThePendingStampMovesItWithoutResizingIt() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            val before = placeSignature()
            val page = frame()
            val step = page.viewBounds.width() * MOVE_FRACTION

            stamp.dragCentreBy(page, -step, -step)

            val after = stamp.awaitStampSettled(before)
            assertEquals("a move must not change the width", before.width, after.width, ONE_PDF_UNIT)
            assertEquals("a move must not change the height", before.height, after.height, ONE_PDF_UNIT)
            assertTrue(
                "dragging up and to the left must move the left edge left, read $before then $after",
                after.left < before.left,
            )
            assertTrue(
                "dragging up and to the left must move the top edge up the page, read $before then $after",
                after.top > before.top,
            )
            assertInsidePage(after)
            AnnotationJournalFile.assertStaysAbsent(journal)
        }
    }

    @Test
    @TestId("ANN-54")
    fun resizingThePendingStampByACornerKeepsItsAspect() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val before = placeSignature()
            val page = frame()
            val aspectBefore = before.height / before.width
            val step = page.viewBounds.width() * RESIZE_FRACTION

            stamp.dragBottomRightBy(page, step, step)

            val after = stamp.awaitStampSettled(before)
            assertTrue(
                "dragging the corner outward must widen the stamp, read $before then $after",
                after.width > before.width,
            )
            assertEquals(
                "the aspect must survive the resize",
                aspectBefore,
                after.height / after.width,
                aspectBefore * ASPECT_TOLERANCE,
            )
            assertInsidePage(after)
        }
    }

    @Test
    @TestId("ANN-55")
    fun aStampCannotBeShrunkBelowTheMinimumWidth() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val before = placeSignature()
            val page = frame()
            val viewRect = stamp.viewRectOf(AnnotationDump.requirePendingStamp(), page)
            val beyondTopLeft = PointF(
                viewRect.left - viewRect.width(),
                viewRect.top - viewRect.height(),
            )

            stamp.dragBottomRightTo(page, beyondTopLeft)

            val after = stamp.awaitStampSettled(before)
            val pageWidth = AnnotationGeometry.pagePointSize(0).x
            assertTrue(
                "the stamp must keep at least a twentieth of the page width, read ${after.width} of $pageWidth",
                after.width >= pageWidth * MINIMUM_WIDTH_FRACTION - ONE_PDF_UNIT,
            )
            assertTrue("the stamp must keep a positive height, read $after", after.height > 0f)
        }
    }

    @Test
    @TestId("ANN-56")
    fun discardingAPendingStampLeavesNothingBehind() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()
            placeSignature()

            stamp.tapDiscardBadge(frame())

            AnnotationDump.awaitNoPendingStamp()
            AnnotationReaderUi.assertBothFabsStayGone()
            AnnotationJournalFile.assertStaysAbsent(journal)
        }
    }

    @Test
    @TestId("ANN-57")
    fun discardingAPendingStampKeepsTheEditsMadeBeforeIt() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            findSelectionPointOnNormalPage()
            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            val group = AnnotationJournalFile.awaitLineCount(journal, 1).single().group!!

            placeSignature()
            stamp.tapDiscardBadge(frame())
            AnnotationDump.awaitNoPendingStamp()

            AnnotationReaderUi.awaitSaveFabVisible()
            assertTrue(
                "the discard button must stay beside the save button",
                AnnotationReaderUi.discardFab().visible,
            )
            AnnotationJournalFile.assertLineCountStays(journal, 1)
            assertEquals(
                "the highlight made before the placement survives it",
                HighlightPalette.YELLOW.colorValue,
                AnnotationDump.requireHighlightGroup(0, group).color,
            )
        }
    }

    @Test
    @TestId("ANN-58")
    fun aSecondSignatureDialogIsRefusedWhileAStampIsPending() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val before = placeSignature()
            val pendingHint = string(R.string.signature_pending_hint)
            Snackbars.awaitText(pendingHint)
            Snackbars.awaitGone()

            SignaturePad.openFromReaderActions()

            Snackbars.awaitText(pendingHint)
            SignaturePad.assertDialogNeverShown()
            assertEquals(
                "the pending placement must be left exactly where it was",
                before,
                AnnotationDump.requirePendingStamp().rect,
            )
        }
    }

    @Test
    @TestId("ANN-59")
    fun aPendingStampSurvivesTheScreenBeingRebuilt() {
        openReader(AnnotationFixtures.NORMAL) { scenario, _ ->
            val journal = journal()
            val before = placeSignature()
            val pageBefore = AnnotationDump.requirePendingStamp().page

            scenario.recreate()
            awaitReaderReady()

            val after = AnnotationDump.awaitPendingStamp()
            assertEquals("the stamp comes back on the page it was placed on", pageBefore, after.page)
            assertEquals("the left edge survives the rebuild", before.left, after.rect.left, ONE_PDF_UNIT)
            assertEquals("the top edge survives the rebuild", before.top, after.rect.top, ONE_PDF_UNIT)
            assertEquals("the right edge survives the rebuild", before.right, after.rect.right, ONE_PDF_UNIT)
            assertEquals("the bottom edge survives the rebuild", before.bottom, after.rect.bottom, ONE_PDF_UNIT)

            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationJournalFile.assertStaysAbsent(journal)
        }
    }

    private fun placeSignature(): PdfRect {
        SignaturePad.openFromReaderActions()
        SignaturePad.awaitDialog()
        SignaturePad.drawOneStroke()
        SignaturePad.awaitPlaceEnabled(true)
        SignaturePad.pressPlace()
        return AnnotationDump.awaitPendingStamp().rect
    }

    private fun assertInsidePage(rect: PdfRect) {
        val size = AnnotationGeometry.pagePointSize(0)
        assertTrue("the stamp must stay inside the page, read $rect", rect.left >= -ONE_PDF_UNIT)
        assertTrue("the stamp must stay inside the page, read $rect", rect.bottom >= -ONE_PDF_UNIT)
        assertTrue("the stamp must stay inside the page, read $rect", rect.right <= size.x + ONE_PDF_UNIT)
        assertTrue("the stamp must stay inside the page, read $rect", rect.top <= size.y + ONE_PDF_UNIT)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val ONE_PDF_UNIT = 1f
        const val MOVE_FRACTION = 0.15f
        const val RESIZE_FRACTION = 0.1f
        const val ASPECT_TOLERANCE = 0.01f
        const val MINIMUM_WIDTH_FRACTION = 0.05f
    }
}
