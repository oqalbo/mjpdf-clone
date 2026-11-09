// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import org.junit.Assert.*
import org.junit.Test

class UserBookmarkToggleTest {
    @Test fun dualPageRowRemovesBothBookmarkedPagesAndLeavesOtherRowsAlone() {
        val toggle = userBookmarkToggle(4..5, setOf(1, 4, 5, 8), 5, { false }, { true })
        assertEquals(listOf(4, 5), toggle.removals)
        assertNull(toggle.addition)
        assertNull(toggle.blocked)
    }

    @Test fun rowWithNoBookmarksAddsOnlyTheCurrentPage() {
        val toggle = userBookmarkToggle(4..5, setOf(1, 8), 5, { false }, { true })
        assertEquals(5, toggle.addition)
        assertEquals(emptyList<Int>(), toggle.removals)
        assertNull(toggle.blocked)
    }

    @Test fun incognitoBlocksAddingBeforeCheckingHistory() {
        val toggle = userBookmarkToggle(4..5, emptySet(), 4, { true }, { error("History must not be checked") })
        assertEquals(BookmarkToggleBlock.INCOGNITO, toggle.blocked)
        assertNull(toggle.addition)
        assertEquals(emptyList<Int>(), toggle.removals)
    }

    @Test fun disabledHistoryBlocksAdding() {
        val toggle = userBookmarkToggle(4..5, emptySet(), 4, { false }, { false })
        assertEquals(BookmarkToggleBlock.HISTORY_DISABLED, toggle.blocked)
        assertNull(toggle.addition)
        assertEquals(emptyList<Int>(), toggle.removals)
    }

    @Test fun existingBookmarkCanBeRemovedWithoutConsultingRecordingPolicy() {
        val toggle = userBookmarkToggle(4..5, setOf(5), 4,
            { error("Incognito must not be checked") }, { error("History must not be checked") })
        assertEquals(listOf(5), toggle.removals)
        assertNull(toggle.addition)
        assertNull(toggle.blocked)
    }
}
