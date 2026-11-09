// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class JumpBackSnackbarTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()

    @Test
    @TestId("HIST-30")
    fun aTableOfContentsJumpOffersAWayBackToTheContents() {
        open(HistoryFixtures.TOC) {
            TableOfContentsRoute.openFromReader(hist)
            TableOfContentsRoute.tapEntry(HistoryFixtures.TOC_RESULTS_TITLE)

            Screens.awaitGone(TableOfContentsActivity::class.java)
            hist.reader.awaitPageIndex(HistoryFixtures.TOC_RESULTS_PAGE)
            JumpBackSnackbars.awaitText(JumpBackSnackbars.backToTableOfContents())

            JumpBackSnackbars.tapMessage()

            TableOfContentsRoute.awaitResumed()
        }
    }

    @Test
    @TestId("HIST-31")
    fun aLinkJumpOffersAWayBackNamingTheOriginPage() {
        open(HistoryFixtures.LINKS) {
            hist.jumpToPage(HistoryFixtures.LINK_ORIGIN_PAGE + 1)
            hist.reader.awaitFullQualityRender()

            hist.tapAtPageFraction(HistoryFixtures.LINK_CENTRE_X_FRACTION, HistoryFixtures.LINK_CENTRE_Y_FRACTION)

            hist.reader.awaitPageIndex(HistoryFixtures.LINK_DESTINATION_PAGE)
            val expected = JumpBackSnackbars.backToPage(HistoryFixtures.LINK_ORIGIN_PAGE + 1)
            JumpBackSnackbars.awaitText(expected)
            assertEquals("the snackbar names the page the link was followed from", expected, JumpBackSnackbars.requireSingle())

            JumpBackSnackbars.tapMessage()

            hist.reader.awaitPageIndex(HistoryFixtures.LINK_ORIGIN_PAGE)
            JumpBackSnackbars.awaitGone()
        }
    }

    @Test
    @TestId("HIST-32")
    @TestDuration(DurationCategory.LONG)
    fun theJumpBackSnackbarDoesNotTimeOutAndDoneDismissesIt() {
        open(HistoryFixtures.LINKS) {
            hist.jumpToPage(HistoryFixtures.LINK_ORIGIN_PAGE + 1)
            hist.reader.awaitFullQualityRender()
            hist.tapAtPageFraction(HistoryFixtures.LINK_CENTRE_X_FRACTION, HistoryFixtures.LINK_CENTRE_Y_FRACTION)
            hist.reader.awaitPageIndex(HistoryFixtures.LINK_DESTINATION_PAGE)

            val expected = JumpBackSnackbars.backToPage(HistoryFixtures.LINK_ORIGIN_PAGE + 1)
            JumpBackSnackbars.awaitText(expected)

            repeat(PAGE_TURNS) { hist.turnPageForward() }
            JumpBackSnackbars.holdShown(expected, OBSERVATION_WINDOW_MS)

            val pageBeforeDone = hist.reader.readState().page
            JumpBackSnackbars.tapDone()

            JumpBackSnackbars.awaitGone()
            assertEquals(
                "dismissing the offer must not move the reader",
                pageBeforeDone,
                hist.reader.readState().page,
            )
        }
    }

    @Test
    @TestId("HIST-33")
    @TestDuration(DurationCategory.NORMAL)
    fun aSecondJumpReplacesTheFirstSnackbar() {
        open(HistoryFixtures.LINKS) {
            hist.jumpToPage(HistoryFixtures.LINK_ORIGIN_PAGE + 1)
            hist.reader.awaitFullQualityRender()
            hist.tapAtPageFraction(HistoryFixtures.LINK_CENTRE_X_FRACTION, HistoryFixtures.LINK_CENTRE_Y_FRACTION)
            hist.reader.awaitPageIndex(HistoryFixtures.LINK_DESTINATION_PAGE)
            JumpBackSnackbars.awaitText(JumpBackSnackbars.backToPage(HistoryFixtures.LINK_ORIGIN_PAGE + 1))

            hist.jumpToPage(HistoryFixtures.SECOND_LINK_ORIGIN_PAGE + 1)
            hist.reader.awaitFullQualityRender()
            hist.tapAtPageFraction(HistoryFixtures.LINK_CENTRE_X_FRACTION, HistoryFixtures.LINK_CENTRE_Y_FRACTION)
            hist.reader.awaitPageIndex(HistoryFixtures.SECOND_LINK_DESTINATION_PAGE)

            val expected = JumpBackSnackbars.backToPage(HistoryFixtures.SECOND_LINK_ORIGIN_PAGE + 1)
            JumpBackSnackbars.awaitText(expected)
            assertEquals(
                "two jumps must never leave two competing offers on screen",
                expected,
                JumpBackSnackbars.requireSingle(),
            )
        }
    }

    private fun open(name: String, block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(name)).use {
            hist.awaitReady()
            hist.reader.awaitPageIndex(0)
            block()
        }
    }

    private companion object {
        const val PAGE_TURNS = 3
        const val OBSERVATION_WINDOW_MS = 10_000L
    }
}
