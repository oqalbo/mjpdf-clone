// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.GoToPageRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.gotopage.GoToPageActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ReleaseOnLeaveTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val goToPage = GoToPageRobot()
    private val home = HomeRobot()

    @Test
    @TestId("MEM-30")
    fun leavingTheThumbnailGridReleasesItsThumbnails() {
        val fixture = MemoryFixtures.file(MemoryFixtures.LARGE)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            goToPage.openDialogFromReader()
            goToPage.tapAllPages()
            goToPage.awaitGridShown()

            var cell = 0
            while (cell < CELLS_TO_SCROLL) {
                cell += GRID_STEP
                goToPage.scrollGridTo(cell)
                goToPage.awaitGridScrollIdle()
            }
            val filled = CacheStats.thumbnailCacheStats()
            assertTrue(
                "scrolling $CELLS_TO_SCROLL cells must have filled the thumbnail cache, it held $filled",
                filled.entryCount > 0,
            )
            CacheStats.forgetClosedThumbnailStats()

            goToPage.pressBack()
            Screens.awaitGone(GoToPageActivity::class.java, Await.LONG_TIMEOUT_MS)
            MemoryReader.awaitDescriptorCount(fixture, 1)

            val afterClose = CacheStats.thumbnailCacheStatsAfterClose()
            assertEquals(
                "PageThumbnailCache.close must run evictAll and not merely cancel its scope, so the cache " +
                    "recorded at the end of close must be empty. It held $afterClose",
                0,
                afterClose.entryCount,
            )
            assertEquals(
                "an emptied LruCache reports a size of zero kilobytes",
                0,
                afterClose.sizeKb,
            )
            assertEquals(
                "only the reader's own document may remain open once the grid is gone",
                1,
                FileDescriptors.countFor(fixture),
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    @Test
    @TestId("MEM-32")
    fun leavingTheHighlightsListReleasesTheSweptHighlights() {
        SettingsSeeder.seed(Preferences.detectExistingHighlightsKey, true)
        val fixture = MemoryFixtures.file(MemoryFixtures.MANY_HIGHLIGHTS)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val beforeSweep = Heap.settledJavaHeap()

            MemoryReader.openAction(R.string.user_highlights_title)
            Screens.awaitResumed(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            HighlightScreens.awaitScanFinished(UserHighlightsActivity::class.java)
            val rows = HighlightScreens.awaitRowCountAtLeast(
                UserHighlightsActivity::class.java,
                R.id.userHighlightsRecyclerView,
                MemoryFixtures.MANY_HIGHLIGHTS_GROUPS,
            )
            assertEquals(
                "the sweep must have listed every highlight in the fixture before the release is measured",
                MemoryFixtures.MANY_HIGHLIGHTS_GROUPS,
                rows,
            )

            val screen = WeakReference(
                TestEnvironment.resumedActivityOfType(UserHighlightsActivity::class.java)
                    ?: throw AssertionError("The highlights list is not resumed, so there is nothing to release")
            )

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            Await.untilCollected(screen)

            val afterSweep = Heap.settledJavaHeap()
            assertTrue(
                Heap.describe("the heap after leaving the highlights list", beforeSweep, afterSweep, SWEEP_BAND),
                Heap.withinBand(beforeSweep, afterSweep, SWEEP_BAND),
            )

            MemoryReader.awaitDescriptorCount(fixture, 1)
            MemoryReader.holdDescriptorCount(fixture, 1)
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    @Test
    @TestId("MEM-33")
    @TestDuration(DurationCategory.SHORT)
    fun theCoverCacheSurvivesLeavingHomeAndStaysBounded() {
        LibrarySeed.seedRecords()

        home.startFromLauncher(Await.LONG_TIMEOUT_MS)
        home.awaitShownAndReturn(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)

        scrollHomeTo { adapterItemCount() - 1 }
        scrollHomeTo { 0 }
        home.awaitGenerationStable()

        val maxSize = CacheStats.coverCacheStats().maxSizeKb
        val whileOpen = CacheStats.coverCacheStats()
        assertTrue(
            "covers must genuinely have been cached before the survival is measured, the cache held " +
                "$whileOpen",
            whileOpen.sizeKb > 0,
        )

        val collected = ReferenceQueue<android.app.Activity>()
        val screen = WeakReference(
            TestEnvironment.resumedActivityOfType(HomeActivity::class.java)
                ?: throw AssertionError("Home is not resumed, so there is nothing to leave"),
            collected,
        )
        TestEnvironment.onMainThread { screen.get()?.finish() }
        Screens.awaitGone(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
        Heap.clearAndroidPieAccessibilityPool()
        Await.untilCollected(screen, collected)

        val afterClose = CacheStats.coverCacheStats()
        assertTrue(
            "CoverCache is a process wide singleton reached by getInstance and it deliberately outlives " +
                "Home, so its bitmaps must still be there. It held $afterClose",
            afterClose.sizeKb > 0,
        )
        assertTrue(
            "surviving Home does not exempt the cover cache from its bound of $maxSize kilobytes, it held " +
                "$afterClose",
            afterClose.sizeKb <= maxSize,
        )
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
        const val CELLS_TO_SCROLL = 200
        const val GRID_STEP = 20
        const val SWEEP_BAND = 0.30
    }
}
