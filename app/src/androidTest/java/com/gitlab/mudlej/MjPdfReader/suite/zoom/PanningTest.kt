// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PanningTest : ZoomTestCase() {

    @Test
    @TestId("ZOOM-16")
    fun panningWhileZoomedMovesTheViewport() {
        openReader {
            ReaderCanvas.doubleTapCentre()
            val before = settledFullQuality()
            assertZoom("the double tap must magnify before the drag", MID_ZOOM, before.zoom!!)

            val centre = ReaderCanvas.centre()
            Swipe.drag(PointF(centre.x + DRAG_FROM_PX, centre.y), PointF(centre.x - DRAG_TO_PX, centre.y))
            val after = settledFullQuality()

            assertCrossAxisTravel(before, after, -(DRAG_FROM_PX + DRAG_TO_PX))
            assertNear("a horizontal drag must not move the scroll axis", before.offsetY!!, after.offsetY!!, CROSS_DRIFT_PX)
            assertZoom("a drag must not change the zoom", MID_ZOOM, after.zoom!!)
        }
    }

    @Test
    @TestId("ZOOM-17")
    @TestDuration(DurationCategory.NORMAL)
    fun panningStopsAtThePageEdges() {
        openReader {
            ReaderCanvas.withQueuedInput { ReaderCanvas.doubleTapCentre() }
            assertZoom("the double tap must magnify before the drag", MID_ZOOM, settledFullQuality().zoom!!)

            val near = ReaderCanvas.at(EDGE_NEAR_FRACTION, 0.5f)
            val far = ReaderCanvas.at(EDGE_FAR_FRACTION, 0.5f)

            Swipe.drag(near, far)
            settledFullQuality()
            Swipe.drag(near, far)
            val afterTwo = settledFullQuality()
            Swipe.drag(near, far)
            val atRightEdge = settledFullQuality()

            assertNear(
                "dragging past the right edge must clamp to the lower offset bound",
                ViewportBounds.crossAxisLowerBound(atRightEdge),
                atRightEdge.offsetX!!,
                EDGE_TOLERANCE_PX,
            )
            assertNear(
                "a further drag past the clamped edge must change nothing",
                afterTwo.offsetX!!,
                atRightEdge.offsetX!!,
                EDGE_TOLERANCE_PX,
            )

            Swipe.drag(far, near)
            settledFullQuality()
            Swipe.drag(far, near)
            val backTwo = settledFullQuality()
            Swipe.drag(far, near)
            val atLeftEdge = settledFullQuality()

            assertNear("dragging past the left edge must clamp to zero", 0f, atLeftEdge.offsetX!!, EDGE_TOLERANCE_PX)
            assertNear(
                "a further drag past the clamped edge must change nothing",
                backTwo.offsetX!!,
                atLeftEdge.offsetX!!,
                EDGE_TOLERANCE_PX,
            )
            assertEquals("horizontal panning must not turn a page", 0 as Int?, atLeftEdge.page)
        }
    }

    @Test
    @TestId("ZOOM-18")
    fun aVerticalDragAtTheRestingZoomScrollsToTheNextPage() {
        openReader {
            val before = state()
            assertEquals("the reader must open on the first page", 0 as Int?, before.page)

            val after = dragForwardUntilPage(1, PAGE_DRAG_ATTEMPTS)

            assertEquals("a vertical drag must reach the second page", 1 as Int?, after.page)
            assertTrue(
                "the scroll offset must have moved further into the document, from ${before.offsetY} to ${after.offsetY}",
                after.offsetY!! < before.offsetY!!,
            )
            assertZoom("a drag must not change the zoom", RESTING_ZOOM, after.zoom!!)
        }
    }

    @Test
    @TestId("ZOOM-20")
    fun freeScrollModeLocksAMostlyVerticalDragToTheVerticalAxis() {
        SettingsSeeder.seed(Preferences.browserScrollModeKey, true)

        openReader {
            ReaderCanvas.doubleTapCentre()
            val before = settledFullQuality()
            assertZoom("the double tap must magnify so sideways movement is possible", MID_ZOOM, before.zoom!!)
            assertEquals("free scroll mode must be resolved on", true as Boolean?, before.freeScroll)

            val centre = ReaderCanvas.centre()
            Swipe.drag(
                PointF(centre.x + AXIS_LOCK_DRIFT_PX, centre.y + AXIS_LOCK_TRAVEL_PX),
                PointF(centre.x, centre.y - AXIS_LOCK_TRAVEL_PX),
                AXIS_LOCK_STEPS,
            )
            val after = settledFullQuality()

            assertNear("the axis lock must hold the horizontal offset", before.offsetX!!, after.offsetX!!, EDGE_TOLERANCE_PX)
            assertScrollAxisTravel(before, after, -2f * AXIS_LOCK_TRAVEL_PX, AXIS_LOCK_TOLERANCE_PX)
        }
    }

    @Test
    @TestId("ZOOM-21")
    fun freeScrollModeReleasesTheAxisLockForADeliberateSidewaysDrag() {
        SettingsSeeder.seed(Preferences.browserScrollModeKey, true)

        openReader {
            ReaderCanvas.doubleTapCentre()
            val before = settledFullQuality()
            assertZoom("the double tap must magnify so sideways movement is possible", MID_ZOOM, before.zoom!!)

            val centre = ReaderCanvas.centre()
            Swipe.drag(
                PointF(centre.x + AXIS_LOCK_TRAVEL_PX, centre.y),
                PointF(centre.x - AXIS_LOCK_TRAVEL_PX, centre.y),
                AXIS_LOCK_STEPS,
            )
            val after = settledFullQuality()

            assertCrossAxisTravel(before, after, -2f * AXIS_LOCK_TRAVEL_PX, AXIS_LOCK_TOLERANCE_PX)
            assertNear(
                "a level sideways drag must leave the scroll axis roughly alone",
                before.offsetY!!,
                after.offsetY!!,
                SIDEWAYS_DRIFT_PX,
            )
        }
    }

    @Test
    @TestId("ZOOM-44")
    fun zoomingOutBelowTheRestingLevelCentresAShortDocument() {
        openReader(ZoomFixtures.LANDSCAPE) {
            Pinch.byRatio(Pinch.maxCloseRatio())
            assertZoom("the pinch must reach the minimum zoom", MIN_ZOOM, settledFullQuality().zoom!!)

            val centre = ReaderCanvas.centre()
            Swipe.drag(PointF(centre.x, centre.y + PUSH_PX), PointF(centre.x, centre.y - PUSH_PX))
            val after = settledFullQuality()

            assertTrue(
                "the whole document must be shorter than the viewport for this test to mean anything, " +
                    "reading a document length of ${after.documentLength} against a viewport of ${after.viewHeight}",
                ViewportBounds.scrollAxisCentred(after),
            )
            assertNear(
                "a document shorter than the viewport must be centred on the scroll axis",
                ViewportBounds.scrollAxisLowerBound(after) / 2f,
                after.offsetY!!,
                EDGE_TOLERANCE_PX,
            )
            assertNear(
                "a page narrower than the viewport must be centred on the cross axis",
                ViewportBounds.crossAxisLowerBound(after) / 2f,
                after.offsetX!!,
                EDGE_TOLERANCE_PX,
            )
            assertZoom("the zoom must stay at the minimum", MIN_ZOOM, after.zoom!!)
        }
    }

    private fun assertCrossAxisTravel(
        before: ReaderState,
        after: ReaderState,
        wanted: Float,
        tolerance: Float = DRAG_TOLERANCE_PX,
    ) {
        val expected = ViewportBounds.expectedCrossAxisOffset(after, ViewportBounds.crossAxisOffset(before) + wanted)
        assertTrue(
            "the drag must move the cross axis to about $expected, but it reads ${ViewportBounds.crossAxisOffset(after)}",
            abs(ViewportBounds.crossAxisOffset(after) - expected) <= tolerance,
        )
    }

    private companion object {
        const val DRAG_FROM_PX = 250f
        const val DRAG_TO_PX = 50f
        const val CROSS_DRIFT_PX = 20f
        const val SIDEWAYS_DRIFT_PX = 40f
        const val EDGE_TOLERANCE_PX = 2f
        const val EDGE_NEAR_FRACTION = 0.9f
        const val EDGE_FAR_FRACTION = 0.1f
        const val PAGE_DRAG_ATTEMPTS = 8
        const val AXIS_LOCK_TRAVEL_PX = 300f
        const val AXIS_LOCK_DRIFT_PX = 40f
        const val AXIS_LOCK_STEPS = 100
        const val AXIS_LOCK_TOLERANCE_PX = 60f
        const val PUSH_PX = 400f
    }
}
