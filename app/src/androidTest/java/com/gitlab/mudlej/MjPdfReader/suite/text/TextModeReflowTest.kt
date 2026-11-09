// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeReflowTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @Test
    @TestId("TXT-06")
    fun thePageTextMatchesTheDocumentContent() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader(TextFixtures.STRUCTURED_HEADING_PAGE)

            val pageOne = text.pageText(TextFixtures.STRUCTURED_HEADING_PAGE)
            CapturedReflow.compare("STRUCTURED_PAGE_1_DEFAULT", CapturedReflow.STRUCTURED_PAGE_1_DEFAULT, pageOne.trim())

            text.goToPage(TextFixtures.STRUCTURED_LIGATURE_PAGE + 1)
            val pageFour = text.pageText(TextFixtures.STRUCTURED_LIGATURE_PAGE)
            CapturedReflow.compare("STRUCTURED_PAGE_4_DEFAULT", CapturedReflow.STRUCTURED_PAGE_4_DEFAULT, pageFour.trim())
            assertTrue(
                "page four must read \"${TextFixtures.STRUCTURED_LIGATURE_WORD}\", proving the fi ligature code " +
                    "point was expanded rather than dropped, the page reads: $pageFour",
                pageFour.contains(TextFixtures.STRUCTURED_LIGATURE_WORD),
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-07")
    @TestDuration(DurationCategory.SHORT)
    fun hardWrappedLinesRebuildIntoOneParagraph() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader(TextFixtures.STRUCTURED_HEADING_PAGE)

            val pageOne = text.pageText(TextFixtures.STRUCTURED_HEADING_PAGE).trim()
            assertTrue(
                "page one must open with the heading, it reads: $pageOne",
                pageOne.startsWith(TextFixtures.STRUCTURED_HEADING),
            )

            val afterHeading = pageOne.removePrefix(TextFixtures.STRUCTURED_HEADING)
            assertTrue(
                "the heading must be followed by a paragraph separator, page one reads: " +
                    CapturedReflow.literal(pageOne),
                afterHeading.startsWith(PARAGRAPH_SEPARATOR),
            )

            val paragraphs = afterHeading.removePrefix(PARAGRAPH_SEPARATOR).split(PARAGRAPH_SEPARATOR)
            assertEquals(
                "page one must reflow into exactly two paragraphs, it reads: " + CapturedReflow.literal(pageOne),
                2,
                paragraphs.size,
            )
            paragraphs.forEachIndexed { index, paragraph ->
                assertFalse(
                    "paragraph ${index + 1} must carry no interior newline, it reads: " +
                        CapturedReflow.literal(paragraph),
                    paragraph.contains("\n"),
                )
            }
            CapturedReflow.compare(
                "STRUCTURED_PAGE_1_FIRST_PARAGRAPH",
                CapturedReflow.STRUCTURED_PAGE_1_FIRST_PARAGRAPH,
                paragraphs.first(),
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-08")
    fun aHyphenWrappedWordJoinsIntoOneWord() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader(TextFixtures.STRUCTURED_HEADING_PAGE)
            text.goToPage(TextFixtures.STRUCTURED_HYPHEN_PAGE + 1)
            val pageThree = text.pageText(TextFixtures.STRUCTURED_HYPHEN_PAGE)

            assertHyphenJoined(pageThree)

            text.finish()
        }
    }

    @Test
    @TestId("TXT-09")
    fun aHyphenWrappedWordJoinsWithEveryReflowSwitchOff() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader(TextFixtures.STRUCTURED_HEADING_PAGE)
            turnEveryReflowSwitchOff()

            text.goToPage(TextFixtures.STRUCTURED_HYPHEN_PAGE + 1)
            val pageThree = text.pageText(TextFixtures.STRUCTURED_HYPHEN_PAGE)

            assertHyphenJoined(pageThree)
            CapturedReflow.compare(
                "STRUCTURED_PAGE_3_SWITCHES_OFF",
                CapturedReflow.STRUCTURED_PAGE_3_SWITCHES_OFF,
                pageThree.trim(),
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-10")
    fun joinParagraphsOffIsRememberedPerBook() {
        val structuredHash: String
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.awaitReaderSettled()
            structuredHash = reader.awaitFileHashResolved()

            text.openFromReaderMenu()
            text.awaitPageText(TextFixtures.STRUCTURED_HEADING_PAGE)
            text.openTypographySheet()
            text.setSheetSwitch(R.id.joinParagraphsSwitch, false)
            text.closeTypographySheet()

            val stored = Records.awaitRow(
                structuredHash,
                "the structured document stores Join Paragraphs off",
            ) { it?.textModeJoinParagraphs == false }!!
            assertNull(
                "Detect Headings must stay unset when only Join Paragraphs was changed",
                stored.textModeDetectHeadings,
            )
            assertNull(
                "Code Blocks must stay unset when only Join Paragraphs was changed",
                stored.textModeCodeBlocks,
            )

            TestEnvironment.uiDevice.pressBack()
            text.awaitFinished()

            text.openFromReaderMenu()
            val reopened = text.awaitPageText(TextFixtures.STRUCTURED_HEADING_PAGE)
            text.openTypographySheet()
            assertFalse(
                "Join Paragraphs must still read off when the book is opened in text mode again",
                text.sheetSwitchChecked(R.id.joinParagraphsSwitch),
            )
            text.closeTypographySheet()
            CapturedReflow.compare(
                "STRUCTURED_PAGE_1_JOIN_OFF",
                CapturedReflow.STRUCTURED_PAGE_1_JOIN_OFF,
                reopened.pageText?.toString()?.trim(),
            )

            text.finish()
        }

        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.NORMAL))
        ).use {
            text.awaitReaderSettled()
            val normalHash = reader.awaitFileHashResolved()
            assertFalse(
                "the two documents must not share an identity, or the per book override proves nothing",
                normalHash == structuredHash,
            )

            text.openFromReaderMenu()
            text.awaitPageText(0)
            text.openTypographySheet()
            assertTrue(
                "Join Paragraphs must still read on for a book that never turned it off",
                text.sheetSwitchChecked(R.id.joinParagraphsSwitch),
            )
            text.closeTypographySheet()

            assertNull(
                "the untouched book must carry no Join Paragraphs override",
                Records.byHash(normalHash)?.textModeJoinParagraphs,
            )

            text.finish()
        }
    }

    private fun assertHyphenJoined(page: String) {
        assertTrue(
            "the hyphen wrapped word must read as \"${TextFixtures.STRUCTURED_HYPHEN_WORD}\", the page reads: " +
                CapturedReflow.literal(page),
            page.contains(TextFixtures.STRUCTURED_HYPHEN_WORD),
        )
        listOf(
            TextFixtures.STRUCTURED_HYPHEN_HEAD,
            "extraor dinary",
            "extraor\ndinary",
        ).forEach { fragment ->
            assertFalse(
                "the page must not carry ${CapturedReflow.literal(fragment)}, it reads: " +
                    CapturedReflow.literal(page),
                page.contains(fragment),
            )
        }
    }

    private fun turnEveryReflowSwitchOff() {
        text.openTypographySheet()
        text.setSheetSwitch(R.id.joinParagraphsSwitch, false)
        text.setSheetSwitch(R.id.detectHeadingsSwitch, false)
        text.setSheetSwitch(R.id.codeBlocksSwitch, false)
        text.closeTypographySheet()
    }

    private companion object {
        const val PARAGRAPH_SEPARATOR = "\n\n"
    }
}
