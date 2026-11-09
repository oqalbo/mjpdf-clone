// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.pdf.SearchResult
import com.gitlab.mudlej.MjPdfReader.core.debug.SearchTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchCoordinator
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import java.util.Collections

object SearchOracle {

    fun session(fileHash: String, query: String, ignoreAccents: Boolean = false): SearchSessionCache.Session? =
        SearchSessionCache.get(fileHash, query, ignoreAccents)

    fun hits(fileHash: String, query: String, ignoreAccents: Boolean = false): List<SearchSessionCache.Hit> =
        session(fileHash, query, ignoreAccents)?.hits?.sortedBy { it.resultIndex } ?: emptyList()

    fun awaitSession(
        fileHash: String,
        query: String,
        ignoreAccents: Boolean = false,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): SearchSessionCache.Session = Await.untilNotNull(
        "a finished search session is cached for \"$query\" with ignoreAccents $ignoreAccents",
        timeoutMs,
    ) { session(fileHash, query, ignoreAccents) }

    fun awaitHits(
        fileHash: String,
        query: String,
        ignoreAccents: Boolean = false,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): List<SearchSessionCache.Hit> =
        awaitSession(fileHash, query, ignoreAccents, timeoutMs).hits.sortedBy { it.resultIndex }

    fun pageNumbersOf(hits: List<SearchSessionCache.Hit>): List<Int> = hits.map { it.pageNumber }

    fun pageIndexesOf(hits: List<SearchSessionCache.Hit>): List<Int> = hits.map { it.pageNumber - 1 }

    fun blockAfterNextResultBatch() {
        SearchTestHooks.blockAfterNextResultBatch()
    }

    fun awaitResultBatchBlocked(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the running search parks after publishing a result batch", timeoutMs) {
            SearchTestHooks.isResultBatchBlocked()
        }
    }

    fun releaseResultBatch() {
        SearchTestHooks.releaseResultBatch()
    }

    fun handledNavigationActions(): Int = SearchTestHooks.navigationActionCount()

    fun awaitHandledNavigationActions(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the reader handles $expected search navigation actions",
            timeoutMs,
            read = { handledNavigationActions() },
        ) { it == expected }
    }

    fun subscribe(fileHash: String, query: String, ignoreAccents: Boolean = false): Recorder {
        val recorder = Recorder()
        val attached = SearchCoordinator.subscribeIfRunning(fileHash, query, ignoreAccents, recorder)
        recorder.attached = attached
        return recorder
    }

    fun awaitSubscribed(
        fileHash: String,
        query: String,
        ignoreAccents: Boolean = false,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): Recorder = Await.untilValue(
        "the test can attach a listener to the running search for \"$query\"",
        timeoutMs,
        read = { subscribe(fileHash, query, ignoreAccents) },
    ) { it.attached }

    class Recorder : SearchCoordinator.Listener {

        @Volatile
        var attached = false

        private val snapshots = Collections.synchronizedList(mutableListOf<List<SearchResult>>())

        @Volatile
        var pagesScanned = 0
            private set

        @Volatile
        var pageCount = 0
            private set

        @Volatile
        var finished = false
            private set

        override fun onProgress(pagesScanned: Int, pageCount: Int) {
            this.pagesScanned = pagesScanned
            this.pageCount = pageCount
        }

        override fun onResults(results: List<SearchResult>, finished: Boolean) {
            snapshots.add(results.toList())
            if (finished) this.finished = true
        }

        fun latest(): List<SearchResult> = synchronized(snapshots) { snapshots.lastOrNull().orEmpty() }

        fun snapshotOfSize(size: Int): List<SearchResult>? =
            synchronized(snapshots) { snapshots.lastOrNull { it.size == size } }

        fun awaitSnapshotOfSize(size: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<SearchResult> =
            Await.untilNotNull(
                "the running search published a snapshot holding exactly $size hits",
                timeoutMs,
            ) { snapshotOfSize(size) }

        fun awaitAtLeast(size: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<SearchResult> =
            Await.untilValue(
                "the running search published at least $size hits",
                timeoutMs,
                read = { latest() },
            ) { it.size >= size }

        fun release() {
            SearchCoordinator.unsubscribe(this)
        }
    }
}
