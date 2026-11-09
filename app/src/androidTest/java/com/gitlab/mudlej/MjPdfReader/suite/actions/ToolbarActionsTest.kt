// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
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
@TestDuration(DurationCategory.SHORT)
class ToolbarActionsTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()
    private val reader = act.reader()

    @Test
    @TestId("ACT-01")
    fun theDefaultToolbarCarriesFullscreenAndTheActionsMenuAndNoDeadSecondButton() {
        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, label(ConfigurableAction.FULLSCREEN))

            assertEquals(
                "the always visible toolbar button runs the shipped default action",
                label(ConfigurableAction.FULLSCREEN),
                ToolbarItems.labelOf(ToolbarItems.primaryId),
            )
            assertEquals(
                "the actions menu is always offered",
                TestEnvironment.targetString(R.string.actions),
                ToolbarItems.labelOf(ToolbarItems.actionsId),
            )
            ToolbarItems.assertNoItemLabelled(label(ConfigurableAction.RELOAD))
            ActionsPreferences.assertUnset(Preferences.secondaryButtonActionKey)
        }
    }

    @Test
    @TestId("ACT-02")
    fun theToolbarFullscreenButtonEntersFullscreenRatherThanOnlyLookingRight() {
        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, label(ConfigurableAction.FULLSCREEN))
            ToolbarItems.tapPrimary()

            act.awaitActionBarHidden()
            assertFalse(
                "entering fullscreen hides the overlay column rather than showing it",
                FullScreenOverlay.isColumnVisible(),
            )

            act.revealOverlayColumn()
            FullScreenOverlay.awaitColumnVisible()

            assertTrue(
                "the revealed column must offer a way out of fullscreen, it shows " +
                    FullScreenOverlay.visibleLabels(),
                FullScreenOverlay.hasLabel(TestEnvironment.targetString(R.string.exit_fullscreen_button)),
            )
            assertEquals(
                "the exit control carries the exit string as its visible text",
                ActionLabels.exitFullscreen(),
                act.overlayButtonText(R.id.exitFullScreenButton),
            )
            ShortcutBar.assertStaysHidden()
        }
    }

    @Test
    @TestId("ACT-03")
    fun aReconfiguredPrimaryButtonChangesBothItsLabelAndItsEffect() {
        ActionsPreferences.seedPrimaryAction(ConfigurableAction.SCREENSHOT)

        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, label(ConfigurableAction.SCREENSHOT))
            ToolbarItems.assertNoItemLabelled(label(ConfigurableAction.FULLSCREEN))

            ToolbarItems.tapPrimary()
            Snackbars.awaitText(TestEnvironment.targetString(R.string.screenshot_saved))

            val displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ScreenshotOracle.awaitSingleRow(screenshotPrefix()).displayName
            } else {
                ScreenshotOracle.awaitSinglePublicPicture(screenshotPrefix()).name
            }
            assertTrue(
                "the capture lands in the gallery as a jpg named after the document, it reads $displayName",
                displayName.endsWith(JPEG_SUFFIX),
            )
        }
    }

    @Test
    @TestId("ACT-04")
    fun theLegacyReloadSwitchStillFillsTheSecondaryToolbarSlot() {
        ActionsPreferences.seedLegacyReloadButton(true)
        ActionsPreferences.assertUnset(Preferences.secondaryButtonActionKey)

        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.secondaryId, label(ConfigurableAction.RELOAD))

            assertEquals(
                "an upgrading user keeps the reload button the old switch gave them",
                label(ConfigurableAction.RELOAD),
                ToolbarItems.labelOf(ToolbarItems.secondaryId),
            )
            ActionsPreferences.assertUnset(Preferences.secondaryButtonActionKey)
        }
    }

    @Test
    @TestId("ACT-05")
    fun anExplicitSecondaryActionBeatsTheLegacyReloadSwitch() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.enableReloadButtonKey to true,
                Preferences.secondaryButtonActionKey to ConfigurableAction.NONE.id,
            )
        )

        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, label(ConfigurableAction.FULLSCREEN))

            ToolbarItems.assertNoItemLabelled(label(ConfigurableAction.RELOAD))
            ToolbarItems.assertStaysHidden(ToolbarItems.secondaryId)
            assertEquals(
                "the actions menu survives an explicit none in the secondary slot",
                TestEnvironment.targetString(R.string.actions),
                ToolbarItems.labelOf(ToolbarItems.actionsId),
            )
        }
    }

    @Test
    @TestId("ACT-06")
    fun aToolbarButtonWhoseActionCannotRunIsHiddenRatherThanShownAndInert() {
        ActionsPreferences.seedPrimaryAction(ConfigurableAction.READING_DIRECTION)
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = false, dualPage = false)

        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.actionsId, TestEnvironment.targetString(R.string.actions))
            ToolbarItems.assertNoItemLabelled(label(ConfigurableAction.READING_DIRECTION))
            ToolbarItems.assertStaysHidden(ToolbarItems.primaryId)
        }

        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = true, dualPage = false)

        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, label(ConfigurableAction.READING_DIRECTION))

            assertEquals(
                "reading direction becomes reachable once horizontal scrolling is on",
                label(ConfigurableAction.READING_DIRECTION),
                ToolbarItems.labelOf(ToolbarItems.primaryId),
            )
        }
    }

    @Test
    @TestId("ACT-07")
    @TestDuration(DurationCategory.NORMAL)
    fun theBackButtonAppearsOnlyOnceAJumpHasCreatedSomewhereToGoBackTo() {
        ActionsPreferences.seedPrimaryAction(ConfigurableAction.NAV_BACK)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.TOC))).use {
            act.awaitReady()
            reader.awaitPageIndex(0)

            ToolbarItems.awaitLabel(ToolbarItems.actionsId, TestEnvironment.targetString(R.string.actions))
            ToolbarItems.assertNoItemLabelled(label(ConfigurableAction.NAV_BACK))

            act.runFromActionsDialog(label(ConfigurableAction.TABLE_OF_CONTENTS))
            ActionsTableOfContents.tapEntry(ActionsFixtures.TOC_RESULTS_ENTRY)

            act.awaitReaderResumed()
            reader.awaitPageIndex(ActionsFixtures.TOC_RESULTS_PAGE_INDEX)
            reader.awaitRenderIdle()

            ToolbarItems.awaitLabel(ToolbarItems.primaryId, label(ConfigurableAction.NAV_BACK))
            ToolbarItems.tapPrimary()

            val state = reader.awaitPageIndex(0)
            assertEquals("back returns the reader to the page the jump left", 0 as Int?, state.page)
        }
    }

    @Test
    @TestId("ACT-08")
    fun theCropMarginsToolbarButtonReportsItsCurrentStateInsteadOfOneFixedWording() {
        ActionsPreferences.seedPrimaryAction(ConfigurableAction.CROP_MARGINS)

        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.cropMargins(cropped = false))

            ToolbarItems.tapPrimary()
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.cropMargins(cropped = true))
            reader.awaitRenderIdle()

            ToolbarItems.tapPrimary()
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.cropMargins(cropped = false))
            reader.awaitRenderIdle()
        }
    }

    @Test
    @TestId("ACT-09")
    fun theBookmarkToolbarButtonFlipsItsLabelWithThePagesBookmarkState() {
        ActionsPreferences.seedPrimaryAction(ConfigurableAction.BOOKMARK_PAGE)

        act.openReader {
            reader.awaitFileHashResolved()
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.bookmark(currentPageBookmarked = false))

            ToolbarItems.tapPrimary()
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.bookmark(currentPageBookmarked = true))

            ToolbarItems.tapPrimary()
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.bookmark(currentPageBookmarked = false))
        }
    }

    @Test
    @TestId("ACT-10")
    fun switchThemeStaysReachableWhileThePdfThemeFollowsTheSystem() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.pdfPagesThemeKey to Preferences.themeSystem,
                Preferences.primaryButtonActionKey to ConfigurableAction.SWITCH_THEME.id,
            )
        )

        act.openReader {
            ToolbarItems.awaitShown(ToolbarItems.primaryId)

            val shown = ToolbarItems.labelOf(ToolbarItems.primaryId)
            assertTrue(
                "the switch theme button must be offered on the follows system theme, it reads \"$shown\"",
                shown == ActionLabels.switchTheme(pdfDarkTheme = false) ||
                    shown == ActionLabels.switchTheme(pdfDarkTheme = true),
            )

            ToolbarItems.tapPrimary()
            act.awaitDialogText(TestEnvironment.targetString(R.string.dark_theme_for_pdf))
            act.awaitDialogText(TestEnvironment.targetString(R.string.pdf_theme_follows_system_dialog))

            onView(withId(R.id.theme_choice_dark)).inRoot(isDialog()).perform(click())

            ActionsPreferences.awaitString(Preferences.pdfPagesThemeKey, Preferences.themeDark)
        }
    }

    @Test
    @TestId("ACT-11")
    @TestDuration(DurationCategory.NORMAL)
    fun anUnknownStoredActionIdLeavesNoButtonRatherThanADeadOne() {
        ActionsPreferences.seedPrimaryActionId(UNKNOWN_ACTION_ID)

        act.openReader {
            ToolbarItems.awaitLabel(ToolbarItems.actionsId, TestEnvironment.targetString(R.string.actions))
            ToolbarItems.assertStaysHidden(ToolbarItems.primaryId)
            ToolbarItems.assertStaysHidden(ToolbarItems.secondaryId)

            act.actionsDialog.open()
            act.actionsDialog.awaitTile(label(ConfigurableAction.FULLSCREEN))
            act.actionsDialog.dismiss()
        }

        ActivityScenario.launch<SettingsActivity>(ActionsSettings.customizeControlsIntent()).use {
            ActionsSettings.awaitResumed()
            ActionsSettings.awaitRowSummaryContains(
                TestEnvironment.targetString(R.string.primary_button_action),
                label(ConfigurableAction.FULLSCREEN),
            )
        }

        assertEquals(
            "the unknown value stays stored until the user picks something, the picker only falls back for display",
            UNKNOWN_ACTION_ID,
            ActionsPreferences.primaryActionId(),
        )
    }

    private fun screenshotPrefix(): String = ActionsFixtures.screenshotPrefix(ActionsFixtures.NORMAL)

    private fun label(action: ConfigurableAction): String = ActionLabels.of(action)

    private companion object {
        const val UNKNOWN_ACTION_ID = "not_a_real_action"
        const val JPEG_SUFFIX = ".jpg"
    }
}
