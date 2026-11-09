// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.exp
import kotlin.math.pow

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class BrightnessAndScreenTest : ViewTestCase() {

    @Test
    @TestId("VIEW-35")
    fun theBrightnessSliderChangesTheWindowBrightnessAttribute() {
        seedLongOverlayDelay()
        openReader()
        openBrightnessControl()

        assertEquals(
            "a fresh reader window carries no brightness override",
            NO_OVERRIDE,
            ViewUi.screenBrightness(),
            BRIGHTNESS_TOLERANCE,
        )

        dragBrightnessTo(MIDDLE_OF_TRACK)

        val progress = ViewUi.seekBarProgress(R.id.brightnessSeekBar)
        val applied = ViewUi.screenBrightness()
        assertTrue(
            "the drag must install an override, but the window still reports $applied",
            applied != NO_OVERRIDE,
        )
        assertTrue("the override must lie in 0f..1f, but reads $applied", applied in 0f..1f)
        assertEquals(
            "the percentage label must follow the seek bar",
            "$progress%",
            ViewUi.textOf(R.id.brightnessPercentage),
        )
        assertEquals(
            "the window attribute must be the gamma conversion of the shown percentage",
            percentToLinear(progress).toDouble(),
            applied.toDouble(),
            GAMMA_TOLERANCE,
        )
    }

    @Test
    @TestId("VIEW-36")
    fun theBrightnessOverrideDoesNotLeakIntoTheNextReaderWindow() {
        seedLongOverlayDelay()
        val file = openReader()
        openBrightnessControl()
        dragBrightnessTo(LOW_ON_TRACK)
        val applied = ViewUi.screenBrightness()
        assertTrue("the drag must install an override before the reopen, but reads $applied", applied != NO_OVERRIDE)

        finishReader()
        launchAt(file)

        assertEquals(
            "a new reader window must start with no brightness override",
            NO_OVERRIDE,
            ViewUi.screenBrightness(),
            BRIGHTNESS_TOLERANCE,
        )
        val offenders = SettingsSeeder.preferences.all.keys.filter { it.contains("bright", ignoreCase = true) }
        assertTrue(
            "no SharedPreferences key may hold a brightness value, but the store holds $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    @TestId("VIEW-37")
    fun theDefaultLeavesTheWakeFlagOffTheReaderWindow() {
        val file = openReader()

        assertEquals(
            "the default must leave FLAG_KEEP_SCREEN_ON off the reader window",
            0,
            ViewUi.keepScreenOnFlag(),
        )

        backgroundAndReturn(file)

        assertEquals(
            "a second onResume must not add FLAG_KEEP_SCREEN_ON either",
            0,
            ViewUi.keepScreenOnFlag(),
        )
        assertTrue(
            "a user who never turned Keep Screen On on must have no such key, but the store holds " +
                "${SettingsSeeder.preferences.all[Preferences.screenOnKey]}",
            !SettingsSeeder.preferences.contains(Preferences.screenOnKey),
        )
    }

    private fun openBrightnessControl() {
        enterFullscreenWithOverlay()
        tapOverlayButton(R.id.brightnessButton)
        ViewUi.awaitVisibility(R.id.brightnessLayout, View.VISIBLE)
        Await.until("the brightness seek bar is laid out") { ViewUi.sizeOf(R.id.brightnessSeekBar).x > 0 }
    }

    private fun dragBrightnessTo(fraction: Float) {
        val bounds = ViewUi.boundsOnScreen(R.id.brightnessSeekBar)
        val y = bounds.exactCenterY()
        val from = PointF(bounds.left + bounds.width() * TRACK_START_FRACTION, y)
        val to = PointF(bounds.left + bounds.width() * fraction, y)
        RawInput.dragWithoutFling(from, to, RawInput.MAX_DRAG_STEP_PX)
        Await.until("the drag installs a window brightness override") {
            ViewUi.screenBrightness() != NO_OVERRIDE
        }
        Await.until("the percentage label settles on the seek bar progress") {
            ViewUi.textOf(R.id.brightnessPercentage) == "${ViewUi.seekBarProgress(R.id.brightnessSeekBar)}%"
        }
    }

    private fun percentToLinear(percent: Int): Float {
        val gamma = percent.coerceIn(0, 100) / 100f
        val normalized = if (gamma <= GAMMA_R) {
            (gamma / GAMMA_R).pow(2)
        } else {
            exp((gamma - GAMMA_C) / GAMMA_A) + GAMMA_B
        }
        return normalized.coerceIn(0f, GAMMA_SPAN) / GAMMA_SPAN
    }

    private companion object {
        const val NO_OVERRIDE = -1f
        const val BRIGHTNESS_TOLERANCE = 0.0001f
        const val GAMMA_TOLERANCE = 0.01
        const val TRACK_START_FRACTION = 0.1f
        const val MIDDLE_OF_TRACK = 0.5f
        const val LOW_ON_TRACK = 0.25f
        const val GAMMA_R = 0.5f
        const val GAMMA_A = 0.17883277f
        const val GAMMA_B = 0.28466892f
        const val GAMMA_C = 0.55991073f
        const val GAMMA_SPAN = 12f
    }
}
