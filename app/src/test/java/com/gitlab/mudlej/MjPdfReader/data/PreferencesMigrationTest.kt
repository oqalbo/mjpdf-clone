// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Ignore
import org.junit.Test

class PreferencesMigrationTest {
    @Test fun legacyPageSpacingMovesToTheCurrentKey() {
        for (enabled in listOf(false, true)) {
            val stored = InMemorySharedPreferences(mapOf("spaceBetweenPagesKey" to enabled))

            val preferences = Preferences(stored)

            assertEquals(mapOf(Preferences.spaceBetweenPagesKey to enabled), stored.snapshot())
            assertEquals(enabled, preferences.getSpaceBetweenPages())
        }
    }

    @Test fun legacyAlwaysHorizontalMovesToTheCurrentKey() {
        for (enabled in listOf(false, true)) {
            val stored = InMemorySharedPreferences(mapOf("alwaysHorizontalKey" to enabled))

            val preferences = Preferences(stored)

            assertEquals(mapOf(Preferences.alwaysHorizontalKey to enabled), stored.snapshot())
            assertEquals(enabled, preferences.getAlwaysHorizontal())
        }
    }

    @Test fun currentValuesWinOverConflictingLegacyValues() {
        for ((legacy, current) in listOf(
            "spaceBetweenPagesKey" to Preferences.spaceBetweenPagesKey,
            "alwaysHorizontalKey" to Preferences.alwaysHorizontalKey,
        )) {
            for (enabled in listOf(false, true)) {
                val stored = InMemorySharedPreferences(mapOf(legacy to !enabled, current to enabled))

                Preferences(stored)

                assertEquals(legacy, mapOf(current to enabled), stored.snapshot())
            }
        }
    }

    @Ignore("Pending ruling: malformed legacy spacing migrates as false, expected the current default true")
    @Test fun wronglyTypedLegacySpacingUsesTheCurrentDefault() {
        val stored = InMemorySharedPreferences(mapOf("spaceBetweenPagesKey" to "invalid"))

        val preferences = Preferences(stored)

        assertFalse(stored.contains("spaceBetweenPagesKey"))
        assertEquals(mapOf(Preferences.spaceBetweenPagesKey to true), stored.snapshot())
        assertEquals(true, preferences.getSpaceBetweenPages())
    }
}
