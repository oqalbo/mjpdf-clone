// Written by Mudlej. License is GPLv3.

package com.shockwave.pdfium;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExtractedTextNormalizationTest {
    @Test
    public void isolatedFathaDoesNotIntroduceASpace() {
        assertEquals("a\u064Eb", PdfiumCore.normalizeExtractedTextStatic("a\uFE76b"));
    }

    @Test
    public void medialFathatanDropsTheTatweel() {
        assertEquals("a\u064Bb", PdfiumCore.normalizeExtractedTextStatic("a\uFE71b"));
    }

    @Test
    public void tailFragmentIsRemoved() {
        assertEquals("ab", PdfiumCore.normalizeExtractedTextStatic("a\uFE73b"));
    }

    @Test
    public void hyphenMarkersJoinWordsAcrossEveryLineEnding() {
        for (String ending : new String[]{"\r\n", "\n", "\r", ""}) {
            assertEquals("ending length " + ending.length(), "example",
                    PdfiumCore.normalizeExtractedTextStatic("exam\uFFFE" + ending + "ple"));
        }
    }

    @Test
    public void ordinaryLineEndingsBecomeSingleNewlines() {
        assertEquals("a\nb\nc\nd", PdfiumCore.normalizeExtractedTextStatic("a\r\nb\rc\nd"));
    }

    @Test
    public void zeroWidthSpacesAreRemovedWithoutRemovingOrdinarySpaces() {
        assertEquals("ab c", PdfiumCore.normalizeExtractedTextStatic("a\u200Bb c"));
    }

    @Test
    public void compatibilityLigaturesExpandToLetters() {
        assertEquals("find", PdfiumCore.normalizeExtractedTextStatic("\uFB01nd"));
    }

    @Test
    public void nullAndEmptyTextBecomeEmpty() {
        assertEquals("", PdfiumCore.normalizeExtractedTextStatic(null));
        assertEquals("", PdfiumCore.normalizeExtractedTextStatic(""));
    }
}
