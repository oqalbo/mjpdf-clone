package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;

import com.github.barteksc.pdfviewer.model.CropBounds;
import com.github.barteksc.pdfviewer.model.CropMargins;
import com.shockwave.pdfium.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CropMarginDetector {

    static final int TARGET_LONGEST_SIDE = 256;

    private static final int MIN_VALID_SAMPLES = 4;
    private static final int INK_THRESHOLD = 26;
    private static final int STRONG_INK_THRESHOLD = 38;
    private static final int EXCLUSION_INK_THRESHOLD = 12;
    private static final float CONTENT_LINE_THRESHOLD = 0.003f;
    private static final float EXCLUSION_CONTENT_LINE_THRESHOLD = 0.001f;
    private static final float MIN_RUN_RATIO = 0.005f;
    private static final float MAX_CONTENT_AREA = 0.98f;
    private static final float MIN_CONTENT_AREA = 0.03f;
    private static final float EDGE_PERCENTILE = 0.35f;
    private static final float SAFETY_PADDING = 0.015f;
    private static final float SAFETY_EXPANSION_LIMIT = SAFETY_PADDING * 2f;
    private static final float MIN_SAVINGS = 0.02f;
    private static final float PARITY_COLLAPSE_EPSILON = 0.015f;
    private static final float EXCLUSION_TOLERANCE = 0.004f;
    private static final float MIN_PAGE_TIGHTEN = 0.05f;

    private CropMarginDetector() {
    }

    static Size detectionBitmapSize(Size pageSize) {
        int pageWidth = pageSize.getWidth();
        int pageHeight = pageSize.getHeight();
        if (pageWidth <= 0 || pageHeight <= 0) {
            return new Size(0, 0);
        }

        int longestSide = Math.max(pageWidth, pageHeight);
        float scale = Math.min(1f, TARGET_LONGEST_SIDE / (float) longestSide);
        int width = Math.max(1, Math.round(pageWidth * scale));
        int height = Math.max(1, Math.round(pageHeight * scale));
        return new Size(width, height);
    }

    static PageScan scan(Bitmap bitmap, ScanBuffers buffers) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 1 || height <= 1) {
            return null;
        }

        buffers.ensureCapacity(width, height);
        int[] pixels = buffers.pixels;
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int backgroundLuminance = estimateBorderLuminance(pixels, width, height);
        int[] rowInkCounts = buffers.rowInkCounts;
        int[] columnInkCounts = buffers.columnInkCounts;
        int[] strongRowInkCounts = buffers.strongRowInkCounts;
        int[] strongColumnInkCounts = buffers.strongColumnInkCounts;
        int[] exclusionRowInkCounts = buffers.exclusionRowInkCounts;
        int[] exclusionColumnInkCounts = buffers.exclusionColumnInkCounts;
        Arrays.fill(rowInkCounts, 0, height, 0);
        Arrays.fill(columnInkCounts, 0, width, 0);
        Arrays.fill(strongRowInkCounts, 0, height, 0);
        Arrays.fill(strongColumnInkCounts, 0, width, 0);
        Arrays.fill(exclusionRowInkCounts, 0, height, 0);
        Arrays.fill(exclusionColumnInkCounts, 0, width, 0);
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int luminance = luminance(pixels[rowOffset + x]);
                int distance = Math.abs(luminance - backgroundLuminance);
                if (distance > INK_THRESHOLD) {
                    rowInkCounts[y]++;
                    columnInkCounts[x]++;
                }
                if (distance > STRONG_INK_THRESHOLD) {
                    strongRowInkCounts[y]++;
                    strongColumnInkCounts[x]++;
                }
                if (distance > EXCLUSION_INK_THRESHOLD) {
                    exclusionRowInkCounts[y]++;
                    exclusionColumnInkCounts[x]++;
                }
            }
        }

        CropBounds bounds = detectBounds(rowInkCounts, columnInkCounts, width, height,
                CONTENT_LINE_THRESHOLD, CONTENT_LINE_THRESHOLD, 2, minRun(height), minRun(width),
                MIN_CONTENT_AREA, MAX_CONTENT_AREA, strongRowInkCounts, strongColumnInkCounts);
        CropBounds exclusionBounds = detectBounds(exclusionRowInkCounts, exclusionColumnInkCounts, width, height,
                EXCLUSION_CONTENT_LINE_THRESHOLD, EXCLUSION_CONTENT_LINE_THRESHOLD, 1, 1, 1,
                0f, 1f, null, null);
        return new PageScan(bounds, exclusionBounds);
    }

    private static CropBounds detectBounds(int[] rowInkCounts, int[] columnInkCounts, int width, int height,
                                            float rowThresholdRatio, float columnThresholdRatio,
                                            int minThreshold, int minRowRun, int minColumnRun,
                                            float minContentArea, float maxContentArea,
                                            int[] strongRowInkCounts, int[] strongColumnInkCounts) {
        boolean[] contentRows = thresholdContentLines(rowInkCounts, height, Math.max(minThreshold, Math.round(width * rowThresholdRatio)));
        boolean[] contentColumns = thresholdContentLines(columnInkCounts, width, Math.max(minThreshold, Math.round(height * columnThresholdRatio)));
        CropBounds cropBounds = null;
        if (strongRowInkCounts != null && strongColumnInkCounts != null) {
            boolean[] trimmedContentRows = contentRows.clone();
            boolean[] trimmedContentColumns = contentColumns.clone();
            trimWeakEdgeContent(trimmedContentRows, strongRowInkCounts);
            trimWeakEdgeContent(trimmedContentColumns, strongColumnInkCounts);
            cropBounds = detectBounds(trimmedContentRows, trimmedContentColumns, width, height,
                    minRowRun, minColumnRun, minContentArea, maxContentArea);
        }
        if (cropBounds == null) {
            cropBounds = detectBounds(contentRows, contentColumns, width, height,
                    minRowRun, minColumnRun, minContentArea, maxContentArea);
        }
        return cropBounds;
    }

    private static CropBounds detectBounds(boolean[] contentRows, boolean[] contentColumns, int width, int height,
                                           int minRowRun, int minColumnRun,
                                           float minContentArea, float maxContentArea) {

        int top = firstContentLine(contentRows, minRowRun);
        int bottom = lastContentLine(contentRows, minRowRun);
        int left = firstContentLine(contentColumns, minColumnRun);
        int right = lastContentLine(contentColumns, minColumnRun);

        if (top < 0 || bottom < top || left < 0 || right < left) {
            return null;
        }

        float cropLeft = left / (float) width;
        float cropTop = top / (float) height;
        float cropRight = (right + 1f) / width;
        float cropBottom = (bottom + 1f) / height;
        float cropWidth = cropRight - cropLeft;
        float cropHeight = cropBottom - cropTop;
        float cropArea = cropWidth * cropHeight;
        if (cropArea > maxContentArea || cropArea < minContentArea) {
            return null;
        }

        return CropBounds.of(cropLeft, cropTop, cropRight, cropBottom);
    }

    private static void trimWeakEdgeContent(boolean[] content, int[] strongInkCounts) {
        int first = 0;
        while (first < content.length && (!content[first] || strongInkCounts[first] == 0)) {
            content[first] = false;
            first++;
        }

        int last = content.length - 1;
        while (last >= first && (!content[last] || strongInkCounts[last] == 0)) {
            content[last] = false;
            last--;
        }
    }

    static CropMargins aggregateWithExclusions(List<Sample> samples) {
        Set<Integer> excludedPages = new LinkedHashSet<>();
        CropMargins cropMargins = CropMargins.fullPage();
        for (int i = 0; i < 3; i++) {
            List<Sample> validSamples = validSamplesExcept(samples, excludedPages);
            cropMargins = aggregate(validSamples, validSamples.size());
            Set<Integer> nextExcludedPages = excludedPages(samples, cropMargins);
            if (nextExcludedPages.equals(excludedPages)) {
                break;
            }
            excludedPages = nextExcludedPages;
        }
        if (cropMargins.isFullPage()) {
            return CropMargins.fullPage();
        }
        return new CropMargins(cropMargins.getEvenCrop(), cropMargins.getOddCrop(), pageCropOverrides(samples, excludedPages, cropMargins));
    }

    static CropMargins aggregate(List<Sample> samples, int sampledPageCount) {
        int requiredValidSamples = Math.min(MIN_VALID_SAMPLES, Math.max(1, sampledPageCount));
        if (samples.size() < requiredValidSamples) {
            return CropMargins.fullPage();
        }

        CropBounds combined = aggregateBounds(samples);

        List<Sample> evenSamples = new ArrayList<>();
        List<Sample> oddSamples = new ArrayList<>();
        for (Sample sample : samples) {
            if ((sample.documentPage & 1) == 0) {
                evenSamples.add(sample);
            } else {
                oddSamples.add(sample);
            }
        }

        if (evenSamples.size() >= MIN_VALID_SAMPLES && oddSamples.size() >= MIN_VALID_SAMPLES) {
            CropBounds even = aggregateBounds(evenSamples);
            CropBounds odd = aggregateBounds(oddSamples);
            if (!even.isFullPage() && !odd.isFullPage() && !even.isSimilarTo(odd, PARITY_COLLAPSE_EPSILON)) {
                return new CropMargins(even, odd);
            }
        }

        if (combined.isFullPage()) {
            return CropMargins.fullPage();
        }
        return CropMargins.single(combined);
    }

    private static List<Sample> validSamplesExcept(List<Sample> samples, Set<Integer> excludedPages) {
        List<Sample> validSamples = new ArrayList<>();
        for (Sample sample : samples) {
            if (sample.bounds != null && !excludedPages.contains(sample.documentPage)) {
                validSamples.add(sample);
            }
        }
        return validSamples;
    }

    private static Set<Integer> excludedPages(List<Sample> samples, CropMargins cropMargins) {
        Set<Integer> excludedPages = new LinkedHashSet<>();
        if (cropMargins == null || cropMargins.isFullPage()) {
            return excludedPages;
        }
        for (Sample sample : samples) {
            CropBounds crop = cropMargins.forDocumentPage(sample.documentPage);
            if (crop.isFullPage()) {
                continue;
            }
            CropBounds pageCropBounds = pageCropBounds(sample);
            if (pageCropBounds != null && contentOutsideCrop(pageCropBounds, crop)) {
                excludedPages.add(sample.documentPage);
            }
        }
        return excludedPages;
    }

    private static Map<Integer, CropBounds> pageCropOverrides(List<Sample> samples, Set<Integer> excludedPages,
                                                              CropMargins cropMargins) {
        Map<Integer, CropBounds> pageCrops = new LinkedHashMap<>();
        for (Sample sample : samples) {
            CropBounds documentCrop = cropMargins.forDocumentPage(sample.documentPage);
            if (excludedPages.contains(sample.documentPage)) {
                CropBounds pageCrop = pageCropOverride(sample);
                if (pageCrop == null) {
                    pageCrop = CropBounds.fullPage();
                }
                if (!pageCrop.isSimilarTo(documentCrop, PARITY_COLLAPSE_EPSILON)) {
                    pageCrops.put(sample.documentPage, pageCrop);
                }
                continue;
            }
            CropBounds tightened = tightenedOverride(sample, documentCrop);
            if (tightened != null) {
                pageCrops.put(sample.documentPage, tightened);
            }
        }
        return pageCrops;
    }

    private static CropBounds tightenedOverride(Sample sample, CropBounds documentCrop) {
        if (sample.bounds == null || documentCrop.isFullPage()) {
            return null;
        }
        CropBounds pageCrop = pad(pageCropBounds(sample));
        float left = Math.max(documentCrop.getLeft(), pageCrop.getLeft());
        float top = Math.max(documentCrop.getTop(), pageCrop.getTop());
        float right = Math.min(documentCrop.getRight(), pageCrop.getRight());
        float bottom = Math.min(documentCrop.getBottom(), pageCrop.getBottom());
        float tightening = Math.max(
                Math.max(left - documentCrop.getLeft(), documentCrop.getRight() - right),
                Math.max(top - documentCrop.getTop(), documentCrop.getBottom() - bottom));
        if (tightening < MIN_PAGE_TIGHTEN) {
            return null;
        }
        CropBounds tightened = CropBounds.of(left, top, right, bottom);
        if (tightened.isFullPage() || tightened.isSimilarTo(documentCrop, PARITY_COLLAPSE_EPSILON)) {
            return null;
        }
        return tightened;
    }

    private static CropBounds pageCropOverride(Sample sample) {
        if (sample.bounds == null) {
            return null;
        }

        CropBounds bounds = pageCropBounds(sample);
        CropBounds crop = pad(bounds);
        float savedWidth = crop.getLeft() + (1f - crop.getRight());
        float savedHeight = crop.getTop() + (1f - crop.getBottom());
        if (savedWidth < MIN_SAVINGS && savedHeight < MIN_SAVINGS) {
            return CropBounds.fullPage();
        }
        return crop;
    }

    private static CropBounds pageCropBounds(Sample sample) {
        if (sample.bounds == null) {
            return null;
        }
        if (sample.exclusionBounds != null && isCloseSafetyExpansion(sample.bounds, sample.exclusionBounds)) {
            return union(sample.bounds, sample.exclusionBounds);
        }
        return sample.bounds;
    }

    private static boolean isCloseSafetyExpansion(CropBounds bounds, CropBounds safetyBounds) {
        return safetyBounds.getLeft() >= bounds.getLeft() - SAFETY_EXPANSION_LIMIT
                && safetyBounds.getTop() >= bounds.getTop() - SAFETY_EXPANSION_LIMIT
                && safetyBounds.getRight() <= bounds.getRight() + SAFETY_EXPANSION_LIMIT
                && safetyBounds.getBottom() <= bounds.getBottom() + SAFETY_EXPANSION_LIMIT;
    }

    private static CropBounds union(CropBounds first, CropBounds second) {
        return CropBounds.of(
                Math.min(first.getLeft(), second.getLeft()),
                Math.min(first.getTop(), second.getTop()),
                Math.max(first.getRight(), second.getRight()),
                Math.max(first.getBottom(), second.getBottom()));
    }

    private static CropBounds pad(CropBounds bounds) {
        return CropBounds.of(
                bounds.getLeft() - SAFETY_PADDING,
                bounds.getTop() - SAFETY_PADDING,
                bounds.getRight() + SAFETY_PADDING,
                bounds.getBottom() + SAFETY_PADDING);
    }

    private static boolean contentOutsideCrop(CropBounds bounds, CropBounds crop) {
        return bounds.getLeft() < crop.getLeft() - EXCLUSION_TOLERANCE
                || bounds.getTop() < crop.getTop() - EXCLUSION_TOLERANCE
                || bounds.getRight() > crop.getRight() + EXCLUSION_TOLERANCE
                || bounds.getBottom() > crop.getBottom() + EXCLUSION_TOLERANCE;
    }

    private static CropBounds aggregateBounds(List<Sample> samples) {
        List<Float> leftMargins = new ArrayList<>(samples.size());
        List<Float> topMargins = new ArrayList<>(samples.size());
        List<Float> rightMargins = new ArrayList<>(samples.size());
        List<Float> bottomMargins = new ArrayList<>(samples.size());

        for (Sample sample : samples) {
            CropBounds bounds = sample.bounds;
            leftMargins.add(bounds.getLeft());
            topMargins.add(bounds.getTop());
            rightMargins.add(1f - bounds.getRight());
            bottomMargins.add(1f - bounds.getBottom());
        }

        float left = percentile(leftMargins, EDGE_PERCENTILE) - SAFETY_PADDING;
        float top = percentile(topMargins, EDGE_PERCENTILE) - SAFETY_PADDING;
        float right = 1f - percentile(rightMargins, EDGE_PERCENTILE) + SAFETY_PADDING;
        float bottom = 1f - percentile(bottomMargins, EDGE_PERCENTILE) + SAFETY_PADDING;
        CropBounds crop = CropBounds.of(left, top, right, bottom);

        float savedWidth = crop.getLeft() + (1f - crop.getRight());
        float savedHeight = crop.getTop() + (1f - crop.getBottom());
        if (savedWidth < MIN_SAVINGS && savedHeight < MIN_SAVINGS) {
            return CropBounds.fullPage();
        }
        return crop;
    }

    private static boolean[] thresholdContentLines(int[] inkCounts, int lineCount, int threshold) {
        boolean[] content = new boolean[lineCount];
        for (int i = 0; i < lineCount; i++) {
            content[i] = inkCounts[i] > threshold;
        }
        return content;
    }

    private static int firstContentLine(boolean[] content, int minRun) {
        for (int i = 0; i < content.length; i++) {
            if (hasRunForward(content, i, minRun)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastContentLine(boolean[] content, int minRun) {
        for (int i = content.length - 1; i >= 0; i--) {
            if (hasRunBackward(content, i, minRun)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasRunForward(boolean[] content, int start, int minRun) {
        if (!content[start] || start + minRun > content.length) {
            return false;
        }
        for (int i = start; i < start + minRun; i++) {
            if (!content[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasRunBackward(boolean[] content, int start, int minRun) {
        if (!content[start] || start - minRun + 1 < 0) {
            return false;
        }
        for (int i = start; i > start - minRun; i--) {
            if (!content[i]) {
                return false;
            }
        }
        return true;
    }

    private static int minRun(int lineCount) {
        return Math.max(3, Math.min(5, Math.round(lineCount * MIN_RUN_RATIO)));
    }

    private static float percentile(List<Float> values, float percentile) {
        Collections.sort(values);
        int index = (int) Math.floor((values.size() - 1) * percentile);
        return values.get(Math.max(0, Math.min(values.size() - 1, index)));
    }

    private static int estimateBorderLuminance(int[] pixels, int width, int height) {
        int[] border = new int[width * 2 + Math.max(0, height - 2) * 2];
        int index = 0;
        for (int x = 0; x < width; x++) {
            border[index++] = luminance(pixels[x]);
            border[index++] = luminance(pixels[(height - 1) * width + x]);
        }
        for (int y = 1; y < height - 1; y++) {
            border[index++] = luminance(pixels[y * width]);
            border[index++] = luminance(pixels[y * width + width - 1]);
        }
        if (index < border.length) {
            border = Arrays.copyOf(border, index);
        }
        Arrays.sort(border);
        return border[border.length / 2];
    }

    private static int luminance(int pixel) {
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = pixel & 0xff;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }

    static final class Sample {
        final int documentPage;
        final CropBounds bounds;
        final CropBounds exclusionBounds;

        Sample(int documentPage, CropBounds bounds) {
            this(documentPage, bounds, bounds);
        }

        Sample(int documentPage, CropBounds bounds, CropBounds exclusionBounds) {
            this.documentPage = documentPage;
            this.bounds = bounds;
            this.exclusionBounds = exclusionBounds;
        }
    }

    static final class PageScan {
        final CropBounds bounds;
        final CropBounds exclusionBounds;

        PageScan(CropBounds bounds, CropBounds exclusionBounds) {
            this.bounds = bounds;
            this.exclusionBounds = exclusionBounds;
        }
    }

    static final class ScanBuffers {
        private int[] pixels = new int[0];
        private int[] rowInkCounts = new int[0];
        private int[] columnInkCounts = new int[0];
        private int[] strongRowInkCounts = new int[0];
        private int[] strongColumnInkCounts = new int[0];
        private int[] exclusionRowInkCounts = new int[0];
        private int[] exclusionColumnInkCounts = new int[0];

        private void ensureCapacity(int width, int height) {
            int pixelCount = width * height;
            if (pixels.length < pixelCount) {
                pixels = new int[pixelCount];
            }
            if (rowInkCounts.length < height) {
                rowInkCounts = new int[height];
                strongRowInkCounts = new int[height];
                exclusionRowInkCounts = new int[height];
            }
            if (columnInkCounts.length < width) {
                columnInkCounts = new int[width];
                strongColumnInkCounts = new int[width];
                exclusionColumnInkCounts = new int[width];
            }
        }
    }
}
