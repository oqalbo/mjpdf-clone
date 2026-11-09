/**
 * Copyright 2017 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer;

import static com.github.barteksc.pdfviewer.util.Constants.Cache.CACHE_SIZE;
import static com.github.barteksc.pdfviewer.util.Constants.PRELOAD_OFFSET;

import android.graphics.RectF;

import com.github.barteksc.pdfviewer.model.PagePart;
import com.github.barteksc.pdfviewer.util.Constants;
import com.github.barteksc.pdfviewer.util.MathUtils;
import com.github.barteksc.pdfviewer.util.Util;
import com.shockwave.pdfium.util.SizeF;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class PagesLoader {

    private PDFView pdfView;
    private int cacheOrder;
    private float xOffset;
    private float yOffset;
    private float pageRelativePartWidth;
    private float pageRelativePartHeight;
    private float partRenderWidth;
    private float partRenderHeight;
    private final int preloadOffset;

    static class Holder {
        int row;
        int col;

        @Override
        public String toString() {
            return "Holder{" +
                    "row=" + row +
                    ", col=" + col +
                    '}';
        }
    }

    static class RenderRange {
        int page;
        GridSize gridSize;
        Holder leftTop;
        Holder rightBottom;

        RenderRange() {
            this.page = 0;
            this.gridSize = new GridSize();
            this.leftTop = new Holder();
            this.rightBottom = new Holder();
        }

        @Override
        public String toString() {
            return "RenderRange{" +
                    "page=" + page +
                    ", gridSize=" + gridSize +
                    ", leftTop=" + leftTop +
                    ", rightBottom=" + rightBottom +
                    '}';
        }
    }

    private static class DebugCell {
        final int page;
        final RectF bounds;

        DebugCell(int page, RectF bounds) {
            this.page = page;
            this.bounds = bounds;
        }
    }

    private final List<DebugCell> debugCells = new ArrayList<>();

    private void debugBeginPass() {
        if (PDFView.isDebugStateEnabled()) {
            debugCells.clear();
        }
    }

    private void debugRecordCell(int page, RectF pageRelativeBounds) {
        if (PDFView.isDebugStateEnabled()) {
            debugCells.add(new DebugCell(page, new RectF(pageRelativeBounds)));
        }
    }

    String debugQuality() {
        if (debugCells.isEmpty()) {
            return "none";
        }
        boolean snapshot = false;
        for (DebugCell cell : debugCells) {
            PagePart part = pdfView.cacheManager.debugActivePart(cell.page, cell.bounds);
            if (part == null || part.isStale() || part.getGeneration() != pdfView.getPageGeneration(cell.page)) {
                return "preview";
            }
            if (part.isSnapshot()) {
                snapshot = true;
            }
        }
        return snapshot ? "snapshot" : "full";
    }

    static class GridSize {
        int rows;
        int cols;

        @Override
        public String toString() {
            return "GridSize{" +
                    "rows=" + rows +
                    ", cols=" + cols +
                    '}';
        }
    }

    PagesLoader(PDFView pdfView) {
        this.pdfView = pdfView;
        this.preloadOffset = Util.getDP(pdfView.getContext(), PRELOAD_OFFSET);
    }

    static void getPageColsRows(ViewportMath.Layout layout, float zoom, GridSize grid, int pageIndex) {
        SizeF size = layout.getPageSize(pageIndex);
        float ratioX = 1f / size.getWidth();
        float ratioY = 1f / size.getHeight();
        final float partHeight = (Constants.PART_SIZE * ratioY) / zoom;
        final float partWidth = (Constants.PART_SIZE * ratioX) / zoom;
        grid.rows = MathUtils.ceil(1f / partHeight);
        grid.cols = MathUtils.ceil(1f / partWidth);
    }

    private void calculatePartSize(GridSize grid) {
        pageRelativePartWidth = 1f / (float) grid.cols;
        pageRelativePartHeight = 1f / (float) grid.rows;
        partRenderWidth = Constants.PART_SIZE / pageRelativePartWidth;
        partRenderHeight = Constants.PART_SIZE / pageRelativePartHeight;
    }


    /**
     * calculate the render range of each page
     */
    private List<RenderRange> getRenderRangeList(float firstXOffset, float firstYOffset, float lastXOffset, float lastYOffset) {
        return getRenderRangeList(pdfView.pdfFile, pdfView.getZoom(), pdfView.isSwipeVertical(),
                firstXOffset, firstYOffset, lastXOffset, lastYOffset);
    }

    static List<RenderRange> getRenderRangeList(ViewportMath.Layout layout, float zoom, boolean swipeVertical, float firstXOffset, float firstYOffset, float lastXOffset, float lastYOffset) {

        float fixedFirstXOffset = -MathUtils.max(firstXOffset, 0);
        float fixedFirstYOffset = -MathUtils.max(firstYOffset, 0);

        float fixedLastXOffset = -MathUtils.max(lastXOffset, 0);
        float fixedLastYOffset = -MathUtils.max(lastYOffset, 0);

        float offsetFirst = swipeVertical ? fixedFirstYOffset : fixedFirstXOffset;
        float offsetLast = swipeVertical ? fixedLastYOffset : fixedLastXOffset;

        int firstLayoutIndex = layout.getRowLayoutIndexAtOffset(offsetFirst, zoom);
        int lastLayoutIndex = layout.getRowLayoutIndexAtOffset(offsetLast, zoom);
        if (lastLayoutIndex < firstLayoutIndex) {
            int swap = firstLayoutIndex;
            firstLayoutIndex = lastLayoutIndex;
            lastLayoutIndex = swap;
        }

        List<RenderRange> renderRanges = new LinkedList<>();

        for (int layoutIndex = firstLayoutIndex; layoutIndex <= lastLayoutIndex; layoutIndex++) {
            int rowIndex = layout.getRowAtLayoutIndex(layoutIndex);
            int rowFirstPage = layout.getRowFirstPage(rowIndex);
            for (int member = 0; member < layout.getPagesInRow(rowIndex); member++) {
                int page = rowFirstPage + member;
                RenderRange range = new RenderRange();
                range.page = page;

                float pageFirstXOffset, pageFirstYOffset, pageLastXOffset, pageLastYOffset;
                if (layoutIndex == firstLayoutIndex) {
                    pageFirstXOffset = fixedFirstXOffset;
                    pageFirstYOffset = fixedFirstYOffset;
                    if (firstLayoutIndex == lastLayoutIndex) {
                        pageLastXOffset = fixedLastXOffset;
                        pageLastYOffset = fixedLastYOffset;
                    } else {
                        float pageOffset = layout.getPageOffset(page, zoom);
                        SizeF pageSize = layout.getScaledPageSize(page, zoom);
                        if (swipeVertical) {
                            pageLastXOffset = fixedLastXOffset;
                            pageLastYOffset = pageOffset + pageSize.getHeight();
                        } else {
                            pageLastYOffset = fixedLastYOffset;
                            pageLastXOffset = pageOffset + pageSize.getWidth();
                        }
                    }
                } else if (layoutIndex == lastLayoutIndex) {
                    float pageOffset = layout.getPageOffset(page, zoom);

                    if (swipeVertical) {
                        pageFirstXOffset = fixedFirstXOffset;
                        pageFirstYOffset = pageOffset;
                    } else {
                        pageFirstYOffset = fixedFirstYOffset;
                        pageFirstXOffset = pageOffset;
                    }

                    pageLastXOffset = fixedLastXOffset;
                    pageLastYOffset = fixedLastYOffset;

                } else {
                    float pageOffset = layout.getPageOffset(page, zoom);
                    SizeF pageSize = layout.getScaledPageSize(page, zoom);
                    if (swipeVertical) {
                        pageFirstXOffset = fixedFirstXOffset;
                        pageFirstYOffset = pageOffset;

                        pageLastXOffset = fixedLastXOffset;
                        pageLastYOffset = pageOffset + pageSize.getHeight();
                    } else {
                        pageFirstXOffset = pageOffset;
                        pageFirstYOffset = fixedFirstYOffset;

                        pageLastXOffset = pageOffset + pageSize.getWidth();
                        pageLastYOffset = fixedLastYOffset;
                    }
                }

                getPageColsRows(layout, zoom, range.gridSize, range.page);
                SizeF scaledPageSize = layout.getScaledPageSize(range.page, zoom);
                float rowHeight = scaledPageSize.getHeight() / range.gridSize.rows;
                float colWidth = scaledPageSize.getWidth() / range.gridSize.cols;

                float secondaryOffset = layout.getSecondaryPageOffset(page, zoom);

                if (swipeVertical) {
                    range.leftTop.row = MathUtils.floor(Math.abs(pageFirstYOffset - layout.getPageOffset(range.page, zoom)) / rowHeight);
                    range.leftTop.col = MathUtils.floor(MathUtils.min(pageFirstXOffset - secondaryOffset, 0) / colWidth);

                    range.rightBottom.row = MathUtils.ceil(Math.abs(pageLastYOffset - layout.getPageOffset(range.page, zoom)) / rowHeight);
                    range.rightBottom.col = MathUtils.floor(MathUtils.min(pageLastXOffset - secondaryOffset, 0) / colWidth);
                } else {
                    range.leftTop.col = MathUtils.floor(Math.abs(pageFirstXOffset - layout.getPageOffset(range.page, zoom)) / colWidth);
                    range.leftTop.row = MathUtils.floor(MathUtils.min(pageFirstYOffset - secondaryOffset, 0) / rowHeight);

                    range.rightBottom.col = MathUtils.floor(Math.abs(pageLastXOffset - layout.getPageOffset(range.page, zoom)) / colWidth);
                    range.rightBottom.row = MathUtils.floor(MathUtils.min(pageLastYOffset - secondaryOffset, 0) / rowHeight);
                }

                renderRanges.add(range);
            }
        }

        return renderRanges;
    }

    private void loadVisible() {
        debugBeginPass();
        int parts = 0;
        float scaledPreloadOffset = preloadOffset;
        float firstXOffset = -xOffset + scaledPreloadOffset;
        float lastXOffset = -xOffset - pdfView.getWidth() - scaledPreloadOffset;
        float firstYOffset = -yOffset + scaledPreloadOffset;
        float lastYOffset = -yOffset - pdfView.getHeight() - scaledPreloadOffset;

        List<RenderRange> rangeList = getRenderRangeList(firstXOffset, firstYOffset, lastXOffset, lastYOffset);

        for (RenderRange range : rangeList) {
            loadPreview(range.page);
        }

        for (RenderRange range : rangeList) {
            calculatePartSize(range.gridSize);
            parts += loadPage(range.page, range.leftTop.row, range.rightBottom.row, range.leftTop.col, range.rightBottom.col, CACHE_SIZE - parts);
            if (parts >= CACHE_SIZE) {
                break;
            }
        }

    }

    private int loadPage(int page, int firstRow, int lastRow, int firstCol, int lastCol,
                         int nbOfPartsLoadable) {
        int loaded = 0;
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) {
                if (loadCell(page, row, col, pageRelativePartWidth, pageRelativePartHeight)) {
                    loaded++;
                }
                if (loaded >= nbOfPartsLoadable) {
                    return loaded;
                }
            }
        }
        return loaded;
    }

    private boolean loadCell(int page, int row, int col, float pageRelativePartWidth, float pageRelativePartHeight) {

        float relX = pageRelativePartWidth * col;
        float relY = pageRelativePartHeight * row;
        float relWidth = pageRelativePartWidth;
        float relHeight = pageRelativePartHeight;

        float renderWidth = partRenderWidth;
        float renderHeight = partRenderHeight;
        if (relX + relWidth > 1) {
            relWidth = 1 - relX;
        }
        if (relY + relHeight > 1) {
            relHeight = 1 - relY;
        }
        renderWidth *= relWidth;
        renderHeight *= relHeight;
        float relRight = relX + relWidth;
        float relBottom = relY + relHeight;
        RectF pageRelativeBounds = new RectF();
        pageRelativeBounds.left = relX;
        pageRelativeBounds.top = relY;
        pageRelativeBounds.right = relRight;
        pageRelativeBounds.bottom = relBottom;

        if (renderWidth > 0 && renderHeight > 0) {
            debugRecordCell(page, pageRelativeBounds);
            if (!pdfView.cacheManager.upPartIfContained(page, pageRelativeBounds, cacheOrder)) {
                pdfView.renderScheduler.submit(RenderTask.tile(page,
                        relX, relY, relRight, relBottom,
                        renderWidth, renderHeight, false, cacheOrder,
                        pdfView.isBestQuality(), pdfView.isAnnotationRendering()));
            }

            cacheOrder++;
            return true;
        }
        return false;
    }

    private void loadPreview(int page) {
        if (pdfView.peekPreview(page) == null) {
            pdfView.requestPreview(page);
        }
    }

    void loadPages() {
        xOffset = -MathUtils.max(pdfView.getCurrentXOffset(), 0);
        yOffset = -MathUtils.max(pdfView.getCurrentYOffset(), 0);

        loadVisible();
    }

    void loadViewportSnapshot() {
        debugBeginPass();
        xOffset = -MathUtils.max(pdfView.getCurrentXOffset(), 0);
        yOffset = -MathUtils.max(pdfView.getCurrentYOffset(), 0);
        float firstXOffset = -xOffset + preloadOffset;
        float lastXOffset = -xOffset - pdfView.getWidth() - preloadOffset;
        float firstYOffset = -yOffset + preloadOffset;
        float lastYOffset = -yOffset - pdfView.getHeight() - preloadOffset;

        for (RenderRange range : getRenderRangeList(firstXOffset, firstYOffset, lastXOffset, lastYOffset)) {
            loadPreview(range.page);
            loadSnapshotPart(range);
        }
    }

    private void loadSnapshotPart(RenderRange range) {
        calculatePartSize(range.gridSize);
        float relLeft = range.leftTop.col * pageRelativePartWidth;
        float relTop = range.leftTop.row * pageRelativePartHeight;
        float relRight = Math.min((range.rightBottom.col + 1) * pageRelativePartWidth, 1f);
        float relBottom = Math.min((range.rightBottom.row + 1) * pageRelativePartHeight, 1f);

        float renderWidth = partRenderWidth * (relRight - relLeft);
        float renderHeight = partRenderHeight * (relBottom - relTop);
        if (renderWidth <= 0 || renderHeight <= 0) {
            return;
        }

        RectF pageRelativeBounds = new RectF();
        pageRelativeBounds.left = relLeft;
        pageRelativeBounds.top = relTop;
        pageRelativeBounds.right = relRight;
        pageRelativeBounds.bottom = relBottom;
        debugRecordCell(range.page, pageRelativeBounds);
        if (!pdfView.cacheManager.upPartIfContained(range.page, pageRelativeBounds, cacheOrder)) {
            pdfView.renderScheduler.submit(RenderTask.tile(range.page,
                    relLeft, relTop, relRight, relBottom,
                    renderWidth, renderHeight, true, cacheOrder,
                    pdfView.isBestQuality(), pdfView.isAnnotationRendering()));
        }
        cacheOrder++;
    }
}
