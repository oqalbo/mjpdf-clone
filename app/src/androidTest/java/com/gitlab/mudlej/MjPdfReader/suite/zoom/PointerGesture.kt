// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

class PointerGesture(start: List<PointF>) {

    private val at: List<PointF> = start.map { PointF(it.x, it.y) }
    private val down = mutableListOf<Int>()
    private var downTime = 0L
    private var eventTime = 0L

    constructor(vararg start: PointF) : this(start.toList())

    fun downFirst(): PointerGesture {
        downTime = SystemClock.uptimeMillis()
        eventTime = downTime
        down.add(0)
        send(MotionEvent.ACTION_DOWN, 0)
        return this
    }

    fun downSecond(afterMs: Long = FRAME_MS, beforeDispatch: () -> Unit = {}): PointerGesture {
        require(at.size >= 2) { "A second pointer needs a second start point" }
        advance(afterMs)
        down.add(1)
        beforeDispatch()
        send(MotionEvent.ACTION_POINTER_DOWN, down.indexOf(1))
        return this
    }

    fun placeAt(pointer: Int, x: Float, y: Float): PointerGesture {
        at[pointer].set(x, y)
        return this
    }

    fun placeAt(pointer: Int, point: PointF): PointerGesture = placeAt(pointer, point.x, point.y)

    fun offsetBy(pointer: Int, dx: Float, dy: Float): PointerGesture {
        at[pointer].offset(dx, dy)
        return this
    }

    fun frame(afterMs: Long = FRAME_MS): PointerGesture {
        advance(afterMs)
        send(MotionEvent.ACTION_MOVE, 0)
        return this
    }

    fun liftSecond(afterMs: Long = FRAME_MS): PointerGesture {
        advance(afterMs)
        send(MotionEvent.ACTION_POINTER_UP, down.indexOf(1))
        down.remove(1)
        return this
    }

    fun up(afterMs: Long = FRAME_MS): PointerGesture {
        advance(afterMs)
        send(MotionEvent.ACTION_UP, 0)
        down.clear()
        return this
    }

    fun cancel(afterMs: Long = FRAME_MS): PointerGesture {
        advance(afterMs)
        send(MotionEvent.ACTION_CANCEL, 0)
        down.clear()
        return this
    }

    fun hold(ms: Long): PointerGesture {
        advance(ms)
        return this
    }

    fun probe(block: () -> Unit): PointerGesture {
        block()
        return this
    }

    private fun advance(ms: Long) {
        eventTime += ms
        val now = SystemClock.uptimeMillis()
        if (now < eventTime) SystemClock.sleep(eventTime - now)
    }

    private fun send(action: Int, pointerIndex: Int) {
        check(down.isNotEmpty()) { "No pointer is down, so $action cannot be dispatched" }
        val count = down.size
        val properties = Array(count) { slot ->
            MotionEvent.PointerProperties().apply {
                id = down[slot]
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(count) { slot ->
            MotionEvent.PointerCoords().apply {
                x = at[down[slot]].x
                y = at[down[slot]].y
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            count,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        try {
            TestEnvironment.instrumentation.sendPointerSync(event)
        } finally {
            event.recycle()
        }
    }

    companion object {
        const val FRAME_MS = 16L
    }
}
