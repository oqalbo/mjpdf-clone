// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.shockwave.pdfium.util.Size;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PdfFileTwoUpLayoutTest extends PdfiumTestSupport.InitializedTest {
    @Test
    public void firstPageAloneOccupiesTheRightColumnBeforePairedRows() {
        PdfFile file = fivePages(true);
        assertEquals(3, file.getRowCount());
        int[] rows = {0, 1, 1, 2, 2};
        for (int page = 0; page < rows.length; page++) {
            assertEquals(rows[page], file.getRowOfPage(page));
        }
        assertEquals(0, file.getRowFirstPage(0));
        assertEquals(1, file.getRowFirstPage(1));
        assertEquals(3, file.getRowFirstPage(2));
        assertEquals(1, file.getPagesInRow(0));
        assertEquals(2, file.getPagesInRow(1));
        assertEquals(2, file.getPagesInRow(2));
        assertEquals(110, file.getSecondaryPageOffset(0, 1), 0);
        assertEquals(0, file.getSecondaryPageOffset(1, 1), 0);
        assertEquals(110, file.getSecondaryPageOffset(2, 1), 0);
        assertEquals(210, file.getMaxPageWidth(), 0);
        assertEquals(0, file.getPageAtOffset(0, 1000, 1));
    }

    @Test
    public void oddLastPageWithoutCoverOccupiesTheLeftColumn() {
        PdfFile file = fivePages(false);
        assertEquals(3, file.getRowCount());
        int[] rows = {0, 0, 1, 1, 2};
        for (int page = 0; page < rows.length; page++) {
            assertEquals(rows[page], file.getRowOfPage(page));
        }
        assertEquals(0, file.getRowFirstPage(0));
        assertEquals(2, file.getRowFirstPage(1));
        assertEquals(4, file.getRowFirstPage(2));
        assertEquals(1, file.getPagesInRow(2));
        assertEquals(0, file.getSecondaryPageOffset(4, 1), 0);
        assertEquals(4, file.getPageAtOffset(file.getPageOffset(4, 1), 1000, 1));
    }

    @Test
    public void shorterPageIsTopAlignedWithinTheTallerRow() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 100), new Size(100, 200),
                new Size(50, 75), new Size(100, 150), new Size(100, 120))
                .view(210, 200).pagesPerRow(2).firstPageAlone(true).build();
        assertEquals(100, file.getRowLength(0, 1), 0);
        assertEquals(200, file.getRowLength(1, 1), 0);
        assertEquals(150, file.getRowLength(2, 1), 0);
        assertEquals(110, file.getPageOffset(1, 1), 0);
        assertEquals(110, file.getPageOffset(2, 1), 0);
        assertEquals(320, file.getPageOffset(3, 1), 0);
        assertEquals(470, file.getDocLen(1), 0);
        assertEquals(135, file.getSecondaryPageOffset(2, 1), 0);
    }

    @Test
    public void exactZoomedColumnMidpointSelectsTheRightPage() {
        PdfFile file = fivePages(true);
        for (float zoom : new float[]{1, 2}) {
            float rowOffset = 210 * zoom;
            assertEquals(1, file.getPageAtOffset(rowOffset, 104.99f * zoom, zoom));
            assertEquals(2, file.getPageAtOffset(rowOffset, 105 * zoom, zoom));
            assertEquals(2, file.getPageAtOffset(rowOffset, 105.01f * zoom, zoom));
        }
    }

    private PdfFile fivePages(boolean firstPageAlone) {
        return new PdfFileTestSupport(new Size(100, 200), new Size(100, 200), new Size(100, 200),
                new Size(100, 200), new Size(100, 200)).view(210, 200).pagesPerRow(2)
                .firstPageAlone(firstPageAlone).build();
    }
}
