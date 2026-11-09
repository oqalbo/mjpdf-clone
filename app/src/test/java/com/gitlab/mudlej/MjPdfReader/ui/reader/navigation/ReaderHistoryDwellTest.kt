// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

class ReaderHistoryDwellTest {
    @Test fun leavingJustBeforeThresholdDoesNotRecordDwell() {
        val reader = HistoryTestReader()
        reader.elapsed += 29_999
        reader.viewport.jumpTo(1)
        assertEquals(emptyList<Int>(), reader.backPages())
        assertEquals(0, reader.changes)
    }

    @Test fun leavingAtThresholdRecordsThePageWithWallClockTimestamp() {
        val reader = HistoryTestReader()
        reader.elapsed += 30_000
        reader.wall = 4321L
        assertEquals(30_000L, reader.manager.elapsedDwellMs())
        reader.viewport.jumpTo(1)
        val entry = reader.manager.backEntries().single()
        assertEquals(0, entry.pageIndex)
        assertEquals(ReaderHistoryManager.Origin.DWELL, entry.origin)
        assertEquals(4321L, entry.timestamp)
        assertEquals(0L, reader.manager.elapsedDwellMs())
    }

    @Test fun pageChangeAfterRecordedJumpDoesNotAddDwell() {
        val reader = HistoryTestReader()
        reader.elapsed += 30_000
        reader.jump(5)
        assertEquals(listOf(0), reader.backPages())
        assertEquals(ReaderHistoryManager.Origin.GO_TO, reader.manager.backEntries().single().origin)
        assertEquals(1, reader.changes)
    }

    @Test fun firstPageChangeAfterClearDoesNotRecordDwell() {
        val reader = HistoryTestReader()
        reader.jump(5)
        reader.manager.clear()
        reader.elapsed += 60_000
        reader.viewport.jumpTo(6)
        assertEquals(emptyList<Int>(), reader.backPages())
        reader.elapsed += 30_000
        reader.viewport.jumpTo(7)
        assertEquals(listOf(6), reader.backPages())
    }

    @Test fun jumpingToCurrentPageDoesNotSuppressTheNextRealDwell() {
        val reader = HistoryTestReader()
        reader.elapsed += 30_000
        reader.manager.recordJump(ReaderHistoryManager.Origin.GO_TO, 0)
        reader.viewport.jumpTo(1)
        assertEquals(listOf(0), reader.backPages())
        assertEquals(ReaderHistoryManager.Origin.DWELL, reader.manager.backEntries().single().origin)
    }

    @Ignore("Pending ruling: a clamped jump retains a jump entry instead of the next dwell entry")
    @Test fun jumpClampedToCurrentPageDoesNotSuppressTheNextRealDwell() {
        val reader = HistoryTestReader(lastPage = 9)
        reader.viewport.jumpTo(9)
        reader.elapsed += 30_000
        reader.jump(9999)
        reader.elapsed += 30_000
        reader.viewport.jumpTo(8)
        val entry = reader.manager.backEntries().single()
        assertEquals(9, entry.pageIndex)
        assertEquals(ReaderHistoryManager.Origin.DWELL, entry.origin)
    }
}
