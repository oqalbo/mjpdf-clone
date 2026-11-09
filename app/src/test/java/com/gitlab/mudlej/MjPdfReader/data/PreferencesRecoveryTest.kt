// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PreferencesRecoveryTest {
    @Test fun integerUnderBooleanKeyIsRemovedAndReturnsTheDefault() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.antiAliasingKey to 1))

        assertEquals(true, Preferences(stored).getAntiAliasing())
        assertFalse(stored.contains(Preferences.antiAliasingKey))
    }

    @Test fun stringUnderIntegerKeyIsRemovedAndReturnsTheDefault() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.hideDelayKey to "3000"))

        assertEquals(3000, Preferences(stored).getHideDelay())
        assertFalse(stored.contains(Preferences.hideDelayKey))
    }

    @Test fun integerUnderLongKeyIsRemovedAndReturnsTheDefault() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.autoBackupLastRunKey to 42))

        assertEquals(0L, Preferences(stored).getAutoBackupLastRun())
        assertFalse(stored.contains(Preferences.autoBackupLastRunKey))
    }

    @Test fun integerUnderFloatKeyIsRemovedAndReturnsTheDefault() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.maxZoomKey to 5))

        assertEquals(10f, Preferences(stored).getMaxZoom(), 0f)
        assertFalse(stored.contains(Preferences.maxZoomKey))
    }

    @Test fun booleanUnderStringKeyIsRemovedAndReturnsTheDefault() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.primaryButtonActionKey to false))

        assertEquals("fullscreen", Preferences(stored).getPrimaryButtonAction())
        assertFalse(stored.contains(Preferences.primaryButtonActionKey))
    }

    @Test fun stringUnderStringSetKeyIsRemovedAndReturnsTheDefault() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.scanLocationsKey to "folder"))

        assertEquals(emptySet<String>(), Preferences(stored).getScanLocations())
        assertFalse(stored.contains(Preferences.scanLocationsKey))
    }

    @Test fun unknownHomeTabIsRemovedAndReturnsLibrary() {
        val stored = InMemorySharedPreferences(mapOf(Preferences.homeTabKey to "NOT_AN_ENUM"))

        assertEquals(HomeTab.LIBRARY, Preferences(stored).getHomeTab())
        assertFalse(stored.contains(Preferences.homeTabKey))
    }
}
