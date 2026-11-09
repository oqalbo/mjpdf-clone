// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryViewportAnchorTest {
    private val anchor = item("anchor", "shared")
    private val before = item("before", "before")
    private val after = item("after", "after")
    private val presented = listOf(before, anchor, after)

    @Test fun exactKeyWinsEvenAfterContentHashChanges() {
        assertEquals(2, LibraryViewport.anchorIndex(
            presented, 1, listOf(item("duplicate", "shared"), after, anchor.copy(hash = "fresh")),
        ))
    }

    @Test fun uniqueHashCanFollowAnIdentityChange() {
        assertEquals(1, LibraryViewport.anchorIndex(
            presented, 1, listOf(after, item("moved", "shared"), before),
        ))
    }

    @Test fun duplicateDestinationHashesUseTheNearestSurvivingNeighbor() {
        assertEquals(3, LibraryViewport.anchorIndex(
            presented, 1, listOf(item("copy1", "shared"), before, item("copy2", "shared"), after),
        ))
    }

    @Test fun duplicateSourceHashesCannotClaimTheOnlyDestination() {
        assertEquals(1, LibraryViewport.anchorIndex(
            listOf(item("copy", "shared"), anchor, after), 1,
            listOf(item("moved", "shared"), after),
        ))
    }

    @Test fun fallbackSearchesPastRemovedNeighborsInBothDirections() {
        assertEquals(0, LibraryViewport.anchorIndex(presented, 1, listOf(before)))
        assertEquals(0, LibraryViewport.anchorIndex(presented, 0, listOf(after)))
    }

    @Test fun ambiguousHashWithoutSurvivingNeighborsDoesNotPickAnArbitraryDuplicate() {
        assertNull(LibraryViewport.anchorIndex(
            presented, 1, listOf(item("copy1", "shared"), item("copy2", "shared")),
        ))
        assertNull(LibraryViewport.anchorIndex(presented, 1, emptyList()))
    }

    private fun item(name: String, hash: String) = HomeItem.fromScan(
        ScannedPdfEntry("/books/$name.pdf", 100, 1_000, hash),
    )
}
