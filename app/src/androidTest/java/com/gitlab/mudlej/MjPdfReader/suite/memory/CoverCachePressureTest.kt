// Written by Mudlej. License is GPLv3.
package com.gitlab.mudlej.MjPdfReader.suite.memory

import android.util.Log
import java.io.File
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.home.BookSeed
import com.gitlab.mudlej.MjPdfReader.suite.home.HomeLibrary
import com.gitlab.mudlej.MjPdfReader.suite.scan.ScanPrefs
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.LruCacheStats
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.CoverCache
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeGridSize
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeViewMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class CoverCachePressureTest {
    @get:Rule
    val rules = SuiteRules.standard()
    private val home = HomeRobot()

    @Test
    @TestId("MEM-12")
    fun theCoverCacheRespectsItsMemoryBoundOverALargeLibrary() {
        SettingsSeeder.seed(Preferences.homeGridSizeKey, HomeGridSize.LARGE.name)
        SettingsSeeder.seed(Preferences.homeViewModeKey, HomeViewMode.GRID.name)
        val directory = rules.testDirectory.directory()
        val copies = LibrarySeed.sourceDocuments().map { source ->
            source.copyTo(File(directory, source.name))
        }
        Fixtures.scanAndWait(copies)
        val seeded = HomeLibrary.seedInPlace(directory, copies.map {
            BookSeed(it.name, indexed = false, pages = 1)
        }).books
        AppDatabase.getInstance(TestEnvironment.targetContext).scannedPdfEntryDao().upsertAll(seeded.map { book ->
            val fingerprint = SourceFingerprint.normalized(book.path, book.file.length(), book.file.lastModified())
            ScannedPdfEntry(fingerprint.path, fingerprint.size, fingerprint.lastModified, book.hash, 1, ScannedPdfMetadataState.COMPLETE)
        })
        ScanPrefs.seedSelected(directory)
        home.startFromLauncher(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        val grid = home.selectTab(HomeTab.LIBRARY)
        grid.awaitItemCount(seeded.size)
        val items = grid.items()
        assertEquals("the UI lists distinct source identities", seeded.size, items.map { it.hash }.distinct().size)
        val cache = CoverCache.getInstance(TestEnvironment.targetContext)
        val limit = idleStats(cache).maxSizeKb
        assertEquals(CacheStats.expectedLruMaxSizeKilobytes(), limit)
        val evictionsBefore = checkNotNull(cache.debugEvictionCount())

        fun visit(index: Int): Long {
            val bytes = grid.cellAt(index).awaitCover().byteCount.toLong()
            val stats = idleStats(cache)
            assertTrue("settled cache uses ${stats.sizeKb} KiB against $limit KiB", stats.sizeKb <= limit)
            assertTrue("the cache retains real covers", stats.entryCount > 0)
            return bytes / 1024
        }

        val visited = mutableListOf<Int>()
        var loadedKiB = 0L
        for (index in items.indices) {
            loadedKiB += visit(index)
            visited += index
            if (loadedKiB > limit && checkNotNull(cache.debugEvictionCount()) > evictionsBefore) break
        }
        assertTrue("distinct displayed covers total $loadedKiB KiB and must exceed $limit KiB", loadedKiB > limit)
        assertTrue("the workload must cause actual LRU eviction", checkNotNull(cache.debugEvictionCount()) > evictionsBefore)
        Log.i("MjPdfCachePressure", "covers=${visited.size} loadedKiB=$loadedKiB budgetKiB=$limit evictions=${cache.debugEvictionCount()}")
        val afterWarm = Heap.settledJavaHeap()
        visited.asReversed().forEach { visit(it) }
        visited.forEach { visit(it) }
        home.awaitGenerationStable()
        val afterRevisits = Heap.settledJavaHeap()
        assertTrue(
            Heap.describe("heap after revisiting the eviction workload", afterWarm, afterRevisits, BAND),
            Heap.withinBand(afterWarm, afterRevisits, BAND),
        )
    }

    private fun idleStats(cache: CoverCache): LruCacheStats = Await.untilNotNull(
        "cover loads settle before measuring the cache", Await.LONG_TIMEOUT_MS,
    ) { runBlocking { cache.debugIdleCacheStats() } }

    private companion object {
        const val BAND = 0.30
    }
}
