// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import android.view.View
import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher

class SearchRobot(private val reader: ReaderRobot = ReaderRobot()) {

    fun reader(): ReaderRobot = reader

    fun awaitReaderReady() {
        reader.awaitReadyAtFullQuality()
    }

    fun fileHash(): String = reader.awaitFileHashResolved()

    fun awaitReaderResumed(timeoutMs: Long = Await.LONG_TIMEOUT_MS): MainActivity =
        Await.untilNotNull("the reader is resumed", timeoutMs) {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
        }

    fun openReaderActionsDialog() {
        Await.until("the reader actions toolbar item is on screen") {
            TestEnvironment.uiDevice.hasObject(By.res(TestEnvironment.targetPackage, ACTIONS_ITEM_ID))
        }
        onView(withId(R.id.readerActionsOption)).perform(click())
        Await.until("the reader actions dialog offers its always present Settings tile") {
            hasTile(string(R.string.settings))
        }
    }

    fun tapReaderTile(label: String) {
        Await.until("the reader actions dialog offers a \"$label\" tile") { hasTile(label) }
        onView(allOf(withContentDescription(label), isDisplayed())).inRoot(isDialog())
            .perform(scrollTo(), click())
        TestEnvironment.waitForIdleSync()
    }

    fun runReaderAction(labelResId: Int) {
        openReaderActionsDialog()
        tapReaderTile(string(labelResId))
    }

    fun openSearchDialog() {
        openReaderActionsDialog()
        tapReaderTile(string(R.string.search))
        awaitSearchDialog()
    }

    fun awaitSearchDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the reader search dialog is shown", timeoutMs) { isSearchDialogShown() }
    }

    fun awaitSearchDialogGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the reader search dialog is dismissed", timeoutMs) { !isSearchDialogShown() }
    }

    fun isSearchDialogShown(): Boolean =
        com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs.hasDialogTitled(R.string.search)

    fun queryFieldText(): String {
        val read = arrayOf("")
        onView(withId(R.id.editText)).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(EditText::class.java)

            override fun getDescription(): String =
                "read the text the search dialog field holds, which an accessibility node cannot give, " +
                    "because an empty EditText reports its hint as its node text"

            override fun perform(uiController: UiController, view: View) {
                read[0] = (view as EditText).text?.toString().orEmpty()
            }
        })
        return read[0]
    }

    fun typeQuery(query: String) {
        val field = Await.untilNotNull("the search dialog query field is present") { queryField() }
        field.text = query
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun ignoreAccentsChecked(): Boolean = checkBox().isChecked

    fun toggleIgnoreAccents() {
        val before = ignoreAccentsChecked()
        checkBox().click()
        Await.until("the ignore accents checkbox flips to ${!before}") { ignoreAccentsChecked() != before }
    }

    fun submit() {
        tapDialogButton(android.R.id.button1)
    }

    fun submitWithoutSettling() {
        tapDialogButton(android.R.id.button1, settle = false)
    }

    fun cancel() {
        tapDialogButton(android.R.id.button2)
        awaitSearchDialogGone()
    }

    fun isTooShortQueryDialogShown(): Boolean =
        TestEnvironment.uiDevice.hasObject(By.text(string(R.string.too_short_query)))

    fun awaitTooShortQueryDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the Too Short Query dialog is shown", timeoutMs) { isTooShortQueryDialogShown() }
    }

    fun tooShortQueryMessageContains(fragment: String): Boolean =
        TestEnvironment.uiDevice.hasObject(By.textContains(fragment))

    fun tapProceedAnyway() {
        tapDialogButton(android.R.id.button3)
        Await.until("the Too Short Query dialog is dismissed") { !isTooShortQueryDialogShown() }
    }

    fun tapTooShortOk() {
        tapDialogButton(android.R.id.button1)
        Await.until("the Too Short Query dialog is dismissed") { !isTooShortQueryDialogShown() }
    }

    fun search(query: String) {
        openSearchDialog()
        typeQuery(query)
        submit()
        awaitSearchDialogGone()
    }

    fun searchWithoutSettling(query: String) {
        openSearchDialog()
        typeQuery(query)
        submitWithoutSettling()
    }

    fun searchAndProceedAnyway(query: String) {
        openSearchDialog()
        typeQuery(query)
        submit()
        awaitTooShortQueryDialog()
        tapProceedAnyway()
        awaitSearchDialogGone()
    }

    fun goToPage(oneBasedPage: Int) {
        openReaderActionsDialog()
        tapReaderTile(string(R.string.go_to_page))
        Await.until("the Go To Page dialog is shown") { isGoToPageDialogShown() }
        val field = Await.untilNotNull("the Go To Page field is present") { anyEditText() }
        field.text = oneBasedPage.toString()
        tapDialogButton(android.R.id.button1)
        Await.until("the Go To Page dialog is dismissed") { !isGoToPageDialogShown() }
        reader.awaitPageIndex(oneBasedPage - 1)
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender()
    }

    fun isGoToPageDialogShown(): Boolean =
        anyEditText() != null &&
            !TestEnvironment.uiDevice.hasObject(By.text(string(R.string.search_dialog_message)))

    fun string(resId: Int): String = TestEnvironment.appString(resId)

    private fun hasTile(label: String): Boolean =
        com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs.hasAnyActionTile()

    private fun queryField(): UiObject2? = anyEditText()

    private fun anyEditText(): UiObject2? =
        TestEnvironment.uiDevice.findObject(By.res(TestEnvironment.targetPackage, EDIT_TEXT_ID))

    private fun checkBox(): UiObject2 =
        Await.untilNotNull("the search dialog ignore accents checkbox is present") {
            TestEnvironment.uiDevice.findObject(By.textContains(IGNORE_ACCENTS_FRAGMENT).checkable(true))
        }

    private fun tapDialogButton(buttonId: Int, settle: Boolean = true) {
        onView(withId(buttonId)).inRoot(isDialog()).perform(click())
        if (!settle) {
            return
        }
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    companion object {
        const val EDIT_TEXT_ID = "editText"
        const val ACTIONS_ITEM_ID = "readerActionsOption"
        const val IGNORE_ACCENTS_FRAGMENT = "Ignore accent letters"
    }
}
