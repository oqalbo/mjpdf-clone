// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import android.app.Activity
import android.widget.TextView
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object IntakeSnackbars {

    private val liveStages = listOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.STOPPED, Stage.CREATED)

    fun textIn(type: Class<out Activity>): String? = TestEnvironment.onMainThread {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        liveStages.asSequence()
            .flatMap { stage -> monitor.getActivitiesInStage(stage).asSequence() }
            .filter { type.isInstance(it) }
            .mapNotNull { activity ->
                activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    ?.text
                    ?.toString()
            }
            .firstOrNull()
    }

    fun awaitTextIn(
        type: Class<out Activity>,
        expected: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ) {
        Await.untilValue(
            "a snackbar reading \"$expected\" is attached to ${type.simpleName}",
            timeoutMs,
            read = { textIn(type) },
        ) { it == expected }
    }
}
