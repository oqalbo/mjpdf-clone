// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static com.github.barteksc.pdfviewer.GestureTestSupport.event;

import static org.junit.Assert.assertEquals;

import static org.mockito.Mockito.when;

import android.view.MotionEvent;

import org.junit.Test;

public class DragPinchManagerAxisLockTest extends GestureTestSupport.GestureTest {

    @Test
    public void horizontalIntentBelowTouchSlopDoesNotUnlockScrolling() {
        MotionEvent down = event(0f, 0f);

        assertEquals(0f, manager.applyAxisLock(down, event(7.99f, 0f), 2f, 0f), 0f);
        assertEquals(2f, manager.applyAxisLock(down, event(8f, 0f), 2f, 0f), 0f);
    }

    @Test
    public void horizontalIntentExactlyAtRatioKeepsVerticalLock() {
        assertEquals(0f, manager.applyAxisLock(event(0f, 0f), event(14f, 8f), 2f, 1f), 0f);
    }

    @Test
    public void horizontalIntentJustAboveRatioAllowsFreeScrollingInEitherDirection() {
        for (float sign : new float[]{-1f, 1f}) {
            manager.onDown(event(0f, 0f));
            assertEquals(sign * 2f, manager.applyAxisLock(event(0f, 0f),
                    event(sign * 14.01f, sign * 8f), sign * 2f, sign), 0f);
        }
    }

    @Test
    public void verticalResistanceAccumulatesAfterDrainAndBreaksAtThreeSlops() {
        MotionEvent down = event(0f, 0f);
        MotionEvent vertical = event(0f, 8f);
        assertEquals(0f, manager.applyAxisLock(down, vertical, 0f, 8f), 0f);
        assertEquals(0f, manager.applyAxisLock(down, vertical, -20f, -8f), 0f);
        assertEquals(0f, manager.applyAxisLock(down, vertical, 15f, 8f), 0f);
        assertEquals(-1f, manager.applyAxisLock(down, vertical, -1f, 0f), 0f);
        assertEquals(2f, manager.applyAxisLock(down, vertical, 2f, 100f), 0f);
    }

    @Test
    public void cancelResetsFreeLockBeforeTheNextGesture() {
        MotionEvent down = event(0f, 0f);
        assertEquals(9f, manager.applyAxisLock(down, event(9f, 0f), 9f, 0f), 0f);
        MotionEvent cancel = event(9f, 0f);
        when(cancel.getActionMasked()).thenReturn(MotionEvent.ACTION_CANCEL);

        manager.onTouch(pdfView, cancel);

        assertEquals(0f, manager.applyAxisLock(down, event(7f, 0f), 7f, 0f), 0f);
        assertEquals(0f, manager.applyAxisLock(down, event(0f, 8f), 1f, 8f), 0f);
    }

    @Test
    public void cancelClearsAccumulatedVerticalResistance() {
        MotionEvent down = event(0f, 0f);
        MotionEvent vertical = event(0f, 8f);
        assertEquals(0f, manager.applyAxisLock(down, vertical, 0f, 8f), 0f);
        assertEquals(0f, manager.applyAxisLock(down, vertical, 23f, 0f), 0f);
        MotionEvent cancel = event(0f, 8f);
        when(cancel.getActionMasked()).thenReturn(MotionEvent.ACTION_CANCEL);

        manager.onTouch(pdfView, cancel);

        assertEquals(0f, manager.applyAxisLock(down, vertical, 1f, 0f), 0f);
        assertEquals(23f, manager.applyAxisLock(down, vertical, 23f, 0f), 0f);
    }
}
