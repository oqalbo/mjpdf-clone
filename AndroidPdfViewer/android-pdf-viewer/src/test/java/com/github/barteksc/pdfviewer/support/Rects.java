// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.support;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import static org.junit.Assert.assertEquals;

public final class Rects {
    private Rects() {}

    public static RectF rectF(float l, float t, float r, float b) {
        RectF result = new RectF();
        result.left = l;
        result.top = t;
        result.right = r;
        result.bottom = b;
        return result;
    }

    public static Rect rect(int l, int t, int r, int b) {
        Rect result = new Rect();
        result.left = l;
        result.top = t;
        result.right = r;
        result.bottom = b;
        return result;
    }

    public static PointF pointF(float x, float y) {
        PointF result = new PointF();
        result.x = x;
        result.y = y;
        return result;
    }

    public static void assertRectF(RectF expected, RectF actual, float tolerance) {
        assertEquals("left", expected.left, actual.left, tolerance);
        assertEquals("top", expected.top, actual.top, tolerance);
        assertEquals("right", expected.right, actual.right, tolerance);
        assertEquals("bottom", expected.bottom, actual.bottom, tolerance);
    }
}
