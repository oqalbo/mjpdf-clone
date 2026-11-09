// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import java.util.Locale

object CrashGate {

    const val PDFIUM_MAIN_THREAD_MARKER = "pdfium work on main thread"

    val ACCEPTED_MAIN_THREAD_PDFIUM_PATHS = setOf(
        "getPageLinks",
        "getFormFieldAtPoint",
        "ensureTextPage",
    )

    const val ACCEPTED_MAIN_THREAD_PDFIUM_REASON =
        "getPageLinks, getFormFieldAtPoint and ensureTextPage answer a touch inline on purpose. " +
            "BUG-003 and BUG-007 were closed as will not fix on 2026-08-10 after measurement: the two tap " +
            "lookups cost less than the thread hop that would carry them away, and the long press text page " +
            "open lands on a gesture the user has already held for half a second. Any other path is still a " +
            "defect and still fails."

    fun mainThreadPdfiumLines(lines: Sequence<String>): List<String> =
        lines.filter { it.contains(PDFIUM_MAIN_THREAD_MARKER) }.toList()

    fun unacceptedMainThreadPdfiumLines(lines: Sequence<String>): List<String> =
        mainThreadPdfiumLines(lines).filterNot { isAcceptedMainThreadPdfium(it) }

    fun isAcceptedMainThreadPdfium(line: String): Boolean {
        val path = MAIN_THREAD_PDFIUM_PATH.find(line)?.groupValues?.get(1) ?: return false
        return path in ACCEPTED_MAIN_THREAD_PDFIUM_PATHS
    }

    private val MAIN_THREAD_PDFIUM_PATH = Regex("$PDFIUM_MAIN_THREAD_MARKER: (\\w+)")

    fun timestampNow(): String {
        val now = System.currentTimeMillis()
        return String.format(Locale.ROOT, "%d.%03d", now / 1000, now % 1000)
    }

    fun fatalLinesSince(since: String): List<String> =
        Shell.executeQuietly("logcat -d -b crash -v threadtime -T $since")
            .lineSequence()
            .filter { it.contains(TestEnvironment.targetPackage) }
            .toList()

    fun assertNoFatalSince(since: String) {
        val fatal = fatalLinesSince(since)
        if (fatal.isNotEmpty()) {
            throw AssertionError(
                "The app crashed during this test, the crash buffer holds:\n${fatal.joinToString("\n")}"
            )
        }
    }
}
