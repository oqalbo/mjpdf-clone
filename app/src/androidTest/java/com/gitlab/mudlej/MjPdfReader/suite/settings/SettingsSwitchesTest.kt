// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SettingsSwitchesTest : SettingsTestCase() {

    @Test
    @TestId("SET-14")
    fun theHomePageSwitchesWriteTheKeysTheLibraryReads() {
        launchSettingsOn(SettingsPage.HOME).use {
            flip(R.string.disable_home_library_title, true)
            flip(R.string.home_show_pdf_title_title, false)

            SettingsPrefs.awaitBoolean(Preferences.homeDisabledKey, true)
            SettingsPrefs.awaitBoolean(Preferences.homeShowPdfTitleKey, false)
            SettingsPrefs.assertDeclaredKind(Preferences.homeDisabledKey)
            SettingsPrefs.assertDeclaredKind(Preferences.homeShowPdfTitleKey)
        }
    }

    @Test
    @TestId("SET-15")
    fun theAppearanceSwitchesMapOneToOneOntoTheirKeys() {
        launchSettingsOn(SettingsPage.APPEARANCE).use {
            flip(R.string.quality, true)
            flip(R.string.anti_aliasing, false)
            flip(R.string.space_between_pages, false)
            flip(R.string.always_hide_margins, true)
            flip(R.string.show_scroll_handle_page_count_title, true)
            flip(R.string.show_app_bar_page_count_title, true)

            SettingsPrefs.awaitBoolean(Preferences.highQualityKey, true)
            SettingsPrefs.awaitBoolean(Preferences.antiAliasingKey, false)
            SettingsPrefs.awaitBoolean(Preferences.spaceBetweenPagesKey, false)
            SettingsPrefs.awaitBoolean(Preferences.alwaysHideMarginsKey, true)
            SettingsPrefs.awaitBoolean(Preferences.showScrollHandlePageCountKey, true)
            SettingsPrefs.awaitBoolean(Preferences.showAppBarPageCountKey, true)
            listOf(
                Preferences.highQualityKey,
                Preferences.antiAliasingKey,
                Preferences.spaceBetweenPagesKey,
                Preferences.alwaysHideMarginsKey,
                Preferences.showScrollHandlePageCountKey,
                Preferences.showAppBarPageCountKey,
            ).forEach(SettingsPrefs::assertDeclaredKind)
        }
    }

    @Test
    @TestId("SET-16")
    fun theReadingSwitchesPersistWithoutTouchingTheReadingModeKeys() {
        launchSettingsOn(SettingsPage.READING).use {
            flip(R.string.dual_page_first_page_alone_title, true)
            flip(R.string.snap, true)
            flip(R.string.fling, true)
            flip(R.string.keep_screen_on, true)
            flip(R.string.always_open_first_page_title, true)
            flip(R.string.always_horizontal, true)

            SettingsPrefs.awaitBoolean(Preferences.dualPageFirstPageAloneKey, true)
            SettingsPrefs.awaitBoolean(Preferences.pageSnapKey, true)
            SettingsPrefs.awaitBoolean(Preferences.pageFlingKey, true)
            SettingsPrefs.awaitBoolean(Preferences.screenOnKey, true)
            SettingsPrefs.awaitBoolean(Preferences.alwaysOpenAtFirstPageKey, true)
            SettingsPrefs.awaitBoolean(Preferences.alwaysHorizontalKey, true)

            SettingsPrefs.assertUnset(
                Preferences.singlePageModeKey,
                Preferences.horizontalScrollKey,
                Preferences.dualPageModeKey,
            )
        }
    }

    @Test
    @TestId("SET-17")
    fun theControlSwitchesPersistTheInputAndStartupChoices() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            flip(R.string.turn_page_by_volume_buttons_title, true)
            flip(R.string.turn_page_by_mouse_buttons_title, false)
            flip(R.string.auto_full_screen, true)

            SettingsPrefs.awaitBoolean(Preferences.turnPageByVolumeButtonsKey, true)
            SettingsPrefs.awaitBoolean(Preferences.turnPageByMouseButtonsKey, false)
            SettingsPrefs.awaitBoolean(Preferences.autoFullScreenKey, true)
        }
    }

    @Test
    @TestId("SET-18")
    fun theTextAndHighlightingSwitchesWriteTheKeysThoseFeaturesRead() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.openPage(SettingsPage.TEXT)
            flip(R.string.default_text_mode, true)
            flip(R.string.inline_text_selection_title, false)
            flip(R.string.search_ignore_accents_title, true)
            flip(R.string.search_zoom_to_result_title, true)
            backToRoot()

            SettingsScreen.openPage(SettingsPage.HIGHLIGHTING)
            flip(R.string.detect_existing_highlights_title, false)

            SettingsPrefs.awaitBoolean(Preferences.defaultTextModeKey, true)
            SettingsPrefs.awaitBoolean(Preferences.inlineTextSelectionKey, false)
            SettingsPrefs.awaitBoolean(Preferences.searchIgnoreAccentsKey, true)
            SettingsPrefs.awaitBoolean(Preferences.searchZoomToResultKey, true)
            SettingsPrefs.awaitBoolean(Preferences.detectExistingHighlightsKey, false)
        }
    }

    @Test
    @TestId("SET-19")
    fun theAdvancedAndPrivacySwitchesArePersistedIndependently() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.openPage(SettingsPage.ADVANCED)
            flip(R.string.double_tap_to_exit, true)
            flip(R.string.three_step_double_tap_zoom, true)
            flip(R.string.separate_windows_title, false)
            backToRoot()

            SettingsScreen.openPage(SettingsPage.HISTORY_PRIVACY)
            flip(R.string.history_enabled_title, false)

            SettingsPrefs.awaitBoolean(Preferences.doubleTapToExitEnabledKey, true)
            SettingsPrefs.awaitBoolean(Preferences.doubleTapThreeStepZoomKey, true)
            SettingsPrefs.awaitBoolean(Preferences.openPdfsInSeparateWindowsKey, false)
            SettingsPrefs.awaitBoolean(Preferences.historyEnabledKey, false)
        }
    }

    @Test
    @TestId("SET-20")
    fun aToggledSwitchIsReadBackFromTheStoreWhenSettingsIsOpenedAgain() {
        launchSettingsOn(SettingsPage.APPEARANCE).use {
            flip(R.string.quality, true)
            SettingsPrefs.awaitBoolean(Preferences.highQualityKey, true)
        }
        SettingsScreen.awaitGone()

        launchSettingsOn(SettingsPage.APPEARANCE).use {
            SettingsPrefs.assertBoolean(Preferences.highQualityKey, true)
            assertEquals(
                "the High Quality switch renders from the stored value on a fresh build of the page",
                true,
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.quality)).checked,
            )
        }
    }

    @Test
    @TestId("SET-21")
    fun turningASwitchBackToItsDefaultStoresTheDefaultExplicitlyRatherThanRemovingTheKey() {
        launchSettingsOn(SettingsPage.APPEARANCE).use {
            flip(R.string.anti_aliasing, false)
            flip(R.string.anti_aliasing, true)

            SettingsPrefs.awaitBoolean(Preferences.antiAliasingKey, Preferences.antiAliasingDefault)
            SettingsPrefs.assertDeclaredKind(Preferences.antiAliasingKey)
            assertEquals(
                "the switch is back in its default on state",
                true,
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.anti_aliasing)).checked,
            )
        }
    }

    private fun flip(titleRes: Int, expected: Boolean) {
        toggleSwitch(SettingsStrings.of(titleRes), expected)
    }
}
