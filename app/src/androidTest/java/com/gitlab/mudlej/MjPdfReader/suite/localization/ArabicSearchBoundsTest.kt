// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchBar
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchOracle
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ArabicSearchBoundsTest : LocaleTestCase() {

    private val search = SearchRobot(reader)

    @After
    fun releaseSearchState() {
        runCatching { SearchIdle.clear() }
    }

    @Test
    @TestId("LOC-38")
    fun arabicSearchHitBoxesLandOnTheRightLettersAcrossALigature() {
        openLigatureFixture {
            val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)
            val hitPage = runSearch(fileHash, LocaleFixtures.LIGATURE_SEARCH_TERM, ignoreAccents = false)

            val bounds = SearchHighlight.awaitRectsOnPage(hitPage)
            assertTrue(
                "NormalizedTextMapper.toRawRange must map the normalized match back onto the raw text " +
                    "across the lam alef ligature, which is one raw character but two normalized ones. " +
                    "The hook reported \"${bounds.raw}\", so no rectangle reached the page.",
                bounds.count >= 1,
            )
            assertEquals(
                "the fixture writes the term once on one line, so exactly one run rectangle is expected. " +
                    "A different count means the mapped range covered the wrong characters. The hook " +
                    "reported \"${bounds.raw}\".",
                1,
                bounds.rects.size,
            )
            val rect = bounds.rects.first()
            val observed = describePageRect(rect)
            val expected = "left ${LocaleFixtures.LIGATURE_TERM_LEFT_POINTS}, " +
                "right ${LocaleFixtures.LIGATURE_TERM_RIGHT_POINTS}, " +
                "bottom ${LocaleFixtures.LIGATURE_TERM_BOTTOM_POINTS}, " +
                "top ${LocaleFixtures.LIGATURE_TERM_TOP_POINTS}"

            assertTrue(
                "the highlight run must start where the fixture drew the word. This is the whole point of " +
                    "the test: a range mapped onto a neighbouring word of similar length still returns one " +
                    "rectangle of word like proportions, so only the position catches it. One character of " +
                    "this term is about $CHARACTER_WIDTH_POINTS points wide and the tolerance is " +
                    "$HORIZONTAL_TOLERANCE_POINTS, so a shift of a single character fails. " +
                    "Observed $observed against expected $expected.",
                horizontallyWithin(rect.left, LocaleFixtures.LIGATURE_TERM_LEFT_POINTS),
            )
            assertTrue(
                "the highlight run must end where the fixture drew the word. Observed $observed against " +
                    "expected $expected.",
                horizontallyWithin(rect.right, LocaleFixtures.LIGATURE_TERM_RIGHT_POINTS),
            )
            assertTrue(
                "the highlight run must sit on the line the fixture drew the word on. The bands are " +
                    "compared rather than the edges because pdfium reports a glyph box and the fixture " +
                    "records an ascent to descent box, which differ by a metric rather than by a position. " +
                    "Observed $observed against expected $expected.",
                verticallyOverlapsTerm(rect),
            )
            assertTrue(
                "the run must be a word rather than a sliver, and it must not have swallowed its " +
                    "neighbours. Observed $observed against expected $expected.",
                pageWidthOf(rect) in MINIMUM_RUN_WIDTH_POINTS..MAXIMUM_RUN_WIDTH_POINTS,
            )

            assertNoHighlightFailureNotice()
            val markers = reader.readState().markerPages
            assertTrue(
                "createHighlightText only records a marker page when it produced rectangles, so the hit " +
                    "page must be in the marker set. It held $markers.",
                markers?.contains(hitPage) == true,
            )
        }
    }

    @Test
    @TestId("LOC-39")
    fun arabicSearchHitBoxesAreCorrectAroundCombiningHarakat() {
        SettingsSeeder.seed(Preferences.searchIgnoreAccentsKey, true)

        openLigatureFixture {
            val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)

            val termPage = runSearch(fileHash, LocaleFixtures.LIGATURE_SEARCH_TERM, ignoreAccents = true)
            val termBounds = SearchHighlight.awaitRectsOnPage(termPage)
            val termWidth = termBounds.widest!!.width()
            dismissSearchBar()

            val harakatPage = runSearch(fileHash, LocaleFixtures.HARAKAT_QUERY, ignoreAccents = true)
            val harakatBounds = SearchHighlight.awaitRectsOnPage(harakatPage)

            assertTrue(
                "NormalizedTextMapper.chunkEnd groups a base character with its following combining marks, " +
                    "so the unvocalised query must still highlight the vocalised word. The hook reported " +
                    "\"${harakatBounds.raw}\".",
                harakatBounds.count >= 1,
            )
            assertEquals(
                "the fixture writes the harakat bearing word once on one line, so exactly one run " +
                    "rectangle is expected. The hook reported \"${harakatBounds.raw}\".",
                1,
                harakatBounds.rects.size,
            )

            val harakatWidth = harakatBounds.widest!!.width()
            val rect = harakatBounds.rects.single()
            assertEquals(
                "the harakat highlight starts at the fixture word",
                LocaleFixtures.HARAKAT_WORD_LEFT_EDGE_POINTS, rect.left.toFloat(), HORIZONTAL_TOLERANCE_POINTS.toFloat(),
            )
            assertEquals(
                "the harakat highlight ends at the fixture word",
                LocaleFixtures.HARAKAT_WORD_RIGHT_EDGE_POINTS, rect.right.toFloat(), HORIZONTAL_TOLERANCE_POINTS.toFloat(),
            )
            assertTrue(
                "the harakat highlight stays within the fixture's line band: $rect",
                pageBottomOf(rect) >= LocaleFixtures.HARAKAT_WORD_BOTTOM_POINTS - HORIZONTAL_TOLERANCE_POINTS &&
                    pageTopOf(rect) <= LocaleFixtures.HARAKAT_WORD_TOP_POINTS + HORIZONTAL_TOLERANCE_POINTS &&
                    pageTopOf(rect) > LocaleFixtures.HARAKAT_WORD_BOTTOM_POINTS &&
                    pageBottomOf(rect) < LocaleFixtures.HARAKAT_WORD_TOP_POINTS,
            )
            assertTrue(
                "the fixture records the harakat word occupying ${HARAKAT_WIDTH_POINTS} points against " +
                    "${TERM_WIDTH_POINTS} points for the search term, and createHighlightText pads both " +
                    "runs by the same amount, so the harakat run must come back wider. It measured " +
                    "$harakatWidth against $termWidth.",
                harakatWidth > termWidth,
            )

            assertNoHighlightFailureNotice()
        }
    }

    private fun openLigatureFixture(body: () -> Unit) {
        val file = fixture(LocaleFixtures.ARABIC_LIGATURE)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            awaitReaderReady()
            body()
        }
    }

    private fun runSearch(fileHash: String, query: String, ignoreAccents: Boolean): Int {
        search.search(query)
        SearchBar.awaitShown()
        SearchIdle.awaitFinished()
        val hits = SearchOracle.awaitHits(fileHash, query, ignoreAccents)
        assertTrue("the fixture must contain \"$query\", but the search found no hit", hits.isNotEmpty())
        val page = SearchOracle.pageIndexesOf(hits).first()
        reader.awaitPageIndex(page, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)
        return page
    }

    private fun dismissSearchBar() {
        SearchBar.tapDone()
        SearchBar.awaitGone()
    }

    private fun assertNoHighlightFailureNotice() {
        val failure = SearchBar.failedToHighlightLabel()
        Await.assertThroughout(
            condition = "the search snackbar label never reads \"$failure\"",
            windowMs = FAILURE_WINDOW_MS,
        ) { SearchBar.labelOrNull() != failure }
    }

    private fun pageWidthOf(rect: Rect): Int = rect.right - rect.left

    private fun pageBottomOf(rect: Rect): Int = minOf(rect.top, rect.bottom)

    private fun pageTopOf(rect: Rect): Int = maxOf(rect.top, rect.bottom)

    private fun describePageRect(rect: Rect): String =
        "left ${rect.left}, right ${rect.right}, bottom ${pageBottomOf(rect)}, top ${pageTopOf(rect)}, " +
            "width ${pageWidthOf(rect)}, raw $rect"

    private fun horizontallyWithin(observed: Int, expected: Int): Boolean =
        kotlin.math.abs(observed - expected) <= HORIZONTAL_TOLERANCE_POINTS

    private fun verticallyOverlapsTerm(rect: Rect): Boolean =
        pageBottomOf(rect) < LocaleFixtures.LIGATURE_TERM_TOP_POINTS &&
            pageTopOf(rect) > LocaleFixtures.LIGATURE_TERM_BOTTOM_POINTS

    private companion object {
        const val FAILURE_WINDOW_MS = 1_500L
        const val TERM_WIDTH_POINTS = 36.97f
        const val HARAKAT_WIDTH_POINTS = 48.29f
        const val HORIZONTAL_TOLERANCE_POINTS = 4
        const val CHARACTER_WIDTH_POINTS = 9
        const val MINIMUM_RUN_WIDTH_POINTS = 28
        const val MAXIMUM_RUN_WIDTH_POINTS = 46
    }
}
