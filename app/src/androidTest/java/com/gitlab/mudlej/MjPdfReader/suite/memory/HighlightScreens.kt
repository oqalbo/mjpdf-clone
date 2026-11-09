// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import android.app.Activity
import android.view.View
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object HighlightScreens {
    fun activityOrNull(type: Class<out Activity>): Activity? =
        TestEnvironment.resumedActivityOfType(type)

    fun isScanning(type: Class<out Activity>): Boolean = TestEnvironment.onMainThread {
        val activity = activityOrNull(type) ?: return@onMainThread false
        activity.findViewById<View>(R.id.scanProgressBar)?.visibility == View.VISIBLE
    }

    fun scanTarget(type: Class<out Activity>): Int = TestEnvironment.onMainThread {
        val activity = activityOrNull(type) ?: return@onMainThread 0
        activity.findViewById<ProgressBar>(R.id.scanProgressBar)?.max ?: 0
    }

    fun awaitScanFinished(type: Class<out Activity>, timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        Await.until("${type.simpleName} has started its sweep", timeoutMs) {
            activityOrNull(type) != null
        }
        Await.until("${type.simpleName} finishes its sweep", timeoutMs, pollMs = POLL_MS) {
            activityOrNull(type) != null && !isScanning(type)
        }
    }

    fun rowCount(type: Class<out Activity>, recyclerViewId: Int): Int = TestEnvironment.onMainThread {
        val activity = activityOrNull(type) ?: return@onMainThread 0
        activity.findViewById<RecyclerView>(recyclerViewId)?.adapter?.itemCount ?: 0
    }

    fun awaitRowCountAtLeast(
        type: Class<out Activity>,
        recyclerViewId: Int,
        expected: Int,
        timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS,
    ): Int = Await.untilValue(
        "${type.simpleName} lists at least $expected rows",
        timeoutMs,
        pollMs = POLL_MS,
        read = { rowCount(type, recyclerViewId) },
    ) { it >= expected }

    private const val POLL_MS = 250L
}
