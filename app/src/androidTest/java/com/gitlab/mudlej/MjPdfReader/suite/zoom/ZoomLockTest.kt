// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.min

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ZoomLockTest : ZoomTestCase() {

    @Test
    @TestId("ZOOM-07")
    fun theZoomLockButtonDisablesPinch() {
        openReader {
            assertZoom("the resting zoom before the lock", RESTING_ZOOM, zoom())
            val ratio = min(SPREAD_RATIO, Pinch.maxSpreadRatio())

            Pinch.byRatio(ratio)
            val unlocked = settled()
            assertTrue(
                "the control pinch must magnify while zoom is unlocked, otherwise the locked pinch below proves " +
                    "nothing about the lock and only that the gesture never became a scale, but the zoom reads " +
                    "${unlocked.zoom}",
                unlocked.zoom!! > RESTING_ZOOM + CONTROL_MARGIN,
            )

            ReaderCanvas.doubleTapCentre()
            assertZoom("the double tap must return the control zoom to the resting level", RESTING_ZOOM, settledFullQuality().zoom!!)

            enterFullscreen()
            revealFullscreenOverlay()
            lockZoom()

            Pinch.byRatio(ratio)
            val after = settled()

            assertEquals("the viewer must report zoom locked", true as Boolean?, after.zoomLocked)
            assertZoom("the same pinch under the zoom lock must change nothing", RESTING_ZOOM, after.zoom!!)
            assertTrue("the zoom lock button must report checked", isChecked(R.id.toggleZoomLockButton))
        }
    }

    @Test
    @TestId("ZOOM-08")
    fun theZoomLockButtonAlsoDisablesDoubleTapZoom() {
        openReader {
            enterFullscreen()
            revealFullscreenOverlay()
            lockZoom()

            ReaderCanvas.doubleTapCentre()
            val after = settled()

            assertZoom("a double tap under the zoom lock must change nothing", RESTING_ZOOM, after.zoom!!)
            assertEquals("the viewer must report zoom locked", true as Boolean?, after.zoomLocked)
        }
    }

    @Test
    @TestId("ZOOM-19")
    @TestDuration(DurationCategory.NORMAL)
    fun theHorizontalSwipeLockBlocksSidewaysPanningButNotVerticalPanning() {
        openReader {
            ReaderCanvas.doubleTapCentre()
            assertZoom("the double tap must magnify before the drag", MID_ZOOM, settledFullQuality().zoom!!)

            enterFullscreen()
            revealFullscreenOverlay()
            lockHorizontalSwipe()
            hideFullscreenOverlay()

            val before = settledFullQuality()
            val centre = ReaderCanvas.centre()
            Swipe.drag(
                PointF(centre.x + DIAGONAL_PX, centre.y + DIAGONAL_PX),
                PointF(centre.x - DIAGONAL_PX, centre.y - DIAGONAL_PX),
            )
            val after = settledFullQuality()

            assertEquals("the viewer must report the horizontal swipe lock", true as Boolean?, after.horizontalSwipeLocked)
            assertNear("the locked axis must not move", before.offsetX!!, after.offsetX!!, OFFSET_TOLERANCE_PX)
            assertScrollAxisTravel(before, after, -2f * DIAGONAL_PX)
            assertTrue("the horizontal swipe button must report checked", isChecked(R.id.toggleHorizontalSwipeButton))
        }
    }

    @Test
    @TestId("ZOOM-26")
    fun aHorizontalFlingIsIgnoredWhenTheHorizontalSwipeLockIsOn() {
        SettingsSeeder.seed(Preferences.horizontalScrollKey, true)

        openReader {
            enterFullscreen()
            revealFullscreenOverlay()
            lockHorizontalSwipe()
            hideFullscreenOverlay()

            val before = settledFullQuality()
            val start = ReaderCanvas.at(FLING_START_FRACTION, 0.5f)
            Swipe.fling(start, -FLING_STEP_PX, 0f)
            val after = settledFullQuality()

            assertEquals("the viewer must report the horizontal swipe lock", true as Boolean?, after.horizontalSwipeLocked)
            assertNear("a locked horizontal fling must not move the scroll axis", before.offsetX!!, after.offsetX!!, OFFSET_TOLERANCE_PX)
            assertEquals("a locked horizontal fling must not turn a page", before.page, after.page)
        }
    }

    private companion object {
        const val SPREAD_RATIO = 2.5f
        const val CONTROL_MARGIN = 0.5f
        const val DIAGONAL_PX = 200f
        const val OFFSET_TOLERANCE_PX = 2f
        const val FLING_START_FRACTION = 0.85f
        const val FLING_STEP_PX = 120f
    }
}
