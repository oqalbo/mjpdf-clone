// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.google.android.material.checkbox.MaterialCheckBox
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.hasToString
import org.hamcrest.Matchers.instanceOf
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

data class SelectionRow(
    val position: Int,
    val label: String,
    val checked: Boolean,
    val checkboxEnabled: Boolean,
    val dragHandleVisible: Boolean,
    val rowClickable: Boolean,
)

object ActionSelectionDialog {

    const val CHECKBOX_TAG = "checkbox"
    const val TITLE_TAG = "title"
    const val DRAG_HANDLE_TAG = "dragHandle"

    fun isShown(): Boolean = runCatching { recyclerView() }.isSuccess

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("an action selection dialog with its row list is shown", timeoutMs) { isShown() }
    }

    fun awaitDismissed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the action selection dialog is dismissed", timeoutMs) { !isShown() }
    }

    fun rowCount(): Int {
        val list = recyclerView()
        return TestEnvironment.onMainThread { list.adapter?.itemCount ?: 0 }
    }

    fun visibleRows(): List<SelectionRow> {
        val list = recyclerView()
        return TestEnvironment.onMainThread {
            (0 until list.childCount).mapNotNull { index ->
                val child = list.getChildAt(index)
                val position = list.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) null else readRow(child, position)
            }
        }
    }

    fun firstVisibleRow(): SelectionRow = visibleRows().minByOrNull { it.position }
        ?: throw ConditionTimeoutException("The action selection dialog has no bound rows to read")

    fun row(label: String): SelectionRow {
        scrollToLabel(label)
        return visibleRows().firstOrNull { it.label == label }
            ?: throw ConditionTimeoutException(
                "The action selection dialog holds no row labelled \"$label\" once scrolled to it"
            )
    }

    fun allLabelsInOrder(): List<String> {
        val total = rowCount()
        val found = LinkedHashMap<Int, String>()
        var position = 0
        while (position < total) {
            scrollToPosition(position)
            val rows = visibleRows()
            rows.forEach { found[it.position] = it.label }
            position += rows.size.coerceAtLeast(1)
        }
        scrollToPosition(0)
        return (0 until total).map { index ->
            found[index] ?: throw ConditionTimeoutException(
                "Row $index of $total never bound while scrolling the action selection dialog, read ${found.keys}"
            )
        }
    }

    fun setChecked(label: String, checked: Boolean) {
        val current = row(label)
        if (current.checked == checked) return
        if (!current.rowClickable) {
            throw AssertionError(
                "The row labelled \"$label\" is locked, so its checkbox cannot be changed. " +
                    "ActionSelectionRow forces enabled true on a locked row and clears its click listener."
            )
        }
        tapRow(label)
        awaitChecked(label, checked)
    }

    fun tapRow(label: String) {
        val bounds = rowBounds(label)
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.waitForIdleSync()
    }

    fun awaitChecked(label: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the action selection row \"$label\" reads checked $expected",
            timeoutMs,
            read = { row(label).checked },
        ) { it == expected }
    }

    fun checkOnly(labels: Set<String>) {
        allLabelsInOrder().forEach { label ->
            val current = row(label)
            if (current.rowClickable) setChecked(label, labels.contains(label))
        }
    }

    fun rowBounds(label: String): Rect {
        scrollToLabel(label)
        val list = recyclerView()
        return TestEnvironment.onMainThread { boundsOf(visibleChildFor(list, label)) }
    }

    fun dragHandleBounds(label: String): Rect {
        scrollToLabel(label)
        val list = recyclerView()
        return TestEnvironment.onMainThread {
            val handle = visibleChildFor(list, label).findViewWithTag<View>(DRAG_HANDLE_TAG)
                ?: throw ConditionTimeoutException("The row labelled \"$label\" carries no drag handle view")
            boundsOf(handle)
        }
    }

    fun dragRowToTop(label: String) {
        val target = firstVisibleRow()
        if (target.label == label) return
        val handle = dragHandleBounds(label)
        val destination = rowBounds(target.label)
        RawInput.dragAlongLine(
            from = PointF(handle.exactCenterX(), handle.exactCenterY()),
            to = PointF(handle.exactCenterX(), destination.exactCenterY() - destination.height()),
            stepPx = DRAG_STEP_PX,
            settleMoves = RawInput.SETTLE_MOVES_BEFORE_RELEASE,
        )
        TestEnvironment.waitForIdleSync()
    }

    fun apply() = tapDialogButton(android.R.id.button1)

    fun cancel() = tapDialogButton(android.R.id.button2)

    fun reset() = tapDialogButton(android.R.id.button3)

    fun pickerLabels(): List<String> {
        val list = pickerListView()
        return TestEnvironment.onMainThread {
            val adapter = list.adapter ?: return@onMainThread emptyList()
            (0 until adapter.count).map { adapter.getItem(it)?.toString().orEmpty() }
        }
    }

    fun isPickerShown(): Boolean = runCatching { pickerLabels().isNotEmpty() }.getOrDefault(false)

    fun awaitPickerShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("a single choice action picker is shown", timeoutMs) { isPickerShown() }
    }

    fun pickAction(label: String) {
        val labels = pickerLabels()
        if (!labels.contains(label)) {
            throw ConditionTimeoutException(
                "The single choice picker offers no entry labelled \"$label\", it reads $labels"
            )
        }
        onData(allOf(instanceOf(CharSequence::class.java), hasToString(label)))
            .inRoot(isDialog())
            .perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun tapDialogButton(buttonId: Int) {
        onView(withId(buttonId)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(buttonId)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun scrollToLabel(label: String): Int {
        if (visibleRows().any { it.label == label }) return visibleRows().first { it.label == label }.position
        val total = rowCount()
        var position = 0
        while (position < total) {
            scrollToPosition(position)
            val rows = visibleRows()
            rows.firstOrNull { it.label == label }?.let { return it.position }
            position += rows.size.coerceAtLeast(1)
        }
        throw ConditionTimeoutException(
            "The action selection dialog holds no row labelled \"$label\" across all $total rows"
        )
    }

    private fun scrollToPosition(position: Int) {
        val list = recyclerView()
        TestEnvironment.onMainThread {
            (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
                ?: list.scrollToPosition(position)
        }
        TestEnvironment.waitForIdleSync()
    }

    private fun visibleChildFor(list: RecyclerView, label: String): View =
        (0 until list.childCount)
            .map { list.getChildAt(it) }
            .firstOrNull { titleOf(it) == label }
            ?: throw ConditionTimeoutException("No bound row labelled \"$label\" in the action selection dialog")

    private fun readRow(child: View, position: Int): SelectionRow {
        val checkbox = child.findViewWithTag<MaterialCheckBox>(CHECKBOX_TAG)
            ?: throw ConditionTimeoutException("Row $position carries no checkbox tagged $CHECKBOX_TAG")
        val handle = child.findViewWithTag<View>(DRAG_HANDLE_TAG)
            ?: throw ConditionTimeoutException("Row $position carries no drag handle tagged $DRAG_HANDLE_TAG")
        return SelectionRow(
            position = position,
            label = titleOf(child) ?: throw ConditionTimeoutException("Row $position carries no title"),
            checked = checkbox.isChecked,
            checkboxEnabled = checkbox.isEnabled,
            dragHandleVisible = handle.visibility == View.VISIBLE,
            rowClickable = child.hasOnClickListeners(),
        )
    }

    private fun titleOf(child: View): String? =
        child.findViewWithTag<TextView>(TITLE_TAG)?.text?.toString()

    private fun boundsOf(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun recyclerView(): RecyclerView {
        val root = espressoDialogRoot()
        return TestEnvironment.onMainThread {
            Dialogs.firstDescendant(root, RecyclerView::class.java)
                ?: throw ConditionTimeoutException(
                    "The open dialog holds no RecyclerView, so it is not an action selection dialog. " +
                        "showActionSelectionPreferenceDialog sets a RecyclerView as the dialog view."
                )
        }
    }

    private fun pickerListView(): ListView {
        val root = espressoDialogRoot()
        return TestEnvironment.onMainThread {
            Dialogs.firstDescendant(root, ListView::class.java)
                ?: throw ConditionTimeoutException(
                    "The open dialog holds no ListView, so it is not a single choice action picker. " +
                        "setSingleChoiceItems builds an AlertController RecycleListView backed by an adapter " +
                        "that holds every entry, only a screenful of which is ever bound to a view."
                )
        }
    }

    private fun espressoDialogRoot(): View {
        if (!Dialogs.isAnyShowing()) {
            throw ConditionTimeoutException("No dialog window is open")
        }
        return Dialogs.dialogRoot()
    }

    private const val DRAG_STEP_PX = 8f
}
