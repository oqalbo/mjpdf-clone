// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.graphics.PointF
import android.graphics.RectF
import android.view.ViewConfiguration
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.SelectionDrag

class SelectionRobot : Robot() {

    private val reader = ReaderRobot()

    override fun awaitShown(timeoutMs: Long) {
        awaitDisplayed(R.id.pdfView, timeoutMs)
    }

    fun readerViewOriginOnScreen(): PointF = readView(R.id.pdfView) { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        PointF(location[0].toFloat(), location[1].toFloat())
    }

    fun toScreen(viewPoint: PointF): PointF {
        val origin = readerViewOriginOnScreen()
        return PointF(origin.x + viewPoint.x, origin.y + viewPoint.y)
    }

    fun longPressAtViewPoint(viewPoint: PointF, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ReaderState {
        val holdMs = ViewConfiguration.getLongPressTimeout().toLong() + LONG_PRESS_MARGIN_MS
        RawInput.pressAndHold(toScreen(viewPoint), holdMs)
        return reader.awaitSelection(timeoutMs)
    }

    fun longPressInsidePage(
        horizontalFraction: Float,
        verticalFraction: Float,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): ReaderState {
        val pageRect = reader.readState().pageRect
            ?: throw ConditionTimeoutException("The reader state hook reports no page rectangle yet")
        return longPressAtViewPoint(pointInside(pageRect, horizontalFraction, verticalFraction), timeoutMs)
    }

    fun dragEndHandleBy(
        dx: Float,
        dy: Float,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        requireSelectionChange: Boolean = true,
    ): ReaderState = dragHandle(Handle.END, dx, dy, timeoutMs, requireSelectionChange)

    fun selectionRects(): List<RectF> = reader.readState().selectionRects.orEmpty()

    fun pointInside(rect: RectF, horizontalFraction: Float, verticalFraction: Float): PointF =
        PointF(
            rect.left + rect.width() * horizontalFraction,
            rect.top + rect.height() * verticalFraction,
        )

    private fun dragHandle(
        handle: Handle,
        dx: Float,
        dy: Float,
        timeoutMs: Long,
        requireSelectionChange: Boolean,
    ): ReaderState {
        val before = reader.readState()
        val extentBefore = extentOf(before)
        val origin = requireHandle(handle, before)
        val from = toScreen(origin)
        val to = PointF(from.x + dx, from.y + dy)

        RawInput.dragAlongLine(from, to)

        return Await.untilValue(
            condition = if (requireSelectionChange) {
                "the ${handle.token} handle drag settles on a selection other than $extentBefore, because " +
                    "selDragging reads none both before a drag and after one, so settling alone cannot tell a " +
                    "finished drag from a drag that never landed"
            } else {
                "the ${handle.token} handle drag settles"
            },
            timeoutMs = timeoutMs,
            read = { reader.readStateOrNull() },
            predicate = {
                it != null && it.hasSelection && it.selectionDrag == SelectionDrag.NONE &&
                    (!requireSelectionChange || extentOf(it) != extentBefore)
            },
        )!!
    }

    private fun extentOf(state: ReaderState): Pair<Int?, Int?> = state.selectionStart to state.selectionEnd

    private fun requireHandle(handle: Handle, state: ReaderState): PointF {
        if (!state.hasSelection) {
            throw ConditionTimeoutException("No selection exists, so the ${handle.token} handle has no position")
        }
        return when (handle) {
            Handle.START -> state.selectionStartHandle
            Handle.END -> state.selectionEndHandle
        } ?: throw ConditionTimeoutException(
            "The selection hook reports no ${handle.token} handle centre, read: $state"
        )
    }

    private enum class Handle(val token: String) {
        START("start"),
        END("end"),
    }

    private companion object {
        const val LONG_PRESS_MARGIN_MS = 250L
    }
}
