// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchCoordinator
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@ExpectsProcessDeath
@TestId("RES-53")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ProcessKillRestoreTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    fun phaseOneReadToTargetPage() {
        PhaseRun.requirePhase(SCENARIO, 1)

        val file = seededFixture()
        ActivityWatcher().use { watcher ->
            TestEnvironment.targetContext.startActivity(
                ReaderRobot.viewIntent(file)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReady()

            val hash = reader.awaitFileHashResolved()
            jumpToPage(TARGET_PAGE)
            Records.awaitStoredPage(hash, TARGET_PAGE, Await.LONG_TIMEOUT_MS)
            cacheASearchSession(file, hash)

            TestEnvironment.uiDevice.pressHome()
            watcher.awaitReached(MainActivity::class.java, Stage.STOPPED, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    fun phaseTwoAssertRestoredFromDisk() {
        PhaseRun.requirePhase(SCENARIO, 2)

        val since = CrashGate.timestampNow()
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitReady()
        val restored = reader.awaitPageIndex(TARGET_PAGE, Await.VERY_LONG_TIMEOUT_MS)

        assertEquals(
            "the relaunched reader must come back on the page the record holds, which is the only place " +
                "that page survived the kill",
            TARGET_PAGE as Int?,
            restored.page,
        )
        val hash = reader.awaitFileHashResolved()
        assertEquals(
            "the record must still carry the page the reader was killed on",
            TARGET_PAGE,
            Records.byHash(hash)?.pageNumber,
        )
        assertNull(
            "a replaced process cannot carry a cached search session, so finding one means the kill did not " +
                "take the process with it",
            SearchSessionCache.get(hash, PROBE_QUERY, false),
        )
        CrashGate.assertNoFatalSince(since)
    }

    private fun cacheASearchSession(file: File, hash: String) {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: throw AssertionError("no reader is resumed while the search session is being seeded")
        SearchCoordinator.startOrSubscribe(
            activity,
            android.net.Uri.fromFile(file).toString(),
            null,
            hash,
            PROBE_QUERY,
            false,
            object : SearchCoordinator.Listener {
                override fun onProgress(pagesScanned: Int, pageCount: Int) = Unit
                override fun onResults(
                    results: List<com.gitlab.mudlej.MjPdfReader.pdf.SearchResult>,
                    finished: Boolean,
                ) = Unit
            },
        )
        Await.untilNotNull(
            "a search session for the probe query is cached in this process before the kill",
            Await.VERY_LONG_TIMEOUT_MS,
        ) { SearchSessionCache.get(hash, PROBE_QUERY, false) }
    }

    private fun seededFixture(): File =
        com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures.requireSeededFixture(ResilienceFixtures.NORMAL, ensureAccess = false)

    private fun awaitReady() {
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)
    }

    private fun jumpToPage(target: Int) {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw AssertionError("no reader is resumed while jumping to page index $target")
            activity.findViewById<com.github.barteksc.pdfviewer.PDFView>(
                com.gitlab.mudlej.MjPdfReader.R.id.pdfView
            ).jumpTo(target)
        }
        reader.awaitPageIndex(target, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled()
    }

    private companion object {
        const val SCENARIO = "RES-53"
        const val TARGET_PAGE = 15
        const val PROBE_QUERY = "the"
    }
}
