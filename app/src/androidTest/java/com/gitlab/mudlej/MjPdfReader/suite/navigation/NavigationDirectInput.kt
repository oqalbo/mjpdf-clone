// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object NavigationDirectInput {

    const val CANCELLED_DRAG_STEPS = 24

    fun dispatchMouseButton(activity: Activity, actionButton: Int): Boolean =
        TestEnvironment.onMainThread {
            val press = mouseButtonEvent(MotionEvent.ACTION_BUTTON_PRESS, actionButton)
            try {
                activity.dispatchGenericMotionEvent(press)
            } finally {
                press.recycle()
            }
        }

    fun dispatchMouseKey(activity: Activity, keyCode: Int): Boolean =
        TestEnvironment.onMainThread {
            val now = SystemClock.uptimeMillis()
            val event = KeyEvent(
                now,
                now,
                KeyEvent.ACTION_DOWN,
                keyCode,
                0,
                0,
                VIRTUAL_DEVICE_ID,
                0,
                0,
                InputDevice.SOURCE_MOUSE,
            )
            activity.dispatchKeyEvent(event)
        }

    fun dispatchCancelledDrag(target: View, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val downTime = SystemClock.uptimeMillis()
        send(target, downTime, downTime, MotionEvent.ACTION_DOWN, fromX, fromY)
        for (step in 1..CANCELLED_DRAG_STEPS) {
            val progress = step.toFloat() / CANCELLED_DRAG_STEPS
            send(
                target,
                downTime,
                downTime + step * MOVE_INTERVAL_MS,
                MotionEvent.ACTION_MOVE,
                fromX + (toX - fromX) * progress,
                fromY + (toY - fromY) * progress,
            )
            SystemClock.sleep(MOVE_INTERVAL_MS)
        }
        send(
            target,
            downTime,
            downTime + (CANCELLED_DRAG_STEPS + 1) * MOVE_INTERVAL_MS,
            MotionEvent.ACTION_CANCEL,
            toX,
            toY,
        )
    }

    private fun send(target: View, downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
        TestEnvironment.onMainThread {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                target.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun mouseButtonEvent(action: Int, actionButton: Int): MotionEvent {
        val now = SystemClock.uptimeMillis()
        val pointerProperties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )
        val pointerCoords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = 0f
                y = 0f
                pressure = 1f
                size = 1f
            }
        )
        val buttonState = when (actionButton) {
            MotionEvent.BUTTON_FORWARD -> MotionEvent.BUTTON_FORWARD
            MotionEvent.BUTTON_BACK -> MotionEvent.BUTTON_BACK
            else -> throw ConditionTimeoutException("Unsupported mouse action button $actionButton")
        }
        val event = MotionEvent.obtain(
            now,
            now,
            action,
            1,
            pointerProperties,
            pointerCoords,
            0,
            if (action == MotionEvent.ACTION_BUTTON_PRESS) buttonState else 0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
        setActionButton(event, actionButton)
        return event
    }

    private fun setActionButton(event: MotionEvent, actionButton: Int) {
        MotionEvent::class.java
            .getMethod("setActionButton", Int::class.javaPrimitiveType)
            .invoke(event, actionButton)
    }

    private const val MOVE_INTERVAL_MS = 16L
    private const val VIRTUAL_DEVICE_ID = -1
}
