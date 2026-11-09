// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import org.junit.Assert.*
import org.junit.Test
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences

class SettingsSearchTest {
    private val entries = SettingsEntryProvider(Preferences(InMemorySharedPreferences())).entries()
    private val resolve: (Int) -> String = { id ->
        when (id) {
            R.string.dark_theme_for_app -> "App theme"
            SettingsPage.HISTORY_PRIVACY.titleRes -> "History and privacy"
            else -> ""
        }
    }

    @Test fun searchIgnoresCaseAndRepeatedSpacesButRequiresEveryTermInOneEntry() {
        val theme = entries.single { it.titleRes == R.string.dark_theme_for_app }
        assertTrue(theme.matches(resolve, "  DARK   theme "))
        assertFalse(theme.matches(resolve, "dark spacing"))
        assertTrue(entries.single { it.titleRes == R.string.space_between_pages }.matches(resolve, "spacing"))
    }

    @Test fun searchingPageTitleReturnsEveryEntryOnThatPage() {
        val pageEntries = entries.filter { it.page == SettingsPage.HISTORY_PRIVACY }
        assertFalse(pageEntries.isEmpty())
        assertEquals(pageEntries, entries.filter { it.matches(resolve, "history and privacy") })
    }

    @Test fun partOfAKeywordPhraseMatches() {
        val spacing = entries.single { it.titleRes == R.string.space_between_pages }
        assertTrue(spacing.matches(resolve, "gap"))
        assertFalse(spacing.matches(resolve, "gap dictionary"))
    }

    @Test fun summaryTextParticipatesInSearch() {
        val entry = SettingEntry(SettingsPage.HOME, 1, summaryRes = 2) { error("Not rendered") }
        assertTrue(entry.matches({ if (it == 2) "resume reading" else "" }, "resume"))
    }

    @Test fun nullSectionAfterCategoryReturnsToRootWithoutReordering() {
        fun entry(title: Int, section: Int?) = SettingEntry(SettingsPage.HOME, title, sectionRes = section) {
            error("Not rendered")
        }
        val first = entry(1, 10)
        val second = entry(2, 10)
        val root = entry(3, null)
        val last = entry(4, 10)
        assertEquals(listOf(10 to listOf(first, second), null to listOf(root), 10 to listOf(last)),
            groupBySection(listOf(first, second, root, last)))
        assertEquals(emptyList<Pair<Int?, List<SettingEntry>>>(), groupBySection(emptyList()))
    }

    @Test fun overrideReasonReplacesBlankSummaryAndAppendsToNonBlankSummary() {
        assertEquals("reason", mergeOverrideSummary(null, "reason"))
        assertEquals("reason", mergeOverrideSummary("  ", "reason"))
        assertEquals("summary\nreason", mergeOverrideSummary("summary", "reason"))
    }
}
