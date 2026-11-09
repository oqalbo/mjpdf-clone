// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.ReaderNavigation
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SnapAndDragTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-30")
    fun aShortDragSnapsToTheRowBoundary() {
        SettingsSeeder.seed(Preferences.pageSnapKey, true)

        launchNormal().use {
            val ready = navigation.awaitReady()
            assertEquals("page snap must be applied", true as Boolean?, ready.pageSnap)
            reader.awaitPageIndex(0)
            ReaderNavigation.jumpToPage(reader, MIDDLE_PAGE)

            dragUpShort(cancelled = false)
            val settled = awaitSnapSettled()

            assertEquals("a drag shorter than half a page stays on its page", MIDDLE_PAGE as Int?, settled.page)
        }
    }

    @Test
    @TestId("NAV-31")
    fun aCancelledDragSettlesOnADeterminedPage() {
        SettingsSeeder.seed(Preferences.pageSnapKey, true)

        launchNormal().use {
            val ready = navigation.awaitReady()
            assertEquals("page snap must be applied", true as Boolean?, ready.pageSnap)
            reader.awaitPageIndex(0)
            ReaderNavigation.jumpToPage(reader, MIDDLE_PAGE)

            dragUpShort(cancelled = true)
            val settled = awaitSnapSettled()

            assertEquals("the cancelled drag travelled less than half a page", MIDDLE_PAGE as Int?, settled.page)
        }
    }

    private fun awaitSnapSettled(): ReaderState {
        reader.awaitState("the page snap animation reaches a row boundary") {
            it.isSettled && it.snapAligned == true
        }
        return reader.awaitMotionSettled()
    }

    private fun dragUpShort(cancelled: Boolean) {
        val bounds = ReaderRobot.pdfViewBounds()
        val x = bounds.width() * CENTRE
        val fromY = bounds.height() * DRAG_START_FRACTION
        val toY = bounds.height() * (DRAG_START_FRACTION - DRAG_DISTANCE_FRACTION)
        if (cancelled) {
            NavigationDirectInput.dispatchCancelledDrag(navigation.pdfView(), x, fromY, x, toY)
        } else {
            NavigationDrag.settledDrag(
                PointF(bounds.left + x, bounds.top + fromY),
                PointF(bounds.left + x, bounds.top + toY),
            )
        }
    }

    private fun launchNormal(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(NavigationFixtures.uri(NavigationFixtures.NORMAL)))

    private companion object {
        const val CENTRE = 0.5f
        const val DRAG_START_FRACTION = 0.6f
        const val DRAG_DISTANCE_FRACTION = 0.15f
        const val MIDDLE_PAGE = 10
    }
}
