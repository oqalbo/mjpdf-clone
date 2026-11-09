// Written by Mudlej. License is GPLv3.

package com.shockwave.pdfium;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PresentationFormMarksTest {
    @Test
    public void everyPresentationFormMapsToItsDocumentedMark() {
        String[] expected = {
                "\u064B", "\u064B", "\u064C", "\u200B",
                "\u064D", "\uFE75", "\u064E", "\u064E",
                "\u064F", "\u064F", "\u0650", "\u0650",
                "\u0651", "\u0651", "\u0652", "\u0652"
        };
        for (int offset = 0; offset < expected.length; offset++) {
            char form = (char) (0xFE70 + offset);
            assertEquals("U+" + Integer.toHexString(form), "a" + expected[offset] + "b",
                    PdfiumCore.mapPresentationFormMarks("a" + form + "b"));
        }
    }

    @Test
    public void charactersOutsideTheTablePassThroughUnchanged() {
        String text = "a\u064B\u0640\uFE6F\uFE80\uFB01\u200B\uD83D\uDE00";
        assertEquals(text, PdfiumCore.mapPresentationFormMarks(text));
    }

    @Test
    public void multiplePresentationFormsKeepTheirOrder() {
        assertEquals("\u064E\u0651\u200B\u0652",
                PdfiumCore.mapPresentationFormMarks("\uFE76\uFE7D\uFE73\uFE7F"));
    }

    @Test
    public void emptyTextStaysEmpty() {
        assertEquals("", PdfiumCore.mapPresentationFormMarks(""));
    }
}
