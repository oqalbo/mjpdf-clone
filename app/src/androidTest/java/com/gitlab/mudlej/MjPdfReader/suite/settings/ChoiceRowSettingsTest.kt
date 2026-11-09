// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeProgressStyle
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTitleEllipsize
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ChoiceRowSettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-29")
    fun theHideDelayPickerOffersFourValuesAndStoresMilliseconds() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openChoiceDialog(hideDelayTitle)

            assertEquals(
                "the picker offers exactly the four supported delays",
                listOf(2000, 3000, 5000, 10000).map(SettingsStrings::hideDelayLabel),
                SettingsDialog.choiceLabels(),
            )
            assertEquals(
                "the shipped default is the checked row",
                SettingsStrings.hideDelayLabel(Preferences.hideDelayDefault),
                SettingsDialog.checkedChoiceLabelOrNull(),
            )

            SettingsDialog.pickChoice(SettingsStrings.hideDelayLabel(10000))
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitInt(Preferences.hideDelayKey, 10000)
            SettingsScreen.awaitRowSummaryIs(hideDelayTitle, SettingsStrings.hideDelayLabel(10000))

            openChoiceDialog(hideDelayTitle)
            SettingsDialog.reset()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitInt(Preferences.hideDelayKey, Preferences.hideDelayDefault)
        }
    }

    @Test
    @TestId("SET-30")
    fun aStoredHideDelayOutsideTheOfferedListShowsNoValueAndCanBeRepaired() {
        SettingsSeeder.seed(Preferences.hideDelayKey, 4000)

        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            assertTrue(
                "labels.getOrNull(-1) is null, so the row shows no current value at all",
                SettingsScreen.awaitRow(hideDelayTitle).summaryOrEmpty().isBlank(),
            )

            openChoiceDialog(hideDelayTitle)
            assertEquals(
                "no row can be checked for a value the picker does not offer",
                emptySet<String>(),
                SettingsDialog.checkedChoiceLabels(),
            )

            SettingsDialog.pickChoice(SettingsStrings.hideDelayLabel(5000))
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitInt(Preferences.hideDelayKey, 5000)
            SettingsScreen.awaitRowSummaryIs(hideDelayTitle, SettingsStrings.hideDelayLabel(5000))
        }
    }

    @Test
    @TestId("SET-31")
    @TestDuration(DurationCategory.NORMAL)
    fun theHomeCardAppearanceChoicesAreStoredUnderTheKeysTheAdapterReads() {
        launchSettingsOn(SettingsPage.HOME).use {
            pick(R.string.home_progress_style_title, SettingsStrings.of(R.string.home_progress_style_bar))
            SettingsPrefs.awaitString(Preferences.homeProgressStyleKey, HomeProgressStyle.BAR.name)

            pick(R.string.home_grid_title_lines_title, SettingsStrings.titleLinesLabel(4))
            SettingsPrefs.awaitInt(Preferences.homeGridTitleLinesKey, 4)

            pick(R.string.home_list_title_lines_title, SettingsStrings.titleLinesLabel(1))
            SettingsPrefs.awaitInt(Preferences.homeListTitleLinesKey, 1)

            pick(R.string.home_title_ellipsize_title, SettingsStrings.of(R.string.home_title_ellipsize_middle))
            SettingsPrefs.awaitString(Preferences.homeTitleEllipsizeKey, HomeTitleEllipsize.MIDDLE.name)

            assertEquals(
                "the page never left the screen while the four choices were made",
                SettingsStrings.pageTitle(SettingsPage.HOME),
                SettingsScreen.title(),
            )
        }
    }

    @Test
    @TestId("SET-32")
    fun theKeepSharedCopiesModeIsStoredAsTheEnumNameTheIntakeFlowReads() {
        launchSettingsOn(SettingsPage.HISTORY_PRIVACY).use {
            openChoiceDialog(sharedCopyTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.shared_copy_mode_ask))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.sharedCopyModeKey, SharedCopyMode.ASK.name)
            SettingsScreen.awaitRowSummaryIs(
                sharedCopyTitle,
                SettingsStrings.of(R.string.shared_copy_mode_ask) + "\n" +
                    SettingsStrings.of(R.string.shared_copy_mode_ask_summary),
            )

            openChoiceDialog(sharedCopyTitle)
            SettingsDialog.reset()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitString(Preferences.sharedCopyModeKey, SharedCopyMode.ALWAYS_COPY.name)
        }
    }

    private fun pick(rowTitleRes: Int, label: String) {
        val title = SettingsStrings.of(rowTitleRes)
        openChoiceDialog(title)
        SettingsDialog.pickChoice(label)
        SettingsDialog.awaitDismissed()
        SettingsScreen.awaitRowSummaryIs(title, label)
    }

    private val hideDelayTitle: String get() = SettingsStrings.of(R.string.hide_delay_title)
    private val sharedCopyTitle: String get() = SettingsStrings.of(R.string.keep_shared_copies_title)
}
