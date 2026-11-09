// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

class CoverCacheKeysTest {
    private val mebibyte = 1024L * 1024

    @Test fun widthBucketsRoundUpAndClampAtBothEnds() {
        for ((width, expected) in listOf(-1 to 160, 0 to 160, 160 to 160, 161 to 320, 320 to 320, 321 to 640, 640 to 640, 641 to 640)) {
            assertEquals(expected, CoverCache.bucketFor(width))
        }
    }

    @Test fun keysSeparateHashesAndWidthBuckets() {
        assertEquals("ab-w160", CoverCache.key("ab", CoverCache.bucketFor(100)))
        assertEquals("ab-w320", CoverCache.key("ab", CoverCache.bucketFor(200)))
        assertEquals("abc-w160", CoverCache.key("abc", CoverCache.bucketFor(100)))
    }

    @Test fun coverHeightPreservesAspectRatioTruncatesAndKeepsAtLeastOneRow() {
        assertEquals(480, CoverCache.coverHeight(320, 200, 300))
        assertEquals(213, CoverCache.coverHeight(320, 300, 200))
        assertEquals(1, CoverCache.coverHeight(160, 1000, 1))
    }

    @Ignore("Pending ruling: a 1:100 page requests 64000 rows instead of capping the cover height at 640")
    @Test fun extremelyTallPagesCapTheRenderedHeight() {
        assertEquals(640, CoverCache.coverHeight(640, 1, 100))
    }

    @Test fun trimDeletesOldestFirstAndStopsExactlyAtTheBudget() {
        val files = listOf(32 * mebibyte to 30L, 16 * mebibyte to 10L, 32 * mebibyte to 20L, 16 * mebibyte to 40L)
        assertEquals(listOf(1, 2), CoverCache.trimPlan(files))
        assertEquals(emptyList<Int>(), CoverCache.trimPlan(listOf(64 * mebibyte to 1L)))
        assertEquals(emptyList<Int>(), CoverCache.trimPlan(emptyList()))
    }

    @Test fun trimIncludesTemporaryFilesAndKeepsInputOrderForEqualAges() {
        val files = listOf(
            "new.jpg" to (32 * mebibyte to 30L),
            "old.jpg.tmp" to (16 * mebibyte to 10L),
            "old.jpg" to (16 * mebibyte to 10L),
            "newer.jpg.tmp" to (32 * mebibyte to 40L),
        )
        val deleted = CoverCache.trimPlan(files.map { it.second }).map { files[it].first }
        assertEquals(listOf("old.jpg.tmp", "old.jpg"), deleted)
    }

    @Test fun trimDeletesAnOversizedSingleFile() {
        assertEquals(listOf(0), CoverCache.trimPlan(listOf((64 * mebibyte + 1) to 1L)))
    }
}
