// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import android.app.Activity
import android.graphics.PointF
import android.graphics.Rect
import android.text.Selection
import android.text.Spannable
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.slider.Slider
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TouchActions
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.RootDescriptionParser
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeDebug
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModePageSnapshot
import org.hamcrest.CoreMatchers.allOf
import java.util.regex.Pattern

class TextModeRobot {

    private val readerRobot = ReaderRobot()

    fun awaitReaderSettled() {
        readerRobot.awaitReady()
    }

    fun enterFromReader(firstPosition: Int = 0) {
        awaitReaderSettled()
        openFromReaderMenu()
        awaitPageText(firstPosition)
    }

    fun finish() {
        if (!isShowing()) return
        TestEnvironment.uiDevice.pressBack()
        awaitFinished()
    }

    fun openFromReaderMenu() {
        openReaderActionsDialog()
        tapReaderTile(string(R.string.text_mode))
        awaitShown()
    }

    fun openReaderActionsDialog() {
        Await.untilNoThrow("the reader actions toolbar item is tappable") {
            onView(withId(R.id.readerActionsOption)).perform(click())
        }
        Dialogs.awaitActionsDialog()
        tile(string(R.string.go_to_page)).check(matches(isDisplayed()))
    }

    fun tapReaderTile(label: String) {
        Dialogs.awaitActionsDialog()
        tile(label).perform(scrollTo(), click())
        TestEnvironment.waitForIdleSync()
    }

    fun readerGoToPage(oneBasedPage: Int) {
        openReaderActionsDialog()
        tapReaderTile(string(R.string.go_to_page))
        Await.until("the reader Go To Page dialog is shown") { isGoToPageDialogShown() }
        onView(withId(R.id.editText)).inRoot(isDialog())
            .perform(replaceText(oneBasedPage.toString()), closeSoftKeyboard())
        onView(withText(string(R.string.go_to))).inRoot(isDialog()).perform(click())
        Await.until("the reader Go To Page dialog is dismissed") { !isGoToPageDialogShown() }
    }

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Activity =
        Screens.awaitResumed(TextModeActivity::class.java, timeoutMs)

    fun awaitFinished(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Screens.awaitGone(TextModeActivity::class.java, timeoutMs)
    }

    fun isShowing(): Boolean = TestEnvironment.resumedActivityOfType(TextModeActivity::class.java) != null

    fun activity(): TextModeActivity =
        TestEnvironment.resumedActivityOfType(TextModeActivity::class.java) as? TextModeActivity
            ?: throw ConditionTimeoutException("No resumed text mode activity")

    fun <T> onActivity(read: (TextModeActivity) -> T): T = TestEnvironment.onMainThread { read(activity()) }

