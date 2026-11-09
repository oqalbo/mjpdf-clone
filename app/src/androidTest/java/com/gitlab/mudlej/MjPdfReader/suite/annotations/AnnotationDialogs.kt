// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.instanceOf

object AnnotationDialogs {

    const val ABSENCE_WINDOW_MS = 1_500L

    private const val ANDROID_PACKAGE = "android"
    private const val POSITIVE_BUTTON_NAME = "button1"

    fun string(resId: Int): String = TestEnvironment.appString(resId)

    fun awaitTitle(title: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        if (TestEnvironment.uiDevice.wait(Until.hasObject(By.text(title)), timeoutMs) != true) {
            throw ConditionTimeoutException("No dialog titled \"$title\" appeared within ${timeoutMs}ms")
        }
    }

    fun awaitTitleGone(title: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        if (TestEnvironment.uiDevice.wait(Until.gone(By.text(title)), timeoutMs) != true) {
            throw ConditionTimeoutException("A dialog titled \"$title\" was still showing after ${timeoutMs}ms")
        }
    }

    fun isTitleShown(title: String): Boolean = TestEnvironment.uiDevice.hasObject(By.text(title))

    fun assertTitleNeverShown(title: String, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no dialog titled \"$title\" appears",
            windowMs = windowMs,
        ) { !isTitleShown(title) }
    }

    fun titleText(): String = capture(withId(androidx.appcompat.R.id.alertTitle)) { (it as TextView).text.toString() }

    fun messageText(): String = capture(withId(android.R.id.message)) { (it as TextView).text.toString() }

    fun awaitTextShown(text: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Dialogs.awaitText(text, timeoutMs)
        onView(withText(text)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun isPositiveEnabled(): Boolean = capture(withId(android.R.id.button1)) { (it as Button).isEnabled }

    fun awaitPositiveEnabled(enabled: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the dialog's positive button node reports enabled $enabled",
            timeoutMs,
            read = { positiveButtonNode()?.isEnabled },
        ) { it == enabled }
        val fromView = isPositiveEnabled()
        if (fromView != enabled) {
            throw ConditionTimeoutException(
                "The accessibility node for the dialog's positive button reports enabled $enabled but the " +
                    "view behind it reports $fromView, so the node tree and the view tree disagree"
            )
        }
    }

    private fun positiveButtonNode(): UiObject2? =
        TestEnvironment.uiDevice.findObject(By.res(ANDROID_PACKAGE, POSITIVE_BUTTON_NAME))

    fun pressPositive() = pressButton(android.R.id.button1)

    fun pressNegative() = pressButton(android.R.id.button2)

    fun pressNeutral() = pressButton(android.R.id.button3)

    fun editorText(): String = capture(editorMatcher()) { (it as EditText).text.toString() }

    fun editorSelectionStart(): Int = capture(editorMatcher()) { (it as EditText).selectionStart }

    fun editorSelectionEnd(): Int = capture(editorMatcher()) { (it as EditText).selectionEnd }

    fun typeIntoEditor(text: String) {
        onView(editorMatcher()).inRoot(isDialog()).perform(ViewActions.replaceText(text))
        TestEnvironment.waitForIdleSync()
    }

    fun <T> capture(matcher: Matcher<View>, read: (View) -> T): T {
        var captured: T? = null
        var found = false
        onView(matcher).inRoot(isDialog()).perform(object : ViewAction {

            override fun getConstraints(): Matcher<View> = ViewMatchers.isAssignableFrom(View::class.java)

            override fun getDescription(): String = "read a value out of a dialog view"

            override fun perform(uiController: UiController, view: View) {
                captured = read(view)
                found = true
            }
        })
        if (!found) {
            throw ConditionTimeoutException("No dialog view matched $matcher")
        }
        @Suppress("UNCHECKED_CAST")
        return captured as T
    }

    fun editorMatcher(): Matcher<View> = allOf(instanceOf(EditText::class.java), isDisplayed())

    private fun pressButton(viewId: Int) {
        onView(withId(viewId)).inRoot(isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }
}
