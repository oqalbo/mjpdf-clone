// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

import com.github.barteksc.pdfviewer.listener.Callbacks;
import com.github.barteksc.pdfviewer.scroll.ScrollHandle;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class DragPinchManagerTest {

    private PDFView pdfView;
    private DragPinchManager manager;
    private Callbacks callbacks;
    private ScaleGestureDetector scaleGestureDetector;

    @Before
    public void setUp() {
        callbacks = new Callbacks();
        pdfView = mock(PDFView.class);
        pdfView.callbacks = callbacks;
        pdfView.cacheManager = mock(CacheManager.class);
        Context context = mock(Context.class);
        when(pdfView.getContext()).thenReturn(context);

        ViewConfiguration configuration = mock(ViewConfiguration.class);
        when(configuration.getScaledTouchSlop()).thenReturn(8);
        try (MockedConstruction<GestureDetector> ignoredGestureDetector =
                     mockConstruction(GestureDetector.class);
             MockedConstruction<ScaleGestureDetector> ignoredScaleGestureDetector =
                     mockConstruction(ScaleGestureDetector.class,
                             (mock, construction) -> scaleGestureDetector = mock);
             MockedStatic<ViewConfiguration> mockedViewConfiguration =
                     mockStatic(ViewConfiguration.class)) {
            mockedViewConfiguration.when(() -> ViewConfiguration.get(context)).thenReturn(configuration);
            manager = new DragPinchManager(pdfView, mock(AnimationManager.class));
        }
        manager.enable();
    }

    @Test
    public void consumedTapUp_sameGestureSkipsSemanticDispatchButCompletesConfirmation() {
        int[] immediateCalls = {0};
        int[] confirmedCalls = {0};
        ScrollHandle scrollHandle = mock(ScrollHandle.class);
        when(pdfView.getScrollHandle()).thenReturn(scrollHandle);
        when(pdfView.getPositionOffset()).thenReturn(0.25f);
        callbacks.setOnTapUp(event -> {
            immediateCalls[0]++;
            return true;
        });
        callbacks.setOnTap(event -> {
            confirmedCalls[0]++;
            return false;
        });
        MotionEvent tapUp = eventWithDownTime(100L);
        MotionEvent confirmation = eventWithDownTime(100L);

        assertTrue(manager.onSingleTapUp(tapUp));
        assertTrue(manager.onSingleTapConfirmed(confirmation));

        assertEquals(1, immediateCalls[0]);
        assertEquals(0, confirmedCalls[0]);
        verify(pdfView, never()).performLinkTap(0f, 0f);
        verify(scrollHandle).setScroll(0.25f);
        verify(pdfView).performClick();
    }

    @Test
    public void unconsumedTapUp_sameGestureRetainsConfirmedTapFallback() {
        int[] confirmedCalls = {0};
        callbacks.setOnTapUp(event -> false);
        callbacks.setOnTap(event -> {
            confirmedCalls[0]++;
            return false;
        });
        MotionEvent tapUp = eventWithDownTime(200L);
        MotionEvent confirmation = eventWithDownTime(200L);

        assertFalse(manager.onSingleTapUp(tapUp));
        assertTrue(manager.onSingleTapConfirmed(confirmation));

        assertEquals(1, confirmedCalls[0]);
        verify(pdfView).performLinkTap(0f, 0f);
        verify(pdfView).performClick();
    }

    @Test
    public void consumedTapUp_doesNotSuppressDifferentGestureConfirmation() {
        int[] confirmedCalls = {0};
        callbacks.setOnTapUp(event -> true);
        callbacks.setOnTap(event -> {
            confirmedCalls[0]++;
            return true;
        });

        assertTrue(manager.onSingleTapUp(eventWithDownTime(300L)));
        assertTrue(manager.onSingleTapConfirmed(eventWithDownTime(301L)));

        assertEquals(1, confirmedCalls[0]);
        verify(pdfView, never()).performLinkTap(0f, 0f);
        verify(pdfView).performClick();
    }

    @Test
    public void consumedTapUp_doesNotPreventDoubleTapZoom() {
        int[] confirmedCalls = {0};
        callbacks.setOnTapUp(event -> true);
        callbacks.setOnTap(event -> {
            confirmedCalls[0]++;
            return true;
        });
        when(pdfView.isDoubleTapEnabled()).thenReturn(true);
        when(pdfView.getZoom()).thenReturn(PDFView.NORMAL_SCALE);
        when(pdfView.getMidZoom()).thenReturn(2f);
        when(pdfView.getMaxZoom()).thenReturn(3f);
        MotionEvent firstTap = eventWithDownTime(400L);
        when(firstTap.getX()).thenReturn(4f);
        when(firstTap.getY()).thenReturn(5f);

        assertTrue(manager.onSingleTapUp(eventWithDownTime(400L)));
        assertTrue(manager.onDoubleTap(firstTap));

        assertEquals(0, confirmedCalls[0]);
        verify(pdfView).zoomWithAnimation(4f, 5f, 2f);
        verify(pdfView, never()).performClick();
    }

    @Test
    public void cancelFromDifferentGesture_doesNotClearPendingConsumedTap() {
        int[] confirmedCalls = {0};
        callbacks.setOnTapUp(event -> true);
        callbacks.setOnTap(event -> {
            confirmedCalls[0]++;
            return true;
        });
        MotionEvent cancel = eventWithDownTime(501L);
        when(cancel.getActionMasked()).thenReturn(MotionEvent.ACTION_CANCEL);

        assertTrue(manager.onSingleTapUp(eventWithDownTime(500L)));
        assertFalse(manager.onTouch(pdfView, cancel));
        assertTrue(manager.onSingleTapConfirmed(eventWithDownTime(500L)));

        assertEquals(0, confirmedCalls[0]);
        verify(pdfView).performClick();
    }

    @Test
    public void disableDiscardsPendingConsumedTapAfterReenable() {
        int[] confirmedCalls = {0};
        callbacks.setOnTapUp(event -> true);
        callbacks.setOnTap(event -> {
            confirmedCalls[0]++;
            return true;
        });
        MotionEvent confirmation = eventWithDownTime(600L);

        assertTrue(manager.onSingleTapUp(eventWithDownTime(600L)));
        manager.disable();
        manager.enable();
        assertFalse(manager.onSingleTapConfirmed(confirmation));

        assertEquals(0, confirmedCalls[0]);
        verify(pdfView, never()).performClick();

        callbacks.setOnTapUp(event -> false);
        MotionEvent nextTap = eventWithDownTime(601L);
        assertTrue(manager.onDown(nextTap));
        assertFalse(manager.onSingleTapUp(nextTap));
        assertTrue(manager.onSingleTapConfirmed(nextTap));
        assertEquals(1, confirmedCalls[0]);
    }

    @Test
    public void disableIgnoresPendingConsumedTapConfirmation() {
        int[] confirmedCalls = {0};
        callbacks.setOnTapUp(event -> true);
        callbacks.setOnTap(event -> {
            confirmedCalls[0]++;
            return true;
        });

        assertTrue(manager.onSingleTapUp(eventWithDownTime(650L)));
        manager.disable();
        assertFalse(manager.onSingleTapConfirmed(eventWithDownTime(650L)));

        assertEquals(0, confirmedCalls[0]);
        verify(pdfView, never()).performClick();
    }

    @Test
    public void touchDownSyncsQuickScaleSettingFromTheView() {
        when(pdfView.isQuickScaleEnabled()).thenReturn(false);
        clearInvocations(scaleGestureDetector);
        MotionEvent down = mock(MotionEvent.class);
        when(down.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);
        when(down.getDownTime()).thenReturn(800L);

        manager.onTouch(pdfView, down);

        verify(scaleGestureDetector).setQuickScaleEnabled(false);
    }

    @Test
    public void dragAfterLongPressPansWhenTextSelectionDoesNotStart() {
        when(pdfView.isLongPressDragEnabled()).thenReturn(true);
        when(pdfView.isSwipeEnabled()).thenReturn(true);
        MotionEvent down = eventAt(MotionEvent.ACTION_DOWN, 40f, 40f);
        MotionEvent move = eventAt(MotionEvent.ACTION_MOVE, 30f, 30f);

        manager.onTouch(pdfView, down);
        manager.onLongPress(down);
        manager.onTouch(pdfView, move);

        verify(pdfView).moveRelativeTo(-10f, -10f);
    }

    @Test
    public void dragAfterLongPressDoesNotPanWhenTextSelectionStarts() {
        when(pdfView.isLongPressDragEnabled()).thenReturn(true);
        when(pdfView.startTextSelectionAt(anyFloat(), anyFloat())).thenReturn(true);
        MotionEvent down = eventAt(MotionEvent.ACTION_DOWN, 40f, 40f);
        MotionEvent move = eventAt(MotionEvent.ACTION_MOVE, 30f, 30f);

        manager.onTouch(pdfView, down);
        manager.onLongPress(down);
        manager.onTouch(pdfView, move);

        verify(pdfView, never()).moveRelativeTo(anyFloat(), anyFloat());
    }

    @Test
    public void dragAfterLongPressIsIgnoredWhenOptionIsDisabled() {
        when(pdfView.isLongPressDragEnabled()).thenReturn(false);
        MotionEvent down = eventAt(MotionEvent.ACTION_DOWN, 40f, 40f);
        MotionEvent move = eventAt(MotionEvent.ACTION_MOVE, 30f, 30f);

        manager.onTouch(pdfView, down);
        manager.onLongPress(down);
        manager.onTouch(pdfView, move);

        verify(pdfView, never()).moveRelativeTo(anyFloat(), anyFloat());
    }

    private MotionEvent eventAt(int action, float x, float y) {
        MotionEvent event = mock(MotionEvent.class);
        when(event.getActionMasked()).thenReturn(action);
        when(event.getX()).thenReturn(x);
        when(event.getY()).thenReturn(y);
        when(event.getPointerCount()).thenReturn(1);
        return event;
    }

    @Test
    public void disabledDoubleTapRejectsDoubleTapWithoutStartingAnAnimation() {
        when(pdfView.isDoubleTapEnabled()).thenReturn(false);

        assertFalse(manager.onDoubleTap(eventWithDownTime(750L)));

        verify(pdfView, never()).zoomWithAnimation(anyFloat(), anyFloat(), anyFloat());
        verify(pdfView, never()).resetZoomToFitPageWithAnimation();
    }

    @Test
    public void disabledZoomRejectsDoubleTapWithoutStartingAnAnimation() {
        when(pdfView.isDoubleTapEnabled()).thenReturn(true);
        when(pdfView.isZoomDisabled()).thenReturn(true);

        assertFalse(manager.onDoubleTap(eventWithDownTime(700L)));

        verify(pdfView, never()).zoomWithAnimation(anyFloat(), anyFloat(), anyFloat());
        verify(pdfView, never()).resetZoomToFitPageWithAnimation();
    }

    private MotionEvent eventWithDownTime(long downTime) {
        MotionEvent event = mock(MotionEvent.class);
        when(event.getDownTime()).thenReturn(downTime);
        return event;
    }
}
