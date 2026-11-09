// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import android.content.res.Resources
import android.graphics.PointF
import android.view.ViewConfiguration
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import kotlin.math.min

enum class GestureAxis { HORIZONTAL, VERTICAL }

enum class GestureEnding { UP, CANCEL, LEAVE_DOWN }

object TouchLimits {

    const val SPAN_MARGIN = 24f

    const val MIN_ENGAGING_SPAN = 240f

    val touchSlop: Int by lazy { ViewConfiguration.get(TestEnvironment.targetContext).scaledTouchSlop }

    val spanSlop: Int get() = touchSlop * 2

    val minScalingSpan: Int by lazy { readMinScalingSpan() }

    val engagingSpan: Float
        get() = maxOf(minScalingSpan + spanSlop + SPAN_MARGIN, MIN_ENGAGING_SPAN)

    private fun readMinScalingSpan(): Int {
        val system = Resources.getSystem()
        val id = system.getIdentifier("config_minScalingSpan", "dimen", "android")
        if (id != 0) {
            val declared = system.getDimensionPixelSize(id)
            if (declared > 0) return declared
        }
        return (MIN_SCALING_SPAN_MM * system.displayMetrics.xdpi / MM_PER_INCH).toInt()
    }

    private const val MIN_SCALING_SPAN_MM = 27f
    private const val MM_PER_INCH = 25.4f
}

object Pinch {

    const val FRAMES = 20

    const val HEADROOM = 4f

    fun byRatio(
        ratio: Float,
        focus: PointF = ReaderCanvas.centre(),
        axis: GestureAxis = GestureAxis.VERTICAL,
        frames: Int = FRAMES,
        frameMs: Long = PointerGesture.FRAME_MS,
    ) {
        withoutLift(ratio, focus, axis, frames, frameMs).liftSecond(frameMs).up(frameMs)
    }

    fun withoutLift(
        ratio: Float,
        focus: PointF = ReaderCanvas.centre(),
        axis: GestureAxis = GestureAxis.VERTICAL,
        frames: Int = FRAMES,
        frameMs: Long = PointerGesture.FRAME_MS,
    ): PointerGesture {
        require(ratio > 0f && ratio != 1f) { "A pinch ratio of $ratio changes nothing" }
        val engaging = TouchLimits.engagingSpan
        val from = if (ratio > 1f) engaging else engaging / ratio
        val to = if (ratio > 1f) engaging * ratio else engaging
        val leadIn = TouchLimits.spanSlop + TouchLimits.SPAN_MARGIN
        val lead = if (ratio > 1f) from - leadIn else from + leadIn

        val startRoom = roomForSpan(focus, axis, ReaderCanvas.rect().let {
            if (axis == GestureAxis.VERTICAL) it.top.toFloat() to it.bottom.toFloat()
            else it.left.toFloat() to it.right.toFloat()
        })
        val moveRoom = roomForSpan(focus, axis, displayRange(axis))
        require(lead <= startRoom) {
            "A pinch at ratio $ratio needs to start ${lead.toInt()} px apart, but only ${startRoom.toInt()} px " +
                "of canvas is available on this axis. The scale detector ignores any span below " +
                "config_minScalingSpan, which reads ${TouchLimits.minScalingSpan} px on this device, so the " +
                "achievable ratio is bounded by the screen."
        }
        require(maxOf(lead, to) <= moveRoom) {
            "A pinch at ratio $ratio needs ${maxOf(lead, to).toInt()} px of travel, but only " +
                "${moveRoom.toInt()} px of display is available on this axis"
        }

        val gesture = PointerGesture(pointsFor(focus, axis, lead))
        gesture.downFirst().downSecond(frameMs)
        place(gesture, focus, axis, from)
        gesture.frame(frameMs)
        for (step in 1..frames) {
            place(gesture, focus, axis, from + (to - from) * step / frames)
            gesture.frame(frameMs)
        }
        return gesture
    }

    fun maxSpreadRatio(focus: PointF = ReaderCanvas.centre(), axis: GestureAxis = GestureAxis.VERTICAL): Float =
        (roomForSpan(focus, axis, displayRange(axis)) - HEADROOM) / TouchLimits.engagingSpan

