// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;

import com.github.barteksc.pdfviewer.model.CropMargins;
import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.util.Size;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.barteksc.pdfviewer.CropTestSupport.assertBounds;
import static com.github.barteksc.pdfviewer.CropTestSupport.bitmap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class CropMarginDetectionLoopTest extends PdfiumTestSupport.InitializedTest {
    private PdfiumCore core;
    private PdfDocument document;

    @Before
    public void setUp() {
        core = mock(PdfiumCore.class);
        document = mock(PdfDocument.class);
        when(core.getPageSize(eq(document), anyInt())).thenReturn(new Size(20, 20));
    }

    @Test
    public void cancellationBeforeRenderingReturnsNullWithoutOpeningAPage() {
        CropMarginDetection.ProgressListener progress = mock(CropMarginDetection.ProgressListener.class);
        assertNull(CropMarginDetection.detect(core, document, 2, progress, () -> true));
        verifyNoInteractions(core);
        verify(progress).onCropMarginsDetectionProgress(0, 2);
        verify(progress, never()).onCropMarginsDetectionProgress(1, 2);
    }

    @Test
    public void cancellationAfterRenderingClosesPageAndRecyclesBitmapWithoutScanning() {
        Bitmap bitmap = contentBitmap(20, 20);
        AtomicBoolean cancelled = new AtomicBoolean();
        doAnswer(invocation -> {
            cancelled.set(true);
            return null;
        }).when(core).renderPageBitmap(document, bitmap, 0, 0, 0, 20, 20, false);
        try (MockedStatic<Bitmap> factory = mockStatic(Bitmap.class)) {
            factory.when(() -> Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)).thenReturn(bitmap);
            assertNull(CropMarginDetection.detect(core, document, 2, null, cancelled::get));
            verify(core).closePage(document, 0);
            verify(core, never()).openPage(document, 1);
            verify(bitmap, never()).getPixels(any(int[].class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            verify(bitmap).recycle();
        }
    }

    @Test
    public void renderOutOfMemoryReturnsFullPageAndReleasesPageAndBitmap() {
        Bitmap bitmap = contentBitmap(20, 20);
        doThrow(new OutOfMemoryError("render allocation")).when(core)
                .renderPageBitmap(document, bitmap, 0, 0, 0, 20, 20, false);
        try (MockedStatic<Bitmap> factory = mockStatic(Bitmap.class)) {
            factory.when(() -> Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)).thenReturn(bitmap);
            CropMargins margins = CropMarginDetection.detect(core, document, 2, null, null);
            assertNotNull(margins);
            assertTrue(margins.isFullPage());
            verify(core).closePage(document, 0);
            verify(core, never()).openPage(document, 1);
            verify(bitmap).recycle();
        }
    }

    @Test
    public void renderExceptionAddsANullSampleAndStillClosesThePage() {
        Bitmap bitmap = contentBitmap(20, 20);
        doThrow(new IllegalStateException("page render failed")).when(core)
                .renderPageBitmap(document, bitmap, 0, 0, 0, 20, 20, false);
        List<Integer> progress = new ArrayList<>();
        List<CropMarginDetector.Sample> captured = new ArrayList<>();
        try (MockedStatic<Bitmap> factory = mockStatic(Bitmap.class);
             MockedStatic<CropMarginDetector> detector = mockStatic(CropMarginDetector.class,
                     org.mockito.Mockito.CALLS_REAL_METHODS)) {
            factory.when(() -> Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)).thenReturn(bitmap);
            detector.when(() -> CropMarginDetector.aggregateWithExclusions(any())).thenAnswer(invocation -> {
                captured.addAll(invocation.getArgument(0));
                return invocation.callRealMethod();
            });
            CropMargins margins = CropMarginDetection.detect(core, document, 2,
                    (done, total) -> progress.add(done), null);
            assertNotNull(margins);
            assertEquals(Arrays.asList(0, 1, 2), progress);
            assertEquals(2, captured.size());
            assertEquals(0, captured.get(0).documentPage);
            assertNull(captured.get(0).bounds);
            assertNull(captured.get(0).exclusionBounds);
            assertEquals(1, captured.get(1).documentPage);
            assertBounds(captured.get(1).bounds, 0.2f, 0.2f, 0.8f, 0.8f);
            verify(core).closePage(document, 0);
            verify(core).closePage(document, 1);
            verify(bitmap).recycle();
        }
    }

    @Test
    public void zeroSizedPageIsCountedWithoutBeingOpenedOrRendered() {
        when(core.getPageSize(document, 0)).thenReturn(new Size(0, 0));
        List<Integer> progress = new ArrayList<>();
        try (MockedStatic<Bitmap> factory = mockStatic(Bitmap.class)) {
            CropMargins margins = CropMarginDetection.detect(core, document, 1,
                    (done, total) -> progress.add(done), null);
            assertTrue(margins.isFullPage());
            assertEquals(Arrays.asList(0, 1), progress);
            verify(core, never()).openPage(document, 0);
            verify(core, never()).closePage(document, 0);
            verify(core, never()).renderPageBitmap(any(), any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
            factory.verifyNoInteractions();
        }
    }

    @Test
    public void progressVisitsEveryPageOnceAndBitmapIsReusedUntilSizeChanges() {
        Bitmap square = contentBitmap(20, 20);
        Bitmap wide = contentBitmap(30, 20);
        when(core.getPageSize(document, 2)).thenReturn(new Size(30, 20));
        List<Integer> doneValues = new ArrayList<>();
        List<Integer> totalValues = new ArrayList<>();
        try (MockedStatic<Bitmap> factory = mockStatic(Bitmap.class)) {
            factory.when(() -> Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)).thenReturn(square);
            factory.when(() -> Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888)).thenReturn(wide);
            CropMargins margins = CropMarginDetection.detect(core, document, 3, (done, total) -> {
                doneValues.add(done);
                totalValues.add(total);
            }, null);
            assertBounds(margins.getEvenCrop(), 0.185f, 0.185f, 0.815f, 0.815f);
            assertEquals(Arrays.asList(0, 1, 2, 3), doneValues);
            assertEquals(Arrays.asList(3, 3, 3, 3), totalValues);
            factory.verify(() -> Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888), times(1));
            factory.verify(() -> Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888), times(1));
            InOrder order = inOrder(core, square, wide);
            order.verify(core).openPage(document, 0);
            order.verify(core).renderPageBitmap(document, square, 0, 0, 0, 20, 20, false);
            order.verify(core).closePage(document, 0);
            order.verify(core).openPage(document, 1);
            order.verify(core).renderPageBitmap(document, square, 1, 0, 0, 20, 20, false);
            order.verify(core).closePage(document, 1);
            order.verify(core).openPage(document, 2);
            order.verify(square).recycle();
            order.verify(core).renderPageBitmap(document, wide, 2, 0, 0, 30, 20, false);
            order.verify(core).closePage(document, 2);
            order.verify(wide).recycle();
            verify(square, times(1)).recycle();
            verify(wide, times(1)).recycle();
        }
    }

    @Test
    public void interruptedPauseReturnsNullAndPreservesInterruptStatus() {
        Bitmap bitmap = contentBitmap(20, 20);
        List<Integer> progress = new ArrayList<>();
        boolean originallyInterrupted = Thread.interrupted();
        try (MockedStatic<Bitmap> factory = mockStatic(Bitmap.class)) {
            factory.when(() -> Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)).thenReturn(bitmap);
            CropMargins margins = CropMarginDetection.detect(core, document, 2, (done, total) -> {
                progress.add(done);
                if (done == 1) {
                    Thread.currentThread().interrupt();
                }
            }, null);
            assertNull(margins);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(Arrays.asList(0, 1), progress);
            verify(core).closePage(document, 0);
            verify(core, never()).openPage(document, 1);
            verify(bitmap).recycle();
        } finally {
            Thread.interrupted();
            if (originallyInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Bitmap contentBitmap(int width, int height) {
        int[][] pixels = new int[height][width];
        for (int[] row : pixels) {
            Arrays.fill(row, 0xffffffff);
        }
        for (int y = height / 5; y < height * 4 / 5; y++) {
            Arrays.fill(pixels[y], width / 5, width * 4 / 5, 0xff000000);
        }
        return bitmap(pixels);
    }
}
