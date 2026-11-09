// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException

class FeatureTopicRobot : Robot() {

    override fun awaitShown(timeoutMs: Long) {
        awaitDisplayed(R.id.entriesContainer, timeoutMs)
        Await.until("the entry list is bound", timeoutMs) { runCatching { entryCount() > 0 }.getOrDefault(false) }
    }

    fun entryCount(): Int = readView(R.id.entriesContainer) { (it as ViewGroup).childCount }

    fun entries(): List<Pair<String, String>> = readView(R.id.entriesContainer) { container ->
        (container as ViewGroup).children().map { row ->
            row.findViewById<TextView>(R.id.rowTitle).text.toString() to
                row.findViewById<TextView>(R.id.rowBody).text.toString()
        }
    }

    fun isAnyEntryRowDisplayed(): Boolean = isDisplayed(R.id.rowBody)

    fun bodyOf(title: String): CharSequence = readView(R.id.entriesContainer) { container ->
        (container as ViewGroup).children()
            .firstOrNull { it.findViewById<TextView>(R.id.rowTitle).text.toString() == title }
            ?.findViewById<TextView>(R.id.rowBody)
            ?.text
            ?: throw ConditionTimeoutException("No feature entry titled \"$title\" is on screen")
    }

    private fun ViewGroup.children(): List<View> = (0 until childCount).map { getChildAt(it) }
}
