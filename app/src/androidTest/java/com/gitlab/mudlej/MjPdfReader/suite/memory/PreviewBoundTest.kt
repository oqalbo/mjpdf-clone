// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class PreviewBoundTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("MEM-14")
    fun thePreviewStoreRespectsItsMemoryBudgetAcrossALongSweep() {
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.THREE_THOUSAND_PAGES)
        ).use {
            val state = reader.awaitReady()
            assertEquals(
                "the fixture must be the three thousand page document, otherwise the sweep is too short " +
                    "to reach the budget",
                MemoryFixtures.THREE_THOUSAND_PAGES_COUNT as Int?,
                state.pageCount,
            )

            val budget = CacheStats.previewStats(MemoryReader.pdfView()).budgetBytes
            assertEquals(
                "the preview memory budget is maxMemory divided by eight",
                Heap.maxMemory() / PREVIEW_BUDGET_DIVISOR,
                budget,
            )

            val samples = mutableListOf<Long>()
            var stableReads = 0
            var lastCount = -1
            Await.until(
                "the preview sweep stops producing new entries",
                SWEEP_TIMEOUT_MS,
                pollMs = SAMPLE_INTERVAL_MS,
            ) {
                val stats = CacheStats.previewStats(MemoryReader.pdfView())
                samples += stats.byteTotal
                stableReads = if (stats.entryCount == lastCount) stableReads + 1 else 0
                lastCount = stats.entryCount
                stableReads >= STABLE_READS_REQUIRED
            }

            val overBudget = samples.filter { it > budget }
            assertEquals(
                "PreviewMemoryCache.trimToBudget must hold every sample at or below the budget of " +
                    "${budget / MEGABYTE} MB. These samples exceeded it: ${overBudget.map { it / MEGABYTE }}",
                emptyList<Long>(),
                overBudget,
            )

            val finalStats = CacheStats.previewStats(MemoryReader.pdfView())
            assertTrue(
                "the sweep must genuinely have produced previews, it reported ${finalStats.entryCount} entries",
                finalStats.entryCount > MINIMUM_ENTRIES,
            )
            val onDisk = MemoryReader.previewCacheDirectory()
                .walkTopDown()
                .count { it.isFile && !it.name.endsWith(TEMPORARY_SUFFIX) }
            assertTrue(
                "eviction must push previews to disk rather than discard them, so the on disk count " +
                    "($onDisk) must exceed the in memory count (${finalStats.entryCount})",
                onDisk > finalStats.entryCount,
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("MEM-15")
    @TestDuration(DurationCategory.SHORT)
    fun thePreviewBitmapPoolHoldsAtMostFourBitmapsPerBucket() {
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.LARGE)
        ).use {
            reader.awaitReady()

            val poolSamples = mutableListOf<Map<Int, Int>>()
            var page = 0
            while (page < PAGES_TO_SCROLL) {
                page += SAMPLE_EVERY_PAGES
                MemoryReader.jumpToPage(reader, minOf(page, MemoryFixtures.LARGE_PAGES - 1))
                reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
                poolSamples += CacheStats.previewStats(MemoryReader.pdfView()).poolSizes
            }

            val overCap = poolSamples.flatMap { sample ->
                sample.entries.filter { it.value > CacheStats.PREVIEW_POOL_PER_BUCKET_CAP }
                    .map { "bucket ${it.key} held ${it.value}" }
            }
            assertEquals(
                "PreviewBitmapPool.perBucketCap is ${CacheStats.PREVIEW_POOL_PER_BUCKET_CAP}, so the reuse " +
                    "pool must never exceed it. These samples did: $overCap",
                emptyList<String>(),
                overCap,
            )
            assertTrue(
                "the pool must hold something at least once, otherwise bitmaps are always being recycled " +
                    "rather than returned and this test proved nothing. The samples were $poolSamples",
                poolSamples.any { sample -> sample.values.any { it > 0 } },
            )

            val buckets = poolSamples.flatMap { it.keys }.toSet()
            assertTrue(
                "a document whose layout never changed must only ever pool one bucket width, it pooled " +
                    buckets,
                buckets.size <= 1,
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private companion object {
        const val PREVIEW_BUDGET_DIVISOR = 8L
        const val MEGABYTE = 1024L * 1024
        const val SWEEP_TIMEOUT_MS = 60_000L
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val STABLE_READS_REQUIRED = 3
        const val MINIMUM_ENTRIES = 10
        const val TEMPORARY_SUFFIX = ".tmp"
        const val PAGES_TO_SCROLL = 200
        const val SAMPLE_EVERY_PAGES = 20
    }
}
