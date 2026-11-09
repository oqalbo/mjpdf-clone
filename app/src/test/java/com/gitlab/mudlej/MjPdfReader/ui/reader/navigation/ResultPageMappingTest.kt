// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import org.junit.Assert.*
import org.junit.Test

class ResultPageMappingTest {
    @Test fun textModeResultsAreClampedToBothPageBounds() {
        assertEquals(0, textModeResultPage(-1, 10))
        assertEquals(0, textModeResultPage(0, 10))
        assertEquals(4, textModeResultPage(4, 10))
        assertEquals(9, textModeResultPage(10, 10))
        assertEquals(0, textModeResultPage(10, 1))
    }

    @Test fun unknownPageCountsClampOnlyTheLowerBound() {
        assertEquals(0, textModeResultPage(-1, 0))
        assertEquals(42, textModeResultPage(42, 0))
        assertEquals(42, textModeResultPage(42, -1))
    }

    @Test fun linkPageNumbersAreConvertedWithoutClamping() {
        assertEquals(0, linksResultPage(1))
        assertEquals(20, linksResultPage(21))
        assertEquals(-1, linksResultPage(0))
    }
}
