// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.res.Configuration
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
class ThemeStripSettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-51")
    fun theAppThemeStripWritesBothKeysAndRepaintsTheScreenAtOnce() {
        launchSettingsOn(SettingsPage.APPEARANCE).use {
            SettingsScreen.awaitRow(appThemeTitle)

            SettingsScreen.tapThemeSegment(appThemeTitle, R.id.theme_choice_dark)
            SettingsPrefs.awaitString(Preferences.interfaceThemeKey, Preferences.themeDark)
            SettingsPrefs.awaitBoolean(Preferences.appFollowSystemThemeKey, false)
            SettingsScreen.awaitNightMode(Configuration.UI_MODE_NIGHT_YES)
            SettingsScreen.awaitCheckedThemeSegment(appThemeTitle, R.id.theme_choice_dark)

            SettingsScreen.tapThemeSegment(appThemeTitle, R.id.theme_choice_system)
            SettingsPrefs.awaitString(Preferences.interfaceThemeKey, Preferences.themeSystem)
            SettingsPrefs.awaitBoolean(Preferences.appFollowSystemThemeKey, true)
            SettingsScreen.awaitNightMode(Configuration.UI_MODE_NIGHT_NO)
            SettingsScreen.awaitCheckedThemeSegment(appThemeTitle, R.id.theme_choice_system)
        }
    }

    @Test
    @TestId("SET-52")
    fun thePdfPagesStripKeepsTheTwoLegacyBooleansConsistentAndLeavesTheAppThemeAlone() {
        launchSettingsOn(SettingsPage.APPEARANCE).use {
            SettingsScreen.awaitRow(pdfThemeTitle)

            SettingsScreen.tapThemeSegment(pdfThemeTitle, R.id.theme_choice_dark)
            SettingsPrefs.awaitString(Preferences.pdfPagesThemeKey, Preferences.themeDark)
            SettingsPrefs.assertBoolean(Preferences.pdfDarkThemeKey, true)
            SettingsPrefs.assertBoolean(Preferences.pdfFollowSystemThemeKey, false)

            SettingsScreen.tapThemeSegment(pdfThemeTitle, R.id.theme_choice_system)
            SettingsPrefs.awaitString(Preferences.pdfPagesThemeKey, Preferences.themeSystem)
            SettingsPrefs.assertBoolean(Preferences.pdfDarkThemeKey, false)
            SettingsPrefs.assertBoolean(Preferences.pdfFollowSystemThemeKey, true)

            SettingsScreen.tapThemeSegment(pdfThemeTitle, R.id.theme_choice_light)
            SettingsPrefs.awaitString(Preferences.pdfPagesThemeKey, Preferences.themeLight)
            SettingsPrefs.assertBoolean(Preferences.pdfDarkThemeKey, false)
            SettingsPrefs.assertBoolean(Preferences.pdfFollowSystemThemeKey, false)

            SettingsPrefs.assertUnset(Preferences.interfaceThemeKey, Preferences.appFollowSystemThemeKey)
        }
    }

    @Test
    @TestId("SET-53")
    fun eachStripBindsFromTheStoredModeRatherThanFallingBackToSystem() {
        launchSettingsOn(SettingsPage.APPEARANCE).use {
            SettingsScreen.tapThemeSegment(appThemeTitle, R.id.theme_choice_light)
            SettingsPrefs.awaitString(Preferences.interfaceThemeKey, Preferences.themeLight)
            SettingsScreen.tapThemeSegment(pdfThemeTitle, R.id.theme_choice_dark)
            SettingsPrefs.awaitString(Preferences.pdfPagesThemeKey, Preferences.themeDark)
        }
        SettingsScreen.awaitGone()

        launchSettingsOn(SettingsPage.APPEARANCE).use {
            SettingsScreen.awaitCheckedThemeSegment(appThemeTitle, R.id.theme_choice_light)
            SettingsScreen.awaitCheckedThemeSegment(pdfThemeTitle, R.id.theme_choice_dark)

            SettingsScreen.scrollListToEnd()
            SettingsScreen.awaitCheckedThemeSegment(appThemeTitle, R.id.theme_choice_light)
            SettingsScreen.awaitCheckedThemeSegment(pdfThemeTitle, R.id.theme_choice_dark)

            assertEquals(
                "recycling the strips must not rewrite the app theme",
                Preferences.themeLight,
                SettingsPrefs.string(Preferences.interfaceThemeKey),
            )
            assertEquals(
                "recycling the strips must not rewrite the pdf pages theme",
                Preferences.themeDark,
                SettingsPrefs.string(Preferences.pdfPagesThemeKey),
            )
        }
    }

    private val appThemeTitle: String get() = SettingsStrings.of(R.string.dark_theme_for_app)
    private val pdfThemeTitle: String get() = SettingsStrings.of(R.string.dark_theme_for_pdf)
}
