// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object PasswordPrompt {

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Dialogs.awaitViewOnScreen(R.id.passwordInput, timeoutMs)
        onView(withId(R.id.passwordInput)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun unlock(password: String) {
        awaitShown()
        onView(withId(R.id.passwordInput)).inRoot(isDialog())
            .perform(replaceText(password), closeSoftKeyboard())
        onView(withText(ActionLabels.string(R.string.ok))).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }
}
