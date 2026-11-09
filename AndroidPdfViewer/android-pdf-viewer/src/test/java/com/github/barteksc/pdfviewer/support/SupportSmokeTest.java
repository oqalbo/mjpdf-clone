// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.support;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import com.github.barteksc.pdfviewer.PDFView;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mockingDetails;

public class SupportSmokeTest extends PdfiumTestSupport.InitializedTest {
    @Test
    public void repeatedInitializationAllowsPdfFileStaticsAndFreshMocks() {
        PdfiumTestSupport.ensureInitialized();
        PDFView.setMainThreadViolationReporter(null);
        assertEquals("a", PdfiumCore.mapPresentationFormMarks("a"));
        PdfiumCore core = PdfiumTestSupport.mockedCore();
        PdfDocument document = PdfiumTestSupport.mockedDocument();
        assertTrue(mockingDetails(core).isMock());
        assertTrue(mockingDetails(document).isMock());
        assertNotSame(core, PdfiumTestSupport.mockedCore());
        assertNotSame(document, PdfiumTestSupport.mockedDocument());
    }

    @Test
    public void geometryFactoriesPopulateEveryCoordinate() {
        RectF floating = Rects.rectF(-1.5f, 2.5f, 3.5f, 4.5f);
        assertArrayEquals(new float[]{-1.5f, 2.5f, 3.5f, 4.5f},
                new float[]{floating.left, floating.top, floating.right, floating.bottom}, 0f);
        Rect integer = Rects.rect(-1, 2, 3, 4);
        assertArrayEquals(new int[]{-1, 2, 3, 4},
                new int[]{integer.left, integer.top, integer.right, integer.bottom});
        PointF point = Rects.pointF(-2.5f, 7.5f);
        assertArrayEquals(new float[]{-2.5f, 7.5f}, new float[]{point.x, point.y}, 0f);
    }

    @Test
    public void rectangleComparisonNamesEachMismatchingFieldAndHonorsTolerance() {
        RectF expected = Rects.rectF(1f, 2f, 3f, 4f);
        Rects.assertRectF(expected, Rects.rectF(1.01f, 1.99f, 3.01f, 3.99f), 0.02f);
        RectF[] mismatches = {
                Rects.rectF(2f, 2f, 3f, 4f), Rects.rectF(1f, 3f, 3f, 4f),
                Rects.rectF(1f, 2f, 4f, 4f), Rects.rectF(1f, 2f, 3f, 5f)
        };
        String[] fields = {"left", "top", "right", "bottom"};
        for (int i = 0; i < fields.length; i++) {
            RectF actual = mismatches[i];
            AssertionError error = assertThrows(AssertionError.class,
                    () -> Rects.assertRectF(expected, actual, 0.02f));
            assertTrue(error.getMessage(), error.getMessage().startsWith(fields[i] + " "));
        }
    }
}
