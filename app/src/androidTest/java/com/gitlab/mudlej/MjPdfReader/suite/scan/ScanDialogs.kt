// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import android.view.View
import android.widget.Checkable
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.hamcrest.CoreMatchers.allOf

object ScanDialogs {

    fun isChoiceDialogShowing(): Boolean = isShowing(R.string.home_scan_choice_title)

    fun awaitChoiceDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until(
            "the scan setup dialog titled \"${string(R.string.home_scan_choice_title)}\" is shown",
            timeoutMs,
        ) { isChoiceDialogShowing() }
    }

    fun awaitChoiceDialogGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the scan setup dialog is dismissed", timeoutMs) { !isChoiceDialogShowing() }
    }

    fun isShowing(textRes: Int): Boolean = Dialogs.isAnyShowing() && Dialogs.hasText(string(textRes))

    fun selectChoice(labelRes: Int) {
        onView(allOf(withText(string(labelRes)), isDisplayed()))
            .inRoot(isDialog())
            .perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun isChoiceChecked(labelRes: Int): Boolean {
        val label = string(labelRes)
        var checked: Boolean? = null
        onView(allOf(withText(label), isDisplayed()))
            .inRoot(isDialog())
            .check { view, error ->
                if (error != null) throw error
                checked = checkedStateOf(view)
            }
        return checked ?: throw ConditionTimeoutException(
            "The dialog row \"$label\" is not a Checkable, so its selected state cannot be read. " +
                "ScanSetupDialog builds its list with setSingleChoiceItems, which renders CheckedTextView rows."
        )
    }

    fun confirm() {
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
        awaitChoiceDialogGone()
    }

    private fun checkedStateOf(view: View): Boolean? {
        if (view is Checkable) return view.isChecked
        var parent = view.parent
        var depth = 0
        while (parent is View && depth < ANCESTOR_DEPTH) {
            if (parent is Checkable) return (parent as Checkable).isChecked
            parent = parent.parent
            depth++
        }
        return null
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private const val ANCESTOR_DEPTH = 4
}
