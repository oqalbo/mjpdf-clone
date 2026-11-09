// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.state.LayoutDirection
import com.gitlab.mudlej.MjPdfReader.suite.state.RootDescriptionParser

object ScreenDirection {

    fun rootOf(activity: Activity): View {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: throw ConditionTimeoutException("${activity.javaClass.simpleName} has no content view")
        if (content.childCount == 0) {
            throw ConditionTimeoutException("${activity.javaClass.simpleName} has no root under its content view")
        }
        return content.getChildAt(0)
    }

    fun rawDescription(type: Class<out Activity>): String? = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(type) ?: return@onMainThread null
        rootOf(activity).contentDescription?.toString()
    }

    fun of(type: Class<out Activity>): LayoutDirection? =
        rawDescription(type)
            ?.takeIf { it.isNotEmpty() }
            ?.let { RootDescriptionParser.parse(it).layoutDirection }

    fun await(type: Class<out Activity>, expected: LayoutDirection, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "${type.simpleName} reports layout direction ${expected.token}",
            timeoutMs,
            read = { runCatching { of(type) }.getOrNull() },
        ) { it == expected }
    }

    fun require(type: Class<out Activity>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): LayoutDirection =
        Await.untilNotNull(
            "${type.simpleName} publishes its resolved layout direction. The group is attached on a layout " +
                "change rather than in onCreate, so a read taken immediately after launch sees nothing.",
            timeoutMs,
        ) { runCatching { of(type) }.getOrNull() }

    fun boundsOnScreen(view: View): Rect = TestEnvironment.onMainThread {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun boundsOf(type: Class<out Activity>, @IdRes viewId: Int): Rect {
        val view = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(type)
                ?: throw ConditionTimeoutException("${type.simpleName} is not resumed")
            activity.window.decorView.findViewById<View>(viewId)
                ?: throw ConditionTimeoutException(
                    "${type.simpleName} carries no view ${LocalizedStrings.nameOf(viewId)} to measure"
                )
        }
        return boundsOnScreen(view)
    }

    fun screenWidth(): Int = TestEnvironment.uiDevice.displayWidth

    fun isInLeftHalf(bounds: Rect): Boolean = bounds.centerX() < screenWidth() / 2
}
