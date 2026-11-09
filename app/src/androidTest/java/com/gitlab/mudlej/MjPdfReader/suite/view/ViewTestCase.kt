// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import android.content.Intent
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.github.barteksc.pdfviewer.PDFView
import org.junit.After
import org.junit.Rule
import org.junit.rules.TestRule
import java.io.File

abstract class ViewTestCase {

    protected val suite = SuiteRules.standard()

    @get:Rule
    val rules: TestRule = suite

    protected val reader = ReaderRobot()

    @After
    fun finishReaders() {
        runCatching { ViewUi.finishLiveReaders() }
    }

    protected fun fixture(name: String): File = ViewFixtures.place(name, suite.testDirectory.directory())

    protected fun seedLongOverlayDelay() {
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)
    }

    protected fun launchAt(file: File): ReaderState {
        TestEnvironment.targetContext.startActivity(
            ReaderRobot.viewIntent(file).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        return awaitReaderReady()
    }

    protected fun openReader(name: String = ViewFixtures.NORMAL): File {
        val file = fixture(name)
        launchAt(file)
        return file
    }

    protected fun awaitReaderReady(): ReaderState =
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)

    protected fun state(): ReaderState = reader.readState()

    protected fun settledFullQuality(): ReaderState {
        reader.awaitMotionSettled()
        return reader.awaitFullQualityRender()
    }

    protected fun readerIdentity(): Int = TestEnvironment.onMainThread { System.identityHashCode(ViewUi.reader()) }

    protected fun finishReader() {
        ViewUi.finishLiveReaders()
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    protected fun backgroundAndReturn(file: File): ReaderState {
        TestEnvironment.uiDevice.pressHome()
        Await.until("the reader leaves the foreground", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java) == null
        }
        Screens.awaitStage(MainActivity::class.java, Stage.STOPPED, Await.LONG_TIMEOUT_MS)
        return launchAt(file)
    }

    protected fun enterFullscreen() {
        Await.untilNoThrow("the fullscreen toolbar item accepts a tap") {
            onView(withId(R.id.toolbarPrimaryActionOption)).perform(ViewActions.click())
        }
        ViewUi.awaitActionBar(showing = false)
    }

    protected fun tapCanvas() {
        RawInput.pressAndHold(ViewUi.canvasCentre(), TAP_HOLD_MS)
    }

    protected fun revealOverlay() {
        repeat(OVERLAY_TAP_ATTEMPTS) {
            if (ViewUi.visibility(R.id.fullScreenButtonsLayout) == View.VISIBLE) return
            tapCanvas()
            val revealed = runCatching {
                ViewUi.awaitVisibility(R.id.fullScreenButtonsLayout, View.VISIBLE, OVERLAY_TIMEOUT_MS)
            }.isSuccess
            if (revealed) return
        }
        throw ConditionTimeoutException(
            "Tapping the page never revealed the fullscreen overlay panel after $OVERLAY_TAP_ATTEMPTS taps"
        )
    }

    protected fun hideOverlay() {
        tapCanvas()
        ViewUi.awaitVisibility(R.id.fullScreenButtonsLayout, View.GONE, OVERLAY_TIMEOUT_MS)
    }

    protected fun enterFullscreenWithOverlay() {
        enterFullscreen()
        revealOverlay()
    }

    protected fun tapOverlayButton(viewId: Int) {
        Await.until("the fullscreen panel has finished appearing") {
            ViewUi.onView(R.id.fullScreenButtonsLayout) {
                it.visibility == View.VISIBLE && it.alpha == 1f && it.translationX == 0f
            }
        }
        val inScrollablePanel = ViewUi.onView(R.id.fullScreenButtonsList) {
            it.findViewById<View>(viewId) != null
        }
        if (inScrollablePanel) {
            onView(withId(viewId)).perform(ViewActions.scrollTo())
        }
        onView(withId(viewId)).perform(com.gitlab.mudlej.MjPdfReader.suite.core.TouchActions.click())
        TestEnvironment.waitForIdleSync()
    }

    protected fun exitFullscreenFromOverlay() {
        revealOverlay()
        tapOverlayButton(R.id.exitFullScreenButton)
        ViewUi.awaitActionBar(showing = true)
    }

    protected fun tapDialogPositive() {
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    protected fun tapDialogNegative() {
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    protected fun awaitDialogWithTitle(titleRes: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val title = ViewUi.string(titleRes)
        if (TestEnvironment.uiDevice.wait(Until.hasObject(By.text(title)), timeoutMs) != true) {
            throw ConditionTimeoutException("No dialog titled \"$title\" appeared within ${timeoutMs}ms")
        }
    }

    protected fun awaitDialogWithTitleGone(titleRes: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val title = ViewUi.string(titleRes)
        if (TestEnvironment.uiDevice.wait(Until.gone(By.text(title)), timeoutMs) != true) {
            throw ConditionTimeoutException("A dialog titled \"$title\" was still showing after ${timeoutMs}ms")
        }
    }

    protected fun assertNoDialogWithTitle(message: String, titleRes: Int) {
        val title = ViewUi.string(titleRes)
        Await.assertThroughout(
            condition = "$message: no dialog titled \"$title\" appears",
            windowMs = DIALOG_ABSENCE_WINDOW_MS,
        ) { !TestEnvironment.uiDevice.hasObject(By.text(title)) }
    }

    protected fun setViewport(zoom: Float, offsetX: Float, offsetY: Float) {
        ViewUi.onView(R.id.pdfView) { view -> (view as PDFView).debugSetViewport(zoom, offsetX, offsetY) }
        reader.awaitMotionSettled()
    }

    protected companion object {
        const val LONG_HIDE_DELAY_MS = 60_000
        const val SHORT_HIDE_DELAY_MS = 1_000
        const val TAP_HOLD_MS = 60L
        const val OVERLAY_TIMEOUT_MS = 6_000L
        const val OVERLAY_TAP_ATTEMPTS = 3
        const val DIALOG_ABSENCE_WINDOW_MS = 1_500L
        const val MOTION_SAMPLES = 3
        const val STABLE_WINDOW_MS = 1_200L
    }
}
