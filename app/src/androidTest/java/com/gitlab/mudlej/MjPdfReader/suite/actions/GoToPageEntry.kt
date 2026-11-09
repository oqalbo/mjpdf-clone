// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.google.android.material.textfield.TextInputLayout

object GoToPageEntry {

    private val HINT_PATTERN = Regex("""(\d+)\s*/\s*(\d+)""")

    fun isShown(): Boolean = Dialogs.hasDialogTitled(R.string.go_to_page)

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the go to page entry dialog is shown", timeoutMs) { isShown() }
    }

    fun awaitDismissed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the go to page entry dialog is dismissed", timeoutMs) { !isShown() }
    }

    fun hint(): String {
        var captured: String? = null
        onView(withId(R.id.inputLayout)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            captured = TestEnvironment.onMainThread { (view as? TextInputLayout)?.hint?.toString() }
        }
        return captured ?: throw ConditionTimeoutException("The go to page entry carries no hint")
    }

    fun goTo(oneBasedPage: Int) {
        awaitShown()
        onView(withId(R.id.editText)).inRoot(isDialog()).perform(
            androidx.test.espresso.action.ViewActions.replaceText(oneBasedPage.toString()),
            androidx.test.espresso.action.ViewActions.closeSoftKeyboard(),
        )
        onView(androidx.test.espresso.matcher.ViewMatchers.withText(ActionLabels.string(R.string.go_to)))
            .inRoot(isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        awaitDismissed()
    }

    fun currentEntryPage(): Int = parsed().first

    fun pageCount(): Int = parsed().second

    private fun parsed(): Pair<Int, Int> {
        val hint = hint()
        val match = HINT_PATTERN.find(hint)
            ?: throw AssertionError(
                "The go to page hint reads \"$hint\" and carries no \"current/total\" pair, which is what " +
                    "showGoToPageDialog writes from \"${TestEnvironment.appString(R.string.go_to_page_current_hint)}\""
            )
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }
}
