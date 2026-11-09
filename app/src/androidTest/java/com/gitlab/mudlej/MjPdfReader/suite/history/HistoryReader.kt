// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.view.View
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
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.RootDescription
import com.gitlab.mudlej.MjPdfReader.suite.state.RootDescriptionParser
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

class HistoryReader(val reader: ReaderRobot = ReaderRobot()) {

    fun activity(): MainActivity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
        ?: throw ConditionTimeoutException("No resumed reader activity")

    fun awaitReady(): ReaderState = reader.awaitReady()

    fun pdfView(): View = TestEnvironment.onMainThread {
        activity().findViewById(R.id.pdfView)
            ?: throw ConditionTimeoutException("The reader has no pdfView")
    }

    fun tapAtPageFraction(xFraction: Float, yFraction: Float): Point =
        reader.tapAtPageFraction(xFraction, yFraction, syncMainThread = false)

    fun zoomToAbove(pageIndex: Int, zoom: Float): ReaderState {
        TestEnvironment.onMainThread { (pdfView() as PDFView).debugSetZoomAtPageOrigin(pageIndex, zoom) }
        return reader.awaitZoom { it > 1.0f }
    }

    fun zoomTo(pageIndex: Int, zoom: Float): ReaderState {
        TestEnvironment.onMainThread { (pdfView() as PDFView).debugSetZoomAtPageOrigin(pageIndex, zoom) }
        return reader.awaitZoom { kotlin.math.abs(it - zoom) <= ZOOM_TOLERANCE }
    }

    fun openActionsDialog() {
        Await.untilNoThrow("the reader actions toolbar item is tappable") {
            onView(withId(R.id.readerActionsOption)).perform(click())
        }
        awaitTileShown(string(R.string.go_to_page))
    }

    fun awaitTileShown(label: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Dialogs.awaitActionsDialog(timeoutMs)
        tileInteraction(label).check(matches(isDisplayed()))
    }

    fun tapTile(label: String) {
        tileInteraction(label).perform(scrollTo(), click())
        TestEnvironment.waitForIdleSync()
    }

    fun tileView(label: String): View {
        tileInteraction(label).perform(scrollTo())
        return AnnotationDialogs.capture(withContentDescription(label)) { it }
    }

    fun tileIsEnabled(label: String): Boolean {
        val tile = tileView(label)
        return TestEnvironment.onMainThread { tile.isClickable && tile.alpha == FULL_ALPHA }
    }

    fun tileAlpha(label: String): Float {
        val tile = tileView(label)
        return TestEnvironment.onMainThread { tile.alpha }
    }

    fun isTilePresent(label: String): Boolean = Dialogs.hasActionTile(label)

    fun isActionsDialogShown(): Boolean = Dialogs.hasAnyActionTile()

    fun dismissActionsDialog() {
        TestEnvironment.uiDevice.pressBack()
        Await.until("the actions dialog is dismissed") { !isActionsDialogShown() }
    }

    fun jumpToPage(oneBasedPage: Int): ReaderState {
        openActionsDialog()
        tapTile(string(R.string.go_to_page))
        enterGoToPage(oneBasedPage)
        reader.awaitPageIndex(oneBasedPage - 1)
        return reader.awaitMotionSettled()
    }

    fun goToPageWithoutMoving(oneBasedPage: Int) {
        openActionsDialog()
        tapTile(string(R.string.go_to_page))
        enterGoToPage(oneBasedPage)
    }

    fun enterGoToPage(oneBasedPage: Int) {
        Await.until("the Go To Page dialog is shown") { isGoToPageDialogShown() }
        onView(withId(R.id.editText)).inRoot(isDialog())
            .perform(replaceText(oneBasedPage.toString()), closeSoftKeyboard())
        onView(withText(string(R.string.go_to))).inRoot(isDialog()).perform(click())
        Await.until("the Go To Page dialog is dismissed") { !isGoToPageDialogShown() }
        reader.awaitMotionSettled()
    }

