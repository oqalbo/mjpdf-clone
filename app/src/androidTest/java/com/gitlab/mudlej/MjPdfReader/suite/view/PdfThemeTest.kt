// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PdfThemeTest : ViewTestCase() {

    @Test
    @TestId("VIEW-19")
    fun theDarkPdfThemePaintsTheDarkSurroundOnAFullyRenderedFrame() {
        seedPdfTheme(Preferences.themeDark)
        openReader()

        enterFullscreen()
        settledFullQuality()
        ViewUi.awaitPdfViewBackgroundColor(DARK_SURROUND)
    }

    @Test
    @TestId("VIEW-20")
    @TestDuration(DurationCategory.NORMAL)
    fun theSwitchThemeActionTogglesThePdfThemeAndPersistsIt() {
        seedPdfTheme(Preferences.themeLight)
        openReader()

        ViewActionsDialog.assertTileTitle(
            "before the switch the tile offers dark mode",
            presentRes = R.string.switch_to_dark_mode,
            absentRes = R.string.switch_to_light_mode,
        )

        ViewActionsDialog.openAndTap(R.string.switch_to_dark_mode)
        settledFullQuality()

        assertEquals(
            "the switch must store the dark PDF pages theme",
            Preferences.themeDark,
            SettingsSeeder.preferences.all[Preferences.pdfPagesThemeKey],
        )
        assertEquals(
            "the legacy dark boolean must mirror the new key",
            true,
            SettingsSeeder.preferences.all[Preferences.pdfDarkThemeKey],
        )
        assertEquals(
            "the legacy follows system boolean must mirror the new key",
            false,
            SettingsSeeder.preferences.all[Preferences.pdfFollowSystemThemeKey],
        )
        ViewUi.awaitPdfViewBackgroundColor(DARK_SURROUND)
        ViewActionsDialog.assertTileTitle(
            "after the switch the tile offers light mode, which is the only observable checked state a menu " +
                "entry has",
            presentRes = R.string.switch_to_light_mode,
            absentRes = R.string.switch_to_dark_mode,
        )
    }

    @Test
    @TestId("VIEW-21")
    fun aFollowsSystemPdfThemeOpensTheChoiceDialogInsteadOfTogglingSilently() {
        seedPdfTheme(Preferences.themeSystem)
        openReader()

        ViewActionsDialog.openAndTap(R.string.switch_to_dark_mode)

        awaitDialogWithTitle(R.string.dark_theme_for_pdf)
        assertTrue(
            "the dialog must explain why the theme cannot be toggled directly",
            TestEnvironment.uiDevice.hasObject(
                androidx.test.uiautomator.By.text(ViewUi.string(R.string.pdf_theme_follows_system_dialog))
            ),
        )

        onView(withId(R.id.theme_choice_dark)).perform(ViewActions.click())
        tapDialogPositive()
        awaitDialogWithTitleGone(R.string.dark_theme_for_pdf)

        assertEquals(
            "picking dark in the choice strip must store the dark theme",
            Preferences.themeDark,
            SettingsSeeder.preferences.all[Preferences.pdfPagesThemeKey],
        )
        ViewUi.awaitPdfViewBackgroundColor(DARK_SURROUND)
    }

    @Test
    @TestId("VIEW-22")
    fun thePdfViewBackgroundColourFollowsThePdfTheme() {
        seedPdfTheme(Preferences.themeLight)
        val file = openReader()

        ViewUi.awaitPdfViewBackgroundColor(LIGHT_SURROUND)
        assertEquals(
            "an explicitly light PDF theme must surround the page with the light colour",
            LIGHT_SURROUND,
            ViewUi.pdfViewBackgroundColor(),
        )

        finishReader()
        seedPdfTheme(Preferences.themeDark)
        launchAt(file)

        ViewUi.awaitPdfViewBackgroundColor(DARK_SURROUND)
        assertEquals(
            "an explicitly dark PDF theme must surround the page with the dark colour",
            DARK_SURROUND,
            ViewUi.pdfViewBackgroundColor(),
        )
    }

    @Test
    @TestId("VIEW-23")
    fun theInterfaceThemeIsIndependentOfThePdfTheme() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.interfaceThemeKey to Preferences.themeDark,
                Preferences.appFollowSystemThemeKey to false,
            )
        )
        seedPdfTheme(Preferences.themeLight)

        openReader()

        assertEquals(
            "a dark interface theme must reach AppCompatDelegate",
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.getDefaultNightMode(),
        )
        assertTrue("the reader configuration must carry the night bit", ViewUi.uiModeNight())
        assertEquals(
            "the page surround must stay light, because the two theme settings are independent",
            LIGHT_SURROUND,
            ViewUi.pdfViewBackgroundColor(),
        )

        enterFullscreen()

        settledFullQuality()
    }

    @Test
    @TestId("VIEW-24")
    fun thePdfThemeIsStillInForceWhenTheDocumentIsReopened() {
        seedPdfTheme(Preferences.themeLight)
        val file = openReader()

        ViewActionsDialog.openAndTap(R.string.switch_to_dark_mode)
        settledFullQuality()
        ViewUi.awaitPdfViewBackgroundColor(DARK_SURROUND)

        finishReader()
        launchAt(file)

        ViewUi.awaitPdfViewBackgroundColor(DARK_SURROUND)
        assertEquals(
            "a theme choice is a setting and must be applied at load with no further action",
            DARK_SURROUND,
            ViewUi.pdfViewBackgroundColor(),
        )
        settledFullQuality()
    }

    private fun seedPdfTheme(mode: String) {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.pdfPagesThemeKey to mode,
                Preferences.pdfDarkThemeKey to (mode == Preferences.themeDark),
                Preferences.pdfFollowSystemThemeKey to (mode == Preferences.themeSystem),
            )
        )
    }

    private companion object {
        val LIGHT_SURROUND = 0xFFCECECE.toInt()
        val DARK_SURROUND = 0xFF323232.toInt()
    }
}
