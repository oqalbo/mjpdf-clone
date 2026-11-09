// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.view.View
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderStateSource
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

data class SaveFabState(
    val visible: Boolean,
    val enabled: Boolean,
    val alpha: Float,
)

object ActionsSaveGate {

    const val SAVING_ALPHA = 0.5f
    const val IDLE_ALPHA = 1.0f

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

    fun isSaving(): Boolean? = ReaderStateSource.read()?.savingAnnotations

    fun awaitSavingState(expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the reader state hook reports saving $expected",
            timeoutMs,
            read = { isSaving() },
        ) { it == expected }
    }

    fun saveFab(): SaveFabState = fabState(R.id.saveAnnotationsFab)

    fun discardFab(): SaveFabState = fabState(R.id.discardAnnotationsFab)

    fun awaitSaveFabVisible(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the save annotations button is visible", timeoutMs) { saveFab().visible }
    }

    fun reset() {
        runCatching { setSaving(false) }
        runCatching { AnnotationTestHooks.reset() }
    }

    private fun fabState(viewId: Int): SaveFabState = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
            ?: return@onMainThread SaveFabState(visible = false, enabled = false, alpha = 0f)
        val fab = activity.findViewById<FloatingActionButton>(viewId)
            ?: return@onMainThread SaveFabState(visible = false, enabled = false, alpha = 0f)
        SaveFabState(
            visible = fab.visibility == View.VISIBLE,
            enabled = fab.isEnabled,
            alpha = fab.alpha,
        )
    }
}
