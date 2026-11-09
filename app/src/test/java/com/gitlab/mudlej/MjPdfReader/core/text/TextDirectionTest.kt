// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDirectionTest {

    @Test
    fun mostlyEnglishWithOneArabicWordStaysLeftToRight() {
        assertFalse(TextDirection.isRtlBaseDirection("The book كتاب is good."))
    }

    @Test
    fun mostlyArabicOpeningWithLatinIsRightToLeft() {
        assertTrue(TextDirection.isRtlBaseDirection("PDF هو صيغة ملف"))
    }

    @Test
    fun arabicQuoteWithEnglishAttributionStaysRightToLeft() {
        assertTrue(TextDirection.isRtlBaseDirection("الحياة جميلة said Mahmoud"))
    }

    @Test
    fun pureScriptsResolveToTheirOwnDirection() {
        assertFalse(TextDirection.isRtlBaseDirection("A plain English sentence."))
        assertTrue(TextDirection.isRtlBaseDirection("جملة عربية كاملة"))
        assertTrue(TextDirection.isRtlBaseDirection("משפט בעברית"))
    }

    @Test
    fun digitsNeverDecideTheDirection() {
        assertFalse(TextDirection.isRtlBaseDirection("1234567890"))
        assertFalse(TextDirection.isRtlBaseDirection("2024 was a year."))
        assertTrue(TextDirection.isRtlBaseDirection("سنة 2024 كانت"))
    }

    @Test
    fun harakatDoNotInflateTheArabicCount() {
        assertFalse(TextDirection.isRtlBaseDirection("The word مَُِرْحَبًا appears once here in a long English sentence."))
    }

    @Test
    fun neutralsAndPunctuationAloneAreLeftToRight() {
        assertFalse(TextDirection.isRtlBaseDirection(""))
        assertFalse(TextDirection.isRtlBaseDirection("   ...!?  "))
    }

    @Test
    fun supplementaryPlaneRightToLeftIsDetected() {
        assertTrue(TextDirection.isRtlBaseDirection("𐤀𐤁𐤂"))
    }

    @Test
    fun aTieKeepsTheFirstStrongVerdict() {
        assertFalse(TextDirection.isRtlBaseDirection("ab اب"))
        assertTrue(TextDirection.isRtlBaseDirection("اب ab"))
    }
}
