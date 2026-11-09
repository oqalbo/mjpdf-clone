// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import android.graphics.Rect
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

object BenchmarkGestures {
    const val WAIT_TIMEOUT_MS = 20_000L
    const val DRAG_STEPS = 30
    const val PINCH_PERCENT = 0.6f
    const val PINCH_STEPS = 60

    fun selector(resourceName: String): BySelector = By.res(BenchmarkTargets.PACKAGE, resourceName)

    fun UiDevice.awaitObject(resourceName: String, timeoutMs: Long = WAIT_TIMEOUT_MS): UiObject2 {
        val found = wait(Until.findObject(selector(resourceName)), timeoutMs)
        return found ?: throw AssertionError(
            "The view $resourceName never appeared within ${timeoutMs}ms, the foreground package is " +
                currentPackageName
        )
    }

    fun UiDevice.awaitVisible(resourceName: String, timeoutMs: Long = WAIT_TIMEOUT_MS) {
        awaitObject(resourceName, timeoutMs)
    }

    fun UiDevice.awaitStableChildCount(
        resourceName: String,
        timeoutMs: Long = WAIT_TIMEOUT_MS,
    ): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        var previous = -1
        while (System.currentTimeMillis() < deadline) {
            val current = findObject(selector(resourceName))?.childCount ?: -1
            if (current > 0 && current == previous) return current
            previous = current
            waitForIdle()
        }
        throw AssertionError(
            "The view $resourceName never reached a stable child count within ${timeoutMs}ms, it last " +
                "reported $previous children"
        )
    }

    fun UiDevice.flingContent(resourceName: String, times: Int) {
        repeat(times) {
            val target = awaitObject(resourceName)
            target.setGestureMargin(target.visibleBounds.width() / GESTURE_MARGIN_DIVISOR)
            target.fling(Direction.DOWN, FLING_SPEED)
            waitForIdle()
        }
    }

    fun UiDevice.dragContent(resourceName: String, times: Int) {
        repeat(times) {
            val bounds = awaitObject(resourceName).visibleBounds
            val startY = bounds.centerY() + bounds.height() / DRAG_FRACTION
            val endY = bounds.centerY() - bounds.height() / DRAG_FRACTION
            drag(bounds.centerX(), startY, bounds.centerX(), endY, DRAG_STEPS)
            waitForIdle()
        }
    }

    fun UiDevice.pinchOpenOn(resourceName: String) {
        val target = awaitObject(resourceName)
        target.pinchOpen(PINCH_PERCENT, PINCH_STEPS)
        waitForIdle()
    }

    fun UiDevice.pinchCloseOn(resourceName: String) {
        val target = awaitObject(resourceName)
        target.pinchClose(PINCH_PERCENT, PINCH_STEPS)
        waitForIdle()
    }

    fun UiDevice.pinchCycleOn(resourceName: String, cycles: Int) {
        repeat(cycles) {
            pinchOpenOn(resourceName)
            pinchCloseOn(resourceName)
        }
    }

    fun UiDevice.turnPagesForward(resourceName: String, times: Int) {
        swipePages(resourceName, times, PAGE_SWIPE_FAR_FRACTION, PAGE_SWIPE_NEAR_FRACTION)
    }

    fun UiDevice.turnPagesBackward(resourceName: String, times: Int) {
        swipePages(resourceName, times, PAGE_SWIPE_NEAR_FRACTION, PAGE_SWIPE_FAR_FRACTION)
    }

    private fun UiDevice.swipePages(resourceName: String, times: Int, fromFraction: Double, toFraction: Double) {
        val bounds: Rect = awaitObject(resourceName).visibleBounds
        val x = bounds.centerX()
        val startY = bounds.top + (bounds.height() * fromFraction).toInt()
        val endY = bounds.top + (bounds.height() * toFraction).toInt()
        repeat(times) {
            drag(x, startY, x, endY, DRAG_STEPS)
            waitForIdle()
        }
    }

    private const val FLING_SPEED = 5000
    private const val GESTURE_MARGIN_DIVISOR = 5
    private const val DRAG_FRACTION = 4
    private const val PAGE_SWIPE_FAR_FRACTION = 0.85
    private const val PAGE_SWIPE_NEAR_FRACTION = 0.15
}
