// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchBar
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchOracle
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchRobot
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchCoordinator
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object MemorySearch {

    const val SEARCH_TIMEOUT_MS = 180_000L
    const val POLL_MS = 250L
    const val IGNORE_ACCENTS = false

    fun isRunning(fileHash: String, query: String): Boolean {
        val recorder = SearchOracle.subscribe(fileHash, query, IGNORE_ACCENTS)
        if (recorder.attached) recorder.release()
        return recorder.attached
    }

    fun awaitNotRunning(fileHash: String, query: String, timeoutMs: Long = SEARCH_TIMEOUT_MS) {
        Await.until(
            "SearchCoordinator.subscribeIfRunning returns false for \"$query\"",
            timeoutMs,
            pollMs = POLL_MS,
        ) { !isRunning(fileHash, query) }
    }

    fun runToCompletion(
        search: SearchRobot,
        fileHash: String,
        query: String,
        timeoutMs: Long = SEARCH_TIMEOUT_MS,
    ): SearchCoordinator.DebugProgress {
        SearchIdle.clear()
        search.search(query)
        SearchBar.awaitShown(Await.LONG_TIMEOUT_MS)
        SearchIdle.awaitFinished(timeoutMs)
        awaitNotRunning(fileHash, query, timeoutMs)
        return requireFinished(query)
    }

    fun requireFinished(query: String): SearchCoordinator.DebugProgress {
        val progress = SearchIdle.progress()
            ?: throw AssertionError(
                "SearchCoordinator.debugProgress returned null after searching for \"$query\", which means " +
                    "the search was cancelled or never started. A release assertion taken against a search " +
                    "that never ran proves nothing about the release."
            )
        assertEquals(
            "the finished search must be the one this test ran",
            query,
            progress.query,
        )
        assertTrue(
            "the search for \"$query\" must report finished, it reported $progress",
            progress.finished,
        )
        assertTrue(
            "the search for \"$query\" must have scanned at least one page, it reported " +
                "${progress.pagesScanned} of ${progress.pageCount}",
            progress.pagesScanned > 0,
        )
        return progress
    }

    fun dismissSearchBar() {
        if (!SearchBar.isShown()) return
        SearchBar.tapDone()
        SearchBar.awaitGone(Await.LONG_TIMEOUT_MS)
    }

    fun session(fileHash: String, query: String): SearchSessionCache.Session? =
        SearchSessionCache.get(fileHash, query, IGNORE_ACCENTS)

    fun cachedQueries(fileHash: String, queries: List<String>): List<String> =
        queries.filter { session(fileHash, it) != null }
}