    fun snapshot(position: Int): TextModePageSnapshot? =
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(TextModeActivity::class.java)
                ?: return@onMainThread null
            val pages = activity.findViewById<RecyclerView>(R.id.textPagesRecyclerView)
                ?: return@onMainThread null
            TextModeDebug.pageSnapshot(pages, position)
        }

    fun awaitPageText(position: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): TextModePageSnapshot =
        Await.untilValue(
            condition = "the text mode item at position $position shows non empty page text",
            timeoutMs = timeoutMs,
            read = { snapshot(position) },
        ) { it != null && it.pageTextVisible && !it.pageText.isNullOrBlank() }!!

    fun awaitFirstVisiblePage(oneBasedPage: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): TextModePageSnapshot =
        Await.untilValue(
            condition = "text mode lays out page $oneBasedPage as its first visible item with its label and text",
            timeoutMs = timeoutMs,
            read = {
                onActivity { activity ->
                    val pages = activity.findViewById<RecyclerView>(R.id.textPagesRecyclerView)
                    val manager = pages.layoutManager as? LinearLayoutManager ?: return@onActivity null
                    if (!pages.isLaidOut || pages.isLayoutRequested || pages.hasPendingAdapterUpdates()) {
                        return@onActivity null
                    }
                    val position = manager.findFirstVisibleItemPosition()
                    if (position != oneBasedPage - 1) return@onActivity null
                    val item = pages.findViewHolderForAdapterPosition(position)?.itemView
                    val label = item?.findViewById<TextView>(R.id.pageLabel)?.text?.toString()
                    if (label != labelFor(oneBasedPage)) return@onActivity null
                    TextModeDebug.pageSnapshot(pages, position)
                }
            },
        ) { it != null && it.pageTextVisible && !it.pageText.isNullOrBlank() }!!

    fun awaitSnapshot(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): TextModePageSnapshot =
        Await.untilValue(
            condition = "the text mode item at position $position is bound",
            timeoutMs = timeoutMs,
            read = { snapshot(position) },
        ) { it != null }!!

    fun pageText(position: Int): String = awaitPageText(position).pageText!!.toString()

    fun pageSpanned(position: Int): CharSequence = awaitPageText(position).pageText!!

    fun pages(): RecyclerView = onActivity { it.findViewById(R.id.textPagesRecyclerView) }

    fun layoutManager(): LinearLayoutManager = TestEnvironment.onMainThread {
        activity().findViewById<RecyclerView>(R.id.textPagesRecyclerView).layoutManager as LinearLayoutManager
    }

    fun firstVisiblePosition(): Int = TestEnvironment.onMainThread {
        val manager = activity().findViewById<RecyclerView>(R.id.textPagesRecyclerView)
            .layoutManager as LinearLayoutManager
        manager.findFirstVisibleItemPosition()
    }

    fun firstCompletelyVisiblePosition(): Int = TestEnvironment.onMainThread {
        val manager = activity().findViewById<RecyclerView>(R.id.textPagesRecyclerView)
            .layoutManager as LinearLayoutManager
        manager.findFirstCompletelyVisibleItemPosition()
    }

    fun itemView(position: Int): View? = TestEnvironment.onMainThread {
        activity().findViewById<RecyclerView>(R.id.textPagesRecyclerView)
            .findViewHolderForAdapterPosition(position)?.itemView
    }

    fun <T> onItem(position: Int, read: (View) -> T): T = TestEnvironment.onMainThread {
        val item = activity().findViewById<RecyclerView>(R.id.textPagesRecyclerView)
            .findViewHolderForAdapterPosition(position)?.itemView
            ?: throw ConditionTimeoutException("The text mode item at position $position is not bound")
        read(item)
    }

    fun pageLabel(position: Int): String? = TestEnvironment.onMainThread {
        itemChild<TextView>(position, R.id.pageLabel)?.text?.toString()
    }

    fun pageMessage(position: Int): String? = TestEnvironment.onMainThread {
        val message = itemChild<TextView>(position, R.id.pageMessage) ?: return@onMainThread null
        if (message.visibility != View.VISIBLE) null else message.text?.toString()
    }

    fun pageProgressVisibility(position: Int): Int? = TestEnvironment.onMainThread {
        itemChild<View>(position, R.id.pageProgressBar)?.visibility
    }

    fun pageTextVisibility(position: Int): Int? = TestEnvironment.onMainThread {
        itemChild<View>(position, R.id.pageText)?.visibility
    }

    fun awaitPageMessage(position: Int, expected: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the text mode item at position $position shows the message \"$expected\"",
            timeoutMs = timeoutMs,
            read = { pageMessage(position) },
        ) { it != null && it.contains(expected) }
    }

    fun tapPageMessage(position: Int) {
        val bounds = TestEnvironment.onMainThread {
            val message = itemChild<TextView>(position, R.id.pageMessage)
                ?: throw ConditionTimeoutException("The text mode item at position $position shows no message")
            val location = IntArray(2)
            message.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + message.width, location[1] + message.height)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun itemBackgroundColor(position: Int): Int = onItem(position) { item ->
        (item.background as? android.graphics.drawable.ColorDrawable)?.color
            ?: throw ConditionTimeoutException("The text mode item at position $position has no solid background")
    }

    fun pageTextColor(position: Int): Int = TestEnvironment.onMainThread {
        itemChild<TextView>(position, R.id.pageText)?.currentTextColor
            ?: throw ConditionTimeoutException("The text mode item at position $position has no page text view")
    }

    fun rootBackgroundColor(): Int = onActivity { activity ->
        (activity.findViewById<View>(R.id.textModeRoot).background as? android.graphics.drawable.ColorDrawable)?.color
            ?: throw ConditionTimeoutException("The text mode root has no solid background")
    }

    fun recyclerBackgroundColor(): Int = onActivity { activity ->
        (activity.findViewById<View>(R.id.textPagesRecyclerView).background
            as? android.graphics.drawable.ColorDrawable)?.color
            ?: throw ConditionTimeoutException("The text mode page list has no solid background")
    }

    fun recyclerWidth(): Int = onActivity { it.findViewById<View>(R.id.textPagesRecyclerView).width }

    fun pageCounter(): String? = TestEnvironment.onMainThread {
        activity().findViewById<TextView>(R.id.pageButton)?.text?.toString()
    }

    fun awaitPageCounter(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the text mode page counter reads \"$expected\"",
            timeoutMs = timeoutMs,
            read = { pageCounter() },
        ) { it == expected }
    }

    fun counterFor(oneBasedPage: Int, pageCount: Int): String =
        TestEnvironment.targetContext.getString(R.string.text_mode_page_counter, oneBasedPage, pageCount)

    fun labelFor(oneBasedPage: Int): String =
        TestEnvironment.targetContext.getString(R.string.text_mode_page_label, oneBasedPage)

    fun sliderValue(): Float = onActivity { it.findViewById<Slider>(R.id.pageSlider).value }

    fun sliderEnabled(): Boolean = onActivity { it.findViewById<Slider>(R.id.pageSlider).isEnabled }

    fun previousEnabled(): Boolean = onActivity { it.findViewById<View>(R.id.previousPageButton).isEnabled }

    fun nextEnabled(): Boolean = onActivity { it.findViewById<View>(R.id.nextPageButton).isEnabled }

    fun controlsVisibility(): Int = onActivity { it.findViewById<View>(R.id.readerControlsCard).visibility }

    fun progressVisibility(): Int = onActivity { it.findViewById<View>(R.id.progressBar).visibility }

    fun showControls() {
        if (controlsVisibility() == View.VISIBLE) return
        tapPageArea()
        Await.until("the text mode controls card is visible") { controlsVisibility() == View.VISIBLE }
    }

    fun hideControls() {
        if (controlsVisibility() != View.VISIBLE) return
        tapPageArea()
        Await.until("the text mode controls card is hidden") { controlsVisibility() != View.VISIBLE }
    }

    fun tapPageArea() {
        val bounds = recyclerBounds()
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.top + (bounds.height() * TAP_Y_FRACTION).toInt())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun awaitControlsGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the text mode controls card is gone", timeoutMs) { controlsVisibility() == View.GONE }
    }

    fun tapNextPage() {
        showControls()
        clickControl(R.id.nextPageButton)
    }

    fun tapPreviousPage() {
        showControls()
        clickControl(R.id.previousPageButton)
    }

    fun tapBackToPdf() {
        showControls()
        clickControl(R.id.backToPdfButton)
    }

    fun tapTableOfContents() {
        showControls()
        clickControl(R.id.tableOfContentsButton)
    }

    fun goToPage(oneBasedPage: Int) {
        openGoToPageDialog()
        val field = TestEnvironment.uiDevice.wait(
            Until.findObject(By.res(TestEnvironment.targetPackage, "editText")),
            Await.LONG_TIMEOUT_MS,
        ) ?: throw ConditionTimeoutException("The text-mode Go To Page dialog has no page field")
        field.text = oneBasedPage.toString()
        val go = TestEnvironment.uiDevice.wait(
            Until.findObject(By.text(string(R.string.go_to))),
            Await.LONG_TIMEOUT_MS,
        ) ?: throw ConditionTimeoutException("The text-mode Go To Page dialog has no Go button")
        go.click()
        Await.until("the Go To Page dialog is dismissed") { !isGoToPageDialogShown() }
    }

    fun openGoToPageDialog() {
        showControls()
        clickControl(R.id.pageButton)
        Await.until("the Go To Page dialog is shown") { isGoToPageDialogShown() }
    }

    fun openAllPagesGrid() {
        openGoToPageDialog()
        onView(withText(string(R.string.all_pages))).inRoot(isDialog()).perform(click())
        Await.until("the Go To Page dialog is dismissed") { !isGoToPageDialogShown() }
    }

    fun isGoToPageDialogShown(): Boolean = Dialogs.hasDialogTitled(R.string.go_to_page)

    fun openTypographySheet() {
        showControls()
        clickControl(R.id.typographyButton)
        Dialogs.awaitViewOnScreen(R.id.resetSettingsButton)
        expandTypographySheet()
    }

    fun openTypographySheetForUiAutomation() {
        showControls()
        clickControl(R.id.typographyButton)
        Dialogs.awaitViewOnScreen(R.id.resetSettingsButton)
    }

    fun tapVisibleSheetControl(@IdRes viewId: Int) {
        val entryName = TestEnvironment.targetContext.resources.getResourceEntryName(viewId)
        val control = TestEnvironment.uiDevice.wait(
            Until.findObject(By.res(TestEnvironment.targetPackage, entryName)),
            Await.DEFAULT_TIMEOUT_MS,
        ) ?: throw ConditionTimeoutException(
            "The visible typography sheet has no control named $entryName"
        )
        control.click()
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun expandTypographySheet() {
        val sheet = sheetView<View>(com.google.android.material.R.id.design_bottom_sheet)
        TestEnvironment.onMainThread {
            BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
        Await.until("the typography sheet reaches its expanded state") {
            TestEnvironment.onMainThread {
                BottomSheetBehavior.from(sheet).state == BottomSheetBehavior.STATE_EXPANDED
            }
        }
        TestEnvironment.waitForIdleSync()
    }

    fun closeTypographySheet() {
        TestEnvironment.uiDevice.pressBack()
        Await.until("the typography sheet is dismissed") { !isTypographySheetShown() }
        TestEnvironment.waitForIdleSync()
    }
    fun isTypographySheetShown(): Boolean = Dialogs.hasViewOnScreen(R.id.resetSettingsButton)

    fun <T : View> sheetView(@IdRes viewId: Int): T {
        Dialogs.awaitViewOnScreen(R.id.resetSettingsButton)
        var captured: View? = null
        Await.untilNoThrow("the typography sheet holds ${resourceName(viewId)}") {
            captured = null
            onView(withId(viewId)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                captured = view
            }
            if (captured == null) throw ConditionTimeoutException("No view captured for ${resourceName(viewId)}")
        }
        @Suppress("UNCHECKED_CAST")
        return captured as T
    }

    fun sheetSliderValue(@IdRes viewId: Int): Float {
        val slider = sheetView<Slider>(viewId)
        return TestEnvironment.onMainThread { slider.value }
    }

    fun sheetSwitchChecked(@IdRes viewId: Int): Boolean {
        val switch = sheetView<MaterialSwitch>(viewId)
        return TestEnvironment.onMainThread { switch.isChecked }
    }

    fun sheetButtonChecked(@IdRes viewId: Int): Boolean {
        val button = sheetView<MaterialButton>(viewId)
        return TestEnvironment.onMainThread { button.isChecked }
    }

    fun setSheetSwitch(@IdRes viewId: Int, checked: Boolean) {
        if (sheetSwitchChecked(viewId) == checked) return
        onView(withId(viewId)).inRoot(isDialog()).perform(scrollTo(), click())
        Await.until("the sheet switch ${resourceName(viewId)} reads $checked") {
            sheetSwitchChecked(viewId) == checked
        }
    }

    fun tapSheetControl(@IdRes viewId: Int) {
        onView(withId(viewId)).inRoot(isDialog()).perform(scrollTo(), click())
        TestEnvironment.waitForIdleSync()
    }

    fun dragSheetSliderToMaximum(@IdRes viewId: Int) {
        dragSheetSlider(viewId, toMaximum = true)
    }

    private fun dragSheetSlider(@IdRes viewId: Int, toMaximum: Boolean) {
        val slider = sheetView<Slider>(viewId)
        onView(withId(viewId)).inRoot(isDialog()).perform(scrollTo())
        val bounds = boundsOf(slider)
        val y = bounds.exactCenterY()
        val from = PointF(bounds.exactCenterX(), y)
        val to = PointF(if (toMaximum) bounds.right - 1f else bounds.left + 1f, y)
        RawInput.dragWithoutFling(from, to)
        TestEnvironment.waitForIdleSync()
        val target = TestEnvironment.onMainThread { if (toMaximum) slider.valueTo else slider.valueFrom }
        Await.untilValue(
            condition = "the sheet slider ${resourceName(viewId)} settles at $target",
            read = { TestEnvironment.onMainThread { slider.value } },
        ) { it == target }
    }

    fun dragPageSliderToFraction(fraction: Float) {
        showControls()
        val slider = onActivity { it.findViewById<Slider>(R.id.pageSlider) }
        val bounds = boundsOf(slider)
        val y = bounds.exactCenterY()
        val from = PointF(bounds.left + THUMB_INSET_PX, y)
        val to = PointF(bounds.left + bounds.width() * fraction, y)
        RawInput.dragWithoutFling(from, to)
        TestEnvironment.waitForIdleSync()
    }

    fun cancelPageSliderGesture(fraction: Float) {
        showControls()
        val slider = onActivity { it.findViewById<Slider>(R.id.pageSlider) }
        val bounds = boundsOf(slider)
        val y = bounds.exactCenterY()
        val from = PointF(bounds.left + THUMB_INSET_PX, y)
        val to = PointF(bounds.left + bounds.width() * fraction, y)
        RawInput.dragAlongLine(from = from, to = to, endWithCancel = true)
        TestEnvironment.waitForIdleSync()
    }

    fun readerBackStackPages(): List<Int> {
        val reader = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: throw ConditionTimeoutException("No resumed reader activity to read the history hook from")
        val description = TestEnvironment.onMainThread {
            reader.findViewById<View>(R.id.mainLayout)?.contentDescription?.toString()
        }?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val raw = RootDescriptionParser.parse(description).value(HISTORY_GROUP, HISTORY_BACK) ?: return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }.map { part ->
            part.toIntOrNull()
                ?: throw AssertionError("The reader history hook holds \"$raw\" which is not a page index list")
        }
    }

    fun recyclerBounds(): Rect = onActivity { activity ->
        val view = activity.findViewById<View>(R.id.textPagesRecyclerView)
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun scrollState(): Int = onActivity { it.findViewById<RecyclerView>(R.id.textPagesRecyclerView).scrollState }

    fun awaitScrollIdle(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the text mode page list stops scrolling", timeoutMs) {
            scrollState() == RecyclerView.SCROLL_STATE_IDLE
        }
    }

    fun swipeUpOnce() {
        val bounds = recyclerBounds()
        RawInput.dragWithoutFling(
            PointF(bounds.exactCenterX(), bounds.top + bounds.height() * SWIPE_FAR_FRACTION),
            PointF(bounds.exactCenterX(), bounds.top + bounds.height() * SWIPE_NEAR_FRACTION),
        )
        awaitScrollIdle()
    }

    fun flingUpOnce() {
        val bounds = recyclerBounds()
        TestEnvironment.uiDevice.swipe(
            bounds.centerX(),
            bounds.top + (bounds.height() * SWIPE_FAR_FRACTION).toInt(),
            bounds.centerX(),
            bounds.top + (bounds.height() * SWIPE_NEAR_FRACTION).toInt(),
            FLING_STEPS,
        )
        awaitScrollIdle()
    }

    fun scrollUntilCounter(oneBasedPage: Int, pageCount: Int, budget: Int = SCROLL_BUDGET) {
        val expected = counterFor(oneBasedPage, pageCount)
        val target = oneBasedPage - 1
        repeat(budget) {
            if (pageCounter() == expected) return
            TestEnvironment.onMainThread {
                layoutManager().scrollToPositionWithOffset(target, 0)
            }
            TestEnvironment.waitForIdleSync()
            dragPageList(creepStep())
        }
        awaitPageCounter(expected, Await.SHORT_TIMEOUT_MS)
    }

    private fun creepStep(): Int = (recyclerBounds().height() * CREEP_FRACTION).toInt()

    private fun touchSlop(): Int =
        ViewConfiguration.get(TestEnvironment.targetContext).scaledTouchSlop

    private fun dragPageList(distancePx: Int) {
        if (distancePx == 0) return
        val bounds = recyclerBounds()
        val reach = (bounds.height() * DRAG_REACH_FRACTION).toInt()
        val slop = touchSlop() + SLOP_MARGIN_PX
        val demanded = distancePx + if (distancePx > 0) slop else -slop
        val step = demanded.coerceIn(-reach, reach)
        val startY = if (step > 0) {
            bounds.bottom - (bounds.height() * DRAG_MARGIN_FRACTION)
        } else {
            bounds.top + (bounds.height() * DRAG_MARGIN_FRACTION)
        }
        RawInput.dragWithoutFling(
            PointF(bounds.exactCenterX(), startY),
            PointF(bounds.exactCenterX(), startY - step),
        )
        awaitScrollIdle()
    }

    fun charOffsetPoint(position: Int, offset: Int): PointF = TestEnvironment.onMainThread {
        val textView = itemChild<TextView>(position, R.id.pageText)
            ?: throw ConditionTimeoutException("The text mode item at position $position has no page text view")
        val layout = textView.layout
            ?: throw ConditionTimeoutException("The page text view at position $position has no layout yet")
        val line = layout.getLineForOffset(offset)
        val x = layout.getPrimaryHorizontal(offset)
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        val location = IntArray(2)
        textView.getLocationOnScreen(location)
        PointF(
            location[0] + textView.totalPaddingLeft + x,
            location[1] + textView.totalPaddingTop + y,
        )
    }

    fun longPressCharOffset(position: Int, offset: Int) {
        val point = charOffsetPoint(position, offset)
        RawInput.pressAndHold(point, LONG_PRESS_MS)
        TestEnvironment.waitForIdleSync()
    }

    fun selectedText(position: Int): String? = TestEnvironment.onMainThread {
        val textView = itemChild<TextView>(position, R.id.pageText) ?: return@onMainThread null
        val text = textView.text ?: return@onMainThread null
        val start = Selection.getSelectionStart(text)
        val end = Selection.getSelectionEnd(text)
        if (start < 0 || end < 0 || start == end) {
            null
        } else {
            text.subSequence(minOf(start, end), maxOf(start, end)).toString()
        }
    }

    fun awaitSelection(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): String =
        Await.untilValue(
            condition = "the page text at position $position carries a selection",
            timeoutMs = timeoutMs,
            read = { selectedText(position) },
        ) { !it.isNullOrEmpty() }!!

    fun extendSelection(position: Int, start: Int, end: Int) {
        TestEnvironment.onMainThread {
            val textView = itemChild<TextView>(position, R.id.pageText)
                ?: throw ConditionTimeoutException("The text mode item at position $position has no page text view")
            val text = textView.text as? Spannable
                ?: throw ConditionTimeoutException("The page text at position $position is not selectable")
            Selection.setSelection(text, start, end)
        }
        TestEnvironment.waitForIdleSync()
    }

    fun awaitActionMode(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): UiObject2 =
        TestEnvironment.uiDevice.wait(Until.findObject(By.text(caseInsensitive(string(android.R.string.copy)))), timeoutMs)
            ?: throw ConditionTimeoutException(
                "No selection action mode carrying a Copy item appeared within ${timeoutMs}ms"
            )

    fun actionModeShowing(): Boolean =
        TestEnvironment.uiDevice.hasObject(By.text(caseInsensitive(string(android.R.string.copy))))

    fun actionModeHasItem(label: String): Boolean {
        if (TestEnvironment.uiDevice.hasObject(By.text(caseInsensitive(label)))) return true
        val overflow = TestEnvironment.uiDevice.findObject(By.desc(caseInsensitive(OVERFLOW_DESCRIPTION)))
            ?: return false
        overflow.click()
        TestEnvironment.uiDevice.waitForIdle()
        val found = TestEnvironment.uiDevice.wait(
            Until.hasObject(By.text(caseInsensitive(label))),
            Await.SHORT_TIMEOUT_MS,
        ) == true
        TestEnvironment.uiDevice.pressBack()
        TestEnvironment.uiDevice.waitForIdle()
        return found
    }

    fun tapActionModeItem(label: String) {
        val direct = TestEnvironment.uiDevice.wait(
            Until.findObject(By.text(caseInsensitive(label))),
            Await.SHORT_TIMEOUT_MS,
        )
        if (direct != null) {
            direct.click()
            TestEnvironment.uiDevice.waitForIdle()
            return
        }
        val overflow = TestEnvironment.uiDevice.wait(
            Until.findObject(By.desc(caseInsensitive(OVERFLOW_DESCRIPTION))),
            Await.SHORT_TIMEOUT_MS,
        ) ?: throw ConditionTimeoutException(
            "The selection action mode shows neither \"$label\" nor an overflow button"
        )
        overflow.click()
        TestEnvironment.uiDevice.waitForIdle()
        val expanded = TestEnvironment.uiDevice.wait(
            Until.findObject(By.text(caseInsensitive(label))),
            Await.SHORT_TIMEOUT_MS,
        ) ?: throw ConditionTimeoutException(
            "The selection action mode overflow does not carry \"$label\""
        )
        expanded.click()
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun awaitActionModeGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the selection action mode is dismissed", timeoutMs) {
            TestEnvironment.uiDevice.findObject(By.text(caseInsensitive(string(android.R.string.copy)))) == null
        }
    }

    fun boundsOf(view: View): Rect = TestEnvironment.onMainThread {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun clickControl(@IdRes viewId: Int) {
        val entryName = TestEnvironment.targetContext.resources.getResourceEntryName(viewId)
        Await.until("the text mode control $entryName is visible") {
            TestEnvironment.onMainThread { activity().findViewById<View>(viewId)?.isShown == true }
        }
        onView(withId(viewId)).perform(TouchActions.click())
        TestEnvironment.waitForIdleSync()
    }

    private fun <T : View> itemChild(position: Int, @IdRes viewId: Int): T? {
        val item = activity().findViewById<RecyclerView>(R.id.textPagesRecyclerView)
            .findViewHolderForAdapterPosition(position)?.itemView ?: return null
        return item.findViewById(viewId)
    }

    private fun tile(label: String) =
        onView(allOf(withContentDescription(label), isDisplayed())).inRoot(isDialog())

    private fun caseInsensitive(value: String): Pattern =
        Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE)

    private fun resourceName(@IdRes viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())

    companion object {

        const val TAP_Y_FRACTION = 0.5f
        const val SWIPE_FAR_FRACTION = 0.82f
        const val SWIPE_NEAR_FRACTION = 0.18f
        const val FLING_STEPS = 5
        const val SCROLL_BUDGET = 40
        const val CREEP_FRACTION = 0.2f
        const val SLOP_MARGIN_PX = 4
        const val DRAG_REACH_FRACTION = 0.55f
        const val DRAG_MARGIN_FRACTION = 0.30f
        const val THUMB_INSET_PX = 4f
        const val LONG_PRESS_MS = 900L
        const val OVERFLOW_DESCRIPTION = "More options"
        const val HISTORY_GROUP = "hist"
        const val HISTORY_BACK = "back"
    }
}
