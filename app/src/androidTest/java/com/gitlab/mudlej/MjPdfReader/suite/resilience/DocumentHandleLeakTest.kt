// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.SearchResult
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.gotopage.GoToPageActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchCoordinator
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.ref.WeakReference

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class DocumentHandleLeakTest : ResilienceTestCase() {

    @After
    fun clearSearchState() {
        runCatching { SearchCoordinator.clearDebugProgress() }
    }

    @Test
    @TestId("RES-18")
    fun closingTextModeWhileItOpensLeaksNoDocument() {
        val file = fixture(ResilienceFixtures.LARGE)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val baseline = FileDescriptors.countFor(file)
        val since = ResilienceLogcat.since()

        repeat(IMPATIENT_OPENS) {
            startTextModeInReaderTask(file)
            TestEnvironment.uiDevice.pressBack()
            Screens.awaitGone(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
        }
        FileDescriptors.awaitSettledCountOf(file, baseline)
        assertEquals(
            "opening and abandoning text mode $IMPATIENT_OPENS times must leave the handle count where it " +
                "started, ${FileDescriptors.describe(file)}. Each back press is sent as soon as the activity " +
                "resumes rather than when the loading indicator appears, because the indicator is hidden the " +
                "moment the first page binds and on a warm page cache that happens between two polls, so a " +
                "wait for it times out on a screen that opened correctly and the impatient back press this " +
                "test exists to make is never sent at all",
            baseline,
            FileDescriptors.countFor(file),
        )
        assertTrue(
            "no text mode activity may remain in any lifecycle stage",
            !Screens.isInTask(TextModeActivity::class.java),
        )
        ResilienceLogcat.assertAbsent(
            since,
            ResilienceLogcat.EXTRACTOR_FAILURE_MARKER,
            "backing out of text mode must close the extractor cleanly rather than fail to open it",
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-19")
    @TestDuration(DurationCategory.SHORT)
    fun aRunningSearchDoesNotRetainTheReader() {
        val file = fixture(ResilienceFixtures.LARGE)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val hash = reader.awaitFileHashResolved()
        val listener = SilentListener()

        val readerReference = startSearchAndWeaklyReferenceTheReader(file, hash, listener)

        assertTrue(
            "the search must still be running at the moment the reader is finished, otherwise this test " +
                "proves nothing about a live search holding the screen",
            SearchCoordinator.subscribeIfRunning(hash, SEARCH_TERM, IGNORE_ACCENTS, listener),
        )

        ReaderProbe.finishAndRemoveReaderTasks()
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        SearchCoordinator.unsubscribe(listener)

        ReaderProbe.assertCollected(readerReference, "the reader a running search was started from")

        SearchIdle.awaitFinished()
        assertNotNull(
            "the search must run to completion after the reader is gone and cache its session. The term is " +
                "$SEARCH_TERM rather than a word on every page because SearchSessionCache.put drops any " +
                "session carrying more than 5000 hits instead of storing it, and \"the\" appears over five " +
                "thousand times in ${ResilienceFixtures.LARGE}, so that term would make this assertion fail " +
                "on a search that completed perfectly",
            SearchSessionCache.get(hash, SEARCH_TERM, IGNORE_ACCENTS),
        )
    }

    @Test
    @TestId("RES-20")
    fun theGoToPageDialogIsReleasedWithTheReader() {
        val file = fixture(ResilienceFixtures.LARGE)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val baseline = FileDescriptors.countFor(file)
        val since = ResilienceLogcat.since()

        openGoToPageDialog()
        val withDialog = FileDescriptors.awaitCount(file, baseline + 1)
        assertEquals(
            "the dialog's thumbnail cache must open its own document handle, otherwise the release assertion " +
                "below measures nothing",
            baseline + 1,
            withDialog,
        )

        val readerReference = weaklyReferenceResumedReaderAndFinishIt()

        FileDescriptors.awaitSettledCountOf(file, 0)
        ReaderProbe.assertCollected(readerReference, "the reader a Go To Page dialog was left open on")
        ResilienceLogcat.assertAbsent(
            since,
            ResilienceLogcat.WINDOW_LEAK_MARKER,
            "destroying the reader with the dialog up must dismiss the dialog rather than leak its window",
        )
    }

    @Test
    @TestId("RES-21")
    @TestDuration(DurationCategory.LONG)
    fun repeatedPageGridCyclesDoNotGrowHandles() {
        val file = fixture(ResilienceFixtures.LARGE)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val documentBaseline = FileDescriptors.countFor(file)
        val descriptorBaseline = FileDescriptors.totalDescriptorCount()

        repeat(GRID_CYCLES) {
            startGridInReaderTask(file)
            ListScreens.awaitItemsAtLeast(R.id.pageGrid, 1, Await.LONG_TIMEOUT_MS)
            TestEnvironment.uiDevice.pressBack()
            Screens.awaitGone(GoToPageActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        val settled = FileDescriptors.awaitSettledCount(file)
        assertTrue(
            "after $GRID_CYCLES open and close cycles the document handle count must be within one of the " +
                "$documentBaseline it started at, but it settled at $settled",
            kotlin.math.abs(settled - documentBaseline) <= 1,
        )
        val descriptorGrowth = FileDescriptors.totalDescriptorCount() - descriptorBaseline
        assertTrue(
            "the process file descriptor table grew by $descriptorGrowth entries across $GRID_CYCLES cycles, " +
                "which is more than the $DESCRIPTOR_GROWTH_BUDGET a non leaking screen may account for",
            descriptorGrowth < DESCRIPTOR_GROWTH_BUDGET,
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private fun startSearchAndWeaklyReferenceTheReader(
        file: File,
        hash: String,
        listener: SearchCoordinator.Listener,
    ): WeakReference<Activity> {
        val activity = ReaderProbe.requireResumedReader()
        SearchCoordinator.startOrSubscribe(
            activity,
            android.net.Uri.fromFile(file).toString(),
            null,
            hash,
            SEARCH_TERM,
            IGNORE_ACCENTS,
            listener,
        )
        SearchIdle.awaitPagesScanned(MINIMUM_PAGES_SCANNED)
        return WeakReference(activity)
    }

    private fun weaklyReferenceResumedReaderAndFinishIt(): WeakReference<Activity> {
        val activity = ReaderProbe.requireResumedReader()
        val reference = WeakReference<Activity>(activity)
        TestEnvironment.onMainThread { activity.finishAndRemoveTask() }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        return reference
    }

    private fun startTextModeInReaderTask(file: File) {
        val intent = TextModeProbe.intentFor(file)
        ReaderProbe.onReader { it.startActivity(intent) }
        Screens.awaitResumed(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun startGridInReaderTask(file: File) {
        val intent = Intent(TestEnvironment.targetContext, GoToPageActivity::class.java).apply {
            putExtra(com.gitlab.mudlej.MjPdfReader.pdf.PDF.filePathKey, android.net.Uri.fromFile(file).toString())
            putExtra(com.gitlab.mudlej.MjPdfReader.pdf.PDF.pageNumberKey, 0)
        }
        ReaderProbe.onReader { it.startActivity(intent) }
        Screens.awaitResumed(GoToPageActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun openGoToPageDialog() {
        androidx.test.espresso.Espresso.onView(
            androidx.test.espresso.matcher.ViewMatchers.withId(R.id.readerActionsOption)
        ).perform(androidx.test.espresso.action.ViewActions.click())
        val label = TestEnvironment.targetContext.getString(R.string.go_to_page)
        val row = TestEnvironment.uiDevice.wait(Until.findObject(By.text(label)), Await.DEFAULT_TIMEOUT_MS)
            ?: throw AssertionError("the reader actions menu offers no \"$label\" row")
        row.click()
        Await.until("the Go To Page dialog is showing", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.hasObject(By.text(label))
        }
    }

    private class SilentListener : SearchCoordinator.Listener {
        override fun onProgress(pagesScanned: Int, pageCount: Int) = Unit
        override fun onResults(results: List<SearchResult>, finished: Boolean) = Unit
    }

    private companion object {
        const val IMPATIENT_OPENS = 10
        const val GRID_CYCLES = 20
        const val DESCRIPTOR_GROWTH_BUDGET = 10
        const val SEARCH_TERM = "orinoco"
        const val IGNORE_ACCENTS = false
        const val MINIMUM_PAGES_SCANNED = 5
    }
}
