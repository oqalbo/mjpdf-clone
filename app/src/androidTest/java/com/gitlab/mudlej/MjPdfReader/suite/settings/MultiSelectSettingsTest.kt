// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class MultiSelectSettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-45")
    fun eachListCardBadgeKeepsItsOwnKey() {
        launchSettingsOn(SettingsPage.HOME).use {
            openMultiChoice(SettingsStrings.of(R.string.home_list_badges_title))

            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.home_badge_pages), false)
            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.home_badge_file_size), false)
            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.home_badge_status), false)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitBoolean(Preferences.homeBadgePagesKey, false)
            SettingsPrefs.assertBoolean(Preferences.homeBadgeFileSizeKey, false)
            SettingsPrefs.assertBoolean(Preferences.homeBadgeStatusKey, false)
            SettingsPrefs.assertBoolean(Preferences.homeBadgeProgressKey, true)
            SettingsPrefs.assertBoolean(Preferences.homeBadgeLastOpenedKey, true)

            openMultiChoice(SettingsStrings.of(R.string.home_list_badges_title))
            assertEquals(
                "the reopened dialog renders the five stored states",
                setOf(
                    SettingsStrings.of(R.string.home_badge_progress),
                    SettingsStrings.of(R.string.home_badge_last_opened),
                ),
                SettingsDialog.checkedChoiceLabels(),
            )
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
        }
    }

    @Test
    @TestId("SET-46")
    fun theScrollingInfoCardContentsAreIndividuallyControllable() {
        launchSettingsOn(SettingsPage.APPEARANCE).use {
            openMultiChoice(SettingsStrings.of(R.string.scrolling_info_card))

            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.scrolling_info_time), true)
            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.scrolling_info_pdf_name), true)
            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.scrolling_info_page_number), false)
            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.scrolling_info_reading_percentage), false)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitBoolean(Preferences.fullScreenInfoShowTimeKey, true)
            SettingsPrefs.assertBoolean(Preferences.fullScreenInfoShowPdfNameKey, true)
            SettingsPrefs.assertBoolean(Preferences.fullScreenInfoShowPageNumberKey, false)
            SettingsPrefs.assertBoolean(Preferences.fullScreenInfoShowReadingPercentageKey, false)
        }
    }

    @Test
    @TestId("SET-47")
    @TestDuration(DurationCategory.LONG)
    fun cancelMeansCancelInEveryMultiSelectDialog() {
        launchSettingsOn(SettingsPage.HOME).use {
            openMultiChoice(SettingsStrings.of(R.string.home_list_badges_title))
            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.home_badge_pages), false)
            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.home_badge_progress), false)
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
            backToRoot()

            SettingsScreen.openPage(SettingsPage.APPEARANCE)
            openMultiChoice(SettingsStrings.of(R.string.scrolling_info_card))
            SettingsDialog.setChoiceChecked(SettingsStrings.of(R.string.scrolling_info_time), true)
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
            backToRoot()

            SettingsScreen.openPage(SettingsPage.CUSTOMIZE_CONTROLS)
            openRowDialog(SettingsStrings.of(R.string.shortcut_bar_buttons))
            val shortcutRows = SettingsDialog.allRowLabelsInOrder()
            SettingsDialog.setRecyclerRowChecked(shortcutRows[0], !SettingsDialog.isRecyclerRowChecked(shortcutRows[0]))
            SettingsDialog.setRecyclerRowChecked(shortcutRows[1], !SettingsDialog.isRecyclerRowChecked(shortcutRows[1]))
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
            backToRoot()

            SettingsScreen.openPage(SettingsPage.HIGHLIGHTING)
            openRowDialog(SettingsStrings.of(R.string.highlight_colors))
            SettingsDialog.setRecyclerRowChecked(colorLabel(HighlightPalette.PURPLE), false)
            SettingsDialog.setRecyclerRowChecked(colorLabel(HighlightPalette.TEAL), true)
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.assertUnset(
                Preferences.homeBadgePagesKey,
                Preferences.homeBadgeProgressKey,
                Preferences.homeBadgeLastOpenedKey,
                Preferences.homeBadgeFileSizeKey,
                Preferences.homeBadgeStatusKey,
                Preferences.fullScreenInfoShowTimeKey,
                Preferences.fullScreenInfoShowPdfNameKey,
                Preferences.fullScreenInfoShowPageNumberKey,
                Preferences.fullScreenInfoShowReadingPercentageKey,
                Preferences.shortcutBarActionsKey,
                Preferences.shortcutBarActionOrderKey,
                Preferences.highlightColorsKey,
            )
        }
    }

    @Test
    @TestId("SET-48")
    fun theHighlightStripApplyIsGatedOnExactlySixColours() {
        launchSettingsOn(SettingsPage.HIGHLIGHTING).use {
            openRowDialog(SettingsStrings.of(R.string.highlight_colors))

            HighlightPalette.defaultSelection.forEach { colour ->
                assertTrue(
                    "the dialog opens with ${colour.name} ticked",
                    SettingsDialog.isRecyclerRowChecked(colorLabel(colour)),
                )
            }
            assertTrue("Apply is enabled at six colours", SettingsDialog.isButtonEnabled(SettingsDialog.POSITIVE))

            SettingsDialog.setRecyclerRowChecked(colorLabel(HighlightPalette.PURPLE), false)
            SettingsDialog.awaitButtonEnabled(SettingsDialog.POSITIVE, false)

            SettingsDialog.setRecyclerRowChecked(colorLabel(HighlightPalette.TEAL), true)
            SettingsDialog.awaitButtonEnabled(SettingsDialog.POSITIVE, true)

            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(
                Preferences.highlightColorsKey,
                "YELLOW,ORANGE,PINK_RED,BLUE,GREEN,TEAL",
            )
        }
    }

    @Test
    @TestId("SET-49")
    @TestDuration(DurationCategory.SHORT)
    fun aSeventhHighlightColourIsRefusedWithTheLimitToast() {
        launchSettingsOn(SettingsPage.HIGHLIGHTING).use {
            openRowDialog(SettingsStrings.of(R.string.highlight_colors))

            val cyan = colorLabel(HighlightPalette.CYAN)
            assertFalse("Cyan starts unticked", SettingsDialog.isRecyclerRowChecked(cyan))

            SettingsDialog.tapRecyclerRow(cyan)

            Toasts.awaitText(
                SettingsStrings.of(R.string.highlight_colors_max_toast, Preferences.highlightColorsCount)
            )
            assertFalse("the seventh colour stays unticked", SettingsDialog.isRecyclerRowChecked(cyan))
            assertTrue("Apply stays enabled at six colours", SettingsDialog.isButtonEnabled(SettingsDialog.POSITIVE))
            SettingsPrefs.assertUnset(Preferences.highlightColorsKey)
        }
    }

    @Test
    @TestId("SET-50")
    fun resetRestoresTheSixShippedColoursInTheRowOrderApplyWrites() {
        launchSettingsOn(SettingsPage.HIGHLIGHTING).use {
            openRowDialog(SettingsStrings.of(R.string.highlight_colors))
            SettingsDialog.setRecyclerRowChecked(colorLabel(HighlightPalette.PURPLE), false)
            SettingsDialog.setRecyclerRowChecked(colorLabel(HighlightPalette.TEAL), true)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitString(Preferences.highlightColorsKey, "YELLOW,ORANGE,PINK_RED,BLUE,GREEN,TEAL")

            openRowDialog(SettingsStrings.of(R.string.highlight_colors))
            SettingsDialog.reset()
            SettingsDialog.awaitRecyclerRowChecked(colorLabel(HighlightPalette.PURPLE), true)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(
                Preferences.highlightColorsKey,
                "YELLOW,ORANGE,PINK_RED,BLUE,GREEN,PURPLE",
            )
            assertFalse(
                "GRAY is excluded from the selectable rows, so it can never be stored",
                SettingsPrefs.string(Preferences.highlightColorsKey).orEmpty().contains(HighlightPalette.GRAY.name),
            )
        }
    }

    private fun openMultiChoice(rowTitle: String) {
        SettingsScreen.tapRow(rowTitle)
        SettingsDialog.awaitChoiceList()
    }

    private fun openRowDialog(rowTitle: String) {
        SettingsScreen.tapRow(rowTitle)
        SettingsDialog.awaitShown()
        SettingsDialog.awaitRowList()
    }

    private fun colorLabel(colour: HighlightPalette): String = SettingsStrings.of(colour.nameRes)
}
