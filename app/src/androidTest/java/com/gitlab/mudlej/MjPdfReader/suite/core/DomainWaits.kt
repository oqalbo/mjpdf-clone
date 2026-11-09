// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.LibraryIndex
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanPhase
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchCoordinator

object ScanLifecycle {
    fun snapshot(): LibraryIndex = ScanDebugSignals.snapshot().index

    fun generation(): Long = snapshot().generation

    fun awaitDiscoveryFinishedAfter(
        afterGeneration: Long,
        timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS,
    ): LibraryIndex = Await.untilValue(
        condition = "library discovery finishes after generation $afterGeneration",
        timeoutMs = timeoutMs,
        read = { readFreshIndexOrFail(afterGeneration) },
        predicate = { index ->
            index.loaded &&
                index.generation > afterGeneration &&
                (
                    index.phase == ScanPhase.READING_DETAILS ||
                        index.phase == ScanPhase.IDLE
                )
        },
    )

    fun awaitCompletedAfter(
        afterGeneration: Long,
        timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS,
    ): LibraryIndex = Await.untilValue(
        condition = "library backend completes after generation $afterGeneration",
        timeoutMs = timeoutMs,
        read = { readFreshIndexOrFail(afterGeneration) },
        predicate = { index ->
            index.loaded &&
                index.generation > afterGeneration &&
                index.phase == ScanPhase.IDLE &&
                index.sourceFailures.isEmpty()
        },
    )

    private fun readFreshIndexOrFail(afterGeneration: Long): LibraryIndex {
        val index = snapshot()
        if (
            index.generation > afterGeneration &&
                (index.phase == ScanPhase.PAUSED || index.phase == ScanPhase.INCOMPLETE)
        ) {
            throw ConditionTimeoutException(
                "library scan entered terminal failure after generation $afterGeneration: $index",
            )
        }
        return index
    }
}

object SearchIdle {

    fun progress(): SearchCoordinator.DebugProgress? = SearchCoordinator.debugProgress()

    fun isRunning(): Boolean = progress()?.finished == false

    fun awaitFinished(timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS): SearchCoordinator.DebugProgress =
        Await.untilValue(
            condition = "the active search reports finished",
            timeoutMs = timeoutMs,
            read = { progress() },
            predicate = { it != null && it.finished },
        )!!

    fun awaitPagesScanned(pages: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): SearchCoordinator.DebugProgress =
        Await.untilValue(
            condition = "the running search has scanned at least $pages pages",
            timeoutMs = timeoutMs,
            read = { progress() },
            predicate = { it != null && it.pagesScanned >= pages },
        )!!

    fun awaitRunning(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): SearchCoordinator.DebugProgress =
        Await.untilValue(
            condition = "a search is running",
            timeoutMs = timeoutMs,
            read = { progress() },
            predicate = { it != null && !it.finished },
        )!!

    fun clear() {
        SearchCoordinator.clearDebugProgress()
    }
}
