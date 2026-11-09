// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class PreferencesDerivedValuesTest {
    @Test fun pageThemeFallbackPrefersSystemThenDarkThenLight() {
        for ((flags, expected) in mapOf(
            (true to true) to "system",
            (true to false) to "system",
            (false to true) to "dark",
            (false to false) to "light",
        )) {
            val stored = InMemorySharedPreferences(mapOf(
                Preferences.pdfFollowSystemThemeKey to flags.first,
                Preferences.pdfDarkThemeKey to flags.second,
            ))

            assertEquals("flags=$flags", expected, Preferences(stored).getPdfPagesTheme())
        }
    }

    @Test fun explicitPageThemeWinsOverLegacyFlags() {
        val stored = InMemorySharedPreferences(mapOf(
            Preferences.pdfPagesThemeKey to "light",
            Preferences.pdfFollowSystemThemeKey to true,
            Preferences.pdfDarkThemeKey to true,
        ))

        assertEquals("light", Preferences(stored).getPdfPagesTheme())
    }

    @Test fun settingDarkPageThemeSynchronizesLegacyPageFlags() {
        for (appFollowsSystem in listOf(false, true)) {
            val stored = InMemorySharedPreferences(mapOf(
                Preferences.pdfFollowSystemThemeKey to true,
                Preferences.pdfDarkThemeKey to false,
                Preferences.appFollowSystemThemeKey to appFollowsSystem,
            ))
            val preferences = Preferences(stored)

            preferences.setPdfPagesTheme("dark")

            assertEquals(mapOf(
                Preferences.pdfPagesThemeKey to "dark",
                Preferences.pdfFollowSystemThemeKey to false,
                Preferences.pdfDarkThemeKey to true,
                Preferences.appFollowSystemThemeKey to appFollowsSystem,
            ), stored.snapshot())
            assertEquals("dark", preferences.getPdfPagesTheme())
        }
    }

    @Test fun invalidAndReservedHighlightColorsAreRemovedBeforePadding() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.highlightColorsKey to "GRAY,YELLOW,BOGUS"))

        assertEquals(defaultHighlightColors(), Preferences(stored).getHighlightColors())
    }

    @Test fun highlightColorsKeepOnlyTheFirstSixValidNames() {
        val stored = InMemorySharedPreferences(mapOf(
            Preferences.highlightColorsKey to "RED,CYAN,BROWN,TEAL,PURPLE,BLUE,GREEN,YELLOW",
        ))

        assertEquals(listOf(
            HighlightPalette.RED, HighlightPalette.CYAN, HighlightPalette.BROWN,
            HighlightPalette.TEAL, HighlightPalette.PURPLE, HighlightPalette.BLUE,
        ).map { it.colorValue }, Preferences(stored).getHighlightColors())
    }

    @Test fun emptyHighlightColorsUseTheDefaultPalette() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.highlightColorsKey to ""))

        assertEquals(defaultHighlightColors(), Preferences(stored).getHighlightColors())
    }

    @Test fun secondaryActionUsesLegacyReloadOnlyWhenNoActionIsStored() {
        for (reloadEnabled in listOf(false, true)) {
            for ((action, expected) in listOf(
                null to if (reloadEnabled) "reload" else "none",
                "none" to "none",
                "fullscreen" to "fullscreen",
                "" to "",
            )) {
                val stored = InMemorySharedPreferences(mapOf(
                    Preferences.enableReloadButtonKey to reloadEnabled,
                    Preferences.secondaryButtonActionKey to action,
                ))

                assertEquals("reload=$reloadEnabled action=$action", expected, Preferences(stored).getSecondaryButtonAction())
            }
        }
    }

    @Test fun missingSharedCopyModeUsesTheLegacyKeepCopiesFlag() {
        for ((keepCopies, expected) in listOf(false to SharedCopyMode.ASK, true to SharedCopyMode.ALWAYS_COPY)) {
            val stored = InMemorySharedPreferences(mapOf(Preferences.keepSharedCopiesKey to keepCopies))

            assertEquals(expected, Preferences(stored).getSharedCopyMode())
        }
    }

    @Test fun explicitSharedCopyModeWinsOverTheLegacyKeepCopiesFlag() {
        for ((keepCopies, mode) in listOf(false to SharedCopyMode.ALWAYS_COPY, true to SharedCopyMode.ASK)) {
            val stored = InMemorySharedPreferences(mapOf(
                Preferences.keepSharedCopiesKey to keepCopies,
                Preferences.sharedCopyModeKey to mode.name,
            ))

            assertEquals(mode, Preferences(stored).getSharedCopyMode())
        }
    }

    @Test fun enabledBackupInitializesAZeroTimestampOnlyOnce() {
        val stored = InMemorySharedPreferences(mapOf(
            Preferences.autoBackupEnabledKey to true,
            Preferences.autoBackupEnabledAtKey to 0L,
        ))
        val preferences = Preferences(stored)
        val before = System.currentTimeMillis()

        val enabledAt = preferences.ensureAutoBackupEnabledAt()

        val after = System.currentTimeMillis()
        assertTrue("enabledAt=$enabledAt outside [$before, $after]", enabledAt in before..after)
        assertEquals(enabledAt, stored.snapshot()[Preferences.autoBackupEnabledAtKey])
        assertEquals(enabledAt, preferences.ensureAutoBackupEnabledAt())
    }

    @Test fun disabledBackupLeavesAnAbsentTimestampAbsent() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.autoBackupEnabledKey to false))

        assertEquals(0L, Preferences(stored).ensureAutoBackupEnabledAt())
        assertEquals(mapOf(Preferences.autoBackupEnabledKey to false), stored.snapshot())
    }

    @Test fun backupPreservesStoredTimestampsWhenDisabledOrAlreadyInitialized() {
        for ((enabled, timestamp) in listOf(false to 0L, false to 1234L, true to 1234L)) {
            val initial = mapOf(
                Preferences.autoBackupEnabledKey to enabled,
                Preferences.autoBackupEnabledAtKey to timestamp,
            )
            val stored = InMemorySharedPreferences(initial)

            assertEquals(timestamp, Preferences(stored).ensureAutoBackupEnabledAt())
            assertEquals(initial, stored.snapshot())
        }
    }

    @Ignore("Pending ruling: an empty stored overlay order returns an empty list, expected the default order")
    @Test fun emptyOverlayActionOrderUsesTheDefaultOrder() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.fullScreenOverlayActionOrderKey to ""))

        assertEquals(
            ConfigurableAction.defaultFullScreenOverlayOrder.map { it.id },
            Preferences(stored).getFullScreenOverlayActionOrder(),
        )
    }

    private fun defaultHighlightColors() = listOf(
        HighlightPalette.YELLOW, HighlightPalette.ORANGE, HighlightPalette.GREEN,
        HighlightPalette.BLUE, HighlightPalette.PINK_RED, HighlightPalette.PURPLE,
    ).map { it.colorValue }
}
