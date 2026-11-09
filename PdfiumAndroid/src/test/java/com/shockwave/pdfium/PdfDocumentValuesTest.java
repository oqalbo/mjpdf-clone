// Written by Mudlej. License is GPLv3.

package com.shockwave.pdfium;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PdfDocumentValuesTest {
    @Test
    public void settingBookmarkChildrenTwiceAppendsDuplicateChildren() {
        PdfDocument.Bookmark parent = new PdfDocument.Bookmark();
        PdfDocument.Bookmark first = new PdfDocument.Bookmark();
        PdfDocument.Bookmark second = new PdfDocument.Bookmark();
        List<PdfDocument.Bookmark> children = Arrays.asList(first, second);
        assertFalse(parent.hasChildren());
        parent.setChildren(children);
        parent.setChildren(children);
        assertEquals(Arrays.asList(first, second, first, second), parent.getChildren());
        assertTrue(parent.hasChildren());
    }

    @Test
    public void nullHighlightTextFieldsBecomeEmptyAcrossConstructors() {
        PdfDocument.HighlightAnnotation[] annotations = {
                new PdfDocument.HighlightAnnotation(0, null, null, null),
                new PdfDocument.HighlightAnnotation(0, null, null, null, 0),
                new PdfDocument.HighlightAnnotation(0, null, null, null, 0, false),
                new PdfDocument.HighlightAnnotation(0, null, null, null, 0, false, null, null),
                new PdfDocument.HighlightAnnotation(0, null, null, null, 0, false, false, null, null)
        };
        for (PdfDocument.HighlightAnnotation annotation : annotations) {
            assertEquals("", annotation.getGroupKey());
            assertEquals("", annotation.getQuote());
            assertEquals("", annotation.getNote());
            assertEquals("", annotation.getCreationDate());
        }
    }

    @Test
    public void nonNullHighlightTextFieldsArePreserved() {
        PdfDocument.HighlightAnnotation annotation = new PdfDocument.HighlightAnnotation(
                0, "group", null, "quote", 0, false, false, "note", "date");
        assertEquals("group", annotation.getGroupKey());
        assertEquals("quote", annotation.getQuote());
        assertEquals("note", annotation.getNote());
        assertEquals("date", annotation.getCreationDate());
    }

    @Test
    public void nullFontNamesAreEquivalentToEmptyNames() {
        for (boolean embedded : new boolean[]{false, true}) {
            PdfDocument.FontInfo missing = new PdfDocument.FontInfo(null, embedded);
            PdfDocument.FontInfo empty = new PdfDocument.FontInfo("", embedded);
            assertEquals("", missing.getName());
            assertEquals(missing, empty);
            assertEquals(empty, missing);
            assertEquals(missing.hashCode(), empty.hashCode());
        }
    }

    @Test
    public void fontIdentityIncludesBothNameAndEmbeddedFlag() {
        PdfDocument.FontInfo font = new PdfDocument.FontInfo("Arial", true);
        PdfDocument.FontInfo copy = new PdfDocument.FontInfo("Arial", true);
        assertEquals(font, copy);
        assertEquals(font.hashCode(), copy.hashCode());
        assertNotEquals(font, new PdfDocument.FontInfo("Arial", false));
        assertNotEquals(font, new PdfDocument.FontInfo("arial", true));
    }

    @Test
    public void readOnlyChecksItsBitEvenWhenOtherFlagsAreSet() {
        for (int flags : new int[]{0, 2, 4096, 4098}) {
            assertFalse("flags " + flags, field(flags).isReadOnly());
        }
        for (int flags : new int[]{1, 3, 4097, -1}) {
            assertTrue("flags " + flags, field(flags).isReadOnly());
        }
    }

    @Test
    public void multilineChecksItsBitEvenWhenOtherFlagsAreSet() {
        for (int flags : new int[]{0, 1, 2048, 8192}) {
            assertFalse("flags " + flags, field(flags).isMultiline());
        }
        for (int flags : new int[]{4096, 4097, 6144, -1}) {
            assertTrue("flags " + flags, field(flags).isMultiline());
        }
    }

    private static PdfDocument.FormField field(int flags) {
        return new PdfDocument.FormField(0, PdfDocument.FormField.TYPE_TEXT_FIELD,
                flags, "name", "alternate", "value", false);
    }
}
