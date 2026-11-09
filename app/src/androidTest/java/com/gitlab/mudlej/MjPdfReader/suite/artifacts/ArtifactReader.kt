// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionDriver
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionPageText
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette

class ArtifactReader {

    private val reader = ReaderRobot()
    private val selection = SelectionRobot()
    private val selectionDriver = SelectionDriver(reader, selection)

    fun state(): ReaderState = reader.readState()

    fun awaitReady(): ReaderState =
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)

    fun selectWordOnCurrentPage(
        horizontalFraction: Float = DEFAULT_WORD_X,
        verticalFraction: Float = DEFAULT_WORD_Y,
    ): ReaderState = selection.longPressInsidePage(horizontalFraction, verticalFraction, Await.LONG_TIMEOUT_MS)

    fun extendSelectionBy(dx: Float, dy: Float): ReaderState =
        selection.dragEndHandleBy(dx, dy, Await.LONG_TIMEOUT_MS)

    fun swipeToPage(pageIndex: Int): ReaderState {
        var swipes = 0
        while (reader.readStateOrNull()?.page != pageIndex && swipes < MAX_SWIPES) {
            reader.swipeForward()
            swipes++
        }
        reader.awaitPageIndex(pageIndex, Await.LONG_TIMEOUT_MS)
        return reader.awaitFullQualityRender()
    }

    fun awaitLandscapeCanvas(): ReaderState {
        TestEnvironment.uiDevice.setOrientationLeft()
        TestEnvironment.uiDevice.waitForIdle()
        Await.until("the reader reconfigures itself into a landscape canvas", Await.LONG_TIMEOUT_MS) {
            val current = reader.readStateOrNull()
            current?.viewWidth != null && current.viewHeight != null && current.viewWidth!! > current.viewHeight!!
        }
        return reader.awaitFullQualityRender()
    }

    fun copySelectionToClipboard(): String {
        clearClipboard()
        tapActionButton(R.id.textSelectionCopyButton)
        return Await.untilValue(
            "the selection reaches the clipboard",
            Await.LONG_TIMEOUT_MS,
            read = { clipboardText() },
        ) { !it.isNullOrBlank() }!!
    }

    fun clipboardText(): String? = TestEnvironment.onMainThread {
        val manager = TestEnvironment.targetContext
            .getSystemService(android.content.ClipboardManager::class.java)
        manager?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(TestEnvironment.targetContext)?.toString()
    }

    fun clearClipboard() = IntentStubs.clearClipboard()

    fun highlightSelection(colour: HighlightPalette = HighlightPalette.YELLOW) {
        tapSwatch(colour)
        Await.until("the reader records an unsaved edit", Await.LONG_TIMEOUT_MS) {
            reader.readStateOrNull()?.hasUnsavedAnnotations == true
        }
    }

    fun highlightWordOnCurrentPage(
        colour: HighlightPalette = HighlightPalette.YELLOW,
        horizontalFraction: Float = DEFAULT_WORD_X,
        verticalFraction: Float = DEFAULT_WORD_Y,
    ) {
        selectWordOnCurrentPage(horizontalFraction, verticalFraction)
        highlightSelection(colour)
    }

    fun highlightExtractedRangeOnCurrentPage(
        page: SelectionPageText,
        range: IntRange,
        colour: HighlightPalette = HighlightPalette.YELLOW,
    ) {
        val selected = selectionDriver.longPressRange(page, range, timeoutMs = Await.LONG_TIMEOUT_MS)
        val start = selected.selectionStart
        val end = selected.selectionEnd
        if (selected.selectionPage != page.pageIndex || start == null || end == null ||
            end <= range.first || start > range.last
        ) {
            throw ConditionTimeoutException(
                "A press aimed at extracted characters ${range.first}..${range.last} on page ${page.pageIndex} " +
                    "did not select that target; the reader reported page ${selected.selectionPage}, $start until $end"
            )
        }
        highlightSelection(colour)
    }

    fun addNoteToSelection(text: String) {
        val annotationsBefore = reader.readState().annotationCount ?: 0
        val field = Await.untilNotNull("the note dialog offers a text field", Await.LONG_TIMEOUT_MS) {
            dialogTextField() ?: run {
                runCatching { clickActionButtonDirectly(R.id.textSelectionNoteButton) }
                dialogTextField()
            }
        }
        TestEnvironment.onMainThread { field.setText(text) }
        TestEnvironment.waitForIdleSync()
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
        Await.until("the Note action is consumed into a new annotation", Await.LONG_TIMEOUT_MS) {
                val current = reader.readStateOrNull()
                current?.hasUnsavedAnnotations == true &&
                (current.annotationCount ?: 0) > annotationsBefore &&
                !current.hasSelection &&
                visibility(R.id.textSelectionActionCard) != View.VISIBLE &&
                !Dialogs.isAnyShowing()
        }
    }

    fun awaitSelectionActionSettled() {
        Await.until("the prior selection and its action card are cleared", Await.LONG_TIMEOUT_MS) {
            val current = reader.readStateOrNull()
            current != null && !current.hasSelection &&
                visibility(R.id.textSelectionActionCard) != View.VISIBLE &&
                !Dialogs.isAnyShowing()
        }
    }

    fun tapHighlightAt(viewPoint: PointF) {
        val screen = selection.toScreen(viewPoint)
        TestEnvironment.uiDevice.click(screen.x.toInt(), screen.y.toInt())
        Await.until("the highlight action card offers a delete button", Await.LONG_TIMEOUT_MS) {
            visibility(R.id.textSelectionDeleteHighlightButton) == View.VISIBLE
        }
    }

    fun deleteActiveHighlight() {
        tapActionButton(R.id.textSelectionDeleteHighlightButton)
        Await.until("the reader records the deletion as an unsaved edit", Await.LONG_TIMEOUT_MS) {
            reader.readStateOrNull()?.hasUnsavedAnnotations == true
        }
    }

    fun tapSave() {
        Await.until("the save button is offered", Await.LONG_TIMEOUT_MS) {
            visibility(R.id.saveAnnotationsFab) == View.VISIBLE
        }
        val clicked = TestEnvironment.onMainThread {
            requireActivity().findViewById<View>(R.id.saveAnnotationsFab).performClick()
        }
        if (!clicked) {
            throw ConditionTimeoutException("The visible save button did not accept its click")
        }
        TestEnvironment.waitForIdleSync()
    }

    fun openSaveChoice() {
        tapSave()
        Await.until("the save destination choice appears", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.hasObject(By.text(string(R.string.save_as_new_copy)))
        }
    }

    fun chooseUpdateSameFile() {
        openSaveChoice()
        tapChoice(string(R.string.update_existing_file))
    }

    fun chooseSaveAsCopy() {
        openSaveChoice()
        tapChoice(string(R.string.save_as_new_copy))
    }

    fun awaitSaveSettled(timeoutMs: Long = SAVE_TIMEOUT_MS) {
        Await.until("the reader reports the save finished", timeoutMs) {
            val current = reader.readStateOrNull()
            current != null && current.savingAnnotations != true && current.hasUnsavedAnnotations != true
        }
    }

    fun hasUnsavedEdits(): Boolean = reader.readStateOrNull()?.hasUnsavedAnnotations == true

    fun clearCurrentSaveDestination() {
        TestEnvironment.onMainThread {
            requireActivity().annotationDebug()?.clearCurrentSaveDestination()
                ?: throw ConditionTimeoutException("The debug reader cannot clear its current save destination")
        }
    }

    fun canvasSize(): Pair<Int, Int> {
        val current = reader.readState()
        val width = current.viewWidth ?: throw ConditionTimeoutException("The hook reports no canvas width")
        val height = current.viewHeight ?: throw ConditionTimeoutException("The hook reports no canvas height")
        return width to height
    }

    fun activityOrNull(): MainActivity? =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity

    fun requireActivity(): MainActivity = activityOrNull()
        ?: throw ConditionTimeoutException("No resumed reader to drive")

    fun openShareQuote() {
        tapActionButton(R.id.textSelectionShareButton)
    }

    private fun tapSwatch(colour: HighlightPalette) {
        val label = string(colour.labelRes)
        val swatch = Await.untilNotNull("the highlight swatch \"$label\" is offered", Await.LONG_TIMEOUT_MS) {
            runCatching { swatchView(label) }.getOrNull()
        }
        val clicked = TestEnvironment.onMainThread {
            swatch.performClick()
        }
        if (!clicked) {
            throw ConditionTimeoutException("The visible highlight swatch \"$label\" did not accept its click")
        }
        TestEnvironment.waitForIdleSync()
    }

    private fun swatchView(label: String): View? = TestEnvironment.onMainThread {
        val activity = activityOrNull() ?: return@onMainThread null
        val container = activity.findViewById<ViewGroup>(R.id.textSelectionActionContent)
            ?: return@onMainThread null
        (0 until container.childCount)
            .map { container.getChildAt(it) }
            .firstOrNull { it.isShown && it.contentDescription?.toString() == label }
    }

    private fun tapActionButton(viewId: Int) {
        Await.until("the selection action button is offered", Await.LONG_TIMEOUT_MS) {
            visibility(viewId) == View.VISIBLE
        }
        onView(withId(viewId)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun clickActionButtonDirectly(viewId: Int) {
        val clicked = TestEnvironment.onMainThread {
            val button = activityOrNull()?.findViewById<View>(viewId)
                ?.takeIf { it.visibility == View.VISIBLE && it.isEnabled }
                ?: return@onMainThread false
            button.performClick()
        }
        if (!clicked) throw ConditionTimeoutException("The visible selection action did not accept its click")
        TestEnvironment.waitForIdleSync()
    }

    private fun tapChoice(label: String) {
        val node = TestEnvironment.uiDevice.wait(
            androidx.test.uiautomator.Until.findObject(By.text(label)),
            Await.DEFAULT_TIMEOUT_MS,
        ) ?: throw ConditionTimeoutException("The save destination choice offers no entry \"$label\"")
        node.click()
        TestEnvironment.waitForIdleSync()
    }

    private fun dialogTextField(): EditText? = runCatching {
        if (!Dialogs.isAnyShowing()) return@runCatching null
        val root = Dialogs.dialogRoot()
        TestEnvironment.onMainThread { Dialogs.firstDescendant(root, EditText::class.java) }
    }.getOrNull()

    private fun visibility(viewId: Int): Int = TestEnvironment.onMainThread {
        activityOrNull()?.findViewById<View>(viewId)?.visibility ?: View.GONE
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    companion object {
        const val DEFAULT_WORD_X = 0.3f
        const val DEFAULT_WORD_Y = 0.15f
        const val SAVE_TIMEOUT_MS = 180_000L
        const val MAX_SWIPES = 12
    }
}
