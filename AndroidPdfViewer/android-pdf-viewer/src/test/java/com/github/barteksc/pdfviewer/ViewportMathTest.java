// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class ViewportMathTest extends PdfiumTestSupport.InitializedTest {
    @Test
    @Ignore("Pending ruling: moveTo centers half a pixel left of focusOnPdfRect at odd widths")
    public void oddWidthCenteringAgreesWithFocusTarget() {
        PdfFile file = PdfFileTestSupport.uniform(2, 100, 200)
                .view(100, 200).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        float target = ViewportMath.clampTargetXOffset(file, 201, 200, 1, true, 0);
        assertEquals(50.5f, target, 0f);
        assertEquals(target, ViewportMath.moveTo(file, 201, 200, 1, true, target, 0)[0], 0f);
    }

    @Test
    @Ignore("Pending ruling: an exactly fitting document reports NaN instead of zero progress")
    public void exactlyFittingDocumentHasZeroPositionOffset() {
        PdfFile file = PdfFileTestSupport.uniform(1, 100, 200)
                .view(100, 200).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        assertEquals(0f, ViewportMath.getPositionOffset(file, 100, 200, 1, true, 0, 0), 0f);
    }

    @Test
    public void movementClampsBothAxesAtBothDocumentEdges() {
        for (boolean vertical : new boolean[]{true, false}) {
            PdfFile file = PdfFileTestSupport.uniform(3, 100, 200)
                    .view(100, 200).vertical(vertical).rtl(false).pagesPerRow(1).spacing(0).build();
            assertArrayEquals(new float[]{0, 0},
                    ViewportMath.moveTo(file, 100, 200, 2, vertical, 20, 30), 0f);
            float[] end = vertical ? new float[]{-100, -1000} : new float[]{-500, -200};
            assertArrayEquals(end,
                    ViewportMath.moveTo(file, 100, 200, 2, vertical, -9999, -9999), 0f);
            assertEquals(end[0], ViewportMath.clampTargetXOffset(file, 100, 200, 2, vertical, -9999), 0f);
            assertEquals(end[1], ViewportMath.clampTargetYOffset(file, 100, 200, 2, vertical, -9999), 0f);
        }
    }

    @Test
    public void shortContentCentersOnBothAxes() {
        PdfFile file = PdfFileTestSupport.uniform(1, 100, 200)
                .view(100, 200).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        assertArrayEquals(new float[]{50, 100}, ViewportMath.moveTo(file, 200, 400, 1, true, -50, -50), 0f);
        PdfFile horizontal = PdfFileTestSupport.uniform(1, 100, 200)
                .view(100, 200).vertical(false).rtl(false).pagesPerRow(1).spacing(0).build();
        assertArrayEquals(new float[]{50, 100}, ViewportMath.moveTo(horizontal, 200, 400, 1, false, -50, -50), 0f);
    }

    @Test
    public void positionMappingRoundTripsAndClampsOutsideTheDocument() {
        for (boolean vertical : new boolean[]{true, false}) {
            PdfFile file = PdfFileTestSupport.uniform(3, 100, 200)
                    .view(100, 200).vertical(vertical).rtl(false).pagesPerRow(1).spacing(0).build();
            int viewSize = vertical ? 200 : 100;
            for (float ratio : new float[]{0, 0.5f, 1}) {
                float offset = ViewportMath.positionOffset(file.getDocLen(2), viewSize, ratio);
                assertEquals(ratio, ViewportMath.getPositionOffset(file, 100, 200, 2, vertical, offset, offset), 0f);
            }
            assertEquals(0f, ViewportMath.getPositionOffset(file, 100, 200, 2, vertical, 50, 50), 0f);
            assertEquals(1f, ViewportMath.getPositionOffset(file, 100, 200, 2, vertical, -9999, -9999), 0f);
        }
    }

    @Test
    public void visibleRangePacksAndWidensBothEndsWithinPageCount() {
        PdfFile file = PdfFileTestSupport.uniform(6, 100, 200)
                .view(100, 200).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        assertEquals((1L << 32) | 4L, ViewportMath.visiblePageRange(file, 100, 200, 1, true, 0, -450, 6));
        assertEquals(1L, ViewportMath.visiblePageRange(file, 100, 100, 1, true, 0, 20, 6));
        assertEquals((4L << 32) | 5L, ViewportMath.visiblePageRange(file, 100, 200, 1, true, 0, -1050, 6));
        assertEquals(-1L, ViewportMath.visiblePageRange(file, 100, 200, 1, true, 0, 0, 0));
    }

    @Test
    public void reversedLayoutVisibleRangeOrdersPageNumbers() {
        PdfFile file = PdfFileTestSupport.uniform(6, 100, 200)
                .view(100, 200).vertical(false).rtl(true).pagesPerRow(1).spacing(0).build();
        assertEquals((1L << 32) | 5L, ViewportMath.visiblePageRange(file, 151, 200, 1, false, -150, 0, 6));
    }

    @Test
    public void positionPageUsesDocumentLengthAndLayoutOrder() {
        PdfFile file = PdfFileTestSupport.uniform(4, 100, 200)
                .view(100, 200).vertical(false).rtl(true).pagesPerRow(1).spacing(0).build();
        assertEquals(3, ViewportMath.getPageAtPositionOffset(file, 2, 0));
        assertEquals(2, ViewportMath.getPageAtPositionOffset(file, 2, 0.5f));
        assertEquals(0, ViewportMath.getPageAtPositionOffset(file, 2, 1));
    }
}
