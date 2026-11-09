// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class TextModeSettingsLoadTest {
    @Test fun unknownThemeFallsBackWithoutDiscardingTheFontChoice() {
        val stored = InMemorySharedPreferences(mapOf(
            Preferences.textModeThemeKey to "NOT_A_THEME",
            Preferences.textModeFontFamilyKey to "SERIF",
        ))

        val settings = TextModeSettings.load(stored)

        assertEquals(ReaderTheme.SYSTEM, settings.theme)
        assertEquals(ReaderFontFamily.SERIF, settings.fontFamily)
    }

    @Test fun unknownFontFallsBackWithoutDiscardingTheThemeChoice() {
        val stored = InMemorySharedPreferences(mapOf(
            Preferences.textModeThemeKey to "SEPIA",
            Preferences.textModeFontFamilyKey to "NOT_A_FONT",
        ))

        val settings = TextModeSettings.load(stored)

        assertEquals(ReaderFontFamily.SANS, settings.fontFamily)
        assertEquals(ReaderTheme.SEPIA, settings.theme)
    }

    @Test fun wronglyTypedValuesFallBackIndependentlyForEveryField() {
        val valid = mapOf(
            Preferences.textModeFontSizeKey to 25f,
            Preferences.textModeLineSpacingKey to 1.5f,
            Preferences.textModeHorizontalMarginKey to 30,
            Preferences.textModeThemeKey to "DRACULA",
            Preferences.textModeFontFamilyKey to "MONO",
            Preferences.textModeReadableLineLengthKey to false,
        )
        val expected = TextModeSettings(25f, 1.5f, 30, ReaderTheme.DRACULA, ReaderFontFamily.MONO, false)
        val defaultsByKey = mapOf(
            Preferences.textModeFontSizeKey to expected.copy(fontSize = 18f),
            Preferences.textModeLineSpacingKey to expected.copy(lineSpacing = 1.35f),
            Preferences.textModeHorizontalMarginKey to expected.copy(horizontalMargin = 20),
            Preferences.textModeThemeKey to expected.copy(theme = ReaderTheme.SYSTEM),
            Preferences.textModeFontFamilyKey to expected.copy(fontFamily = ReaderFontFamily.SANS),
            Preferences.textModeReadableLineLengthKey to expected.copy(readableLineLength = true),
        )
        for ((key, recovered) in defaultsByKey) {
            val stored = InMemorySharedPreferences(valid + (key to setOf("wrong type")))

            assertEquals(key, recovered, TextModeSettings.load(stored))
        }
    }

    @Test fun halfwayMarginRoundsUpToTheNextStep() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.textModeHorizontalMarginKey to 21))

        assertEquals(22, TextModeSettings.load(stored).horizontalMargin)
    }

    @Test fun marginBelowTheMinimumClampsToEight() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.textModeHorizontalMarginKey to 7))

        assertEquals(8, TextModeSettings.load(stored).horizontalMargin)
    }

    @Test fun savingAndLoadingPreservesEveryField() {
        val stored = InMemorySharedPreferences()
        val settings = TextModeSettings(
            fontSize = 25f,
            lineSpacing = 1.5f,
            horizontalMargin = 30,
            theme = ReaderTheme.DRACULA,
            fontFamily = ReaderFontFamily.MONO,
            readableLineLength = false,
        )

        settings.save(stored)

        assertEquals(settings, TextModeSettings.load(stored))
    }
}
