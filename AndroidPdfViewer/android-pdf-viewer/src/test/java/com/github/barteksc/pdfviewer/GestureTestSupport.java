// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

import com.github.barteksc.pdfviewer.listener.Callbacks;
import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import org.junit.Before;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

final class GestureTestSupport {

    static final int TOUCH_SLOP = 8;

    private GestureTestSupport() {
    }

    static PDFView mockedView() {
        PDFView pdfView = mock(PDFView.class);
        pdfView.callbacks = new Callbacks();
        pdfView.cacheManager = mock(CacheManager.class);
        Context context = mock(Context.class);
        when(pdfView.getContext()).thenReturn(context);
        return pdfView;
    }

    static DragPinchManager enabledManager(PDFView pdfView, AnimationManager animationManager) {
        ViewConfiguration configuration = mock(ViewConfiguration.class);
        when(configuration.getScaledTouchSlop()).thenReturn(TOUCH_SLOP);
        DragPinchManager manager;
        try (MockedConstruction<GestureDetector> gestures = mockConstruction(GestureDetector.class);
             MockedConstruction<ScaleGestureDetector> scales = mockConstruction(ScaleGestureDetector.class);
             MockedStatic<ViewConfiguration> configurations = mockStatic(ViewConfiguration.class)) {
            configurations.when(() -> ViewConfiguration.get(pdfView.getContext())).thenReturn(configuration);
            manager = new DragPinchManager(pdfView, animationManager);
        }
        manager.enable();
        return manager;
    }

    static MotionEvent event(float x, float y) {
        MotionEvent event = mock(MotionEvent.class);
        when(event.getX()).thenReturn(x);
        when(event.getY()).thenReturn(y);
        return event;
    }

    public abstract static class GestureTest extends PdfiumTestSupport.InitializedTest {
        PDFView pdfView;
        AnimationManager animationManager;
        DragPinchManager manager;

        @Before
        public void setUp() {
            pdfView = mockedView();
            animationManager = mock(AnimationManager.class);
            manager = enabledManager(pdfView, animationManager);
        }
    }
}
