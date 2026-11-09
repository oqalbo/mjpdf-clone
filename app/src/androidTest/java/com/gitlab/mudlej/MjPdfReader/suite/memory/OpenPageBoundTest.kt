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
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class OpenPageBoundTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("MEM-20")
    @TestDuration(DurationCategory.SHORT)
    fun openPdfiumRenderPagesAreCappedAtTwelve() {
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.SCANNED_LONG)
        ).use {
            val opened = reader.awaitReady()
            assertEquals(
                "the fixture must be the three hundred page scanned book, which is where native pages are " +
                    "the largest per page cost",
                MemoryFixtures.SCANNED_LONG_PAGES as Int?,
                opened.pageCount,
            )
            val samples = mutableListOf<OpenPageStats>()
            var page = 0
            fun sampleThrough(lastPage: Int) {
                while (page < lastPage) {
                    page += SAMPLE_EVERY_PAGES
                    MemoryReader.jumpToPage(reader, page)
                    reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
                    samples += CacheStats.openPageStats(MemoryReader.pdfView())
                }
            }

            sampleThrough(PAGES_TO_PAGE_THROUGH)
            val startingNativeHeap = Heap.settledNativeHeap()
            sampleThrough(PAGES_TO_PAGE_THROUGH * 2)

            val overCap = samples.filter { it.renderPageOrder > CacheStats.MAX_OPEN_RENDER_PAGES }
            assertEquals(
                "PdfFile.evictOverCap must hold the render page order at or below " +
                    "${CacheStats.MAX_OPEN_RENDER_PAGES} open native pages. These samples did not: $overCap",
                emptyList<OpenPageStats>(),
                overCap,
            )

            val nothingOpen = samples.filter { it.renderPageOrder == 0 }
            assertEquals(
                "a document with no page open at all satisfies any cap, so every sample taken after a " +
                    "render settled must report at least one open render page. These did not: $nothingOpen",
                emptyList<OpenPageStats>(),
                nothingOpen,
            )

            val withText = samples.filter { it.textOpenedPages != 0 }
            assertEquals(
                "no text selection and no search ran in this test, so no page may hold an open text page. " +
                    "These samples did: $withText",
                emptyList<OpenPageStats>(),
                withText,
            )
            val geometryOnly = samples.filter { geometryOnlyPages(it) != 0 }
            assertEquals(
                "a page opened only for geometry sits in neither the render order nor the text opened set, " +
                    "so evictOverCap cannot see it and nothing can close it until the document is disposed. " +
                    "The count must be zero. These samples carried geometry only pages: $geometryOnly",
                emptyList<OpenPageStats>(),
                geometryOnly,
            )

            val settledNativeHeap = Heap.settledNativeHeap()
            assertTrue(
                Heap.describe(
                    "the native heap after another $PAGES_TO_PAGE_THROUGH scanned pages with populated caches",
                    startingNativeHeap,
                    settledNativeHeap,
                    NATIVE_BAND,
                ),
                Heap.withinBand(startingNativeHeap, settledNativeHeap, NATIVE_BAND),
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("MEM-25")
    fun aThreeHundredPageScannedBookKeepsMemoryBounded() {
        val fixture = MemoryFixtures.file(MemoryFixtures.SCANNED_LONG)
        val lastPage = MemoryFixtures.SCANNED_LONG_PAGES - 1

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            val opened = reader.awaitReady()
            assertEquals(
                "the fixture must be the three hundred page scanned book",
                MemoryFixtures.SCANNED_LONG_PAGES as Int?,
                opened.pageCount,
            )
            val starting = Heap.settledNativeHeap()

            pageAcross(0, lastPage)
            val atEnd = reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the traversal must have reached the final page",
                lastPage as Int?,
                atEnd.page,
            )
            assertEquals(
                "the final page must be fully rendered rather than left mid render",
                RenderActivity.IDLE,
                atEnd.render,
            )
            val forward = Heap.settledNativeHeap()

            pageAcross(lastPage, 0)
            reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            val back = Heap.settledNativeHeap()

            assertTrue(
                Heap.describe("the native heap after scrolling to the last page", starting, forward, SCANNED_BAND),
                Heap.withinBand(starting, forward, SCANNED_BAND),
            )
            assertTrue(
                Heap.describe("the native heap after scrolling back to page one", starting, back, SCANNED_BAND),
                Heap.withinBand(starting, back, SCANNED_BAND),
            )

            val finalStats = CacheStats.openPageStats(MemoryReader.pdfView())
            assertTrue(
                "the open native page count must still be at or below ${CacheStats.MAX_OPEN_RENDER_PAGES} " +
                    "after the whole book was traversed twice, it read $finalStats",
                finalStats.renderPageOrder <= CacheStats.MAX_OPEN_RENDER_PAGES,
            )
            assertTrue(
                "and it must be above zero, because a document holding no open page satisfies the cap " +
                    "without the cap ever having been tested. It read $finalStats",
                finalStats.renderPageOrder > 0,
            )

            MemoryReader.jumpToPage(reader, lastPage)
            val settledOnLast = reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the reader must still render the final page at the end of the run",
                RenderActivity.IDLE,
                settledOnLast.render,
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    private fun pageAcross(from: Int, to: Int) {
        val step = if (to > from) TRAVERSAL_STEP else -TRAVERSAL_STEP
        var page = from
        while (page != to) {
            page = if (step > 0) minOf(page + step, to) else maxOf(page + step, to)
            MemoryReader.jumpToPage(reader, page, Await.VERY_LONG_TIMEOUT_MS)
        }
    }

    private fun geometryOnlyPages(stats: OpenPageStats): Int =
        stats.openPages - stats.renderPageOrder - stats.textOpenedPages

    private companion object {
        const val PAGES_TO_PAGE_THROUGH = 100
        const val SAMPLE_EVERY_PAGES = 10
        const val TRAVERSAL_STEP = 10
        const val NATIVE_BAND = 0.25
        const val SCANNED_BAND = 0.30
    }
}
