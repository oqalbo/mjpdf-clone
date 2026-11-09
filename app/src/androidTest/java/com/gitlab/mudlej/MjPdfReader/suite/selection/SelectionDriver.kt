// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.graphics.PointF
import android.graphics.RectF
import android.view.ViewConfiguration
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.SelectionDrag
import kotlin.math.abs

enum class SelectionHandle { START, END }

class SelectionDriver(
    private val reader: ReaderRobot,
    private val selection: SelectionRobot,
) {

    fun projection(pageIndex: Int = reader.readState().page ?: 0): SelectionProjection =
        SelectionProjection.of(reader.readState(), pageIndex)

    fun viewPointOf(page: SelectionPageText, range: IntRange, horizontalFraction: Float = 0.5f): PointF {
        val box = page.boundsOf(range)
        val projection = projection(page.pageIndex)
        return projection.toViewPoint(box.left + box.width * horizontalFraction, box.centerY)
    }

    fun longPressRange(
        page: SelectionPageText,
        range: IntRange,
        horizontalFraction: Float = 0.5f,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): ReaderState = selection.longPressAtViewPoint(viewPointOf(page, range, horizontalFraction), timeoutMs)

    fun longPressWord(
        page: SelectionPageText,
        word: String,
        occurrence: Int = 1,
        horizontalFraction: Float = 0.5f,
    ): ReaderState {
        val range = page.rangeOf(word, occurrence)
        val state = longPressRange(page, range, horizontalFraction)
        val start = state.selectionStart ?: -1
        val end = state.selectionEnd ?: -1
        if (end <= range.first || start > range.last) {
            throw ConditionTimeoutException(
                "A long press aimed at \"$word\" (characters ${range.first} to ${range.last}) selected characters " +
                    "$start to $end instead, which reads \"${page.normalized(start until end)}\". The press point " +
                    "was computed from the fixture's own character boxes, so a miss means the projection from pdf " +
                    "points to view pixels is wrong for this state: $state"
            )
        }
        return state
    }

    fun longPressViewPoint(point: PointF, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ReaderState =
        selection.longPressAtViewPoint(point, timeoutMs)

    fun pressWithoutSelecting(point: PointF) {
        RawInput.pressAndHold(
            selection.toScreen(point),
            ViewConfiguration.getLongPressTimeout().toLong() + LONG_PRESS_MARGIN_MS,
        )
        reader.waitForIdle()
    }

    fun tapViewPoint(point: PointF) {
        val screen = selection.toScreen(point)
        TestEnvironment.uiDevice.click(screen.x.toInt(), screen.y.toInt())
        reader.waitForIdle()
    }

    fun selectedRange(state: ReaderState = reader.readState()): IntRange {
        val start = state.selectionStart
            ?: throw ConditionTimeoutException("The hook reports no selection, read: $state")
        val end = state.selectionEnd
            ?: throw ConditionTimeoutException("The hook reports a selection start but no end, read: $state")
        return start until end
    }

    fun selectionRects(state: ReaderState = reader.readState()): List<RectF> = state.selectionRects.orEmpty()

    fun handleCentre(handle: SelectionHandle, state: ReaderState = reader.readState()): PointF =
        when (handle) {
            SelectionHandle.START -> state.selectionStartHandle
            SelectionHandle.END -> state.selectionEndHandle
        } ?: throw ConditionTimeoutException(
            "The selection hook reports no ${handle.name.lowercase()} handle centre, read: $state"
        )

    fun handleRect(handle: SelectionHandle, state: ReaderState = reader.readState()): RectF {
        val rects = selectionRects(state)
        if (rects.isEmpty()) {
            throw ConditionTimeoutException("The selection hook reports no run rectangles, read: $state")
        }
        return if (handle == SelectionHandle.START) rects.first() else rects.last()
    }

    fun caretAnchor(handle: SelectionHandle, state: ReaderState = reader.readState()): PointF {
        val rect = handleRect(handle, state)
        val centre = handleCentre(handle, state)
        val radius = centre.y - rect.bottom
        val candidates = listOf(centre.x - radius, centre.x + radius)
        val anchorX = candidates.minByOrNull { minOf(abs(it - rect.left), abs(it - rect.right)) }!!
        return PointF(anchorX, rect.centerY())
    }

    fun beginCaretDrag(handle: SelectionHandle): CaretDrag {
        val state = reader.readState()
        val centre = handleCentre(handle, state)
        val anchor = caretAnchor(handle, state)
        val downScreen = selection.toScreen(centre)
        val downTime = RawInput.down(downScreen)
        return CaretDrag(handle, downTime, downScreen, PointF(centre.x - anchor.x, centre.y - anchor.y), state)
    }

    fun dragCaretTo(handle: SelectionHandle, target: PointF): ReaderState {
        val drag = beginCaretDrag(handle)
        drag.moveCaretTo(target)
        return drag.release()
    }

    fun dragHandleBy(handle: SelectionHandle, dx: Float, dy: Float): ReaderState {
        val anchor = caretAnchor(handle)
        return dragCaretTo(handle, PointF(anchor.x + dx, anchor.y + dy))
    }

    inner class CaretDrag(
        private val handle: SelectionHandle,
        private val downTime: Long,
        private var fingerScreen: PointF,
        private val fingerMinusCaret: PointF,
        private val stateBefore: ReaderState,
    ) {

        fun moveCaretTo(target: PointF) {
            val fingerView = PointF(target.x + fingerMinusCaret.x, target.y + fingerMinusCaret.y)
            val targetScreen = selection.toScreen(fingerView)
            RawInput.pointsAlong(fingerScreen, targetScreen).forEach { point ->
                RawInput.move(downTime, point)
                pace()
            }
            fingerScreen = targetScreen
        }

        fun state(): ReaderState = reader.readState()

        fun release(): ReaderState {
            RawInput.up(downTime, fingerScreen)
            val settled = Await.untilValue(
                condition = "the ${handle.name.lowercase()} handle drag settles with a selection still present",
                timeoutMs = Await.DEFAULT_TIMEOUT_MS,
                read = { reader.readStateOrNull() },
                predicate = { it != null && it.hasSelection && it.selectionDrag == SelectionDrag.NONE },
            )!!
            requireNoPan(stateBefore, settled, handle)
            return settled
        }

        private fun pace() {
            android.os.SystemClock.sleep(RawInput.FRAME_INTERVAL_MS)
        }
    }

    private fun requireNoPan(before: ReaderState, after: ReaderState, handle: SelectionHandle) {
        val movedX = abs((after.offsetX ?: 0f) - (before.offsetX ?: 0f))
        val movedY = abs((after.offsetY ?: 0f) - (before.offsetY ?: 0f))
        if (movedX > PAN_TOLERANCE_PX || movedY > PAN_TOLERANCE_PX) {
            throw ConditionTimeoutException(
                "Dragging the ${handle.name.lowercase()} handle panned the page by ($movedX, $movedY) pixels. " +
                    "The state before was $before and after was $after"
            )
        }
    }

    companion object {
        const val LONG_PRESS_MARGIN_MS = 250L
        const val PAN_TOLERANCE_PX = 1f
    }
}
