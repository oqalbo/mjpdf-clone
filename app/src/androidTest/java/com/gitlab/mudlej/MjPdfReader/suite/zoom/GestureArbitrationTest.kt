// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class GestureArbitrationTest : ZoomTestCase() {

    @Test
    @TestId("ZOOM-27")
    @TestDuration(DurationCategory.NORMAL)
    fun aCancelledDragStillSnapsThePageAndHidesTheScrollHandle() {
        SettingsSeeder.seed(Preferences.pageSnapKey, true)

        openReader {
            ReaderCanvas.placeViewportForSetup(CANCEL_DRAG_MIDDLE_PAGE)
            settledFullQuality()
            ReaderCanvas.tapCentre()
            Await.until("the scroll handle becomes visible after a tap") { scrollHandleDisplayed() }

            val gesture = Swipe.stroke(
                ReaderCanvas.at(0.5f, CANCEL_DRAG_FRACTION),
                0f,
                -CANCEL_STEP_PX,
                CANCEL_STEPS,
                CANCEL_STEP_MS,
                GestureEnding.LEAVE_DOWN,
            )
            val mid = state()
            assertFalse(
                "the drag must leave the viewport between two snap boundaries, but snapDelta reads ${mid.snapDelta}",
                mid.snapAligned == true,
            )

            gesture.cancel()
            val after = settledFullQuality()

            assertNear(
                "a cancelled drag must still snap to the focused page",
                -after.snapOffset!!,
                after.offsetY!!,
                SNAP_TOLERANCE_PX,
            )
            assertEquals("the viewer must report the position as snapped", true as Boolean?, after.snapAligned)
            assertEquals(
                "a drag shorter than half a page must settle back on the page it started from",
                CANCEL_DRAG_MIDDLE_PAGE as Int?,
                after.page,
            )
            Await.until("the scroll handle hides after its own delay", HANDLE_HIDE_TIMEOUT_MS) { !scrollHandleDisplayed() }
            assertEquals("rendering must be idle once the cancel has settled", RenderActivity.IDLE, state().render)
        }
    }

    @Test
    @TestId("ZOOM-30")
    fun aSingleTapTogglesTheControlsAndLeavesZoomAlone() {
        openReader {
            enterFullscreen()
            assertFalse("fullscreen entry must leave the overlay column hidden", isDisplayed(R.id.fullScreenButtonsLayout))
            val before = settledFullQuality()

            RawInput.pressAndHold(ReaderCanvas.centre(), SINGLE_TAP_HOLD_MS)
            awaitDisplayed(R.id.fullScreenButtonsLayout)
            val after = state()

            assertTrue("the scroll handle must be revealed with the overlay", scrollHandleDisplayed())
            assertZoom("a single tap must not change the zoom", before.zoom!!, after.zoom!!)
            assertNear("a single tap must not move the viewport", before.offsetX!!, after.offsetX!!, TAP_TOLERANCE_PX)
            assertNear("a single tap must not move the viewport", before.offsetY!!, after.offsetY!!, TAP_TOLERANCE_PX)
        }
    }

    @Test
    @TestId("ZOOM-31")
    fun aTouchThatTravelsPastTheTouchSlopIsADragNotATap() {
        openReader {
            enterFullscreen()
            val before = settledFullQuality()
            val travel = SLOP_DRAG_STEPS * SLOP_STEP_PX
            assertTrue(
                "the injected travel of $travel px must be far above the touch slop of ${TouchLimits.touchSlop} px",
                travel > TouchLimits.touchSlop * 3,
            )

            Swipe.drag(
                ReaderCanvas.centre(),
                ReaderCanvas.centre().also { it.offset(0f, -travel) },
                SLOP_DRAG_STEPS,
                SLOP_STEP_MS,
            )
            val after = settledFullQuality()

            assertFalse("a drag must not reveal the reading controls", isDisplayed(R.id.fullScreenButtonsLayout))
            assertScrollAxisTravel(before, after, -travel)
            assertZoom("a drag must not change the zoom", before.zoom!!, after.zoom!!)
        }
    }

    @Test
    @TestId("ZOOM-32")
    fun aLongPressStartsATextSelectionInsteadOfPanning() {
        openReader {
            val before = settledFullQuality()
            val onText = ReaderCanvas.pointInPage(before, TEXT_FRACTION_X, TEXT_FRACTION_Y)

            ReaderCanvas.longPress(onText)
            awaitDisplayed(R.id.textSelectionActionCard)
            val after = state()

            assertTrue("a long press on body text must start a selection", after.hasSelection)
            assertNear("a long press must not pan the page", before.offsetX!!, after.offsetX!!, TAP_TOLERANCE_PX)
            assertNear("a long press must not pan the page", before.offsetY!!, after.offsetY!!, TAP_TOLERANCE_PX)
            assertZoom("a long press must not change the zoom", before.zoom!!, after.zoom!!)
        }
    }

    @Test
    @TestId("ZOOM-33")
    fun edgeTapPageTurningIsSuppressedWhileZoomedIn() {
        SettingsSeeder.seed(Preferences.singlePageModeKey, true)

        openReader {
            ReaderCanvas.tap(ReaderCanvas.at(EDGE_ZONE_FRACTION, 0.5f))
            Await.until("the right tap zone turns the page at the resting zoom") { state().page == 1 }
            settledFullQuality()

            ReaderCanvas.doubleTapCentre()
            val magnified = settledFullQuality()
            assertZoom("the double tap must magnify the page", MID_ZOOM, magnified.zoom!!)

            ReaderCanvas.tap(ReaderCanvas.at(EDGE_ZONE_FRACTION, 0.5f))
            Await.assertThroughout(
                condition = "the edge tap stays suppressed while zoomed in",
                windowMs = SUPPRESSED_TAP_WINDOW_MS,
            ) {
                val now = state()
                now.page == 1 && zoomNear(MID_ZOOM, now.zoom!!)
            }
        }
    }

    @Test
    @TestId("ZOOM-40")
    fun pinchStillWorksWhileATextSelectionIsActive() {
        openReader {
            val opened = settledFullQuality()
            ReaderCanvas.longPress(ReaderCanvas.pointInPage(opened, TEXT_FRACTION_X, TEXT_FRACTION_Y))
            awaitDisplayed(R.id.textSelectionActionCard)
            val before = reader.awaitSelection()
            val selection = before.selectionStart to before.selectionEnd

            Pinch.byRatio(SELECTION_PINCH_RATIO, awayFromSelection(before))
            val after = settledFullQuality()

            assertTrue("the pinch must magnify even with a selection active", after.zoom!! > before.zoom!!)
            assertDisplayed(R.id.textSelectionActionCard)
            assertTrue("the selection must survive the pinch", after.hasSelection)
            assertEquals(
                "the pinch must neither clear nor extend the selection",
                selection,
                after.selectionStart to after.selectionEnd,
            )
            assertTrue("the start handle must still be placed", after.selectionStartHandle != null)
            assertTrue("the end handle must still be placed", after.selectionEndHandle != null)
        }
    }

    @Test
    @TestId("ZOOM-42")
    fun aLongPressOnAScannedPageDoesNotStartASelectionOrAPan() {
        openReader(ZoomFixtures.SCANNED) {
            val before = settledFullQuality()

            ReaderCanvas.longPress(ReaderCanvas.centre())
            val after = settledFullQuality()

            assertNotDisplayed(R.id.textSelectionActionCard)
            assertFalse("an image only page must not produce a selection", after.hasSelection)
            assertNear("a long press must not pan the page", before.offsetX!!, after.offsetX!!, TAP_TOLERANCE_PX)
            assertNear("a long press must not pan the page", before.offsetY!!, after.offsetY!!, TAP_TOLERANCE_PX)
            assertZoom("a long press must not change the zoom", before.zoom!!, after.zoom!!)
        }
    }

    private fun awayFromSelection(state: ReaderState): PointF {
        val bounds = ReaderCanvas.rect()
        val handles = listOf(
            state.selectionStartHandle ?: throw AssertionError("The active selection has no start handle"),
            state.selectionEndHandle ?: throw AssertionError("The active selection has no end handle"),
        )
        val candidates = listOf(0.75f, 0.65f, 0.85f, 0.5f)
        val focus = candidates
            .filter { fraction ->
                val y = bounds.height() * fraction
                handles.all { abs(it.y - y) >= SELECTION_CLEARANCE_PX }
            }
            .map { ReaderCanvas.at(0.5f, it) }
            .maxByOrNull(Pinch::maxSpreadRatio)
            ?: throw AssertionError("No pinch focus is at least $SELECTION_CLEARANCE_PX px from both selection handles")
        val reachableRatio = Pinch.maxSpreadRatio(focus)
        if (reachableRatio < SELECTION_PINCH_RATIO) {
            throw AssertionError(
                "A handle-safe pinch focus only allows ratio $reachableRatio, below the required $SELECTION_PINCH_RATIO"
            )
        }
        return focus
    }

    private companion object {
        const val CANCEL_DRAG_FRACTION = 0.8f
        const val CANCEL_DRAG_MIDDLE_PAGE = 9
        const val SUPPRESSED_TAP_WINDOW_MS = 800L
        const val CANCEL_STEP_PX = 30f
        const val CANCEL_STEPS = 12
        const val CANCEL_STEP_MS = 20L
        const val SNAP_TOLERANCE_PX = 2f
        const val HANDLE_HIDE_TIMEOUT_MS = 8_000L
        const val TAP_TOLERANCE_PX = 1f
        const val SINGLE_TAP_HOLD_MS = 60L
        const val SLOP_DRAG_STEPS = 15
        const val SLOP_STEP_PX = 20f
        const val SLOP_STEP_MS = 20L
        const val TEXT_FRACTION_X = 0.35f
        const val TEXT_FRACTION_Y = 0.2f
        const val EDGE_ZONE_FRACTION = 0.85f
        const val SELECTION_PINCH_RATIO = 2.0f
        const val SELECTION_CLEARANCE_PX = 200f
    }
}
