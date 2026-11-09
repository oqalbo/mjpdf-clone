// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ActionsDialogTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()
    private val reader = act.reader()
    private val dialog = act.actionsDialog
    private val picker = DocumentPickerRobot()

    @Test
    @TestId("ACT-12")
    fun theBurgerIconOpensOneDialogCarryingTheWholeActionSurface() {
        act.openReader {
            dialog.open()

            listOf(
                ConfigurableAction.OPEN_LOCAL,
                ConfigurableAction.SHARE,
                ConfigurableAction.PRINT,
                ConfigurableAction.SCREENSHOT,
                ConfigurableAction.TABLE_OF_CONTENTS,
                ConfigurableAction.SETTINGS,
                ConfigurableAction.ABOUT,
            ).forEach { action -> dialog.awaitTile(ActionLabels.of(action)) }

            dialog.awaitTile(ActionLabels.toggleShortcuts())

            assertEquals(
                "the grid is built as two sections, the actions section and the pages section",
                2,
                dialog.sections().size,
            )
        }
    }

    @Test
    @TestId("ACT-13")
    fun aTileDismissesTheDialogBeforeItsActionPutsAnythingOnScreen() {
        act.openReader {
            dialog.open()
            dialog.tap(ActionLabels.of(ConfigurableAction.FILE_METADATA))

            MetadataDialog.awaitShown()
            dialog.assertNoTile(ActionLabels.of(ConfigurableAction.FILE_METADATA))

            assertEquals(
                "the file properties dialog reports the fixture's real page count",
                ActionsFixtures.NORMAL_PAGES.toString(),
                MetadataDialog.pageCountText(),
            )
        }
    }

    @Test
    @TestId("ACT-14")
    @TestDuration(DurationCategory.NORMAL)
    fun withNothingOpenTheDialogOffersOnlyWaysToOpenSomething() {
        ActivityScenario.launch<MainActivity>(ActionsRobot.launchWithoutDocument()).use {
            picker.awaitShown()
            picker.cancel()
            act.awaitReaderWithoutDocument()

            dialog.open()

            listOf(
                ConfigurableAction.OPEN_LOCAL,
                ConfigurableAction.OPEN_ONLINE,
                ConfigurableAction.SETTINGS,
                ConfigurableAction.ABOUT,
            ).forEach { action -> dialog.awaitTile(ActionLabels.of(action)) }

            listOf(
                ConfigurableAction.SCREENSHOT,
                ConfigurableAction.SHARE,
                ConfigurableAction.PRINT,
                ConfigurableAction.TABLE_OF_CONTENTS,
                ConfigurableAction.EXTRACT_TEXT,
            ).forEach { action -> dialog.assertNoTile(ActionLabels.of(action)) }

            dialog.assertNoTile(ActionLabels.toggleShortcuts())
            ShortcutBar.assertStaysHidden()
        }
    }

    @Test
    @TestId("ACT-15")
    fun navigationTilesAreShownDisabledRatherThanRemovedWhileHistoryIsEmpty() {
        act.openReader {
            dialog.open()

            val navigationActions = listOf(
                ConfigurableAction.NAV_BACK,
                ConfigurableAction.NAV_FORWARD,
                ConfigurableAction.NAV_HISTORY,
            )
            navigationActions.forEach { action ->
                val label = ActionLabels.of(action)
                dialog.awaitTile(label)
                assertFalse("the $label tile must not be clickable with empty history", dialog.tileIsClickable(label))
                assertEquals(
                    "the $label tile must read as unavailable through its alpha",
                    ActionsDialogRobot.DISABLED_ALPHA,
                    dialog.tileAlpha(label),
                    ALPHA_TOLERANCE,
                )
            }

            val pageBefore = reader.readState().page
            dialog.tap(ActionLabels.of(ConfigurableAction.NAV_BACK))

            assertTrue("tapping a disabled tile must leave the dialog open", dialog.isShown())
            assertEquals("tapping a disabled tile must not move the reader", pageBefore, reader.readState().page)
        }
    }

    @Test
    @TestId("ACT-16")
    @TestDuration(DurationCategory.NORMAL)
    fun dualPageAndReadingDirectionAreNeverBothOffered() {
        ActionsPreferences.seedShortcutBar(
            selected = listOf(ConfigurableAction.DUAL_PAGE, ConfigurableAction.READING_DIRECTION),
            order = listOf(ConfigurableAction.DUAL_PAGE, ConfigurableAction.READING_DIRECTION),
        )
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = false, dualPage = false)

        act.openReader {
            dialog.open()
            dialog.assertNoTile(ActionLabels.of(ConfigurableAction.READING_DIRECTION))
            dialog.dismiss()

            ShortcutBar.awaitLabels(listOf(ActionLabels.dualPage(enabled = false)))
            ShortcutBar.assertNoLabel(ActionLabels.of(ConfigurableAction.READING_DIRECTION))
        }

        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = true, dualPage = false)

        act.openReader {
            dialog.open()
            dialog.awaitTile(ActionLabels.of(ConfigurableAction.READING_DIRECTION))
            dialog.dismiss()

            ShortcutBar.awaitLabels(listOf(ActionLabels.of(ConfigurableAction.READING_DIRECTION)))
            ShortcutBar.assertNoLabel(ActionLabels.dualPage(enabled = false))
            ShortcutBar.assertNoLabel(ActionLabels.dualPage(enabled = true))
        }
    }

    @Test
    @TestId("ACT-17")
    fun theToggleShortcutsTileWritesTheSamePreferenceTheSettingsScreenUses() {
        act.openReader {
            dialog.open()
            dialog.tap(ActionLabels.toggleShortcuts())

            ShortcutBar.awaitVisible()
            ActionsPreferences.awaitBoolean(Preferences.secondBarEnabledKey, true)

            dialog.open()
            dialog.tap(ActionLabels.toggleShortcuts())

            ShortcutBar.awaitHidden()
            ActionsPreferences.awaitBoolean(Preferences.secondBarEnabledKey, false)
        }
    }

    @Test
    @TestId("ACT-54")
    fun theActionGridStaysUsableAcrossARotationBecauseTheReaderHandlesTheChangeItself() {
        act.openReader {
            dialog.open()
            val labelsBefore = dialog.tileLabels()
            val activityBefore = act.activity()

            TestEnvironment.uiDevice.setOrientationLeft()
            reader.awaitState("the reader reports a landscape viewport") { state ->
                val width = state.viewWidth
                val height = state.viewHeight
                width != null && height != null && width > height
            }
            Await.until("the rotated actions dialog settles with its tiles measured") {
                runCatching { dialog.tileLabels().isNotEmpty() }.getOrDefault(false)
            }

            assertTrue(
                "MainActivity declares configChanges for orientation, so the same instance must answer after the turn",
                act.activity() === activityBefore,
            )
            assertTrue("the dialog must still be showing after the turn", dialog.isShown())
            assertEquals(
                "the already inflated grid must be re-measured, not rebuilt with different contents",
                labelsBefore,
                dialog.tileLabels(),
            )

            val decor = dialog.dialogDecorBounds()
            labelsBefore.forEach { label ->
                val tile = dialog.tileBounds(label)
                assertTrue(
                    "the tile \"$label\" at $tile is clipped by the dialog bounds $decor after the rotation",
                    decor.contains(tile),
                )
            }

            dialog.tap(ActionLabels.of(ConfigurableAction.GO_TO_PAGE))
            GoToPageEntry.awaitShown()
            assertEquals(
                "the go to page entry opens on the page the reader is showing, one based",
                (reader.readState().page ?: -1) + 1,
                GoToPageEntry.currentEntryPage(),
            )
        }
    }

    @Test
    @TestId("ACT-56")
    fun theDualPageActionWritesTheGlobalPreferenceUsedByTheNextDocument() {
        ActionsPreferences.seedShortcutBar(
            selected = listOf(ConfigurableAction.DUAL_PAGE),
        )
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = false, dualPage = false)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()
            assertEquals("the first document starts with one page per row", 1 as Int?, reader.readState().pagesPerRow)

            ShortcutBar.awaitLabels(listOf(ActionLabels.dualPage(enabled = false)))
            ShortcutBar.tap(ActionLabels.dualPage(enabled = false))

            ActionsPreferences.awaitBoolean(Preferences.dualPageModeKey, true)
            reader.awaitState("the first document reloads into dual-page mode") { state ->
                state.pagesPerRow == 2
            }
        }

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.EIGHT_PAGE))).use {
            val ready = act.awaitReady()
            assertEquals(
                "a different document inherits the global dual-page preference without another action",
                2 as Int?,
                ready.pagesPerRow,
            )
        }
    }

    private companion object {
        const val ALPHA_TOLERANCE = 0.001f
    }
}
