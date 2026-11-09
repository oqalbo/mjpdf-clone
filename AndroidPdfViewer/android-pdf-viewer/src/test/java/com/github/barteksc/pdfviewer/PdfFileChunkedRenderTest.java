// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.Rect;
import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.shockwave.pdfium.PdfiumCore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PdfFileChunkedRenderTest extends PdfiumTestSupport.InitializedTest {
    private PdfFileTestSupport fixture;

    @Before
    public void setUp() {
        fixture = PdfFileTestSupport.uniform(1, 100, 200).spacing(0).textPages().chunkedRender();
        fixture.build();
    }

    @Test
    public void continuedRenderClosesOnceAsCompletedWhenDone() {
        when(fixture.core.renderPageBitmapChunkedContinue(fixture.document, 41L, 0))
                .thenReturn(PdfiumCore.RENDER_STATUS_TO_BE_CONTINUED, PdfiumCore.RENDER_STATUS_DONE);
        assertTrue(fixture.render(new AtomicBoolean()));
        verify(fixture.core, times(2)).renderPageBitmapChunkedContinue(fixture.document, 41L, 0);
        verifyClosedOnce(true);
        verify(fixture.core).renderPageBitmapChunkedStart(fixture.document, fixture.bitmap, 0,
                0, 0, 100, 200, true, 0);
    }

    @Test
    public void failedFinalStatusClosesOnceAsIncomplete() {
        when(fixture.core.renderPageBitmapChunkedContinue(fixture.document, 41L, 0))
                .thenReturn(PdfiumCore.RENDER_STATUS_FAILED);
        assertFalse(fixture.render(new AtomicBoolean()));
        verifyClosedOnce(false);
    }

    @Test
    public void cancellationDuringContinueStopsBeforeTheNextChunk() {
        AtomicBoolean cancel = new AtomicBoolean();
        when(fixture.core.renderPageBitmapChunkedContinue(fixture.document, 41L, 0)).thenAnswer(call -> {
            cancel.set(true);
            return PdfiumCore.RENDER_STATUS_TO_BE_CONTINUED;
        });
        assertFalse(fixture.render(cancel));
        verify(fixture.core, times(1)).renderPageBitmapChunkedContinue(fixture.document, 41L, 0);
        verifyClosedOnce(false);
    }

    @Test
    public void mutatorCancelsTheActiveRenderAndRunsAfterItsContextCloses() throws Exception {
        CountDownLatch continuing = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicBoolean state = new AtomicBoolean();
        AtomicBoolean cancel = mock(AtomicBoolean.class);
        when(cancel.get()).thenAnswer(call -> state.get());
        doAnswer(call -> {
            state.set(call.getArgument(0));
            cancelled.countDown();
            return null;
        }).when(cancel).set(true);
        when(fixture.core.renderPageBitmapChunkedContinue(fixture.document, 41L, 0)).thenAnswer(call -> {
            continuing.countDown();
            assertTrue("mutator must cancel the active render", cancelled.await(500, TimeUnit.MILLISECONDS));
            return PdfiumCore.RENDER_STATUS_TO_BE_CONTINUED;
        });
        Rect[] highlight = new Rect[0];
        when(fixture.core.createHighlightText(fixture.document, 0, 2, 4, false)).thenReturn(highlight);
        ExecutorService workers = workerPool(2);
        try {
            Future<Boolean> render = workers.submit(() -> fixture.render(cancel));
            assertTrue("render must reach continue", continuing.await(500, TimeUnit.MILLISECONDS));
            Future<Rect[]> mutation = workers.submit(() -> fixture.file.createHighlightText(0, 2, 4, false));
            assertFalse(render.get(500, TimeUnit.MILLISECONDS));
            assertTrue(state.get());
            assertSame(highlight, mutation.get(500, TimeUnit.MILLISECONDS));
            InOrder order = inOrder(fixture.core);
            order.verify(fixture.core).renderPageBitmapChunkedClose(
                    fixture.document, 41L, 0, fixture.bitmap, true, true, false);
            order.verify(fixture.core).createHighlightText(fixture.document, 0, 2, 4, false);
            verifyClosedOnce(false);
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(500, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void zeroContextReturnsFalseWithoutReadingStatusOrClosing() {
        when(fixture.core.renderPageBitmapChunkedStart(eq(fixture.document), eq(fixture.bitmap), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyInt())).thenReturn(0L);
        assertFalse(fixture.render(new AtomicBoolean()));
        verify(fixture.core, never()).renderPageBitmapChunkedStatus(anyLong());
        verify(fixture.core, never()).renderPageBitmapChunkedContinue(any(), anyLong(), anyInt());
        verify(fixture.core, never()).renderPageBitmapChunkedClose(any(), anyLong(), anyInt(), any(),
                anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    public void throwingContinueClosesTheContextAndReleasesTheGateForAnotherThread() throws Exception {
        IllegalStateException failure = new IllegalStateException("continue failed");
        when(fixture.core.renderPageBitmapChunkedContinue(fixture.document, 41L, 0))
                .thenThrow(failure).thenReturn(PdfiumCore.RENDER_STATUS_DONE);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.render(new AtomicBoolean())));
        verifyClosedOnce(false);
        ExecutorService worker = workerPool(1);
        try {
            assertTrue(worker.submit(() -> fixture.render(new AtomicBoolean())).get(500, TimeUnit.MILLISECONDS));
            verify(fixture.core).renderPageBitmapChunkedClose(
                    fixture.document, 41L, 0, fixture.bitmap, true, true, true);
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(500, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void disposedFileRejectsRenderingWithoutNativeCalls() {
        fixture.file.dispose();
        clearInvocations(fixture.core);
        assertFalse(fixture.render(new AtomicBoolean()));
        verifyNoInteractions(fixture.core);
    }

    private static ExecutorService workerPool(int count) {
        return Executors.newFixedThreadPool(count, task -> {
            Thread worker = new Thread(task);
            worker.setDaemon(true);
            return worker;
        });
    }

    private void verifyClosedOnce(boolean completed) {
        verify(fixture.core).renderPageBitmapChunkedClose(
                fixture.document, 41L, 0, fixture.bitmap, true, true, completed);
        verify(fixture.core, times(1)).renderPageBitmapChunkedClose(any(), anyLong(), anyInt(), any(),
                anyBoolean(), anyBoolean(), anyBoolean());
    }
}
