// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import org.junit.Assert.*
import org.junit.Test
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences

class SettingsEntryProviderInvariantsTest {
    private val entries = SettingsEntryProvider(Preferences(InMemorySharedPreferences())).entries()

    @Test fun everySettingsPageContainsEntries() {
        assertEquals(SettingsPage.values().toSet(), entries.map { it.page }.toSet())
    }

    @Test fun titlesAreUniqueWithinEachPage() {
        entries.groupBy { it.page }.forEach { (page, entries) ->
            val titles = entries.map { it.titleRes }
            assertEquals(page.name, titles.size, titles.toSet().size)
        }
    }

    @Test fun sectionsNeverReappearAfterAnotherSectionOnTheSamePage() {
        entries.groupBy { it.page }.forEach { (page, entries) ->
            val sections = entries.map { it.sectionRes }.filterIndexed { index, section ->
                index == 0 || entries[index - 1].sectionRes != section
            }.filterNotNull()
            assertEquals(page.name, sections.size, sections.toSet().size)
        }
    }
}
