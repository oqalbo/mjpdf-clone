// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await

data class SenseRow(val label: String, val definition: String, val example: String?, val synonyms: String?)

object DictionarySheet {

    fun isShown(): Boolean = SelectionViews.exists(R.id.dictionaryWordTitle)

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the dictionary definition sheet is shown", timeoutMs) { isShown() }
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the dictionary definition sheet is gone", timeoutMs) { !isShown() }
    }

    fun assertNeverShown(windowMs: Long = SelectionViews.ABSENCE_WINDOW_MS) {
        SelectionViews.assertNeverAppears(R.id.dictionaryWordTitle, windowMs)
    }

    fun title(): String = SelectionViews.read(R.id.dictionaryWordTitle) { (it as TextView).text.toString() }

    fun senses(): List<SenseRow> = SelectionViews.read(R.id.sensesContainer) { container ->
        val group = container as ViewGroup
        (0 until group.childCount).map { index ->
            val row = group.getChildAt(index)
            SenseRow(
                label = text(row, R.id.senseLabel).orEmpty(),
                definition = text(row, R.id.senseDefinition).orEmpty(),
                example = visibleText(row, R.id.senseExample),
                synonyms = visibleText(row, R.id.senseSynonyms),
            )
        }
    }

    fun tapTranslateInstead() {
        onView(withId(R.id.translateInsteadButton)).perform(ViewActions.click())
    }

    private fun text(row: View, id: Int): String? = row.findViewById<TextView>(id)?.text?.toString()

    private fun visibleText(row: View, id: Int): String? {
        val view = row.findViewById<TextView>(id) ?: return null
        return if (view.visibility == View.VISIBLE) view.text.toString() else null
    }
}
