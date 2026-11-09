// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.PdfExtractorDebug
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModePageStateTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val text = TextModeRobot()

    @After
    fun clearPageTextFault() {
        PdfExtractorDebug.clearPageTextFault()
    }

    @Test
    @TestId("TXT-14")
    fun aScannedDocumentShowsTheEmptyPageMessage() {
        val noText = text.string(R.string.text_mode_no_text)

        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.SCANNED))
        ).use {
            text.awaitReaderSettled()
            text.openFromReaderMenu()
            text.awaitPageMessage(0, noText)

            (0 until EMPTY_PAGES_CHECKED).forEach { position ->
                text.awaitPageMessage(position, noText)
                assertEquals(
                    "the empty page message must replace the spinner at position $position",
                    View.GONE as Int?,
                    text.pageProgressVisibility(position),
                )
                assertFalse(
                    "no page text may be visible at position $position",
                    text.pageTextVisibility(position) == View.VISIBLE,
                )
                assertEquals(
                    "page identity must survive an empty extraction at position $position",
                    text.labelFor(position + 1),
                    text.pageLabel(position),
                )
            }

            text.hideControls()
            text.swipeUpOnce()
            text.swipeUpOnce()

            Screens.assertTopIs(TextModeActivity::class.java)
            assertNotNull(
                "an all empty document must stay open rather than being treated as a bad file",
                text.pageMessage(text.firstVisiblePosition()),
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-15")
    fun aFailedPageOffersRetryAndRetryReExtracts() {
        val failed = text.string(R.string.text_mode_failed_page)
        val retry = text.string(R.string.text_mode_retry)

        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.NORMAL))
        ).use {
            text.awaitReaderSettled()
            text.openFromReaderMenu()
            val beforeReload = text.awaitPageText(0).pageText!!

            text.openTypographySheet()
            text.setSheetSwitch(R.id.joinParagraphsSwitch, false)
            text.setSheetSwitch(R.id.detectHeadingsSwitch, false)
            PdfExtractorDebug.failNextPageTextByIndex(FAULT_PAGE)
            text.setSheetSwitch(R.id.codeBlocksSwitch, false)
            text.closeTypographySheet()

            Await.untilValue(
                condition = "the plain formatter re extracts page one",
                timeoutMs = Await.LONG_TIMEOUT_MS,
                read = { text.snapshot(0) },
            ) { it != null && it.pageTextVisible && !it.pageText.isNullOrBlank() && it.pageText !== beforeReload }

            text.goToPage(FAULT_PAGE + 1)
            text.awaitPageCounter(text.counterFor(FAULT_PAGE + 1, TextFixtures.NORMAL_PAGES))
            text.awaitPageMessage(FAULT_PAGE, failed)

            val message = text.pageMessage(FAULT_PAGE).orEmpty()
            assertTrue(
                "the failed page must offer a retry, it reads: " + CapturedReflow.literal(message),
                message.contains(failed) && message.contains(retry),
            )
            assertFalse(
                "the failed page must show no page text",
                text.pageTextVisibility(FAULT_PAGE) == View.VISIBLE,
            )

            listOf(FAULT_PAGE - 1, FAULT_PAGE + 1).forEach { position ->
                text.awaitPageText(position)
                assertFalse(
                    "page ${position + 1} must never enter the failed state, it reads: " +
                        CapturedReflow.literal(text.pageMessage(position)),
                    text.pageMessage(position)?.contains(failed) == true,
                )
            }

            PdfExtractorDebug.clearPageTextFault()
            text.tapPageMessage(FAULT_PAGE)

            val recovered = text.awaitPageText(FAULT_PAGE)
            assertTrue(
                "retrying must re extract the page",
                recovered.pageTextVisible && !recovered.pageText.isNullOrBlank(),
            )

            text.finish()
        }
    }

    private companion object {
        const val EMPTY_PAGES_CHECKED = 3
        const val FAULT_PAGE = 2
    }
}
