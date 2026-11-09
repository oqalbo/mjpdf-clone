// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import android.content.Intent
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsDialogRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object MemoryReader {
    const val DESCRIPTOR_STABLE_MS = 1_000L
    const val DESCRIPTOR_STABLE_LONG_MS = 2_000L

    fun fixtureViewIntent(name: String): Intent = ReaderRobot.viewIntent(MemoryFixtures.file(name))

    fun activityOrNull(): MainActivity? =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity

    fun activity(): MainActivity = activityOrNull()
        ?: throw ConditionTimeoutException("No resumed reader activity")

    fun pdfView(): PDFView = TestEnvironment.onMainThread {
        activity().findViewById(R.id.pdfView)
            ?: throw ConditionTimeoutException("The reader has no pdfView")
    }

    fun jumpToPage(reader: ReaderRobot, pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState {
        TestEnvironment.onMainThread { pdfView().jumpTo(pageIndex, false) }
        reader.awaitPageIndex(pageIndex, timeoutMs)
        return reader.awaitMotionSettled(timeoutMs)
    }

    fun pageThrough(reader: ReaderRobot, pageIndexes: Iterable<Int>, settleEach: Boolean = true) {
        for (pageIndex in pageIndexes) {
            jumpToPage(reader, pageIndex)
            if (settleEach) reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
        }
    }

    fun closeReaderAndAwaitGone(reader: ReaderRobot) {
        repeat(MAX_BACK_PRESSES) {
            if (!Screens.isInTask(MainActivity::class.java)) return
            reader.pressBack()
            TestEnvironment.uiDevice.waitForIdle()
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    fun awaitDescriptorCount(file: File, expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the descriptor count for ${file.name} settles at $expected",
            timeoutMs,
            read = { FileDescriptors.countFor(file) },
        ) { it == expected }
    }

    fun holdDescriptorCount(file: File, expected: Int, windowMs: Long = DESCRIPTOR_STABLE_MS) {
        Await.assertThroughout(
            condition = "the descriptor count for ${file.name} stays at $expected",
            windowMs = windowMs,
        ) { FileDescriptors.countFor(file) == expected }
    }

    fun totalDescriptorCount(): Int = FileDescriptors.openPaths().size

    fun awaitTotalDescriptorsStable(windowMs: Long = DESCRIPTOR_STABLE_MS): Int {
        var last = totalDescriptorCount()
        Await.untilTrueContinuously(
            condition = "the total descriptor count is stable",
            stableForMs = windowMs,
            timeoutMs = windowMs + Await.LONG_TIMEOUT_MS,
        ) {
            val current = totalDescriptorCount()
            val stable = current == last
            last = current
            stable
        }
        return last
    }

    fun openAction(@StringRes labelRes: Int) {
        val dialog = ActionsDialogRobot()
        dialog.open()
        dialog.awaitTile(string(labelRes))
        dialog.tap(string(labelRes))
    }

    fun cacheDirectory(name: String): File = File(TestEnvironment.targetContext.cacheDir, name)

    fun previewCacheDirectory(): File = cacheDirectory("previews")

    fun coverCacheDirectory(): File = cacheDirectory("covers")

    fun directoryBytes(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun sendTrimMemory(level: String) {
        val output = com.gitlab.mudlej.MjPdfReader.suite.core.Shell.executeWithStderr(
            "am send-trim-memory ${TestEnvironment.targetPackage} $level"
        )
        check(output.isBlank()) { "Trim request $level failed: $output" }
    }

    data class TrimObservation<T>(val before: T, val after: T)

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    fun <T> observeTrimMemory(
        view: PDFView,
        level: String,
        includeImageCaches: Boolean = false,
        read: () -> T,
    ): TrimObservation<T> {
        val expected = when (level) {
            "RUNNING_MODERATE" -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
            "RUNNING_LOW" -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            "RUNNING_CRITICAL" -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            else -> error("Unsupported observed trim level: $level")
        }
        val field = PDFView::class.java.getDeclaredField("previewTrimCallbacks").apply { isAccessible = true }
        val original = TestEnvironment.onMainThread {
            field.get(view) as? ComponentCallbacks2
                ?: throw MissingDebugHookException("The live preview store has no trim callback")
        }
        val locks = TestEnvironment.onMainThread {
            fun fieldValue(owner: Any, name: String): Any =
                checkNotNull(owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner))
            val store = fieldValue(view, "previewStore")
            buildList {
                add(fieldValue(store, "memory"))
                add(fieldValue(store, "pool"))
                if (includeImageCaches) {
                    val covers = com.gitlab.mudlej.MjPdfReader.ui.home.CoverCache.getInstance(view.context)
                    val thumbnails = com.gitlab.mudlej.MjPdfReader.pdf.PageThumbnailCache.debugLiveInstances().last()
                    add(fieldValue(covers, "memoryCache"))
                    add(fieldValue(thumbnails, "memoryCache"))
                }
            }
        }
        fun snapshotTrim(index: Int): TrimObservation<T> = if (index < locks.size) {
            synchronized(locks[index]) { snapshotTrim(index + 1) }
        } else {
            val before = read()
            original.onTrimMemory(expected)
            TrimObservation(before, read())
        }
        val delivered = CountDownLatch(1)
        var observation: TrimObservation<T>? = null
        var failure: Throwable? = null
        val probe = object : ComponentCallbacks2 {
            override fun onTrimMemory(actualLevel: Int) {
                if (actualLevel != expected) {
                    original.onTrimMemory(actualLevel)
                    return
                }
                try {
                    observation = snapshotTrim(0)
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    delivered.countDown()
                }
            }

            override fun onConfigurationChanged(configuration: Configuration) =
                original.onConfigurationChanged(configuration)

            override fun onLowMemory() = original.onLowMemory()
        }
        TestEnvironment.onMainThread {
            view.context.unregisterComponentCallbacks(original)
            view.context.registerComponentCallbacks(probe)
        }
        try {
            sendTrimMemory(level)
            check(delivered.await(Await.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                "The system did not deliver $level to the reader's registered trim callback"
            }
            failure?.let { throw it }
            return checkNotNull(observation)
        } finally {
            TestEnvironment.onMainThread {
                view.context.unregisterComponentCallbacks(probe)
                view.context.registerComponentCallbacks(original)
            }
        }
    }

    fun string(@StringRes resId: Int): String = TestEnvironment.targetContext.getString(resId)

    const val MAX_BACK_PRESSES = 6
}
