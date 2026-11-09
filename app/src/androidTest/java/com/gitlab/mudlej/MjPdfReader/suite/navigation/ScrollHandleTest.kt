// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
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
class ScrollHandleTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-33")
    fun draggingTheScrollHandleMovesThroughTheDocument() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.pageSnapKey to true,
                Preferences.hideDelayKey to LONG_HIDE_DELAY_MS,
            )
        )

        launch(NavigationFixtures.LARGE).use {
            val ready = navigation.awaitReady()
            val pageCount = ready.pageCount ?: throw AssertionError("The reader reports no page count")
            assertEquals("the page count of large.pdf", NavigationFixtures.LARGE_PAGES, pageCount)

            navigation.showReaderChrome()
            navigation.awaitScrollHandleVisible()

            navigation.dragScrollHandleToFraction(MIDDLE)
            reader.awaitMotionSettled()

            val settled = reader.readState()
            val page = settled.page ?: throw AssertionError("The reader reports no page index")
            val offset = settled.positionOffset ?: throw AssertionError("The reader reports no position offset")

            assertTrue("the handle drag must leave the first page, read $page", page > 0)
            assertTrue("the handle drag must not reach the last page, read $page", page < pageCount - 1)
            assertTrue(
                "the drag to the middle of the track must land near the middle, read $offset",
                offset in MIDDLE_LOWER..MIDDLE_UPPER,
            )
            assertEquals("the release must snap to a row boundary", true as Boolean?, settled.snapAligned)

            val hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, page)
        }
    }

    @Test
    @TestId("NAV-34")
    fun tappingTheScrollHandleOpensGoToPage() {
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)

        launch(NavigationFixtures.NORMAL).use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            navigation.showReaderChrome()
            navigation.awaitScrollHandleVisible()

            navigation.tapScrollHandle()
            navigation.awaitGoToPageDialog()

            onView(withText(string(R.string.go_to_page))).inRoot(isDialog()).check(matches(isDisplayed()))

            navigation.cancelGoToPageDialog()
            reader.awaitMotionSettled()
            assertEquals("a handle tap must never move the reader by itself", 0 as Int?, reader.readState().page)
        }
    }

    @Test
    @TestId("NAV-35")
    fun theScrollHandleStaysHiddenOnAOnePageDocument() {
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)

        launch(NavigationFixtures.SINGLE_PAGE).use {
            val ready = navigation.awaitReady()
            assertEquals("the page count of single-page.pdf", NavigationFixtures.SINGLE_PAGE_PAGES as Int?, ready.pageCount)
            navigation.assertScrollHandleNotVisible()

            navigation.enterFullscreen()
            navigation.assertScrollHandleNotVisible()

            navigation.tapAtFraction(NavigationRobot.CENTRE, NavigationRobot.CENTRE)
            navigation.awaitExitFullscreenButtonVisible()

            assertEquals(
                "the overlay must have opened",
                View.VISIBLE as Int?,
                navigation.viewVisibility(R.id.exitFullScreenButton),
            )
            navigation.assertScrollHandleNotVisible()
        }
    }

    @Test
    @TestId("NAV-36")
    @TestDuration(DurationCategory.NORMAL)
    fun theScrollHandleLabelCanShowThePageCount() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.showScrollHandlePageCountKey to true,
                Preferences.hideDelayKey to LONG_HIDE_DELAY_MS,
            )
        )

        launch(NavigationFixtures.LARGE).use {
            navigation.awaitReady()
            navigation.jumpToPage(7)
            reader.awaitPageIndex(6)

            navigation.showReaderChrome()
            navigation.awaitScrollHandleVisible()

            awaitHandleLabel("7/${NavigationFixtures.LARGE_PAGES}")
        }

        SettingsSeeder.seed(Preferences.showScrollHandlePageCountKey, false)

        launch(NavigationFixtures.LARGE).use {
            navigation.awaitReady()
            navigation.jumpToPage(7)
            reader.awaitPageIndex(6)

            navigation.showReaderChrome()
            navigation.awaitScrollHandleVisible()

            awaitHandleLabel("7")
        }
    }

    private fun awaitHandleLabel(expected: String) {
        Await.untilValue(
            "the scroll handle label reads \"$expected\"",
            read = { navigation.scrollHandleLabel() },
        ) { it == expected }
    }

    private fun launch(fixture: String): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(NavigationFixtures.uri(fixture)))

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val MIDDLE = 0.5f
        const val MIDDLE_LOWER = 0.4f
        const val MIDDLE_UPPER = 0.6f
        const val LONG_HIDE_DELAY_MS = 120_000
    }
}
