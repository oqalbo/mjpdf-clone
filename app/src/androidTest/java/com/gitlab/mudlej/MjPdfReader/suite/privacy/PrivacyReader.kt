// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import android.view.View
import androidx.constraintlayout.helper.widget.Flow
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
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.Taps
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.hamcrest.CoreMatchers.allOf

class PrivacyReader(val reader: ReaderRobot = ReaderRobot()) {

    private val selection = SelectionRobot()

    fun activity(): MainActivity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
        ?: throw ConditionTimeoutException("No resumed reader activity")

    fun awaitReady(): ReaderState =
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)

    fun awaitIdentity(): String = reader.awaitFileHashResolved()

    fun documentUri(): Uri {
        val raw = reader.awaitState("the reader reports the document URI it is using") {
            !it.documentUri.isNullOrEmpty()
        }.documentUri!!
        return Uri.parse(raw)
    }

    fun finishTask() {
        runCatching {
            TestEnvironment.onMainThread {
                TestEnvironment.resumedActivityOfType(MainActivity::class.java)?.finishAndRemoveTask()
            }
        }
        runCatching {
            com.gitlab.mudlej.MjPdfReader.suite.core.Screens
                .awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }
        runCatching {
            TestEnvironment.targetContext.getSystemService(android.app.ActivityManager::class.java)
                ?.appTasks
                ?.filter { it.taskInfo.baseIntent.getBooleanExtra(PDF.incognitoKey, false) }
                ?.forEach { it.finishAndRemoveTask() }
        }
    }

    fun isIncognitoIconVisible(): Boolean = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?.findViewById<View>(R.id.actionbarIncognitoIcon)?.visibility == View.VISIBLE
    }

    fun awaitIncognitoIcon(visible: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until(
            "the action bar incognito icon is ${if (visible) "visible" else "gone"}",
            timeoutMs,
        ) { isIncognitoIconVisible() == visible }
    }

    fun openActions() {
        Await.untilNoThrow("the reader actions toolbar item accepts a tap") {
            onView(withId(R.id.readerActionsOption)).perform(click())
        }
        awaitTile(string(R.string.go_to_page))
    }

    fun awaitTile(label: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Dialogs.awaitActionsDialog(timeoutMs)
        tile(label).check(matches(isDisplayed()))
    }

    fun isTilePresent(label: String): Boolean = Dialogs.hasActionTile(label)

    fun tapTile(label: String) {
        tile(label).perform(scrollTo(), click())
        TestEnvironment.waitForIdleSync()
    }

    fun dismissActions() {
        TestEnvironment.uiDevice.pressBack()
        Await.until("the actions dialog is dismissed") { !Dialogs.hasAnyActionTile() }
    }

    fun incognitoTileLabel(): String =
        if (isTilePresent(string(R.string.incognito_exit))) string(R.string.incognito_exit)
        else string(R.string.incognito)

    fun readIncognitoTileLabel(): String {
        openActions()
        val label = incognitoTileLabel()
        dismissActions()
        return label
    }

    fun toggleIncognito() {
        openActions()
        tapTile(incognitoTileLabel())
        TestEnvironment.waitForIdleSync()
    }

    fun bookmarkTileLabel(): String =
        if (isTilePresent(string(R.string.remove_bookmark))) string(R.string.remove_bookmark)
        else string(R.string.add_bookmark)

    fun readBookmarkTileLabel(): String {
        openActions()
        val label = bookmarkTileLabel()
        dismissActions()
        return label
    }

    fun tapBookmarkTile() {
        openActions()
        tapTile(bookmarkTileLabel())
        TestEnvironment.waitForIdleSync()
    }

    fun openChildScreen(label: String) {
        openActions()
        tapTile(label)
        TestEnvironment.waitForIdleSync()
    }

    fun goToPage(oneBasedPage: Int): ReaderState {
        openActions()
        tapTile(string(R.string.go_to_page))
        Dialogs.awaitDialogTitled(R.string.go_to_page)
        onView(withId(R.id.editText)).inRoot(isDialog())
            .perform(replaceText(oneBasedPage.toString()), closeSoftKeyboard())
        onView(withText(string(R.string.go_to))).inRoot(isDialog()).perform(click())
        reader.awaitPageIndex(oneBasedPage - 1, Await.LONG_TIMEOUT_MS)
        return reader.awaitMotionSettled()
    }

    fun jumpToPageIndex(pageIndex: Int): ReaderState {
        TestEnvironment.onMainThread {
            val view = activity().findViewById<PDFView>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader has no pdfView while jumping to page index $pageIndex")
            view.jumpTo(pageIndex, false)
        }
        reader.awaitPageIndex(pageIndex, Await.LONG_TIMEOUT_MS)
        return reader.awaitFullQualityRender()
    }

    fun createHighlightOnCurrentPage(): ReaderState {
        selection.longPressInsidePage(SELECTION_X_FRACTION, SELECTION_Y_FRACTION)
        selection.dragEndHandleBy(SELECTION_DRAG_PX, 0f)
        tapFirstSwatch()
        return reader.awaitState("the reader reports an unsaved edit", Await.LONG_TIMEOUT_MS) {
            it.hasUnsavedAnnotations == true
        }
    }

    fun tapFirstSwatch() {
        val swatch = Await.untilNotNull("the inline selection card offers a colour swatch") {
            TestEnvironment.onMainThread {
                val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                    ?: return@onMainThread null
                val flow = activity.findViewById<Flow>(R.id.textSelectionSwatchFlow) ?: return@onMainThread null
                flow.referencedIds.firstOrNull()?.let { activity.findViewById<View>(it) }
                    ?.takeIf { it.isShown && it.width > 0 && it.height > 0 }
            }
        }
        Taps.tapCentreOf(swatch)
    }

    fun isDirtyFabVisible(): Boolean = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?.findViewById<View>(R.id.saveAnnotationsFab)?.visibility == View.VISIBLE
    }

    fun awaitDirtyFab(visible: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until(
            "the unsaved annotations button is ${if (visible) "visible" else "gone"}",
            timeoutMs,
        ) { isDirtyFabVisible() == visible }
    }

    fun tapSaveFab() {
        val clicked = TestEnvironment.onMainThread {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?.findViewById<View>(R.id.saveAnnotationsFab)
                ?.takeIf { it.isShown && it.isEnabled }
                ?.performClick() == true
        }
        if (!clicked) throw ConditionTimeoutException("The visible save annotations button refused its click")
        TestEnvironment.waitForIdleSync()
    }

    fun chooseSaveAsNewCopy() {
        Dialogs.awaitText(string(R.string.save_as_new_copy))
        onView(withText(string(R.string.save_as_new_copy))).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun saveAsNewCopy() {
        tapSaveFab()
        chooseSaveAsNewCopy()
    }

    fun commitPendingStampWithoutSaving() {
        tapSaveFab()
        AnnotationDump.awaitNoPendingStamp(Await.LONG_TIMEOUT_MS)
        reader.awaitState("the committed signature becomes an unsaved document edit", Await.LONG_TIMEOUT_MS) {
            it.hasUnsavedAnnotations == true
        }
        val saveAsCopy = string(R.string.save_as_new_copy)
        val destinationShown = runCatching {
            Dialogs.awaitText(saveAsCopy, Await.SHORT_TIMEOUT_MS)
        }.isSuccess
        if (!destinationShown) tapSaveFab()
        Dialogs.awaitText(saveAsCopy)
        onView(withText(saveAsCopy)).inRoot(isDialog()).check(matches(isDisplayed()))
        TestEnvironment.uiDevice.pressBack()
        Await.until("the save destination sheet is dismissed") {
            !TestEnvironment.uiDevice.hasObject(By.text(string(R.string.save_as_new_copy)))
        }
    }

    fun openSignatureDialog() {
        openActions()
        tapTile(string(R.string.add_signature))
        Dialogs.awaitAnyShowing()
        onView(withId(R.id.signatureView)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun drawSignatureStroke() {
        val pad = Await.untilNotNull(
            "the signature pad has a size on screen. It lives in the dialog's own window, so the resumed " +
                "activity's decor view never holds it and must not be searched."
        ) {
            runCatching { captureDialogView(R.id.signatureView) }.getOrNull()
                ?.takeIf { view ->
                    TestEnvironment.onMainThread { view.isShown && view.width > 0 && view.height > 0 }
                }
        }
        val centre = Taps.centreOf(pad)
        RawInput.dragAlongLine(
            PointF(centre.x - SIGNATURE_STROKE_PX, centre.y),
            PointF(centre.x + SIGNATURE_STROKE_PX, centre.y),
        )
        TestEnvironment.waitForIdleSync()
    }

    fun tapSignaturePlace() {
        onView(withText(string(R.string.signature_place))).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun triggerCropMargins() {
        openActions()
        tapTile(string(R.string.crop_margins))
        TestEnvironment.waitForIdleSync()
    }

    fun chooseReadingDirection(directionTitleRes: Int) {
        openActions()
        tapTile(string(R.string.reading_direction))
        val label = string(directionTitleRes)
        Await.until("the reading direction dialog reports \"$label\" as the chosen row") {
            val row = TestEnvironment.uiDevice.findObject(By.text(label)) ?: return@until false
            if (row.isChecked) return@until true
            runCatching { row.click() }
            TestEnvironment.uiDevice.waitForIdle()
            TestEnvironment.uiDevice.findObject(By.text(label))?.isChecked == true
        }
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun autoScrollSpeedText(): String? = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?.findViewById<android.widget.TextView>(R.id.autoScrollSpeedText)
            ?.text
            ?.toString()
    }

    fun tapPageCentre() {
        val state = reader.readState()
        val pageRect = state.pageRect
            ?: throw ConditionTimeoutException("The reader state hook reports no page rectangle yet")
        val centreX = pageRect.left + pageRect.width() / 2f
        val centreY = pageRect.top + pageRect.height() / 2f
        val viewWidth = state.viewWidth ?: 0
        val viewHeight = state.viewHeight ?: 0
        if (centreX < 0f || centreY < 0f || centreX > viewWidth || centreY > viewHeight) {
            throw ConditionTimeoutException(
                "The centre of the page sits at ($centreX, $centreY) outside the ${viewWidth}x$viewHeight " +
                    "viewport, so a tap there would miss the page entirely. Read: $state"
            )
        }
        val origin = readerOriginOnScreen()
        Taps.tap(PointF(origin.x + centreX, origin.y + centreY))
    }

    private fun readerOriginOnScreen(): PointF = TestEnvironment.onMainThread {
        val view = activity().findViewById<View>(R.id.pdfView)
            ?: throw ConditionTimeoutException("The reader has no pdfView")
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        PointF(location[0].toFloat(), location[1].toFloat())
    }

    fun enterFullscreen() {
        Await.untilNoThrow("the fullscreen toolbar item accepts a tap") {
            onView(withId(R.id.toolbarPrimaryActionOption)).perform(click())
        }
    }

    fun revealOverlay() {
        repeat(OVERLAY_TAP_ATTEMPTS) {
            if (visibilityOf(R.id.fullScreenButtonsLayout) == View.VISIBLE) return
            RawInput.pressAndHold(canvasCentre(), CANVAS_TAP_HOLD_MS)
            val revealed = runCatching {
                Await.until("the fullscreen overlay panel is visible", OVERLAY_TIMEOUT_MS) {
                    visibilityOf(R.id.fullScreenButtonsLayout) == View.VISIBLE
                }
            }.isSuccess
            if (revealed) return
        }
        throw ConditionTimeoutException(
            "Tapping the page never revealed the fullscreen overlay panel after $OVERLAY_TAP_ATTEMPTS taps"
        )
    }

    fun tapOverlayButton(viewId: Int) {
        val handled = TestEnvironment.onMainThread {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?.findViewById<View>(viewId)
                ?.takeIf { it.isShown && it.isEnabled && it.isClickable }
                ?.performClick()
        }
        if (handled != true) {
            throw ConditionTimeoutException(
                "The visible reader control ${TestEnvironment.targetContext.resources.getResourceEntryName(viewId)} " +
                    "did not handle performClick()"
            )
        }
        TestEnvironment.waitForIdleSync()
    }

    fun startAutoScrollAndRaiseSpeed(steps: Int) {
        enterFullscreen()
        revealOverlay()
        tapOverlayButton(R.id.autoScrollButton)
        Await.until("the auto scroll speed controls are shown") {
            visibilityOf(R.id.autoScrollLayout) == View.VISIBLE
        }
        tapOverlayButton(R.id.toggleAutoScrollButton)
        reader.awaitState("the reader reports auto scrolling") { it.autoScrolling == true }

        val start = autoScrollSpeedText()?.toIntOrNull()
            ?: throw ConditionTimeoutException(
                "The auto scroll speed readout is not showing a number, it reads ${autoScrollSpeedText()}"
            )
        repeat(steps) { step ->
            val target = (start + step + 1).toString()
            Await.until("the auto scroll speed readout reaches $target", Await.DEFAULT_TIMEOUT_MS) {
                if (autoScrollSpeedText() == target) return@until true
                if (visibilityOf(R.id.autoScrollLayout) != View.VISIBLE) revealOverlay()
                tapOverlayButton(R.id.incScrollSpeedButton)
                autoScrollSpeedText() == target
            }
        }
    }

    fun visibilityOf(viewId: Int): Int = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?.findViewById<View>(viewId)?.visibility ?: View.GONE
    }

    fun canvasCentre(): PointF {
        val view = Await.untilNotNull("the reader canvas has a size on screen") {
            TestEnvironment.onMainThread {
                TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                    ?.findViewById<View>(R.id.pdfView)
                    ?.takeIf { it.width > 0 && it.height > 0 }
            }
        }
        return Taps.centreOf(view)
    }

    private fun tile(label: String) =
        onView(allOf(withContentDescription(label), isDisplayed())).inRoot(isDialog())

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun captureDialogView(viewId: Int): View {
        var captured: View? = null
        onView(withId(viewId)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            captured = view
        }
        return captured ?: throw ConditionTimeoutException(
            "The open dialog holds no view ${TestEnvironment.targetContext.resources.getResourceEntryName(viewId)}"
        )
    }

    companion object {

        const val SELECTION_X_FRACTION = 0.3f
        const val SELECTION_Y_FRACTION = 0.3f
        const val SELECTION_DRAG_PX = 140f
        const val SIGNATURE_STROKE_PX = 60f
        const val CANVAS_TAP_HOLD_MS = 60L
        const val OVERLAY_TAP_ATTEMPTS = 4
        const val OVERLAY_TIMEOUT_MS = 3_000L

        fun viewIntent(name: String): Intent = ReaderRobot.viewIntent(PrivacyFixtures.uri(name))

        fun onlineViewIntent(url: String): Intent =
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setClass(TestEnvironment.targetContext, MainActivity::class.java)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        fun incognitoOnlineViewIntent(url: String): Intent =
            onlineViewIntent(url).putExtra(PDF.incognitoKey, true)

        fun incognitoViewIntent(uri: Uri): Intent = ReaderRobot.viewIntent(uri).putExtra(PDF.incognitoKey, true)

        fun incognitoViewIntent(name: String): Intent = incognitoViewIntent(PrivacyFixtures.uri(name))

        fun startFromOutside(intent: Intent) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            TestEnvironment.targetContext.startActivity(intent)
        }
    }
}
