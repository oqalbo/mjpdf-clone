// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import org.junit.Assert.*
import org.junit.Test

class PersistedGrantReleaseTest {
    private fun grants(count: Int) = (0 until count).map { GrantInfo("content://grant/$it", it.toLong(), false) }

    @Test fun belowTheHeadroomThresholdReleasesNothing() {
        assertEquals(emptyList<GrantInfo>(), grantsToRelease(grants(119), 128))
        assertEquals(emptyList<GrantInfo>(), grantsToRelease(grants(503), 512))
    }

    @Test fun reachingTheHeadroomThresholdReleasesTheOldestReadOnlyGrant() {
        val values = grants(120).reversed()
        assertEquals(listOf(values.last()), grantsToRelease(values, 128))
        val larger = grants(504)
        assertEquals(listOf(larger.first()), grantsToRelease(larger, 512))
    }

    @Test fun excessPlusOneReadOnlyGrantsAreReleasedInTimeOrder() {
        val values = grants(122).map { if (it.time == 0L) it.copy(write = true) else it }.reversed()
        assertEquals(listOf("content://grant/1", "content://grant/2", "content://grant/3"),
            grantsToRelease(values, 128).map { it.uri })
    }

    @Test fun writeGrantsAreRetainedEvenWhenNoReadOnlyGrantsRemain() {
        assertEquals(emptyList<GrantInfo>(), grantsToRelease(grants(128).map { it.copy(write = true) }, 128))
        val values = grants(128).map { it.copy(write = it.time != 127L) }
        assertEquals(listOf(values.last()), grantsToRelease(values, 128))
    }
}
