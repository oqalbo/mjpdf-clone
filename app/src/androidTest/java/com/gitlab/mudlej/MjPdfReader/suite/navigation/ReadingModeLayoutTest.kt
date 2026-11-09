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
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReadingModeLayoutTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-24")
    @TestDuration(DurationCategory.NORMAL)
    fun horizontalScrollModePagesSideways() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = true, dualPage = false)

        launchNormal().use {
            navigation.awaitReady()
            reader.awaitState("the reader reports a horizontal layout") { it.swipeVertical == false }
            reader.awaitPageIndex(0)

            navigation.swipeWithinPage(0.8f, 0.5f, 0.2f, 0.5f)
            reader.awaitPageIndex(1)
            assertEquals("a right to left swipe turns forward", 1 as Int?, reader.readState().page)

            navigation.swipeWithinPage(0.2f, 0.5f, 0.8f, 0.5f)
            reader.awaitPageIndex(0)
            assertEquals("a left to right swipe turns back", 0 as Int?, reader.readState().page)

            navigation.swipeForwardWithinPage()
            reader.awaitMotionSettled()

            val settled = reader.readState()
            assertEquals("a vertical swipe must not turn a page", 0 as Int?, settled.page)
            assertEquals("the layout stays horizontal", false as Boolean?, settled.swipeVertical)
        }
    }

    @Test
    @TestId("NAV-25")
    fun dualPageModePutsTwoPagesInARowAndStepsByRow() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = false, dualPage = true)
        SettingsSeeder.seed(Preferences.turnPageByVolumeButtonsKey, true)

        launchNormal().use {
            val ready = navigation.awaitReady()

            assertEquals("dual page mode resolves to two pages per row", 2 as Int?, ready.pagesPerRow)
            assertEquals("dual page mode stays vertical", true as Boolean?, ready.swipeVertical)
            assertEquals("the first row starts at page one", 0 as Int?, ready.rowFirstPage)
            assertEquals("the first row ends at page two", 1 as Int?, ready.rowLastPage)

            pressVolumeDown()
            reader.awaitPageIndex(2)
            reader.awaitMotionSettled()

            val stepped = reader.readState()
            assertEquals("one step moves by a whole row", 2 as Int?, stepped.page)
            assertEquals("the second row starts at page three", 2 as Int?, stepped.rowFirstPage)
            assertEquals("the second row ends at page four", 3 as Int?, stepped.rowLastPage)
        }
    }

    @Test
    @TestId("NAV-26")
    fun firstPageAloneShiftsThePairingInDualPageMode() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = false, dualPage = true)
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.dualPageFirstPageAloneKey to true,
                Preferences.turnPageByVolumeButtonsKey to true,
            )
        )

        launchNormal().use {
            val ready = navigation.awaitReady()

            assertEquals("first page alone is applied", true as Boolean?, ready.firstPageAlone)
            assertEquals("the cover row starts at page one", 0 as Int?, ready.rowFirstPage)
            assertEquals("the cover row ends at page one", 0 as Int?, ready.rowLastPage)

            pressVolumeDown()
            reader.awaitPageIndex(1)
            reader.awaitMotionSettled()

            val stepped = reader.readState()
            assertEquals("the step lands on page two", 1 as Int?, stepped.page)
            assertEquals("the second row starts at page two", 1 as Int?, stepped.rowFirstPage)
            assertEquals("the second row ends at page three", 2 as Int?, stepped.rowLastPage)
        }
    }

    @Test
    @TestId("NAV-29")
    fun browserScrollModeTurnsOffSnapAndFling() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.browserScrollModeKey to true,
                Preferences.pageSnapKey to true,
                Preferences.pageFlingKey to true,
            )
        )

        launchNormal().use {
            val ready = navigation.awaitReady()

            assertEquals("browser scrolling overrides page snap", false as Boolean?, ready.pageSnap)
            assertEquals("browser scrolling overrides page fling", false as Boolean?, ready.pageFling)
            assertEquals("browser scrolling resolves to free scroll", true as Boolean?, ready.freeScroll)

            val bounds = ReaderRobot.pdfViewBounds()
            NavigationDrag.settledDrag(
                PointF(
                    bounds.left + bounds.width() * NavigationRobot.CENTRE,
                    bounds.top + bounds.height() * DRAG_START_FRACTION,
                ),
                PointF(
                    bounds.left + bounds.width() * NavigationRobot.CENTRE,
                    bounds.top + bounds.height() * (DRAG_START_FRACTION - DRAG_DISTANCE_FRACTION),
                ),
            )
            reader.awaitMotionSettled()

            val settled = reader.readState()
            val offset = settled.positionOffset ?: throw AssertionError("The reader reports no position offset")
            val delta = settled.snapDelta ?: throw AssertionError("The reader reports no snap delta")

            assertTrue("the free scroll drag must leave the top of the document, read $offset", offset > 0f)
            assertTrue(
                "the view must not have settled on a row boundary, snap delta read $delta",
                abs(delta) > BOUNDARY_TOLERANCE_PX,
            )
            assertEquals("nothing may report a snapped alignment", false as Boolean?, settled.snapAligned)
        }
    }

    private fun pressVolumeDown() {
        NavigationKeys.pressVolumeDown()
    }

    private fun launchNormal(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(NavigationFixtures.uri(NavigationFixtures.NORMAL)))

    private companion object {
        const val DRAG_START_FRACTION = 0.7f
        const val DRAG_DISTANCE_FRACTION = 0.2f
        const val BOUNDARY_TOLERANCE_PX = 1f
    }
}
