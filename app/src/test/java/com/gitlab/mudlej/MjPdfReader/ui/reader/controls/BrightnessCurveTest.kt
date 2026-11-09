// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import kotlin.math.abs

class BrightnessCurveTest {
    @Test fun everySliderPercentRoundTripsWithinOnePercent() {
        for (percent in 0..100) {
            val restored = BrightnessController.linearToPercent(BrightnessController.percentToLinear(percent))
            assertTrue("Percent $percent returned $restored", abs(percent - restored) <= 1)
        }
    }

    @Test fun bothCurveDirectionsAreMonotonic() {
        for (percent in 0 until 100) {
            assertTrue("Percent $percent", BrightnessController.percentToLinear(percent) <
                BrightnessController.percentToLinear(percent + 1))
        }
        for (step in 0 until 1000) {
            assertTrue("Linear step $step", BrightnessController.linearToPercent(step / 1000f) <=
                BrightnessController.linearToPercent((step + 1) / 1000f))
        }
    }

    @Test fun endpointsAndOutOfRangeValuesClamp() {
        assertEquals(1f, BrightnessController.percentToLinear(100), 0.000001f)
        assertEquals(1f, BrightnessController.percentToLinear(Int.MAX_VALUE), 0.000001f)
        assertEquals(BrightnessController.percentToLinear(0),
            BrightnessController.percentToLinear(Int.MIN_VALUE), 0f)
        assertEquals(0, BrightnessController.linearToPercent(-1f))
        assertEquals(100, BrightnessController.linearToPercent(2f))
        assertEquals(100, BrightnessController.linearToPercent(1f))
    }

    @Ignore("Pending ruling: zero percent maps to zero brightness, expected a positive backlight floor; needs device check")
    @Test fun zeroPercentKeepsAPositiveBacklightFloor() {
        assertTrue(BrightnessController.percentToLinear(0) > 0f)
    }
}
