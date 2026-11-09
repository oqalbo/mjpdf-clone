// Written by Mudlej. License is GPLv3.
package com.gitlab.mudlej.MjPdfReader.suite.core

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import org.hamcrest.Matcher

object TouchActions {
    fun click(): ViewAction = taps(GeneralLocation.CENTER, 1)

    fun doubleClick(coordinates: CoordinatesProvider): ViewAction = taps(coordinates, 2)

    fun click(coordinates: CoordinatesProvider): ViewAction = taps(coordinates, 1)

    private fun taps(coordinates: CoordinatesProvider, count: Int): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayingAtLeast(90)

        override fun getDescription(): String = "dispatch $count complete taps through the view's window"

        override fun perform(uiController: UiController, view: View) {
            val root = view.rootView
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            val point = coordinates.calculateCoordinates(view)
            val pointer = MotionEvent.PointerCoords().apply {
                x = point[0] - location[0]
                y = point[1] - location[1]
                pressure = 1f
                size = 1f
            }
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            val interval = ViewConfiguration.getDoubleTapTimeout().toLong() / 2
            val duration = minOf(ViewConfiguration.getTapTimeout().toLong() / 4, interval / 4)
            val start = SystemClock.uptimeMillis() - (count - 1) * interval - duration
            repeat(count) { index ->
                val downTime = start + index * interval
                for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(
                        downTime, downTime + if (action == MotionEvent.ACTION_UP) duration else 0,
                        action, 1, arrayOf(properties), arrayOf(pointer), 0, MotionEvent.BUTTON_PRIMARY,
                        1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
                    )
                    try {
                        root.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
            }
            uiController.loopMainThreadUntilIdle()
        }
    }
}
