// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import org.junit.Assert.*
import org.junit.Test

class DictionaryCandidateTest {
    @Test fun surroundingPunctuationIsRemoved() {
        assertEquals("word", dictionaryCandidate("«word»"))
        assertEquals("word", dictionaryCandidate("word."))
    }

    @Test fun whitespaceAndNumbersAloneAreNotDictionaryCandidates() {
        for (input in listOf("two words", "two\twords", "123", "...", "")) {
            assertNull(input, dictionaryCandidate(input))
        }
    }

    @Test fun fortyEightLettersAreAcceptedButFortyNineAreRejected() {
        assertEquals("a".repeat(48), dictionaryCandidate("a".repeat(48)))
        assertNull(dictionaryCandidate("a".repeat(49)))
    }

    @Test fun trailingHarakatAndTanweenAreStrippedByCurrentBehavior() {
        assertEquals("كتاب", dictionaryCandidate("كتابٌ"))
        assertEquals("كتاب", dictionaryCandidate("كتابَ"))
    }
}
