// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object FullScreenOverlay {

    const val ABSENCE_WINDOW_MS = 1_000L

    fun isColumnVisible(): Boolean = TestEnvironment.onMainThread {
        activityOrNull()?.findViewById<View>(R.id.fullScreenButtonsLayout)?.visibility == View.VISIBLE
    }

    fun visibleLabels(): List<String> = TestEnvironment.onMainThread {
        val list = listOrNull() ?: return@onMainThread emptyList()
        (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE }
            .map { button ->
                button.contentDescription?.toString()
                    ?: throw ConditionTimeoutException(
                        "A visible fullscreen overlay button carries no content description, so it is neither " +
                            "a configured action nor one of the fixed buttons."
                    )
            }
    }

    fun visibleCount(): Int = visibleLabels().size

    fun awaitColumnVisible(timeoutMs: Long = Await.SCROLL_HANDLE_TIMEOUT_MS) {
        Await.until("the fullscreen overlay button column is visible", timeoutMs) {
            isColumnVisible() && visibleCount() > 0
        }
    }

    fun awaitLabels(expected: List<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the fullscreen overlay shows exactly $expected in order",
            timeoutMs,
            read = { visibleLabels() },
        ) { it == expected }
    }

    fun hasLabel(label: String): Boolean = visibleLabels().contains(label)

    fun assertNoLabel(label: String, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the fullscreen overlay shows no button labelled \"$label\"",
            windowMs = windowMs,
        ) { !hasLabel(label) }
    }

    fun isChecked(label: String): Boolean? = TestEnvironment.onMainThread {
        (buttonOrNull(label) as? MaterialButton)?.isChecked
    }

    fun isCheckable(label: String): Boolean? = TestEnvironment.onMainThread {
        (buttonOrNull(label) as? MaterialButton)?.isCheckable
    }

    fun tap(label: String) {
        val bounds = TestEnvironment.onMainThread {
            val button = buttonOrNull(label)
                ?: throw ConditionTimeoutException(
                    "The fullscreen overlay shows no button labelled \"$label\", it shows ${visibleLabelsUnsafe()}"
                )
            val location = IntArray(2)
            button.getLocationOnScreen(location)
            intArrayOf(location[0] + button.width / 2, location[1] + button.height / 2)
        }
        TestEnvironment.uiDevice.click(bounds[0], bounds[1])
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun isBrightnessControlVisible(): Boolean = TestEnvironment.onMainThread {
        activityOrNull()?.findViewById<View>(R.id.brightnessLayout)?.visibility == View.VISIBLE
    }

    private fun buttonOrNull(label: String): View? {
        val list = listOrNull() ?: return null
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .firstOrNull { it.visibility == View.VISIBLE && it.contentDescription?.toString() == label }
    }

    private fun visibleLabelsUnsafe(): List<String?> {
        val list = listOrNull() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE }
            .map { it.contentDescription?.toString() }
    }

    private fun listOrNull(): ViewGroup? = activityOrNull()?.findViewById(R.id.fullScreenButtonsList)

    private fun activityOrNull(): MainActivity? =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
}
