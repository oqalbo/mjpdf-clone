// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import android.graphics.PointF
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object SaveActions {

    fun openActionsDialog() {
        val point = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No reader is resumed to open the actions dialog from")
            val view: View = activity.window.decorView.findViewById(R.id.readerActionsOption)
                ?: throw ConditionTimeoutException("The reader toolbar carries no actions item")
            if (!view.isShown) {
                throw ConditionTimeoutException("The reader toolbar actions item is not on screen")
            }
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
        }
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    fun runFromActionsDialog(labelRes: Int) {
        val label = AnnotationDialogs.string(labelRes)
        openActionsDialog()
        AnnotationDialogs.awaitTextShown(label)
        onView(withContentDescription(label)).inRoot(isDialog())
            .perform(ViewActions.scrollTo(), ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun tapHomeIndicator() {
        onView(withContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description))
            .perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }
}

object SavePassword {

    fun awaitPrompt(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Dialogs.awaitViewOnScreen(R.id.passwordInput, timeoutMs)
        onView(withId(R.id.passwordInput)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun enter(password: String) {
        awaitPrompt()
        onView(withId(R.id.passwordInput)).inRoot(isDialog())
            .perform(replaceText(password), closeSoftKeyboard())
        onView(withText(AnnotationDialogs.string(R.string.ok))).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }
}
