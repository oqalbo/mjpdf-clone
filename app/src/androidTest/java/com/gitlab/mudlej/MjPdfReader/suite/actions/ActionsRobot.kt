// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

class ActionsRobot(private val reader: ReaderRobot = ReaderRobot()) {

    val actionsDialog = ActionsDialogRobot()

    fun reader(): ReaderRobot = reader

    fun activity(): MainActivity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
        ?: throw ConditionTimeoutException("No resumed reader activity")

    fun awaitReady(): ReaderState = reader.awaitReady()

    fun awaitReaderWithoutDocument(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until(
            "the reader is resumed with no document open",
            timeoutMs,
        ) {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java) != null &&
                reader.readStateOrNull()?.documentUri.isNullOrEmpty()
        }
    }

    fun runFromActionsDialog(label: String) {
        actionsDialog.open()
        actionsDialog.tap(label)
    }

    fun activityOrNull(): MainActivity? =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity

    fun awaitReaderResumed(timeoutMs: Long = Await.LONG_TIMEOUT_MS): MainActivity =
        Await.untilNotNull("the reader is resumed again", timeoutMs) { activityOrNull() }

    fun isActionBarShown(): Boolean = TestEnvironment.onMainThread {
        activityOrNull()?.supportActionBar?.isShowing == true
    }

    fun awaitActionBarHidden(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the reader action bar is hidden", timeoutMs) { !isActionBarShown() }
    }

    fun awaitActionBarShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the reader action bar is shown", timeoutMs) { isActionBarShown() }
    }

    fun tapPageCentre() {
        val bounds = ReaderRobot.pdfViewBounds()
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun revealOverlayColumn(timeoutMs: Long = OVERLAY_REVEAL_TIMEOUT_MS) {
        Await.until(
            "the fullscreen overlay column comes up after a tap on the page",
            timeoutMs,
            pollMs = OVERLAY_POLL_MS,
        ) {
            if (FullScreenOverlay.isColumnVisible()) return@until true
            tapPageCentre()
            FullScreenOverlay.isColumnVisible()
        }
    }

    fun overlayButtonText(viewId: Int): String? = TestEnvironment.onMainThread {
        (activityOrNull()?.findViewById<android.view.View>(viewId)
            as? com.google.android.material.button.MaterialButton)?.text?.toString()
    }

    fun enterFullscreen() {
        ToolbarItems.tapPrimary()
        awaitActionBarHidden()
    }

    fun tapDialogButton(label: String) {
        onView(withText(label)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun awaitDialogText(text: String) {
        Dialogs.awaitText(text)
        onView(withText(text)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun pressBackUntilReaderResumed() {
        repeat(BACK_PRESS_BUDGET) {
            if (activityOrNull() != null) return
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.uiDevice.waitForIdle()
        }
        awaitReaderResumed()
    }

    fun openReader(fixtureName: String = ActionsFixtures.NORMAL, body: () -> Unit) {
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(fixtureName))).use {
            awaitReady()
            body()
        }
    }

    companion object {

        fun launchWithoutDocument(): Intent = Intent(Intent.ACTION_MAIN).apply {
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
        }

        const val OVERLAY_REVEAL_TIMEOUT_MS = 8_000L
        const val OVERLAY_POLL_MS = 400L
        const val BACK_PRESS_BUDGET = 5
    }
}