    fun maxCloseRatio(focus: PointF = ReaderCanvas.centre(), axis: GestureAxis = GestureAxis.VERTICAL): Float {
        val bounds = ReaderCanvas.rect()
        val range = if (axis == GestureAxis.VERTICAL) bounds.top.toFloat() to bounds.bottom.toFloat()
        else bounds.left.toFloat() to bounds.right.toFloat()
        val widest = roomForSpan(focus, axis, range) - TouchLimits.spanSlop - TouchLimits.SPAN_MARGIN - HEADROOM
        return TouchLimits.engagingSpan / widest
    }

    private fun place(gesture: PointerGesture, focus: PointF, axis: GestureAxis, span: Float) {
        val points = pointsFor(focus, axis, span)
        gesture.placeAt(0, points[0])
        gesture.placeAt(1, points[1])
    }

    private fun pointsFor(focus: PointF, axis: GestureAxis, span: Float): List<PointF> =
        if (axis == GestureAxis.VERTICAL) {
            listOf(PointF(focus.x, focus.y - span / 2f), PointF(focus.x, focus.y + span / 2f))
        } else {
            listOf(PointF(focus.x - span / 2f, focus.y), PointF(focus.x + span / 2f, focus.y))
        }

    private fun roomForSpan(focus: PointF, axis: GestureAxis, range: Pair<Float, Float>): Float {
        val position = if (axis == GestureAxis.VERTICAL) focus.y else focus.x
        return 2f * min(position - range.first, range.second - position) - TouchLimits.SPAN_MARGIN
    }

    private fun displayRange(axis: GestureAxis): Pair<Float, Float> {
        val device = TestEnvironment.uiDevice
        return if (axis == GestureAxis.VERTICAL) 1f to (device.displayHeight - 1f)
        else 1f to (device.displayWidth - 1f)
    }
}

object Swipe {

    const val FLING_STEPS = 8
    const val FLING_STEP_MS = 8L
    const val DRAG_STEPS = 80
    const val DRAG_STEP_MS = 5L
    const val DRAG_SETTLE_MS = 150L

    fun fling(
        from: PointF,
        stepX: Float,
        stepY: Float,
        steps: Int = FLING_STEPS,
        stepMs: Long = FLING_STEP_MS,
    ) {
        stroke(from, stepX, stepY, steps, stepMs, GestureEnding.UP)
    }

    fun stroke(
        from: PointF,
        stepX: Float,
        stepY: Float,
        steps: Int,
        stepMs: Long,
        ending: GestureEnding = GestureEnding.UP,
        onFrame: (Int) -> Unit = {},
    ): PointerGesture {
        val gesture = PointerGesture(PointF(from.x, from.y))
        gesture.downFirst()
        for (step in 1..steps) {
            gesture.offsetBy(0, stepX, stepY).frame(stepMs).probe { onFrame(step) }
        }
        when (ending) {
            GestureEnding.UP -> gesture.up(stepMs)
            GestureEnding.CANCEL -> gesture.cancel(stepMs)
            GestureEnding.LEAVE_DOWN -> Unit
        }
        return gesture
    }

    fun press(at: PointF, holdMs: Long = 50L, onDown: () -> Unit = {}) {
        PointerGesture(PointF(at.x, at.y))
            .downFirst()
            .probe(onDown)
            .up(holdMs)
    }

    fun drag(
        from: PointF,
        to: PointF,
        steps: Int = DRAG_STEPS,
        stepMs: Long = DRAG_STEP_MS,
        settleMs: Long = DRAG_SETTLE_MS,
        ending: GestureEnding = GestureEnding.UP,
        onFrame: (Int) -> Unit = {},
    ) {
        require(steps > 0) { "A drag needs at least one step" }
        val stepX = (to.x - from.x) / steps
        val stepY = (to.y - from.y) / steps
        val gesture = PointerGesture(PointF(from.x, from.y))
        gesture.downFirst()
        for (step in 1..steps) {
            gesture.offsetBy(0, stepX, stepY).frame(stepMs).probe { onFrame(step) }
        }
        gesture.frame(settleMs)
        when (ending) {
            GestureEnding.UP -> gesture.up()
            GestureEnding.CANCEL -> gesture.cancel()
            GestureEnding.LEAVE_DOWN -> Unit
        }
    }
}
