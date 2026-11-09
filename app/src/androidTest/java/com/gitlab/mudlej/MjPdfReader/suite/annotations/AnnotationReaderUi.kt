// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.view.View
import android.widget.ProgressBar
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderStateSource
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

data class FabState(val visible: Boolean, val enabled: Boolean, val alpha: Float)

object AnnotationReaderUi {

    const val SAVING_ALPHA = 0.5f
    const val IDLE_ALPHA = 1.0f
    const val ALPHA_TOLERANCE = 0.001f

    fun saveFab(): FabState = fabState(R.id.saveAnnotationsFab)

    fun discardFab(): FabState = fabState(R.id.discardAnnotationsFab)

    fun awaitSaveFabVisible(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the save annotations button is visible", timeoutMs) { saveFab().visible }
    }

    fun awaitSaveFabGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the save annotations button leaves the screen", timeoutMs) { !saveFab().visible }
    }

    fun assertBothFabsStayGone(windowMs: Long = AnnotationJournalFile.STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "neither annotation button appears",
            windowMs = windowMs,
        ) { !saveFab().visible && !discardFab().visible }
    }

    fun tapSaveFab() = clickFab(R.id.saveAnnotationsFab)

    fun tapDiscardFab() = clickFab(R.id.discardAnnotationsFab)

    fun isProgressShown(): Boolean = TestEnvironment.onMainThread {
        progressBar()?.visibility == View.VISIBLE
    }

    fun isProgressIndeterminate(): Boolean = TestEnvironment.onMainThread {
        progressBar()?.isIndeterminate == true
    }

    fun beginSeededSave() {
        setSaving(true)
        awaitSavingState(true)
    }

    fun endSeededSave() {
        setSaving(false)
        awaitSavingState(false)
    }

    fun setSaving(saving: Boolean) {
        TestEnvironment.onMainThread {
            val target = (TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity)
                ?.annotationDebug()
                ?: throw ConditionTimeoutException("No resumed reader debug target")
            target.setSavingAnnotations(saving)
        }
        TestEnvironment.waitForIdleSync()
    }

    fun awaitSavingState(expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the reader state hook reports saving $expected",
            timeoutMs,
            read = { ReaderStateSource.read()?.savingAnnotations },
        ) { it == expected }
    }

    fun scrollHandleVisible(): Boolean = TestEnvironment.onMainThread {
        scrollHandle()?.visibility == View.VISIBLE
    }

    fun awaitScrollHandleVisible(expected: Boolean, timeoutMs: Long = Await.SCROLL_HANDLE_TIMEOUT_MS) {
        Await.untilValue(
            "the scroll handle reports visible $expected",
            timeoutMs,
            read = { scrollHandleVisible() },
        ) { it == expected }
    }

    fun requireScrollHandleHidden() {
        if (scrollHandleVisible()) {
            throw ConditionTimeoutException(
                "The page handle is already showing, so a later reveal would prove nothing. " +
                    "FullScreenOptionsManager.showAll is what reveals it outside fullscreen, and this test " +
                    "needs it hidden first."
            )
        }
    }

    fun reset() {
        runCatching { setSaving(false) }
        runCatching { AnnotationTestHooks.reset() }
    }

    private fun scrollHandle(): View? {
        val activity = TestEnvironment.resumedActivity() ?: return null
        val pdfView = activity.findViewById<PDFView>(R.id.pdfView) ?: return null
        return (0 until pdfView.childCount)
            .map { pdfView.getChildAt(it) }
            .firstOrNull { it.contentDescription?.toString() == DefaultScrollHandle.DEBUG_HANDLE_DESCRIPTION }
    }

    private fun progressBar(): ProgressBar? =
        TestEnvironment.resumedActivity()?.findViewById(R.id.progressBar)

    private fun fabState(viewId: Int): FabState = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: return@onMainThread FabState(visible = false, enabled = false, alpha = 0f)
        val fab = activity.findViewById<FloatingActionButton>(viewId)
            ?: return@onMainThread FabState(visible = false, enabled = false, alpha = 0f)
        FabState(
            visible = fab.visibility == View.VISIBLE,
            enabled = fab.isEnabled,
            alpha = fab.alpha,
        )
    }

    private fun clickFab(viewId: Int) {
        val accepted = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No reader is resumed to press an annotation button on")
            val fab = activity.findViewById<View>(viewId)
                ?: throw ConditionTimeoutException("The reader has no annotation button ${AnnotationCard.resourceName(viewId)}")
            if (fab.visibility != View.VISIBLE) {
                throw ConditionTimeoutException(
                    "The annotation button ${AnnotationCard.resourceName(viewId)} is not visible, so there is nothing to press. " +
                        "It only appears while the document holds unsaved annotations."
                )
            }
            fab.performClick()
        }
        if (!accepted) {
            throw AssertionError(
                "The annotation button ${AnnotationCard.resourceName(viewId)} refused the click, which is the state " +
                    "MainActivity.updateDirtyUi seeds while a save is running"
            )
        }
        TestEnvironment.waitForIdleSync()
    }
}
