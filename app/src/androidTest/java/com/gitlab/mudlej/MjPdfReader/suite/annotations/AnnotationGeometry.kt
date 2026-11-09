// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.graphics.PointF
import android.graphics.RectF
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState

data class PageFrame(val pageIndex: Int, val viewBounds: RectF, val pointWidth: Float, val pointHeight: Float) {

    fun toViewPoint(pdfX: Float, pdfY: Float): PointF = PointF(
        viewBounds.left + (pdfX / pointWidth) * viewBounds.width(),
        viewBounds.top + ((pointHeight - pdfY) / pointHeight) * viewBounds.height(),
    )

    fun toViewRect(rect: PdfRect): RectF {
        val topLeft = toViewPoint(rect.left, rect.top)
        val bottomRight = toViewPoint(rect.right, rect.bottom)
        return RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    fun centreOf(rect: PdfRect): PointF = toViewPoint(rect.centerX, rect.centerY)

    fun viewPixelsPerPoint(): Float = viewBounds.height() / pointHeight
}

object AnnotationGeometry {

    fun pagePointSize(pageIndex: Int): PointF = TestEnvironment.onMainThread {
        val view = readerView()
        val size = view.getPagePointSize(pageIndex)
            ?: throw ConditionTimeoutException(
                "PDFView reports no point size for page $pageIndex, which means the document is not loaded yet"
            )
        PointF(size.width, size.height)
    }

    fun frameOf(state: ReaderState): PageFrame {
        val pageIndex = state.page
            ?: throw ConditionTimeoutException("The reader state hook reports no current page, read: $state")
        val bounds = state.pageRect
            ?: throw ConditionTimeoutException("The reader state hook reports no page rectangle yet, read: $state")
        val size = pagePointSize(pageIndex)
        return PageFrame(pageIndex, bounds, size.x, size.y)
    }

    fun isOnScreen(state: ReaderState, point: PointF): Boolean {
        val width = state.viewWidth ?: return false
        val height = state.viewHeight ?: return false
        return point.x >= 0f && point.y >= 0f && point.x <= width && point.y <= height
    }

    fun requireOnScreen(state: ReaderState, point: PointF, what: String): PointF {
        if (!isOnScreen(state, point)) {
            throw ConditionTimeoutException(
                "$what maps to view point (${point.x}, ${point.y}), which is outside the " +
                    "${state.viewWidth} by ${state.viewHeight} viewer, so a touch there would land on " +
                    "another view. The page rectangle was ${state.pageRect}."
            )
        }
        return point
    }

    fun jumpToPage(pageIndex: Int) {
        TestEnvironment.onMainThread { readerView().jumpTo(pageIndex) }
    }

    private fun readerView(): PDFView {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed, so the viewer cannot be reached")
        return activity.findViewById(R.id.pdfView)
            ?: throw ConditionTimeoutException("The resumed activity carries no pdfView")
    }
}
