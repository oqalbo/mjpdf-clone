// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.shockwave.pdfium.util.Size;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PdfFileLayoutTest extends PdfiumTestSupport.InitializedTest {
    @Test
    public void verticalPagesIncludeSpacingOnlyBetweenPages() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200), new Size(100, 200),
                new Size(100, 200)).build();
        assertEquals(0, file.getPageOffset(0, 1), 0);
        assertEquals(210, file.getPageOffset(1, 1), 0);
        assertEquals(420, file.getPageOffset(2, 1), 0);
        assertEquals(620, file.getDocLen(1), 0);
        assertEquals(840, file.getPageOffset(2, 2), 0);
        assertEquals(1240, file.getDocLen(2), 0);
    }

    @Test
    public void autoSpacingCentersThreeFullHeightPagesInTheirViewports() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200), new Size(100, 200),
                new Size(100, 200)).autoSpacing(true).build();
        for (int page = 0; page < 3; page++) {
            assertEquals(page * 210, file.getPageOffset(page, 1), 0);
        }
        assertEquals(620, file.getDocLen(1), 0);
    }

    @Test
    public void autoSpacingCentersShortPagesWithinSeparateViewports() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 100), new Size(100, 200),
                new Size(100, 150)).autoSpacing(true).build();
        assertEquals(50, file.getPageOffset(0, 1), 0);
        assertEquals(210, file.getPageOffset(1, 1), 0);
        assertEquals(445, file.getPageOffset(2, 1), 0);
        assertEquals(620, file.getDocLen(1), 0);
    }

    @Test
    public void horizontalPagesAdvanceAlongXAndCenterAlongY() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200), new Size(50, 100),
                new Size(100, 150)).vertical(false).build();
        assertEquals(0, file.getPageOffset(0, 1), 0);
        assertEquals(110, file.getPageOffset(1, 1), 0);
        assertEquals(170, file.getPageOffset(2, 1), 0);
        assertEquals(270, file.getDocLen(1), 0);
        assertEquals(0, file.getSecondaryPageOffset(0, 1), 0);
        assertEquals(50, file.getSecondaryPageOffset(1, 1), 0);
        assertEquals(50, file.getSecondaryPageOffset(2, 2), 0);
        assertEquals(200, file.getMaxPageHeight(), 0);
        assertEquals(100, file.getMaxPageWidth(), 0);
    }

    @Test
    public void rightToLeftHorizontalPagesReverseRowOrder() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200), new Size(100, 200),
                new Size(100, 200)).vertical(false).rtl(true).build();
        assertEquals(220, file.getPageOffset(0, 1), 0);
        assertEquals(110, file.getPageOffset(1, 1), 0);
        assertEquals(0, file.getPageOffset(2, 1), 0);
        assertEquals(2, file.getRowAtLayoutIndex(0));
        assertEquals(0, file.getRowAtLayoutIndex(2));
    }

    @Test
    public void rightToLeftAutoSpacingMirrorsLeftToRightEvenWithDifferentWidths() {
        Size[] sizes = {new Size(100, 200), new Size(50, 100), new Size(80, 160)};
        PdfFile leftToRight = new PdfFileTestSupport(sizes).vertical(false).autoSpacing(true).build();
        PdfFile rightToLeft = new PdfFileTestSupport(sizes).vertical(false).autoSpacing(true).rtl(true).build();
        assertEquals(320, rightToLeft.getDocLen(1), 0);
        assertEquals(2, rightToLeft.getRowAtLayoutIndex(0));
        for (int page = 0; page < 3; page++) {
            assertEquals(320 - leftToRight.getPageOffset(page, 1) - sizes[page].getWidth(),
                    rightToLeft.getPageOffset(page, 1), 0);
        }
    }

    @Test
    public void userPageMappingRejectsInvalidIndexesAndClampsNavigation() {
        PdfFile file = userPageFile();
        assertEquals(-1, file.documentPage(-1));
        assertEquals(-1, file.documentPage(2));
        assertEquals(5, file.documentPage(0));
        assertEquals(3, file.documentPage(1));
        assertEquals(1, file.determineValidPageNumberFrom(99));
        assertEquals(0, file.determineValidPageNumberFrom(-3));
    }

    @Test
    public void horizontalAndAutoSpacingLayoutsForceSinglePageRows() {
        for (boolean vertical : new boolean[]{false, true}) {
            PdfFile file = new PdfFileTestSupport(new Size(100, 200), new Size(100, 200))
                    .vertical(vertical).autoSpacing(vertical).pagesPerRow(2).firstPageAlone(true).build();
            assertEquals(1, file.getPagesPerRow());
            assertEquals(2, file.getRowCount());
            assertEquals(false, file.isFirstPageAlone());
        }
    }

    @Test
    public void disposedUserPageMappingFallsBackToIdentity() {
        PdfFile file = userPageFile();
        assertEquals(3, file.documentPage(1));
        file.dispose();
        assertEquals(0, file.documentPage(0));
        assertEquals(1, file.documentPage(1));
        assertEquals(-1, file.documentPage(2));
    }

    private PdfFile userPageFile() {
        return new PdfFileTestSupport(new Size(100, 200), new Size(100, 200), new Size(100, 200),
                new Size(100, 200), new Size(100, 200), new Size(100, 200)).userPages(5, 3).build();
    }
}
