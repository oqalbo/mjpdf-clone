// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.github.barteksc.pdfviewer.util.SnapEdge;
import org.junit.Test;
import static org.junit.Assert.*;

public class ViewportSnapAndFocusTest extends PdfiumTestSupport.InitializedTest {
    @Test
    public void snapEdgesKeepTheirExactInclusiveAndStrictBoundaries() {
        PdfFile file = PdfFileTestSupport.uniform(3, 100, 400)
                .view(100, 400).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        assertEquals(SnapEdge.CENTER, ViewportMath.findSnapEdge(file, 100, 400, 1, true, 0, -400, true, 1));
        assertEquals(SnapEdge.START, ViewportMath.findSnapEdge(file, 100, 200, 1, true, 0, -400, true, 1));
        assertEquals(SnapEdge.START, ViewportMath.findSnapEdge(file, 100, 200, 1, true, 0, -399, true, 1));
        assertEquals(SnapEdge.NONE, ViewportMath.findSnapEdge(file, 100, 200, 1, true, 0, -401, true, 1));
        assertEquals(SnapEdge.NONE, ViewportMath.findSnapEdge(file, 100, 200, 1, true, 0, -600, true, 1));
        assertEquals(SnapEdge.END, ViewportMath.findSnapEdge(file, 100, 200, 1, true, 0, -601, true, 1));
    }

    @Test
    public void disabledSnapAndInvalidPagesHaveNoEdge() {
        PdfFile file = PdfFileTestSupport.uniform(2, 100, 200)
                .view(100, 200).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        assertEquals(SnapEdge.NONE, ViewportMath.findSnapEdge(file, 100, 200, 1, true, 0, 0, false, 0));
        assertEquals(SnapEdge.NONE, ViewportMath.findSnapEdge(file, 100, 200, 1, true, 0, 0, true, -1));
    }

    @Test
    public void snapOffsetsUseTheWholeRowForEitherMember() {
        PdfFile file = PdfFileTestSupport.uniform(4, 100, 400)
                .view(200, 400).vertical(true).rtl(false).pagesPerRow(2).spacing(0).build();
        assertEquals(400f, ViewportMath.snapOffsetForPage(file, 200, 200, 1, true, 3, SnapEdge.START), 0f);
        assertEquals(500f, ViewportMath.snapOffsetForPage(file, 200, 200, 1, true, 2, SnapEdge.CENTER), 0f);
        assertEquals(600f, ViewportMath.snapOffsetForPage(file, 200, 200, 1, true, 3, SnapEdge.END), 0f);
    }

    @Test
    public void focusAtReversedDocumentEdgesUsesLayoutOrder() {
        PdfFile file = PdfFileTestSupport.uniform(3, 100, 200)
                .view(100, 200).vertical(false).rtl(true).pagesPerRow(1).spacing(0).build();
        assertEquals(2, ViewportMath.findFocusPage(file, 100, 200, 1, false, 0, 0));
        assertEquals(0, ViewportMath.findFocusPage(file, 100, 200, 1, false, -200, 0));
        assertEquals(1, ViewportMath.findFocusPage(file, 100, 200, 1, false, -100, 0));
    }

    @Test
    public void focusAtTwoUpDocumentEndSelectsTheRightPage() {
        PdfFile file = PdfFileTestSupport.uniform(6, 100, 200)
                .view(200, 200).vertical(true).rtl(false).pagesPerRow(2).spacing(0).build();
        assertEquals(0, ViewportMath.findFocusPage(file, 200, 200, 1, true, 0, 0));
        assertEquals(5, ViewportMath.findFocusPage(file, 200, 200, 1, true, 0, -400));
        assertEquals(2, ViewportMath.findFocusPage(file, 200, 200, 1, true, 0, -200));
    }

    @Test
    public void rowStepsLandOnFirstMembersAndClampAtDocumentEdges() {
        PdfFile file = PdfFileTestSupport.uniform(6, 100, 200)
                .view(200, 200).vertical(true).rtl(false).pagesPerRow(2).spacing(0).build();
        assertEquals(4, ViewportMath.getPageAfterRowStep(file, 3, 1));
        assertEquals(0, ViewportMath.getPageAfterRowStep(file, 3, -1));
        assertEquals(4, ViewportMath.getPageAfterRowStep(file, 5, 1));
        assertEquals(0, ViewportMath.getPageAfterRowStep(file, 0, -1));
    }

    @Test
    public void rowFillsScreenOnlyWhenBothEdgesAreStrictlyOutside() {
        PdfFile file = PdfFileTestSupport.uniform(2, 100, 400)
                .view(100, 400).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        assertEquals(false, ViewportMath.pageFillsScreen(file, 100, 200, 1, true, 1, 0, -400));
        assertEquals(true, ViewportMath.pageFillsScreen(file, 100, 200, 1, true, 1, 0, -401));
        assertEquals(false, ViewportMath.pageFillsScreen(file, 100, 200, 1, true, 1, 0, -600));
    }
}
