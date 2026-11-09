// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.RectF;
import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.github.barteksc.pdfviewer.support.Rects;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TextSelectionLinesTest extends PdfiumTestSupport.InitializedTest {
    private TextSelectionManager.LooseGlyphLookup missingGlyph() {
        TextSelectionManager.LooseGlyphLookup lookup = mock(TextSelectionManager.LooseGlyphLookup.class);
        when(lookup.charIndexAtPoint(anyFloat(), anyFloat(), anyFloat())).thenReturn(-1);
        return lookup;
    }

    private List<RectF> lines(float... values) {
        return TextSelectionManager.groupLines(values, missingGlyph(), new float[4]);
    }

    private void assertRect(RectF rect, float left, float bottom, float right, float top) {
        Rects.assertRectF(Rects.rectF(left, top, right, bottom), rect, 0.00001f);
    }

    @Test
    public void halfHeightOverlapKeepsRunsOnSeparateLines() {
        List<RectF> rects = lines(0, 0, 10, 10, 10, 5, 20, 15);
        assertEquals(2, rects.size());
        assertRect(rects.get(0), 0, -1.5f, 10, 11.5f);
        assertRect(rects.get(1), 10, 3.5f, 20, 16.5f);
    }

    @Test
    public void seventyPercentOverlapMergesRunsIntoOneLine() {
        List<RectF> rects = lines(0, 0, 10, 10, 10, 3, 20, 13);
        assertEquals(1, rects.size());
        assertRect(rects.get(0), 0, -1.95f, 20, 14.95f);
    }

    @Test
    public void sameLineRunsSortByLeftAndMergeOnlyAcrossSmallGaps() {
        List<RectF> rects = lines(30, 0, 40, 10, 14, 0, 20, 10, 0, 0, 10, 10);
        assertEquals(2, rects.size());
        assertRect(rects.get(0), 0, -1.5f, 20, 11.5f);
        assertRect(rects.get(1), 30, -1.5f, 40, 11.5f);
    }

    @Test
    public void acceptedLooseBoxControlsHeightAndInclusiveMergeGap() {
        TextSelectionManager.LooseGlyphLookup lookup = mock(TextSelectionManager.LooseGlyphLookup.class);
        when(lookup.charIndexAtPoint(5, 5, 5)).thenReturn(7);
        when(lookup.looseCharBox(eq(7), any())).thenAnswer(invocation -> {
            float[] out = invocation.getArgument(1);
            out[1] = -5;
            out[3] = 20;
            return true;
        });
        List<RectF> rects = TextSelectionManager.groupLines(new float[]{0, 0, 10, 10, 22.5f, 0, 30, 10}, lookup, new float[4]);
        assertEquals(1, rects.size());
        assertRect(rects.get(0), 0, -5, 30, 20);
        verify(lookup).charIndexAtPoint(5, 5, 5);
        verify(lookup).looseCharBox(eq(7), any());
    }

    @Test
    public void excessiveLooseHeightFallsBackToFifteenPercentPadding() {
        TextSelectionManager.LooseGlyphLookup lookup = mock(TextSelectionManager.LooseGlyphLookup.class);
        when(lookup.charIndexAtPoint(anyFloat(), anyFloat(), anyFloat())).thenReturn(2);
        when(lookup.looseCharBox(eq(2), any())).thenAnswer(invocation -> {
            float[] out = invocation.getArgument(1);
            out[1] = -5;
            out[3] = 21;
            return true;
        });
        List<RectF> rects = TextSelectionManager.groupLines(new float[]{0, 0, 10, 10}, lookup, new float[4]);
        assertEquals(1, rects.size());
        assertRect(rects.get(0), 0, -1.5f, 10, 11.5f);
    }

    @Test
    public void invertedRunsAreNormalizedAndEmptyOrIncompleteRunsAreSkipped() {
        List<RectF> rects = lines(10, 10, 0, 0, 20, 0, 20, 10, 40, 2, 50, 2, 99);
        assertEquals(1, rects.size());
        assertRect(rects.get(0), 0, -1.5f, 10, 11.5f);
    }

    @Test
    public void copiedTextStripsMarkersAndNormalizesLineEndingsAndLigatures() {
        assertEquals("abcdef\ng\nh\ni", TextSelectionManager.normalizeCopiedText(
                "a\uFFFE\r\nb\uFFFE\nc\uFFFE\rd\uFFFEe\u200Bf\r\ng\rh\ni"));
        assertEquals("fi", TextSelectionManager.normalizeCopiedText("\uFB01"));
        assertEquals("", TextSelectionManager.normalizeCopiedText(null));
        assertEquals("", TextSelectionManager.normalizeCopiedText(""));
    }

    @Test
    public void wordCharactersIncludeCombiningMarksAndUnderscores() {
        for (int codePoint : new int[]{'a', '7', '_', 0x0301}) {
            assertEquals(true, TextSelectionManager.isWordChar(codePoint));
        }
        for (int codePoint : new int[]{' ', '\n', '.', '-', '!'}) {
            assertEquals(false, TextSelectionManager.isWordChar(codePoint));
        }
    }
}
