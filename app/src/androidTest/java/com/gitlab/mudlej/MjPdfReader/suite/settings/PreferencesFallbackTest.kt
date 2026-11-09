// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PreferencesFallbackTest : SettingsTestCase() {

    @Test
    @TestId("SET-86")
    fun theAppThemeFallsBackToTheLegacyFollowSystemBoolean() {
        SettingsPrefs.remove(Preferences.interfaceThemeKey, Preferences.appFollowSystemThemeKey)
        assertEquals(
            "with nothing stored the default follow system flag resolves to system",
            Preferences.themeSystem,
            Preferences(SettingsPrefs.store).getInterfaceTheme(),
        )

        SettingsPrefs.seedLegacyBoolean(Preferences.appFollowSystemThemeKey, true)
        assertEquals(
            "the legacy true keeps the system theme",
            Preferences.themeSystem,
            Preferences(SettingsPrefs.store).getInterfaceTheme(),
        )

        SettingsPrefs.seedLegacyBoolean(Preferences.appFollowSystemThemeKey, false)
        assertEquals(
            "there is no legacy boolean that means dark, so a false resolves to light",
            Preferences.themeLight,
            Preferences(SettingsPrefs.store).getInterfaceTheme(),
        )

        assertFalse(
            "the fallback is computed on every read and never written back",
            SettingsPrefs.contains(Preferences.interfaceThemeKey),
        )
    }

    @Test
    @TestId("SET-87")
    fun thePdfPagesThemeFallsBackToTheTwoLegacyBooleans() {
        SettingsPrefs.remove(
            Preferences.pdfPagesThemeKey,
            Preferences.pdfFollowSystemThemeKey,
            Preferences.pdfDarkThemeKey,
        )
        assertEquals(
            "both legacy defaults are false, so pages start light",
            Preferences.themeLight,
            Preferences(SettingsPrefs.store).getPdfPagesTheme(),
        )

        SettingsPrefs.seedLegacyBoolean(Preferences.pdfFollowSystemThemeKey, true)
        SettingsPrefs.seedLegacyBoolean(Preferences.pdfDarkThemeKey, true)
        assertEquals(
            "the follow system test is evaluated first, so the dark flag does not matter",
            Preferences.themeSystem,
            Preferences(SettingsPrefs.store).getPdfPagesTheme(),
        )

        SettingsPrefs.seedLegacyBoolean(Preferences.pdfFollowSystemThemeKey, false)
        SettingsPrefs.seedLegacyBoolean(Preferences.pdfDarkThemeKey, true)
        assertEquals(
            "an upgraded install that had chosen dark pages keeps them",
            Preferences.themeDark,
            Preferences(SettingsPrefs.store).getPdfPagesTheme(),
        )

        SettingsPrefs.seedLegacyBoolean(Preferences.pdfDarkThemeKey, false)
        assertEquals(
            "neither legacy flag set resolves to light",
            Preferences.themeLight,
            Preferences(SettingsPrefs.store).getPdfPagesTheme(),
        )

        assertFalse(
            "the fallback is computed on every read and never written back",
            SettingsPrefs.contains(Preferences.pdfPagesThemeKey),
        )
    }

    @Test
    @TestId("SET-88")
    fun theSecondaryToolbarButtonFallsBackToTheLegacyReloadFlag() {
        SettingsPrefs.remove(Preferences.secondaryButtonActionKey, Preferences.enableReloadButtonKey)
        assertEquals(
            "with nothing stored the second slot is empty",
            Preferences.secondaryButtonActionDefault,
            Preferences(SettingsPrefs.store).getSecondaryButtonAction(),
        )

        SettingsPrefs.seedLegacyBoolean(Preferences.enableReloadButtonKey, true)
        assertEquals(
            "a user who had the reload button on still gets one",
            ConfigurableAction.RELOAD.id,
            Preferences(SettingsPrefs.store).getSecondaryButtonAction(),
        )

        SettingsPrefs.seedLegacyString(Preferences.secondaryButtonActionKey, ConfigurableAction.SEARCH.id)
        assertEquals(
            "the gate is a presence check, so any stored value wins over the legacy flag",
            ConfigurableAction.SEARCH.id,
            Preferences(SettingsPrefs.store).getSecondaryButtonAction(),
        )
    }

    @Test
    @TestId("SET-89")
    fun theSharedCopyModeFallsBackToTheLegacyKeepCopiesBoolean() {
        SettingsPrefs.remove(Preferences.sharedCopyModeKey, Preferences.keepSharedCopiesKey)
        assertEquals(
            "the legacy default is true, so shared files are copied without asking",
            SharedCopyMode.ALWAYS_COPY,
            Preferences(SettingsPrefs.store).getSharedCopyMode(),
        )

        SettingsPrefs.seedLegacyBoolean(Preferences.keepSharedCopiesKey, false)
        assertEquals(
            "a user who had turned copies off is asked each time instead",
            SharedCopyMode.ASK,
            Preferences(SettingsPrefs.store).getSharedCopyMode(),
        )

        SettingsPrefs.seedLegacyBoolean(Preferences.keepSharedCopiesKey, true)
        assertEquals(
            "the legacy true resolves back to always copying",
            SharedCopyMode.ALWAYS_COPY,
            Preferences(SettingsPrefs.store).getSharedCopyMode(),
        )

        assertFalse(
            "the fallback is computed on every read and never written back",
            SettingsPrefs.contains(Preferences.sharedCopyModeKey),
        )
    }

    @Test
    @TestId("SET-90")
    fun theTwoRenamedPreferenceKeysMigrateWhenPreferencesIsConstructed() {
        SettingsPrefs.remove(
            Preferences.spaceBetweenPagesKey,
            Preferences.alwaysHorizontalKey,
            LEGACY_SPACE_BETWEEN_PAGES,
            LEGACY_ALWAYS_HORIZONTAL,
        )
        SettingsPrefs.seedLegacyBoolean(LEGACY_SPACE_BETWEEN_PAGES, false)
        SettingsPrefs.seedLegacyBoolean(LEGACY_ALWAYS_HORIZONTAL, true)

        val migrated = Preferences(SettingsPrefs.store)

        assertTrue(
            "the value moved to the new name",
            SettingsPrefs.contains(Preferences.spaceBetweenPagesKey),
        )
        assertTrue(
            "the value moved to the new name",
            SettingsPrefs.contains(Preferences.alwaysHorizontalKey),
        )
        assertFalse(
            "the old name is removed",
            SettingsPrefs.contains(LEGACY_SPACE_BETWEEN_PAGES),
        )
        assertFalse(
            "the old name is removed",
            SettingsPrefs.contains(LEGACY_ALWAYS_HORIZONTAL),
        )
        assertEquals("the seeded legacy value survives rather than the default", false, migrated.getSpaceBetweenPages())
        assertEquals("the seeded legacy value survives rather than the default", true, migrated.getAlwaysHorizontal())

        SettingsPrefs.seedLegacyBoolean(LEGACY_SPACE_BETWEEN_PAGES, false)
        SettingsPrefs.seedLegacyBoolean(LEGACY_ALWAYS_HORIZONTAL, true)
        SettingsPrefs.seedLegacyBoolean(Preferences.spaceBetweenPagesKey, true)

        val second = Preferences(SettingsPrefs.store)

        assertEquals(
            "migrateLegacyKey only copies when the new key is absent, so an existing value is kept",
            true,
            second.getSpaceBetweenPages(),
        )
        assertFalse(
            "the removal happens whether or not the copy did",
            SettingsPrefs.contains(LEGACY_SPACE_BETWEEN_PAGES),
        )
    }

    private companion object {
        const val LEGACY_SPACE_BETWEEN_PAGES = "spaceBetweenPagesKey"
        const val LEGACY_ALWAYS_HORIZONTAL = "alwaysHorizontalKey"
    }
}
