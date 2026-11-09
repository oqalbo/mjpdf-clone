// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.GoToPageRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class TrimMemoryTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val goToPage = GoToPageRobot()
    private val home = HomeRobot()

    @After
    fun closeAnyReaderTask() {
        runCatching { MemoryTasks.finishReaderTasks() }
    }

    @Test
    @TestId("MEM-36")
    fun aForegroundTrimAtRunningLowDropsPreviewsAndReadingContinues() {
        val fixture = MemoryFixtures.file(MemoryFixtures.LARGE)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            readThrough(TRIM_PAGE_INDEX)
            MemoryReader.jumpToPage(reader, TRIM_PAGE_INDEX)
            reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)

            val before = Heap.settledJavaHeap()
            val populated = CacheStats.previewStats(MemoryReader.pdfView())
            assertTrue(
                "the preview store must hold entries before the trim, otherwise the drop is unobservable. " +
                    "It held $populated",
                populated.entryCount > 0,
            )

            val trim = MemoryReader.observeTrimMemory(MemoryReader.pdfView(), RUNNING_LOW) {
                CacheStats.previewStats(MemoryReader.pdfView())
            }
            assertTrue("previews exist at delivery", trim.before.entryCount > 0)
            assertEquals("the delivered low trim clears previews", 0, trim.after.entryCount)
            MemoryReader.jumpToPage(reader, TRIM_PAGE_INDEX + PAGES_AFTER_TRIM)
            val moved = reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the trim must not disturb the page the user reads on to next",
                (TRIM_PAGE_INDEX + PAGES_AFTER_TRIM) as Int?,
                moved.page,
            )
            assertEquals(
                "the page the reader moved to must render rather than stay blank",
                RenderActivity.IDLE,
                moved.render,
            )
            assertEquals(
                "the trim must not have cost the reader its document handle",
                1,
                FileDescriptors.countFor(fixture),
            )

            val after = Heap.settledJavaHeap()
            android.util.Log.i(
                LOG_TAG,
                "MEM-36 heap readings, no band asserted: before the trim " +
                    "${Heap.megabytes(before)} MB, after ${Heap.megabytes(after)} MB",
            )
        }
    }

    @Test
    @TestId("MEM-37")
    fun aTrimBelowRunningLowDoesNotDropPreviews() {
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.LARGE)
        ).use {
            reader.awaitReady()
            readThrough(TRIM_PAGE_INDEX)

            val populated = CacheStats.previewStats(MemoryReader.pdfView())
            assertTrue(
                "the preview store must hold more than $MINIMUM_ENTRIES entries before the trim, otherwise " +
                    "an unchanged count proves nothing. It held $populated",
                populated.entryCount > MINIMUM_ENTRIES,
            )

            val view = MemoryReader.pdfView()
            val moderate = MemoryReader.observeTrimMemory(view, RUNNING_MODERATE) {
                CacheStats.previewStats(view)
            }
            assertTrue("previews exist at delivery", moderate.before.entryCount > MINIMUM_ENTRIES)
            assertEquals(
                "a delivered moderate trim leaves the preview store and reuse pool unchanged",
                moderate.before,
                moderate.after,
            )

            val low = MemoryReader.observeTrimMemory(view, RUNNING_LOW) { CacheStats.previewStats(view) }
            assertTrue("the control store is populated at delivery", low.before.entryCount > 0)
            assertEquals("the delivered low trim clears the same store", 0, low.after.entryCount)

        }
    }

    @Test
    @TestId("MEM-38")
    @TestDuration(DurationCategory.NORMAL)
    fun theTwoLruCachesDoNotRespondToTrimAndTheGapIsRecorded() {
        LibrarySeed.seedRecords()
        val since = CrashGate.timestampNow()

        home.startFromLauncher(Await.LONG_TIMEOUT_MS)
        home.awaitShownAndReturn(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        scrollHomeTo { adapterItemCount() - 1 }
        scrollHomeTo { 0 }
        home.awaitGenerationStable()

        val coversBefore = CacheStats.coverCacheStats()
        assertTrue(
            "the cover cache must be populated before the trim, it held $coversBefore",
            coversBefore.sizeKb > 0,
        )

        val fixture = MemoryFixtures.file(MemoryFixtures.LARGE)
        MemoryTasks.startReader(reader, ReaderRobot.viewIntent(fixture))
        readThrough(PREVIEW_WARM_PAGES)
        val view: PDFView = MemoryReader.pdfView()
        val previewsBefore = CacheStats.previewStats(view)
        assertTrue(
            "the preview store must be populated too, because it is the control proving the trim really " +
                "reached the app. It held $previewsBefore",
            previewsBefore.entryCount > 0,
        )

        goToPage.openDialogFromReader()
        goToPage.tapAllPages()
        goToPage.awaitGridShown()
        var cell = 0
        while (cell < GRID_CELLS) {
            cell += GRID_STEP
            goToPage.scrollGridTo(cell)
            goToPage.awaitGridScrollIdle()
        }
        val thumbnailsBefore = CacheStats.thumbnailCacheStats()
        assertTrue(
            "the thumbnail cache must be populated before the trim, it held $thumbnailsBefore",
            thumbnailsBefore.sizeKb > 0,
        )

        val trim = MemoryReader.observeTrimMemory(view, RUNNING_CRITICAL, includeImageCaches = true) {
            Triple(CacheStats.previewStats(view), CacheStats.thumbnailCacheStats(), CacheStats.coverCacheStats())
        }
        assertTrue("previews exist when the critical trim arrives", trim.before.first.entryCount > 0)
        assertEquals("the delivered critical trim clears previews", 0, trim.after.first.entryCount)
        assertEquals(
            "the delivered critical trim leaves the thumbnail cache unchanged",
            trim.before.second,
            trim.after.second,
        )
        assertEquals(
            "the delivered critical trim leaves the cover cache unchanged",
            trim.before.third,
            trim.after.third,
        )
        CrashGate.assertNoFatalSince(since)
    }

    private fun readThrough(pages: Int) {
        var page = 0
        while (page < pages) {
            page += READ_STEP
            MemoryReader.jumpToPage(reader, minOf(page, MemoryFixtures.LARGE_PAGES - 1))
            reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
        }
    }

    private fun scrollHomeTo(position: () -> Int) {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java)
                ?: throw AssertionError("Home is not resumed, so its list cannot be scrolled")
            activity.window.decorView.findViewById<RecyclerView>(R.id.pageRecyclerView)
                ?.scrollToPosition(position().coerceAtLeast(0))
        }
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    private fun adapterItemCount(): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java)
            ?: return@onMainThread 0
        activity.window.decorView.findViewById<RecyclerView>(R.id.pageRecyclerView)?.adapter?.itemCount ?: 0
    }

    private companion object {
        const val LOG_TAG = "MjPdfMemory"
        const val RUNNING_LOW = "RUNNING_LOW"
        const val RUNNING_MODERATE = "RUNNING_MODERATE"
        const val RUNNING_CRITICAL = "RUNNING_CRITICAL"
        const val PREVIEW_WARM_PAGES = 20
        const val TRIM_PAGE_INDEX = 30
        const val PAGES_AFTER_TRIM = 5
        const val READ_STEP = 3
        const val GRID_CELLS = 100
        const val GRID_STEP = 20
        const val MINIMUM_ENTRIES = 5
    }
}
