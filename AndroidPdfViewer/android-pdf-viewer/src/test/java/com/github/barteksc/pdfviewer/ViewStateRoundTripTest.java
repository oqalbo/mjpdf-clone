// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import org.junit.Test;
import static org.junit.Assert.*;

public class ViewStateRoundTripTest extends PdfiumTestSupport.InitializedTest {
    @Test
    public void twoUpOffsetsRoundTripAtZoomTwo() {
        PdfFile file = PdfFileTestSupport.uniform(6, 100, 200)
                .view(200, 200).vertical(true).rtl(false).pagesPerRow(2).spacing(0).build();
        PDFView.ViewState state = ViewportMath.captureViewState(file, 200, 200, 2, true, -100, -450, 2, false, 2, false);
        assertEquals(1f, state.relativeCrossAxisCenter, 0f);
        assertEquals(0.375f, state.pageCenterOffsetRatio, 0f);
        float[] restored = ViewportMath.applyViewState(file, 200, 200, 2, true, state, 2, file.getPageLength(2, 2));
        assertArrayEquals(new float[]{-100, -450}, restored, 0f);
        assertArrayEquals(restored, ViewportMath.moveTo(file, 200, 200, 2, true, restored[0], restored[1]), 0f);
    }

    @Test
    public void loweredMaximumZoomDriftsCrossAxisByFiftyPixels() {
        PdfFile file = PdfFileTestSupport.uniform(6, 100, 200)
                .view(200, 200).vertical(true).rtl(false).pagesPerRow(2).spacing(0).build();
        PDFView.ViewState state = ViewportMath.captureViewState(file, 200, 200, 2, true, -100, -450, 2, false, 2, false);
        float zoom = ViewportMath.validZoom(state.zoom, 1, 1.5f);
        assertEquals(1.5f, zoom, 0f);
        float[] restored = ViewportMath.applyViewState(file, 200, 200, zoom, true, state, 2, file.getPageLength(2, zoom));
        float[] moved = ViewportMath.moveTo(file, 200, 200, zoom, true, restored[0], restored[1]);
        assertArrayEquals(new float[]{-100, -312.5f}, moved, 0f);
        float preservedCenterOffset = -((-(-100f) + 100) / 2 * zoom) + 100;
        assertEquals(-50f, preservedCenterOffset, 0f);
        assertEquals(-50f, moved[0] - preservedCenterOffset, 0f);
    }

    @Test
    public void invalidStateRatiosFallBackToPageAndCrossAxisCenters() {
        PdfFile file = PdfFileTestSupport.uniform(3, 100, 200)
                .view(100, 200).vertical(false).rtl(false).pagesPerRow(1).spacing(0).build();
        PDFView.ViewState state = new PDFView.ViewState(Float.NaN, 1, false, false, Float.NaN, Float.POSITIVE_INFINITY, 1, false);
        assertEquals(1f, ViewportMath.validZoom(state.zoom, 2, 3), 0f);
        assertArrayEquals(new float[]{-100, 0}, ViewportMath.applyViewState(file, 100, 200, 1, false, state, 1, 100), 0f);
    }
}
