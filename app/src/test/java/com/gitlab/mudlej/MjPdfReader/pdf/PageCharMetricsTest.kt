// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageCharMetricsTest {

    @Test
    fun emptyMetricsHaveNoCharacters() {
        assertNull(PageCharMetrics.fromRaw(doubleArrayOf()))
    }

    @Test
    fun incompleteCharacterRecordsAreRejected() {
        for (size in listOf(1, 8, 10, 17)) {
            assertNull("size=$size", PageCharMetrics.fromRaw(DoubleArray(size)))
        }
    }

    @Test
    fun twoCharactersUnpackEveryFieldInOrder() {
        val metrics = requireNotNull(PageCharMetrics.fromRaw(doubleArrayOf(
            1.25, 2.5, 3.75, 4.0, 12.5, 400.0, 65.0, 0.5, 3.0,
            11.25, 12.5, 13.75, 14.0, 24.5, 700.0, 66.0, 1.5, 8.0,
        )))

        assertEquals(2, metrics.count)
        assertArrayEquals(floatArrayOf(1.25f, 11.25f), metrics.left, 0f)
        assertArrayEquals(floatArrayOf(2.5f, 12.5f), metrics.bottom, 0f)
        assertArrayEquals(floatArrayOf(3.75f, 13.75f), metrics.right, 0f)
        assertArrayEquals(floatArrayOf(4f, 14f), metrics.top, 0f)
        assertArrayEquals(floatArrayOf(12.5f, 24.5f), metrics.fontSize, 0f)
        assertArrayEquals(intArrayOf(400, 700), metrics.fontWeight)
        assertArrayEquals(intArrayOf(65, 66), metrics.codePoint)
        assertArrayEquals(floatArrayOf(0.5f, 1.5f), metrics.angleRadians, 0f)
        assertArrayEquals(intArrayOf(3, 8), metrics.fontFlags)
    }

    @Test
    fun fractionalCodePointsAreTruncated() {
        val metrics = requireNotNull(PageCharMetrics.fromRaw(doubleArrayOf(
            1.0, 2.0, 3.0, 4.0, 12.0, 400.0, 65.9, 0.0, 3.0,
        )))
        assertArrayEquals(intArrayOf(65), metrics.codePoint)
    }

    @Test
    fun nanIntegerFieldsBecomeZero() {
        val metrics = requireNotNull(PageCharMetrics.fromRaw(doubleArrayOf(
            1.0, 2.0, 3.0, 4.0, 12.0, Double.NaN, Double.NaN, 0.0, Double.NaN,
        )))
        assertArrayEquals(intArrayOf(0), metrics.fontWeight)
        assertArrayEquals(intArrayOf(0), metrics.codePoint)
        assertArrayEquals(intArrayOf(0), metrics.fontFlags)
    }
}
