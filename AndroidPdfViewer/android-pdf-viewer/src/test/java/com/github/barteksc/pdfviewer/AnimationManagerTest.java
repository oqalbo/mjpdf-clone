// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import com.github.barteksc.pdfviewer.support.Rects;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class AnimationManagerTest {
    private PDFView pdfView;
    private AnimationManager manager;
    private ValueAnimator animator;
    private OverScroller scroller;
    private MockedConstruction<OverScroller> scrollers;
    private MockedConstruction<DecelerateInterpolator> interpolators;
    private MockedStatic<ValueAnimator> animators;

    @Before
    public void setUp() {
        pdfView = mock(PDFView.class);
        animator = mock(ValueAnimator.class);
        scrollers = mockConstruction(OverScroller.class);
        interpolators = mockConstruction(DecelerateInterpolator.class);
        animators = mockStatic(ValueAnimator.class);
        animators.when(() -> ValueAnimator.ofFloat(any(float[].class))).thenReturn(animator);
        manager = new AnimationManager(pdfView);
        scroller = scrollers.constructed().get(0);
    }

    @After
    public void tearDown() {
        animators.close();
        interpolators.close();
        scrollers.close();
    }

    @Test
    public void completedFlingLoadsPagesAndSnapsExactlyOnce() {
        manager.startFlingAnimation(-10, -20, 100, -900, -300, 0, -2000, 0);
        verify(scroller).fling(-10, -20, 100, -900, -300, 0, -2000, 0);
        assertTrue(manager.isFlinging());
        verify(pdfView).setRenderFlinging(true);
        when(scroller.computeScrollOffset()).thenReturn(true, false, false);
        when(scroller.getCurrX()).thenReturn(-30);
        when(scroller.getCurrY()).thenReturn(-60);
        clearInvocations(pdfView);

        manager.computeFling();

        verify(pdfView).moveTo(-30f, -60f);
        verify(pdfView).loadPageByOffset();
        verify(pdfView, never()).loadPages();
        verify(pdfView, never()).performPageSnap();
        assertTrue(manager.isFlinging());

        manager.computeFling();
        manager.computeFling();

        assertFalse(manager.isFlinging());
        verify(pdfView).setRenderFlinging(false);
        verify(pdfView).loadPages();
        verify(pdfView).performPageSnap();
    }

    @Test
    public void idleScrollerDoesNotLoadOrSnap() {
        when(scroller.computeScrollOffset()).thenReturn(false);
        clearInvocations(pdfView);

        manager.computeFling();

        assertFalse(manager.isFlinging());
        verifyNoInteractions(pdfView);
    }

    @Test
    public void cancellingHorizontalPageFlingClearsRenderFlinging() {
        assertPageFlingCancellation(false);
    }

    @Test
    public void cancellingVerticalPageFlingClearsRenderFlinging() {
        assertPageFlingCancellation(true);
    }

    private void assertPageFlingCancellation(boolean vertical) {
        when(pdfView.isSwipeVertical()).thenReturn(vertical);
        when(pdfView.getCurrentXOffset()).thenReturn(-10f);
        when(pdfView.getCurrentYOffset()).thenReturn(-20f);
        manager.startPageFlingAnimation(-100f);
        assertTrue(manager.isFlinging());
        verify(pdfView).setRenderFlinging(true);
        when(animator.getAnimatedValue()).thenReturn(-50f);
        updateListener().onAnimationUpdate(animator);
        verify(pdfView).moveTo(vertical ? -10f : -50f, vertical ? -50f : -20f);
        verify(pdfView).loadPageByOffset();
        clearInvocations(pdfView);

        listener().onAnimationCancel(animator);

        assertFalse(manager.isFlinging());
        verify(pdfView).setRenderFlinging(false);
        verify(pdfView).loadPages();
    }

    @Test
    public void zoomEndWithOffsetsSetsFinalZoomAndPositionWithoutSnapping() {
        manager.startZoomAnimation(40f, 50f, 1f, 2f, Rects.pointF(-120f, -340f));
        when(animator.getAnimatedValue()).thenReturn(1.5f);

        updateListener().onAnimationUpdate(animator);
        listener().onAnimationEnd(animator);

        verify(pdfView).zoomCenteredTo(eq(1.5f), any());
        InOrder order = inOrder(pdfView);
        order.verify(pdfView).zoomTo(2f);
        order.verify(pdfView).moveTo(-120f, -340f);
        order.verify(pdfView).loadPages();
        verify(pdfView, never()).performPageSnap();
    }

    @Test
    public void zoomEndWithoutOffsetsLoadsPagesAndSnaps() {
        manager.startZoomAnimation(40f, 50f, 1f, 2f);

        listener().onAnimationEnd(animator);

        verify(pdfView).loadPages();
        verify(pdfView).performPageSnap();
        verify(pdfView, never()).moveTo(anyFloat(), anyFloat());
        verify(pdfView, never()).zoomTo(anyFloat());
    }

    @Test
    public void stoppingScrollLeavesZoomAnimationRunning() {
        manager.startZoomAnimation(40f, 50f, 1f, 2f);

        manager.stopScrollAnimation();

        verify(animator, never()).cancel();
        manager.stopAll();
        verify(animator).cancel();
    }

    @Test
    public void stoppingScrollCancelsScrollAnimationOnlyOnce() {
        manager.startXAnimation(-10f, -100f);

        manager.stopScrollAnimation();
        manager.stopScrollAnimation();

        verify(animator).cancel();
    }

    private Animator.AnimatorListener listener() {
        ArgumentCaptor<Animator.AnimatorListener> listener =
                ArgumentCaptor.forClass(Animator.AnimatorListener.class);
        verify(animator).addListener(listener.capture());
        return listener.getValue();
    }

    private ValueAnimator.AnimatorUpdateListener updateListener() {
        ArgumentCaptor<ValueAnimator.AnimatorUpdateListener> listener =
                ArgumentCaptor.forClass(ValueAnimator.AnimatorUpdateListener.class);
        verify(animator).addUpdateListener(listener.capture());
        return listener.getValue();
    }
}
