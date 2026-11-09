// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.translation

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DictionaryCandidatesTest {
    @Test fun curlyPossessiveIsNormalizedBeforeLookup() {
        assertEquals("james", DictionaryStore.normalize("James’s"))
        assertEquals("james", DictionaryStore.normalize("James'"))
    }

    @Test fun turkishDefaultLocaleDoesNotChangeEnglishLowercasing() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("title", DictionaryStore.normalize("TITLE"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun pluralCandidatesKeepOriginalFirstAndPreserveRuleOrder() {
        assertEquals(listOf("boxes", "box", "boxe"), DictionaryStore.candidates("boxes"))
        assertEquals(listOf("flies", "fly", "flie", "fli"), DictionaryStore.candidates("flies"))
        assertEquals(listOf("women", "woman"), DictionaryStore.candidates("women"))
    }

    @Test fun shortWordsDoNotLoseTheirLastLetter() {
        assertEquals(listOf("is"), DictionaryStore.candidates("is"))
    }

    @Test fun exceptionCandidatesPrecedeSuffixesWithoutDuplicates() {
        assertEquals(listOf("boxes", "crate", "box", "boxe"),
            DictionaryStore.candidates("boxes", listOf("boxes", "crate", "box", "crate")))
    }
}
