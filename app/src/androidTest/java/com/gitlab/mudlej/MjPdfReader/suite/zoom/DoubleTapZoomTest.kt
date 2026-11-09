// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class DoubleTapZoomTest : ZoomTestCase() {

    @Test
    @TestId("ZOOM-10")
    fun doubleTapCyclesBetweenTheRestingZoomAndTheMiddleZoom() {
        openReader {
            val readings = doubleTapCycle(2)

            assertZoom("the first double tap must reach the middle zoom", MID_ZOOM, readings[0])
            assertZoom("the second double tap must reset to the resting zoom", RESTING_ZOOM, readings[1])
        }
    }

    @Test
    @TestId("ZOOM-11")
    fun threeStepDoubleTapAddsAMaximumZoomStop() {
        SettingsSeeder.seed(Preferences.doubleTapThreeStepZoomKey, true)

        openReader {
            val readings = doubleTapCycle(3)

            assertZoom("the first stop is the middle zoom", MID_ZOOM, readings[0])
            assertZoom("the second stop is the maximum zoom", DEFAULT_MAX_ZOOM, readings[1])
            assertZoom("the third stop resets to the resting zoom", RESTING_ZOOM, readings[2])
        }
    }

    @Test
    @TestId("ZOOM-12")
    fun theDoubleTapCycleDoesNotStickWhenTheMaximumIsBelowTheMiddleZoom() {
        SettingsSeeder.seed(Preferences.maxZoomKey, LOW_MAX_ZOOM)

        openReader {
            val readings = doubleTapCycle(3)

            assertZoom("the first double tap is clamped to the ceiling", LOW_MAX_ZOOM, readings[0])
            assertZoom(
                "the second double tap must reset rather than re-target the clamped middle step",
                RESTING_ZOOM,
                readings[1],
            )
            assertZoom("the cycle repeats from the clamped middle step", LOW_MAX_ZOOM, readings[2])
            readings.forEach {
                assertTrue("no reading may pass the ceiling of $LOW_MAX_ZOOM, but read $it", it <= LOW_MAX_ZOOM + ZOOM_TOLERANCE)
            }
        }
    }

    @Test
    @TestId("ZOOM-13")
    fun threeStepDoubleTapNeverExceedsTheMaximumZoomPreference() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.doubleTapThreeStepZoomKey to true,
                Preferences.maxZoomKey to THREE_STEP_MAX_ZOOM,
            )
        )

        openReader {
            val readings = doubleTapCycle(4)

            assertZoom("the middle step", MID_ZOOM, readings[0])
            assertZoom("the maximum step is the preference", THREE_STEP_MAX_ZOOM, readings[1])
            assertZoom("the reset step", RESTING_ZOOM, readings[2])
            assertZoom("the cycle restarts at the middle step", MID_ZOOM, readings[3])
            readings.forEach {
                assertTrue(
                    "no reading may pass the ceiling of $THREE_STEP_MAX_ZOOM, but read $it",
                    it <= THREE_STEP_MAX_ZOOM + ZOOM_TOLERANCE,
                )
            }
        }
    }

    @Test
    @TestId("ZOOM-14")
    fun doubleTapBelowTheRestingZoomReturnsToTheRestingZoomFirst() {
        openReader {
            Pinch.byRatio(Pinch.maxCloseRatio())
            assertZoom("the pinch must reach the minimum zoom first", MIN_ZOOM, settled().zoom!!)

            ReaderCanvas.doubleTapCentre()
            val after = settledFullQuality()

            assertZoom(
                "one double tap below the resting zoom returns to it rather than jumping to the middle zoom",
                RESTING_ZOOM,
                after.zoom!!,
            )
        }
    }

    @Test
    @TestId("ZOOM-15")
    fun doubleTapZoomsAroundTheTappedPoint() {
        openReader {
            val point = ReaderCanvas.at(TAP_FRACTION, TAP_FRACTION)
            val before = state()
            val documentX = ReaderCanvas.documentX(before, point.x)
            val documentY = ReaderCanvas.documentY(before, point.y)

            ReaderCanvas.doubleTap(point)
            val after = settledFullQuality()

            assertZoom("the double tap must reach the middle zoom", MID_ZOOM, after.zoom!!)
            assertAnchored("x", documentX, ReaderCanvas.documentX(after, point.x), after.offsetX!!, ViewportBounds.offsetXLowerBound(after))
            assertAnchored("y", documentY, ReaderCanvas.documentY(after, point.y), after.offsetY!!, ViewportBounds.offsetYLowerBound(after))
        }
    }

    @Test
    @TestId("ZOOM-41")
    fun doubleTapInHorizontalModeResetsToAFitHeightZoom() {
        SettingsSeeder.seed(Preferences.horizontalScrollKey, true)

        openReader(FIT_HEIGHT_FIXTURE) {
            val before = state()
            assertZoom("the fixture must rest at zoom 1.0", RESTING_ZOOM, before.zoom!!)
            assertEquals("the layout must resolve to horizontal scrolling", false as Boolean?, before.swipeVertical)
            val unscaledPageHeight = before.pageHeight!! / before.zoom!!
            val fitHeightZoom = before.viewHeight!! / unscaledPageHeight
            val expected = min(max(fitHeightZoom, MIN_ZOOM), RESTING_ZOOM)
            assertTrue(
                "this test only separates the horizontal branch from the flat reset when the page is taller than " +
                    "the viewport, which needs a fit height zoom below 1.0, but $FIT_HEIGHT_FIXTURE computes " +
                    "$fitHeightZoom from a page height of $unscaledPageHeight against a viewport of ${before.viewHeight}",
                fitHeightZoom < RESTING_ZOOM - BRANCH_MARGIN,
            )

            ReaderCanvas.doubleTapCentre()
            settledFullQuality()
            ReaderCanvas.doubleTapCentre()
            val after = settledFullQuality()

            assertZoom(
                "the reset step of the cycle must land on the fit height zoom, computed as $fitHeightZoom",
                expected,
                after.zoom!!,
                FIT_HEIGHT_TOLERANCE,
            )
            assertTrue(
                "the reset step must take the horizontal fit height branch rather than the flat reset to 1.0, " +
                    "but the zoom reads ${after.zoom}",
                after.zoom!! < RESTING_ZOOM - BRANCH_MARGIN,
            )
        }
    }

    private fun doubleTapCycle(taps: Int): List<Float> = (1..taps).map {
        ReaderCanvas.doubleTapCentre()
        settledFullQuality().zoom!!
    }

    private fun assertAnchored(axis: String, before: Float, after: Float, offset: Float, lowerBound: Float) {
        if (abs(after - before) <= ANCHOR_TOLERANCE_PX) return
        val onABound = abs(offset) <= OFFSET_TOLERANCE_PX || abs(offset - lowerBound) <= OFFSET_TOLERANCE_PX
        assertTrue(
            "the document $axis under the tap moved from $before to $after, which is only allowed when the " +
                "offset was clamped to a document edge, but the offset reads $offset against a lower bound of $lowerBound",
            onABound,
        )
    }

    private companion object {
        const val FIT_HEIGHT_FIXTURE = ZoomFixtures.TALL_PAGE
        const val BRANCH_MARGIN = 0.05f
        const val LOW_MAX_ZOOM = 1.5f
        const val THREE_STEP_MAX_ZOOM = 3.0f
        const val TAP_FRACTION = 0.3f
        const val ANCHOR_TOLERANCE_PX = 8f
        const val OFFSET_TOLERANCE_PX = 2f
        const val FIT_HEIGHT_TOLERANCE = 0.02f
    }
}
