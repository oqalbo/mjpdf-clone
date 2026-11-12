package com.github.barteksc.pdfviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextSelectionTest {

    @Test
    public void samePageSelectionKeepsItsRange() {
        TextSelection selection = selection(2, 3, 2, 8);

        assertTrue(selection.isSinglePage());
        assertEquals(2, selection.startPage());
        assertEquals(3, selection.startChar());
        assertEquals(2, selection.endPage());
        assertEquals(8, selection.endChar());
    }

    @Test
    public void forwardCrossPageSelectionKeepsStartAndEndPages() {
        TextSelection selection = selection(1, 40, 3, 12);

        assertFalse(selection.isSinglePage());
        assertEquals(1, selection.startPage());
        assertEquals(40, selection.startChar());
        assertEquals(3, selection.endPage());
        assertEquals(12, selection.endChar());
    }

    @Test
    public void reverseCrossPageSelectionOrdersItsEndpoints() {
        TextSelection selection = selection(3, 12, 1, 40);

        assertFalse(selection.isSinglePage());
        assertEquals(1, selection.startPage());
        assertEquals(40, selection.startChar());
        assertEquals(3, selection.endPage());
        assertEquals(12, selection.endChar());
    }

    @Test
    public void reverseSamePageSelectionOrdersItsCharacters() {
        TextSelection selection = selection(4, 9, 4, 2);

        assertTrue(selection.isSinglePage());
        assertEquals(2, selection.startChar());
        assertEquals(9, selection.endChar());
    }

    @Test
    public void emptySelectionUsesTheAnchorPosition() {
        TextSelection selection = selection(1, 5, 1, 5);

        assertTrue(selection.isEmpty());
        assertTrue(selection.isSinglePage());
        assertEquals(5, selection.startChar());
        assertEquals(5, selection.endChar());
    }

    private static TextSelection selection(int anchorPage, int anchorChar,
                                           int focusPage, int focusChar) {
        TextSelection selection = new TextSelection();
        selection.anchorPage = anchorPage;
        selection.anchorChar = anchorChar;
        selection.focusPage = focusPage;
        selection.focusChar = focusChar;
        return selection;
    }
}
