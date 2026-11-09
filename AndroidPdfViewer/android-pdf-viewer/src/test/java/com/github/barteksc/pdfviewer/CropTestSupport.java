// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;

import com.github.barteksc.pdfviewer.model.CropBounds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class CropTestSupport {
    private CropTestSupport() {
    }

    static Bitmap bitmap(int[][] pixels) {
        Bitmap bitmap = mock(Bitmap.class);
        when(bitmap.getWidth()).thenReturn(pixels[0].length);
        when(bitmap.getHeight()).thenReturn(pixels.length);
        doAnswer(invocation -> {
            int[] destination = invocation.getArgument(0);
            int offset = invocation.getArgument(1);
            int stride = invocation.getArgument(2);
            int x = invocation.getArgument(3);
            int y = invocation.getArgument(4);
            int width = invocation.getArgument(5);
            int height = invocation.getArgument(6);
            for (int row = 0; row < height; row++) {
                System.arraycopy(pixels[y + row], x, destination, offset + row * stride, width);
            }
            return null;
        }).when(bitmap).getPixels(any(int[].class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        return bitmap;
    }

    static void assertBounds(CropBounds actual, float left, float top, float right, float bottom) {
        assertNotNull(actual);
        assertEquals(left, actual.getLeft(), 0.00001f);
        assertEquals(top, actual.getTop(), 0.00001f);
        assertEquals(right, actual.getRight(), 0.00001f);
        assertEquals(bottom, actual.getBottom(), 0.00001f);
    }
}
