package com.github.barteksc.pdfviewer.model;

import java.util.Locale;

/**
 * A crop rectangle expressed as normalized page fractions in Android display coordinates.
 */
public final class CropBounds {

    private static final float FULL_LEFT = 0f;
    private static final float FULL_TOP = 0f;
    private static final float FULL_RIGHT = 1f;
    private static final float FULL_BOTTOM = 1f;
    private static final float MIN_SIZE = 0.01f;
    private static final float FULL_PAGE_EPSILON = 0.0001f;

    private static final CropBounds FULL_PAGE = new CropBounds(FULL_LEFT, FULL_TOP, FULL_RIGHT, FULL_BOTTOM);

    private final float left;
    private final float top;
    private final float right;
    private final float bottom;

    private CropBounds(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static CropBounds fullPage() {
        return FULL_PAGE;
    }

    public static CropBounds of(float left, float top, float right, float bottom) {
        if (!isUsable(left) || !isUsable(top) || !isUsable(right) || !isUsable(bottom)) {
            return fullPage();
        }

        float safeLeft = clamp01(left);
        float safeTop = clamp01(top);
        float safeRight = clamp01(right);
        float safeBottom = clamp01(bottom);
        if (safeRight - safeLeft < MIN_SIZE || safeBottom - safeTop < MIN_SIZE) {
            return fullPage();
        }

        return new CropBounds(safeLeft, safeTop, safeRight, safeBottom);
    }

    public float getLeft() {
        return left;
    }

    public float getTop() {
        return top;
    }

    public float getRight() {
        return right;
    }

    public float getBottom() {
        return bottom;
    }

    public float getWidth() {
        return right - left;
    }

    public float getHeight() {
        return bottom - top;
    }

    public boolean isFullPage() {
        return Math.abs(left - FULL_LEFT) < FULL_PAGE_EPSILON
                && Math.abs(top - FULL_TOP) < FULL_PAGE_EPSILON
                && Math.abs(right - FULL_RIGHT) < FULL_PAGE_EPSILON
                && Math.abs(bottom - FULL_BOTTOM) < FULL_PAGE_EPSILON;
    }

    public boolean isSimilarTo(CropBounds other, float epsilon) {
        return other != null
                && Math.abs(left - other.left) <= epsilon
                && Math.abs(top - other.top) <= epsilon
                && Math.abs(right - other.right) <= epsilon
                && Math.abs(bottom - other.bottom) <= epsilon;
    }

    String toStorageString() {
        return String.format(Locale.US, "%f,%f,%f,%f", left, top, right, bottom);
    }

    static CropBounds fromStorageString(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 4) {
            return null;
        }
        for (String part : parts) {
            if (part.isEmpty() || !part.equals(part.trim())) {
                return null;
            }
        }
        try {
            float left = Float.parseFloat(parts[0]);
            float top = Float.parseFloat(parts[1]);
            float right = Float.parseFloat(parts[2]);
            float bottom = Float.parseFloat(parts[3]);
            if (!isUsable(left) || !isUsable(top) || !isUsable(right) || !isUsable(bottom)) {
                return null;
            }
            if (!isInUnitRange(left) || !isInUnitRange(top) || !isInUnitRange(right) || !isInUnitRange(bottom)) {
                return null;
            }
            if (right - left < MIN_SIZE || bottom - top < MIN_SIZE) {
                return null;
            }
            return new CropBounds(left, top, right, bottom);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isUsable(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static boolean isInUnitRange(float value) {
        return value >= 0f && value <= 1f;
    }
}
