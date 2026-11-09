// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.min

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PinchZoomTest : ZoomTestCase() {

    @Test
    @TestId("ZOOM-01")
    fun pinchOutIncreasesZoomByTheInjectedScaleFactor() {
        openReader {
            assertZoom("the resting zoom before the pinch", RESTING_ZOOM, zoom())

            Pinch.byRatio(SPREAD_RATIO)
            val settled = settled()

            assertZoom(
                "a spread of ratio $SPREAD_RATIO must multiply the zoom by that ratio",
                RESTING_ZOOM * SPREAD_RATIO,
                settled.zoom!!,
                SPAN_RATIO_TOLERANCE,
            )
            assertTrue("the pinch must have magnified the page", settled.zoom!! > RESTING_ZOOM)
            assertEquals("rendering must be idle once the pinch has ended", RenderActivity.IDLE, settled.render)
        }
    }

    @Test
    @TestId("ZOOM-02")
    fun pinchInBringsZoomBackTowardTheRestingLevel()  {
        openReader {
            val spreading = min(THREE_TIMES, CLOSE_HEADROOM / Pinch.maxCloseRatio())
            Pinch.byRatio(spreading)
            val magnified = settled().zoom!!
            assertTrue(
                "the opening spread must materially magnify the page, but read $magnified",
                magnified > RESTING_ZOOM + CONTROL_MARGIN,
            )

            Pinch.byRatio(1f / magnified)
            val restored = settled()

            assertZoom(
                "closing by the inverse of the spread must come back to the resting zoom",
                RESTING_ZOOM,
                restored.zoom!!,
                RETURN_TOLERANCE,
            )
            assertTrue("the closing pinch must have shrunk the page", restored.zoom!! < magnified)
        }
    }

    @Test
    @TestId("ZOOM-03")
    fun pinchCannotGoBelowTheMinimumZoom() {
        openReader {
            val closing = Pinch.maxCloseRatio()
            repeat(CLAMP_ATTEMPTS) {
                Pinch.byRatio(closing)
                val reading = settled().zoom!!
                assertTrue("zoom must never fall below the minimum of $MIN_ZOOM, but read $reading", reading >= MIN_ZOOM - ZOOM_TOLERANCE)
            }

            assertZoom("the minimum zoom clamp", MIN_ZOOM, zoom())
        }
    }

    @Test
    @TestId("ZOOM-04")
    fun pinchCannotExceedTheDefaultMaximumZoom() {
        openReader {
            TestEnvironment.uiDevice.setOrientationLeft()
            Await.until("the landscape pinch canvas is laid out") {
                val bounds = ReaderCanvas.rect()
                bounds.width() > bounds.height()
            }
            awaitReaderReady()
            val spreading = min(SPREAD_TO_CLAMP_RATIO, Pinch.maxSpreadRatio())
            assertTrue("the available gesture span must allow zooming in", spreading > 1f)
            val attempts = maxOf(
                CLAMP_ATTEMPTS,
                ceil(ln(DEFAULT_MAX_ZOOM / zoom()) / ln(spreading)).toInt() + 1,
            )
            repeat(attempts) {
                Pinch.byRatio(spreading)
                val reading = settled().zoom!!
                assertTrue(
                    "zoom must never pass the shipped maximum of $DEFAULT_MAX_ZOOM, but read $reading",
                    reading <= DEFAULT_MAX_ZOOM + ZOOM_TOLERANCE,
                )
            }

            assertZoom("the shipped maximum zoom clamp", DEFAULT_MAX_ZOOM, zoom())
        }
    }

    @Test
    @TestId("ZOOM-05")
    fun theMaximumZoomPreferenceClampsPinch() {
        SettingsSeeder.seed(Preferences.maxZoomKey, SEEDED_MAX_ZOOM)

        openReader {
            assertZoom("the loader must apply the seeded ceiling", SEEDED_MAX_ZOOM, state().maxZoom!!)

            val spreading = min(SPREAD_TO_CLAMP_RATIO, Pinch.maxSpreadRatio())
            repeat(CLAMP_ATTEMPTS) { Pinch.byRatio(spreading) }
            val reading = settled().zoom!!

            assertZoom("the seeded maximum zoom clamp", SEEDED_MAX_ZOOM, reading)
            assertTrue("zoom must stop at the preference, well below the shipped default", reading < DEFAULT_MAX_ZOOM)
        }
    }

    @Test
    @TestId("ZOOM-06")
    fun pinchKeepsTheFocalPointUnderTheFingers() {
        openReader {
            val focus = ReaderCanvas.at(FOCUS_FRACTION, FOCUS_FRACTION)
            val before = state()
            val documentX = ReaderCanvas.documentX(before, focus.x)
            val documentY = ReaderCanvas.documentY(before, focus.y)

            Pinch.byRatio(SPREAD_RATIO, focus)
            val after = settled()

            assertNear(
                "the document x under the focal point must not move",
                documentX,
                ReaderCanvas.documentX(after, focus.x),
                FOCUS_TOLERANCE_PX,
            )
            assertNear(
                "the document y under the focal point must not move",
                documentY,
                ReaderCanvas.documentY(after, focus.y),
                FOCUS_TOLERANCE_PX,
            )
            assertTrue("the pinch must have magnified the page", after.zoom!! > before.zoom!!)
        }
    }

    private companion object {
        const val SPREAD_RATIO = 2.0f
        const val THREE_TIMES = 3.0f
        const val SPREAD_TO_CLAMP_RATIO = 2.5f
        const val SEEDED_MAX_ZOOM = 2.0f
        const val CLAMP_ATTEMPTS = 5
        const val SPAN_RATIO_TOLERANCE = 0.15f
        const val RETURN_TOLERANCE = 0.2f
        const val CLOSE_HEADROOM = 0.9f
        const val CONTROL_MARGIN = 0.5f
        const val FOCUS_FRACTION = 0.25f
        const val FOCUS_TOLERANCE_PX = 8f
    }
}
