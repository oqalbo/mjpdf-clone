// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import com.gitlab.mudlej.MjPdfReader.suite.core.TouchActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

data class ToolbarItem(
    @IdRes val viewId: Int,
    val slot: String,
    val label: String?,
)

object ToolbarItems {

    const val PRIMARY = "primary"
    const val SECONDARY = "secondary"
    const val ACTIONS = "actions"

    const val ABSENCE_WINDOW_MS = 1_000L

    @IdRes
    val primaryId: Int = R.id.toolbarPrimaryActionOption

    @IdRes
    val secondaryId: Int = R.id.toolbarSecondaryActionOption

    @IdRes
    val actionsId: Int = R.id.readerActionsOption

    fun primary(): ToolbarItem? = item(primaryId, PRIMARY)

    fun secondary(): ToolbarItem? = item(secondaryId, SECONDARY)

    fun actions(): ToolbarItem? = item(actionsId, ACTIONS)

    fun item(@IdRes viewId: Int, slot: String): ToolbarItem? = TestEnvironment.onMainThread {
        val view = findShownView(viewId) ?: return@onMainThread null
        ToolbarItem(viewId, slot, labelOf(view))
    }

    fun isShown(@IdRes viewId: Int): Boolean = TestEnvironment.onMainThread { findShownView(viewId) != null }

    fun labelOf(@IdRes viewId: Int): String? = TestEnvironment.onMainThread {
        findShownView(viewId)?.let { labelOf(it) }
    }

    fun shownItems(): List<ToolbarItem> =
        listOfNotNull(secondary(), primary(), actions())

    fun shownLabels(): List<String> = shownItems().mapNotNull { it.label }

    fun awaitLabel(@IdRes viewId: Int, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the toolbar item ${resourceName(viewId)} is labelled \"$expected\"",
            timeoutMs,
            read = { labelOf(viewId) },
        ) { it == expected }
    }

    fun awaitShown(@IdRes viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the toolbar item ${resourceName(viewId)} is shown", timeoutMs) { isShown(viewId) }
    }

    fun assertStaysHidden(@IdRes viewId: Int, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the toolbar item ${resourceName(viewId)} stays out of the toolbar",
            windowMs = windowMs,
        ) { !isShown(viewId) }
    }

    fun assertNoItemLabelled(label: String, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no toolbar item is labelled \"$label\"",
            windowMs = windowMs,
        ) { shownLabels().none { it == label } }
    }

    fun tap(@IdRes viewId: Int) {
        Await.untilNoThrow("the toolbar item ${resourceName(viewId)} is tappable") {
            onView(withId(viewId)).perform(click())
        }
        TestEnvironment.waitForIdleSync()
    }

    fun tapWithRealTouch(@IdRes viewId: Int) {
        val centre = TestEnvironment.onMainThread {
            val view = findShownView(viewId)
                ?: throw ConditionTimeoutException(
                    "The toolbar item ${resourceName(viewId)} is not on screen, so it cannot be tapped"
                )
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            intArrayOf(location[0] + view.width / 2, location[1] + view.height / 2)
        }
        TestEnvironment.uiDevice.click(centre[0], centre[1])
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun tapPrimary() = tap(primaryId)

    fun tapActions() = tap(actionsId)

    private fun findShownView(@IdRes viewId: Int): View? {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: return null
        val view = activity.window.decorView.findViewById<View>(viewId) ?: return null
        return if (view.isShown) view else null
    }

    private fun labelOf(view: View): String? {
        view.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let { return it }
        return (view as? TextView)?.text?.toString()?.takeIf { it.isNotEmpty() }
            ?: throw ConditionTimeoutException(
                "The toolbar item ${resourceName(view.id)} carries neither a content description nor text, " +
                    "so its configured action cannot be read. Every configured item is labelled, so this one " +
                    "was read before the toolbar finished configuring it."
            )
    }

    private fun resourceName(@IdRes viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())
}
