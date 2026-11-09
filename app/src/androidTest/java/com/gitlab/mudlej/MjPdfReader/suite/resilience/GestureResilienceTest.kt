// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class GestureResilienceTest : ResilienceTestCase() {

    @Test
    @TestId("RES-45")
    fun aGestureStreamWithNoDownEventDoesNotCrash() {
        val file = fixture(ResilienceFixtures.NORMAL)
        val since = CrashGate.timestampNow()
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        jumpToPage(MALFORMED_STREAM_PAGE)
        val before = settledFullQuality()
        val offsetBefore = before.positionOffset
            ?: throw AssertionError("the state hook must report a position offset before the malformed streams")

        repeat(MALFORMED_STREAMS) { index ->
            val x = ReaderProbe.canvasPointOnScreen(MALFORMED_X_FRACTIONS[index % MALFORMED_X_FRACTIONS.size], 0.5f)
            injectMoveThenUp(x)
        }

        CrashGate.assertNoFatalSince(since)

        reader.swipeForward()
        val after = settledFullQuality()

        assertTrue(
            "the gesture pipeline must still accept input after the malformed streams, but the document " +
                "offset stayed at $offsetBefore",
            (after.positionOffset ?: 0f) > offsetBefore,
        )
        assertTrue(
            "the page must not have gone backwards, it read ${after.page} against $MALFORMED_STREAM_PAGE",
            (after.page ?: 0) >= MALFORMED_STREAM_PAGE,
        )
        assertEquals(
            "the malformed streams must leave no animation flag stuck on",
            false,
            after.animating,
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-46")
    fun aCancelledDragLeavesNoStrandedScrollHandle() {
        SettingsSeeder.seedReadingMode(singlePage = true, horizontalScroll = false, dualPage = false)
        val file = fixture(ResilienceFixtures.NORMAL)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        jumpToPage(CANCELLED_DRAG_PAGE)
        val before = settledFullQuality()
        assertEquals(
            "single page mode must resolve page snap on, otherwise the settle this test asserts is not the " +
                "behaviour under test",
            true,
            before.pageSnap,
        )
        val offsetBefore = before.positionOffset
            ?: throw AssertionError("the state hook must report a position offset before the drag")

        val start = ReaderProbe.canvasPointOnScreen(0.5f, DRAG_START_Y_FRACTION)
        val end = ReaderProbe.canvasPointOnScreen(0.5f, DRAG_END_Y_FRACTION)
        RawInput.dragAlongLine(start, end, endWithCancel = true)

        val settled = reader.awaitState("the interrupted drag settles", Await.LONG_TIMEOUT_MS) {
            it.animating == false && it.flinging == false
        }
        assertEquals(
            "a drag shorter than half a page must snap back to the page it started on",
            CANCELLED_DRAG_PAGE as Int?,
            settled.page,
        )
        assertEquals(
            "the settled position must be a snap target, which is what distinguishes it from an intermediate offset",
            true,
            settled.snapAligned,
        )
        assertTrue(
            "the snap delta must be within a pixel of zero, but it read ${settled.snapDelta}",
            abs(settled.snapDelta ?: Float.MAX_VALUE) <= SNAP_TOLERANCE_PX,
        )
        assertEquals(
            "the document offset must return to where the drag started",
            offsetBefore.toDouble(),
            (settled.positionOffset ?: 0f).toDouble(),
            OFFSET_TOLERANCE.toDouble(),
        )
        Await.until(
            "the scroll handle hides itself again after the cancelled drag",
            Await.SCROLL_HANDLE_TIMEOUT_MS,
        ) { !scrollHandleVisible() }
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private fun scrollHandleVisible(): Boolean = TestEnvironment.onMainThread {
        val reader = ReaderProbe.liveReaderOrNull() ?: return@onMainThread false
        val pdfView = reader.findViewById<android.view.ViewGroup>(com.gitlab.mudlej.MjPdfReader.R.id.pdfView)
            ?: return@onMainThread false
        (0 until pdfView.childCount)
            .map(pdfView::getChildAt)
            .any {
                it.contentDescription?.toString() == DefaultScrollHandle.DEBUG_HANDLE_DESCRIPTION &&
                    it.visibility == android.view.View.VISIBLE
            }
    }

    private fun injectMoveThenUp(point: PointF) {
        val eventTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_MOVE, eventTime, point)
        inject(MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), point)
    }

    private fun inject(action: Int, eventTime: Long, point: PointF) {
        val event = MotionEvent.obtain(eventTime, eventTime, action, point.x, point.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            TestEnvironment.instrumentation.uiAutomation.injectInputEvent(event, true)
        } finally {
            event.recycle()
        }
    }

    private companion object {
        const val MALFORMED_STREAM_PAGE = 4
        const val CANCELLED_DRAG_PAGE = 5
        const val MALFORMED_STREAMS = 5
        val MALFORMED_X_FRACTIONS = listOf(0.3f, 0.5f, 0.7f, 0.4f, 0.6f)
        const val DRAG_START_Y_FRACTION = 0.7f
        const val DRAG_END_Y_FRACTION = 0.37f
        const val SNAP_TOLERANCE_PX = 1f
        const val OFFSET_TOLERANCE = 0.001f
    }
}
