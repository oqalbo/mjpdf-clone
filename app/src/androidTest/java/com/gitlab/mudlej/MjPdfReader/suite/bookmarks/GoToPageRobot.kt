// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.gotopage.GoToPageActivity

class GoToPageRobot {

    fun openDialogFromReader() {
        BookmarksReader.openAction(R.string.go_to_page)
        awaitDialog()
    }

    fun isDialogShown(): Boolean = Dialogs.hasDialogTitled(R.string.go_to_page)

    fun awaitDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the Go To Page dialog is shown", timeoutMs) { isDialogShown() }
    }

    fun awaitDialogGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the Go To Page dialog is dismissed", timeoutMs) { !isDialogShown() }
    }

    fun dialogTitle(): String = AnnotationDialogs.titleText()

    fun hintText(): String? {
        val layout = dialogView<TextInputLayout>(R.id.inputLayout)
        return TestEnvironment.onMainThread { layout.hint?.toString() }
    }

    fun hintFor(oneBasedPage: Int, pageCount: Int): String =
        TestEnvironment.appString(R.string.go_to_page_current_hint, oneBasedPage, pageCount)

    fun type(text: String) {
        onView(withId(R.id.editText)).inRoot(isDialog()).perform(replaceText(text), closeSoftKeyboard())
        TestEnvironment.waitForIdleSync()
    }

    fun typeAsciiThroughKeyboard(text: String) {
        text.forEach { asciiKeyCode(it) }
        onView(withId(R.id.editText)).inRoot(isDialog()).perform(click())
        text.forEach { character ->
            val keyCode = asciiKeyCode(character)
            check(TestEnvironment.uiDevice.pressKeyCode(keyCode)) {
                "UiDevice refused key code $keyCode for '$character'"
            }
        }
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    private fun asciiKeyCode(character: Char): Int =
        when (character) {
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (character - '0')
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (character - 'a')
            in 'A'..'Z' -> KeyEvent.KEYCODE_A + (character - 'A')
            '.' -> KeyEvent.KEYCODE_PERIOD
            else -> throw IllegalArgumentException(
                "Only ASCII digits, letters, and period can be injected; got '$character'",
            )
        }

    fun clearField() {
        type("")
    }

    fun fieldText(): String {
        val field = dialogView<TextView>(R.id.editText)
        return TestEnvironment.onMainThread { field.text?.toString().orEmpty() }
    }

    fun isGoToEnabled(): Boolean = AnnotationDialogs.isPositiveEnabled()

    fun awaitGoToEnabled(
        enabled: Boolean,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ) {
        AnnotationDialogs.awaitPositiveEnabled(enabled, timeoutMs)
    }

    fun tapGoTo() {
        tapDialogButton(android.R.id.button1)
    }

    fun tapCancel() {
        tapDialogButton(android.R.id.button2)
        awaitDialogGone()
    }

    fun tapAllPages() {
        tapDialogButton(android.R.id.button3)
        awaitDialogGone()
    }

    fun isAllPagesShown(): Boolean = Dialogs.hasViewOnScreen(android.R.id.button3)

    fun strip(): RecyclerView = dialogView(R.id.thumbnailStrip)

    fun awaitStripSettled(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        val strip = strip()
        Await.until("the thumbnail strip stops scrolling", timeoutMs) {
            TestEnvironment.onMainThread { strip.scrollState } == RecyclerView.SCROLL_STATE_IDLE
        }
    }

    fun stripFirstVisiblePosition(): Int = onStrip { strip ->
        (strip.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
    }

    fun stripLastVisiblePosition(): Int = onStrip { strip ->
        (strip.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
    }

    fun awaitStripCellVisible(pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("strip cell for page index $pageIndex is on screen", timeoutMs) {
            pageIndex in stripFirstVisiblePosition()..stripLastVisiblePosition()
        }
    }

    fun stripCellPageNumber(pageIndex: Int): String? = onStrip { strip ->
        stripCellChild<TextView>(strip, pageIndex, R.id.pageNumber)?.text?.toString()
    }

    fun stripCellHasThumbnail(pageIndex: Int): Boolean = onStrip { strip ->
        stripCellChild<ImageView>(strip, pageIndex, R.id.pageImage)?.drawable != null
    }

    fun awaitStripThumbnail(pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("strip cell for page index $pageIndex has a rendered thumbnail", timeoutMs) {
            stripCellHasThumbnail(pageIndex)
        }
    }

    fun stripCellIsCurrentPage(pageIndex: Int): Boolean = onStrip { strip ->
        val card = stripCellChild<MaterialCardView>(strip, pageIndex, R.id.pageCard) ?: return@onStrip false
        card.strokeWidth >= currentPageStrokeWidth()
    }

    fun tapStripCell(pageIndex: Int) {
        val bounds = onStrip { strip ->
            val card = stripCellChild<View>(strip, pageIndex, R.id.pageCard)
                ?: throw ConditionTimeoutException("Strip cell for page index $pageIndex is not on screen")
            boundsOf(card)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    fun awaitGridShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Screens.awaitResumed(GoToPageActivity::class.java, timeoutMs)
        Await.until("the grid progress bar is gone", timeoutMs) {
            TestEnvironment.onMainThread {
                gridActivity().findViewById<View>(R.id.progressBar)?.visibility != View.VISIBLE
            }
        }
        Await.until("the grid is laid out with a stable visible range", timeoutMs) {
            TestEnvironment.onMainThread {
                val grid = grid()
                val layoutManager = grid.layoutManager as? GridLayoutManager
                val first = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                val last = layoutManager?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
                grid.isLaidOut &&
                    !grid.isLayoutRequested &&
                    grid.width > 0 &&
                    grid.height > 0 &&
                    (grid.adapter?.itemCount ?: 0) > 0 &&
                    grid.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
                    first != RecyclerView.NO_POSITION &&
                    last != RecyclerView.NO_POSITION
            }
        }
        var observedRange: Pair<Int, Int>? = null
        Await.untilTrueContinuously(
            condition = "the grid visible range stops changing after posted restoration",
            stableForMs = GRID_RANGE_STABLE_MS,
            timeoutMs = timeoutMs,
        ) {
            val range = TestEnvironment.onMainThread {
                val grid = grid()
                val layoutManager = grid.layoutManager as? GridLayoutManager
                val first = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                val last = layoutManager?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
                if (
                    grid.isLaidOut &&
                    !grid.isLayoutRequested &&
                    grid.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
                    first != RecyclerView.NO_POSITION &&
                    last != RecyclerView.NO_POSITION
                ) {
                    first to last
                } else {
                    null
                }
            }
            if (range == null) {
                observedRange = null
                false
            } else if (range == observedRange) {
                true
            } else {
                observedRange = range
                false
            }
        }
    }

    fun awaitGridClosed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Screens.awaitGone(GoToPageActivity::class.java, timeoutMs)
    }

    fun isGridShown(): Boolean = TestEnvironment.resumedActivityOfType(GoToPageActivity::class.java) != null

    fun isGridInAnyStage(): Boolean = Screens.isInTask(GoToPageActivity::class.java)

    fun gridActivity(): GoToPageActivity =
        TestEnvironment.resumedActivityOfType(GoToPageActivity::class.java) as? GoToPageActivity
            ?: throw ConditionTimeoutException("No resumed go to page grid activity")

    fun gridTitle(): String? = TestEnvironment.onMainThread { gridActivity().title?.toString() }

    fun spanCount(): Int = TestEnvironment.onMainThread {
        (grid().layoutManager as GridLayoutManager).spanCount
    }

    fun awaitSpanCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the page grid lays out $expected cells per row",
            timeoutMs = timeoutMs,
            read = { spanCount() },
        ) { it == expected }
    }

    fun cellCount(): Int = TestEnvironment.onMainThread { grid().adapter?.itemCount ?: 0 }

    fun gridFirstVisiblePosition(): Int = TestEnvironment.onMainThread {
        (grid().layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
    }

    fun gridLastVisiblePosition(): Int = TestEnvironment.onMainThread {
        (grid().layoutManager as GridLayoutManager).findLastVisibleItemPosition()
    }

    fun gridCanScrollVertically(): Boolean = TestEnvironment.onMainThread {
        val grid = grid()
        grid.canScrollVertically(-1) || grid.canScrollVertically(1)
    }

    fun scrollGridTo(pageIndex: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        Await.until("grid cell for page index $pageIndex is visible", timeoutMs) {
            requestGridPosition(pageIndex, requireBoundHolder = false)
        }
        val remainingMs = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(1L)
        Await.until("grid cell for page index $pageIndex is bound", remainingMs) {
            requestGridPosition(pageIndex, requireBoundHolder = true)
        }
    }

    fun awaitGridCellVisible(pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("grid cell for page index $pageIndex is on screen", timeoutMs) {
            pageIndex in gridFirstVisiblePosition()..gridLastVisiblePosition()
        }
    }

    fun gridCellPageNumber(pageIndex: Int): String? = TestEnvironment.onMainThread {
        gridCellChild<TextView>(pageIndex, R.id.pageNumber)?.text?.toString()
    }

    fun gridCellHasThumbnail(pageIndex: Int): Boolean = TestEnvironment.onMainThread {
        gridCellChild<ImageView>(pageIndex, R.id.pageImage)?.drawable != null
    }

    fun gridCellThumbnailWidth(pageIndex: Int): Int = TestEnvironment.onMainThread {
        gridCellChild<ImageView>(pageIndex, R.id.pageImage)?.drawable?.intrinsicWidth ?: -1
    }

    fun awaitGridThumbnailWiderThan(
        pageIndex: Int,
        width: Int,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ) {
        Await.untilValue(
            condition = "grid cell for page index $pageIndex is re-rendered wider than $width pixels",
            timeoutMs = timeoutMs,
            read = { gridCellThumbnailWidth(pageIndex) },
        ) { it > width }
    }

    fun awaitGridThumbnail(pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("grid cell for page index $pageIndex has a rendered thumbnail", timeoutMs) {
            gridCellHasThumbnail(pageIndex)
        }
    }

    fun gridCellStrokeWidth(pageIndex: Int): Int = TestEnvironment.onMainThread {
        gridCellChild<MaterialCardView>(pageIndex, R.id.pageCard)?.strokeWidth
            ?: throw ConditionTimeoutException("Grid cell for page index $pageIndex is not on screen")
    }

    fun gridCellIsCurrentPage(pageIndex: Int): Boolean =
        gridCellStrokeWidth(pageIndex) >= currentPageStrokeWidth()

    fun tapGridCell(pageIndex: Int) {
        val bounds = TestEnvironment.onMainThread {
            val card = gridCellChild<View>(pageIndex, R.id.pageCard)
                ?: throw ConditionTimeoutException("Grid cell for page index $pageIndex is not on screen")
            boundsOf(card)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    fun sliderValue(): Float = TestEnvironment.onMainThread { slider().value }

    fun dragSliderToMinimum() = dragSlider(toMaximum = false)

    fun moveSliderWithoutRelease(toMaximum: Boolean): Long {
        val bounds = TestEnvironment.onMainThread { boundsOf(slider()) }
        val y = bounds.exactCenterY()
        val from = PointF(bounds.exactCenterX(), y)
        val to = PointF(if (toMaximum) bounds.right - EDGE_INSET_PX else bounds.left + EDGE_INSET_PX, y)
        val downTime = RawInput.down(from)
        RawInput.pointsAlong(from, to, RawInput.MAX_DRAG_STEP_PX).forEach { point ->
            RawInput.move(downTime, point)
        }
        TestEnvironment.waitForIdleSync()
        return downTime
    }

    fun releaseSlider(downTime: Long, toMaximum: Boolean) {
        val bounds = TestEnvironment.onMainThread { boundsOf(slider()) }
        val y = bounds.exactCenterY()
        val to = PointF(if (toMaximum) bounds.right - EDGE_INSET_PX else bounds.left + EDGE_INSET_PX, y)
        RawInput.up(downTime, to)
        TestEnvironment.waitForIdleSync()
    }

    fun scrollThumbAlpha(): Float = TestEnvironment.onMainThread {
        gridActivity().findViewById<View>(R.id.scrollHandleThumb)?.alpha ?: 0f
    }

    fun scrollThumbVisible(): Boolean = scrollThumbAlpha() > 0f

    fun awaitScrollThumbHidden(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the grid scroll thumb fades out", timeoutMs) { !scrollThumbVisible() }
    }

    fun scrollThumbBounds(): Rect = TestEnvironment.onMainThread {
        val thumb = gridActivity().findViewById<View>(R.id.scrollHandleThumb)
            ?: throw ConditionTimeoutException("The grid has no scroll thumb")
        boundsOf(thumb)
    }

    fun dragScrollThumbToBottom() {
        val thumb = scrollThumbBounds()
        val gridBounds = TestEnvironment.onMainThread { boundsOf(grid()) }
        RawInput.dragAlongLine(
            from = PointF(thumb.exactCenterX(), thumb.exactCenterY()),
            to = PointF(thumb.exactCenterX(), gridBounds.bottom - EDGE_INSET_PX),
            stepPx = RawInput.COARSE_DRAG_STEP_PX,
            settleMoves = RawInput.SETTLE_MOVES_BEFORE_RELEASE,
        )
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    fun flingGridDown() {
        val bounds = TestEnvironment.onMainThread { boundsOf(grid()) }
        TestEnvironment.uiDevice.swipe(
            bounds.centerX(),
            bounds.top + (bounds.height() * FLING_FAR_FRACTION).toInt(),
            bounds.centerX(),
            bounds.top + (bounds.height() * FLING_NEAR_FRACTION).toInt(),
            FLING_STEPS,
        )
        awaitGridScrollIdle()
    }

    fun awaitGridScrollIdle(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the page grid stops scrolling", timeoutMs) {
            TestEnvironment.onMainThread { grid().scrollState } == RecyclerView.SCROLL_STATE_IDLE
        }
    }

    fun pressBack() {
        TestEnvironment.uiDevice.pressBack()
    }

    private fun dragSlider(toMaximum: Boolean) {
        val downTime = moveSliderWithoutRelease(toMaximum)
        releaseSlider(downTime, toMaximum)
    }

    private fun slider(): Slider = gridActivity().findViewById(R.id.sizeSlider)
        ?: throw ConditionTimeoutException("The grid has no columns slider")

    private fun requestGridPosition(
        pageIndex: Int,
        requireBoundHolder: Boolean,
    ): Boolean = TestEnvironment.onMainThread {
        val grid = grid()
        val layoutManager = grid.layoutManager as? GridLayoutManager
            ?: throw ConditionTimeoutException("The page grid has no GridLayoutManager")
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (
            first == RecyclerView.NO_POSITION ||
            last == RecyclerView.NO_POSITION ||
            pageIndex !in first..last
        ) {
            grid.scrollToPosition(pageIndex)
            false
        } else {
            !requireBoundHolder || grid.findViewHolderForAdapterPosition(pageIndex)?.itemView != null
        }
    }

    private fun grid(): RecyclerView = gridActivity().findViewById(R.id.pageGrid)
        ?: throw ConditionTimeoutException("The grid has no page list")

    private fun <T : View> gridCellChild(pageIndex: Int, @IdRes viewId: Int): T? =
        grid().findViewHolderForAdapterPosition(pageIndex)?.itemView?.findViewById<T>(viewId)

    private fun <T> onStrip(read: (RecyclerView) -> T): T {
        val strip = strip()
        return TestEnvironment.onMainThread { read(strip) }
    }

    private fun <T : View> stripCellChild(strip: RecyclerView, pageIndex: Int, @IdRes viewId: Int): T? =
        strip.findViewHolderForAdapterPosition(pageIndex)?.itemView?.findViewById<T>(viewId)

    private fun <T : View> dialogView(@IdRes viewId: Int): T {
        val captured = AnnotationDialogs.capture(withId(viewId)) { it }
        @Suppress("UNCHECKED_CAST")
        return captured as? T
            ?: throw ConditionTimeoutException("No dialog view ${BookmarksRows.resourceName(viewId)} was captured")
    }

    private fun tapDialogButton(@IdRes buttonId: Int) {
        onView(withId(buttonId)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun currentPageStrokeWidth(): Int =
        (CURRENT_PAGE_STROKE_DP * TestEnvironment.targetContext.resources.displayMetrics.density).toInt()

    private fun boundsOf(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    companion object {
        const val CURRENT_PAGE_STROKE_DP = 2
        const val EDGE_INSET_PX = 2f
        const val FLING_STEPS = 5
        const val FLING_FAR_FRACTION = 0.85f
        const val FLING_NEAR_FRACTION = 0.15f
        const val GRID_RANGE_STABLE_MS = 500L
    }
}
