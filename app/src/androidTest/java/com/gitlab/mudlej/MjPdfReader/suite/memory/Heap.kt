// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Heap {
    const val SETTLE_TIMEOUT_MS = 5_000L
    const val SETTLE_POLL_MS = 200L
    const val SETTLE_TOLERANCE = 0.01

    fun clearAndroidPieAccessibilityPool() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) return
        TestEnvironment.onMainThread {
            val context = TestEnvironment.targetContext.applicationContext
            val group = FrameLayout(context)
            repeat(ANDROID_PIE_VIEW_LOCATION_POOL_SIZE) {
                group.addView(View(context))
            }
            group.addChildrenForAccessibility(ArrayList())
        }
    }

    fun clearFrameworkActivityRetention() {
        clearAndroidPieAccessibilityPool()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        Thread.getAllStackTraces().keys.filterIsInstance<HandlerThread>()
            .filter { it.name == "queued-work-looper" }
            .forEach { thread ->
                val looper = thread.looper ?: return@forEach
                val finished = CountDownLatch(1)
                if (!Handler(looper).post { finished.countDown() } ||
                    !finished.await(Await.LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                ) {
                    throw ConditionTimeoutException("Android's queued-work handler did not process the collection barrier")
                }
            }
    }

    fun javaHeapInUse(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    fun nativeHeapAllocated(): Long = Debug.getNativeHeapAllocatedSize()

    fun maxMemory(): Long = Runtime.getRuntime().maxMemory()

    fun settle(read: () -> Long = ::javaHeapInUse): Long {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        System.runFinalization()
        runtime.gc()

        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
        var previous = read()
        while (SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(SETTLE_POLL_MS)
            val current = read()
            val larger = maxOf(previous, current).toDouble()
            if (larger == 0.0 || kotlin.math.abs(current - previous) / larger <= SETTLE_TOLERANCE) {
                return current
            }
            previous = current
        }
        return previous
    }

    fun settledJavaHeap(): Long = settle(::javaHeapInUse)

    fun settledNativeHeap(): Long = settle(::nativeHeapAllocated)

    fun totalPssKilobytes(): Long {
        val output = Shell.executeQuietly("dumpsys meminfo ${TestEnvironment.targetPackage}")
        val line = output.lineSequence().firstOrNull { it.contains(TOTAL_PSS_MARKER) }
            ?: output.lineSequence().firstOrNull { it.trimStart().startsWith(TOTAL_MARKER) }
            ?: throw AssertionError(
                "dumpsys meminfo reported no TOTAL row for ${TestEnvironment.targetPackage}. It read:\n" +
                    output.take(MAX_REPORTED_CHARACTERS)
            )
        val value = TOTAL_NUMBER.find(line)?.groupValues?.getOrNull(1)
            ?: throw AssertionError("Could not read a PSS figure out of the meminfo row \"$line\"")
        return value.toLong()
    }

    fun withinBand(reference: Long, observed: Long, bandFraction: Double): Boolean {
        if (reference <= 0L) {
            throw AssertionError(
                "A band comparison was asked for against a reference reading of $reference, which cannot be " +
                    "compared against. Debug.getNativeHeapAllocatedSize can report zero, and every band built " +
                    "on it would otherwise pass unconditionally. Take the reference again, or assert the " +
                    "reference is readable before banding against it. Observed was $observed."
            )
        }
        return observed <= reference * (1.0 + bandFraction)
    }

    fun withinGrowthBytes(reference: Long, observed: Long, maxGrowthBytes: Long): Boolean {
        require(maxGrowthBytes >= 0L) { "A retained-byte budget cannot be negative: $maxGrowthBytes" }
        return observed - reference <= maxGrowthBytes
    }

    fun describe(label: String, reference: Long, observed: Long, bandFraction: Double): String {
        val percent = (bandFraction * 100).toInt()
        val growth = if (reference <= 0L) 0.0 else (observed - reference) * 100.0 / reference
        return "$label: reference ${megabytes(reference)} MB, observed ${megabytes(observed)} MB, " +
            "growth ${"%.1f".format(growth)} percent against a band of $percent percent"
    }

    fun describeGrowthBytes(label: String, reference: Long, observed: Long, maxGrowthBytes: Long): String =
        "$label: reference ${megabytes(reference)} MB, observed ${megabytes(observed)} MB, retained " +
            "${megabytes((observed - reference).coerceAtLeast(0L))} MB against an absolute cap of " +
            "${megabytes(maxGrowthBytes)} MB"

    fun megabytes(bytes: Long): Long = bytes / (1024 * 1024)

    fun allocateBallastLeavingFree(freeFraction: Double): ByteArray {
        val target = ((maxMemory() - javaHeapInUse()) * (1.0 - freeFraction)).toLong()
        var size = target
        while (size > MINIMUM_BALLAST_BYTES) {
            val attempt = runCatching { ByteArray(size.toInt()) }.getOrNull()
            if (attempt != null) return attempt
            size = size * BALLAST_BACKOFF_NUMERATOR / BALLAST_BACKOFF_DENOMINATOR
        }
        throw AssertionError(
            "No ballast of at least ${megabytes(MINIMUM_BALLAST_BYTES)} MB could be allocated against a max " +
                "memory of ${megabytes(maxMemory())} MB, so the constrained heap the test needs was never reached"
        )
    }

    private const val ANDROID_PIE_VIEW_LOCATION_POOL_SIZE = 32
    private const val TOTAL_PSS_MARKER = "TOTAL PSS:"
    private const val TOTAL_MARKER = "TOTAL"
    private const val MAX_REPORTED_CHARACTERS = 600
    private const val MINIMUM_BALLAST_BYTES = 8L * 1024 * 1024
    private const val BALLAST_BACKOFF_NUMERATOR = 3L
    private const val BALLAST_BACKOFF_DENOMINATOR = 4L
    private val TOTAL_NUMBER = Regex("(\\d+)")
}
