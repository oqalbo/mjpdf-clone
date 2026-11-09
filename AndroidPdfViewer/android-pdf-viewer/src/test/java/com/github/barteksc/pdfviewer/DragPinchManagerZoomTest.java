// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static com.github.barteksc.pdfviewer.GestureTestSupport.event;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class DragPinchManagerZoomTest extends GestureTestSupport.GestureTest {

    private void configureDoubleTap(float zoom, float mid, float max, boolean threeStep) {
        when(pdfView.isDoubleTapEnabled()).thenReturn(true);
        when(pdfView.getZoom()).thenReturn(zoom);
        when(pdfView.getMidZoom()).thenReturn(mid);
        when(pdfView.getMaxZoom()).thenReturn(max);
        when(pdfView.isThreeStepDoubleTapZoom()).thenReturn(threeStep);
    }

    private void assertDoubleTapZoom(float expected) {
        assertTrue(manager.onDoubleTap(event(40f, 50f)));
        verify(pdfView).zoomWithAnimation(40f, 50f, expected);
        verify(pdfView, never()).resetZoomToFitPageWithAnimation();
    }

    private void assertDoubleTapResets() {
        assertTrue(manager.onDoubleTap(event(40f, 50f)));
        verify(pdfView).resetZoomToFitPageWithAnimation();
        verify(pdfView, never()).zoomWithAnimation(anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    public void doubleTapBelowNormalZoomReturnsToNormalZoom() {
        configureDoubleTap(0.75f, 2f, 3f, true);
        assertDoubleTapZoom(1f);
    }

    @Test
    public void doubleTapBetweenNormalAndMidZoomGoesToMidZoom() {
        configureDoubleTap(1.5f, 2f, 3f, true);
        assertDoubleTapZoom(2f);
    }

    @Test
    public void doubleTapCapsMidZoomAtMaximumZoom() {
        configureDoubleTap(1f, 4f, 3f, true);
        assertDoubleTapZoom(3f);
    }

    @Test
    public void doubleTapAtMidZoomGoesToMaximumWithThreeStepsEnabled() {
        configureDoubleTap(2f, 2f, 3f, true);
        assertDoubleTapZoom(3f);
    }

    @Test
    public void doubleTapAtMaximumZoomResetsToFitPage() {
        configureDoubleTap(3f, 2f, 3f, true);
        assertDoubleTapResets();
    }

    @Test
    public void doubleTapAtMidZoomResetsToFitPageWithThreeStepsDisabled() {
        configureDoubleTap(2f, 2f, 3f, false);
        assertDoubleTapResets();
    }

    @Test
    @Ignore("Pending ruling: Pinch reaches 0.5 with user minimum 1; expected the user minimum to hold")
    public void pinchCannotZoomBelowTheUserMinimum() {
        when(pdfView.getZoom()).thenReturn(1f);
        when(pdfView.getMinZoom()).thenReturn(1f);
        when(pdfView.getMaxZoom()).thenReturn(3f);
        ScaleGestureDetector detector = mock(ScaleGestureDetector.class);
        when(detector.getScaleFactor()).thenReturn(0.5f);

        assertTrue(manager.onScale(detector));

        ArgumentCaptor<Float> factor = ArgumentCaptor.forClass(Float.class);
        verify(pdfView).zoomCenteredRelativeTo(factor.capture(), any());
        assertEquals(1f, factor.getValue(), 0f);
    }

    @Test
    public void snapshotReloadsAtEachOutwardStepAndAdvancesItsReferenceZoom() {
        ScaleGestureDetector detector = beginSnapshotScale(1f);
        scaleTo(detector, 1.249f);
        verify(pdfView, never()).loadViewportSnapshot();
        scaleTo(detector, 1.25f);
        verify(pdfView).loadViewportSnapshot();
        scaleTo(detector, 1.5f);
        verify(pdfView).loadViewportSnapshot();
        scaleTo(detector, 1.5625f);
        verify(pdfView, times(2)).loadViewportSnapshot();
    }

    @Test
    public void snapshotReloadsAtEachInwardStepAndAdvancesItsReferenceZoom() {
        ScaleGestureDetector detector = beginSnapshotScale(2f);
        scaleTo(detector, 1.601f);
        verify(pdfView, never()).loadViewportSnapshot();
        scaleTo(detector, 1.6f);
        verify(pdfView).loadViewportSnapshot();
        scaleTo(detector, 1.3f);
        verify(pdfView).loadViewportSnapshot();
        scaleTo(detector, 1.28f);
        verify(pdfView, times(2)).loadViewportSnapshot();
    }

    private ScaleGestureDetector beginSnapshotScale(float initialZoom) {
        when(pdfView.getZoom()).thenReturn(initialZoom);
        when(pdfView.getMinZoom()).thenReturn(0.5f);
        when(pdfView.getMaxZoom()).thenReturn(4f);
        when(pdfView.doRenderDuringScale()).thenReturn(true);
        ScaleGestureDetector detector = mock(ScaleGestureDetector.class);
        when(detector.getScaleFactor()).thenReturn(1f);
        assertTrue(manager.onScaleBegin(detector));
        return detector;
    }

    private void scaleTo(ScaleGestureDetector detector, float zoom) {
        when(pdfView.getZoom()).thenReturn(zoom);
        assertTrue(manager.onScale(detector));
    }
}
