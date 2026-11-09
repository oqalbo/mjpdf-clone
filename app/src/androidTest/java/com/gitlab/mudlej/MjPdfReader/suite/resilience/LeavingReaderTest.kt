// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class LeavingReaderTest : ResilienceTestCase() {

    @Test
    @TestId("RES-39")
    fun leavingTheReaderFlushesThePendingAutoScrollSpeed() {
        val file = fixture(ResilienceFixtures.LARGE)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val hash = reader.awaitFileHashResolved()
        Records.awaitRowExists(hash, Await.LONG_TIMEOUT_MS)

        startAutoScroll()
        val shownSpeed = changeSpeedAndLeaveImmediately()

        val record = Records.awaitRow(
            hash,
            "the debounced auto scroll speed is flushed when the reader stops",
            Await.LONG_TIMEOUT_MS,
        ) { it?.autoScrollSpeed != null }!!
        assertEquals(
            "the stored speed must be the one the control was showing when the reader was left",
            shownSpeed,
            record.autoScrollSpeed,
        )

        returnToReaderTask(file)
        assertEquals(
            "auto scroll must not still be running on return",
            false,
            state().autoScrolling,
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-40")
    @TestDuration(DurationCategory.SHORT)
    fun theScreenOffCycleKeepsTheReaderAndTheWakeFlag() {
        SettingsSeeder.seed(Preferences.screenOnKey, true)
        val file = fixture(ResilienceFixtures.NORMAL)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        jumpToPage(SCREEN_OFF_PAGE)
        zoomKeepingPage(RESTORED_ZOOM)
        settledFullQuality()
        val identityBefore = ReaderProbe.resumedReaderIdentity()

        TestEnvironment.uiDevice.sleep()
        Await.until("the screen reports off", Await.LONG_TIMEOUT_MS) { !TestEnvironment.uiDevice.isScreenOn }
        TestEnvironment.uiDevice.wakeUp()
        Await.until("the screen reports on", Await.LONG_TIMEOUT_MS) { TestEnvironment.uiDevice.isScreenOn }
        Shell.executeQuietly("wm dismiss-keyguard")
        TestEnvironment.uiDevice.waitForIdle()

        ReaderProbe.awaitResumedReader()
        val restored = settledFullQuality()
        assertEquals(
            "the screen cycle must leave the reader on the page it was on",
            SCREEN_OFF_PAGE as Int?,
            restored.page,
        )
        assertEquals(
            "the screen cycle must leave the zoom where it was",
            RESTORED_ZOOM.toDouble(),
            (restored.zoom ?: 0f).toDouble(),
            ZOOM_TOLERANCE.toDouble(),
        )
        assertTrue(
            "onResume must reapply the keep screen on flag after the screen comes back",
            ReaderProbe.keepScreenOnFlagSet(),
        )
        assertEquals(
            "no configuration change happened, so the same reader instance must be resumed",
            identityBefore,
            ReaderProbe.resumedReaderIdentity(),
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private fun startAutoScroll() {
        enterFullscreenOverlay()
        ReaderProbe.onReaderView(R.id.autoScrollButton) { it.performClick() }
        ReaderProbe.awaitVisibility(R.id.autoScrollLayout, View.VISIBLE)
        ReaderProbe.onReaderView(R.id.toggleAutoScrollButton) { it.performClick() }
        reader.awaitState("auto scroll starts running", Await.LONG_TIMEOUT_MS) { it.autoScrolling == true }
    }

    private fun changeSpeedAndLeaveImmediately(): Int {
        ReaderProbe.onReaderView(R.id.incScrollSpeedButton) { it.performClick() }
        val shown = speedFromControl()
        backgroundReader()
        return shown
    }

    private fun speedFromControl(): Int {
        val text = ReaderProbe.onReaderView(R.id.autoScrollSpeedText) {
            (it as android.widget.TextView).text.toString()
        }
        return text.filter { it.isDigit() }.toIntOrNull()
            ?: throw AssertionError("The auto scroll speed control reads \"$text\", which carries no number")
    }

    private fun enterFullscreenOverlay() {
        ReaderProbe.onReaderView(R.id.toolbarPrimaryActionOption) { it.performClick() }
        repeat(OVERLAY_TAP_ATTEMPTS) {
            if (ReaderProbe.visibility(R.id.fullScreenButtonsLayout) == View.VISIBLE) return
            ReaderProbe.tapCanvas()
            runCatching {
                ReaderProbe.awaitVisibility(R.id.fullScreenButtonsLayout, View.VISIBLE, Await.SHORT_TIMEOUT_MS)
            }
        }
        ReaderProbe.awaitVisibility(R.id.fullScreenButtonsLayout, View.VISIBLE)
    }

    private companion object {
        const val SCREEN_OFF_PAGE = 6
        const val RESTORED_ZOOM = 1.5f
        const val OVERLAY_TAP_ATTEMPTS = 3
    }
}
