// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import java.util.concurrent.atomic.AtomicBoolean;

class RenderTask {

    enum Kind { TILE, PREWARM, PREVIEW }

    static final int P0 = 0;
    static final int P1 = 1;
    static final int P2 = 2;

    final Kind kind;
    final int page;
    final boolean hasBounds;
    final float boundsLeft;
    final float boundsTop;
    final float boundsRight;
    final float boundsBottom;
    final float renderWidth;
    final float renderHeight;
    final boolean snapshot;
    final int cacheOrder;
    final boolean bestQuality;
    final boolean annotationRendering;
    final int priorityClass;

    final AtomicBoolean cancel = new AtomicBoolean(false);

    long waveId;
    long seq;
    int generation;

    private boolean queueCancelled;

    RenderTask(Kind kind, int page, boolean hasBounds,
               float boundsLeft, float boundsTop, float boundsRight, float boundsBottom,
               float renderWidth, float renderHeight, boolean snapshot,
               int cacheOrder, boolean bestQuality, boolean annotationRendering, int priorityClass) {
        this.kind = kind;
        this.page = page;
        this.hasBounds = hasBounds;
        this.boundsLeft = boundsLeft;
        this.boundsTop = boundsTop;
        this.boundsRight = boundsRight;
        this.boundsBottom = boundsBottom;
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
        this.snapshot = snapshot;
        this.cacheOrder = cacheOrder;
        this.bestQuality = bestQuality;
        this.annotationRendering = annotationRendering;
        this.priorityClass = priorityClass;
    }

    static RenderTask tile(int page, float left, float top, float right, float bottom,
                           float renderWidth, float renderHeight, boolean snapshot,
                           int cacheOrder, boolean bestQuality, boolean annotationRendering) {
        return new RenderTask(Kind.TILE, page, true, left, top, right, bottom,
                renderWidth, renderHeight, snapshot, cacheOrder, bestQuality, annotationRendering, P0);
    }

    static RenderTask prewarm(int page) {
        return new RenderTask(Kind.PREWARM, page, false, 0f, 0f, 0f, 0f,
                0f, 0f, false, 0, false, false, P0);
    }

    static RenderTask preview(int page, float renderWidth, float renderHeight,
                              boolean annotationRendering, int priorityClass) {
        return new RenderTask(Kind.PREVIEW, page, true, 0f, 0f, 1f, 1f,
                renderWidth, renderHeight, false, 0, false, annotationRendering, priorityClass);
    }

    RenderTask copyForResubmit() {
        return new RenderTask(kind, page, hasBounds, boundsLeft, boundsTop, boundsRight, boundsBottom,
                renderWidth, renderHeight, snapshot, cacheOrder, bestQuality, annotationRendering, priorityClass);
    }

    void cancelFromQueue() {
        queueCancelled = true;
        cancel.set(true);
    }

    boolean isQueueCancelled() {
        return queueCancelled;
    }

    boolean equivalentTo(RenderTask other) {
        return other != null
                && kind == other.kind
                && page == other.page
                && generation == other.generation
                && boundsLeft == other.boundsLeft
                && boundsTop == other.boundsTop
                && boundsRight == other.boundsRight
                && boundsBottom == other.boundsBottom
                && renderWidth == other.renderWidth
                && renderHeight == other.renderHeight;
    }
}
