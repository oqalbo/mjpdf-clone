// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher
import org.hamcrest.Matchers.any

object HomeDialogs {

    val optionRows: List<Int> = listOf(
        R.id.statusDropdown,
        R.id.removeRecentRow,
        R.id.hideRow,
        R.id.openIncognitoRow,
        R.id.sharePdfRow,
        R.id.renameRow,
        R.id.deleteRow,
    )

    fun awaitOptionsDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Dialogs.awaitViewOnScreen(R.id.optionsTitle, timeoutMs)
    }

    fun awaitOptionsDialogGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Dialogs.awaitViewGone(R.id.optionsTitle, timeoutMs)
    }

    fun optionsTitle(): String = textOf(R.id.optionsTitle)

    fun hideRowLabel(): String = textOf(R.id.hideRowLabel)

    fun statusDropdownText(): String = textOf(R.id.statusDropdown)

    fun visibleOptionRows(): List<Int> = optionRows.filter { isShowing(it) }

    fun tapRow(@IdRes rowId: Int) {
        Dialogs.tapViewOnScreen(rowId)
    }

    fun chooseStatus(label: String) {
        onView(allOf(withId(R.id.statusDropdown), isDisplayed())).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
        Dialogs.awaitText(label)
        onView(withText(label)).inRoot(RootMatchers.isPlatformPopup()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
        Await.untilValue(
            condition = "the status dropdown acknowledges the selection \"$label\"",
            read = { statusDropdownText() },
        ) { it == label }
    }

    fun enterRenameText(text: String) {
        Await.until("the rename dialog is showing") { isShowing(R.id.renameInput) }
        onView(allOf(withId(R.id.renameInput), isDisplayed())).inRoot(RootMatchers.isDialog()).perform(ViewActions.replaceText(text))
        TestEnvironment.waitForIdleSync()
    }

    fun awaitConfirmationDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Dialogs.awaitDialogWithView(android.R.id.button1, timeoutMs)
    }

    fun awaitConfirmationDialogGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Dialogs.awaitViewGone(android.R.id.message, timeoutMs)
    }

    fun confirmationMessage(): String = textOf(android.R.id.message)

    fun dialogTitle(): String = textOf(androidx.appcompat.R.id.alertTitle)

    fun isDialogTitleShowing(): Boolean = isShowing(androidx.appcompat.R.id.alertTitle)

    fun tapPositive() {
        onView(allOf(withId(android.R.id.button1), isDisplayed())).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun tapNegative() {
        onView(allOf(withId(android.R.id.button2), isDisplayed())).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun chooseListItem(label: String) {
        Await.untilNoThrow("the list dialog offers \"$label\"") {
            onView(withText(label)).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        }
        onView(withText(label)).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun awaitStatsDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val header = string(R.string.home_stats_on_device)
        Await.untilNoThrow("the library stats dialog is showing", timeoutMs) {
            onView(withText(header)).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        }
    }

    fun statValue(@androidx.annotation.StringRes labelRes: Int): String = statValue(string(labelRes))

    fun statValue(label: String): String = capture(withText(label)) { view ->
        val row = view.parent as? ViewGroup
            ?: throw AssertionError("The stats row labelled \"$label\" has no parent row")
        val value = (0 until row.childCount)
            .mapNotNull { row.getChildAt(it) as? TextView }
            .firstOrNull { it !== view }
            ?: throw AssertionError("The stats row labelled \"$label\" carries no value view")
        value.text.toString()
    }

    fun string(@androidx.annotation.StringRes id: Int): String =
        TestEnvironment.targetContext.getString(id)

    private fun isShowing(@IdRes viewId: Int): Boolean = Dialogs.hasViewOnScreen(viewId)

    private fun textOf(@IdRes viewId: Int): String = capture(allOf(withId(viewId), isDisplayed())) { view ->
        (view as TextView).text.toString()
    }

    private fun <T> capture(matcher: Matcher<View>, read: (View) -> T): T {
        var captured: Any? = NOTHING
        onView(matcher).inRoot(RootMatchers.isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = any(View::class.java)

            override fun getDescription(): String = "read a value off the matched view"

            override fun perform(uiController: UiController, view: View) {
                captured = read(view)
            }
        })
        if (captured === NOTHING) {
            throw AssertionError("The capture action never ran against $matcher")
        }
        @Suppress("UNCHECKED_CAST")
        return captured as T
    }

    private val NOTHING = Any()
}
