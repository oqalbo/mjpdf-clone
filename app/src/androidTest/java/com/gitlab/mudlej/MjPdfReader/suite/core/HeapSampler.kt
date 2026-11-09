// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.os.SystemClock
import android.os.Debug
import java.io.Closeable
import kotlin.concurrent.thread

class HeapSampler private constructor(private val intervalMs: Long) : Closeable {
    @Volatile
    private var running = true

    @Volatile
    private var peakBytes = 0L

    private val baselineBytes = inUseBytes()

    private val worker = thread(name = "mjpdf-heap-sampler", isDaemon = true) {
        while (running) {
            val sample = inUseBytes()
            if (sample > peakBytes) peakBytes = sample
            SystemClock.sleep(intervalMs)
        }
        val last = inUseBytes()
        if (last > peakBytes) peakBytes = last
    }

    fun baseline(): Long = baselineBytes

    fun peak(): Long = maxOf(peakBytes, inUseBytes())

    fun growthBytes(): Long = peak() - baselineBytes

    fun describe(): String =
        "managed + native heap: baseline ${megabytes(baselineBytes)} MB, peak ${megabytes(peak())} MB, " +
            "growth ${megabytes(growthBytes())} MB"

    override fun close() {
        running = false
        runCatching { worker.join(JOIN_TIMEOUT_MS) }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 500L
        const val JOIN_TIMEOUT_MS = 2_000L
        const val BUDGET_256_MB = 256L * 1024 * 1024
        const val BUDGET_512_MB = 512L * 1024 * 1024
        const val BUDGET_128_MB = 128L * 1024 * 1024

        fun start(intervalMs: Long = DEFAULT_INTERVAL_MS): HeapSampler = HeapSampler(intervalMs)

        fun <T> around(intervalMs: Long = DEFAULT_INTERVAL_MS, block: (HeapSampler) -> T): Pair<T, HeapSampler> {
            val sampler = start(intervalMs)
            return try {
                block(sampler) to sampler
            } finally {
                sampler.close()
            }
        }

        fun inUseBytes(): Long {
            val runtime = Runtime.getRuntime()
            return runtime.totalMemory() - runtime.freeMemory() + Debug.getNativeHeapAllocatedSize()
        }

        fun megabytes(bytes: Long): Long = bytes / (1024 * 1024)
    }
}
