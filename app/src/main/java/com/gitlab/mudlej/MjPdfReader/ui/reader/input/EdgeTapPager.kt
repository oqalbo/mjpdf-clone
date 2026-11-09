// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.input

import android.view.MotionEvent
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.TapToTurnZones
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding

class EdgeTapPager(
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val pref: Preferences,
) {

    fun handleTap(event: MotionEvent): Boolean {
        if (!pref.getSinglePageMode()) {
            return false
        }
        val zones = pref.getTapToTurnZones()
        if (zones == TapToTurnZones.OFF) {
            return false
        }
        val pdfView = binding.pdfView
        if (pdfView.zoom > 1f) {
            return false
        }
        val step = edgeTapStep(zones, event.x, event.y, pdfView.width, pdfView.height,
            pdfView.isHorizontalReadingDirectionRtl, pdfView.zoom) ?: return false
        if (pdfView.hasLinkAt(event.x, event.y)) {
            return false
        }
        pdfView.jumpTo(pdfView.getPageAfterRowStep(pdf.pageNumber, step), true)
        return true
    }
}

internal fun edgeTapStep(
    zones: TapToTurnZones,
    x: Float,
    y: Float,
    width: Int,
    height: Int,
    rtl: Boolean,
    zoom: Float,
): Int? {
    if (zoom > 1f) return null
    return when (zones) {
        TapToTurnZones.LEFT_RIGHT -> {
            val forward = if (rtl) -1 else 1
            when {
                x >= width * 0.75f -> forward
                x < width * 0.25f -> -forward
                else -> null
            }
        }
        TapToTurnZones.TOP_BOTTOM -> when {
            y >= height * 0.75f -> 1
            y < height * 0.25f -> -1
            else -> null
        }
        TapToTurnZones.OFF -> null
    }
}
