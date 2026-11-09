// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.scroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScrollHandleMathTest {
    @Test
    public void scrollPositionRoundTripsWithATopReachLimit() {
        float[] ratios = {0, 0.5f, 1};
        float[] positions = {80, 420, 760};
        for (int i = 0; i < ratios.length; i++) {
            float position = DefaultScrollHandle.scrollPosition(ratios[i], 800, 80, 40);
            assertEquals(positions[i], position, 0f);
            assertEquals(ratios[i], DefaultScrollHandle.computePositionOffset(position, 800, 80, 40), 0f);
        }
    }

    @Test
    public void tooSmallViewsHaveNoPositionUpdateAndZeroProgress() {
        for (float viewSize : new float[]{120, 100}) {
            assertEquals(Float.NaN, DefaultScrollHandle.scrollPosition(0.5f, viewSize, 80, 40), 0f);
            assertEquals(0f, DefaultScrollHandle.computePositionOffset(90, viewSize, 80, 40), 0f);
        }
    }

    @Test
    public void positionsOutsideTheHandleTrackClampToItsEnds() {
        assertEquals(0f, DefaultScrollHandle.computePositionOffset(79, 800, 80, 40), 0f);
        assertEquals(1f, DefaultScrollHandle.computePositionOffset(761, 800, 80, 40), 0f);
        assertEquals(380f, DefaultScrollHandle.scrollPosition(0.5f, 800, 0, 40), 0f);
    }

    @Test
    public void labelsUseRowRangesAndOptionalPageCounts() {
        assertEquals("3/10", DefaultScrollHandle.getPageNumText(3, 3, 3, 10, true));
        assertEquals("3-4/10", DefaultScrollHandle.getPageNumText(4, 3, 4, 10, true));
        assertEquals("3-4", DefaultScrollHandle.getPageNumText(4, 3, 4, 10, false));
        assertEquals("3", DefaultScrollHandle.getPageNumText(3, 3, 3, 0, true));
    }
}
