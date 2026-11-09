// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.view.View
import android.widget.Button
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.google.android.material.textfield.TextInputLayout
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.hamcrest.Matcher

class OnlineDocumentRobot {
    fun open() {
        SecurityReader.openAction(R.string.open_online_pdf)
        awaitShown()
    }

    fun isShown(): Boolean = Dialogs.hasDialogTitled(R.string.open_online_pdf)

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the online document dialog is shown", timeoutMs) { isShown() }
    }

    fun awaitDismissed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the online document dialog is dismissed", timeoutMs) { !isShown() }
    }

    fun enterUrl(url: String) {
        onView(withId(R.id.editText)).inRoot(isDialog()).perform(replaceText(url), closeSoftKeyboard())
        TestEnvironment.waitForIdleSync()
    }

    fun pressOpen() {
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(
            androidx.test.espresso.action.ViewActions.click()
        )
        TestEnvironment.waitForIdleSync()
    }

    fun buttonLabel(): String? = readFromDialog(android.R.id.button1) { view ->
        (view as? Button)?.text?.toString()
    }

    fun errorText(): String? = readFromDialog(R.id.editText) { view ->
        inputLayoutAbove(view)?.error?.toString()
    }

    fun awaitErrorText(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the online document dialog shows the error \"$expected\"",
            timeoutMs,
            read = { errorText() },
        ) { it == expected }
    }

    fun awaitButtonLabel(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the online document dialog button reads \"$expected\"",
            timeoutMs,
            read = { buttonLabel() },
        ) { it == expected }
    }

    fun openCleartext(url: String) {
        enterUrl(url)
        pressOpen()
        awaitButtonLabel(SecurityReader.string(R.string.proceed_anyway))
        pressOpen()
        awaitDismissed()
    }

    fun openWithoutWarning(url: String) {
        enterUrl(url)
        pressOpen()
        awaitDismissed()
    }

    private fun inputLayoutAbove(view: View): TextInputLayout? {
        var parent = view.parent
        while (parent != null) {
            if (parent is TextInputLayout) return parent
            parent = (parent as? View)?.parent
        }
        return null
    }

    private fun readFromDialog(viewId: Int, read: (View) -> String?): String? {
        var value: String? = null
        val action = object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

            override fun getDescription(): String =
                "read a value out of the online document dialog, which lives in its own window and so is " +
                    "not reachable through the reader activity's decor view"

            override fun perform(uiController: UiController, view: View) {
                value = read(view)
            }
        }
        runCatching { onView(withId(viewId)).inRoot(isDialog()).perform(action) }
        TestEnvironment.waitForIdleSync()
        return value
    }
}
