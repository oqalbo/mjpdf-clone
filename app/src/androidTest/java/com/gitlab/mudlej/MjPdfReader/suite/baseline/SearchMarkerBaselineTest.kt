// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchBar
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchMarkerBaselineTest : BaselineTestCase() {

    private val search = SearchRobot()

    @Test
    @TestId("VIS-39")
    fun theSearchMarkerCoversTheMatchedCharactersAndNotTheirNeighbours() {
        SettingsSeeder.seed(Preferences.searchZoomToResultKey, false)
        val file = baselineFixture(BaselineFixtures.GOLDEN_BOOK)
        launchAt(file)

        search.search(BaselineFixtures.BOOK_SEARCH_QUERY)
        SearchBar.awaitCounter()
        awaitMarkerOnPage(BaselineFixtures.BOOK_SEARCH_PAGE_INDEX)
        jumpToPageIndex(BaselineFixtures.BOOK_SEARCH_PAGE_INDEX)

        enterFullscreenAndSettle()
        assertTrue(
            "the marker must still be on page index ${BaselineFixtures.BOOK_SEARCH_PAGE_INDEX} at capture " +
                "time, the hook reads ${state().markerPages}",
            state().markerPages?.contains(BaselineFixtures.BOOK_SEARCH_PAGE_INDEX) == true,
        )

        assertCroppedScreenMatches(
            goldenName = "VIS-39-search-tlcl.png",
            region = pageRegionAboveSearchBar(),
            expectation = readerExpectation(
                "TLCL 25.12A page index ${BaselineFixtures.BOOK_SEARCH_PAGE_INDEX} with the search marker " +
                    "over ${BaselineFixtures.BOOK_SEARCH_QUERY}",
                pageIndex = BaselineFixtures.BOOK_SEARCH_PAGE_INDEX,
            ),
        )
    }

    @Test
    @TestId("VIS-41")
    fun aWordContainingALigatureIsMarkedAcrossItsWholeWidth() {
        SettingsSeeder.seed(Preferences.searchZoomToResultKey, false)
        val file = baselineFixture(BaselineFixtures.LIGATURES)
        launchAt(file)

        search.search(BaselineFixtures.LIGATURE_QUERY)
        SearchBar.awaitCounter()
        awaitMarkerOnPage(0)

        enterFullscreenAndSettle()
        assertTrue(
            "the marker must still be on page index 0 at capture time, the hook reads ${state().markerPages}",
            state().markerPages?.contains(0) == true,
        )

        assertCroppedScreenMatches(
            goldenName = "VIS-41-search-ligature.png",
            region = pageRegionAboveSearchBar(),
            expectation = readerExpectation(
                "ligatures.pdf page index 0 with the search marker over ${BaselineFixtures.LIGATURE_QUERY}",
                pageIndex = 0,
            ),
        )
    }

    @Test
    @TestId("VIS-42")
    fun closingSearchRemovesEveryMarkerFromThePage() {
        SettingsSeeder.seed(Preferences.searchZoomToResultKey, false)
        val file = baselineFixture(BaselineFixtures.GOLDEN_BOOK)
        launchAt(file)

        search.search(BaselineFixtures.BOOK_SEARCH_QUERY)
        SearchBar.awaitCounter()
        awaitMarkerOnPage(BaselineFixtures.BOOK_SEARCH_PAGE_INDEX)
        jumpToPageIndex(BaselineFixtures.BOOK_SEARCH_PAGE_INDEX)

        SearchBar.tapDone()
        SearchBar.awaitGone()
        Await.until("the search marker page set empties", Await.LONG_TIMEOUT_MS) {
            state().markerPages.isNullOrEmpty()
        }
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        enterFullscreenAndSettle()
        jumpToPageIndex(BaselineFixtures.BOOK_SEARCH_PAGE_INDEX)
        assertTrue(
            "no marker may survive at capture time, the hook reads ${state().markerPages}",
            state().markerPages.isNullOrEmpty(),
        )

        assertScreenMatches(
            goldenName = "VIS-01-tlcl-page41.png",
            expectation = readerExpectation(
                "TLCL 25.12A body page restored to its unmarked state after a search was closed",
                pageIndex = BaselineFixtures.BOOK_SEARCH_PAGE_INDEX,
            ),
        )
    }

    private fun awaitMarkerOnPage(pageIndex: Int) {
        Await.until("the search marker page set contains page index $pageIndex", Await.LONG_TIMEOUT_MS) {
            state().markerPages?.contains(pageIndex) == true
        }
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    private fun pageRegionAboveSearchBar(): Rect {
        val top = searchBarTopOnScreen()
        val width = TestEnvironment.uiDevice.displayWidth
        if (top <= 0) {
            throw ConditionTimeoutException(
                "The search bar reports a top edge of $top, so there is no page region above it to crop. " +
                    "This capture must be taken with the bar still on screen, because closing it clears the " +
                    "marker the test exists to photograph."
            )
        }
        return Rect(0, 0, width, top)
    }

    private fun searchBarTopOnScreen(): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed to read the search bar from")
        val label = activity.findViewById<View>(com.google.android.material.R.id.snackbar_text)
            ?: throw ConditionTimeoutException(
                "The search bar is not on screen. VIS-39 and VIS-41 must capture with it still open."
            )
        val bar = snackbarRootOf(label)
        val location = IntArray(2)
        bar.getLocationOnScreen(location)
        location[1]
    }

    private fun snackbarRootOf(label: View): View {
        var current: View = label
        var parent = current.parent
        while (parent is ViewGroup) {
            if (parent.javaClass.name.contains(SNACKBAR_LAYOUT_MARKER)) return parent
            current = parent
            parent = current.parent
        }
        return current
    }

    private companion object {
        const val SNACKBAR_LAYOUT_MARKER = "Snackbar"
    }
}
