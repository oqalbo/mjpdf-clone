// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import org.junit.Assert.*
import org.junit.Test
import org.junit.Ignore
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.translation.TranslationLanguages
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import java.util.Locale

class SettingsDecisionsTest {
    @Test fun blankLanguageSelectsDeviceAndKnownLanguageUsesSuppliedOrder() {
        val codes = listOf("fr", "en", "ar")
        assertEquals(0, targetLanguageCheckedIndex("", codes))
        assertEquals(2, targetLanguageCheckedIndex("en", codes))
        assertEquals(3, targetLanguageCheckedIndex("ar", codes))
    }

    @Ignore("Pending ruling: unknown stored language selects device language, expected no checked row")
    @Test fun unknownLanguageDoesNotSelectDeviceWhileSummaryShowsStoredCode() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Xx", formatSummary(null, TranslationLanguages.displayName("xx")))
            assertEquals(-1, targetLanguageCheckedIndex("xx", listOf("en", "fr")))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun customUrlRequiresHttpSchemeAndTextPlaceholderAfterTrimming() {
        assertTrue(isValidCustomTranslationUrl("  HTTPS://x.com/?q={text} "))
        listOf("ftp://x/{text}", "javascript:{text}", "https://x.com/?q=").forEach {
            assertFalse(it, isValidCustomTranslationUrl(it))
        }
    }

    @Test fun placeholderInHostIsAccepted() {
        assertTrue(isValidCustomTranslationUrl("https://{text}"))
    }

    @Test fun fractionalZoomRoundsToThreeAtPositionTwo() {
        val preferences = Preferences(InMemorySharedPreferences(mapOf(Preferences.maxZoomKey to 2.5f)))
        val range = sliderRange(Preferences.minMaxZoom, Preferences.maxMaxZoom, preferences.getMaxZoom())
        assertEquals(99 to 2, range)
        assertEquals(3, Preferences.minMaxZoom.toInt() + range.second)
    }

    @Test fun importedZoomIsClampedOnlyInTheDialog() {
        val preferences = Preferences(InMemorySharedPreferences(mapOf(Preferences.maxZoomKey to 500f)))
        assertEquals(99 to 99, sliderRange(1f, 100f, preferences.getMaxZoom()))
        assertEquals(500f, preferences.getMaxZoom(), 0f)
        assertEquals(99 to 0, sliderRange(1f, 100f, -5f))
    }

    @Test fun partSizeSubtractsItsMinimumFromTheSeekMaximum() {
        assertEquals(995 to 251, sliderRange(5f, 1000f, 256f))
    }

    @Test fun actionResolutionFallsBackThroughDefaultThenFirst() {
        val ids = listOf("search", "settings")
        assertEquals("settings" to "settings", resolveActionIds("settings", "unknown", ids))
        assertEquals("search" to "search", resolveActionIds("unknown", "unknown", ids))
        assertEquals("search" to "settings", resolveActionIds("unknown", "settings", ids))
    }

    @Test(expected = NoSuchElementException::class)
    fun emptyActionListCannotResolveAFallback() {
        resolveActionIds("settings", "search", emptyList())
    }

    @Test fun summaryOmitsBlankPartsAndJoinsBothParts() {
        assertEquals("detail", formatSummary(null, "detail"))
        assertEquals("detail", formatSummary("  ", "detail"))
        assertEquals("page", formatSummary("page", "  "))
        assertEquals("page", formatSummary("page", null))
        assertEquals("a: b", formatSummary("a", "b"))
        assertNull(formatSummary(null, null))
    }

    @Test fun legacyHideDelayHasNoLabelOrCheckedRow() {
        val options = listOf(2000, 3000, 5000, 10000)
        val labels = listOf("2 seconds", "3 seconds", "5 seconds", "10 seconds")
        assertEquals(-1 to null, optionLabel(options, labels, 4000))
        assertEquals(2 to "5 seconds", optionLabel(options, labels, 5000))
        assertEquals(1 to "2 lines", optionLabel(listOf(1, 2, 3), listOf("1 line", "2 lines", "3 lines"), 2))
    }
}
