// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import org.hamcrest.CoreMatchers.allOf
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.userbookmarks.UserBookmarksActivity

class BookmarksRobot {

    fun openFromReader() {
        BookmarksReader.openAction(R.string.bookmarks)
        awaitShown()
    }

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Screens.awaitResumed(UserBookmarksActivity::class.java, timeoutMs)
        Await.until("the bookmarks list is attached", timeoutMs) {
            runCatching { activity() }.isSuccess
        }
    }

    fun awaitClosed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Screens.awaitGone(UserBookmarksActivity::class.java, timeoutMs)
    }

    fun isShown(): Boolean =
        TestEnvironment.resumedActivityOfType(UserBookmarksActivity::class.java) != null

    fun activity(): UserBookmarksActivity =
        TestEnvironment.resumedActivityOfType(UserBookmarksActivity::class.java) as? UserBookmarksActivity
            ?: throw ConditionTimeoutException("No resumed bookmarks activity")

    fun title(): String? = TestEnvironment.onMainThread { activity().title?.toString() }

    fun rowCount(): Int = BookmarksRows.itemCount(activity(), R.id.userBookmarksRecyclerView)

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the bookmarks list shows $expected rows",
            timeoutMs = timeoutMs,
            read = { rowCount() },
        ) { it == expected }
    }

    fun emptyMessageShown(): Boolean = TestEnvironment.onMainThread {
        activity().findViewById<View>(R.id.message)?.visibility == View.VISIBLE
    }

    fun emptyMessageText(): String? = TestEnvironment.onMainThread {
        activity().findViewById<TextView>(R.id.message)?.text?.toString()
    }

    fun rowTitle(position: Int): String? = rowText(position, R.id.userBookmarkTitle)

    fun rowSubtitle(position: Int): String? = visibleRowText(position, R.id.userBookmarkSubtitle)

    fun rowPageInfo(position: Int): String? = visibleRowText(position, R.id.userBookmarkPageInfo)

    fun rowTopPage(position: Int): String? = visibleRowText(position, R.id.userBookmarkTopPage)

    fun rowTopTime(position: Int): String? = visibleRowText(position, R.id.userBookmarkTopTime)

    fun rowTitles(): List<String> = (0 until rowCount()).map { position ->
        BookmarksRows.scrollTo(activity(), R.id.userBookmarksRecyclerView, position)
        rowTitle(position).orEmpty()
    }

    fun awaitRowTitle(position: Int, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "bookmark row $position is titled \"$expected\"",
            timeoutMs = timeoutMs,
            read = { rowTitle(position) },
        ) { it == expected }
    }

    fun awaitRowSubtitle(position: Int, expected: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            condition = "bookmark row $position carries the subtitle \"$expected\"",
            timeoutMs = timeoutMs,
            read = { rowSubtitle(position) },
        ) { it == expected }
    }

    fun pageLabel(oneBasedPage: Int): String =
        TestEnvironment.targetContext.getString(R.string.bookmark_page_label, oneBasedPage)

    fun tapRow(position: Int) {
        tapRowChild(position, R.id.userBookmarkTitle)
    }

    fun tapRename(position: Int) {
        tapRowChild(position, R.id.userBookmarkRename)
        awaitRenameDialog()
    }

    fun tapDelete(position: Int) {
        tapRowChild(position, R.id.userBookmarkDelete)
        awaitConfirmDialog()
    }

    fun longPressRow(position: Int) {
        val bounds = rowChildBounds(position, R.id.userBookmarkTitle)
        RawInput.pressAndHold(PointF(bounds.exactCenterX(), bounds.exactCenterY()), LONG_PRESS_MS)
        TestEnvironment.waitForIdleSync()
        awaitRenameDialog()
    }

    fun dragRowOnto(fromPosition: Int, toPosition: Int) {
        val handle = rowChildBounds(fromPosition, R.id.userBookmarkDragHandle)
        val target = rowBounds(toPosition)
        val from = PointF(handle.exactCenterX(), handle.exactCenterY())
        val destinationY = when {
            fromPosition > toPosition -> target.top + 1f
            fromPosition < toPosition -> target.bottom - 1f
            else -> target.exactCenterY()
        }
        val to = PointF(handle.exactCenterX(), destinationY)
        RawInput.dragAlongLine(
            from = from,
            to = to,
            stepPx = RawInput.MAX_DRAG_STEP_PX,
            settleMoves = DRAG_SETTLE_MOVES,
        )
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    fun isRenameDialogShown(): Boolean = Dialogs.hasDialogTitled(R.string.rename_bookmark)

    fun awaitRenameDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the rename bookmark dialog is shown", timeoutMs) { isRenameDialogShown() }
    }

    fun awaitRenameDialogGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the rename bookmark dialog is dismissed", timeoutMs) { !isRenameDialogShown() }
    }

    fun typeLabel(label: String) {
        renameField().perform(replaceText(label), closeSoftKeyboard())
    }

    fun applyRename() {
        tapDialogButton(android.R.id.button1)
        awaitRenameDialogGone()
    }

    fun cancelRename() {
        tapDialogButton(android.R.id.button2)
        awaitRenameDialogGone()
    }

    fun isConfirmDialogShown(): Boolean = Dialogs.hasViewOnScreen(android.R.id.message)

    fun awaitConfirmDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the delete bookmark confirmation is shown", timeoutMs) { isConfirmDialogShown() }
    }

    fun awaitConfirmDialogGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the delete bookmark confirmation is dismissed", timeoutMs) { !isConfirmDialogShown() }
    }

    fun confirmMessage(): String = AnnotationDialogs.messageText()

    fun confirmTitle(): String = AnnotationDialogs.titleText()

    fun confirmDelete() {
        tapDialogButton(android.R.id.button1)
        awaitConfirmDialogGone()
    }

    fun cancelDelete() {
        tapDialogButton(android.R.id.button2)
        awaitConfirmDialogGone()
    }

    fun pressBack() {
        TestEnvironment.uiDevice.pressBack()
    }

    private fun renameField() = onView(
        allOf(isAssignableFrom(EditText::class.java), isDisplayed())
    ).inRoot(isDialog())

    private fun tapDialogButton(@IdRes buttonId: Int) {
        onView(withId(buttonId)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun rowText(position: Int, @IdRes viewId: Int): String? = TestEnvironment.onMainThread {
        rowChild<TextView>(position, viewId)?.text?.toString()
    }

    private fun visibleRowText(position: Int, @IdRes viewId: Int): String? = TestEnvironment.onMainThread {
        val view = rowChild<TextView>(position, viewId) ?: return@onMainThread null
        if (view.visibility == View.VISIBLE) view.text?.toString() else null
    }

    private fun <T : View> rowChild(position: Int, @IdRes viewId: Int): T? =
        BookmarksRows.itemView(activity(), R.id.userBookmarksRecyclerView, position)?.findViewById<T>(viewId)

    private fun rowBounds(position: Int): Rect = TestEnvironment.onMainThread {
        val view = BookmarksRows.itemView(activity(), R.id.userBookmarksRecyclerView, position)
            ?: throw ConditionTimeoutException("Bookmark row $position is not on screen")
        boundsOf(view)
    }

    private fun rowChildBounds(position: Int, @IdRes viewId: Int): Rect = TestEnvironment.onMainThread {
        val view = rowChild<View>(position, viewId)
            ?: throw ConditionTimeoutException(
                "Bookmark row $position has no ${BookmarksRows.resourceName(viewId)} on screen"
            )
        boundsOf(view)
    }

    private fun tapRowChild(position: Int, @IdRes viewId: Int) {
        val bounds = rowChildBounds(position, viewId)
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    private fun boundsOf(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    companion object {
        const val LONG_PRESS_MS = 900L
        const val DRAG_SETTLE_MOVES = 6
    }
}
