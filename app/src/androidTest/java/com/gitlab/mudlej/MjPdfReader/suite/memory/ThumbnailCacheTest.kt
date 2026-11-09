// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.GoToPageRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.gotopage.GoToPageActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ThumbnailCacheTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val goToPage = GoToPageRobot()

    @Test
    @TestId("MEM-08")
    fun oneThumbnailCacheHoldsOneDocumentNoMatterHowManyThumbnailsAreBound() {
        val fixture = MemoryFixtures.file(MemoryFixtures.LARGE)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val readerOnly = FileDescriptors.countFor(fixture)
            assertEquals(
                "the reader alone must hold exactly one descriptor before the grid opens",
                1,
                readerOnly,
            )

            openGrid()

            val samples = mutableListOf<Int>()
            var page = 0
            while (page < CELLS_TO_SCROLL) {
                page += SCROLL_STEP
                goToPage.scrollGridTo(minOf(page, MemoryFixtures.LARGE_PAGES - 1))
                goToPage.awaitGridScrollIdle()
                samples += FileDescriptors.countFor(fixture)
            }

            val wrong = samples.filter { it != READER_PLUS_GRID }
            assertEquals(
                "the reader plus the grid's own cache is $READER_PLUS_GRID descriptors, and no sample taken " +
                    "across $CELLS_TO_SCROLL cells may read anything else. These did: $wrong",
                emptyList<Int>(),
                wrong,
            )

            goToPage.pressBack()
            Screens.awaitGone(GoToPageActivity::class.java, Await.LONG_TIMEOUT_MS)

            MemoryReader.awaitDescriptorCount(fixture, 1)
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    @Test
    @TestId("MEM-10")
    fun theThumbnailCacheRespectsItsMemoryBound() {
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.LARGE)
        ).use {
            reader.awaitReady()
            openGrid()

            assertEquals(
                "exactly one page thumbnail cache must be live, otherwise the readings below cannot be " +
                    "attributed to the grid under test",
                1,
                CacheStats.liveThumbnailCaches(),
            )
            val maxSize = CacheStats.thumbnailCacheStats().maxSizeKb
            assertEquals(
                "the thumbnail LruCache is sized maxMemory divided by 1024 divided by 8 kilobytes",
                CacheStats.expectedLruMaxSizeKilobytes(),
                maxSize,
            )

            goToPage.scrollGridTo(0)
            goToPage.awaitGridScrollIdle()
            val afterFirstScreen = Heap.settledJavaHeap()

            val samples = mutableListOf<Int>()
            var page = 0
            while (page < MemoryFixtures.LARGE_PAGES - 1) {
                page += SCROLL_STEP
                goToPage.scrollGridTo(minOf(page, MemoryFixtures.LARGE_PAGES - 1))
                goToPage.awaitGridScrollIdle()
                samples += CacheStats.thumbnailCacheStats().sizeKb
            }

            val overBound = samples.filter { it > maxSize }
            assertEquals(
                "LruCache eviction must hold every sample at or below $maxSize kilobytes. These exceeded " +
                    "it: $overBound",
                emptyList<Int>(),
                overBound,
            )
            assertTrue(
                "the cache must still hold something at the end, otherwise eviction simply emptied it and " +
                    "the bound was met for the wrong reason",
                CacheStats.thumbnailCacheStats().sizeKb > 0,
            )

            val afterFullScroll = Heap.settledJavaHeap()
            assertTrue(
                Heap.describe("the heap after scrolling every page", afterFirstScreen, afterFullScroll, BAND),
                Heap.withinBand(afterFirstScreen, afterFullScroll, BAND),
            )

            goToPage.pressBack()
            Screens.awaitGone(GoToPageActivity::class.java, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("MEM-11")
    @TestDuration(DurationCategory.LONG)
    fun theThumbnailCacheKeySpaceIsBoundedBySixWidthBuckets() {
        val keys = mutableSetOf<String>()

        for (columns in MIN_COLUMNS..MAX_COLUMNS) {
            SettingsSeeder.seed(Preferences.goToPageGridColumnsKey, columns)

            ActivityScenario.launch<MainActivity>(
                MemoryReader.fixtureViewIntent(MemoryFixtures.LARGE)
            ).use {
                reader.awaitReady()
                openGrid()

                var page = 0
                while (page < CELLS_PER_COLUMN_PASS) {
                    page += COLUMN_PASS_STEP
                    goToPage.scrollGridTo(page)
                    goToPage.awaitGridScrollIdle()
                }
                keys += CacheStats.thumbnailCacheKeys()

                goToPage.pressBack()
                Screens.awaitGone(GoToPageActivity::class.java, Await.LONG_TIMEOUT_MS)
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        val malformed = keys.filterNot { KEY_PATTERN.matches(it) }
        assertEquals(
            "every thumbnail cache key must read p<page>-w<bucket>. These did not: $malformed",
            emptyList<String>(),
            malformed,
        )
        val widths = keys.mapNotNull { KEY_PATTERN.find(it)?.groupValues?.getOrNull(2)?.toIntOrNull() }.toSet()
        val unexpected = widths - CacheStats.THUMBNAIL_WIDTH_BUCKETS.toSet()
        assertEquals(
            "cell widths must be collapsed into the six declared buckets rather than cached per pixel width. " +
                "These widths were outside them: $unexpected",
            emptySet<Int>(),
            unexpected,
        )
        assertTrue(
            "six column counts must produce at most six distinct bucket widths, they produced $widths",
            widths.size <= CacheStats.THUMBNAIL_WIDTH_BUCKETS.size,
        )
    }

    private fun openGrid() {
        goToPage.openDialogFromReader()
        goToPage.awaitDialog()
        goToPage.tapAllPages()
        goToPage.awaitGridShown()
    }

    private companion object {
        const val READER_PLUS_GRID = 2
        const val CELLS_TO_SCROLL = 300
        const val SCROLL_STEP = 25
        const val CELLS_PER_COLUMN_PASS = 30
        const val COLUMN_PASS_STEP = 10
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 7
        const val BAND = 0.30
        val KEY_PATTERN = Regex("p(\\d+)-w(\\d+)")
    }
}
