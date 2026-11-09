// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
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
class ShortcutBarSettingsTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()

    @Test
    @TestId("ACT-27")
    fun applyingANewSelectionInTheShortcutBarDialogPersistsExactlyWhatWasChosen() {
        ActivityScenario.launch<SettingsActivity>(ActionsSettings.customizeControlsIntent()).use {
            ActionsSettings.awaitResumed()
            ActionsSettings.openRow(TestEnvironment.targetString(R.string.shortcut_bar_buttons))
            ActionSelectionDialog.awaitShown()

            ActionSelectionDialog.setChecked(label(ConfigurableAction.SEARCH), false)
            ActionSelectionDialog.setChecked(label(ConfigurableAction.TEXT_MODE), false)
            ActionSelectionDialog.setChecked(label(ConfigurableAction.PRINT), true)

            ActionSelectionDialog.apply()
            ActionSelectionDialog.awaitDismissed()

            val selected = ActionsPreferences.shortcutBarActionIds().orEmpty()
            assertTrue("the newly checked action must be stored, it reads $selected", ConfigurableAction.PRINT.id in selected)
            assertTrue("the unchecked search action must be gone, it reads $selected", ConfigurableAction.SEARCH.id !in selected)
            assertTrue("the unchecked text mode action must be gone, it reads $selected", ConfigurableAction.TEXT_MODE.id !in selected)

            listOf(
                ConfigurableAction.SWITCH_THEME,
                ConfigurableAction.TABLE_OF_CONTENTS,
                ConfigurableAction.GO_TO_PAGE,
                ConfigurableAction.BOOKMARK_PAGE,
                ConfigurableAction.SETTINGS,
            ).forEach { action ->
                assertTrue("the untouched action ${action.id} must survive, it reads $selected", action.id in selected)
            }

            val order = ActionsPreferences.shortcutBarOrderIds().orEmpty()
            assertTrue("the stored order must not be empty", order.isNotEmpty())
            val validIds = ConfigurableAction.shortcutBarActions.map { it.id }.toSet()
            assertTrue(
                "every stored order entry must be a shortcut bar action id, it reads $order",
                order.all { it in validIds },
            )
        }
    }

    @Test
    @TestId("ACT-28")
    @TestDuration(DurationCategory.SHORT)
    fun cancellingTheShortcutBarDialogWritesNothingAtAll() {
        ActivityScenario.launch<SettingsActivity>(ActionsSettings.customizeControlsIntent()).use {
            ActionsSettings.awaitResumed()
            ActionsSettings.openRow(TestEnvironment.targetString(R.string.shortcut_bar_buttons))
            ActionSelectionDialog.awaitShown()

            ActionSelectionDialog.setChecked(label(ConfigurableAction.SEARCH), false)
            ActionSelectionDialog.setChecked(label(ConfigurableAction.SETTINGS), false)

            ActionSelectionDialog.cancel()
            ActionSelectionDialog.awaitDismissed()

            ActionsPreferences.assertUnset(Preferences.shortcutBarActionsKey)
            ActionsPreferences.assertUnset(Preferences.shortcutBarActionOrderKey)
        }
    }

    @Test
    @TestId("ACT-29")
    @TestDuration(DurationCategory.SHORT)
    fun resetRestoresTheShippedSelectionAndOrderInOneTap() {
        ActionsPreferences.seedShortcutBar(
            selected = listOf(ConfigurableAction.PRINT),
            order = listOf(
                ConfigurableAction.PRINT,
                ConfigurableAction.SHARE,
                ConfigurableAction.SCREENSHOT,
            ),
            enabled = false,
        )

        ActivityScenario.launch<SettingsActivity>(ActionsSettings.customizeControlsIntent()).use {
            ActionsSettings.awaitResumed()
            ActionsSettings.openRow(TestEnvironment.targetString(R.string.shortcut_bar_buttons))
            ActionSelectionDialog.awaitShown()

            ActionSelectionDialog.reset()

            assertEquals(
                "Reset scrolls the list back to the top, so the first shipped row is the one on screen",
                label(ConfigurableAction.SWITCH_THEME),
                ActionSelectionDialog.firstVisibleRow().label,
            )

            ActionSelectionDialog.apply()
            ActionSelectionDialog.awaitDismissed()

            assertEquals(
                "Reset then Apply restores exactly the shipped selection",
                ActionsPreferences.idSet(ActionsPreferences.defaultShortcutBarActions),
                ActionsPreferences.shortcutBarActionIds(),
            )
            ActionsPreferences.awaitOrderStartsWith(
                Preferences.shortcutBarActionOrderKey,
                ActionsPreferences.ids(ActionsPreferences.defaultShortcutBarActions),
            )
        }
    }

    @Test
    @TestId("ACT-30")
    fun draggingARowReordersTheBarAndTheReaderRendersTheNewOrder() {
        ActionsPreferences.seedShortcutBarEnabled(true)

        ActivityScenario.launch<SettingsActivity>(ActionsSettings.customizeControlsIntent()).use {
            ActionsSettings.awaitResumed()
            ActionsSettings.openRow(TestEnvironment.targetString(R.string.shortcut_bar_buttons))
            ActionSelectionDialog.awaitShown()

            ActionSelectionDialog.dragRowToTop(label(ConfigurableAction.SETTINGS))
            assertEquals(
                "the dragged row must come to rest at the top of the list",
                label(ConfigurableAction.SETTINGS),
                ActionSelectionDialog.firstVisibleRow().label,
            )

            ActionSelectionDialog.apply()
            ActionSelectionDialog.awaitDismissed()
        }

        ActionsPreferences.awaitOrderStartsWith(
            Preferences.shortcutBarActionOrderKey,
            listOf(ConfigurableAction.SETTINGS.id),
        )

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()
            ShortcutBar.awaitVisible()
            ShortcutBar.awaitChildCount(ActionsPreferences.DEFAULT_SHORTCUT_BAR_SIZE)
            assertEquals(
                "the reader renders the reordered bar with Settings first",
                label(ConfigurableAction.SETTINGS),
                ShortcutBar.labels().first(),
            )
        }
    }

    @Test
    @TestId("ACT-53")
    @TestDuration(DurationCategory.LONG)
    fun theBarRebuildsItselfOnReturnFromSettingsRatherThanCachingTheOldConfiguration() {
        ActionsPreferences.seedShortcutBarEnabled(true)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()
            ShortcutBar.awaitVisible()
            ShortcutBar.awaitChildCount(ActionsPreferences.DEFAULT_SHORTCUT_BAR_SIZE)

            ShortcutBar.tap(label(ConfigurableAction.SETTINGS))
            ActionsSettings.awaitResumed()
            ActionsSettings.openRow(TestEnvironment.targetString(R.string.customize_controls))
            ActionsSettings.awaitRow(TestEnvironment.targetString(R.string.shortcut_bar_buttons))
            ActionsSettings.openRow(TestEnvironment.targetString(R.string.shortcut_bar_buttons))
            ActionSelectionDialog.awaitShown()

            ActionSelectionDialog.checkOnly(setOf(label(ConfigurableAction.SCREENSHOT)))
            ActionSelectionDialog.apply()
            ActionSelectionDialog.awaitDismissed()

            act.pressBackUntilReaderResumed()

            ShortcutBar.awaitChildCount(1)
            assertEquals(
                "the reader rebuilds the bar from the new selection on resume",
                listOf(label(ConfigurableAction.SCREENSHOT)),
                ShortcutBar.labels(),
            )
            assertEquals(
                "the settings dialog stored exactly the one action that stayed checked",
                setOf(ConfigurableAction.SCREENSHOT.id),
                ActionsPreferences.shortcutBarActionIds(),
            )
        }
    }

    private fun label(action: ConfigurableAction): String = ActionLabels.row(action)
}
