// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.graphics.PointF
import android.graphics.RectF
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.model.CropBounds
import com.github.barteksc.pdfviewer.model.CropMargins
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState

class SelectionProjection(
    val pageIndex: Int,
    val viewBounds: RectF,
    val widthPoints: Float,
    val heightPoints: Float,
    val crop: CropBounds,
) {

    fun toViewPoint(pdfX: Float, pdfY: Float): PointF {
        val fractionX = (pdfX / widthPoints - crop.left) / crop.width
        val fractionY = ((heightPoints - pdfY) / heightPoints - crop.top) / crop.height
        return PointF(
            viewBounds.left + fractionX * viewBounds.width(),
            viewBounds.top + fractionY * viewBounds.height(),
        )
    }

    fun centreOf(box: PdfBox): PointF = toViewPoint(box.centerX, box.centerY)

    fun toViewRect(box: PdfBox): RectF {
        val topLeft = toViewPoint(box.left, box.top)
        val bottomRight = toViewPoint(box.right, box.bottom)
        return RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    fun viewPixelsPerPoint(): Float = viewBounds.height() / heightPoints / crop.height

    fun shiftedBy(dx: Float, dy: Float): SelectionProjection = SelectionProjection(
        pageIndex,
        RectF(viewBounds.left + dx, viewBounds.top + dy, viewBounds.right + dx, viewBounds.bottom + dy),
        widthPoints,
        heightPoints,
        crop,
    )

    fun forNeighbourPage(pageIndex: Int, dx: Float): SelectionProjection = SelectionProjection(
        pageIndex,
        RectF(viewBounds.left + dx, viewBounds.top, viewBounds.right + dx, viewBounds.bottom),
        widthPoints,
        heightPoints,
        crop,
    )

    companion object {

        fun of(state: ReaderState, pageIndex: Int = state.page ?: 0): SelectionProjection {
            val bounds = state.pageRect
                ?: throw ConditionTimeoutException("The reader state hook reports no page rectangle yet, read: $state")
            if (state.page != pageIndex) {
                throw ConditionTimeoutException(
                    "The hook publishes the page rectangle of the current page only, which is page ${state.page}, " +
                        "so it cannot describe page $pageIndex. Move the reader onto that page first, or derive " +
                        "the neighbour rectangle from this one."
                )
            }
            val size = pagePointSize(pageIndex)
            return SelectionProjection(pageIndex, bounds, size.x, size.y, cropOf(state, pageIndex))
        }

        fun pagePointSize(pageIndex: Int): PointF = TestEnvironment.onMainThread {
            val size = readerView().getPagePointSize(pageIndex)
                ?: throw ConditionTimeoutException(
                    "PDFView reports no point size for page $pageIndex, which means the document is not loaded yet"
                )
            PointF(size.width, size.height)
        }

        fun cropOf(state: ReaderState, pageIndex: Int): CropBounds {
            val enabled = TestEnvironment.onMainThread { readerView().isCropMarginsEnabled }
            if (!enabled) return CropBounds.fullPage()
            val hash = state.fileHash
                ?: throw ConditionTimeoutException(
                    "Crop margins are applied but the document identity has not resolved, so the stored crop " +
                        "cannot be read and no view coordinate can be computed"
                )
            val stored = Records.byHash(hash)?.cropMargins
                ?: throw ConditionTimeoutException(
                    "Crop margins are applied but the PdfRecord for $hash stores none, so the projection from " +
                        "pdf points to view pixels has no crop to apply"
                )
            val margins = CropMargins.fromStorageString(stored)
                ?: throw AssertionError("The stored crop margins string \"$stored\" did not parse")
            return margins.forDocumentPage(pageIndex)
        }

        private fun readerView(): PDFView {
            val activity = TestEnvironment.resumedActivity()
                ?: throw ConditionTimeoutException("No activity is resumed, so the viewer cannot be reached")
            return activity.findViewById(R.id.pdfView)
                ?: throw ConditionTimeoutException("The resumed activity carries no pdfView")
        }
    }
}
