// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Rule
import java.io.File

abstract class ResilienceTestCase {

    protected val suite = SuiteRules.standard()

    @get:Rule
    val rules: SuiteRules = suite

    protected val reader = ReaderRobot()

    protected fun testDirectory(): File = suite.testDirectory.directory()

    protected fun fixture(name: String): File = ResilienceFixtures.place(name, testDirectory())

    @After
    fun releaseResilienceState() {
        runCatching { AnnotationTestHooks.reset() }
        runCatching { AnnotationTestHooks.clearSaveFaultMode(TestEnvironment.targetContext) }
        runCatching { ReaderProbe.finishLiveReaders() }
    }

    protected fun openReader(name: String = ResilienceFixtures.NORMAL, block: (File) -> Unit) {
        val file = fixture(name)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            awaitReaderReady()
            block(file)
        }
    }

    protected fun launchReaderOutsideScenario(intent: Intent): MainActivity {
        ReaderProbe.startReader(intent)
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitReaderReady()
        return ReaderProbe.requireResumedReader()
    }

    protected fun awaitReaderReady(): ReaderState =
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)

    protected fun state(): ReaderState = reader.readState()

    protected fun settledFullQuality(): ReaderState {
        reader.awaitMotionSettled()
        return reader.awaitFullQualityRender()
    }

    protected fun rotateReaderToLandscape() {
        TestEnvironment.uiDevice.setOrientationLeft()
        TestEnvironment.uiDevice.waitForIdle()
        awaitReaderViewport(landscape = true)
    }

    protected fun rotateReaderToPortrait() {
        TestEnvironment.uiDevice.setOrientationNatural()
        TestEnvironment.uiDevice.waitForIdle()
        awaitReaderViewport(landscape = false)
    }

    protected fun awaitReaderViewport(landscape: Boolean) {
        reader.awaitState(
            condition = if (landscape) "the reader viewport becomes wider than tall"
            else "the reader viewport becomes taller than wide",
            timeoutMs = Await.LONG_TIMEOUT_MS,
        ) { readerState ->
            val width = readerState.viewWidth
            val height = readerState.viewHeight
            width != null && height != null && width > 0 && height > 0 &&
                if (landscape) width > height else height > width
        }
        reader.awaitRenderIdle()
    }

    protected fun backgroundReader() {
        TestEnvironment.uiDevice.pressHome()
        Await.until("the reader leaves the foreground", Await.LONG_TIMEOUT_MS) {
            ReaderProbe.resumedReaderOrNull() == null
        }
        Screens.awaitStage(MainActivity::class.java, Stage.STOPPED, Await.LONG_TIMEOUT_MS)
    }

    protected fun returnToReaderTask(file: File): ReaderState {
        ReaderProbe.startReader(ReaderRobot.viewIntent(file))
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        return awaitReaderReady()
    }

    protected fun scrollToPage(target: Int) {
        val current = state().page ?: 0
        if (current == target) return
        val forward = target > current
        var guard = 0
        while (guard++ < PAGE_STEP_LIMIT) {
            val page = state().page ?: 0
            if (forward && page >= target) break
            if (!forward && page <= target) break
            if (forward) reader.swipeForward() else reader.swipeBackward()
        }
        reader.awaitMotionSettled()
        val landed = state().page
        if (landed != target) {
            throw ConditionTimeoutException(
                "Swiping stopped on page index $landed rather than $target after $guard drags"
            )
        }
    }

    protected fun zoomKeepingPage(zoom: Float) {
        val before = state()
        val pageBefore = before.page
        val ratio = zoom / (before.zoom ?: 1f)
        ReaderProbe.onReaderView(com.gitlab.mudlej.MjPdfReader.R.id.pdfView) { view ->
            (view as com.github.barteksc.pdfviewer.PDFView).debugSetViewport(
                zoom,
                (before.offsetX ?: 0f) * ratio,
                (before.offsetY ?: 0f) * ratio,
            )
        }
        reader.awaitMotionSettled()
        reader.awaitZoom { kotlin.math.abs(it - zoom) <= ZOOM_TOLERANCE }
        val after = state()
        if (after.page != pageBefore) {
            throw AssertionError(
                "Zooming to $zoom moved the reader from page index $pageBefore to ${after.page}. Page offsets " +
                    "scale with zoom, so the offsets have to be scaled by the same ratio the zoom changed by, " +
                    "otherwise the setup lands on a different page and everything asserted afterwards is " +
                    "measuring the wrong starting position"
            )
        }
    }

    protected fun jumpToPage(target: Int) {
        ReaderProbe.onReaderView(com.gitlab.mudlej.MjPdfReader.R.id.pdfView) { view ->
            (view as com.github.barteksc.pdfviewer.PDFView).jumpTo(target)
        }
        reader.awaitPageIndex(target, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled()
    }

    protected fun deleteFixtureCopy(file: File) {
        Fixtures.delete(file)
        Await.untilFileAbsent(file, Await.SHORT_TIMEOUT_MS)
    }

    protected companion object {
        const val PAGE_STEP_LIMIT = 200
        const val SETTLE_WINDOW_MS = 1_000L
        const val ZOOM_TOLERANCE = 0.05f
    }
}
