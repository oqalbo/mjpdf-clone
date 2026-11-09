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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class FullScreenOverlayTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()

    @Test
    @TestId("ACT-31")
    fun theExitRowIsLockedSoNobodyCanConfigureAFullscreenTheyCannotLeave() {
        ActivityScenario.launch<SettingsActivity>(ActionsSettings.customizeControlsIntent()).use {
            ActionsSettings.awaitResumed()
            ActionsSettings.openRow(ActionLabels.string(R.string.fullscreen_buttons))
            ActionSelectionDialog.awaitShown()

            val exitLabel = ActionLabels.row(ConfigurableAction.EXIT_FULLSCREEN)
            val first = ActionSelectionDialog.firstVisibleRow()
            assertEquals("the exit row is forced to the top of the list", exitLabel, first.label)
            assertTrue("the exit row must read as selected", first.checked)
            assertFalse("the exit row's checkbox must be disabled", first.checkboxEnabled)
            assertFalse("the exit row's drag handle must be invisible", first.dragHandleVisible)

            ActionSelectionDialog.tapRow(exitLabel)
            assertTrue(
                "tapping a locked row must leave it checked",
                ActionSelectionDialog.row(exitLabel).checked,
            )

            val second = ActionSelectionDialog.allLabelsInOrder()[1]
            ActionSelectionDialog.dragRowToTop(second)
            assertEquals(
                "a drag must not be able to displace the exit row from the top",
                exitLabel,
                ActionSelectionDialog.firstVisibleRow().label,
            )

            ActionSelectionDialog.apply()
            ActionSelectionDialog.awaitDismissed()

            val selected = ActionsPreferences.fullScreenOverlayActionIds().orEmpty()
            assertTrue(
                "exit fullscreen must survive in the stored selection, it reads $selected",
                ConfigurableAction.EXIT_FULLSCREEN.id in selected,
            )
            ActionsPreferences.awaitOrderStartsWith(
                Preferences.fullScreenOverlayActionOrderKey,
                listOf(ConfigurableAction.EXIT_FULLSCREEN.id),
            )
        }
    }

    @Test
    @TestId("ACT-32")
    @TestDuration(DurationCategory.SHORT)
    fun theOverlayShowsExactlyTheConfiguredButtonsInTheConfiguredOrder() {
        ActionsPreferences.seedFullScreenOverlay(
            selected = listOf(
                ConfigurableAction.EXIT_FULLSCREEN,
                ConfigurableAction.SCREENSHOT,
                ConfigurableAction.TABLE_OF_CONTENTS,
            ),
            order = listOf(
                ConfigurableAction.EXIT_FULLSCREEN,
                ConfigurableAction.TABLE_OF_CONTENTS,
                ConfigurableAction.SCREENSHOT,
            ),
        )

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()

            act.enterFullscreen()
            act.revealOverlayColumn()
            FullScreenOverlay.awaitColumnVisible()

            FullScreenOverlay.awaitLabels(
                listOf(
                    ActionLabels.string(R.string.exit_fullscreen_button),
                    ActionLabels.of(ConfigurableAction.TABLE_OF_CONTENTS),
                    ActionLabels.of(ConfigurableAction.SCREENSHOT),
                )
            )
            assertEquals(
                "only the three configured buttons are built",
                3,
                FullScreenOverlay.visibleCount(),
            )

            FullScreenOverlay.assertNoLabel(ActionLabels.string(R.string.brightness))
            FullScreenOverlay.assertNoLabel(ActionLabels.string(R.string.auto_scroll))
            assertFalse(
                "the brightness slider must not be on screen when brightness is deselected",
                FullScreenOverlay.isBrightnessControlVisible(),
            )
        }
    }
}
