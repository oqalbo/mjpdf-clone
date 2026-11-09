// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import kotlin.math.abs

object SelectionScroll {

    const val BOUNDARY_VISIBLE_FRACTION = 0.7f
    const val PLACEMENT_TOLERANCE_PX = 2f
    const val PLACEMENT_ATTEMPTS = 4

    fun apply(reader: ReaderRobot, zoom: Float, offsetX: Float, offsetY: Float): ReaderState {
        TestEnvironment.onMainThread { readerView().debugSetViewport(zoom, offsetX, offsetY) }
        reader.awaitMotionSettled()
        return reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    fun toPageBoundary(reader: ReaderRobot, from: ReaderState): ReaderState {
        val pageHeight = from.pageHeight
            ?: throw ConditionTimeoutException("The hook reports no scaled page height, read: $from")
        val viewHeight = (from.viewHeight ?: 0).toFloat()
        val settled = apply(reader, from.zoom!!, from.offsetX!!, -(pageHeight - viewHeight * BOUNDARY_VISIBLE_FRACTION))
        if (settled.pageCount!! < 2) {
            throw AssertionError("The fixture holds one page, so there is no page boundary to scroll to")
        }
        return settled
    }

    fun placePdfPointAt(
        reader: ReaderRobot,
        pageIndex: Int,
        pdfPoint: PdfBox,
        viewFractionX: Float,
        viewFractionY: Float,
        zoom: Float,
    ): ReaderState {
        val start = reader.readState()
        var state = apply(reader, zoom, start.offsetX ?: 0f, start.offsetY ?: 0f)
        repeat(PLACEMENT_ATTEMPTS) {
            val projection = SelectionProjection.of(state, pageIndex)
            val current = projection.toViewPoint(pdfPoint.centerX, pdfPoint.centerY)
            val targetX = (state.viewWidth ?: 0).toFloat() * viewFractionX
            val targetY = (state.viewHeight ?: 0).toFloat() * viewFractionY
            if (abs(current.x - targetX) <= PLACEMENT_TOLERANCE_PX &&
                abs(current.y - targetY) <= PLACEMENT_TOLERANCE_PX
            ) {
                return state
            }
            state = apply(
                reader,
                zoom,
                state.offsetX!! + (targetX - current.x),
                state.offsetY!! + (targetY - current.y),
            )
        }
        val projection = SelectionProjection.of(state, pageIndex)
        val reached = projection.toViewPoint(pdfPoint.centerX, pdfPoint.centerY)
        throw ConditionTimeoutException(
            "The viewer would not put the pdf point (${pdfPoint.centerX}, ${pdfPoint.centerY}) of page $pageIndex " +
                "at ($viewFractionX, $viewFractionY) of the ${state.viewWidth} by ${state.viewHeight} viewport at " +
                "zoom $zoom. It settled with that point at $reached, which means the requested offset was clamped " +
                "and the document is not large enough at this zoom to place it there. The state reads $state"
        )
    }

    private fun readerView(): PDFView {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed, so the viewer cannot be reached")
        return activity.findViewById(R.id.pdfView)
            ?: throw ConditionTimeoutException("The resumed activity carries no pdfView")
    }
}
