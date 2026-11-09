// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

final class StampFrame {
    private static final float MIN_WIDTH_PAGE_FRACTION = 0.05f;
    float left;
    float top;
    float right;
    float bottom;

    void move(float dx, float dy, float frameW, float frameH) {
        float clampedDx = clamp(dx, -left, frameW - right);
        float clampedDy = clamp(dy, -top, frameH - bottom);
        left += clampedDx;
        top += clampedDy;
        right += clampedDx;
        bottom += clampedDy;
    }

    void resize(StampPlacementManager.DragMode mode, float dxFrame, float frameWidth, float frameHeight, float aspect) {
        float minWidth = MIN_WIDTH_PAGE_FRACTION * frameWidth;
        float width = (right - left);
        float newWidth;
        float maxWidth;
        switch (mode) {
            case RESIZE_TL:
                newWidth = width - dxFrame;
                maxWidth = Math.min(right, bottom / aspect);
                newWidth = clamp(newWidth, minWidth, maxWidth);
                left = right - newWidth;
                top = bottom - newWidth * aspect;
                break;
            case RESIZE_BL:
                newWidth = width - dxFrame;
                maxWidth = Math.min(right, (frameHeight - top) / aspect);
                newWidth = clamp(newWidth, minWidth, maxWidth);
                left = right - newWidth;
                bottom = top + newWidth * aspect;
                break;
            case RESIZE_BR:
                newWidth = width + dxFrame;
                maxWidth = Math.min(frameWidth - left, (frameHeight - top) / aspect);
                newWidth = clamp(newWidth, minWidth, maxWidth);
                right = left + newWidth;
                bottom = top + newWidth * aspect;
                break;
            default:
                break;
        }
    }

    static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    static int invertColor(int color) {
        return 0xFF000000 | (~color & 0x00FFFFFF);
    }
}
