// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.graphics.PointF

object SelectionTargets {

    const val INSIDE_GLYPH_FRACTION = 0.25f

    fun caretJustAfter(page: SelectionPageText, range: IntRange, projection: SelectionProjection): PointF {
        val last = page.charBoxOf(range.last)
        val line = page.boundsOf(range)
        return projection.toViewPoint(last.right - last.width * INSIDE_GLYPH_FRACTION, line.centerY)
    }

    fun caretJustBefore(page: SelectionPageText, range: IntRange, projection: SelectionProjection): PointF {
        val first = page.charBoxOf(range.first)
        val line = page.boundsOf(range)
        return projection.toViewPoint(first.left + first.width * INSIDE_GLYPH_FRACTION, line.centerY)
    }

    fun caretJustAfterInRtl(page: SelectionPageText, range: IntRange, projection: SelectionProjection): PointF {
        val last = page.charBoxOf(range.last)
        val line = page.boundsOf(range)
        return projection.toViewPoint(last.left + last.width * INSIDE_GLYPH_FRACTION, line.centerY)
    }

    fun centreOfLine(page: SelectionPageText, lineIndex: Int, projection: SelectionProjection): PointF {
        val box = page.boundsOf(page.lineRange(lineIndex))
        return projection.toViewPoint(box.centerX, box.centerY)
    }
}
