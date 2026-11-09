// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import com.github.barteksc.pdfviewer.PDFView
import com.shockwave.pdfium.util.SizeF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PdfPropertiesSummary {

    private const val POINTS_TO_MM = 25.4f / 72f
    private const val SIZE_TOLERANCE_MM = 2f
    const val MAX_FONT_SCAN_PAGES = 50

    private val standardSizesMm = listOf(
        "A3" to Pair(297f, 420f),
        "A4" to Pair(210f, 297f),
        "A5" to Pair(148f, 210f),
        "A6" to Pair(105f, 148f),
        "Letter" to Pair(216f, 279f),
        "Legal" to Pair(216f, 356f),
    )

    fun formatPageSizes(pdfView: PDFView, mixedLabel: String): String? {
        val sizes = (0 until pdfView.pageCount)
            .mapNotNull { pdfView.getPagePointSize(it) }
            .filter { it.width > 0f && it.height > 0f }
        if (sizes.isEmpty()) return null

        val first = sizes.first()
        if (sizes.any { !sameSize(it, first) }) return mixedLabel
        return formatSize(first)
    }

    fun formatFonts(pdfView: PDFView, embeddedLabel: String, notEmbeddedLabel: String): String? {
        val fonts = pdfView.getAllFonts(MAX_FONT_SCAN_PAGES)
        if (fonts.isEmpty()) return null

        val lines = fonts
            .sortedBy { it.name.lowercase() }
            .map { "${it.name} (${if (it.isEmbedded) embeddedLabel else notEmbeddedLabel})" }
            .toMutableList()
        if (pdfView.pageCount > MAX_FONT_SCAN_PAGES) {
            lines.add("…")
        }
        return lines.joinToString("\n")
    }

    private fun sameSize(a: SizeF, b: SizeF): Boolean {
        val (aShort, aLong) = sortedMm(a)
        val (bShort, bLong) = sortedMm(b)
        return abs(aShort - bShort) <= SIZE_TOLERANCE_MM && abs(aLong - bLong) <= SIZE_TOLERANCE_MM
    }

    private fun sortedMm(size: SizeF): Pair<Float, Float> {
        val width = size.width * POINTS_TO_MM
        val height = size.height * POINTS_TO_MM
        return min(width, height) to max(width, height)
    }

    private fun formatSize(size: SizeF): String {
        val widthMm = (size.width * POINTS_TO_MM).roundToInt()
        val heightMm = (size.height * POINTS_TO_MM).roundToInt()
        val (shortSide, longSide) = sortedMm(size)
        val standardName = standardSizesMm.firstOrNull { (_, dimensions) ->
            abs(shortSide - dimensions.first) <= SIZE_TOLERANCE_MM
                    && abs(longSide - dimensions.second) <= SIZE_TOLERANCE_MM
        }?.first

        val dimensionsText = "$widthMm × $heightMm mm"
        return if (standardName != null) "$standardName ($dimensionsText)" else dimensionsText
    }
}
