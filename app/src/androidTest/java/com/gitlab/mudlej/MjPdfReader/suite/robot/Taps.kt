// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.graphics.PointF
import android.view.View
import android.view.ViewConfiguration
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object Taps {

    const val LONG_PRESS_MARGIN_MS = 250L

    fun centreOf(view: View): PointF = TestEnvironment.onMainThread {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (view.width <= 0 || view.height <= 0) {
            throw ConditionTimeoutException(
                "View ${describe(view)} has no size on screen, so it cannot be tapped"
            )
        }
        PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
    }

    fun tapCentreOf(view: View) {
        tap(centreOf(view))
    }

    fun tapCentreOfAndAwait(
        view: View,
        condition: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        predicate: () -> Boolean,
    ) {
        tapAndAwait(centreOf(view), condition, timeoutMs, predicate)
    }

    fun longPressCentreOf(view: View) {
        longPress(centreOf(view))
    }

    fun tap(point: PointF) {
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    private fun tapAndAwait(
        point: PointF,
        condition: String,
        timeoutMs: Long,
        predicate: () -> Boolean,
    ) {
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        Await.until(condition, timeoutMs, predicate = predicate)
    }

    fun longPress(point: PointF) {
        RawInput.pressAndHold(point, ViewConfiguration.getLongPressTimeout().toLong() + LONG_PRESS_MARGIN_MS)
        TestEnvironment.waitForIdleSync()
    }

    fun dragDown(from: PointF, distancePx: Float) {
        RawInput.dragAlongLine(from, PointF(from.x, from.y + distancePx))
        TestEnvironment.waitForIdleSync()
    }

    private fun describe(view: View): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(view.id) }
            .getOrDefault(view::class.java.simpleName)
}
