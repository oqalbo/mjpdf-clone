// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import org.junit.Assert.*
import org.junit.Test

class ReaderHistoryManagerTest {
    @Test fun goingBackToOldestEntryMovesIntermediatePagesOntoForwardStack() {
        val reader = HistoryTestReader()
        reader.jump(5)
        reader.jump(9)
        reader.manager.goBackTo(reader.manager.backEntries().first())
        assertEquals(0, reader.viewport.currentPage)
        assertEquals(emptyList<Int>(), reader.backPages())
        assertEquals(listOf(9, 5), reader.forwardPages())
        reader.manager.goForward()
        assertEquals(5, reader.viewport.currentPage)
    }

    @Test fun jumpingToCurrentPageLeavesBothStacksAndNotificationsUntouched() {
        val reader = HistoryTestReader()
        reader.jump(5)
        reader.jump(9)
        reader.manager.goBack()
        val back = reader.manager.backEntries()
        val forward = reader.manager.forwardEntries()
        val changes = reader.changes
        reader.manager.recordJump(ReaderHistoryManager.Origin.SEARCH, 5)
        assertEquals(back, reader.manager.backEntries())
        assertEquals(forward, reader.manager.forwardEntries())
        assertEquals(changes, reader.changes)
    }

    @Test fun jumpingToAnEarlierPageRemovesItBeforePushingTheCurrentPage() {
        val reader = HistoryTestReader()
        reader.jump(5)
        reader.jump(9)
        reader.jump(0)
        assertEquals(listOf(5, 9), reader.backPages())
        assertEquals(emptyList<Int>(), reader.forwardPages())
    }

    @Test fun fiftyFirstUniquePushEvictsOnlyTheOldestPage() {
        val reader = HistoryTestReader()
        (1..51).forEach(reader::jump)
        assertEquals((1..50).toList(), reader.backPages())
    }

    @Test fun mismatchedArraysLeaveExistingStacksUntouched() {
        val reader = HistoryTestReader()
        reader.jump(5)
        reader.jump(9)
        reader.manager.goBack()
        val back = reader.manager.backEntries()
        val forward = reader.manager.forwardEntries()
        val invalid = listOf(
            HistorySnapshot.fromArrays(intArrayOf(7), emptyArray(), longArrayOf(1)),
            HistorySnapshot.fromArrays(intArrayOf(7), arrayOf("LINK"), longArrayOf()),
            HistorySnapshot.fromArrays(null, arrayOf("LINK"), longArrayOf(1)),
        )
        invalid.forEach {
            assertNull(it)
            reader.manager.restoreSnapshots(it, it)
            assertEquals(back, reader.manager.backEntries())
            assertEquals(forward, reader.manager.forwardEntries())
        }
    }

    @Test fun restoringUnknownOriginsFallsBackToHistoryAndKeepsNewestDuplicate() {
        val reader = HistoryTestReader()
        val snapshot = HistorySnapshot.fromArrays(intArrayOf(2, 4, 2),
            arrayOf("LINK", "SEARCH", "futureOrigin"), longArrayOf(10, 20, 30))
        reader.manager.restoreSnapshots(snapshot, snapshot)
        assertEquals(listOf(4, 2), reader.backPages())
        assertEquals(listOf(4, 2), reader.forwardPages())
        val restored = reader.manager.backEntries().last()
        assertEquals(ReaderHistoryManager.Origin.HISTORY, restored.origin)
        assertEquals(30L, restored.timestamp)
        assertNull(restored.viewState)
    }

    @Test fun snapshotArraysRoundTripPageOriginAndTimestampInStackOrder() {
        val reader = HistoryTestReader()
        reader.wall = 1234L
        reader.jump(5)
        reader.wall = 5678L
        reader.jump(9)
        val arrays = HistorySnapshot(reader.manager.backEntries()).toArrays()
        assertArrayEquals(intArrayOf(0, 5), arrays.pages)
        assertArrayEquals(arrayOf("GO_TO", "GO_TO"), arrays.origins)
        assertArrayEquals(longArrayOf(1234, 5678), arrays.times)
        val restored = HistorySnapshot.fromArrays(arrays.pages, arrays.origins, arrays.times)
        reader.manager.clear()
        reader.manager.restoreSnapshots(restored, null)
        assertEquals(listOf(0, 5), reader.backPages())
        assertEquals(listOf(1234L, 5678L), reader.manager.backEntries().map { it.timestamp })
    }

    @Test fun stackIndicesSelectTheirOriginalEntriesAndRejectInvalidIndices() {
        val reader = HistoryTestReader()
        reader.jump(5)
        reader.jump(9)
        assertFalse(reader.manager.goBackToBackStackIndex(-1))
        assertFalse(reader.manager.goBackToBackStackIndex(2))
        assertTrue(reader.manager.goBackToBackStackIndex(0))
        assertEquals(0, reader.viewport.currentPage)
        assertEquals(listOf(9, 5), reader.forwardPages())
        assertFalse(reader.manager.goForwardToForwardStackIndex(-1))
        assertFalse(reader.manager.goForwardToForwardStackIndex(2))
        assertTrue(reader.manager.goForwardToForwardStackIndex(0))
        assertEquals(9, reader.viewport.currentPage)
        assertEquals(listOf(0, 5), reader.backPages())
        assertEquals(emptyList<Int>(), reader.forwardPages())
    }
}

internal class HistoryTestReader(lastPage: Int = 100) {
    var elapsed = 100L
    var wall = 1000L
    var changes = 0
    val viewport = FakeHistoryViewport(lastPage)
    private val preferences = InMemorySharedPreferences(mapOf(ReaderHistoryManager.debugDwellThresholdKey to 30_000L))
    val manager = ReaderHistoryManager(viewport, { changes++ }, { elapsed }, { wall },
        { preferences.getLong(ReaderHistoryManager.debugDwellThresholdKey, 30_000L) })

    init {
        viewport.onPageChanged = manager::onPageChanged
        manager.onPageChanged(0)
    }

    fun jump(page: Int) {
        manager.recordJump(ReaderHistoryManager.Origin.GO_TO, page)
        viewport.jumpTo(page)
    }

    fun backPages() = manager.backEntries().map { it.pageIndex }
    fun forwardPages() = manager.forwardEntries().map { it.pageIndex }
}

internal class FakeHistoryViewport(private val lastPage: Int) : HistoryViewport {
    override var currentPage = 0
        private set
    var onPageChanged: (Int) -> Unit = {}
    override fun captureViewState(): PDFView.ViewState? = null
    override fun applyViewState(state: PDFView.ViewState?) = false
    override fun jumpTo(pageIndex: Int) {
        currentPage = pageIndex.coerceIn(0, lastPage)
        onPageChanged(currentPage)
    }
}
