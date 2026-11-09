// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.hamcrest.CoreMatchers.allOf

object JumpBackSnackbars {

    const val MESSAGE_ID = com.google.android.material.R.id.snackbar_text

    fun texts(): List<String> = TestEnvironment.onMainThread {
        val root = TestEnvironment.resumedActivity()?.window?.decorView
            ?: return@onMainThread emptyList()
        collect(root).filter { it.isShown }.map { it.text.toString() }
    }

    fun awaitText(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "a jump back snackbar reading \"$expected\" is shown",
            timeoutMs,
            read = { texts() },
        ) { it.contains(expected) }
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("no jump back snackbar remains", timeoutMs) { texts().isEmpty() }
    }

    fun holdShown(expected: String, windowMs: Long) {
        Await.assertThroughout(
            condition = "the jump back snackbar reading \"$expected\" stays on screen",
            windowMs = windowMs,
        ) { texts().contains(expected) }
    }

    fun tapMessage() {
        onView(allOf(withId(MESSAGE_ID), isDisplayed())).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun tapDone() {
        onView(
            allOf(
                withId(com.google.android.material.R.id.snackbar_action),
                isDisplayed(),
            )
        ).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun backToPage(oneBasedPage: Int): String =
        TestEnvironment.targetContext.getString(R.string.back_to_page, oneBasedPage)

    fun backToTableOfContents(): String =
        TestEnvironment.targetContext.getString(R.string.back_to_table_of_contents)

    fun requireSingle(): String = texts().singleOrNull()
        ?: throw ConditionTimeoutException(
            "Expected exactly one jump back snackbar, the window holds ${texts()}"
        )

    private fun collect(root: View): List<TextView> {
        if (root.id == MESSAGE_ID && root is TextView) return listOf(root)
        if (root !is ViewGroup) return emptyList()
        val found = mutableListOf<TextView>()
        for (index in 0 until root.childCount) {
            found += collect(root.getChildAt(index))
        }
        return found
    }
}
