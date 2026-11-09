// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.util.MathUtils;
import com.github.barteksc.pdfviewer.util.SnapEdge;
import com.shockwave.pdfium.util.SizeF;

final class ViewportMath {
    interface Layout {
        int getRowOfPage(int page);
        int getRowFirstPage(int row);
        int getPagesInRow(int row);
        float getRowOffset(int row, float zoom);
        float getRowLength(int row, float zoom);
        float getRowSpacing(int row, float zoom);
        int getRowAtLayoutIndex(int index);
        int getRowCount();
        float getDocLen(float zoom);
        float getMaxPageWidth();
        float getMaxPageHeight();
        float getPageOffset(int page, float zoom);
        float getSecondaryPageOffset(int page, float zoom);
        SizeF getPageSize(int page);
        SizeF getScaledPageSize(int page, float zoom);
        float getPageLength(int page, float zoom);
        int getPageAtOffset(float offset, float zoom);
        int getRowLayoutIndexAtOffset(float offset, float zoom);
    }

    static float getPositionOffset(Layout layout, int width, int height, float zoom, boolean swipeVertical, float currentXOffset, float currentYOffset) {
        float offset;
        if (swipeVertical) {
            offset = -currentYOffset / (layout.getDocLen(zoom) - height);
        } else {
            offset = -currentXOffset / (layout.getDocLen(zoom) - width);
        }
        return MathUtils.limit(offset, 0, 1);
    }

    static float positionOffset(float documentLength, int viewSize, float progress) {
        return (-documentLength + viewSize) * progress;
    }

    static SnapEdge findSnapEdge(Layout layout, int width, int height, float zoom, boolean swipeVertical, float currentXOffset, float currentYOffset, boolean pageSnap, int page) {
        if (!pageSnap || page < 0) {
            return SnapEdge.NONE;
        }
        int row = layout.getRowOfPage(page);
        float currentOffset = swipeVertical ? currentYOffset : currentXOffset;
        float offset = -layout.getRowOffset(row, zoom);
        int length = swipeVertical ? height : width;
        float pageLength = layout.getRowLength(row, zoom);

        if (length >= pageLength) {
            return SnapEdge.CENTER;
        }
        else if (currentOffset >= offset) {
            return SnapEdge.START;
        }
        else if (offset - pageLength > currentOffset - length) {
            return SnapEdge.END;
        }
        else {
            return SnapEdge.NONE;
        }
    }

    static float snapOffsetForPage(Layout layout, int width, int height, float zoom, boolean swipeVertical, int pageIndex, SnapEdge edge) {
        int row = layout.getRowOfPage(pageIndex);
        float offset = layout.getRowOffset(row, zoom);

        float length = swipeVertical ? height : width;
        float pageLength = layout.getRowLength(row, zoom);

        if (edge == SnapEdge.CENTER) {
            offset = offset - length / 2f + pageLength / 2f;
        } else if (edge == SnapEdge.END) {
            offset = offset - length + pageLength;
        }
        return offset;
    }

    static int findFocusPage(Layout layout, int width, int height, float zoom, boolean swipeVertical, float xOffset, float yOffset) {
        float currOffset = swipeVertical ? yOffset : xOffset;
        float length = swipeVertical ? height : width;
        if (currOffset > -1) {
            return layout.getRowFirstPage(layout.getRowAtLayoutIndex(0));
        } else if (currOffset < -layout.getDocLen(zoom) + length + 1) {
            int lastRow = layout.getRowAtLayoutIndex(layout.getRowCount() - 1);
            return layout.getRowFirstPage(lastRow) + layout.getPagesInRow(lastRow) - 1;
        }
        float center = currOffset - length / 2f;
        return layout.getPageAtOffset(-center, zoom);
    }

