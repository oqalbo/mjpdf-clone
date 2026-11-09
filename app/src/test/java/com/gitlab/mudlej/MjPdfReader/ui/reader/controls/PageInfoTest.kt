// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import org.junit.Assert.*
import org.junit.Test
import com.gitlab.mudlej.MjPdfReader.R

class PageInfoTest {
    @Test fun everyFlagCombinationChoosesItsSinglePageVariantAndArguments() {
        assertNull(pageInfo(2, 2, 2, 10, PageInfoFlags(false, false)))
        assertEquals(PageInfo(R.string.fullscreen_page_number_info, listOf(3, 10)),
            pageInfo(2, 2, 2, 10, PageInfoFlags(true, false)))
        assertEquals(PageInfo(R.string.fullscreen_percentage_info, listOf(30)),
            pageInfo(2, 2, 2, 10, PageInfoFlags(false, true)))
        assertEquals(PageInfo(R.string.fullscreen_page_info, listOf(3, 10, 30)),
            pageInfo(2, 2, 2, 10, PageInfoFlags(true, true)))
    }

    @Test fun dualPagesUseTheRangeVariantAndTheRangeEndPercentage() {
        assertEquals(PageInfo(R.string.fullscreen_page_range_info, listOf(3, 4, 10, 40)),
            pageInfo(2, 3, 3, 10, PageInfoFlags(true, true)))
        assertEquals(PageInfo(R.string.fullscreen_page_range_number_info, listOf(3, 4, 10)),
            pageInfo(2, 3, 3, 10, PageInfoFlags(true, false)))
        assertEquals(PageInfo(R.string.fullscreen_percentage_info, listOf(40)),
            pageInfo(2, 3, 3, 10, PageInfoFlags(false, true)))
        assertNull(pageInfo(2, 3, 3, 10, PageInfoFlags(false, false)))
    }

    @Test fun firstPageOfALongBookShowsAtLeastOnePercent() {
        assertEquals(PageInfo(R.string.fullscreen_page_info, listOf(1, 1000, 1)),
            pageInfo(0, 0, 0, 1000, PageInfoFlags(true, true)))
    }

    @Test fun rangeStartEndAndLengthAreBoundedAgainstTheCurrentPage() {
        assertEquals(PageInfo(R.string.fullscreen_page_range_info, listOf(3, 4, 4, 100)),
            pageInfo(5, 2, 3, 1, PageInfoFlags(true, true)))
        assertEquals(PageInfo(R.string.fullscreen_page_info, listOf(3, 3, 100)),
            pageInfo(5, 2, 0, 0, PageInfoFlags(true, true)))
    }
}
