// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class AnnotationGateActionsTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL, ActionsFixtures.HIGHLIGHTS)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()
    private val reader = act.reader()
    private val selection = SelectionRobot()
    private val picker = DocumentPickerRobot()

    @Test
    @TestId("ACT-48")
    fun anEditMadeWhileASaveIsRunningIsRefusedPolitelyAndLeavesTheJournalAlone() {
        openReaderWithOneHighlight { journal ->
            val linesBefore = ActionsHighlights.journalLineCount(journal)

            ActionsHighlights.startSelectionOnUnhighlightedText(selection)

            ActionsSaveGate.beginSeededSave()

            ActionsHighlights.clickFirstSwatchDirectly()

            Snackbars.awaitText(ActionLabels.string(R.string.annotation_edit_blocked_while_saving))
            ActionsHighlights.assertJournalLineCountStays(journal, linesBefore)

            ActionsSaveGate.endSeededSave()

            ActionsHighlights.clickFirstSwatchDirectly()
            ActionsHighlights.awaitJournalLineCount(journal, linesBefore + 1)
        }
    }

    @Test
    @TestId("ACT-49")
    fun theWholeReaderIsSealedWhileASaveRunsSoNothingCanRaceTheWriter() {
        openReaderWithOneHighlight {
            ActionsSaveGate.awaitSaveFabVisible()

            ActionsSaveGate.beginSeededSave()

            val save = ActionsSaveGate.saveFab()
            val discard = ActionsSaveGate.discardFab()
            assertTrue("the save button must stay on screen while the save runs", save.visible)
            assertFalse("the save button must refuse a second submission", save.enabled)
            assertFalse("the discard button must refuse a submission too", discard.enabled)
            assertEquals("the save button must read as busy", ActionsSaveGate.SAVING_ALPHA, save.alpha, ALPHA_TOLERANCE)
            assertEquals("the discard button must read as busy", ActionsSaveGate.SAVING_ALPHA, discard.alpha, ALPHA_TOLERANCE)
            assertTrue("the indeterminate progress bar must be shown", isProgressShown())

            ToolbarItems.tapWithRealTouch(ToolbarItems.actionsId)
            act.actionsDialog.assertNotShown()

            ActionsSaveGate.endSeededSave()

            ToolbarItems.tapWithRealTouch(ToolbarItems.actionsId)
            act.actionsDialog.awaitShown()
            act.actionsDialog.dismiss()

            val afterSave = ActionsSaveGate.saveFab()
            assertTrue("the save button comes back once the save ends", afterSave.enabled)
            assertEquals(
                "the save button reads as idle again",
                ActionsSaveGate.IDLE_ALPHA,
                afterSave.alpha,
                ALPHA_TOLERANCE,
            )
        }
    }

    @Test
    @TestId("ACT-50")
    @TestDuration(DurationCategory.SHORT)
    fun theHighlightsListActionGoesThroughTheUnsavedWorkGateFirst() {
        openReaderWithOneHighlight { journal ->
            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.USER_HIGHLIGHTS))

            act.awaitDialogText(ActionLabels.string(R.string.unsaved_highlights))
            assertFalse(
                "the highlights screen must not open before the user has answered the gate",
                Screens.isInTask(UserHighlightsActivity::class.java),
            )

            act.tapDialogButton(ActionLabels.string(R.string.cancel))

            act.awaitReaderResumed()
            assertFalse(
                "cancelling the gate must leave the reader where it was",
                Screens.isInTask(UserHighlightsActivity::class.java),
            )
            assertTrue(
                "cancelling the gate must leave the unsaved work on disk",
                journal.isFile && ActionsHighlights.journalLineCount(journal) > 0,
            )
        }
    }

    @Test
    @TestId("ACT-51")
    fun openingAnotherDocumentCannotSilentlyDropUnsavedHighlights() {
        openReaderWithOneHighlight { journal ->
            val documentBefore = reader.readState().documentUri

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.OPEN_LOCAL))

            act.awaitDialogText(ActionLabels.string(R.string.unsaved_highlights))
            assertFalse("the picker must not open before the gate is answered", picker.isShown())

            act.tapDialogButton(ActionLabels.string(R.string.discard))

            Await.until("the annotation journal is removed by the discard", Await.LONG_TIMEOUT_MS) {
                !journal.exists()
            }
            picker.awaitShown()

            picker.cancel()
            act.awaitReaderResumed()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)

            assertEquals(
                "cancelling a picker the user opened deliberately returns to the same document",
                documentBefore,
                reader.readState().documentUri,
            )
        }
    }

    private fun openReaderWithOneHighlight(body: (java.io.File) -> Unit) {
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()
            val journal = ActionsHighlights.journalFileOfOpenDocument(reader)
            ActionsHighlights.createOne(reader, selection)
            ActionsHighlights.awaitJournalLineCount(journal, 1)
            body(journal)
        }
    }

    private fun isProgressShown(): Boolean =
        TestEnvironment.onMainThread {
            act.activityOrNull()?.findViewById<android.view.View>(R.id.progressBar)?.visibility ==
                android.view.View.VISIBLE
        }

    private companion object {
        const val ALPHA_TOLERANCE = 0.001f
    }
}
