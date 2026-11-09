// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanWalkCoverageTest {
    @Test fun expansionRetainsOnlyTheOriginalUnfinishedRoots() {
        val retained = ScanWalk(listOf("/books/a")).within(listOf("/books/a", "/books/b"))

        assertEquals(listOf("/books/a"), retained.roots)
        assertTrue(retained.contains("/books/a/unindexed.pdf"))
        assertFalse(retained.contains("/books/b/unindexed.pdf"))
    }

    @Test fun narrowingIntersectsRequestedRootsWithoutEscalatingToTheirParent() {
        val retained = ScanWalk(listOf("/books/a", "/books/b"))
            .within(listOf("/books/a/selected", "/other"))

        assertEquals(listOf("/books/a/selected"), retained.roots)
        assertTrue(retained.contains("/books/a/selected/book.pdf"))
        assertFalse(retained.contains("/books/a/not-selected.pdf"))
        assertFalse(retained.contains("/books/b/book.pdf"))
        assertFalse(retained.contains("/other/book.pdf"))
    }

    @Test fun resumedAndExpandedSegmentsKeepTheirDifferentExclusions() {
        val retained = ScanWalk(listOf("/storage/books"), listOf("/storage/books/completed"))
            .within(listOf("/storage"))
        val added = ScanWalk(listOf("/storage"), listOf("/storage/books"))

        assertTrue(retained.contains("/storage/books/unfinished.pdf"))
        assertFalse(added.contains("/storage/books/unfinished.pdf"))
        assertTrue(added.contains("/storage/new.pdf"))
        assertFalse(retained.contains("/storage/new.pdf"))
        assertFalse(listOf(retained, added).any { it.contains("/storage/books/completed/later.pdf") })
    }

    @Test fun aScopeEntirelyInsideAnExclusionHasNoRemainingWalk() {
        val retained = ScanWalk(listOf("/books"), listOf("/books/completed"))
            .within(listOf("/books/completed/nested"))

        assertTrue(retained.roots.isEmpty())
    }

    @Test fun wholeDeviceScopeDoesNotTurnASelectedRequestIntoAWholeDeviceWalk() {
        val retained = ScanWalk(listOf("/books/a")).within(null)

        assertEquals(listOf("/books/a"), retained.roots)
        assertFalse(retained.contains("/elsewhere/book.pdf"))
    }

    @Test fun unconfiguredScopeDropsAllRequestedCoverage() {
        assertTrue(ScanWalk(listOf("/storage")).within(emptyList()).roots.isEmpty())
    }

    @Test fun clippingAndSourceCompletionDoNotMutateExistingCoverage() {
        val roots = mutableListOf("/books")
        val excluded = mutableListOf("/books/completed")
        val original = ScanWalk(roots, excluded)
        val clipped = original.within(listOf("/books/remaining"))
        roots.clear()
        excluded.clear()

        assertEquals(listOf("/books/remaining"), clipped.roots)
        assertEquals(listOf("/books/completed"), clipped.excludedRoots)
        val work = ScanWorkIndex()
        work.resetScope(null)
        assertFalse(work.filesystemComplete)
        work.sourceFinished(ScanEvent.DiscoverySourceFinished(0, DiscoverySource.MEDIA_STORE))
        assertFalse(work.filesystemComplete)
        work.sourceFinished(ScanEvent.DiscoverySourceFinished(0, DiscoverySource.FILESYSTEM))
        assertTrue(work.filesystemComplete)
        work.resetScope(null)
        assertFalse(work.filesystemComplete)
    }
}
