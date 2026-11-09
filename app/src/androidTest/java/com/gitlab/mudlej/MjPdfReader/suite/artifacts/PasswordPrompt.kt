// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object PasswordPrompt {

    fun isShowing(): Boolean = TestEnvironment.uiDevice.hasObject(By.res(ANDROID, POSITIVE_BUTTON))

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the password prompt is showing", timeoutMs) { isShowing() }
    }

    fun enter(password: String) {
        awaitShown()
        val field = Await.untilNotNull("the password prompt offers a text field") { textField() }
        TestEnvironment.onMainThread { field.setText(password) }
        TestEnvironment.waitForIdleSync()
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
        Await.until("the password prompt is dismissed", Await.LONG_TIMEOUT_MS) { !isShowing() }
    }

    private fun textField(): EditText? = runCatching {
        if (!isShowing()) return@runCatching null
        val root = Dialogs.dialogRoot()
        TestEnvironment.onMainThread { Dialogs.firstDescendant(root, EditText::class.java) }
    }.getOrNull()

    private const val ANDROID = "android"
    private const val POSITIVE_BUTTON = "button1"
}