    static float clampTargetXOffset(Layout layout, int width, int height, float zoom, boolean swipeVertical, float offsetX) {
        if (swipeVertical) {
            float scaledPageWidth = layout.getMaxPageWidth() * zoom;
            if (scaledPageWidth < width) {
                return width / 2f - scaledPageWidth / 2f;
            }
            return Math.max(Math.min(offsetX, 0), width - scaledPageWidth);
        }
        float contentWidth = layout.getDocLen(zoom);
        if (contentWidth < width) {
            return (width - contentWidth) / 2f;
        }
        return Math.max(Math.min(offsetX, 0), width - contentWidth);
    }

    static float clampTargetYOffset(Layout layout, int width, int height, float zoom, boolean swipeVertical, float offsetY) {
        if (swipeVertical) {
            float contentHeight = layout.getDocLen(zoom);
            if (contentHeight < height) {
                return (height - contentHeight) / 2f;
            }
            return Math.max(Math.min(offsetY, 0), height - contentHeight);
        }
        float scaledPageHeight = layout.getMaxPageHeight() * zoom;
        if (scaledPageHeight < height) {
            return height / 2f - scaledPageHeight / 2f;
        }
        return Math.max(Math.min(offsetY, 0), height - scaledPageHeight);
    }

    static boolean pageFillsScreen(Layout layout, int width, int height, float zoom, boolean swipeVertical, int currentPage, float currentXOffset, float currentYOffset) {
        int row = layout.getRowOfPage(currentPage);
        float start = -layout.getRowOffset(row, zoom);
        float end = start - layout.getRowLength(row, zoom);
        if (swipeVertical) {
            return start > currentYOffset && end < currentYOffset - height;
        } else {
            return start > currentXOffset && end < currentXOffset - width;
        }
    }

    static int getPageAfterRowStep(Layout layout, int fromPage, int direction) {
        int row = layout.getRowOfPage(fromPage);
        int targetRow = Math.max(0, Math.min(layout.getRowCount() - 1, row + direction));
        return layout.getRowFirstPage(targetRow);
    }

    static long visiblePageRange(Layout layout, int width, int height, float zoom, boolean swipeVertical, float currentXOffset, float currentYOffset, int count) {
        if (count <= 0) {
            return -1L;
        }
        float offset = swipeVertical ? currentYOffset : currentXOffset;
        float viewSize = swipeVertical ? height : width;
        float startDist = Math.max(0f, -offset);
        float endDist = Math.max(0f, -offset + viewSize);
        int first = layout.getPageAtOffset(startDist, zoom);
        int last = layout.getPageAtOffset(endDist, zoom);
        int from = Math.max(0, Math.min(first, last) - 1);
        int to = Math.min(count - 1, Math.max(first, last) + 1);
        return ((long) from << 32) | (to & 0xFFFFFFFFL);
    }

    static int getPageAtPositionOffset(Layout layout, float zoom, float positionOffset) {
        return layout.getPageAtOffset(layout.getDocLen(zoom) * positionOffset, zoom);
    }

    static float[] moveTo(Layout layout, int width, int height, float zoom, boolean swipeVertical, float offsetX, float offsetY) {
        if (swipeVertical) {
            float scaledPageWidth = layout.getMaxPageWidth() * zoom;
            if (scaledPageWidth < width) {
                offsetX = width / 2 - scaledPageWidth / 2;
            } else {
                if (offsetX > 0) {
                    offsetX = 0;
                } else if (offsetX + scaledPageWidth < width) {
                    offsetX = width - scaledPageWidth;
                }
            }
            float contentHeight = layout.getDocLen(zoom);
            if (contentHeight < height) {
                offsetY = (height - contentHeight) / 2;
            } else {
                if (offsetY > 0) {
                    offsetY = 0;
                } else if (offsetY + contentHeight < height) {
                    offsetY = -contentHeight + height;
                }
            }

        } else {
            float scaledPageHeight = layout.getMaxPageHeight() * zoom;
            if (scaledPageHeight < height) {
                offsetY = height / 2 - scaledPageHeight / 2;
            } else {
                if (offsetY > 0) {
                    offsetY = 0;
                } else if (offsetY + scaledPageHeight < height) {
                    offsetY = height - scaledPageHeight;
                }
            }
            float contentWidth = layout.getDocLen(zoom);
            if (contentWidth < width) {
                offsetX = (width - contentWidth) / 2;
            } else {
                if (offsetX > 0) {
                    offsetX = 0;
                } else if (offsetX + contentWidth < width) {
                    offsetX = -contentWidth + width;
                }
            }

        }

        return new float[]{offsetX, offsetY};
    }

