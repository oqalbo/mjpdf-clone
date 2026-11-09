// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static com.github.barteksc.pdfviewer.GestureTestSupport.event;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.MotionEvent;

import com.github.barteksc.pdfviewer.util.SnapEdge;
import org.junit.Ignore;
import org.junit.Test;

public class DragPinchManagerFlingTest extends GestureTestSupport.GestureTest {

    private void enablePageFling(boolean vertical) {
        pdfView.pdfFile = mock(PdfFile.class);
        when(pdfView.isSwipeEnabled()).thenReturn(true);
        when(pdfView.isPageFlingEnabled()).thenReturn(true);
        when(pdfView.isSwipeVertical()).thenReturn(vertical);
        when(pdfView.getZoom()).thenReturn(1f);
        when(pdfView.findFocusPage(0f, 0f)).thenReturn(4);
        when(pdfView.getPageAfterRowStep(4, 1)).thenReturn(5);
        when(pdfView.getPageAfterRowStep(4, -1)).thenReturn(3);
        when(pdfView.findSnapEdge(anyInt())).thenReturn(SnapEdge.START);
        when(pdfView.snapOffsetForPage(5, SnapEdge.START)).thenReturn(500f);
        when(pdfView.snapOffsetForPage(3, SnapEdge.START)).thenReturn(300f);
    }

    @Test
    public void positiveHorizontalVelocityStepsForwardInRightToLeftReading() {
        enablePageFling(false);
        when(pdfView.isHorizontalReadingDirectionRtl()).thenReturn(true);

        assertTrue(manager.onFling(null, event(0f, 0f), 900f, -100f));

        verify(pdfView).getPageAfterRowStep(4, 1);
        verify(animationManager).startPageFlingAnimation(-500f);
    }

    @Test
    public void verticalDirectionFollowsYVelocityDespiteOppositeXVelocity() {
        enablePageFling(true);
        when(pdfView.isHorizontalReadingDirectionRtl()).thenReturn(true);

        manager.onFling(null, event(0f, 0f), 100f, -900f);
        manager.onFling(null, event(0f, 0f), -100f, 900f);

        verify(pdfView).getPageAfterRowStep(4, 1);
        verify(pdfView).getPageAfterRowStep(4, -1);
        verify(animationManager).startPageFlingAnimation(-500f);
        verify(animationManager).startPageFlingAnimation(-300f);
    }

    @Test
    public void horizontalPageFlingRejectsDominantOrEqualCrossAxisVelocity() {
        enablePageFling(false);

        manager.onFling(null, event(0f, 0f), -100f, 101f);
        manager.onFling(null, event(0f, 0f), -100f, 100f);

        verify(animationManager, never()).startPageFlingAnimation(anyFloat());
        verify(pdfView, never()).findFocusPage(anyFloat(), anyFloat());
    }

    @Test
    public void verticalPageFlingRejectsDominantOrEqualCrossAxisVelocity() {
        enablePageFling(true);

        manager.onFling(null, event(0f, 0f), 101f, -100f);
        manager.onFling(null, event(0f, 0f), 100f, -100f);

        verify(animationManager, never()).startPageFlingAnimation(anyFloat());
        verify(pdfView, never()).findFocusPage(anyFloat(), anyFloat());
    }

    @Test
    @Ignore("Pending ruling: Page fling subtracts delta times zoom; expected raw pixel delta to recover the down time page")
    public void zoomedHalfPageDragReconstructsDownTimeOffsetInRawPixels() {
        enablePageFling(false);
        when(pdfView.getZoom()).thenReturn(2f);
        when(pdfView.getPageAfterRowStep(3, 1)).thenReturn(4);
        when(pdfView.snapOffsetForPage(4, SnapEdge.START)).thenReturn(400f);
        when(pdfView.getCurrentXOffset()).thenReturn(-1000f);
        when(pdfView.getCurrentYOffset()).thenReturn(0f);
        doAnswer(invocation -> {
            when(pdfView.getCurrentXOffset()).thenReturn(-1500f);
            return null;
        }).when(pdfView).moveRelativeTo(-500f, -0f);
        when(pdfView.findFocusPage(anyFloat(), anyFloat())).thenAnswer(invocation ->
                ((float) invocation.getArgument(0)) <= -1000f ? 4 : 3);
        MotionEvent down = event(750f, 100f);
        MotionEvent up = event(250f, 100f);

        manager.onScroll(down, up, 500f, 0f);
        verify(pdfView).moveRelativeTo(-500f, -0f);
        manager.onFling(down, up, -900f, 0f);

        verify(pdfView).findFocusPage(-1000f, 500f);
        verify(pdfView).getPageAfterRowStep(4, 1);
        verify(animationManager).startPageFlingAnimation(-500f);
    }

    @Test
    public void verticalPageFillingTheScreenBoundsFlingToCurrentRow() {
        enablePageFling(true);
        configureBoundedRow();
        when(pdfView.pdfFile.getMaxPageWidth()).thenReturn(700f);
        when(pdfView.toCurrentScale(700f)).thenReturn(1400f);

        assertTrue(manager.onFling(null, event(0f, 0f), 123f, -900f));

        verify(animationManager).startFlingAnimation(-2200, -2300, 123, -900,
                -800, 0, -3200, -2000);
        verify(animationManager, never()).startPageFlingAnimation(anyFloat());
    }

    @Test
    public void horizontalPageFillingTheScreenBoundsFlingToCurrentRow() {
        enablePageFling(false);
        configureBoundedRow();
        when(pdfView.pdfFile.getMaxPageHeight()).thenReturn(900f);
        when(pdfView.toCurrentScale(900f)).thenReturn(1800f);

        assertTrue(manager.onFling(null, event(0f, 0f), -900f, 123f));

        verify(animationManager).startFlingAnimation(-2200, -2300, -900, 123,
                -3400, -2000, -1000, 0);
        verify(animationManager, never()).startPageFlingAnimation(anyFloat());
    }

    private void configureBoundedRow() {
        when(pdfView.pageFillsScreen()).thenReturn(true);
        when(pdfView.getZoom()).thenReturn(2f);
        when(pdfView.getCurrentPage()).thenReturn(5);
        when(pdfView.pdfFile.getRowOfPage(5)).thenReturn(2);
        when(pdfView.pdfFile.getRowOffset(2, 2f)).thenReturn(2000f);
        when(pdfView.pdfFile.getRowLength(2, 2f)).thenReturn(2000f);
        when(pdfView.getCurrentXOffset()).thenReturn(-2200f);
        when(pdfView.getCurrentYOffset()).thenReturn(-2300f);
        when(pdfView.getWidth()).thenReturn(600);
        when(pdfView.getHeight()).thenReturn(800);
    }
}
