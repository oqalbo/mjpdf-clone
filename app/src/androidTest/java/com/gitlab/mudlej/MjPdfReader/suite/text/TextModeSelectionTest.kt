// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.SearchManager
import android.content.Intent
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeSelectionTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val intents = IntentsRule()

    private val text = TextModeRobot()

    @Test
    @TestId("TXT-41")
    @TestDuration(DurationCategory.NORMAL)
    fun aLongPressSelectsAWordAndCopyPutsItOnTheClipboard() {
        IntentStubs.clearClipboard()
        val word = CapturedReflow.require("SELECTION_WORD", CapturedReflow.SELECTION_WORD)

        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            enterTextMode()
            selectWord(word)

            assertTrue("the action mode must offer Copy", text.actionModeHasItem(copyLabel()))
            assertTrue(
                "the action mode must offer the text mode Share item",
                text.actionModeHasItem(text.string(R.string.share)),
            )
            assertTrue(
                "the action mode must offer the text mode Search Web item",
                text.actionModeHasItem(text.string(R.string.search_web)),
            )

            if (!text.actionModeShowing()) {
                Screens.assertTopIs(TextModeActivity::class.java)
                selectWord(word)
            }
            text.tapActionModeItem(copyLabel())

            assertEquals("copying must place exactly the selected word on the clipboard", word, IntentStubs.clipboardText())
            text.awaitActionModeGone()

            text.finish()
        }
    }

    @Test
    @TestId("TXT-42")
    @TestDuration(DurationCategory.NORMAL)
    fun shareFromTheSelectionSendsTheSelectedText() {
        IntentStubs.stubChooser()
        IntentStubs.stubEveryOutgoingIntent()
        val word = CapturedReflow.require("SELECTION_WORD", CapturedReflow.SELECTION_WORD)
        val phrase = CapturedReflow.require("SELECTION_PHRASE", CapturedReflow.SELECTION_PHRASE)

        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            enterTextMode()
            selectWord(word)

            val page = text.pageText(0)
            val start = page.indexOf(phrase)
            assertTrue(
                "page one must carry the phrase ${CapturedReflow.literal(phrase)}",
                start >= 0,
            )
            text.extendSelection(0, start, start + phrase.length)
            assertEquals(
                "the selection must cover exactly the phrase",
                phrase,
                text.selectedText(0),
            )

            text.tapActionModeItem(text.string(R.string.share))

            intended(
                allOf(
                    hasAction(Intent.ACTION_CHOOSER),
                    hasExtra(
                        org.hamcrest.CoreMatchers.`is`(Intent.EXTRA_INTENT),
                        allOf(
                            hasAction(Intent.ACTION_SEND),
                            hasType("text/plain"),
                            hasExtra(Intent.EXTRA_TEXT, phrase),
                        ),
                    ),
                )
            )
            text.awaitActionModeGone()

            text.finish()
        }
    }

    @Test
    @TestId("TXT-43")
    fun webSearchFromTheSelectionOpensTheSearchApp() {
        IntentStubs.stubOkFor(Intent.ACTION_WEB_SEARCH)
        IntentStubs.stubEveryOutgoingIntent()
        val word = CapturedReflow.require("SELECTION_WORD", CapturedReflow.SELECTION_WORD)

        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            enterTextMode()
            selectWord(word)

            text.tapActionModeItem(text.string(R.string.search_web))

            intended(
                allOf(
                    hasAction(Intent.ACTION_WEB_SEARCH),
                    hasExtra(SearchManager.QUERY, word),
                )
            )
            text.awaitActionModeGone()

            text.finish()
        }
    }

    private fun selectWord(word: String) {
        val page = text.pageText(0)
        val at = page.indexOf(word)
        assertTrue(
            "page one must carry the word ${CapturedReflow.literal(word)}, it reads: " +
                CapturedReflow.literal(page),
            at >= 0,
        )
        text.longPressCharOffset(0, at + word.length / 2)
        text.awaitActionMode()
        assertEquals("the long press must select exactly one word", word, text.awaitSelection(0))
    }

    private fun copyLabel(): String = TestEnvironment.targetContext.getString(android.R.string.copy)

    private fun enterTextMode() {
        text.enterFromReader()
        text.hideControls()
        Screens.assertTopIs(TextModeActivity::class.java)
    }
}