    static PDFView.ViewState captureViewState(Layout layout, int width, int height, float zoom, boolean swipeVertical, float currentXOffset, float currentYOffset, int pageIndex, boolean horizontalRtl, int pagesPerRow, boolean firstPageAlone) {
        float centerX = -currentXOffset + width * 0.5f;
        float centerY = -currentYOffset + height * 0.5f;
        float relativeCrossAxisCenter;
        float pageCenterOffsetRatio;

        if (swipeVertical) {
            relativeCrossAxisCenter = ratioOrDefault(centerX, layout.getMaxPageWidth(), 0.5f);
            pageCenterOffsetRatio = ratioOrDefault(
                    centerY - layout.getPageOffset(pageIndex, zoom),
                    layout.getPageLength(pageIndex, zoom),
                    0.5f);
        } else {
            relativeCrossAxisCenter = ratioOrDefault(centerY, layout.getMaxPageHeight(), 0.5f);
            pageCenterOffsetRatio = ratioOrDefault(
                    centerX - layout.getPageOffset(pageIndex, zoom),
                    layout.getPageLength(pageIndex, zoom),
                    0.5f);
        }

        return new PDFView.ViewState(
                zoom,
                pageIndex,
                swipeVertical,
                horizontalRtl,
                relativeCrossAxisCenter,
                MathUtils.limit(pageCenterOffsetRatio, 0f, 1f),
                pagesPerRow,
                firstPageAlone);
    }

    static float[] applyViewState(Layout layout, int width, int height, float zoom, boolean swipeVertical, PDFView.ViewState viewState, int pageIndex, float pageLength) {
        float pageCenterOffsetRatio = isValidNumber(viewState.pageCenterOffsetRatio)
                ? MathUtils.limit(viewState.pageCenterOffsetRatio, 0f, 1f)
                : 0.5f;
        float relativeCrossAxisCenter = isValidNumber(viewState.relativeCrossAxisCenter)
                ? viewState.relativeCrossAxisCenter
                : 0.5f;
        float pageCenterOffset = layout.getPageOffset(pageIndex, zoom) + pageLength * pageCenterOffsetRatio;

        float offsetX;
        float offsetY;
        if (swipeVertical) {
            float crossAxisSize = layout.getMaxPageWidth();
            if (!isUsable(crossAxisSize)) {
                return null;
            }
            offsetX = -(relativeCrossAxisCenter * crossAxisSize) + width * 0.5f;
            offsetY = -pageCenterOffset + height * 0.5f;
        } else {
            float crossAxisSize = layout.getMaxPageHeight();
            if (!isUsable(crossAxisSize)) {
                return null;
            }
            offsetX = -pageCenterOffset + width * 0.5f;
            offsetY = -(relativeCrossAxisCenter * crossAxisSize) + height * 0.5f;
        }

        return new float[]{offsetX, offsetY};
    }

    static float validZoom(float value, float minZoom, float maxZoom) {
        if (!isValidNumber(value)) {
            return 1f;
        }
        return MathUtils.limit(value, minZoom, maxZoom);
    }

    static float ratioOrDefault(float numerator, float denominator, float fallback) {
        if (!isUsable(denominator)) {
            return fallback;
        }
        float ratio = numerator / denominator;
        return isValidNumber(ratio) ? ratio : fallback;
    }

    static boolean isUsable(float value) {
        return isValidNumber(value) && value > 0f;
    }

    static boolean isValidNumber(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
