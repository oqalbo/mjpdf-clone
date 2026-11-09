// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.JumpBackSnackbars
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
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
class LinksTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val links = LinksRobot()

    @Test
    @TestId("BMK-33")
    fun theLinksListShowsEveryExternalLinkWithItsPage() {
        openLinks(BookmarksFixtures.LINKS) {
            links.awaitRowCount(BookmarksFixtures.LINKS_LISTED_ROWS)

            assertEquals(
                "every link carrying a uri is listed, destination only links are not",
                BookmarksFixtures.LINKS_ALL_URLS,
                links.rowUrls(),
            )
            assertEquals(
                "each row shows the one based page the link sits on",
                BookmarksFixtures.LINKS_ALL_PAGE_NUMBERS.map { it.toString() },
                (0 until links.rowCount()).map { position ->
                    links.scrollTo(position)
                    links.rowPageNumber(position).orEmpty()
                },
            )
            links.awaitTitle(links.titleFor(BookmarksFixtures.LINKS_LISTED_ROWS))
            assertTrue("the search entry point appears once links are loaded", links.isSearchShown())
        }
    }

    @Test
    @TestId("BMK-34")
    fun tappingALinksPageNumberReturnsToThatPage() {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.LINKS)).use {
            reader.awaitReady()
            Intents.init()
            try {
                links.openFromReader()
                val position = links.positionOfUrl(BookmarksFixtures.LINKS_EXTERNAL_URL)
                assertEquals(
                    "the row for the link on page index ${BookmarksFixtures.LINKS_EXTERNAL_PAGE_INDEX}",
                    BookmarksFixtures.LINKS_EXTERNAL_ROW_PAGE_NUMBER.toString(),
                    links.rowPageNumber(position),
                )

                links.tapPageNumber(position)

                links.awaitClosed()
                reader.awaitShown()
                reader.awaitPageIndex(BookmarksFixtures.LINKS_EXTERNAL_PAGE_INDEX)
                assertTrue(
                    "no browser or chooser may be launched by a page number tap, the recorded intents were " +
                        Intents.getIntents().map { it.action },
                    Intents.getIntents().none { intent ->
                        intent.action == Intent.ACTION_VIEW && intent.data?.scheme?.startsWith("http") == true
                    },
                )
            } finally {
                Intents.release()
            }
        }
    }

    @Test
    @TestId("BMK-35")
    fun tappingALinkDispatchesItsExactUrl() {
        openLinks(BookmarksFixtures.LINKS) {
            Intents.init()
            try {
                IntentStubs.stubEveryOutgoingIntent()
                val position = links.positionOfUrl(BookmarksFixtures.LINKS_BROWSER_URL)
                links.tapUrl(position)
                val outgoing = Await.untilValue(
                    "one external link intent is dispatched",
                    read = { Intents.getIntents().filterNot { isInternal().matches(it) } },
                ) { it.isNotEmpty() }
                assertEquals("one link tap dispatches one intent", 1, outgoing.size)
                assertEquals(Intent.ACTION_VIEW, outgoing.single().action)
                assertEquals(BookmarksFixtures.LINKS_BROWSER_URL, outgoing.single().dataString)
                links.awaitShown()
                links.awaitRowCount(BookmarksFixtures.LINKS_LISTED_ROWS)
            } finally {
                Intents.release()
            }
        }
    }

    @Test
    @TestId("BMK-36")
    fun anExternalLinkWithNoAppToOpenIt() {
        openLinks(BookmarksFixtures.LINKS) {
            val position = links.positionOfUrl(BookmarksFixtures.LINKS_UNHANDLED_URL)
            links.tapUrl(position)

            Snackbars.awaitText(TestEnvironment.targetContext.getString(R.string.no_app_to_open_link))
            assertTrue("the links list stays in the foreground", links.isShown())
            assertEquals(
                "the row list is unchanged",
                BookmarksFixtures.LINKS_LISTED_ROWS,
                links.rowCount(),
            )
        }
    }

    @Test
    @TestId("BMK-37")
    @TestDuration(DurationCategory.NORMAL)
    fun aDocumentWithNoLinks() {
        openLinks(BookmarksFixtures.NO_LINKS) {
            links.awaitTitle(links.titleFor(0))
            assertEquals("no rows are listed", 0, links.rowCount())
            assertTrue("the empty state is visible", links.messageShown())
            assertEquals(
                "the empty state text",
                TestEnvironment.targetContext.getString(R.string.no_links_put_in_pdf),
                links.messageText(),
            )
            assertTrue("the search entry point stays hidden", !links.isSearchShown())
        }
    }

    @Test
    @TestId("BMK-38")
    fun anInternalLinkTappedOnThePageJumpsAndOffersAWayBack() {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.LINKS)).use {
            reader.awaitReady()
            Intents.init()
            try {
                BookmarksReader.jumpToPage(
                    reader,
                    BookmarksFixtures.LINKS_INTERNAL_SOURCE_PAGE_INDEX,
                )
                reader.awaitFullQualityRender()
                val originZoom = reader.readState().zoom
                    ?: throw ConditionTimeoutException("The reader state hook reports no zoom")
                val link = BookmarksReader.findLinkAtPageFraction(
                    reader,
                    BookmarksFixtures.linkRectCentreXFraction(),
                    BookmarksFixtures.linkRectCentreYFraction(),
                ) ?: throw AssertionError("The fixture exposes no link at the generated link rectangle")
                assertTrue("the generated internal link carries no URI", link.link.uri.isNullOrEmpty())
                assertEquals(
                    "the generated internal-link destination",
                    BookmarksFixtures.LINKS_INTERNAL_DESTINATION_PAGE_INDEX,
                    link.link.destPageIdx,
                )

                reader.tapAtPageFraction(
                    BookmarksFixtures.linkRectCentreXFraction(),
                    BookmarksFixtures.linkRectCentreYFraction(),
                )

                reader.awaitPageIndex(BookmarksFixtures.LINKS_INTERNAL_DESTINATION_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
                assertTrue(
                    "an internal link must not launch an external app, the recorded intents were " +
                        Intents.getIntents().map { it.action },
                    Intents.getIntents().none { intent -> intent.action == Intent.ACTION_VIEW },
                )

                val backText = TestEnvironment.targetContext.getString(
                    R.string.back_to_page,
                    BookmarksFixtures.LINKS_INTERNAL_SOURCE_PAGE_INDEX + 1,
                )
                Snackbars.awaitText(backText)

                setZoom(originZoom * ZOOM_FACTOR)
                reader.awaitZoom { abs(it - originZoom) > ZOOM_TOLERANCE }

                tapSnackbarBack(backText)

                reader.awaitPageIndex(BookmarksFixtures.LINKS_INTERNAL_SOURCE_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
                val restored = reader.awaitZoom(Await.LONG_TIMEOUT_MS) { abs(it - originZoom) <= ZOOM_TOLERANCE }
                assertEquals(
                    "the captured view state restores the origin page",
                    BookmarksFixtures.LINKS_INTERNAL_SOURCE_PAGE_INDEX as Int?,
                    restored.page,
                )
                assertEquals(
                    "the captured view state restores the origin zoom",
                    originZoom.toDouble(),
                    restored.zoom!!.toDouble(),
                    ZOOM_TOLERANCE.toDouble(),
                )
            } finally {
                Intents.release()
            }
        }
    }

    @Test
    @TestId("BMK-39")
    fun copyingALinkUrl() {
        IntentStubs.clearClipboard()
        openLinks(BookmarksFixtures.LINKS) {
            val position = links.positionOfUrl(BookmarksFixtures.LINKS_COPY_URL)
            links.tapCopy(position)

            Await.untilValue(
                condition = "the copied url reaches the clipboard",
                read = { IntentStubs.clipboardText() },
            ) { it == BookmarksFixtures.LINKS_COPY_URL }

            assertTrue("the links list stays in the foreground", links.isShown())
            assertEquals("the row list is unchanged", BookmarksFixtures.LINKS_LISTED_ROWS, links.rowCount())
        }
    }

    @Test
    @TestId("BMK-40")
    fun filteringTheLinksList() {
        openLinks(BookmarksFixtures.LINKS) {
            val expected = BookmarksFixtures.LINKS_ALL_URLS.filter {
                it.contains(BookmarksFixtures.LINKS_HOST_QUERY, ignoreCase = true)
            }

            links.openSearch()
            links.typeQuery(BookmarksFixtures.LINKS_HOST_QUERY)

            Await.untilValue(
                condition = "the list is filtered to the ${expected.size} matching rows",
                read = { links.rowCount() },
            ) { it == expected.size }
            assertEquals("only the matching urls are listed", expected, links.rowUrls())
            Snackbars.awaitText(links.filteredResultsText(expected.size))

            links.closeSearch()
            links.awaitRowCount(BookmarksFixtures.LINKS_LISTED_ROWS)
            assertEquals("closing the search restores the full list", BookmarksFixtures.LINKS_ALL_URLS, links.rowUrls())
        }
    }

    private fun setZoom(zoom: Float) {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No reader is resumed while setting the zoom")
            val pdfView = activity.findViewById<PDFView>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader has no pdfView while setting the zoom")
            pdfView.debugSetViewport(zoom, pdfView.currentXOffset, pdfView.currentYOffset)
        }
        TestEnvironment.waitForIdleSync()
    }

    private fun tapSnackbarBack(snackbarText: String) {
        Snackbars.awaitText(snackbarText)
        JumpBackSnackbars.tapMessage()
    }

    private fun openLinks(fixture: String, block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(fixture)).use {
            reader.awaitReady()
            links.openFromReader()
            block()
        }
    }

    private companion object {
        const val ZOOM_FACTOR = 2.0f
        const val ZOOM_TOLERANCE = 0.01f
    }
}
