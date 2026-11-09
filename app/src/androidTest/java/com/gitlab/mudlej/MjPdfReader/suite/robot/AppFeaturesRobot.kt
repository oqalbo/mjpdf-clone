// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import org.hamcrest.CoreMatchers.allOf

class AppFeaturesRobot : Robot() {

    override fun awaitShown(timeoutMs: Long) {
        awaitDisplayed(R.id.topicsContainer, timeoutMs)
        Await.until("the topic list is bound", timeoutMs) { runCatching { topicCount() > 0 }.getOrDefault(false) }
    }

    fun topicCount(): Int = readView(R.id.topicsContainer) { (it as ViewGroup).childCount }

    fun topicTitles(): List<String> = readView(R.id.topicsContainer) { container ->
        (container as ViewGroup).children().map { it.findViewById<TextView>(R.id.rowTitle).text.toString() }
    }

    fun topicSubtitles(): List<Pair<String, Int>> = readView(R.id.topicsContainer) { container ->
        (container as ViewGroup).children().map {
            val subtitle = it.findViewById<TextView>(R.id.rowSubtitle)
            subtitle.text.toString() to subtitle.visibility
        }
    }

    fun openTopic(title: String): AppFeaturesRobot {
        bringIntoView { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.topicsContainer)
            container.children().first { it.findViewById<TextView>(R.id.rowTitle).text.toString() == title }
        }
        onView(
            allOf(
                withParent(withId(R.id.topicsContainer)),
                hasDescendant(allOf(withId(R.id.rowTitle), withText(title))),
            )
        ).perform(ViewActions.click())
        waitForIdle()
        return this
    }

    private fun ViewGroup.children(): List<View> = (0 until childCount).map { getChildAt(it) }
}
