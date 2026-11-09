// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R

class ReaderHistoryManager internal constructor(
    private val viewport: HistoryViewport,
    private val onChanged: () -> Unit,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val dwellThresholdMillis: () -> Long = { DWELL_THRESHOLD_MS },
) {
    constructor(
        pdfView: () -> PDFView,
        onChanged: () -> Unit,
        elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
        wallClock: () -> Long = System::currentTimeMillis,
        dwellThresholdMillis: () -> Long = defaultDwellThreshold(pdfView),
    ) : this(PdfHistoryViewport(pdfView), onChanged, elapsedRealtime, wallClock, dwellThresholdMillis)

    enum class Origin(@StringRes val labelRes: Int) {
        LINK(R.string.history_origin_link),
        TOC(R.string.table_of_contents),
        GO_TO(R.string.go_to_page),
        SEARCH(R.string.search),
        BOOKMARK(R.string.history_origin_bookmark),
        TEXT_MODE(R.string.text_mode),
        DWELL(R.string.history_origin_dwell),
        HISTORY(R.string.history_origin_visited),
    }

    data class Entry(
        val pageIndex: Int,
        val viewState: PDFView.ViewState?,
        val timestamp: Long,
        val origin: Origin,
    )

    private val backStack = ArrayDeque<Entry>()
    private val forwardStack = ArrayDeque<Entry>()
    private var navigatingInternally = false
    private var suppressDwellOnce = false
    private var currentPageIndex = -1
    private var arrivedAtElapsed = 0L

    fun elapsedDwellMs(): Long {
        if (currentPageIndex < 0) {
            return 0L
        }
        return elapsedRealtime() - arrivedAtElapsed
    }

    fun canGoBack() = backStack.isNotEmpty()

    fun canGoForward() = forwardStack.isNotEmpty()

    fun hasTrail() = canGoBack() || canGoForward()

    fun backEntries(): List<Entry> = backStack.toList()

    fun forwardEntries(): List<Entry> = forwardStack.toList()

    fun recordJump(origin: Origin, targetPageIndex: Int? = null) {
        val view = viewport
        if (targetPageIndex != null && targetPageIndex == view.currentPage) {
            return
        }
        targetPageIndex?.let { removePage(backStack, it) }
        pushBack(currentEntry(origin))
        forwardStack.clear()
        suppressDwellOnce = true
        onChanged()
    }

    fun goBack() {
        val entry = backStack.removeLastOrNull() ?: return
        pushForward(currentEntry(Origin.HISTORY))
        navigate(entry)
    }

    fun goForward() {
        val entry = forwardStack.removeLastOrNull() ?: return
        pushBack(currentEntry(Origin.HISTORY))
        navigate(entry)
    }

    fun goBackTo(entry: Entry) {
        if (backStack.none { it === entry }) {
            return
        }
        pushForward(currentEntry(Origin.HISTORY))
        while (backStack.isNotEmpty()) {
            val top = backStack.removeLast()
            if (top === entry) {
                navigate(top)
                return
            }
            pushForward(top)
        }
    }

    fun goBackToBackStackIndex(index: Int): Boolean {
        val entry = backStack.toList().getOrNull(index) ?: return false
        goBackTo(entry)
        return true
    }

    fun goForwardTo(entry: Entry) {
        if (forwardStack.none { it === entry }) {
            return
        }
        pushBack(currentEntry(Origin.HISTORY))
        while (forwardStack.isNotEmpty()) {
            val top = forwardStack.removeLast()
            if (top === entry) {
                navigate(top)
                return
            }
            pushBack(top)
        }
    }

    fun goForwardToForwardStackIndex(index: Int): Boolean {
        val entry = forwardStack.toList().getOrNull(index) ?: return false
        goForwardTo(entry)
        return true
    }

    fun onPageChanged(pageIndex: Int) {
        val now = elapsedRealtime()
        val leftPage = currentPageIndex
        val dwellMs = now - arrivedAtElapsed
        val skipDwell = navigatingInternally || suppressDwellOnce
        navigatingInternally = false
        suppressDwellOnce = false
        currentPageIndex = pageIndex
        arrivedAtElapsed = now
        if (leftPage < 0 || leftPage == pageIndex || skipDwell) {
            return
        }
        if (dwellMs < dwellThresholdMillis()) {
            return
        }
        if (backStack.lastOrNull()?.pageIndex == leftPage) {
            return
        }
        pushBack(Entry(leftPage, null, wallClock(), Origin.DWELL))
        forwardStack.clear()
        onChanged()
    }

    fun clear() {
        backStack.clear()
        forwardStack.clear()
        navigatingInternally = false
        suppressDwellOnce = false
        currentPageIndex = -1
        arrivedAtElapsed = 0L
        onChanged()
    }

    fun saveState(outState: Bundle) {
        saveStack(outState, backStack, backPagesKey, backOriginsKey, backTimesKey)
        saveStack(outState, forwardStack, forwardPagesKey, forwardOriginsKey, forwardTimesKey)
    }

    fun restoreState(savedState: Bundle) {
        restoreSnapshots(
            HistorySnapshot.fromArrays(savedState.getIntArray(backPagesKey),
                savedState.getStringArray(backOriginsKey), savedState.getLongArray(backTimesKey)),
            HistorySnapshot.fromArrays(savedState.getIntArray(forwardPagesKey),
                savedState.getStringArray(forwardOriginsKey), savedState.getLongArray(forwardTimesKey)),
        )
    }

    internal fun restoreSnapshots(back: HistorySnapshot?, forward: HistorySnapshot?) {
        restoreStack(back, backStack)
        restoreStack(forward, forwardStack)
        onChanged()
    }

    private fun currentEntry(origin: Origin): Entry {
        val view = viewport
        return Entry(view.currentPage, view.captureViewState(), wallClock(), origin)
    }

    private fun navigate(entry: Entry) {
        removePage(backStack, entry.pageIndex)
        removePage(forwardStack, entry.pageIndex)
        val view = viewport
        navigatingInternally = entry.pageIndex != view.currentPage
        if (!view.applyViewState(entry.viewState)) {
            view.jumpTo(entry.pageIndex)
        }
        onChanged()
    }

    private fun pushBack(entry: Entry) {
        pushUnique(backStack, entry)
    }

    private fun pushForward(entry: Entry) {
        pushUnique(forwardStack, entry)
    }

    private fun pushUnique(stack: ArrayDeque<Entry>, entry: Entry) {
        removePage(stack, entry.pageIndex)
        stack.addLast(entry)
        while (stack.size > MAX_ENTRIES) {
            stack.removeFirst()
        }
    }

    private fun removePage(stack: ArrayDeque<Entry>, pageIndex: Int) {
        val retained = stack.filterNot { it.pageIndex == pageIndex }
        if (retained.size == stack.size) {
            return
        }
        stack.clear()
        stack.addAll(retained)
    }

    private fun saveStack(
        outState: Bundle,
        stack: ArrayDeque<Entry>,
        pagesKey: String,
        originsKey: String,
        timesKey: String,
    ) {
        val arrays = HistorySnapshot(stack.toList()).toArrays()
        outState.putIntArray(pagesKey, arrays.pages)
        outState.putStringArray(originsKey, arrays.origins)
        outState.putLongArray(timesKey, arrays.times)
    }

    private fun restoreStack(snapshot: HistorySnapshot?, stack: ArrayDeque<Entry>) {
        if (snapshot == null) return
        stack.clear()
        snapshot.entries.forEach { pushUnique(stack, it) }
    }

    companion object {
        private fun defaultDwellThreshold(pdfView: () -> PDFView): () -> Long {
            val debugThreshold by lazy {
                val preferences = PreferenceManager.getDefaultSharedPreferences(pdfView().context)
                runCatching { preferences.getLong(debugDwellThresholdKey, DWELL_THRESHOLD_MS) }
                    .getOrDefault(DWELL_THRESHOLD_MS)
            }
            return { if (BuildConfig.DEBUG) debugThreshold else DWELL_THRESHOLD_MS }
        }

        private const val DWELL_THRESHOLD_MS = 30_000L
        private const val MAX_ENTRIES = 50
        const val debugDwellThresholdKey = "debugDwellThresholdMs"
        private const val backPagesKey = "readerHistoryBackPages"
        private const val backOriginsKey = "readerHistoryBackOrigins"
        private const val backTimesKey = "readerHistoryBackTimes"
        private const val forwardPagesKey = "readerHistoryForwardPages"
        private const val forwardOriginsKey = "readerHistoryForwardOrigins"
        private const val forwardTimesKey = "readerHistoryForwardTimes"
    }
}

