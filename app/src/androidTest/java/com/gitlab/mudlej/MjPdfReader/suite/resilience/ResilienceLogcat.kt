// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate

object ResilienceLogcat {

    const val STALL_MARKER = "stall ended: "
    const val PREVIEW_MARKER = "preview p"
    const val WINDOW_LEAK_MARKER = "WindowLeaked"
    const val EXTRACTOR_FAILURE_MARKER = "Failed to open PdfExtractor"

    private val stallPattern = Regex("stall ended: (\\d+)ms")
    private val previewPattern = Regex("preview p(\\d+) ")

    fun since(): String = AppLogcat.since()

    fun stallsSince(since: String): List<Int> =
        AppLogcat.appLinesSince(since)
            .filter { it.contains(STALL_MARKER) }
            .mapNotNull { line -> stallPattern.find(line)?.groupValues?.get(1)?.toIntOrNull() }

    fun assertNoStallOver(since: String, budgetMs: Int) {
        val stalls = stallsSince(since)
        val worst = stalls.maxOrNull() ?: return
        if (worst > budgetMs) {
            throw AssertionError(
                "The main thread stall watchdog reported ${worst}ms, over the ${budgetMs}ms budget. " +
                    "All stalls seen during this interaction were $stalls"
            )
        }
    }

    fun assertNoMainThreadPdfiumWork(since: String) {
        val hits = CrashGate.unacceptedMainThreadPdfiumLines(AppLogcat.appLinesSince(since).asSequence())
        if (hits.isNotEmpty()) {
            throw AssertionError(
                "pdfium ran on the main thread during this test:\n${hits.joinToString("\n")}\n\n" +
                    CrashGate.ACCEPTED_MAIN_THREAD_PDFIUM_REASON
            )
        }
    }

    fun assertAbsent(since: String, marker: String, why: String) {
        val hits = AppLogcat.appLinesSince(since).filter { it.contains(marker) }
        if (hits.isNotEmpty()) {
            throw AssertionError("$why, but logcat carries:\n${hits.joinToString("\n")}")
        }
    }

    fun containsSince(since: String, marker: String): Boolean =
        AppLogcat.appLinesSince(since).any { it.contains(marker) }

    fun awaitLineContaining(
        since: String,
        marker: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): String = Await.untilNotNull("a logcat line containing \"$marker\" is written", timeoutMs) {
        AppLogcat.appLinesSince(since).firstOrNull { it.contains(marker) }
    }

    fun previewPagesInOrderSince(since: String): List<Int> =
        AppLogcat.appLinesSince(since)
            .filter { it.contains(PREVIEW_MARKER) }
            .mapNotNull { line -> previewPattern.find(line)?.groupValues?.get(1)?.toIntOrNull() }

    fun awaitPreviewFor(page: Int, since: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<Int> =
        Await.untilValue(
            condition = "a preview render line for page index $page is logged",
            timeoutMs = timeoutMs,
            read = { previewPagesInOrderSince(since) },
        ) { it.contains(page) }
}
