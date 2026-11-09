// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import java.util.Collections
import com.gitlab.mudlej.MjPdfReader.core.debug.ToastRecorder

class TextNoticeRecording internal constructor(private val intervalMs: Long) {

    private val seen = Collections.synchronizedSet(LinkedHashSet<String>())
    private val handler = Handler(Looper.getMainLooper())
    private var toastMark = 0L

    @Volatile
    private var running = false

    private val sampler = object : Runnable {
        override fun run() {
            if (!running) return
            sample()
            handler.postDelayed(this, intervalMs)
        }
    }

    internal fun start() {
        toastMark = ToastRecorder.mark()
        running = true
        handler.post(sampler)
    }

    fun stop() {
        collectToasts()
        running = false
        handler.removeCallbacks(sampler)
    }

    fun seen(): Set<String> {
        if (running) collectToasts()
        return synchronized(seen) { LinkedHashSet(seen) }
    }

    private fun collectToasts() {
        seen.addAll(ToastRecorder.since(toastMark))
    }

    fun awaitSeen(text: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "a notice reading \"$text\" is shown at some point",
            timeoutMs = timeoutMs,
            read = { seen() },
        ) { it.contains(text) }
    }

    fun assertNeverSeen(text: String) {
        val observed = seen()
        if (observed.contains(text)) {
            throw AssertionError(
                "A notice reading \"$text\" was shown while none was expected. " +
                    "Every notice this recording sampled: $observed"
            )
        }
    }

    private fun sample() {
        collectToasts()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        LIVE_STAGES.forEach { stage ->
            monitor.getActivitiesInStage(stage).forEach { activity -> collectFrom(activity) }
        }
    }

    private fun collectFrom(activity: Activity) {
        val label = activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text) ?: return
        if (!label.isShown) return
        val text = label.text?.toString().orEmpty()
        if (text.isNotEmpty()) {
            seen.add(text)
        }
    }

    private companion object {
        val LIVE_STAGES = listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
    }
}

object TextNotices {

    const val SAMPLE_INTERVAL_MS = 100L

    fun record(intervalMs: Long = SAMPLE_INTERVAL_MS): TextNoticeRecording =
        TextNoticeRecording(intervalMs).also { it.start() }
}
