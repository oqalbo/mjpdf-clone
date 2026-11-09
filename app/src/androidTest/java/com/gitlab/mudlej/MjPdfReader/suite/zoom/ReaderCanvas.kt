// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import android.graphics.PointF
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.ViewConfiguration
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TouchActions
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.github.barteksc.pdfviewer.PDFView

object ReaderCanvas {

    fun rect(): Rect = TestEnvironment.onMainThread {
        val view = canvas()
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun centre(): PointF = at(0.5f, 0.5f)

    fun at(fractionX: Float, fractionY: Float): PointF {
        val bounds = rect()
        return PointF(
            bounds.left + bounds.width() * fractionX,
            bounds.top + bounds.height() * fractionY,
        )
    }

    fun toViewX(screenX: Float): Float = screenX - rect().left

    fun toViewY(screenY: Float): Float = screenY - rect().top

    fun documentX(state: ReaderState, screenX: Float): Float =
        (toViewX(screenX) - state.offsetX!!) / state.zoom!!

    fun documentY(state: ReaderState, screenY: Float): Float =
        (toViewY(screenY) - state.offsetY!!) / state.zoom!!

    fun pointInPage(state: ReaderState, fractionX: Float, fractionY: Float): PointF {
        val page = state.pageRect!!
        val bounds = rect()
        return PointF(
            bounds.left + page.left + page.width() * fractionX,
            bounds.top + page.top + page.height() * fractionY,
        )
    }

    fun placeViewportForSetup(pageIndex: Int, zoom: Float = 1f) {
        TestEnvironment.onMainThread { pdfView().debugSetZoomAtPageOrigin(pageIndex, zoom) }
    }

    fun tap(point: PointF) {
        onView(withId(R.id.pdfView)).perform(TouchActions.click(coordinates(point)))
    }

    fun doubleTap(point: PointF) {
        onView(withId(R.id.pdfView)).perform(TouchActions.doubleClick(coordinates(point)))
    }

    private fun coordinates(point: PointF) = CoordinatesProvider { floatArrayOf(point.x, point.y) }

    fun longPress(point: PointF) = perform(Tap.LONG, point)

    fun tapCentre() = tap(centre())

    fun doubleTapCentre() = doubleTap(centre())

    fun withQueuedInput(block: () -> Unit) {
        val window = TestEnvironment.onMainThread {
            TestEnvironment.resumedActivity()?.window
                ?: throw ConditionTimeoutException("No reader window is resumed")
        }
        val original = TestEnvironment.onMainThread { window.callback }
        val queuedFor = ViewConfiguration.getLongPressTimeout().toLong() * 2
        TestEnvironment.onMainThread {
            window.callback = object : Window.Callback by original {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    val properties = Array(event.pointerCount) { index ->
                        MotionEvent.PointerProperties().also { event.getPointerProperties(index, it) }
                    }
                    val coordinates = Array(event.pointerCount) { index ->
                        MotionEvent.PointerCoords().also { event.getPointerCoords(index, it) }
                    }
                    val queued = MotionEvent.obtain(
                        event.downTime - queuedFor, event.eventTime - queuedFor, event.action,
                        event.pointerCount, properties, coordinates, event.metaState, event.buttonState,
                        event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags, event.source, event.flags,
                    )
                    return try {
                        original.dispatchTouchEvent(queued)
                    } finally {
                        queued.recycle()
                    }
                }
            }
        }
        try {
            block()
        } finally {
            TestEnvironment.onMainThread { window.callback = original }
        }
    }

    private fun perform(tap: Tap, point: PointF) {
        val provider = CoordinatesProvider { floatArrayOf(point.x, point.y) }
        onView(withId(R.id.pdfView)).perform(
            GeneralClickAction(tap, provider, Press.FINGER, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.BUTTON_PRIMARY)
        )
    }

    private fun canvas(): View = pdfView()

    private fun pdfView(): PDFView {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No resumed activity while reading the reader canvas")
        return activity.findViewById(R.id.pdfView)
            ?: throw ConditionTimeoutException("The resumed activity carries no pdfView")
    }
}
