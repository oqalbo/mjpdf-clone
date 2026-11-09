// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FlingTest : ZoomTestCase() {

    @Test
    @TestId("ZOOM-22")
    fun aFlingKeepsScrollingAfterTheFingerLiftsAndComesToRest() {
        openReader(ZoomFixtures.LARGE) {
            val before = state()

            Swipe.fling(ReaderCanvas.at(0.5f, FLING_START_FRACTION), 0f, -FLING_STEP_PX)
            val released = state()

            assertEquals("the fling must be running when the finger lifts", true as Boolean?, released.flinging)

            reader.awaitState("the fling comes to rest") { it.flinging == false }
            val rested = settled()

            assertTrue(
                "the viewport must coast at least $COAST_PX px after the finger lifted, moving from " +
                    "${released.offsetY} to ${rested.offsetY}",
                released.offsetY!! - rested.offsetY!! >= COAST_PX,
            )
            assertEquals("rendering must be idle once the fling has settled", RenderActivity.IDLE, rested.render)
            assertTrue(
                "the fling must carry the reader forward at least one page, from ${before.page} to ${rested.page}",
                rested.page!! >= before.page!! + 1,
            )
        }
    }

    @Test
    @TestId("ZOOM-23")
    fun touchingTheScreenDuringAFlingStopsItImmediately() {
        openReader(ZoomFixtures.LARGE) {
            Swipe.fling(ReaderCanvas.at(0.5f, FLING_START_FRACTION), 0f, -FLING_STEP_PX)
            reader.awaitState("the fling is running") { it.flinging == true }

            var atTouch: ReaderState? = null
            Swipe.press(ReaderCanvas.centre(), INTERRUPT_HOLD_MS) { atTouch = state() }
            val interrupted = atTouch!!

            Await.until("the fling stops within $STOP_WINDOW_MS ms of the touch", STOP_WINDOW_MS) {
                state().flinging == false
            }
            val stopped = settled()

            assertTrue(
                "the coast must stop rather than decelerate on, but the offset moved from ${interrupted.offsetY} " +
                    "to ${stopped.offsetY}",
                abs(stopped.offsetY!! - interrupted.offsetY!!) < STOPPED_DRIFT_PX,
            )
            assertEquals("the interrupted fling must not turn another page", interrupted.page, stopped.page)
        }
    }

    @Test
    @TestId("ZOOM-24")
    fun withPageFlingOnOneFlingTurnsExactlyOnePage() {
        seedPageFling()

        openReader {
            assertEquals("the reader must open on the first page", 0 as Int?, state().page)

            Swipe.fling(ReaderCanvas.at(0.5f, PAGE_FLING_START_FRACTION), 0f, -PAGE_FLING_STEP_PX)
            val after = settledFullQuality()

            assertEquals("one page fling must advance exactly one page", 1 as Int?, after.page)
            assertSnapped(after)
        }
    }

    @Test
    @TestId("ZOOM-25")
    fun repeatedFastFlingsInPageFlingModeStayOnOnePagePerFling() {
        seedPageFling()

        openReader(ZoomFixtures.LARGE) {
            val since = CrashGate.timestampNow()
            assertEquals("the reader must open on the first page", 0 as Int?, state().page)

            repeat(REPEATED_FLINGS) {
                Swipe.fling(ReaderCanvas.at(0.5f, PAGE_FLING_START_FRACTION), 0f, -PAGE_FLING_STEP_PX)
                SystemClock.sleep(BETWEEN_FLINGS_MS)
            }
            val after = settledFullQuality()

            assertEquals("each fling must advance exactly one page", REPEATED_FLINGS as Int?, after.page)
            assertSnapped(after)
            assertNoNullPointerSince(since)
        }
    }

    @Test
    @TestId("ZOOM-28")
    fun aDragStartedRightAfterAPageTurnIsNotSwallowed() {
        seedPageFling()

        openReader {
            Swipe.fling(ReaderCanvas.at(0.5f, PAGE_FLING_START_FRACTION), 0f, -PAGE_FLING_STEP_PX)
            SystemClock.sleep(INTERRUPT_GAP_MS)

            var duringDrag: ReaderState? = null
            Swipe.stroke(
                ReaderCanvas.at(0.5f, FOLLOW_UP_DRAG_FRACTION),
                0f,
                -FOLLOW_UP_STEP_PX,
                FOLLOW_UP_STEPS,
                FOLLOW_UP_STEP_MS,
            ) { frame -> if (frame == PROBE_FRAME) duringDrag = state() }
            val afterUp = state()
            val settled = settledFullQuality()

            val early = duringDrag!!
            assertTrue(
                "the drag must be applied rather than overwritten by the running snap animation, but the offset " +
                    "moved only from ${early.offsetY} to ${afterUp.offsetY}",
                abs(afterUp.offsetY!! - early.offsetY!!) >= FOLLOW_UP_MIN_TRAVEL_PX,
            )
            assertSnapped(settled)
            assertTrue(
                "the reader must have moved forward and stayed there, reading page ${settled.page}",
                settled.page == 1 || settled.page == 2,
            )
        }
    }

    @Test
    @TestId("ZOOM-29")
    fun aPinchStartedRightAfterAPageTurnIsNotOverwrittenBySnap() {
        seedPageFling()

        openReader {
            Swipe.fling(ReaderCanvas.at(0.5f, PAGE_FLING_START_FRACTION), 0f, -PAGE_FLING_STEP_PX)
            SystemClock.sleep(INTERRUPT_GAP_MS)

            Pinch.byRatio(PINCH_AFTER_TURN_RATIO)
            val after = settled()

            assertZoom(
                "the pinch that follows a page turn must survive the snap animation",
                PINCH_AFTER_TURN_RATIO,
                after.zoom!!,
                PINCH_AFTER_TURN_TOLERANCE,
            )
            assertTrue("the pinch must have magnified the page", after.zoom!! > RESTING_ZOOM)
            assertEquals("rendering must be idle after the pinch", RenderActivity.IDLE, after.render)
        }
    }

    private fun seedPageFling() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.pageFlingKey to true,
                Preferences.pageSnapKey to true,
            )
        )
    }

    private fun assertSnapped(state: ReaderState) {
        assertEquals("page snap must be resolved on", true as Boolean?, state.pageSnap)
        assertNear(
            "the settled offset must sit on the snap offset of the focused page",
            -state.snapOffset!!,
            state.offsetY!!,
            SNAP_TOLERANCE_PX,
        )
        assertEquals("the viewer must report the position as snapped", true as Boolean?, state.snapAligned)
    }

    private fun assertNoNullPointerSince(since: String) {
        val pid = android.os.Process.myPid().toString()
        val offenders = Shell.executeQuietly("logcat -d -v threadtime -T $since")
            .lineSequence()
            .filter { it.contains("NullPointerException") }
            .filter { line -> line.trim().split(Regex("\\s+")).getOrNull(THREADTIME_PID_FIELD) == pid }
            .toList()
        assertTrue(
            "overlapping page flings must not log a NullPointerException, but the log holds:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private companion object {
        const val FLING_START_FRACTION = 0.8f
        const val FLING_STEP_PX = 100f
        const val COAST_PX = 400f
        const val INTERRUPT_HOLD_MS = 50L
        const val STOP_WINDOW_MS = 300L
        const val STOPPED_DRIFT_PX = 30f
        const val PAGE_FLING_START_FRACTION = 0.85f
        const val PAGE_FLING_STEP_PX = 140f
        const val REPEATED_FLINGS = 6
        const val BETWEEN_FLINGS_MS = 30L
        const val INTERRUPT_GAP_MS = 40L
        const val FOLLOW_UP_DRAG_FRACTION = 0.7f
        const val FOLLOW_UP_STEP_PX = 20f
        const val FOLLOW_UP_STEPS = 20
        const val FOLLOW_UP_STEP_MS = 16L
        const val PROBE_FRAME = 3
        const val FOLLOW_UP_MIN_TRAVEL_PX = 200f
        const val PINCH_AFTER_TURN_RATIO = 2.5f
        const val PINCH_AFTER_TURN_TOLERANCE = 0.3f
        const val SNAP_TOLERANCE_PX = 2f
        const val THREADTIME_PID_FIELD = 2
    }
}
