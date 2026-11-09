// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsDialogRobot
import com.gitlab.mudlej.MjPdfReader.suite.actions.ShortcutBar
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

class NavigationRobot(private val reader: ReaderRobot) {

    private val actionsDialog = ActionsDialogRobot()

    fun activity(): MainActivity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
        ?: throw ConditionTimeoutException("No resumed reader activity")

    fun awaitReady(): ReaderState = reader.awaitReady()

    fun pdfView(): View = TestEnvironment.onMainThread {
        activity().findViewById(R.id.pdfView)
            ?: throw ConditionTimeoutException("The reader has no pdfView")
    }

    fun tapAtFraction(xFraction: Float, yFraction: Float) {
        val bounds = ReaderRobot.pdfViewBounds()
        val x = bounds.left + (bounds.width() * xFraction).toInt()
        val y = bounds.top + (bounds.height() * yFraction).toInt()
        TestEnvironment.uiDevice.click(x, y)
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun pointAtPageFraction(xFraction: Float, yFraction: Float): android.graphics.Point =
        reader.pagePointAtFraction(xFraction, yFraction)

    fun tapAtPageFraction(xFraction: Float, yFraction: Float): android.graphics.Point =
        reader.tapAtPageFraction(xFraction, yFraction, syncMainThread = false)

    fun swipeWithinPage(
        fromXFraction: Float,
        fromYFraction: Float,
        toXFraction: Float,
        toYFraction: Float,
    ) {
        val bounds = ReaderRobot.pdfViewBounds()
        NavigationDrag.settledDrag(
            PointF(
                bounds.left + bounds.width() * fromXFraction,
                bounds.top + bounds.height() * fromYFraction,
            ),
            PointF(
                bounds.left + bounds.width() * toXFraction,
                bounds.top + bounds.height() * toYFraction,
            ),
        )
        reader.awaitMotionSettled()
    }

    fun swipeForwardWithinPage() = swipeWithinPage(0.5f, 0.8f, 0.5f, 0.2f)

    fun swipeBackwardWithinPage() = swipeWithinPage(0.5f, 0.2f, 0.5f, 0.8f)

    fun swipeUntilPageChanges(forward: Boolean, budget: Int = SWIPE_BUDGET): ReaderState {
        val start = reader.readState().page
            ?: throw ConditionTimeoutException("The reader reports no page index")
        repeat(budget) {
            if (forward) swipeForwardWithinPage() else swipeBackwardWithinPage()
            val state = reader.readState()
            val page = state.page
            if (page != null && page != start) return state
        }
        throw ConditionTimeoutException(
            "The page index stayed at $start after $budget swipes, reader state: ${reader.readState()}"
        )
    }

    fun openActionsDialog() {
        actionsDialog.open()
        awaitTileShown(string(R.string.go_to_page))
    }

    fun awaitTileShown(label: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        actionsDialog.awaitTile(label, timeoutMs)
    }

    fun tapTile(label: String) {
        actionsDialog.tap(label)
    }

    fun tileAlpha(label: String): Float = actionsDialog.tileAlpha(label)

    fun tileIsClickable(label: String): Boolean = actionsDialog.tileIsClickable(label)

    fun dismissActionsDialog() {
        actionsDialog.dismiss()
    }

    fun isTilePresent(label: String): Boolean = actionsDialog.hasTile(label)

    fun isActionsDialogShown(): Boolean = actionsDialog.isShown()

    fun goToPage(oneBasedPage: Int) {
        openActionsDialog()
        tapTile(string(R.string.go_to_page))
        enterGoToPage(oneBasedPage)
    }

    fun awaitGoToPageDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Dialogs.awaitDialogTitled(R.string.go_to_page, timeoutMs)
        onView(withId(R.id.editText)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun enterGoToPage(oneBasedPage: Int) {
        awaitGoToPageDialog()
        onView(withId(R.id.editText)).inRoot(isDialog())
            .perform(replaceText(oneBasedPage.toString()), closeSoftKeyboard())
        onView(withText(string(R.string.go_to))).inRoot(isDialog()).perform(click())
        Await.until("the Go To Page dialog is dismissed") { !isGoToPageDialogShown() }
        reader.awaitMotionSettled()
    }

    fun isGoToPageDialogShown(): Boolean = Dialogs.hasDialogTitled(R.string.go_to_page)

    fun cancelGoToPageDialog() {
        onView(withText(string(R.string.cancel))).inRoot(isDialog()).perform(click())
        Await.until("the Go To Page dialog is dismissed") { !isGoToPageDialogShown() }
    }

    fun jumpToPage(oneBasedPage: Int): ReaderState {
        goToPage(oneBasedPage)
        reader.awaitPageIndex(oneBasedPage - 1)
        return reader.awaitMotionSettled()
    }

    fun tapShortcutBarAction(label: String) {
        ShortcutBar.tap(label)
    }

    fun showReaderChrome() {
        tapAtFraction(CENTRE, CENTRE)
    }

    fun scrollHandleVisibility(): Int? = TestEnvironment.onMainThread { scrollHandleUnsafe()?.visibility }

    fun scrollHandleLabel(): String? = TestEnvironment.onMainThread {
        val label = viewDescribedAs(DefaultScrollHandle.DEBUG_HANDLE_LABEL_DESCRIPTION) as? TextView
        label?.text?.toString()
    }

    fun awaitScrollHandleVisible(timeoutMs: Long = Await.SCROLL_HANDLE_TIMEOUT_MS) {
        Await.until("the scroll handle is visible", timeoutMs) { scrollHandleVisibility() == View.VISIBLE }
    }

    fun assertScrollHandleNotVisible() {
        val visibility = scrollHandleVisibility()
        if (visibility == View.VISIBLE) {
            throw AssertionError("The scroll handle is VISIBLE while it must not be")
        }
    }

    fun scrollHandleBounds(): Rect = TestEnvironment.onMainThread {
        val handle = scrollHandleUnsafe()
            ?: throw ConditionTimeoutException("The reader has no scroll handle view")
        val location = IntArray(2)
        handle.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + handle.width, location[1] + handle.height)
    }

    fun dragScrollHandleToFraction(yFraction: Float) {
        val handle = scrollHandleBounds()
        val page = ReaderRobot.pdfViewBounds()
        TestEnvironment.uiDevice.drag(
            handle.centerX(),
            handle.centerY(),
            handle.centerX(),
            page.top + (page.height() * yFraction).toInt(),
            HANDLE_DRAG_STEPS,
        )
        reader.awaitMotionSettled()
    }

    fun tapScrollHandle() {
        val handle = scrollHandleBounds()
        TestEnvironment.uiDevice.click(handle.centerX(), handle.centerY())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun pageCounterText(): String? = TestEnvironment.onMainThread {
        activity().findViewById<TextView>(R.id.actionbarPageNumber)?.text?.toString()
    }

    fun pageCounterVisibility(): Int? = TestEnvironment.onMainThread {
        activity().findViewById<TextView>(R.id.actionbarPageNumber)?.visibility
    }

    fun awaitPageCounter(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the title bar page counter reads \"$expected\"",
            timeoutMs,
            read = { pageCounterText() },
        ) { it == expected }
    }

    fun enterFullscreen() {
        Await.untilNoThrow("the fullscreen toolbar item is tappable") {
            onView(withId(R.id.toolbarPrimaryActionOption)).perform(click())
        }
        Await.until("the fullscreen overlay column is hidden") {
            viewVisibility(R.id.fullScreenButtonsLayout) != View.VISIBLE
        }
    }

    fun awaitExitFullscreenButtonVisible(timeoutMs: Long = Await.SCROLL_HANDLE_TIMEOUT_MS) {
        Await.until("the exit fullscreen button is visible", timeoutMs) {
            viewVisibility(R.id.exitFullScreenButton) == View.VISIBLE &&
                viewVisibility(R.id.fullScreenButtonsLayout) == View.VISIBLE
        }
    }

    fun viewVisibility(viewId: Int): Int? = TestEnvironment.onMainThread {
        activity().findViewById<View>(viewId)?.visibility
    }

    private fun scrollHandleUnsafe(): View? = viewDescribedAs(DefaultScrollHandle.DEBUG_HANDLE_DESCRIPTION)

    private fun viewDescribedAs(description: String): View? =
        Dialogs.firstDescendant(activity().window.decorView, View::class.java) {
            it.contentDescription?.toString() == description
        }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    companion object {

        const val CENTRE = 0.5f
        const val DISABLED_ALPHA = 0.38f
        const val SWIPE_BUDGET = 12
        const val HANDLE_DRAG_STEPS = 40
    }
}
