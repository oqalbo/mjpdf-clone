// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

object RawInput {

    const val MAX_DRAG_STEP_PX = 8f
    const val COARSE_DRAG_STEP_PX = 24f
    const val FRAME_INTERVAL_MS = 16L
    const val SETTLE_MOVES_BEFORE_RELEASE = 4

    fun down(point: PointF, source: Int = InputDevice.SOURCE_TOUCHSCREEN): Long {
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downTime, downTime, point, source)
        return downTime
    }

    fun move(downTime: Long, point: PointF, source: Int = InputDevice.SOURCE_TOUCHSCREEN) {
        inject(MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis(), point, source)
    }

    fun up(downTime: Long, point: PointF, source: Int = InputDevice.SOURCE_TOUCHSCREEN) {
        inject(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), point, source)
    }

    fun cancel(downTime: Long, point: PointF, source: Int = InputDevice.SOURCE_TOUCHSCREEN) {
        inject(MotionEvent.ACTION_CANCEL, downTime, SystemClock.uptimeMillis(), point, source)
    }

    fun dragAlongLine(
        from: PointF,
        to: PointF,
        stepPx: Float = MAX_DRAG_STEP_PX,
        intervalMs: Long = FRAME_INTERVAL_MS,
        source: Int = InputDevice.SOURCE_TOUCHSCREEN,
        endWithCancel: Boolean = false,
        settleMoves: Int = 0,
    ) {
        val downTime = down(from, source)
        pace(intervalMs)
        pointsAlong(from, to, stepPx).forEach { point ->
            move(downTime, point, source)
            pace(intervalMs)
        }
        repeat(settleMoves) {
            move(downTime, to, source)
            pace(intervalMs)
        }
        if (endWithCancel) cancel(downTime, to, source) else up(downTime, to, source)
    }

    fun dragWithoutFling(
        from: PointF,
        to: PointF,
        stepPx: Float = COARSE_DRAG_STEP_PX,
        source: Int = InputDevice.SOURCE_TOUCHSCREEN,
    ) {
        dragAlongLine(
            from = from,
            to = to,
            stepPx = stepPx,
            source = source,
            settleMoves = SETTLE_MOVES_BEFORE_RELEASE,
        )
    }

    fun pressAndHold(point: PointF, holdMs: Long, source: Int = InputDevice.SOURCE_TOUCHSCREEN) {
        val downTime = down(point, source)
        pace(holdMs)
        up(downTime, point, source)
    }

    fun pointsAlong(from: PointF, to: PointF, stepPx: Float = MAX_DRAG_STEP_PX): List<PointF> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = kotlin.math.hypot(dx, dy)
        if (distance <= 0f) return listOf(PointF(to.x, to.y))
        val steps = kotlin.math.ceil(distance / stepPx).toInt().coerceAtLeast(1)
        return (1..steps).map { step ->
            val fraction = step.toFloat() / steps
            PointF(from.x + dx * fraction, from.y + dy * fraction)
        }
    }

    private fun inject(action: Int, downTime: Long, eventTime: Long, point: PointF, source: Int) {
        val event = MotionEvent.obtain(downTime, eventTime, action, point.x, point.y, 0)
        try {
            event.source = source
            TestEnvironment.instrumentation.uiAutomation.injectInputEvent(event, true)
        } finally {
            event.recycle()
        }
    }

    private fun pace(millis: Long) {
        if (millis > 0) SystemClock.sleep(millis)
    }
}
