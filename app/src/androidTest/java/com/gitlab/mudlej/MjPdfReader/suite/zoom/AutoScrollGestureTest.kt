// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.Stage
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class AutoScrollGestureTest : ZoomTestCase() {

    @Test
    @TestId("ZOOM-34")
    fun aTwoFingerGestureDuringAutoScrollPausesItUntilTheLastFingerLifts() {
        openReader(ZoomFixtures.LARGE) {
            startAutoScroll()
            awaitAutoScrollProgress()

            val gesture = Pinch.withoutLift(SPREAD_RATIO)
            gesture.hold(HOLD_MS).liftSecond()
            val paused = state()

            Await.assertThroughout(
                condition = "auto scroll stays paused while one pointer is still down",
                windowMs = PAUSE_WINDOW_MS,
            ) { abs(state().offsetY!! - paused.offsetY!!) <= PAUSE_TOLERANCE_PX }

            gesture.up()

            reader.awaitState("auto scroll resumes once the last pointer lifts", RESUME_TIMEOUT_MS) {
                it.offsetY!! < paused.offsetY!! - PAUSE_TOLERANCE_PX
            }
            val resumed = state()

            assertTrue("the pinch itself must still have applied", resumed.zoom!! > RESTING_ZOOM)
            assertEquals("the reader must still report auto scrolling", true as Boolean?, resumed.autoScrolling)
        }
    }

    private fun startAutoScroll() {
        enterFullscreen()
        revealFullscreenOverlay()
        onView(withId(R.id.autoScrollButton)).perform(ViewActions.click())
        awaitDisplayed(R.id.autoScrollLayout)
        onView(withId(R.id.toggleAutoScrollButton)).perform(ViewActions.click())
        Await.until("the reader reports auto scrolling") { state().autoScrolling == true }
    }

    private fun awaitAutoScrollProgress(): ReaderState {
        val start = state().offsetY!!
        return reader.awaitState("auto scroll has moved the viewport", Await.LONG_TIMEOUT_MS) {
            it.offsetY!! <= start - PROGRESS_PX
        }
    }

    private companion object {
        const val SPREAD_RATIO = 2.0f
        const val HOLD_MS = 400L
        const val PAUSE_WINDOW_MS = 300L
        const val PAUSE_TOLERANCE_PX = 2f
        const val PROGRESS_PX = 100f
        const val RESUME_TIMEOUT_MS = 3_000L
    }
}
