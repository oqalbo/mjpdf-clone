// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.espresso.action.ViewActions
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

class WhatsNewRobot : Robot() {

    override fun awaitShown(timeoutMs: Long) {
        awaitDisplayed(R.id.sectionsContainer, timeoutMs)
        Await.until("the change list is bound", timeoutMs) { runCatching { sectionCount() > 0 }.getOrDefault(false) }
    }

    fun versionChipText(): String = readView(R.id.versionChip) { (it as TextView).text.toString() }

    fun sectionCount(): Int = readView(R.id.sectionsContainer) { (it as ViewGroup).childCount }

    fun sectionTitles(): List<String> = readView(R.id.sectionsContainer) { container ->
        (container as ViewGroup).children().map {
            it.findViewById<TextView>(R.id.sectionTitle).text.toString()
        }
    }

    fun rowCountsPerSection(): List<Int> = readView(R.id.sectionsContainer) { container ->
        (container as ViewGroup).children().map {
            it.findViewById<ViewGroup>(R.id.sectionRows).childCount
        }
    }

    fun rows(): List<Pair<String, String>> = readView(R.id.sectionsContainer) { container ->
        (container as ViewGroup).children().flatMap { section ->
            section.findViewById<ViewGroup>(R.id.sectionRows).children().map { row ->
                row.findViewById<TextView>(R.id.rowTitle).text.toString() to
                    row.findViewById<TextView>(R.id.rowBody).text.toString()
            }
        }
    }

    fun tapIntroCard(): WhatsNewRobot {
        bringIntoView(R.id.introCard)
        view(R.id.introCard).perform(ViewActions.click())
        waitForIdle()
        return this
    }

    fun isIntroCardDisplayed(): Boolean = isDisplayed(R.id.introCard)

    fun scrollToBottom(): WhatsNewRobot {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivity() ?: return@onMainThread
            val scroll = activity.findViewById<View>(R.id.sectionsContainer)?.parent?.parent
            if (scroll is android.widget.ScrollView) scroll.fullScroll(View.FOCUS_DOWN)
        }
        waitForIdle()
        return this
    }

    fun pressUp(): WhatsNewRobot {
        AppBar.pressUp()
        waitForIdle()
        return this
    }

    private fun ViewGroup.children(): List<View> = (0 until childCount).map { getChildAt(it) }
}
