// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiUtilPercentTest {

    @Test
    fun zeroDivisorGivesZeroPercent() {
        assertEquals(0, 2.divideToPercent(0))
    }

    @Test
    fun fractionalPercentIsTruncated() {
        assertEquals(66, 2.divideToPercent(3))
    }

    @Test
    fun allPagesReadGivesOneHundredPercent() {
        assertEquals(100, 3.divideToPercent(3))
    }
}
