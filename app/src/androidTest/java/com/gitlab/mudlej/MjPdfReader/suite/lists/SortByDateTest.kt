// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SortByDateTest : ListsTestCase() {

    @Test
    @TestId("LIST-26")
    fun sortByDatePutsNewestFirstAndUndatedEntriesLast() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()
            val pageOrder = list.allPagePills()

            list.sortByDate()
            list.awaitItemCount(ListsFixtures.richGroups.size)

            val newestFirst = ListsFixtures.richGroups
                .sortedWith(compareBy<RichGroup> { it.createdAt == null }.thenByDescending { it.createdAt ?: "" })
                .map { it.key }
            assertEquals("the order after sorting by date", newestFirst, list.groupKeys())
            assertEquals(
                "the first row is the newest highlight, which sits on page 10",
                ListsFixtures.newestGroup.pagePill,
                list.allPagePills().first(),
            )
            assertEquals("the item count is unchanged", ListsFixtures.richGroups.size, list.itemCount())
            assertTrue("the sort item is checked", list.isOverflowItemChecked(R.string.sort_by_creation_date))

            list.sortByDate()
            list.awaitItemCount(ListsFixtures.richGroups.size)
            assertEquals("the order returns to page ascending", pageOrder, list.allPagePills())
            assertFalse("the sort item is still checked", list.isOverflowItemChecked(R.string.sort_by_creation_date))
        }

        openReader(ListsFixtures.RICH_MIXED_DATES) { _, _ ->
            val list = openHighlightsAndScan()

            list.sortByDate()
            list.awaitItemCount(ListsFixtures.richGroups.size)

            assertEquals(
                "the two undated entries sit last, after every dated one",
                UNDATED_GROUPS,
                list.groupKeys().takeLast(UNDATED_GROUPS.size),
            )
            assertEquals(
                "the first row is still the newest dated highlight",
                ListsFixtures.newestGroup.key,
                list.groupKeys().first(),
            )
        }
    }

    private companion object {
        val UNDATED_GROUPS = listOf("mj-group-01", "mj-group-02")
    }
}
