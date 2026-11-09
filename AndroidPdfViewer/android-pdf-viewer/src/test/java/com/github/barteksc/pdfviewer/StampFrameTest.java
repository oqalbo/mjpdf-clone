// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.Path;
import org.junit.Test;
import org.mockito.MockedConstruction;
import static com.github.barteksc.pdfviewer.StampPlacementManager.DragMode.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StampFrameTest {
    private StampFrame frame() {
        StampFrame frame = new StampFrame();
        frame.left = 20;
        frame.top = 30;
        frame.right = 60;
        frame.bottom = 50;
        return frame;
    }

    private void assertFrame(StampFrame frame, float left, float top, float right, float bottom) {
        assertArrayEquals(new float[]{left, top, right, bottom},
                new float[]{frame.left, frame.top, frame.right, frame.bottom}, 0f);
    }

    @Test
    public void movementClampsAtAllFourEdgesWithoutChangingSize() {
        StampFrame frame = frame();
        frame.move(-100, -100, 100, 80);
        assertFrame(frame, 0, 0, 40, 20);
        frame.move(200, 200, 100, 80);
        assertFrame(frame, 60, 60, 100, 80);
    }

    @Test
    public void bottomRightResizeCapsAtWidthOrHeightAndKeepsAspect() {
        StampFrame widthLimited = frame();
        widthLimited.resize(RESIZE_BR, 100, 100, 100, 0.5f);
        assertFrame(widthLimited, 20, 30, 100, 70);
        StampFrame heightLimited = frame();
        heightLimited.resize(RESIZE_BR, 100, 200, 60, 0.5f);
        assertFrame(heightLimited, 20, 30, 80, 60);
    }

    @Test
    public void topLeftResizeKeepsBottomRightFixed() {
        StampFrame frame = frame();
        frame.resize(RESIZE_TL, -100, 100, 80, 0.5f);
        assertFrame(frame, 0, 20, 60, 50);
    }

    @Test
    public void bottomLeftResizeKeepsTopRightFixed() {
        StampFrame frame = frame();
        frame.resize(RESIZE_BL, -100, 100, 55, 0.5f);
        assertFrame(frame, 10, 30, 60, 55);
    }

    @Test
    public void resizeMinimumIsFivePercentOfFrameWidth() {
        StampFrame frame = frame();
        frame.resize(RESIZE_BR, -100, 200, 80, 0.5f);
        assertFrame(frame, 20, 30, 30, 35);
        frame.resize(RESIZE_BR, 0, 200, 80, 0.5f);
        assertFrame(frame, 20, 30, 30, 35);
    }

    @Test
    public void maximumBelowMinimumAllowsOverflow() {
        StampFrame frame = frame();
        frame.left = 98;
        frame.right = 100;
        frame.resize(RESIZE_BR, 100, 100, 80, 0.5f);
        assertFrame(frame, 98, 30, 103, 32.5f);
        assertEquals(5f, StampFrame.clamp(100, 5, 2), 0f);
    }

    @Test
    public void pathSkipsMalformedStrokesAndRetainsValidCubicSegments() {
        try (MockedConstruction<Path> paths = mockConstruction(Path.class)) {
            Path path = StampPlacementManager.buildPath(new float[][]{
                    null, {}, {1}, {1, 2, 3}, {9, 10}, {1, 2, 3, 4, 5, 6, 7, 8}
            });
            assertEquals(1, paths.constructed().size());
            verify(path).moveTo(9, 10);
            verify(path).moveTo(1, 2);
            verify(path).cubicTo(3, 4, 5, 6, 7, 8);
            verifyNoMoreInteractions(path);
        }
    }

    @Test
    public void invertedColorsHaveComplementedChannelsAndOpaqueAlpha() {
        assertEquals(0xFFEFDFCF, StampFrame.invertColor(0xFF102030));
        assertEquals(0xFFEFDFCF, StampFrame.invertColor(0x00102030));
    }
}
