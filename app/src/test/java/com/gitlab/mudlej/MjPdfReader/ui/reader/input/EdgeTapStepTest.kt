// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.input

import com.gitlab.mudlej.MjPdfReader.data.TapToTurnZones
import org.junit.Assert.*
import org.junit.Test

class EdgeTapStepTest {
    @Test fun rightBoundaryIsIncludedAndLeftBoundaryIsExcluded() {
        assertEquals(1, step(x = 75f))
        assertNull(step(x = 25f))
        assertEquals(-1, step(x = 24.99f))
        assertNull(step(x = 74.99f))
    }

    @Test fun readingDirectionReversesOnlyLeftAndRightZones() {
        assertEquals(-1, step(x = 90f, rtl = true))
        assertEquals(1, step(x = 10f, rtl = true))
        for (rtl in listOf(false, true)) {
            assertEquals(1, step(zones = TapToTurnZones.TOP_BOTTOM, y = 150f, rtl = rtl))
            assertEquals(-1, step(zones = TapToTurnZones.TOP_BOTTOM, y = 49f, rtl = rtl))
            assertNull(step(zones = TapToTurnZones.TOP_BOTTOM, y = 50f, rtl = rtl))
        }
    }

    @Test fun zoomAboveOneAndDisabledZonesDoNotTurnPages() {
        assertEquals(1, step(x = 90f, zoom = 1f))
        assertNull(step(x = 90f, zoom = 1.01f))
        assertNull(step(zones = TapToTurnZones.OFF, x = 90f))
    }

    private fun step(zones: TapToTurnZones = TapToTurnZones.LEFT_RIGHT,
        x: Float = 50f, y: Float = 100f, rtl: Boolean = false, zoom: Float = 1f): Int? =
        edgeTapStep(zones, x, y, 100, 200, rtl, zoom)
}
