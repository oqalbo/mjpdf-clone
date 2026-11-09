// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ScrollHandleFullscreenTest : ViewTestCase() {

    @Test
    @TestId("VIEW-56")
    fun theScrollHandleStaysOutOfTheStatusBarAreaInFullscreen() {
        openReader(ViewFixtures.LARGE)

        revealScrollHandle()

        enterFullscreen()
        val statusBarInset = ViewUi.statusBarInsetHeight()
        assertTrue(
            "the pinned profile must report a status bar inset for this test to mean anything, but it reads " +
                "$statusBarInset",
            statusBarInset > 0,
        )
        revealScrollHandle()
        dragHandleToTheTop()
        val inFullscreen = ViewUi.scrollHandleY()

        exitFullscreenFromOverlay()
        revealScrollHandle()
        dragHandleToTheTop()
        val windowed = ViewUi.scrollHandleY()

        assertTrue(
            "in fullscreen the handle must stop at the status bar inset of $statusBarInset, but it reached " +
                "$inFullscreen",
            inFullscreen >= statusBarInset - HANDLE_TOLERANCE_PX,
        )
        assertTrue(
            "outside fullscreen the top limit drops back to zero, so the handle must reach higher than the " +
                "$inFullscreen it stopped at in fullscreen, but it reached $windowed",
            windowed < inFullscreen,
        )
    }

    private fun revealScrollHandle() {
        repeat(HANDLE_TAP_ATTEMPTS) {
            if (ViewUi.scrollHandleOrNull() != null && ViewUi.scrollHandleVisible()) return
            tapCanvas()
            val shown = runCatching {
                Await.until("the scroll handle is on screen", Await.SCROLL_HANDLE_TIMEOUT_MS) {
                    ViewUi.scrollHandleOrNull() != null && ViewUi.scrollHandleVisible()
                }
            }.isSuccess
            if (shown) return
        }
        throw AssertionError("The scroll handle never appeared after $HANDLE_TAP_ATTEMPTS taps on the page")
    }

    private fun dragHandleToTheTop() {
        val bounds = ViewUi.scrollHandleBoundsOnScreen()
        val from = PointF(bounds.exactCenterX(), bounds.exactCenterY())
        RawInput.dragWithoutFling(from, PointF(bounds.exactCenterX(), 0f), RawInput.MAX_DRAG_STEP_PX)
        reader.awaitMotionSettled()
        Await.untilTrueContinuously(
            condition = "the scroll handle settles at its top limit",
            stableForMs = HANDLE_SETTLE_MS,
            timeoutMs = HANDLE_SETTLE_MS + Await.SHORT_TIMEOUT_MS,
        ) { ViewUi.scrollHandleOrNull() != null }
    }

    private companion object {
        const val HANDLE_TAP_ATTEMPTS = 3
        const val HANDLE_TOLERANCE_PX = 1f
        const val HANDLE_SETTLE_MS = 400L
    }
}