    fun isGoToPageDialogShown(): Boolean = Dialogs.hasDialogTitled(R.string.go_to_page)

    fun jumpBack(): ReaderState {
        openActionsDialog()
        tapTile(string(R.string.nav_back))
        return reader.awaitMotionSettled()
    }

    fun jumpForward(): ReaderState {
        openActionsDialog()
        tapTile(string(R.string.nav_forward))
        return reader.awaitMotionSettled()
    }

    fun openNavigationHistory(): NavigationHistoryScreen {
        openActionsDialog()
        tapTile(string(R.string.navigation_history))
        return NavigationHistoryScreen.awaitOpen()
    }

    fun toggleIncognito() {
        openActionsDialog()
        tapTile(incognitoTileLabel())
        TestEnvironment.waitForIdleSync()
    }

    fun incognitoTileLabel(): String =
        if (isTilePresent(string(R.string.incognito_exit))) string(R.string.incognito_exit) else string(R.string.incognito)

    fun openInPlace(uri: Uri) {
        TestEnvironment.onMainThread {
            val intent = ReaderRobot.viewIntent(uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            activity().startActivity(intent)
        }
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun rootDescription(): RootDescription? = TestEnvironment.onMainThread {
        val root = activity().findViewById<View>(R.id.mainLayout) ?: return@onMainThread null
        root.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let(RootDescriptionParser::parse)
    }

    fun backStackPages(): List<Int> = pageList(HISTORY_BACK)

    fun forwardStackPages(): List<Int> = pageList(HISTORY_FORWARD)

    fun awaitBackStack(expected: List<Int>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the reader back stack reads $expected",
            timeoutMs,
            read = { backStackPages() },
        ) { it == expected }
    }

    fun awaitBackStackSize(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the reader back stack holds $expected entries",
            timeoutMs,
            read = { backStackPages() },
        ) { it.size == expected }
    }

    fun assertBackStackStaysEmpty(windowMs: Long = STACK_ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the reader back stack stays empty",
            windowMs = windowMs,
        ) { backStackPages().isEmpty() }
    }

    fun awaitDwellPast(thresholdMs: Long, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the reader reports it has dwelt on the current page for more than ${thresholdMs}ms",
            timeoutMs,
            read = { reader.readState().dwellMs },
        ) { it != null && it > thresholdMs }
    }

    fun turnPageForward(): ReaderState {
        val before = reader.readState().page ?: 0
        repeat(PAGE_TURN_DRAGS) {
            reader.swipeForward()
            val state = reader.readState()
            if (state.page != null && state.page != before) return state
        }
        throw ConditionTimeoutException(
            "The reader stayed on page index $before after $PAGE_TURN_DRAGS forward drags, it reads ${reader.readState()}"
        )
    }

    private fun pageList(key: String): List<Int> {
        val raw = rootDescription()?.value(HISTORY_GROUP, key) ?: return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }.map { part ->
            part.toIntOrNull()
                ?: throw AssertionError("The reader history hook holds \"$raw\" which is not a page index list")
        }
    }

    private fun tileInteraction(label: String) =
        onView(withContentDescription(label)).inRoot(isDialog())

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    companion object {

        const val FULL_ALPHA = 1.0f
        const val DISABLED_ALPHA = 0.38f
        const val ALPHA_TOLERANCE = 0.001f
        const val ZOOM_TOLERANCE = 0.01f
        const val STACK_ABSENCE_WINDOW_MS = 1_500L
        const val PAGE_TURN_DRAGS = 4

        const val HISTORY_GROUP = "hist"
        const val HISTORY_BACK = "back"
        const val HISTORY_FORWARD = "forward"

        fun viewIntent(name: String): Intent = ReaderRobot.viewIntent(HistoryFixtures.uri(name))

        fun incognitoViewIntent(name: String): Intent =
            ReaderRobot.viewIntent(HistoryFixtures.uri(name)).putExtra(PDF.incognitoKey, true)
    }
}
