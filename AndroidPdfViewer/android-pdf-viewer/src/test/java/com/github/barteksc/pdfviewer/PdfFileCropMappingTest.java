// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.model.CropBounds;
import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.shockwave.pdfium.util.Size;
import com.shockwave.pdfium.util.SizeF;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PdfFileCropMappingTest extends PdfiumTestSupport.InitializedTest {
    private static final float TOLERANCE = 0.001f;

    @Test
    public void croppedDocumentOriginAndRenderedWidthMapToTheRotatedCropFrame() {
        CropBounds crop = CropBounds.of(0.1f, 0.2f, 0.9f, 0.8f);
        for (int rotation = 0; rotation < 4; rotation++) {
            for (boolean vertical : new boolean[]{true, false}) {
                PdfFileTestSupport builder = new PdfFileTestSupport(new Size(612, 792), new Size(612, 792))
                        .view(300, 400).vertical(vertical).crop(crop);
                builder.geometry(1, 10, 20, 622, 812, rotation);
                PdfFile file = builder.build();
                file.ensurePageGeometry(1);
                float zoom = 2;
                float x = vertical ? file.getSecondaryPageOffset(1, zoom) : file.getPageOffset(1, zoom);
                float y = vertical ? file.getPageOffset(1, zoom) : file.getSecondaryPageOffset(1, zoom);
                PdfFile.PageGeometry geometry = new PdfFile.PageGeometry(10, 20, 622, 812, rotation);
                float[] expected = new float[2];
                PdfFile.frameTopDownToUser(geometry, 0.1f * geometry.frameWidth(),
                        0.2f * geometry.frameHeight(), expected);
                assertArrayEquals(expected, file.documentToPdfValues(1, zoom, x, y), TOLERANCE);
                SizeF rendered = file.getScaledPageSize(1, zoom);
                float[] userRight = file.documentToPdfValues(1, zoom, x + rendered.getWidth(), y);
                float[] frameRight = new float[2];
                PdfFile.userToFrameTopDown(geometry, userRight[0], userRight[1], frameRight);
                assertEquals(0.8f * geometry.frameWidth(), frameRight[0] - 0.1f * geometry.frameWidth(), TOLERANCE);
                assertEquals(0.2f * geometry.frameHeight(), frameRight[1], TOLERANCE);
                assertArrayEquals(new float[]{x, y, x + rendered.getWidth(), y + rendered.getHeight()},
                        file.frameRectToDocumentValues(1, zoom,
                                0.1f * geometry.frameWidth(), 0.2f * geometry.frameHeight(),
                                0.9f * geometry.frameWidth(), 0.8f * geometry.frameHeight(), false), TOLERANCE);
            }
        }
    }

    @Test
    public void fullPageCropLeavesRenderBoundsUnchanged() {
        assertArrayEquals(new int[]{7, -9, 108, 194}, PdfFile.mapCropRenderBoundsToFullPageValues(
                CropBounds.fullPage(), 7, -9, 101, 203));
    }

    @Test
    public void halfWidthCropDoublesRenderWidthAndShiftsTheOrigin() {
        assertArrayEquals(new int[]{-40, 20, 160, 220}, PdfFile.mapCropRenderBoundsToFullPageValues(
                CropBounds.of(0.25f, 0, 0.75f, 1), 10, 20, 100, 200));
    }

    @Test
    public void oddRenderSizesRoundOriginsAndDimensionsIndependently() {
        assertArrayEquals(new int[]{-50, -51, 152, 155}, PdfFile.mapCropRenderBoundsToFullPageValues(
                CropBounds.of(0.25f, 0.25f, 0.75f, 0.75f), 0, 0, 101, 103));
        assertArrayEquals(new int[]{-31, -11, 104, 58}, PdfFile.mapCropRenderBoundsToFullPageValues(
                CropBounds.of(0.25f, 0.25f, 1, 1), 3, 6, 101, 52));
    }

    @Test
    public void nonPositiveRenderDimensionsBypassCropExpansion() {
        CropBounds crop = CropBounds.of(0.25f, 0.25f, 0.75f, 0.75f);
        assertArrayEquals(new int[]{7, 9, 7, 29}, PdfFile.mapCropRenderBoundsToFullPageValues(crop, 7, 9, 0, 20));
        assertArrayEquals(new int[]{7, 9, 27, 8}, PdfFile.mapCropRenderBoundsToFullPageValues(crop, 7, 9, 20, -1));
    }

    @Test
    public void pageBoundsRejectRectanglesOnlyTouchingAnyEdge() {
        float[][] touching = {{0, 30, 10, 40}, {110, 30, 120, 40}, {20, 0, 30, 20}, {20, 220, 30, 230}};
        for (float[] rect : touching) {
            assertNull(PdfFile.clipToPageBoundsValues(rect[0], rect[1], rect[2], rect[3], 10, 20, 100, 200));
        }
    }

    @Test
    public void partialOverlapClampsEveryPageEdge() {
        assertArrayEquals(new float[]{10, 20, 90, 200},
                PdfFile.clipToPageBoundsValues(0, 0, 90, 200, 10, 20, 100, 200), 0);
        assertArrayEquals(new float[]{30, 40, 110, 220},
                PdfFile.clipToPageBoundsValues(30, 40, 200, 300, 10, 20, 100, 200), 0);
    }

    @Test
    public void viewportSortsInvertedCornersBeforeClipping() {
        assertArrayEquals(new float[]{10, 20, 90, 200},
                PdfFile.clipToViewportValues(90, 200, 0, 0, 10, 20, 100, 200), 0);
        assertNull(PdfFile.clipToViewportValues(120, 40, 110, 30, 10, 20, 100, 200));
    }

    @Test
    public void frameRectangleClipsToCroppedPageOnlyWhenRequested() {
        PdfFile file = new PdfFileTestSupport(new Size(100, 200)).view(80, 120)
                .crop(CropBounds.of(0.1f, 0.2f, 0.9f, 0.8f)).build();
        assertArrayEquals(new float[]{0, 0, 80, 120},
                file.frameRectToDocumentValues(0, 1, 0, 0, 100, 200, true), TOLERANCE);
        assertArrayEquals(new float[]{-10, -40, 90, 160},
                file.frameRectToDocumentValues(0, 1, 0, 0, 100, 200, false), TOLERANCE);
        assertNull(file.frameRectToDocumentValues(0, 1, 90, 40, 100, 100, true));
    }
}
