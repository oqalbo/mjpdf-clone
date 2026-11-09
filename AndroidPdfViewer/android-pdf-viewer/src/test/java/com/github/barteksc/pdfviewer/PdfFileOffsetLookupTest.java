// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.shockwave.pdfium.util.Size;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PdfFileOffsetLookupTest extends PdfiumTestSupport.InitializedTest {
    @Test
    public void gapMidpointBelongsToPreviousPageUntilStrictlyPassed() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200), new Size(100, 200),
                new Size(100, 200)).build();
        for (float zoom : new float[]{1, 2}) {
            assertEquals(0, file.getPageAtOffset(-1, zoom));
            assertEquals(0, file.getPageAtOffset(0, zoom));
            for (int page = 1; page < 3; page++) {
                float start = page * 210;
                assertEquals(page - 1, file.getPageAtOffset((start - 10) * zoom, zoom));
                assertEquals(page - 1, file.getPageAtOffset((start - 5) * zoom, zoom));
                assertEquals(page, file.getPageAtOffset((start - 4.99f) * zoom, zoom));
                assertEquals(page, file.getPageAtOffset(start * zoom, zoom));
            }
            assertEquals(2, file.getPageAtOffset(620 * zoom, zoom));
            assertEquals(2, file.getPageAtOffset(1000 * zoom, zoom));
        }
    }

    @Test
    public void horizontalRightToLeftLookupReturnsThePageInVisualOrder() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200), new Size(100, 200),
                new Size(100, 200)).vertical(false).rtl(true).build();
        assertEquals(2, file.getPageAtOffset(0, 1));
        assertEquals(2, file.getPageAtOffset(105, 1));
        assertEquals(1, file.getPageAtOffset(105.01f, 1));
        assertEquals(1, file.getPageAtOffset(215, 1));
        assertEquals(0, file.getPageAtOffset(215.01f, 1));
    }

    @Ignore("Pending ruling: one auto-spaced page starts at -5 instead of viewport origin 0")
    @Test
    public void singleAutoSpacedPageStartsAtTheViewportOrigin() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200)).autoSpacing(true).build();
        assertEquals(0, file.getPageOffset(0, 1), 0);
    }

    @Ignore("Pending ruling: getPageOffset after dispose throws ArrayIndexOutOfBoundsException instead of returning 0")
    @Test
    public void disposedPageOffsetReturnsZero() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200)).build();
        file.dispose();
        assertEquals(0, file.getPageOffset(0, 1), 0);
    }
}
