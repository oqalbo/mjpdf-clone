// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.app.Activity
import android.content.Intent
import android.graphics.PointF
import android.view.View
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage

object ActionsSettings {

    fun customizeControlsIntent(): Intent = pageIntent(SettingsPage.CUSTOMIZE_CONTROLS)

    fun pageIntent(page: SettingsPage): Intent =
        Intent(TestEnvironment.targetContext, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_PAGE, page.name)
        }

    fun activityOrNull(): Activity? = TestEnvironment.resumedActivityOfType(SettingsActivity::class.java)

    fun activity(): Activity = activityOrNull()
        ?: throw ConditionTimeoutException("No settings screen is resumed")

    fun awaitResumed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Activity =
        Await.untilNotNull("the settings screen is resumed", timeoutMs) { activityOrNull() }

    fun hasRow(title: String): Boolean = TestEnvironment.onMainThread { rowTitleView(title) != null }

    fun awaitRow(title: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the settings page shows a row titled \"$title\"", timeoutMs) { hasRow(title) }
    }

    fun openRow(title: String) {
        awaitRow(title)
        val point = TestEnvironment.onMainThread {
            val label = rowTitleView(title)
                ?: throw ConditionTimeoutException("The settings page shows no row titled \"$title\"")
            centreOf(clickableAncestorOf(label))
        }
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    fun rowSummary(title: String): String? = TestEnvironment.onMainThread {
        val label = rowTitleView(title) ?: return@onMainThread null
        val container = clickableAncestorOf(label)
        container.findViewById<TextView>(android.R.id.summary)?.text?.toString()
    }

    fun awaitRowSummaryContains(title: String, fragment: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the \"$title\" row summary contains \"$fragment\"",
            timeoutMs,
            read = { rowSummary(title) },
        ) { it != null && it.contains(fragment) }
    }

    private fun rowTitleView(title: String): View? {
        val activity = activityOrNull() ?: return null
        return Dialogs.firstDescendant(activity.window.decorView, TextView::class.java) {
            it.isShown && it.text?.toString() == title
        }
    }

    private fun clickableAncestorOf(view: View): View {
        var current: View = view
        repeat(ANCESTOR_DEPTH) {
            if (current.isClickable) return current
            current = current.parent as? View ?: return current
        }
        return current
    }

    private fun centreOf(view: View): PointF {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (view.width <= 0 || view.height <= 0) {
            throw ConditionTimeoutException("A settings row has no size on screen, so it cannot be tapped")
        }
        return PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
    }

    private const val ANCESTOR_DEPTH = 6
}
