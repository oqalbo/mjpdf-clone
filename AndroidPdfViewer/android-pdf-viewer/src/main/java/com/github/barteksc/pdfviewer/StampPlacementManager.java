package com.github.barteksc.pdfviewer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.shockwave.pdfium.util.SizeF;

final class StampPlacementManager {

    private static final int ACCENT_COLOR = 0xFF3F51B5;
    private static final float HANDLE_RADIUS_DP = 6f;
    private static final float HANDLE_TOUCH_RADIUS_DP = 22f;
    private static final float DISCARD_RADIUS_DP = 9f;
    private static final float DISCARD_TOUCH_RADIUS_DP = 22f;
    private static final float DISCARD_GLYPH_STROKE_DP = 1.6f;
    private static final float DISCARD_GLYPH_EXTENT_FRACTION = 0.42f;
    private static final float BORDER_STROKE_WIDTH_DP = 1.5f;
    private static final float BORDER_DASH_LENGTH_DP = 6f;
    private static final float BORDER_GAP_LENGTH_DP = 4f;
    private static final int STROKE_HEADER_FLOATS = 2;
    private static final int FLOATS_PER_SEGMENT = 6;

    enum DragMode { NONE, MOVE, RESIZE_TL, RESIZE_BL, RESIZE_BR }

    private final PDFView pdfView;
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discardFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discardGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path scratchPath = new Path();
    private final float handleRadius;
    private final float handleTouchRadius;
    private final float discardRadius;
    private final float discardTouchRadius;

    private boolean active;
    private int pageIndex = -1;
    private final RectF frameRect = new RectF();
    private final StampFrame frame = new StampFrame();
    private float[][] strokes;
    private int color = Color.BLACK;
    private float normalizedStrokeWidth;
    private float aspect = 1f;
    private Path normalizedPath;
    private DragMode dragMode = DragMode.NONE;
    private float lastDocX;
    private float lastDocY;

    StampPlacementManager(PDFView pdfView) {
        this.pdfView = pdfView;
        float density = pdfView.getResources().getDisplayMetrics().density;
        handleRadius = HANDLE_RADIUS_DP * density;
        handleTouchRadius = HANDLE_TOUCH_RADIUS_DP * density;
        discardRadius = DISCARD_RADIUS_DP * density;
        discardTouchRadius = DISCARD_TOUCH_RADIUS_DP * density;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(BORDER_STROKE_WIDTH_DP * density);
        borderPaint.setColor(ACCENT_COLOR);
        borderPaint.setPathEffect(new DashPathEffect(
                new float[]{BORDER_DASH_LENGTH_DP * density, BORDER_GAP_LENGTH_DP * density}, 0));
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(ACCENT_COLOR);
        discardFillPaint.setStyle(Paint.Style.FILL);
        discardFillPaint.setColor(ACCENT_COLOR);
        discardGlyphPaint.setStyle(Paint.Style.STROKE);
        discardGlyphPaint.setColor(Color.WHITE);
        discardGlyphPaint.setStrokeCap(Paint.Cap.ROUND);
        discardGlyphPaint.setStrokeWidth(DISCARD_GLYPH_STROKE_DP * density);
    }

