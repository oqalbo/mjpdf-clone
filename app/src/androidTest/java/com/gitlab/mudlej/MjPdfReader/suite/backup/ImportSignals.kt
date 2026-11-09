// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import com.gitlab.mudlej.MjPdfReader.core.debug.BackupDebugHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity

object ImportSignals {
    const val OUTCOME_REJECTED = BackupDebugHooks.OUTCOME_REJECTED
    const val OUTCOME_SAFETY_ABORTED = BackupDebugHooks.OUTCOME_SAFETY_ABORTED
    const val OUTCOME_IMPORTED = BackupDebugHooks.OUTCOME_IMPORTED
    const val OUTCOME_FAILED_AFTER_WIPE = BackupDebugHooks.OUTCOME_FAILED_AFTER_WIPE

    fun completions(): Int = BackupDebugHooks.importCompletions()

    fun isRunning(): Boolean = BackupDebugHooks.importRunning()

    fun lastOutcome(): String = BackupDebugHooks.lastImportOutcome()

    fun requireRestartSuppressed() {
        if (!BackupDebugHooks.shouldSuppressRestart(TestEnvironment.targetContext)) {
            throw ConditionTimeoutException(
                "Hook H2 is not armed, so the import about to start would call exit(0) and kill the " +
                    "instrumentation process along with the app. BackupCleanupRule arms it for every test in " +
                    "this surface, so a test seeing this message is not using BackupTestCase."
            )
        }
    }

    fun awaitImport(timeoutMs: Long = Await.LONG_TIMEOUT_MS, action: () -> Unit): String {
        requireRestartSuppressed()
        val before = completions()
        action()
        awaitCompletionAfter(before, timeoutMs)
        return lastOutcome()
    }

    fun awaitCompletionAfter(before: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the import pipeline records a completion past $before",
            timeoutMs,
            read = { completions() },
        ) { it > before }
        Await.until("the import pipeline is no longer running", timeoutMs) { !isRunning() }
    }

    fun assertNoImportWithin(windowMs: Long = Await.SHORT_TIMEOUT_MS) {
        val before = completions()
        Await.assertThroughout(
            condition = "no import pipeline run completes",
            windowMs = windowMs,
        ) { completions() == before && !isRunning() }
    }

    fun failNextPrepareImport() {
        BackupDebugHooks.failNextPrepareImport()
    }

    fun clearProgressDialog(scenario: ActivityScenario<SettingsActivity>, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.recreate()
        scenario.moveToState(Lifecycle.State.RESUMED)
        TestEnvironment.waitForIdleSync()
        BackupPage.awaitShown(timeoutMs)
        Await.until("the rebuilt settings window has input focus", timeoutMs) {
            TestEnvironment.onMainThread {
                TestEnvironment.resumedActivityOfType(SettingsActivity::class.java)?.hasWindowFocus() == true
            }
        }
        Await.until("the import progress dialog is gone after the rebuild", timeoutMs) {
            BackupDialog.title() != BackupStrings.importRunning
        }
    }
}
