// Written by Mudlej. License is GPLv3.

package com.shockwave.pdfium;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FontEntryParsingTest {
    @Test
    public void oneMarksAFontAsEmbedded() {
        PdfDocument.FontInfo font = PdfiumCore.parseFontEntry("Arial\t1");
        assertNotNull(font);
        assertEquals("Arial", font.getName());
        assertTrue(font.isEmbedded());
    }

    @Test
    public void lastTabSeparatesTheFlagFromANameContainingTabs() {
        PdfDocument.FontInfo font = PdfiumCore.parseFontEntry("Name\twith\ttabs\t0");
        assertNotNull(font);
        assertEquals("Name\twith\ttabs", font.getName());
        assertFalse(font.isEmbedded());
    }

    @Test
    public void entriesWithoutANameOrSeparatorAreSkipped() {
        for (String entry : new String[]{null, "", "\t1", "Arial"}) {
            assertNull("entry " + entry, PdfiumCore.parseFontEntry(entry));
        }
    }

    @Test
    public void onlyTheExactOneFlagMeansEmbedded() {
        for (String flag : new String[]{"", "true", "0", "01", " 1", "1 "}) {
            PdfDocument.FontInfo font = PdfiumCore.parseFontEntry("Arial\t" + flag);
            assertNotNull(font);
            assertEquals("Arial", font.getName());
            assertFalse("flag " + flag, font.isEmbedded());
        }
    }
}
