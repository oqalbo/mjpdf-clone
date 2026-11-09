// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.UiAutomation
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
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

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReaderPagingTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-01")
    fun aDocumentNeverOpenedBeforeStartsOnItsFirstPageAndKnowsItsLength() {
        launchNormal().use {
            val state = navigation.awaitReady()

            assertEquals("the opening page index", 0 as Int?, state.page)
            assertEquals("the page count of normal.pdf", NavigationFixtures.NORMAL_PAGES as Int?, state.pageCount)

            val hash = reader.awaitFileHashResolved()
            val record = NavigationRecords.awaitRowExists(hash)
            assertEquals("the stored page index", 0, record.pageNumber)
            assertEquals("the stored page count", NavigationFixtures.NORMAL_PAGES, record.length)
        }
    }

    @Test
    @TestId("NAV-02")
    @TestDuration(DurationCategory.NORMAL)
    fun swipingForwardAdvancesThePageInContinuousMode() {
        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)
            val pageCountBefore = reader.readState().pageCount

            val moved = navigation.swipeUntilPageChanges(forward = true)

            assertTrue("the page index must advance, read: $moved", (moved.page ?: 0) > 0)
            assertEquals("one page of travel lands on the next page", 1 as Int?, moved.page)
            assertEquals("the page count must not change", pageCountBefore, moved.pageCount)
        }
    }

    @Test
    @TestId("NAV-03")
    @TestDuration(DurationCategory.NORMAL)
    fun swipingBackReturnsToThePreviousPage() {
        launchNormal().use {
            navigation.awaitReady()
            navigation.jumpToPage(3)
            reader.awaitPageIndex(2)

            val moved = navigation.swipeUntilPageChanges(forward = false)

            assertEquals("swiping back lands on the previous page", 1 as Int?, moved.page)
            val hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, 1)
        }
    }

    @Test
    @TestId("NAV-04")
    @TestDuration(DurationCategory.LONG)
    fun theFirstPageBoundaryHolds() {
        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            repeat(BOUNDARY_SWIPES) { navigation.swipeBackwardWithinPage() }
            val settled = reader.awaitMotionSettled()

            assertEquals("the reader stays on the first page", 0 as Int?, settled.page)
            assertEquals(
                "the document scroll ratio stays at the top",
                0.0f,
                settled.positionOffset ?: -1f,
                POSITION_TOLERANCE,
            )
            val hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, 0)
        }
    }

    @Test
    @TestId("NAV-05")
    @TestDuration(DurationCategory.LONG)
    fun theLastPageBoundaryHolds() {
        launchNormal().use {
            val ready = navigation.awaitReady()
            val pageCount = ready.pageCount ?: throw AssertionError("The reader reports no page count")

            navigation.jumpToPage(pageCount)
            reader.awaitPageIndex(pageCount - 1)

            repeat(BOUNDARY_SWIPES) { navigation.swipeForwardWithinPage() }
            val settled = reader.awaitMotionSettled()

            assertEquals("the reader stays on the last page", (pageCount - 1) as Int?, settled.page)
            assertEquals(
                "the document scroll ratio stays at the bottom",
                1.0f,
                settled.positionOffset ?: -1f,
                POSITION_TOLERANCE,
            )
            val hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, pageCount - 1)
        }
    }

    @Test
    @TestId("NAV-06")
    fun theTitleBarPageCounterTracksTheCurrentPage() {
        SettingsSeeder.seed(Preferences.showAppBarPageCountKey, true)

        launchNormal().use {
            navigation.awaitReady()

            navigation.awaitPageCounter("[1/${NavigationFixtures.NORMAL_PAGES}]")
            assertEquals(
                "the page counter must be visible when the setting is on",
                View.VISIBLE as Int?,
                navigation.pageCounterVisibility(),
            )

            navigation.jumpToPage(7)

            navigation.awaitPageCounter("[7/${NavigationFixtures.NORMAL_PAGES}]")
            assertEquals(
                "the page counter must stay visible after the jump",
                View.VISIBLE as Int?,
                navigation.pageCounterVisibility(),
            )
        }
    }

    @Test
    @TestId("NAV-07")
    fun aPageChangeWritesThePageNumberToTheDatabaseRow() {
        launchNormal().use {
            navigation.awaitReady()

            navigation.jumpToPage(5)
            reader.awaitPageIndex(4)

            val hash = reader.awaitFileHashResolved()
            val record = NavigationRecords.awaitStoredPage(hash, 4)

            assertEquals("the record is keyed on the hash the reader resolved", hash, record.hash)
            assertEquals("exactly one PdfRecord row must exist", 1, NavigationRecords.rowCount())
        }
    }

    @Test
    @TestId("NAV-08")
    fun aPageChangeIsAnnouncedForAccessibility() {
        launchNormal().use {
            navigation.awaitReady()

            val expected = TestEnvironment.targetContext
                .getString(R.string.page_x_of_y, 4, NavigationFixtures.NORMAL_PAGES)
            val automation = TestEnvironment.instrumentation.uiAutomation
            val announcement = automation.executeAndWaitForEvent(
                Runnable { navigation.jumpToPage(4) },
                UiAutomation.AccessibilityEventFilter { event ->
                    event.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT &&
                        event.packageName?.toString() == TestEnvironment.targetPackage &&
                        event.text.singleOrNull()?.toString() == expected
                },
                Await.DEFAULT_TIMEOUT_MS,
            )
            try {
                val collected = announcement.text.map { it.toString() }
                assertEquals("the reader lands on page index 3", 3 as Int?, reader.awaitPageIndex(3).page)
                assertEquals(
                    "the announcement carries exactly the localized page text",
                    listOf(expected),
                    collected,
                )
                assertTrue(
                    "no announcement may carry an unformatted placeholder, collected: $collected",
                    collected.none { it.contains(UNFORMATTED_PLACEHOLDER) },
                )
            } finally {
                recycleAccessibilityEvent(announcement)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun recycleAccessibilityEvent(event: AccessibilityEvent) {
        event.recycle()
    }

    private fun launchNormal(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(NavigationFixtures.uri(NavigationFixtures.NORMAL)))

    private companion object {
        const val BOUNDARY_SWIPES = 5
        const val POSITION_TOLERANCE = 0.001f
        const val UNFORMATTED_PLACEHOLDER = "%1\$d"
    }
}
