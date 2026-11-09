// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.history.NavigationHistoryActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@ExpectsProcessDeath
@TestId("HIST-36")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class History36ProcessDeathTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()

    @Test
    fun phaseOneBuildTrailAndBackground() {
        PhaseRun.requirePhase(SCENARIO, 1)

        ActivityWatcher().use { watcher ->
            TestEnvironment.targetContext.startActivity(
                HistoryReader.viewIntent(HistoryFixtures.ALPHA)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            hist.awaitReady()
            hist.reader.awaitPageIndex(0)

            hist.jumpToPage(9)
            hist.zoomToAbove(8, ZOOMED_IN)
            hist.jumpToPage(17)
            hist.zoomTo(16, CURRENT_PAGE_ZOOM)
            hist.awaitBackStack(listOf(0, 8))

            TestEnvironment.uiDevice.pressHome()
            watcher.awaitReached(MainActivity::class.java, Stage.STOPPED, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestDuration(DurationCategory.SHORT)
    fun phaseTwoAssertTrailRestored() {
        PhaseRun.requirePhase(SCENARIO, 2)

        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        hist.awaitReady()
        hist.reader.awaitPageIndex(16)
        val restoredCurrentZoom = hist.reader.readState().zoom
            ?: throw AssertionError("the restored reader reports no current zoom")
        assertEquals(
            "the page that was current at process death restores its own zoom",
            CURRENT_PAGE_ZOOM,
            restoredCurrentZoom,
            ZOOM_TOLERANCE,
        )

        val screen = hist.openNavigationHistory()
        val rows = screen.awaitRowCount(3)
        assertEquals("the page the app was killed on is the current one", page(17), rows[0].title)
        assertEquals("the zoomed page survives the kill", page(9), rows[1].title)
        assertEquals("the page the reading started on survives the kill", page(1), rows[2].title)
        assertTrue(
            "a restored entry keeps its origin label, read \"${rows[1].subtitle}\"",
            rows[1].subtitle.startsWith(goToPagePrefix()),
        )
        assertTrue(
            "a restored entry keeps its origin label, read \"${rows[2].subtitle}\"",
            rows[2].subtitle.startsWith(goToPagePrefix()),
        )

        screen.tapRow(page(9))
        Screens.awaitGone(NavigationHistoryActivity::class.java)

        hist.reader.awaitPageIndex(8)
        hist.reader.awaitMotionSettled()
        assertEquals(
            "a restored history entry carries no view state, so selecting it must preserve the current " +
                "restored zoom rather than applying the entry's old zoom",
            restoredCurrentZoom,
            hist.reader.readState().zoom ?: 0f,
            ZOOM_TOLERANCE,
        )
    }

    private fun page(oneBasedPage: Int) = NavigationHistoryScreen.pageLabel(oneBasedPage)

    private fun goToPagePrefix() = NavigationHistoryScreen.originPrefix(R.string.go_to_page)

    private companion object {
        const val SCENARIO = "HIST-36"
        const val ZOOMED_IN = 2.0f
        const val CURRENT_PAGE_ZOOM = 1.5f
        const val ZOOM_TOLERANCE = 0.01f
    }
}
