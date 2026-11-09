// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.hamcrest.CoreMatchers.allOf

object ShortcutBar {

    const val ABSENCE_WINDOW_MS = 1_000L

    val longPressHoldMs: Long
        get() = ViewConfiguration.getLongPressTimeout() + LONG_PRESS_MARGIN_MS

    fun isVisible(): Boolean = TestEnvironment.onMainThread {
        scrollViewOrNull()?.visibility == View.VISIBLE
    }

    fun childCount(): Int = TestEnvironment.onMainThread { layoutOrNull()?.childCount ?: 0 }

    fun labels(): List<String> = TestEnvironment.onMainThread {
        val layout = layoutOrNull() ?: return@onMainThread emptyList()
        (0 until layout.childCount).map { index ->
            layout.getChildAt(index).contentDescription?.toString()
                ?: throw ConditionTimeoutException(
                    "Shortcut bar child $index carries no content description, so it was not built from a " +
                        "configured action."
                )
        }
    }

    fun awaitLabels(expected: List<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the shortcut bar holds exactly $expected in order",
            timeoutMs,
            read = { labels() },
        ) { it == expected }
    }

    fun awaitChildCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the shortcut bar holds exactly $expected buttons",
            timeoutMs,
            read = { childCount() },
        ) { it == expected }
    }

    fun awaitVisible(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the shortcut bar is visible", timeoutMs) { isVisible() }
    }

    fun awaitHidden(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the shortcut bar leaves the screen", timeoutMs) { !isVisible() }
    }

    fun assertStaysHidden(windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the shortcut bar stays off screen",
            windowMs = windowMs,
        ) { !isVisible() }
    }

    fun hasLabel(label: String): Boolean = labels().contains(label)

    fun assertNoLabel(label: String, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the shortcut bar holds no button labelled \"$label\"",
            windowMs = windowMs,
        ) { !hasLabel(label) }
    }

    fun tap(label: String) {
        onView(
            allOf(
                withContentDescription(label),
                isDescendantOfA(withId(R.id.secondBarLayout)),
                isDisplayed(),
            )
        ).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun longPress(label: String) {
        val bounds = boundsOf(label)
        RawInput.pressAndHold(
            PointF(bounds.exactCenterX(), bounds.exactCenterY()),
            longPressHoldMs,
        )
        TestEnvironment.waitForIdleSync()
    }

    fun boundsOf(label: String): Rect = TestEnvironment.onMainThread {
        val layout = layoutOrNull()
            ?: throw ConditionTimeoutException("The reader has no shortcut bar layout")
        val child = (0 until layout.childCount)
            .map { layout.getChildAt(it) }
            .firstOrNull { it.contentDescription?.toString() == label }
            ?: throw ConditionTimeoutException(
                "The shortcut bar holds no button labelled \"$label\", it holds ${labelsOf(layout)}"
            )
        val location = IntArray(2)
        child.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + child.width, location[1] + child.height)
    }

    private fun labelsOf(layout: ViewGroup): List<String?> =
        (0 until layout.childCount).map { layout.getChildAt(it).contentDescription?.toString() }

    private fun layoutOrNull(): ViewGroup? = activityOrNull()?.findViewById(R.id.secondBarLayout)

    private fun scrollViewOrNull(): View? = activityOrNull()?.findViewById(R.id.secondBarScrollView)

    private fun activityOrNull(): MainActivity? =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity

    private const val LONG_PRESS_MARGIN_MS = 400L
}
