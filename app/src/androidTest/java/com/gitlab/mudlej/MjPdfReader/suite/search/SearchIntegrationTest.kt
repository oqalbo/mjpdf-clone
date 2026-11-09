// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.RootDescriptionParser
import com.gitlab.mudlej.MjPdfReader.ui.history.NavigationHistoryActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchIntegrationTest : SearchTestCase() {

    @Test
    @TestId("SRCH-52")
    fun theFirstSearchJumpIsRecordedInReadingHistory() {
        openReader(SearchFixtures.TEXT) {
            robot.goToPage(ORIGIN_PAGE)
            val originIndex = ORIGIN_PAGE - 1

            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.first())

            Await.until("the reader back stack records the page the search jumped from") {
                readerBackStack().contains(originIndex)
            }

            robot.runReaderAction(R.string.navigation_history)
            Screens.awaitResumed(NavigationHistoryActivity::class.java)
            assertTrue(
                "the navigation history screen must show a back entry whose origin is the search jump",
                TestEnvironment.uiDevice.hasObject(By.textContains(robot.string(R.string.search))),
            )
            assertTrue(
                "the back entry must name the page the search jumped from",
                TestEnvironment.uiDevice.hasObject(
                    By.text(robot.string(R.string.bookmark_page_label).format(ORIGIN_PAGE))
                ),
            )
            TestEnvironment.uiDevice.pressBack()
            Screens.awaitTopIs(MainActivity::class.java)

            robot.runReaderAction(R.string.nav_back)
            awaitSettledOnPage(originIndex)
            assertEquals("navigating back must return to the page the search left", originIndex, pageIndex())
        }
    }

    @Test
    @TestId("SRCH-53")
    fun aPasswordProtectedDocumentIsFullySearchable() {
        val file = fixture(SearchFixtures.PROTECTED)
        openProtectedReader(file) {
            val hash = fileHash()
            robot.search(SearchFixtures.PROTECTED_TOKEN)
            SearchBar.awaitShown()

            val hits = SearchOracle.awaitHits(hash, SearchFixtures.PROTECTED_TOKEN)
            val counter = SearchBar.awaitCounterStable()
            assertEquals(
                "the counter total must equal the cached hit count for the protected document",
                hits.size,
                counter.total,
            )
            assertEquals(
                "the hit count for \"${SearchFixtures.PROTECTED_TOKEN}\". " +
                    "${SearchFixtures.PROTECTED_TOKEN_GROUND_TRUTH}. The hits landed on page numbers " +
                    "${SearchOracle.pageNumbersOf(hits)}",
                SearchFixtures.PROTECTED_TOKEN_HITS,
                hits.size,
            )

            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitItemCount(hits.size)
            Toasts.assertNoneWithin(SearchNotices.unreadableDocument())

            results.tapRow(0)
            Screens.awaitTopIs(MainActivity::class.java)
            robot.awaitReaderResumed()
            SearchBar.awaitCounterOf(1, hits.size)
            awaitSettledOnPage(hits.first().pageNumber - 1)
            reader.awaitState("the jumped page carries a search marker") {
                it.markerPages?.contains(hits.first().pageNumber - 1) == true
            }
        }
    }

    @Test
    @TestId("SRCH-55")
    fun incognitoIsCarriedIntoTheResultsScreen() {
        openReader(SearchFixtures.TEXT) {
            robot.runReaderAction(R.string.incognito)
            robot.awaitReaderReady()

            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)

            SearchBar.tapLabel()
            results.awaitShown()

            assertTrue("the launched intent must carry the incognito extra", results.incognitoExtra())
            assertEquals(
                "the results screen must run in its own night mode",
                AppCompatDelegate.MODE_NIGHT_YES,
                results.nightMode(),
            )
            assertEquals(
                "the results screen colorSurface must resolve to the incognito surface colour",
                ContextCompat.getColor(TestEnvironment.targetContext, R.color.incognito_surface),
                results.resolvedColorSurface(),
            )
            results.awaitItemCount(SearchFixtures.BEACON_HITS)

            results.tapRow(0)
            Screens.awaitTopIs(MainActivity::class.java)
            robot.awaitReaderResumed()
            robot.openReaderActionsDialog()
            assertTrue(
                "the reader must still be in incognito after returning from the results list",
                TestEnvironment.uiDevice.hasObject(By.desc(robot.string(R.string.incognito_exit))),
            )
            TestEnvironment.uiDevice.pressBack()
        }
    }

    private fun openProtectedReader(file: java.io.File, block: () -> Unit) {
        androidx.test.core.app.ActivityScenario
            .launch<MainActivity>(ReaderRobot.viewIntent(file))
            .use {
                unlock(SearchFixtures.PROTECTED_PASSWORD)
                robot.awaitReaderReady()
                block()
            }
    }

    private fun unlock(password: String) {
        val field = Await.untilNotNull(
            "the password prompt offers its passwordInput field, which is a different id from the search " +
                "dialog's editText because password_dialog.xml is its own layout"
        ) {
            TestEnvironment.uiDevice.findObject(
                By.res(TestEnvironment.targetPackage, PASSWORD_INPUT_ID)
            )
        }
        field.text = password
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        TestEnvironment.uiDevice.waitForIdle()
    }

    private fun readerBackStack(): List<Int> {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) ?: return emptyList()
        val description = TestEnvironment.onMainThread {
            activity.findViewById<View>(R.id.mainLayout)?.contentDescription?.toString()
        }?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val raw = RootDescriptionParser.parse(description).value(HISTORY_GROUP, HISTORY_BACK) ?: return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
    }

    companion object {
        const val ORIGIN_PAGE = 2
        const val PASSWORD_INPUT_ID = "passwordInput"
        const val HISTORY_GROUP = "hist"
        const val HISTORY_BACK = "back"
    }
}
