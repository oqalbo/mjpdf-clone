// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ShortcutBarTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()
    private val reader = act.reader()

    @Test
    @TestId("ACT-18")
    fun aCleanInstallShowsNoShortcutBarEvenThoughSevenActionsAreSelectedForIt() {
        act.openReader {
            ShortcutBar.assertStaysHidden()
            ActionsPreferences.assertUnset(Preferences.secondBarEnabledKey)
            assertEquals(
                "a clean install resolves seven shortcut bar actions even while the bar itself is off",
                ActionsPreferences.DEFAULT_SHORTCUT_BAR_SIZE,
                Preferences(SettingsSeeder.preferences).getShortcutBarActions().size,
            )
        }
    }

    @Test
    @TestId("ACT-19")
    fun theEnabledShortcutBarIsExactlyTheShippedDefaultSetInTheShippedOrder() {
        ActionsPreferences.seedShortcutBarEnabled(true)

        act.openReader {
            ShortcutBar.awaitVisible()
            ShortcutBar.awaitChildCount(ActionsPreferences.DEFAULT_SHORTCUT_BAR_SIZE)
            ShortcutBar.awaitLabels(
                listOf(
                    ActionLabels.switchTheme(pdfDarkTheme = false),
                    ActionLabels.of(ConfigurableAction.TABLE_OF_CONTENTS),
                    ActionLabels.of(ConfigurableAction.SEARCH),
                    ActionLabels.of(ConfigurableAction.TEXT_MODE),
                    ActionLabels.of(ConfigurableAction.GO_TO_PAGE),
                    ActionLabels.bookmark(currentPageBookmarked = false),
                    ActionLabels.of(ConfigurableAction.SETTINGS),
                )
            )
        }
    }

    @Test
    @TestId("ACT-20")
    fun aCustomSelectionAndOrderIsWhatTheReaderBuilds() {
        ActionsPreferences.seedShortcutBar(
            selected = listOf(
                ConfigurableAction.PRINT,
                ConfigurableAction.SHARE,
                ConfigurableAction.SCREENSHOT,
            ),
            order = listOf(
                ConfigurableAction.SCREENSHOT,
                ConfigurableAction.SHARE,
                ConfigurableAction.PRINT,
            ),
        )

        act.openReader {
            ShortcutBar.awaitVisible()
            ShortcutBar.awaitChildCount(3)
            ShortcutBar.awaitLabels(
                listOf(
                    ActionLabels.of(ConfigurableAction.SCREENSHOT),
                    ActionLabels.of(ConfigurableAction.SHARE),
                    ActionLabels.of(ConfigurableAction.PRINT),
                )
            )
        }
    }

    @Test
    @TestId("ACT-21")
    fun aShortcutBarButtonRunsItsRealHandler() {
        ActionsPreferences.seedShortcutBarEnabled(true)

        act.openReader {
            ShortcutBar.awaitVisible()
            reader.awaitPageIndex(0)

            ShortcutBar.tap(ActionLabels.of(ConfigurableAction.GO_TO_PAGE))

            GoToPageEntry.awaitShown()
            assertEquals(
                "the go to page entry opens on the page the reader is showing, one based",
                (reader.readState().page ?: -1) + 1,
                GoToPageEntry.currentEntryPage(),
            )
            assertEquals(
                "the entry names the open document's length",
                ActionsFixtures.NORMAL_PAGES,
                GoToPageEntry.pageCount(),
            )
        }
    }

    @Test
    @TestId("ACT-22")
    fun aLongPressNamesAnIconOnlyShortcutInsteadOfRunningIt() {
        ActionsPreferences.seedShortcutBarEnabled(true)

        act.openReader {
            ShortcutBar.awaitVisible()

            ShortcutBar.longPress(ActionLabels.of(ConfigurableAction.SEARCH))

            Toasts.awaitText(ActionLabels.of(ConfigurableAction.SEARCH))
            assertFalse(
                "the long press must be consumed, so the search dialog must not have opened",
                anyTextEntryDialogShown(),
            )
        }
    }

    @Test
    @TestId("ACT-23")
    fun anUnavailableActionIsDroppedFromTheBarRatherThanBuiltAndRefused() {
        ActionsPreferences.seedShortcutBar(
            selected = listOf(
                ConfigurableAction.READING_DIRECTION,
                ConfigurableAction.DUAL_PAGE,
                ConfigurableAction.SEARCH,
            ),
            order = listOf(
                ConfigurableAction.READING_DIRECTION,
                ConfigurableAction.DUAL_PAGE,
                ConfigurableAction.SEARCH,
            ),
        )
        SettingsSeeder.seedReadingMode(
            singlePage = false,
            horizontalScroll = false,
            dualPage = false,
        )

        act.openReader {
            ShortcutBar.awaitVisible()
            ShortcutBar.awaitChildCount(2)
            ShortcutBar.awaitLabels(
                listOf(
                    ActionLabels.dualPage(enabled = false),
                    ActionLabels.of(ConfigurableAction.SEARCH),
                )
            )
            ShortcutBar.assertNoLabel(ActionLabels.of(ConfigurableAction.READING_DIRECTION))
        }
    }

    @Test
    @TestId("ACT-24")
    fun anEnabledButEmptyBarTakesNoReadingSpace() {
        ActionsPreferences.seedEmptyShortcutBar()

        act.openReader {
            ShortcutBar.awaitChildCount(0)
            ShortcutBar.assertStaysHidden()
        }
    }

    @Test
    @TestId("ACT-25")
    fun fullscreenTakesTheBarAwayAndLeavingFullscreenGivesItBack() {
        ActionsPreferences.seedShortcutBarEnabled(true)

        act.openReader {
            ShortcutBar.awaitVisible()
            ShortcutBar.awaitChildCount(ActionsPreferences.DEFAULT_SHORTCUT_BAR_SIZE)
            val labelsBefore = ShortcutBar.labels()

            act.enterFullscreen()
            ShortcutBar.awaitHidden()
            assertEquals(
                "fullscreen hides the bar without turning the user's preference off",
                true,
                ActionsPreferences.shortcutBarEnabled(),
            )

            act.revealOverlayColumn()
            FullScreenOverlay.awaitColumnVisible()
            FullScreenOverlay.tap(ActionLabels.string(R.string.exit_fullscreen_button))

            act.awaitActionBarShown()
            ShortcutBar.awaitVisible()
            ShortcutBar.awaitChildCount(ActionsPreferences.DEFAULT_SHORTCUT_BAR_SIZE)
            assertEquals("leaving fullscreen restores the same bar", labelsBefore, ShortcutBar.labels())
        }
    }

    @Test
    @TestId("ACT-26")
    @TestDuration(DurationCategory.NORMAL)
    fun oneActionPlacedInTwoSurfacesWorksFromEachOfThemIndependently() {
        ActionsPreferences.seedPrimaryAction(ConfigurableAction.SCREENSHOT)
        ActionsPreferences.seedShortcutBar(selected = listOf(ConfigurableAction.SCREENSHOT))

        act.openReader {
            ShortcutBar.awaitVisible()
            val screenshotLabel = ActionLabels.of(ConfigurableAction.SCREENSHOT)

            ToolbarItems.awaitLabel(ToolbarItems.primaryId, screenshotLabel)
            val barLabels = ShortcutBar.labels()
            val toolbarLabel = ToolbarItems.labelOf(ToolbarItems.primaryId)
            assertTrue(
                "the same action must be built into both surfaces at once rather than moving between them, " +
                    "toolbar reads \"$toolbarLabel\" and the bar reads $barLabels",
                toolbarLabel == screenshotLabel && barLabels.contains(screenshotLabel),
            )

            ToolbarItems.tapPrimary()
            Snackbars.awaitText(ActionLabels.string(R.string.screenshot_saved))
            awaitScreenshotKeys(1)
            Snackbars.awaitGone()

            ShortcutBar.tap(screenshotLabel)
            Snackbars.awaitText(ActionLabels.string(R.string.screenshot_saved))

            val captures = awaitScreenshotKeys(2)
            assertEquals(
                "each surface writes its own capture",
                2,
                captures.distinct().size,
            )
        }
    }

    private fun anyTextEntryDialogShown(): Boolean =
        Dialogs.isAnyShowing() &&
            Dialogs.hasViewOnScreen(R.id.editText)

    private fun awaitScreenshotKeys(expectedCount: Int): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ScreenshotOracle.awaitRowCount(screenshotPrefix(), expectedCount).map { it.id.toString() }
        } else {
            ScreenshotOracle.awaitPublicPictureCount(screenshotPrefix(), expectedCount).map { it.canonicalPath }
        }

    private fun screenshotPrefix(): String = ActionsFixtures.screenshotPrefix(ActionsFixtures.NORMAL)
}
