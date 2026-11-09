// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.gitlab.mudlej.MjPdfReader.core.debug.SearchTestHooks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.memory.Heap
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchRunningTest : SearchTestCase() {

    @Test
    @TestId("SRCH-14")
    fun nextWhileTheSearchIsRunningJumpsToTheFirstHitFound() {
        openReader(SearchFixtures.LARGE) {
            val hash = fileHash()
            robot.goToPage(SearchFixtures.LARGE_PAGES)

            SearchOracle.blockAfterNextResultBatch()
            robot.searchWithoutSettling(SearchFixtures.LARGE_COMMON_WORD)
            SearchOracle.awaitResultBatchBlocked()
            val recorder = record(hash, SearchFixtures.LARGE_COMMON_WORD)
            val counter = SearchBar.awaitCounter()

            val handledBefore = SearchOracle.handledNavigationActions()
            SearchBar.tapNext()
            SearchOracle.awaitHandledNavigationActions(handledBefore + 1)
            val afterTap = SearchBar.awaitCounterPosition(1)

            val snapshot = recorder.awaitSnapshotOfSize(afterTap.total)
            val firstHitPage = snapshot.first().pageNumber - 1
            awaitSettledOnPage(firstHitPage)

            assertTrue(
                "the counter total must not shrink after the tap, it read ${afterTap.total}",
                afterTap.total >= counter.total,
            )
            SearchOracle.releaseResultBatch()
            val grown = SearchBar.awaitCounterTotalAbove(afterTap.total, Await.VERY_LONG_TIMEOUT_MS)
            assertTrue(
                "the total must keep growing, proving the tap did not cancel the search",
                grown.total > afterTap.total,
            )
        }
    }

    @Test
    @TestId("SRCH-15")
    fun previousWhileTheSearchIsRunningJumpsToTheLastHitFound() {
        openReader(SearchFixtures.LARGE) {
            val hash = fileHash()
            robot.goToPage(SearchFixtures.LARGE_PAGES)

            SearchOracle.blockAfterNextResultBatch()
            robot.searchWithoutSettling(SearchFixtures.LARGE_COMMON_WORD)
            SearchOracle.awaitResultBatchBlocked()
            val recorder = record(hash, SearchFixtures.LARGE_COMMON_WORD)
            SearchBar.awaitCounter()

            val handledBefore = SearchOracle.handledNavigationActions()
            SearchBar.tapPrevious()
            SearchOracle.awaitHandledNavigationActions(handledBefore + 1)
            val afterTap = Await.untilValue(
                "the counter points at the last hit found so far",
                read = { SearchBar.counter() },
            ) { it != null && it.position == it.total }!!

            val snapshot = recorder.awaitSnapshotOfSize(afterTap.total)
            val lastHitPage = snapshot.last().pageNumber - 1
            awaitSettledOnPage(lastHitPage)

            assertEquals(
                "the reader must sit on the page of the last hit in the partial list",
                lastHitPage,
                pageIndex(),
            )
            SearchOracle.releaseResultBatch()
            SearchIdle.awaitFinished()
        }
    }

    @Test
    @TestId("SRCH-22")
    fun rotatingTheResultsListDuringARunningSearchDoesNotRestartIt() {
        openReader(SearchFixtures.LARGE) {
            SearchOracle.blockAfterNextResultBatch()
            robot.searchWithoutSettling(SearchFixtures.LARGE_COMMON_WORD)
            SearchBar.awaitShown()
            SearchBar.tapLabelWithoutSettling()
            results.awaitShown()
            results.awaitAtLeastOneRow()
            SearchOracle.awaitResultBatchBlocked()

            val progressBefore = results.determinateProgress()
            val countBefore = results.itemCount()

            Rotation.toLandscape(SearchActivity::class.java, Await.LONG_TIMEOUT_MS)
            results.awaitShown()
            results.awaitAtLeastOneRow()

            val progressAfter = results.determinateProgress()
            val countAfter = results.itemCount()
            val titleAfterRotation = results.awaitTitleCount()

            SearchOracle.releaseResultBatch()
            results.awaitSearchFinished()
            val finalTitleCount = results.awaitTitleCount()
            val finalItemCount = results.awaitItemCountMatchesTitle()

            assertTrue(
                "the progress must not reset on rotation, it went from $progressBefore to $progressAfter",
                progressAfter >= progressBefore,
            )
            assertTrue(
                "the row count must not fall on rotation, it went from $countBefore to $countAfter",
                countAfter >= countBefore,
            )
            assertTrue(
                "the same search must have kept producing results across the recreation, the title went from " +
                    "$titleAfterRotation to $finalTitleCount",
                finalTitleCount > titleAfterRotation,
            )
            assertEquals(
                "the finished title count must equal the row count",
                finalItemCount,
                finalTitleCount,
            )
        }
    }

    @Test
    @TestId("SRCH-24")
    fun aRunningSearchCanBeCancelledWithDone() {
        openReader(SearchFixtures.LARGE) { file ->
            val hash = fileHash()
            val since = SearchLogcat.since()
            val handlesBefore = FileDescriptors.awaitSettledCount(file)
            val completedBefore = SearchTestHooks.searchCompletionCount()
            assertTrue("the reader's document handle is observable", handlesBefore > 0)
            SearchOracle.blockAfterNextResultBatch()
            try {
                robot.searchWithoutSettling(SearchFixtures.LARGE_COMMON_WORD)
                SearchBar.awaitShown()
                SearchOracle.awaitResultBatchBlocked()
                SearchIdle.awaitRunning()
                val running = checkNotNull(SearchIdle.progress())
                assertTrue("cancellation occurs before the final page", running.pagesScanned in 1 until running.pageCount)
                assertTrue("the search has opened an additional document handle", FileDescriptors.countFor(file) > handlesBefore)

                SearchBar.tapDone()
                SearchBar.awaitGone()
                Await.until("Done cancels the held search") { SearchIdle.progress() == null }
                SearchOracle.releaseResultBatch()
                FileDescriptors.awaitSettledCountOf(file, handlesBefore)
                assertEquals("cancelled work must not complete a scan", completedBefore, SearchTestHooks.searchCompletionCount())
                assertTrue("no counter or arrow remains", SearchBar.navigationAbsent())
                assertNull("a cancelled scan stores no session", SearchOracle.session(hash, SearchFixtures.LARGE_COMMON_WORD))
                reader.awaitState("the search marker set empties after cancel") {
                    it.markerPages?.isEmpty() == true
                }
                awaitReaderInteractive()
                SearchLogcat.assertNoErrorFrom(since, SearchLogcat.EXTRACTOR_TAG)
            } finally {
                SearchOracle.releaseResultBatch()
            }
        }
    }

    @Test
    @TestId("SRCH-25")
    fun aRunningSearchDoesNotRetainTheReader() {
        val since = SearchLogcat.since()
        val file = fixture(SearchFixtures.LARGE)
        val watcher = ReaderInstanceWatcher()
        val application = TestEnvironment.targetContext.applicationContext as Application
        application.registerActivityLifecycleCallbacks(watcher)
        try {
            TestEnvironment.targetContext.startActivity(
                ReaderRobot.viewIntent(file).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            robot.awaitReaderReady()
            SearchOracle.blockAfterNextResultBatch()
            try {
                robot.searchWithoutSettling(SearchFixtures.LARGE_COMMON_WORD)
                SearchBar.awaitShown()
                SearchOracle.awaitResultBatchBlocked()
                SearchIdle.awaitRunning()

                val reference = WeakReference(watcher.latest())
                watcher.forget()
                TestEnvironment.uiDevice.pressBack()
                Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

                Heap.clearAndroidPieAccessibilityPool()
                Await.untilCollected(reference, RETENTION_TIMEOUT_MS)
                assertNull("the destroyed reader must be collectible while the search runs", reference.get())

                val progress = SearchIdle.progress()
                assertTrue(
                    "the blocked search must still be running after the reader is destroyed, the hook reports $progress",
                    SearchIdle.isRunning(),
                )
                SearchLogcat.assertNoErrorFrom(since, SearchLogcat.COORDINATOR_TAG)
            } finally {
                SearchOracle.releaseResultBatch()
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(watcher)
        }
    }

    @Test
    @TestId("SRCH-26")
    fun theTooManyResultsWarningIsShownOnlyForAVeryLargeResultList() {
        openReader(SearchFixtures.LARGE) {
            robot.search(SearchFixtures.LARGE_COMMON_WORD)
            SearchBar.awaitShown()
            SearchIdle.awaitFinished()

            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitSearchFinished()

            SearchNotices.awaitTooManyResults()
            val count = results.awaitTitleCount()
            assertTrue(
                "the parsed title count must be above the too many results threshold, it read $count",
                count > SearchNotices.TOO_MANY_RESULTS_THRESHOLD,
            )

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitTopIs(MainActivity::class.java)
        }

        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitItemCount(SearchFixtures.BEACON_HITS)

            SearchNotices.assertNoTooManyResults()
        }
    }

    @Test
    @TestId("SRCH-27")
    fun aSearchTooLargeToCacheStillNavigatesToThePickedMatch() {
        openReader(SearchFixtures.LARGE) {
            val hash = fileHash()
            robot.search(SearchFixtures.LARGE_COMMON_WORD)
            SearchBar.awaitShown()
            SearchIdle.awaitFinished()
            SearchBar.awaitCounterStable(timeoutMs = Await.VERY_LONG_TIMEOUT_MS)

            assertNull(
                "a search above the session cache limit must store no session",
                SearchOracle.session(hash, SearchFixtures.LARGE_COMMON_WORD),
            )

            SearchBar.tapLabelWithoutSettling()
            results.awaitShown()
            results.awaitDeterminateProgressVisible()
            val rescanRecorder = record(hash, SearchFixtures.LARGE_COMMON_WORD)
            results.awaitSearchFinished()
            results.scrollToPosition(RESCAN_ROW)
            val expectedPage = rescanRecorder.latest()[RESCAN_ROW].pageNumber - 1

            results.tapRow(RESCAN_ROW)
            Screens.awaitTopIs(MainActivity::class.java)
            robot.awaitReaderResumed()
            awaitSettledOnPage(expectedPage)

            assertEquals("the counter must be gone with no full session to restore", View.GONE as Int?, SearchBar.counterVisibility())
            assertEquals("the previous arrow must be gone", View.GONE as Int?, SearchBar.previousVisibility())
            assertEquals("the next arrow must be gone", View.GONE as Int?, SearchBar.nextVisibility())
            assertEquals("the snackbar label must still offer the results list", SearchBar.resultsLabel(), SearchBar.label())
        }
    }

    private class ReaderInstanceWatcher : Application.ActivityLifecycleCallbacks {

        private var instance: MainActivity? = null

        fun latest(): MainActivity = instance
            ?: throw AssertionError("No reader instance was seen by the lifecycle callbacks")

        fun forget() {
            instance = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is MainActivity) instance = activity
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    companion object {
        const val RESCAN_ROW = 9
        const val RETENTION_TIMEOUT_MS = 5_000L
    }
}
