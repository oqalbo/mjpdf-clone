// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ToolbarPickerSettingsTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()

    @Test
    @TestId("ACT-52")
    fun aToolbarChangeMadeInSettingsIsLiveOnTheReaderAsSoonAsTheUserComesBack() {
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.of(ConfigurableAction.FULLSCREEN))

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.SETTINGS))
            ActionsSettings.awaitResumed()
            ActionsSettings.openRow(ActionLabels.string(R.string.customize_controls))
            ActionsSettings.awaitRow(ActionLabels.string(R.string.primary_button_action))
            ActionsSettings.openRow(ActionLabels.string(R.string.primary_button_action))
            ActionSelectionDialog.awaitPickerShown()

            val entries = ActionSelectionDialog.pickerLabels()
            assertEquals(
                "the picker offers exactly the toolbar action set",
                ConfigurableAction.toolbarActions.size,
                entries.size,
            )
            assertEquals(
                "the picker leads with the none entry, which is how a slot is emptied",
                ActionLabels.string(R.string.none),
                entries.first(),
            )
            assertTrue(
                "the picker must carry the default action, it reads $entries",
                ActionLabels.of(ConfigurableAction.FULLSCREEN) in entries,
            )
            assertTrue(
                "the picker must carry the last toolbar action, it reads $entries",
                ActionLabels.of(ConfigurableAction.ABOUT) in entries,
            )

            ActionSelectionDialog.pickAction(ActionLabels.of(ConfigurableAction.PRINT))
            ActionsPreferences.awaitString(Preferences.primaryButtonActionKey, ConfigurableAction.PRINT.id)

            act.pressBackUntilReaderResumed()

            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.of(ConfigurableAction.PRINT))
            ToolbarItems.assertNoItemLabelled(ActionLabels.of(ConfigurableAction.FULLSCREEN))
        }
    }

    @Test
    @TestId("ACT-55")
    fun theSingleChoicePickerCommitsOnTapAndItsResetReturnsTheShippedDefault() {
        ActivityScenario.launch<SettingsActivity>(ActionsSettings.customizeControlsIntent()).use {
            ActionsSettings.awaitResumed()
            val rowTitle = ActionLabels.string(R.string.primary_button_action)

            ActionsSettings.openRow(rowTitle)
            ActionSelectionDialog.awaitPickerShown()
            ActionSelectionDialog.pickAction(ActionLabels.of(ConfigurableAction.SEARCH))

            ActionsPreferences.awaitString(Preferences.primaryButtonActionKey, ConfigurableAction.SEARCH.id)
            ActionsSettings.awaitRowSummaryContains(rowTitle, ActionLabels.of(ConfigurableAction.SEARCH))

            ActionsSettings.openRow(rowTitle)
            ActionSelectionDialog.awaitPickerShown()
            ActionSelectionDialog.cancel()

            assertEquals(
                "cancelling the picker must leave the stored value untouched",
                ConfigurableAction.SEARCH.id,
                ActionsPreferences.primaryActionId(),
            )

            ActionsSettings.openRow(rowTitle)
            ActionSelectionDialog.awaitPickerShown()
            ActionSelectionDialog.reset()

            ActionsPreferences.awaitString(Preferences.primaryButtonActionKey, Preferences.primaryButtonActionDefault)
            ActionsSettings.awaitRowSummaryContains(rowTitle, ActionLabels.of(ConfigurableAction.FULLSCREEN))
        }
    }
}
