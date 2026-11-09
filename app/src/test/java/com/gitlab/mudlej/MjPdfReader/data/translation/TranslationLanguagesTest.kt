// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.translation

import org.junit.Assert.*
import org.junit.Test

class TranslationLanguagesTest {
    @Test fun blankStoredLanguageNormalizesLegacyDeviceLanguage() {
        assertEquals("he", TranslationLanguages.resolve("", "iw"))
        assertEquals("id", TranslationLanguages.resolve("  ", "in"))
        assertEquals("fr", TranslationLanguages.resolve("", "fr"))
    }

    @Test fun unsupportedDeviceLanguageFallsBackToEnglish() {
        assertEquals("en", TranslationLanguages.resolve("", "xx"))
    }

    @Test fun explicitlyStoredUnsupportedLanguageIsPreserved() {
        assertEquals("xx", TranslationLanguages.resolve("xx", "fr"))
    }

    @Test fun unknownEngineFallsBackToGoogleAndKnownIdsRoundTrip() {
        assertEquals(TranslationEngine.GOOGLE, TranslationEngine.fromId("bogus"))
        TranslationEngine.entries.forEach { assertEquals(it, TranslationEngine.fromId(it.id)) }
        assertEquals(TranslationEngine.entries.size, TranslationEngine.entries.map { it.id }.toSet().size)
    }
}
