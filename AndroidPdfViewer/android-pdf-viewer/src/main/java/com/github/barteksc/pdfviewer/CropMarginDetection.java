package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;

import com.github.barteksc.pdfviewer.model.CropMargins;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.util.Size;

import java.util.ArrayList;
import java.util.List;

public final class CropMarginDetection {

    private static final long PAGE_THROTTLE_MS = 2L;

    private CropMarginDetection() {
    }

    public static CropMargins detect(PdfiumCore core, PdfDocument doc, int pageCount,
                                     ProgressListener progress, CancellationSignal cancel) {
        int total = Math.max(0, pageCount);
        List<CropMarginDetector.Sample> samples = new ArrayList<>();
        CropMarginDetector.ScanBuffers buffers = new CropMarginDetector.ScanBuffers();
        Bitmap bitmap = null;
        notifyProgress(progress, 0, total);

        try {
            for (int pageIndex = 0; pageIndex < total; pageIndex++) {
                if (isCancelled(cancel)) {
                    return null;
                }

                CropMarginDetector.PageScan scan = null;
                boolean opened = false;
                try {
                    Size fullSize = core.getPageSize(doc, pageIndex);
                    Size bitmapSize = CropMarginDetector.detectionBitmapSize(fullSize);
                    if (bitmapSize.getWidth() > 0 && bitmapSize.getHeight() > 0) {
                        core.openPage(doc, pageIndex);
                        opened = true;
                        if (bitmap == null
                                || bitmap.getWidth() != bitmapSize.getWidth()
                                || bitmap.getHeight() != bitmapSize.getHeight()) {
                            if (bitmap != null) {
                                bitmap.recycle();
                            }
                            bitmap = Bitmap.createBitmap(bitmapSize.getWidth(), bitmapSize.getHeight(), Bitmap.Config.ARGB_8888);
                        }
                        core.renderPageBitmap(doc, bitmap, pageIndex,
                                0, 0, bitmapSize.getWidth(), bitmapSize.getHeight(), false);
                        if (isCancelled(cancel)) {
                            return null;
                        }
                        scan = CropMarginDetector.scan(bitmap, buffers);
                    }
                } catch (OutOfMemoryError ignored) {
                    return CropMargins.fullPage();
                } catch (Exception ignored) {
                } finally {
                    if (opened) {
                        core.closePage(doc, pageIndex);
                    }
                }

                if (scan == null) {
                    samples.add(new CropMarginDetector.Sample(pageIndex, null, null));
                } else {
                    samples.add(new CropMarginDetector.Sample(pageIndex, scan.bounds, scan.exclusionBounds));
                }
                notifyProgress(progress, pageIndex + 1, total);
                if (!pauseBetweenPages(pageIndex, total, cancel)) {
                    return null;
                }
            }
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }

        return CropMarginDetector.aggregateWithExclusions(samples);
    }

    private static boolean pauseBetweenPages(int pageIndex, int total, CancellationSignal cancel) {
        if (pageIndex >= total - 1 || isCancelled(cancel)) {
            return !isCancelled(cancel);
        }
        try {
            Thread.sleep(PAGE_THROTTLE_MS);
            return !isCancelled(cancel);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean isCancelled(CancellationSignal cancel) {
        return cancel != null && cancel.isCancelled();
    }

    private static void notifyProgress(ProgressListener progress, int done, int total) {
        if (progress != null) {
            progress.onCropMarginsDetectionProgress(done, total);
        }
    }

    public interface ProgressListener {
        void onCropMarginsDetectionProgress(int done, int total);
    }

    public interface CancellationSignal {
        boolean isCancelled();
    }
}
