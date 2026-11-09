// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TileCacheBoundTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("MEM-17")
    @TestDuration(DurationCategory.SHORT)
    fun thePagePartCacheIsBoundedAtTheDerivedTileCacheSize() {
        val samplesByCase = LinkedHashMap<String, List<PagePartStats>>()

        QUALITY_CASES.forEach { case ->
            SettingsSeeder.seed(Preferences.highQualityKey, case.highQuality)

            ActivityScenario.launch<MainActivity>(
                MemoryReader.fixtureViewIntent(MemoryFixtures.LARGE)
            ).use {
                reader.awaitReady()
                val bound = CacheStats.currentCacheSize(MemoryReader.pdfView())
                assertEquals(
                    "the default tile size of ${Preferences.partSizeDefault.toInt()} derives a page part " +
                        "bound of ${CacheStats.DEFAULT_CACHE_SIZE}, not the viewer library's declared default " +
                        "of 120, and case ${case.name} must see the same bound",
                    CacheStats.DEFAULT_CACHE_SIZE,
                    bound,
                )

                val samples = mutableListOf<PagePartStats>()
                MemoryReader.jumpToPage(reader, SAMPLE_EVERY_PAGES)
                reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
                var page = SAMPLE_EVERY_PAGES
                while (page < PAGES_TO_SCROLL) {
                    page += SAMPLE_EVERY_PAGES
                    MemoryReader.jumpToPage(reader, page)
                    reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
                    samples += CacheStats.pagePartStats(MemoryReader.pdfView())
                }
                samplesByCase[case.name] = samples

                val overBound = samples.filter { it.total > bound }
                assertEquals(
                    "CacheManager must hold the combined active and passive page part count at or below the bound " +
                        "of $bound at every sample in case ${case.name}. These did not: $overBound",
                    emptyList<PagePartStats>(),
                    overBound,
                )
                val empty = samples.filter { it.total == 0 }
                assertEquals(
                    "every sample in case ${case.name} is taken after a page rendered, so none may report an " +
                        "empty cache. These did: $empty",
                    emptyList<PagePartStats>(),
                    empty,
                )
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        val counts = samplesByCase.mapValues { entry -> entry.value.map { it.total } }
        assertEquals(
            "Best Quality Rendering changes the bytes per page part from RGB_565 to ARGB_8888 and leaves " +
                "the tile count alone, so both cases must produce the same part counts at the same pages. " +
                "They produced $counts",
            counts[QUALITY_CASES.first().name],
            counts[QUALITY_CASES.last().name],
        )
    }

    @Test
    @TestId("MEM-19")
    fun theTileSizeSettingChangesTheBoundAndTheNewBoundIsEnforced() {
        val heapByPartSize = LinkedHashMap<Int, Long>()

        PART_SIZES.forEach { partSize ->
            SettingsSeeder.seed(Preferences.partSizeKey, partSize.toFloat())

            ActivityScenario.launch<MainActivity>(
                MemoryReader.fixtureViewIntent(MemoryFixtures.LARGE)
            ).use {
                reader.awaitReady()

                val expected = CacheStats.expectedCacheSizeFor(partSize)
                val reported = CacheStats.currentCacheSize(MemoryReader.pdfView())
                assertEquals(
                    "a tile size of $partSize derives a bound of $expected from the formula in " +
                        "DocumentLoader.applyTileRenderingPreferences, coerced into 24 to 480. Note that the " +
                        "settings slider stops at ${Preferences.maxPartSize.toInt()}, so a tile size above " +
                        "that is reachable only by seeding, which is what this case does",
                    expected,
                    reported,
                )
                assertEquals(
                    "the applied tile size must be the seeded one",
                    partSize.toFloat(),
                    CacheStats.currentPartSize(MemoryReader.pdfView()),
                    0f,
                )

                val samples = mutableListOf<PagePartStats>()
                var page = 0
                while (page < MEM19_PAGES) {
                    page += SAMPLE_EVERY_PAGES
                    MemoryReader.jumpToPage(reader, page)
                    reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
                    samples += CacheStats.pagePartStats(MemoryReader.pdfView())
                }

                val overBound = samples.filter { it.total > reported }
                assertEquals(
                    "the page part count must stay at or below the derived bound of $reported at tile size " +
                        "$partSize. These samples did not: $overBound",
                    emptyList<PagePartStats>(),
                    overBound,
                )
                val empty = samples.filter { it.total == 0 }
                assertEquals(
                    "a cache that rendered nothing satisfies any upper bound, so no sample at tile size " +
                        "$partSize may report an empty cache. These did: $empty",
                    emptyList<PagePartStats>(),
                    empty,
                )

                heapByPartSize[partSize] = Heap.settledJavaHeap()
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        val smallest = heapByPartSize[PART_SIZES.first()]
        val largest = heapByPartSize[PART_SIZES.last()]
        Log.i(
            LOG_TAG,
            "MEM-19 directional check, no band asserted: the heap at tile size ${PART_SIZES.first()} read " +
                "${Heap.megabytes(smallest ?: 0)} MB and at tile size ${PART_SIZES.last()} read " +
                "${Heap.megabytes(largest ?: 0)} MB",
        )
    }

    private data class QualityCase(val name: String, val highQuality: Boolean)

    private companion object {
        const val LOG_TAG = "MjPdfMemory"
        const val PAGES_TO_SCROLL = 60
        const val MEM19_PAGES = 40
        const val SAMPLE_EVERY_PAGES = 10
        val PART_SIZES = listOf(256, 512, 1024)

        val QUALITY_CASES = listOf(
            QualityCase("default quality", false),
            QualityCase("best quality", true),
        )
    }
}
