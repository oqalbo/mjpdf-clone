// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object ViewActionsDialog {

    const val MAX_SEARCH_SWIPES = 8

    fun open() {
        onView(withId(R.id.readerActionsOption)).perform(ViewActions.click())
        Await.until("the reader actions dialog is showing") { isShowing() }
    }

    fun close() {
        TestEnvironment.uiDevice.pressBack()
        Await.until("the reader actions dialog is gone") { !isShowing() }
    }

    fun isShowing(): Boolean = Dialogs.hasAnyActionTile()

    fun tileWithTitle(titleRes: Int): UiObject2? =
        TestEnvironment.uiDevice.findObject(Dialogs.actionTileSelector(ViewUi.string(titleRes)))

    fun hasTile(titleRes: Int): Boolean = tileWithTitle(titleRes) != null

    fun awaitTile(titleRes: Int): UiObject2 {
        val label = ViewUi.string(titleRes)
        return Await.untilNotNull("the actions dialog shows a tile described \"$label\"") {
            tileWithTitle(titleRes) ?: scrollToward(label).let { tileWithTitle(titleRes) }
        }
    }

    fun tap(titleRes: Int) {
        tapWithoutAwaitingClose(titleRes)
        awaitClosed()
    }

    fun tapWithoutAwaitingClose(titleRes: Int) {
        awaitTile(titleRes).click()
    }

    fun awaitClosed() {
        Await.until("the reader actions dialog closes after the tap") { !isShowing() }
    }

    fun openAndTap(titleRes: Int) {
        open()
        tap(titleRes)
    }

    fun openAndTapWithoutAwaitingClose(titleRes: Int) {
        open()
        tapWithoutAwaitingClose(titleRes)
    }

    fun assertTileTitle(message: String, presentRes: Int, absentRes: Int) {
        open()
        val present = hasTile(presentRes)
        val other = hasTile(absentRes)
        close()
        if (!present || other) {
            throw AssertionError(
                "$message: expected a tile described \"${ViewUi.string(presentRes)}\" and none described " +
                    "\"${ViewUi.string(absentRes)}\", but the expected one is present=$present " +
                    "and the other is present=$other"
            )
        }
    }

    fun assertTileAbsent(message: String, titleRes: Int) {
        open()
        val present = hasTile(titleRes)
        close()
        if (present) {
            throw AssertionError("$message: a tile described \"${ViewUi.string(titleRes)}\" is present")
        }
    }

    private fun scrollToward(label: String) {
        runCatching {
            val list = UiScrollable(UiSelector().scrollable(true))
            list.setMaxSearchSwipes(MAX_SEARCH_SWIPES)
            list.scrollIntoView(UiSelector().descriptionContains(label))
        }
        TestEnvironment.uiDevice.waitForIdle()
    }
}
