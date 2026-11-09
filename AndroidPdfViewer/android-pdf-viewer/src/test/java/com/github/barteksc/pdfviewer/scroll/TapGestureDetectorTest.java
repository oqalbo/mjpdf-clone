// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.scroll;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class TapGestureDetectorTest {
    private TapGestureDetector detector;

    @Before
    public void setUp() {
        Context context = mock(Context.class);
        ViewConfiguration configuration = mock(ViewConfiguration.class);
        when(configuration.getScaledTouchSlop()).thenReturn(10);
        try (MockedStatic<ViewConfiguration> configurations = mockStatic(ViewConfiguration.class)) {
            configurations.when(() -> ViewConfiguration.get(context)).thenReturn(configuration);
            detector = new TapGestureDetector(context);
        }
    }

    @Test
    public void releaseExactlyAtSlopDistanceIsStillATap() {
        assertFalse(detector.isTap(event(MotionEvent.ACTION_DOWN, 100f, 200f)));
        assertTrue(detector.isTap(event(MotionEvent.ACTION_UP, 106f, 208f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_UP, 106f, 208f)));
    }

    @Test
    public void movingBeyondSlopAndBackCancelsTheTap() {
        assertFalse(detector.isTap(event(MotionEvent.ACTION_DOWN, 100f, 200f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_MOVE, 106f, 208.01f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_MOVE, 100f, 200f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_UP, 100f, 200f)));
    }

    @Test
    public void secondPointerDownCancelsTheTap() {
        assertFalse(detector.isTap(event(MotionEvent.ACTION_DOWN, 100f, 200f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_POINTER_DOWN, 100f, 200f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_UP, 100f, 200f)));
    }

    @Test
    public void cancelRejectsTheOldTapAndAllowsANewDown() {
        assertFalse(detector.isTap(event(MotionEvent.ACTION_DOWN, 100f, 200f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_CANCEL, 100f, 200f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_UP, 100f, 200f)));
        assertFalse(detector.isTap(event(MotionEvent.ACTION_DOWN, 300f, 400f)));
        assertTrue(detector.isTap(event(MotionEvent.ACTION_UP, 300f, 400f)));
    }

    @Test
    public void releaseWithoutDownIsNotATap() {
        assertFalse(detector.isTap(event(MotionEvent.ACTION_UP, 0f, 0f)));
    }

    private MotionEvent event(int action, float rawX, float rawY) {
        MotionEvent event = mock(MotionEvent.class);
        when(event.getActionMasked()).thenReturn(action);
        when(event.getRawX()).thenReturn(rawX);
        when(event.getRawY()).thenReturn(rawY);
        return event;
    }
}
