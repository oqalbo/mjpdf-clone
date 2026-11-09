// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.shockwave.pdfium.util.Size;

import org.junit.Test;

import java.util.Arrays;

import static com.github.barteksc.pdfviewer.CropTestSupport.assertBounds;
import static com.github.barteksc.pdfviewer.CropTestSupport.bitmap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CropMarginDetectorScanTest {
    @Test
    public void detectionSizePreservesAspectAndDoesNotUpscaleSmallPages() {
        assertEquals(new Size(198, 256), CropMarginDetector.detectionBitmapSize(new Size(612, 792)));
        assertEquals(new Size(256, 198), CropMarginDetector.detectionBitmapSize(new Size(792, 612)));
        assertEquals(new Size(100, 200), CropMarginDetector.detectionBitmapSize(new Size(100, 200)));
    }

    @Test
    public void nonPositivePageDimensionsProduceAnEmptyDetectionSize() {
        for (Size size : new Size[]{new Size(0, 0), new Size(0, 200), new Size(200, 0),
                new Size(-1, 200), new Size(200, -1)}) {
            assertEquals(new Size(0, 0), CropMarginDetector.detectionBitmapSize(size));
        }
    }

    @Test
    public void blackContentBlockDefinesNormalizedBounds() {
        int[][] pixels = whitePage();
        fill(pixels, 40, 40, 160, 160, 0xff000000);
        CropMarginDetector.PageScan scan = scan(pixels);
        assertBounds(scan.bounds, 0.2f, 0.2f, 0.8f, 0.8f);
        assertBounds(scan.exclusionBounds, 0.2f, 0.2f, 0.8f, 0.8f);
    }

    @Test
    public void contentCoveringMoreThanNinetyEightPercentHasNoCropBounds() {
        int[][] pixels = whitePage();
        fill(pixels, 0, 0, 200, 200, 0xff000000);
        for (int x = 0; x < 200; x++) {
            pixels[0][x] = 0xffffffff;
            pixels[199][x] = 0xffffffff;
        }
        CropMarginDetector.PageScan scan = scan(pixels);
        assertNull(scan.bounds);
        assertBounds(scan.exclusionBounds, 0f, 0.005f, 1f, 0.995f);
    }

    @Test
    public void isolatedOnePixelSpeckDoesNotMoveContentBounds() {
        int[][] pixels = whitePage();
        fill(pixels, 40, 40, 160, 160, 0xff000000);
        pixels[10][10] = 0xff000000;
        CropMarginDetector.PageScan scan = scan(pixels);
        assertBounds(scan.bounds, 0.2f, 0.2f, 0.8f, 0.8f);
        assertBounds(scan.exclusionBounds, 0.2f, 0.2f, 0.8f, 0.8f);
    }

    @Test
    public void shortInkRunIsIgnoredUntilItSpansThreeRows() {
        int[][] pixels = whitePage();
        fill(pixels, 40, 40, 160, 160, 0xff000000);
        fill(pixels, 40, 10, 160, 12, 0xff000000);
        assertBounds(scan(pixels).bounds, 0.2f, 0.2f, 0.8f, 0.8f);
        fill(pixels, 40, 12, 160, 13, 0xff000000);
        assertBounds(scan(pixels).bounds, 0.2f, 0.05f, 0.8f, 0.8f);
    }

    @Test
    public void lightGreyLineExpandsOnlyExclusionBounds() {
        int[][] pixels = whitePage();
        fill(pixels, 40, 40, 160, 160, 0xff000000);
        fill(pixels, 40, 20, 160, 21, 0xffebebeb);
        CropMarginDetector.PageScan scan = scan(pixels);
        assertBounds(scan.bounds, 0.2f, 0.2f, 0.8f, 0.8f);
        assertBounds(scan.exclusionBounds, 0.2f, 0.1f, 0.8f, 0.8f);
    }

    @Test
    public void reusedScanBuffersForgetInkFromThePreviousPage() {
        CropMarginDetector.ScanBuffers buffers = new CropMarginDetector.ScanBuffers();
        int[][] pixels = whitePage();
        fill(pixels, 40, 40, 160, 160, 0xff000000);
        assertNotNull(CropMarginDetector.scan(bitmap(pixels), buffers).bounds);
        CropMarginDetector.PageScan blank = CropMarginDetector.scan(bitmap(whitePage()), buffers);
        assertNull(blank.bounds);
        assertNull(blank.exclusionBounds);
    }

    private static CropMarginDetector.PageScan scan(int[][] pixels) {
        return CropMarginDetector.scan(bitmap(pixels), new CropMarginDetector.ScanBuffers());
    }

    private static int[][] whitePage() {
        int[][] pixels = new int[200][200];
        for (int[] row : pixels) {
            Arrays.fill(row, 0xffffffff);
        }
        return pixels;
    }

    private static void fill(int[][] pixels, int left, int top, int right, int bottom, int color) {
        for (int y = top; y < bottom; y++) {
            Arrays.fill(pixels[y], left, right, color);
        }
    }
}
