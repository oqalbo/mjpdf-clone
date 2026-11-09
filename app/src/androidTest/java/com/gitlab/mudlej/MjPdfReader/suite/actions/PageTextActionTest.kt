// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class PageTextActionTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.KNOWN_TEXT, ActionsFixtures.SCANNED)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()

    @Before
    fun clearTheClipboard() {
        IntentStubs.clearClipboard()
    }

    @Test
    @TestId("ACT-33")
    @TestDuration(DurationCategory.SHORT)
    fun copyPageTextPutsThePagesRealTextOnTheClipboard() {
        ActionsPreferences.seedShortcutBar(selected = listOf(ConfigurableAction.EXTRACT_TEXT))
        val expected = ActionsFixtures.groundTruthPageText()

        act.openReader(ActionsFixtures.KNOWN_TEXT) {
            ShortcutBar.awaitVisible()
            ShortcutBar.tap(ActionLabels.of(ConfigurableAction.EXTRACT_TEXT))

            val expectedTitle = "${TestEnvironment.targetString(R.string.selectable_text)} #1"
            Dialogs.awaitText(expectedTitle)
            onView(withText(expectedTitle)).inRoot(isDialog()).check(matches(isDisplayed()))

            onView(withText(TestEnvironment.targetString(R.string.copy_all))).inRoot(isDialog())
                .perform(androidx.test.espresso.action.ViewActions.click())

            Await.untilValue(
                "the clipboard holds the page text",
                read = { IntentStubs.clipboardText() },
            ) { !it.isNullOrEmpty() }

            assertEquals(
                "the clipboard must carry the committed ground truth of page one, not a placeholder",
                expected,
                IntentStubs.clipboardText()?.trim(),
            )
            assertEquals(
                "the clip is labelled with the zero based page index the copier used",
                "${TestEnvironment.targetString(R.string.page)} #0 Text",
                IntentStubs.clipboardLabel(),
            )
        }
    }

    @Test
    @TestId("ACT-34")
    fun aPageWithNoTextSaysSoOnceRatherThanOnEveryAttempt() {
        ActionsPreferences.seedShortcutBar(selected = listOf(ConfigurableAction.EXTRACT_TEXT))

        act.openReader(ActionsFixtures.SCANNED) {
            ShortcutBar.awaitVisible()
            val clipboardBefore = IntentStubs.clipboardText()

            ShortcutBar.tap(ActionLabels.of(ConfigurableAction.EXTRACT_TEXT))
            Snackbars.awaitText(NO_TEXT_MESSAGE)
            assertFalse("no selectable text dialog belongs on an image only page", isSelectableTextDialogShown())

            Snackbars.awaitGone()

            ShortcutBar.tap(ActionLabels.of(ConfigurableAction.EXTRACT_TEXT))
            Snackbars.assertNoneWithin()
            assertFalse("the second attempt must still show no dialog", isSelectableTextDialogShown())

            assertEquals(
                "a page with no text must leave the clipboard alone",
                clipboardBefore,
                IntentStubs.clipboardText(),
            )
        }
    }

    private fun isSelectableTextDialogShown(): Boolean = Dialogs.isAnyShowing() && runCatching {
        onView(withText(TestEnvironment.targetString(R.string.copy_all))).inRoot(isDialog()).check(matches(isDisplayed()))
    }.isSuccess

    private companion object {
        const val NO_TEXT_MESSAGE = "Couldn't find text in this page."
    }
}
