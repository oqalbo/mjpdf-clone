// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.resilience.ResilienceLogcat
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeDebug
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class ScreenReleaseTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("MEM-03")
    fun textModeReleasesItsExtractorAfterANormalOpenAndLeave() {
        val fixture = MemoryFixtures.file(MemoryFixtures.LARGE)
        val since = AppLogcat.since()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val readerOnly = FileDescriptors.countFor(fixture)
            assertTrue(
                "the reader must hold at least one descriptor before text mode opens, otherwise the return " +
                    "assertion below is vacuous",
                readerOnly >= 1,
            )

            repeat(REPETITIONS) { pass ->
                MemoryReader.openAction(R.string.text_mode)
                Screens.awaitResumed(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
                awaitBoundPages(READY_POSITIONS, pass)

                TestEnvironment.uiDevice.pressBack()
                Screens.awaitGone(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)

                MemoryReader.awaitDescriptorCount(fixture, readerOnly)
            }

            MemoryReader.holdDescriptorCount(fixture, readerOnly)
            assertEquals(
                "ten completed text mode opens must leave exactly the reader's own descriptor",
                readerOnly,
                FileDescriptors.countFor(fixture),
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
        val extractorFailures = AppLogcat.appLinesSince(since)
            .filter { it.contains(ResilienceLogcat.EXTRACTOR_FAILURE_MARKER) }
        assertEquals(
            "ten text mode open and leave cycles must never fail to open the text extractor",
            emptyList<String>(),
            extractorFailures,
        )
    }

    @Test
    @TestId("MEM-06")
    @TestDuration(DurationCategory.NORMAL)
    fun theHighlightsSweeperReleasesItsDocumentAfterAFullSweep() {
        val fixture = MemoryFixtures.file(MemoryFixtures.MANY_HIGHLIGHTS)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val readerOnly = FileDescriptors.countFor(fixture)
            val beforeSweep = Heap.settledJavaHeap()

            MemoryReader.openAction(R.string.user_highlights_title)
            Screens.awaitResumed(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            HighlightScreens.awaitScanFinished(UserHighlightsActivity::class.java)

            val target = HighlightScreens.scanTarget(UserHighlightsActivity::class.java)
            assertEquals(
                "the sweep must have walked every page of the fixture, otherwise it did not run to completion",
                MemoryFixtures.MANY_HIGHLIGHTS_PAGES,
                target,
            )
            val rows = HighlightScreens.awaitRowCountAtLeast(
                UserHighlightsActivity::class.java,
                R.id.userHighlightsRecyclerView,
                MemoryFixtures.MANY_HIGHLIGHTS_GROUPS,
            )
            assertEquals(
                "every highlight in the fixture must be listed before leaving, proving the sweep completed",
                MemoryFixtures.MANY_HIGHLIGHTS_GROUPS,
                rows,
            )

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)

            MemoryReader.awaitDescriptorCount(fixture, readerOnly)
            MemoryReader.holdDescriptorCount(fixture, readerOnly)

            val afterSweep = Heap.settledJavaHeap()
            assertTrue(
                Heap.describe("the heap after a 600 highlight sweep", beforeSweep, afterSweep, SWEEP_BAND),
                Heap.withinBand(beforeSweep, afterSweep, SWEEP_BAND),
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    private fun awaitBoundPages(positions: Int, pass: Int) {
        Await.until(
            "pass ${pass + 1} binds at least $positions text mode pages",
            Await.VERY_LONG_TIMEOUT_MS,
            pollMs = POLL_MS,
        ) {
            TestEnvironment.onMainThread {
                val activity = TestEnvironment.resumedActivityOfType(TextModeActivity::class.java)
                    ?: return@onMainThread false
                val pages = activity.findViewById<RecyclerView>(R.id.textPagesRecyclerView)
                    ?: return@onMainThread false
                (0 until positions).count { position ->
                    TextModeDebug.pageSnapshot(pages, position) != null
                } >= positions
            }
        }
    }

    private companion object {
        const val REPETITIONS = 10
        const val READY_POSITIONS = 1
        const val POLL_MS = 250L
        const val SWEEP_BAND = 0.40
    }
}
