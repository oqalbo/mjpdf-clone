// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.TapToTurnZones
import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class TapToTurnTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-09")
    fun theRightZoneTurnsForwardInSinglePageMode() {
        seedSinglePage()

        launch(NavigationFixtures.NORMAL).use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)
            assertZoomIsOne()

            navigation.tapAtFraction(RIGHT_ZONE, NavigationRobot.CENTRE)
            reader.awaitPageIndex(1)
            reader.awaitMotionSettled()

            assertEquals("the right zone tap turns forward", 1 as Int?, reader.readState().page)
            val hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, 1)
            navigation.assertScrollHandleNotVisible()
        }
    }

    @Test
    @TestId("NAV-10")
    fun theLeftZoneTurnsBackInSinglePageMode() {
        seedSinglePage()

        launch(NavigationFixtures.NORMAL).use {
            navigation.awaitReady()
            navigation.jumpToPage(3)
            reader.awaitPageIndex(2)

            navigation.tapAtFraction(LEFT_ZONE, NavigationRobot.CENTRE)
            reader.awaitPageIndex(1)
            reader.awaitMotionSettled()

            assertEquals("the left zone tap turns back", 1 as Int?, reader.readState().page)
            val hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, 1)
        }
    }

    @Test
    @TestId("NAV-11")
    fun theCentreTapTogglesTheChromeInsteadOfTurningThePage() {
        seedSinglePage()
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)

        launch(NavigationFixtures.NORMAL).use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            navigation.enterFullscreen()
            navigation.tapAtFraction(NavigationRobot.CENTRE, NavigationRobot.CENTRE)

            navigation.awaitExitFullscreenButtonVisible()
            assertEquals("the centre tap must not turn the page", 0 as Int?, reader.readState().page)
            assertEquals(
                "the exit fullscreen button must be visible after the centre tap",
                View.VISIBLE as Int?,
                navigation.viewVisibility(R.id.exitFullScreenButton),
            )
        }
    }

    @Test
    @TestId("NAV-12")
    fun theTopAndBottomZonesPageVertically() {
        seedSinglePage()
        SettingsSeeder.seed(Preferences.tapToTurnKey, TapToTurnZones.TOP_BOTTOM.name)

        launch(NavigationFixtures.NORMAL).use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            navigation.tapAtFraction(NavigationRobot.CENTRE, BOTTOM_ZONE)
            reader.awaitPageIndex(1)
            reader.awaitMotionSettled()
            assertEquals("the bottom zone tap turns forward", 1 as Int?, reader.readState().page)

            navigation.tapAtFraction(NavigationRobot.CENTRE, TOP_ZONE)
            reader.awaitPageIndex(0)
            reader.awaitMotionSettled()
            assertEquals("the top zone tap turns back", 0 as Int?, reader.readState().page)

            navigation.tapAtFraction(NavigationRobot.CENTRE, NavigationRobot.CENTRE)
            reader.awaitMotionSettled()
            assertEquals("the centre stays a dead zone", 0 as Int?, reader.readState().page)
        }
    }

    @Test
    @TestId("NAV-13")
    @TestDuration(DurationCategory.NORMAL)
    fun turningTapToTurnOffDisablesEveryZone() {
        seedSinglePage()
        SettingsSeeder.seed(Preferences.tapToTurnKey, TapToTurnZones.OFF.name)

        launch(NavigationFixtures.NORMAL).use {
            navigation.awaitReady()
            navigation.jumpToPage(3)
            assertZoomIsOne()
            val hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, 2)
            val zones = listOf(
                RIGHT_ZONE to NavigationRobot.CENTRE,
                LEFT_ZONE to NavigationRobot.CENTRE,
                NavigationRobot.CENTRE to TOP_ZONE,
                NavigationRobot.CENTRE to BOTTOM_ZONE,
            )
            for ((x, y) in zones) {
                navigation.tapAtFraction(x, y)
                Await.assertThroughout(
                    "tap zone ($x, $y) leaves page index 2 unchanged while tap-to-turn is off",
                    windowMs = NavigationRecords.STABLE_ABSENCE_MS,
                ) { reader.readState().page == 2 }
            }
            NavigationRecords.assertStoredPageStays(hash, 2)
        }
    }

    @Test
    @TestId("NAV-14")
    fun theTapZonesAreInertOutsideSinglePageMode() {
        launch(NavigationFixtures.NORMAL).use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)
            val before = reader.readState().positionOffset

            navigation.tapAtFraction(RIGHT_ZONE, NavigationRobot.CENTRE)
            reader.awaitMotionSettled()

            val after = reader.readState()
            assertEquals("continuous mode must not page on a right side tap", 0 as Int?, after.page)
            assertEquals(
                "the scroll position must not move",
                before ?: -1f,
                after.positionOffset ?: -2f,
                POSITION_TOLERANCE,
            )
        }
    }

    @Test
    @TestId("NAV-16")
    fun aLinkInsideTheTurnZoneWinsOverThePageTurn() {
        seedSinglePage()

        launch(NavigationFixtures.EDGE_LINK).use {
            val ready = navigation.awaitReady()
            reader.awaitPageIndex(NavigationFixtures.EDGE_LINK_SOURCE_PAGE)
            assertEquals(
                "the page count of edge-link.pdf",
                NavigationFixtures.EDGE_LINK_PAGES as Int?,
                ready.pageCount,
            )

            val bounds = ReaderRobot.pdfViewBounds()
            val tapPoint = navigation.pointAtPageFraction(
                NavigationFixtures.EDGE_LINK_CENTRE_X_FRACTION,
                NavigationFixtures.EDGE_LINK_CENTRE_Y_FRACTION,
            )
            assertTrue(
                "the link centre must sit inside the right quarter of the view, tapped x ${tapPoint.x} " +
                    "in view ${bounds.left} to ${bounds.right}",
                tapPoint.x >= bounds.left + bounds.width() * RIGHT_ZONE_BOUNDARY,
            )

            navigation.tapAtPageFraction(
                NavigationFixtures.EDGE_LINK_CENTRE_X_FRACTION,
                NavigationFixtures.EDGE_LINK_CENTRE_Y_FRACTION,
            )
            reader.awaitPageIndex(NavigationFixtures.EDGE_LINK_DESTINATION_PAGE)
            reader.awaitMotionSettled()

            assertEquals(
                "the link destination wins over the turn zone",
                NavigationFixtures.EDGE_LINK_DESTINATION_PAGE as Int?,
                reader.readState().page,
            )
            Snackbars.awaitText(
                TestEnvironment.targetContext.getString(
                    R.string.back_to_page,
                    NavigationFixtures.EDGE_LINK_SOURCE_PAGE + 1,
                )
            )
        }
    }

    @Test
    @TestId("NAV-17")
    fun aRightToLeftDocumentReversesTheTapZones() {
        seedSinglePage()

        launch(NavigationFixtures.ARABIC_RTL).use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)
            reader.awaitState("the reader reports a right to left reading direction") {
                it.readingDirectionRtl == true
            }

            navigation.tapAtFraction(LEFT_ZONE, NavigationRobot.CENTRE)
            reader.awaitPageIndex(1)
            reader.awaitMotionSettled()
            assertEquals("the left zone moves forward in a right to left book", 1 as Int?, reader.readState().page)

            navigation.tapAtFraction(RIGHT_ZONE, NavigationRobot.CENTRE)
            reader.awaitPageIndex(0)
            reader.awaitMotionSettled()
            assertEquals("the right zone moves back in a right to left book", 0 as Int?, reader.readState().page)

            val hash = reader.awaitFileHashResolved()
            val record = Await.untilValue(
                "the record records a detected reading direction",
                read = { NavigationRecords.byHash(hash) },
            ) { it?.detectedReadingDirection == ReadingDirection.RIGHT_TO_LEFT.id }!!
            assertNull("no reading direction override may be stored", record.readingDirectionOverride)
        }
    }

    private fun assertZoomIsOne() {
        val zoom = reader.readState().zoom ?: throw AssertionError("The reader reports no zoom")
        assertEquals("the tap zones are only live at zoom one", 1.0f, zoom, ZOOM_TOLERANCE)
    }

    private fun seedSinglePage() {
        SettingsSeeder.seedReadingMode(singlePage = true, horizontalScroll = false, dualPage = false)
    }

    private fun launch(fixture: String): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(uriOf(fixture)))

    private fun uriOf(fixture: String): Uri = NavigationFixtures.uri(fixture)

    private companion object {
        const val LEFT_ZONE = 0.1f
        const val RIGHT_ZONE = 0.9f
        const val RIGHT_ZONE_BOUNDARY = 0.75f
        const val TOP_ZONE = 0.1f
        const val BOTTOM_ZONE = 0.9f
        const val POSITION_TOLERANCE = 0.001f
        const val ZOOM_TOLERANCE = 0.001f
        const val LONG_HIDE_DELAY_MS = 120_000
    }
}
