// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Checkable
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.textfield.TextInputLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.hasToString
import org.hamcrest.Matchers.instanceOf

object SettingsDialog {

    const val POSITIVE = android.R.id.button1
    const val NEGATIVE = android.R.id.button2
    const val NEUTRAL = android.R.id.button3

    const val ABSENCE_WINDOW_MS = 1_000L

    private const val ANDROID_PACKAGE = "android"

    fun isShowing(): Boolean = Dialogs.isAnyShowing()

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("a settings dialog with its buttons is showing", timeoutMs) { isShowing() }
    }

    fun awaitDismissed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the settings dialog is dismissed", timeoutMs) { !isShowing() }
    }

    fun assertNoneWithin(windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no settings dialog appears",
            windowMs = windowMs,
        ) { !isShowing() }
    }

    fun assertNoTextWithin(text: String, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no dialog shows \"$text\"",
            windowMs = windowMs,
        ) { !Dialogs.hasText(text) }
    }

    fun title(): String? = readRoot { root ->
        findViewById<TextView>(root, "alertTitle")?.text?.toString()
    }

    fun message(): String? = readRoot { root ->
        root.findViewById<TextView>(android.R.id.message)?.text?.toString()
    }

    fun buttonLabel(buttonId: Int): String? = readRoot { root ->
        root.findViewById<Button>(buttonId)?.takeIf { it.isShown }?.text?.toString()
    }

    fun isButtonEnabled(buttonId: Int): Boolean = readRoot { root ->
        root.findViewById<Button>(buttonId)?.isEnabled ?: false
    }

    fun awaitButtonEnabled(buttonId: Int, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the dialog button $buttonId reads enabled $expected",
            timeoutMs,
            read = { runCatching { isButtonEnabled(buttonId) }.getOrNull() },
        ) { it == expected }
    }

    fun awaitDisabledButtonForMessage(
        expectedMessage: String,
        buttonId: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): String = Await.untilNotNull(
        "the dialog showing the expected message has a disabled button $buttonId",
        timeoutMs,
    ) {
        runCatching {
            readRoot { root ->
                val message = root.findViewById<TextView>(android.R.id.message)?.text?.toString()
                val button = root.findViewById<Button>(buttonId)?.takeIf { it.isShown }
                button?.text?.toString()?.takeIf { message == expectedMessage && !button.isEnabled }
            }
        }.getOrNull()
    }

    fun isOwnDialogShowing(): Boolean =
        isShowing() && TestEnvironment.uiDevice.currentPackageName == TestEnvironment.targetPackage

    fun awaitOwnDialogShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until(
            "a dialog belonging to ${TestEnvironment.targetPackage} is showing",
            timeoutMs,
        ) { isOwnDialogShowing() }
    }

    fun tap(buttonId: Int) {
        awaitOwnDialogShown()
        onView(isRoot()).inRoot(isDialog()).check { _, error -> if (error != null) throw error }
        onView(androidx.test.espresso.matcher.ViewMatchers.withId(buttonId)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun apply() = tap(POSITIVE)

    fun cancel() = tap(NEGATIVE)

    fun reset() = tap(NEUTRAL)

    fun confirm() = tap(POSITIVE)

    fun choiceLabels(): List<String> = readRoot { root ->
        val list = Dialogs.firstDescendant(root, ListView::class.java) ?: throw missingList()
        val adapter = list.adapter ?: return@readRoot emptyList()
        (0 until adapter.count).map { adapter.getItem(it)?.toString().orEmpty() }
    }

    fun checkedChoiceIndex(): Int = readRoot { root ->
        val list = Dialogs.firstDescendant(root, ListView::class.java) ?: throw missingList()
        list.checkedItemPosition
    }

    fun checkedChoiceLabelOrNull(): String? {
        val index = checkedChoiceIndex()
        if (index < 0) return null
        return choiceLabels().getOrNull(index)
    }

    fun checkedChoiceIndexes(): Set<Int> = readRoot { root ->
        val list = Dialogs.firstDescendant(root, ListView::class.java) ?: throw missingList()
        val checked = list.checkedItemPositions ?: return@readRoot emptySet()
        (0 until checked.size())
            .filter { checked.valueAt(it) }
            .map { checked.keyAt(it) }
            .toSet()
    }

    fun checkedChoiceLabels(): Set<String> {
        val labels = choiceLabels()
        return checkedChoiceIndexes().mapNotNull { labels.getOrNull(it) }.toSet()
    }

    fun awaitChoiceList(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the dialog shows its choice list", timeoutMs) {
            isShowing() && TestEnvironment.uiDevice.hasObject(By.clazz(LIST_VIEW_CLASS))
        }
    }

    fun awaitRowList(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the dialog shows its row list", timeoutMs) {
            isShowing() && TestEnvironment.uiDevice.hasObject(By.clazz(RECYCLER_VIEW_CLASS))
        }
    }

    fun pickChoice(label: String) {
        val labels = choiceLabels()
        if (label !in labels) {
            throw ConditionTimeoutException("The dialog offers no entry labelled \"$label\", it offers $labels")
        }
        onData(allOf(instanceOf(CharSequence::class.java), hasToString(label)))
            .inRoot(isDialog())
            .perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun setChoiceChecked(label: String, checked: Boolean) {
        if ((label in checkedChoiceLabels()) == checked) return
        pickChoice(label)
        Await.untilValue(
            "the multi choice row \"$label\" reads checked $checked",
            Await.DEFAULT_TIMEOUT_MS,
            read = { runCatching { label in checkedChoiceLabels() }.getOrNull() },
        ) { it == checked }
    }

    fun progressValue(): Int = readRoot { root ->
        Dialogs.firstDescendant(root, ProgressBar::class.java)?.progress
            ?: throw ConditionTimeoutException("The dialog carries no progress indicator")
    }

    fun isProgressIndeterminate(): Boolean = readRoot { root ->
        Dialogs.firstDescendant(root, ProgressBar::class.java)?.isIndeterminate
            ?: throw ConditionTimeoutException("The dialog carries no progress indicator")
    }

    fun hasProgressIndicator(): Boolean =
        runCatching { readRoot { root -> Dialogs.firstDescendant(root, ProgressBar::class.java) != null } }.getOrDefault(false)

    fun awaitProgressAbove(value: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the download progress indicator reports more than $value percent",
            timeoutMs,
            read = { runCatching { progressValue() }.getOrNull() },
        ) { it != null && it > value }
    }

    fun setInputText(text: String) {
        readRoot { root ->
            val field = Dialogs.firstDescendant(root, EditText::class.java)
                ?: throw ConditionTimeoutException("The dialog carries no text field")
            field.setText(text)
            field.setSelection(text.length)
        }
        TestEnvironment.waitForIdleSync()
    }

    fun inputError(): String? = readRoot { root ->
        Dialogs.firstDescendant(root, TextInputLayout::class.java)?.error?.toString()
    }

    fun awaitInputError(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the dialog text field reports the error \"$expected\"",
            timeoutMs,
            read = { runCatching { inputError() }.getOrNull() },
        ) { it == expected }
    }

    fun sliderValueLabel(): String = readRoot { root ->
        findSeekBarSibling(root)?.text?.toString()
            ?: throw ConditionTimeoutException("The dialog carries no slider value label")
    }

    fun sliderValue(): Int = sliderValueLabel().trim().toInt()

    fun sliderBounds(): Rect = readRoot { root ->
        val bar = Dialogs.firstDescendant(root, SeekBar::class.java) ?: throw ConditionTimeoutException("The dialog carries no slider")
        boundsOf(bar)
    }

    fun dragSliderTo(fraction: Float) {
        val bounds = sliderBounds()
        val insetX = bounds.width() * SLIDER_EDGE_INSET
        val startX = bounds.exactCenterX()
        val targetX = bounds.left + insetX + (bounds.width() - 2 * insetX) * fraction
        RawInput.dragWithoutFling(
            from = PointF(startX, bounds.exactCenterY()),
            to = PointF(targetX, bounds.exactCenterY()),
        )
        TestEnvironment.waitForIdleSync()
    }

    fun dragSliderToFarLeft() {
        val bounds = sliderBounds()
        RawInput.dragWithoutFling(
            from = PointF(bounds.exactCenterX(), bounds.exactCenterY()),
            to = PointF(bounds.left.toFloat() - OVERSHOOT_PX, bounds.exactCenterY()),
        )
        TestEnvironment.waitForIdleSync()
    }

    fun dragSliderToFarRight() {
        val bounds = sliderBounds()
        RawInput.dragWithoutFling(
            from = PointF(bounds.exactCenterX(), bounds.exactCenterY()),
            to = PointF(bounds.right.toFloat() + OVERSHOOT_PX, bounds.exactCenterY()),
        )
        TestEnvironment.waitForIdleSync()
    }

    fun firstBoundRowLabel(): String = readRoot { root ->
        val list = Dialogs.firstDescendant(root, RecyclerView::class.java) ?: throw missingRecycler()
        (0 until list.childCount)
            .map { list.getChildAt(it) }
            .minByOrNull { list.getChildAdapterPosition(it) }
            ?.let { findTaggedText(it, TITLE_TAG) }
            ?: throw ConditionTimeoutException("The dialog list has no bound rows to read")
    }

    fun recyclerRowCount(): Int = readRoot { root ->
        Dialogs.firstDescendant(root, RecyclerView::class.java)?.adapter?.itemCount ?: 0
    }

    fun allRowLabelsInOrder(): List<String> {
        val total = recyclerRowCount()
        val found = LinkedHashMap<Int, String>()
        var position = 0
        while (position < total) {
            scrollRecyclerToPosition(position)
            val bound = boundRowsWithPositions()
            bound.forEach { (index, label) -> found[index] = label }
            position += bound.size.coerceAtLeast(1)
        }
        scrollRecyclerToPosition(0)
        return (0 until total).map { index ->
            found[index] ?: throw ConditionTimeoutException(
                "Row $index of $total never bound while walking the dialog list, read ${found.keys}"
            )
        }
    }

    fun isRecyclerRowChecked(label: String): Boolean {
        scrollToRecyclerRow(label)
        return readRoot { root ->
            (recyclerChildFor(root, label).findViewWithTag<View>(CHECKBOX_TAG) as? Checkable)?.isChecked ?: false
        }
    }

    fun isRecyclerRowCheckboxEnabled(label: String): Boolean {
        scrollToRecyclerRow(label)
        return readRoot { root ->
            recyclerChildFor(root, label).findViewWithTag<View>(CHECKBOX_TAG)?.isEnabled ?: false
        }
    }

    fun isRecyclerRowDragHandleVisible(label: String): Boolean {
        scrollToRecyclerRow(label)
        return readRoot { root ->
            recyclerChildFor(root, label).findViewWithTag<View>(DRAG_HANDLE_TAG)?.visibility == View.VISIBLE
        }
    }

    fun isRecyclerRowClickable(label: String): Boolean {
        scrollToRecyclerRow(label)
        return readRoot { root -> recyclerChildFor(root, label).hasOnClickListeners() }
    }

    fun tapRecyclerRow(label: String) {
        val position = scrollToRecyclerRow(label)
        scrollRecyclerToPosition(position)
        val bounds = clippedRecyclerRowBounds(label)
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.waitForIdleSync()
    }

    fun recyclerRowBounds(label: String): Rect {
        scrollToRecyclerRow(label)
        return readRoot { root -> boundsOf(recyclerChildFor(root, label)) }
    }

    private fun clippedRecyclerRowBounds(label: String): Rect = readRoot { root ->
        val list = Dialogs.firstDescendant(root, RecyclerView::class.java) ?: throw missingRecycler()
        val row = boundsOf(recyclerChildFor(root, label))
        val visible = boundsOf(list)
        if (!row.intersect(visible)) {
            throw ConditionTimeoutException(
                "The row labelled \"$label\" sits outside the visible bounds of the dialog list, so a tap on its " +
                    "centre would land on the dialog button bar or the scrim and dismiss the dialog instead of " +
                    "toggling the row"
            )
        }
        row
    }

    fun dragHandleBounds(label: String): Rect {
        scrollToRecyclerRow(label)
        return readRoot { root ->
            val handle = recyclerChildFor(root, label).findViewWithTag<View>(DRAG_HANDLE_TAG)
                ?: throw ConditionTimeoutException("The row labelled \"$label\" carries no drag handle")
            boundsOf(handle)
        }
    }

    fun dragRecyclerRowToTop(label: String) {
        val target = firstBoundRowLabel()
        if (target == label) return
        val handle = dragHandleBounds(label)
        val destination = recyclerRowBounds(target)
        RawInput.dragAlongLine(
            from = PointF(handle.exactCenterX(), handle.exactCenterY()),
            to = PointF(handle.exactCenterX(), destination.exactCenterY() - destination.height()),
            stepPx = RawInput.MAX_DRAG_STEP_PX,
            settleMoves = RawInput.SETTLE_MOVES_BEFORE_RELEASE,
        )
        TestEnvironment.waitForIdleSync()
    }

    fun setRecyclerRowChecked(label: String, checked: Boolean) {
        if (isRecyclerRowChecked(label) == checked) return
        if (!isRecyclerRowClickable(label)) {
            throw AssertionError(
                "The row labelled \"$label\" is locked, so its checkbox cannot be changed. " +
                    "ActionSelectionRow forces enabled true on a locked row and clears its click listener."
            )
        }
        tapRecyclerRow(label)
        awaitRecyclerRowChecked(label, checked)
    }

    fun checkOnlyRecyclerRows(labels: Set<String>) {
        allRowLabelsInOrder().forEach { label ->
            if (isRecyclerRowClickable(label)) setRecyclerRowChecked(label, label in labels)
        }
    }

    private fun boundRowsWithPositions(): List<Pair<Int, String>> = readRoot { root ->
        val list = Dialogs.firstDescendant(root, RecyclerView::class.java) ?: throw missingRecycler()
        (0 until list.childCount).mapNotNull { index ->
            val child = list.getChildAt(index)
            val position = list.getChildAdapterPosition(child)
            val label = findTaggedText(child, TITLE_TAG)
            if (position == RecyclerView.NO_POSITION || label == null) null else position to label
        }
    }

    private fun scrollToRecyclerRow(label: String): Int {
        boundRowsWithPositions().firstOrNull { it.second == label }?.let { return it.first }
        val total = recyclerRowCount()
        var position = 0
        while (position < total) {
            scrollRecyclerToPosition(position)
            val bound = boundRowsWithPositions()
            bound.firstOrNull { it.second == label }?.let { return it.first }
            position += bound.size.coerceAtLeast(1)
        }
        throw ConditionTimeoutException(
            "The dialog list holds no row labelled \"$label\" across all $total rows"
        )
    }

    private fun scrollRecyclerToPosition(position: Int) {
        readRoot { root ->
            val list = Dialogs.firstDescendant(root, RecyclerView::class.java) ?: throw missingRecycler()
            (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
                ?: list.scrollToPosition(position)
        }
        TestEnvironment.waitForIdleSync()
    }

    fun awaitRecyclerRowChecked(label: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the dialog row \"$label\" reads checked $expected",
            timeoutMs,
            read = { runCatching { isRecyclerRowChecked(label) }.getOrNull() },
        ) { it == expected }
    }

    fun <T> readRoot(read: (View) -> T): T {
        val root = captureDialogRoot()
        return TestEnvironment.onMainThread { read(root) }
    }

    private fun captureDialogRoot(): View {
        if (!isShowing()) {
            throw ConditionTimeoutException("No settings dialog is on screen to read")
        }
        return Dialogs.dialogRoot()
    }

    private fun recyclerChildFor(root: View, label: String): View {
        val list = Dialogs.firstDescendant(root, RecyclerView::class.java) ?: throw missingRecycler()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .firstOrNull { findTaggedText(it, TITLE_TAG) == label }
            ?: throw ConditionTimeoutException(
                "The dialog holds no bound row labelled \"$label\", it shows " +
                    (0 until list.childCount).mapNotNull { findTaggedText(list.getChildAt(it), TITLE_TAG) }
            )
    }

    private fun findTaggedText(view: View, tag: String): String? =
        view.findViewWithTag<TextView>(tag)?.text?.toString()

    private fun findSeekBarSibling(root: View): TextView? {
        val bar = Dialogs.firstDescendant(root, SeekBar::class.java) ?: return null
        val parent = bar.parent as? ViewGroup ?: return null
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child is TextView) return child
        }
        return null
    }

    private fun <T : View> findViewById(root: View, entryName: String): T? {
        val resources = TestEnvironment.targetContext.resources
        val packages = listOf(TestEnvironment.targetPackage, ANDROID_PACKAGE)
        packages.forEach { packageName ->
            val id = resources.getIdentifier(entryName, "id", packageName)
            if (id != 0) {
                @Suppress("UNCHECKED_CAST")
                (root.findViewById<View>(id) as? T)?.let { return it }
            }
        }
        return null
    }

    private fun boundsOf(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun missingList() = ConditionTimeoutException(
        "The open dialog holds no ListView, so it is not a choice dialog. setSingleChoiceItems and " +
            "setMultiChoiceItems both build an AlertController RecycleListView backed by an adapter that " +
            "holds every entry, only a screenful of which is ever bound to a view."
    )

    private fun missingRecycler() = ConditionTimeoutException(
        "The open dialog holds no RecyclerView, so it is not a row selection dialog"
    )

    private const val TITLE_TAG = "title"
    private const val CHECKBOX_TAG = "checkbox"
    private const val DRAG_HANDLE_TAG = "dragHandle"
    private const val SLIDER_EDGE_INSET = 0.02f
    private const val OVERSHOOT_PX = 200f
    private const val LIST_VIEW_CLASS = "android.widget.ListView"
    private const val RECYCLER_VIEW_CLASS = "androidx.recyclerview.widget.RecyclerView"
}
