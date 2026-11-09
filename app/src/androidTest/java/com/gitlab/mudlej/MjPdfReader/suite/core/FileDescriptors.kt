// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.system.Os
import java.io.File

object FileDescriptors {

    const val SETTLE_WINDOW_MS = 1_000L

    private val root = File("/proc/self/fd")

    fun openPaths(): List<String> =
        readableEntries().mapNotNull { entry ->
            runCatching { Os.readlink(entry.absolutePath) }.getOrNull()
        }

    fun totalDescriptorCount(): Int = readableEntries().size

    fun readableEntries(): List<File> {
        val entries = root.listFiles()
        if (entries == null || entries.isEmpty()) {
            throw AssertionError(
                "${root.absolutePath} listed ${entries?.size ?: 0} entries. A running process always holds at " +
                    "least its own standard descriptors and the APK it was loaded from, so an empty listing " +
                    "means the descriptor oracle cannot see anything at all rather than that nothing is open. " +
                    "Without this check every count would read zero and every assertion that no descriptor " +
                    "leaked would pass while measuring nothing."
            )
        }
        return entries.toList()
    }

    fun countFor(path: String): Int = openPaths().count { it == path }

    fun countFor(file: File): Int = countFor(file.absolutePath)

    fun countsFor(files: Iterable<File>): Map<File, Int> {
        val countsByPath = openPaths().groupingBy { it }.eachCount()
        return files.associateWith { file -> countsByPath[file.absolutePath] ?: 0 }
    }

    fun describe(file: File): String {
        val matching = openPaths().filter { it == file.absolutePath }
        return if (matching.isEmpty()) "no descriptor resolves to ${file.absolutePath}"
        else "${matching.size} descriptor(s) resolve to ${file.absolutePath}"
    }

    fun awaitCount(file: File, expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int =
        Await.untilValue(
            condition = "exactly $expected open descriptors resolve to ${file.absolutePath}",
            timeoutMs = timeoutMs,
            read = { countFor(file) },
        ) { it == expected }

    fun awaitAtLeast(file: File, expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int =
        Await.untilValue(
            condition = "at least $expected open descriptors resolve to ${file.absolutePath}",
            timeoutMs = timeoutMs,
            read = { countFor(file) },
        ) { it >= expected }

    fun awaitNoneFor(file: File, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "no file descriptor resolves to ${file.absolutePath}",
            timeoutMs,
            read = { countFor(file) },
        ) { it == 0 }
    }

    fun holdNoneFor(file: File, windowMs: Long) {
        Await.assertThroughout(
            condition = "no file descriptor resolves to ${file.absolutePath}",
            windowMs = windowMs,
        ) { countFor(file) == 0 }
    }

    fun awaitSettledCount(file: File, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int {
        var last = countFor(file)
        Await.untilTrueContinuously(
            condition = "the open descriptor count for ${file.absolutePath} stops moving",
            stableForMs = SETTLE_WINDOW_MS,
            timeoutMs = timeoutMs,
        ) {
            val now = countFor(file)
            val stable = now == last
            last = now
            stable
        }
        return countFor(file)
    }

    fun awaitSettledCountOf(file: File, expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the open descriptor count for ${file.absolutePath} settles on $expected",
            timeoutMs = timeoutMs,
            read = { countFor(file) },
        ) { it == expected }
        Await.untilTrueContinuously(
            condition = "the open descriptor count for ${file.absolutePath} stays at $expected",
            stableForMs = SETTLE_WINDOW_MS,
            timeoutMs = timeoutMs,
        ) { countFor(file) == expected }
    }
}
