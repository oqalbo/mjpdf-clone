// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.performance

import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsDialogRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

class PerfReader(private val reader: ReaderRobot = ReaderRobot()) {

    fun activityOrNull(): MainActivity? =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity

    fun activity(): MainActivity = activityOrNull()
        ?: throw ConditionTimeoutException("No resumed reader activity")

    fun awaitReady() {
        reader.awaitReadyAtFullQuality()
    }

    fun turnPageForward() {
        TestEnvironment.uiDevice.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)
    }

    fun canvasObject(): UiObject2 = Await.untilNotNull("the page canvas is addressable by UIAutomator") {
        TestEnvironment.uiDevice.findObject(By.res(TestEnvironment.targetPackage, CANVAS_ID))
    }

    fun pinchOpen(percent: Float = PINCH_PERCENT): Long {
        val canvas = canvasObject()
        val began = SystemClock.uptimeMillis()
        canvas.pinchOpen(percent)
        return began
    }

    fun pinchClose(percent: Float = PINCH_PERCENT): Long {
        val canvas = canvasObject()
        val began = SystemClock.uptimeMillis()
        canvas.pinchClose(percent)
        return began
    }

    fun openActionsDialog() {
        Await.until("the reader actions toolbar item is on screen") {
            TestEnvironment.uiDevice.hasObject(By.res(TestEnvironment.targetPackage, ACTIONS_ITEM_ID))
        }
        onView(withId(R.id.readerActionsOption)).perform(click())
        Dialogs.awaitActionsDialog()
    }

    fun tapTile(label: String) {
        val dialog = ActionsDialogRobot()
        dialog.awaitShown()
        dialog.tap(label)
    }

    fun runAction(labelResId: Int) {
        openActionsDialog()
        tapTile(string(labelResId))
    }

    fun openGoToPageDialog() {
        runAction(R.string.go_to_page)
        Await.until("the Go To Page dialog offers a page field") { editField() != null }
    }

    fun confirmGoToPage(oneBasedPage: Int) {
        prepareGoToPage(oneBasedPage)
        confirmPreparedGoToPage()
    }

    fun prepareGoToPage(oneBasedPage: Int) {
        val field = Await.untilNotNull("the Go To Page field is present") { editField() }
        field.text = oneBasedPage.toString()
    }

    fun confirmPreparedGoToPage() {
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }

    fun goToPage(oneBasedPage: Int) {
        openGoToPageDialog()
        confirmGoToPage(oneBasedPage)
        reader.awaitPageIndex(oneBasedPage - 1, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender()
    }

    fun editField(): UiObject2? =
        TestEnvironment.uiDevice.findObject(By.res(TestEnvironment.targetPackage, EDIT_TEXT_ID))

    fun confirmDialog() {
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }

    fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    companion object {

        const val COARSE_POLL_MS = 50L
        const val PINCH_PERCENT = 0.75f
        const val CANVAS_ID = "pdfView"
        const val EDIT_TEXT_ID = "editText"
        const val ACTIONS_ITEM_ID = "readerActionsOption"
    }
}