    void start(int pageIndex, RectF rect, float[][] strokes, int color, float normalizedStrokeWidth) {
        if (rect == null || rect.width() <= 0 || strokes == null || strokes.length == 0) {
            return;
        }
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return;
        }
        RectF frame = pdfFile.userRectToFrame(pageIndex, rect.left, rect.bottom, rect.right, rect.top);
        if (frame.width() <= 0 || frame.height() <= 0) {
            return;
        }
        this.pageIndex = pageIndex;
        this.frame.left = frame.left;
        this.frame.top = frame.top;
        this.frame.right = frame.right;
        this.frame.bottom = frame.bottom;
        copyFrameRect();
        this.strokes = strokes;
        this.color = color;
        this.normalizedStrokeWidth = normalizedStrokeWidth;
        this.aspect = frame.height() / frame.width();
        this.normalizedPath = buildPath(strokes);
        this.dragMode = DragMode.NONE;
        this.active = true;
    }

    void cancel() {
        active = false;
        strokes = null;
        normalizedPath = null;
        dragMode = DragMode.NONE;
        pageIndex = -1;
    }

    boolean hasPending() {
        return active;
    }

    int getPendingPageIndex() {
        return pageIndex;
    }

    RectF getPendingRect() {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return new RectF();
        }
        return pdfFile.frameRectToUser(pageIndex, frameRect.left, frameRect.top,
                frameRect.right, frameRect.bottom);
    }

    float[][] getPendingStrokes() {
        return strokes;
    }

    int getPendingColor() {
        return color;
    }

    float getPendingNormalizedStrokeWidth() {
        return normalizedStrokeWidth;
    }

    boolean isDragging() {
        return dragMode != DragMode.NONE;
    }

    void recycle() {
        cancel();
    }

    void draw(Canvas canvas) {
        if (!active || normalizedPath == null) {
            return;
        }
        RectF docRect = docRectFor(pageIndex, frameRect);
        if (docRect == null || docRect.width() <= 0) {
            return;
        }
        drawStamp(canvas, normalizedPath, docRect, aspect, normalizedStrokeWidth, color);
        canvas.drawRect(docRect, borderPaint);
        canvas.drawCircle(docRect.left, docRect.top, handleRadius, handlePaint);
        canvas.drawCircle(docRect.left, docRect.bottom, handleRadius, handlePaint);
        canvas.drawCircle(docRect.right, docRect.bottom, handleRadius, handlePaint);
        drawDiscardBadge(canvas, docRect.right, docRect.top);
    }

    private void drawDiscardBadge(Canvas canvas, float cx, float cy) {
        canvas.drawCircle(cx, cy, discardRadius, discardFillPaint);
        float extent = discardRadius * DISCARD_GLYPH_EXTENT_FRACTION;
        canvas.drawLine(cx - extent, cy - extent, cx + extent, cy + extent, discardGlyphPaint);
        canvas.drawLine(cx - extent, cy + extent, cx + extent, cy - extent, discardGlyphPaint);
    }

    private void drawStamp(Canvas canvas, Path path, RectF docRect, float stampAspect,
                           float strokeWidth, int stampColor) {
        if (stampAspect <= 0) {
            return;
        }
        matrix.setScale(docRect.width(), docRect.height() / stampAspect);
        matrix.postTranslate(docRect.left, docRect.top);
        path.transform(matrix, scratchPath);
        strokePaint.setStrokeWidth(strokeWidth * docRect.width());
        strokePaint.setColor(pdfView.isNightModeEnabled() ? StampFrame.invertColor(stampColor) : stampColor);
        canvas.drawPath(scratchPath, strokePaint);
    }

    boolean handleTouch(MotionEvent event) {
        if (!active) {
            return false;
        }
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return false;
        }
        float docX = -pdfView.getCurrentXOffset() + event.getX();
        float docY = -pdfView.getCurrentYOffset() + event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return beginDrag(docX, docY);
            case MotionEvent.ACTION_MOVE:
                if (dragMode != DragMode.NONE) {
                    moveDrag(docX, docY);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragMode != DragMode.NONE) {
                    dragMode = DragMode.NONE;
                    return true;
                }
                return false;
            default:
                return dragMode != DragMode.NONE;
        }
    }

    private boolean beginDrag(float docX, float docY) {
        RectF docRect = docRectFor(pageIndex, frameRect);
        if (docRect == null || docRect.width() <= 0) {
            return false;
        }
        if (hitsDiscard(docX, docY, docRect.right, docRect.top)) {
            pdfView.notifyStampPlacementDiscard();
            return true;
        }
        if (hitsCorner(docX, docY, docRect.left, docRect.top)) {
            dragMode = DragMode.RESIZE_TL;
        } else if (hitsCorner(docX, docY, docRect.left, docRect.bottom)) {
            dragMode = DragMode.RESIZE_BL;
        } else if (hitsCorner(docX, docY, docRect.right, docRect.bottom)) {
            dragMode = DragMode.RESIZE_BR;
        } else if (docRect.contains(docX, docY)) {
            dragMode = DragMode.MOVE;
        } else {
            return false;
        }
        lastDocX = docX;
        lastDocY = docY;
        return true;
    }

    private boolean hitsCorner(float docX, float docY, float cornerX, float cornerY) {
        float dx = docX - cornerX;
        float dy = docY - cornerY;
        return dx * dx + dy * dy <= handleTouchRadius * handleTouchRadius;
    }

    private boolean hitsDiscard(float docX, float docY, float cornerX, float cornerY) {
        float dx = docX - cornerX;
        float dy = docY - cornerY;
        return dx * dx + dy * dy <= discardTouchRadius * discardTouchRadius;
    }

    private void moveDrag(float docX, float docY) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return;
        }
        RectF docRect = docRectFor(pageIndex, frameRect);
        if (docRect == null || docRect.width() <= 0 || docRect.height() <= 0) {
            return;
        }
        SizeF frameSize = pdfFile.getPageFrameSize(pageIndex);
        float frameWidth = frameSize.getWidth();
        float frameHeight = frameSize.getHeight();
        if (frameWidth <= 0 || frameHeight <= 0) {
            return;
        }
        float scaleX = frameRect.width() / docRect.width();
        float scaleY = frameRect.height() / docRect.height();
        float dxFrame = (docX - lastDocX) * scaleX;
        float dyFrame = (docY - lastDocY) * scaleY;

        if (dragMode == DragMode.MOVE) {
            frame.move(dxFrame, dyFrame, frameWidth, frameHeight);
        } else {
            frame.resize(dragMode, dxFrame, frameWidth, frameHeight, aspect);
        }
        copyFrameRect();
        lastDocX = docX;
        lastDocY = docY;
        pdfView.invalidate();
    }

    private void copyFrameRect() {
        frameRect.left = frame.left;
        frameRect.top = frame.top;
        frameRect.right = frame.right;
        frameRect.bottom = frame.bottom;
    }

    private RectF docRectFor(int page, RectF rect) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return null;
        }
        return pdfFile.frameRectToDocument(page, pdfView.getZoom(),
                rect.left, rect.top, rect.right, rect.bottom, false);
    }

    static Path buildPath(float[][] strokes) {
        Path path = new Path();
        for (float[] stroke : strokes) {
            if (stroke == null || stroke.length < STROKE_HEADER_FLOATS
                    || (stroke.length - STROKE_HEADER_FLOATS) % FLOATS_PER_SEGMENT != 0) {
                continue;
            }
            path.moveTo(stroke[0], stroke[1]);
            for (int k = STROKE_HEADER_FLOATS;
                    k + FLOATS_PER_SEGMENT - 1 < stroke.length; k += FLOATS_PER_SEGMENT) {
                path.cubicTo(stroke[k], stroke[k + 1], stroke[k + 2], stroke[k + 3],
                        stroke[k + 4], stroke[k + 5]);
            }
        }
        return path;
    }
}
