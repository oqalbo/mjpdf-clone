// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.os.SystemClock
import java.io.File
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

class ConditionTimeoutException(message: String) : AssertionError(message)

object Await {

    const val SHORT_TIMEOUT_MS = 3_000L
    const val DEFAULT_TIMEOUT_MS = 10_000L
    const val LONG_TIMEOUT_MS = 30_000L
    const val VERY_LONG_TIMEOUT_MS = 120_000L
    const val SCROLL_HANDLE_TIMEOUT_MS = 6_000L
    const val DEFAULT_POLL_MS = 50L
    const val ABSENCE_WINDOW_MS = 1_500L

    fun until(
        condition: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollMs: Long = DEFAULT_POLL_MS,
        predicate: () -> Boolean,
    ) {
        untilValue(condition, timeoutMs, pollMs, { predicate() }) { it }
    }

    fun <T> untilValue(
        condition: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollMs: Long = DEFAULT_POLL_MS,
        read: () -> T,
        predicate: (T) -> Boolean,
    ): T {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastObserved: Any? = NOTHING_OBSERVED
        var lastFailure: Throwable? = null
        while (true) {
            try {
                val observed = read()
                lastObserved = observed
                lastFailure = null
                if (predicate(observed)) return observed
            } catch (error: Throwable) {
                if (error is ConditionTimeoutException) throw error
                lastFailure = error
            }
            if (SystemClock.uptimeMillis() >= deadline) {
                val message = timeoutMessage(condition, timeoutMs, lastObserved, lastFailure)
                throw ConditionTimeoutException(message)
            }
            SystemClock.sleep(pollMs)
        }
    }

    fun <T : Any> untilNotNull(
        condition: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollMs: Long = DEFAULT_POLL_MS,
        read: () -> T?,
    ): T = untilValue(condition, timeoutMs, pollMs, read) { it != null }!!

    fun untilNoThrow(
        condition: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollMs: Long = DEFAULT_POLL_MS,
        action: () -> Unit,
    ) {
        untilValue(condition, timeoutMs, pollMs, { action(); true }) { it }
    }

    fun untilTrueContinuously(
        condition: String,
        stableForMs: Long,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollMs: Long = DEFAULT_POLL_MS,
        predicate: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val stableSince = SystemClock.uptimeMillis()
            var held = true
            while (SystemClock.uptimeMillis() - stableSince < stableForMs) {
                if (!predicate()) {
                    held = false
                    break
                }
                SystemClock.sleep(pollMs)
            }
            if (held) return
            if (SystemClock.uptimeMillis() >= deadline) {
                throw ConditionTimeoutException(
                    "$condition did not hold for ${stableForMs}ms within ${timeoutMs}ms"
                )
            }
            SystemClock.sleep(pollMs)
        }
    }

    fun assertThroughout(
        condition: String,
        windowMs: Long,
        pollMs: Long = DEFAULT_POLL_MS,
        predicate: () -> Boolean,
    ) {
        require(windowMs >= 0L)
        require(pollMs > 0L)
        val started = SystemClock.uptimeMillis()
        while (true) {
            if (!predicate()) {
                throw AssertionError("$condition was violated after ${SystemClock.uptimeMillis() - started}ms")
            }
            val remaining = windowMs - (SystemClock.uptimeMillis() - started)
            if (remaining <= 0L) return
            SystemClock.sleep(minOf(pollMs, remaining))
        }
    }

    fun untilFileExists(file: File, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        until("file ${file.absolutePath} exists", timeoutMs) { file.exists() }
    }

    fun untilFileAbsent(file: File, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        until("file ${file.absolutePath} is gone", timeoutMs) { !file.exists() }
    }

    fun untilPreference(key: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, predicate: (Any?) -> Boolean): Any? =
        untilValue(
            "preference $key satisfies the expected value",
            timeoutMs,
            read = { TestEnvironment.defaultPreferences.all[key] },
            predicate = predicate,
        )

    fun untilCollected(reference: WeakReference<*>, timeoutMs: Long = SHORT_TIMEOUT_MS + 2_000L) {
        until("weak reference is cleared", timeoutMs, pollMs = 200L) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Runtime.getRuntime().gc()
            reference.get() == null
        }
    }

    fun untilCollected(
        reference: WeakReference<*>,
        queue: ReferenceQueue<*>,
        timeoutMs: Long = LONG_TIMEOUT_MS,
    ) {
        until("weak reference is cleared and enqueued", timeoutMs, pollMs = 200L) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Runtime.getRuntime().gc()
            reference.get() == null && queue.poll() === reference
        }
    }

    private val NOTHING_OBSERVED = Any()

    private fun timeoutMessage(condition: String, timeoutMs: Long, lastObserved: Any?, lastFailure: Throwable?): String {
        val observed = when {
            lastFailure != null -> "last poll threw ${lastFailure::class.java.name}: ${lastFailure.message}"
            lastObserved === NOTHING_OBSERVED -> "nothing was observed"
            else -> "last observed value was $lastObserved"
        }
        return "Timed out after ${timeoutMs}ms waiting until $condition, $observed"
    }
}
