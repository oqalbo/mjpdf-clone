// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PrintActionTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(
        ActionsFixtures.EIGHT_PAGE,
        ActionsFixtures.NORMAL,
        ActionsFixtures.CORRUPT,
        ActionsFixtures.PROTECTED,
    )

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    @get:Rule
    val intents = IntentsRule()

    private val act = ActionsRobot()

    @Test
    @TestId("ACT-37")
    fun printingHandsTheFrameworkTheWholeDocument() {
        PrintStaging.requireSpoolerInstalled()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.EIGHT_PAGE))).use {
            act.awaitReady()

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.PRINT))

            val staged = PrintStaging.awaitSingleStagedFile()
            PrintStaging.awaitSpoolerShown()

            assertEquals(
                "the staged copy carries every page of the document",
                ActionsFixtures.EIGHT_PAGE_PAGES,
                PrintStaging.pageCountOf(staged),
            )
            assertTrue(
                "the staged file follows the print-*.pdf naming the controller writes, it reads ${staged.name}",
                staged.name.startsWith(PrintStaging.STAGED_PREFIX) && staged.name.endsWith(PrintStaging.STAGED_SUFFIX),
            )

            PrintStaging.dismissSpooler()
        }
    }

    @Test
    @TestId("ACT-38")
    fun backingOutOfThePrintPreviewLeavesNoCopyOfTheDocumentInTheCache() {
        PrintStaging.requireSpoolerInstalled()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.PRINT))
            PrintStaging.awaitSingleStagedFile()
            PrintStaging.awaitSpoolerShown()

            PrintStaging.dismissSpooler()
            act.awaitReaderResumed()

            PrintStaging.awaitStagingEmpty()
            ToolbarItems.awaitLabel(ToolbarItems.actionsId, ActionLabels.string(R.string.actions))
        }
    }

    @Test
    @TestId("ACT-39")
    fun anUnprintableDocumentSaysSoAndOffersShareInstead() {
        IntentStubs.stubChooser()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.CORRUPT))).use {
            act.reader().awaitShown()
            act.reader().awaitLoadSettled()

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.PRINT))

            Snackbars.awaitText(ActionLabels.string(R.string.print_cannot_print))
            assertEquals(
                "the snackbar offers sharing as the alternative",
                ActionLabels.of(ConfigurableAction.SHARE),
                requireSnackbarActionText(),
            )
            PrintStaging.assertStagingStaysEmpty()
            PrintStaging.awaitSpoolerGone(Await.SHORT_TIMEOUT_MS)

            onView(withId(com.google.android.material.R.id.snackbar_action)).perform(click())

            val payload = ShareIntents.payloadOf(ShareIntents.awaitChooser())
            assertEquals(
                "the share alternative must launch a real send intent",
                Intent.ACTION_SEND,
                payload.action,
            )
        }
    }

    @Test
    @TestId("ACT-40")
    @TestDuration(DurationCategory.NORMAL)
    fun anUnlockedDocumentPrintsBecauseTheStagedCopyCarriesNoEncryption() {
        PrintStaging.requireSpoolerInstalled()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.PROTECTED))).use {
            PasswordPrompt.unlock(ActionsFixtures.PROTECTED_PASSWORD)
            act.awaitReady()

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.PRINT))

            val staged = PrintStaging.awaitSingleStagedFile()
            PrintStaging.awaitSpoolerShown()

            assertTrue(
                "the staged copy must open with no password at all",
                PrintStaging.opensWithoutPassword(staged),
            )
            assertEquals(
                "the decrypted copy carries every page of the fixture",
                ActionsFixtures.PROTECTED_PAGES,
                PrintStaging.pageCountOf(staged),
            )

            PrintStaging.dismissSpooler()
        }
    }

    private fun snackbarActionText(): String? = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity() ?: return@onMainThread null
        val action = activity.findViewById<android.widget.Button>(com.google.android.material.R.id.snackbar_action)
        if (action == null || !action.isShown) null else action.text?.toString()
    }

    private fun requireSnackbarActionText(): String = Await.untilNotNull(
        "the print failure snackbar carries a shown action button offering the alternative",
        Await.SHORT_TIMEOUT_MS,
    ) {
        snackbarActionText()
    }
}