internal interface HistoryViewport {
    val currentPage: Int
    fun captureViewState(): PDFView.ViewState?
    fun applyViewState(state: PDFView.ViewState?): Boolean
    fun jumpTo(pageIndex: Int)
}

private class PdfHistoryViewport(private val pdfView: () -> PDFView) : HistoryViewport {
    override val currentPage: Int get() = pdfView().currentPage
    override fun captureViewState(): PDFView.ViewState? = pdfView().captureViewState()
    override fun applyViewState(state: PDFView.ViewState?): Boolean = pdfView().applyViewState(state)
    override fun jumpTo(pageIndex: Int) = pdfView().jumpTo(pageIndex)
}

internal data class HistorySnapshot(val entries: List<ReaderHistoryManager.Entry>) {
    data class Arrays(val pages: IntArray, val origins: Array<String>, val times: LongArray)

    fun toArrays() = Arrays(
        entries.map { it.pageIndex }.toIntArray(),
        entries.map { it.origin.name }.toTypedArray(),
        entries.map { it.timestamp }.toLongArray(),
    )

    companion object {
        fun fromArrays(pages: IntArray?, origins: Array<String>?, times: LongArray?): HistorySnapshot? {
            if (pages == null || origins == null || times == null
                || pages.size != origins.size || pages.size != times.size) return null
            return HistorySnapshot(pages.indices.map { index ->
                val origin = ReaderHistoryManager.Origin.entries.firstOrNull { it.name == origins[index] }
                    ?: ReaderHistoryManager.Origin.HISTORY
                ReaderHistoryManager.Entry(pages[index], null, times[index], origin)
            })
        }
    }
}
