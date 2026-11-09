// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureData

class SignatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = STROKE_WIDTH_DP * resources.displayMetrics.density
    }
    private val capturedStrokes = mutableListOf<MutableList<PointF>>()
    private val livePath = Path()
    private val scratchMatrix = Matrix()
    private val scratchPath = Path()
    private val decimationDistance = DECIMATION_DP * resources.displayMetrics.density
    private var loadedData: SignatureData? = null
    private var loadedPath: Path? = null

    var onInkChanged: (() -> Unit)? = null

    init {
        strokePaint.color = DEFAULT_INK_COLOR
    }

    fun setInkColor(color: Int) {
        strokePaint.color = color
        loadedData = loadedData?.copy(color = color)
        invalidate()
    }

    fun currentInkColor(): Int = strokePaint.color

    fun clear() {
        capturedStrokes.clear()
        livePath.reset()
        loadedData = null
        loadedPath = null
        invalidate()
        onInkChanged?.invoke()
    }

    fun hasInk(): Boolean = capturedStrokes.isNotEmpty() || loadedData != null

    fun setSignature(data: SignatureData) {
        capturedStrokes.clear()
        livePath.reset()
        loadedData = data
        loadedPath = buildNormalizedPath(data.strokes)
        strokePaint.color = data.color
        invalidate()
        onInkChanged?.invoke()
    }

    fun buildSignatureData(): SignatureData? {
        loadedData?.let { return it }
        if (capturedStrokes.isEmpty()) {
            return null
        }
        val cubicStrokes = capturedStrokes.mapNotNull { points -> toCubicStroke(points) }
        if (cubicStrokes.isEmpty()) {
            return null
        }
        val bounds = strokeBounds(cubicStrokes)
        if (bounds.width() < MIN_INK_EXTENT_PX) {
            return null
        }
        bounds.inset(-strokePaint.strokeWidth / 2, -strokePaint.strokeWidth / 2)
        val scale = 1f / bounds.width()
        val normalized = cubicStrokes.map { stroke ->
            FloatArray(stroke.size) { index ->
                if (index % 2 == 0) (stroke[index] - bounds.left) * scale
                else (stroke[index] - bounds.top) * scale
            }
        }
        return SignatureData(
            strokes = normalized,
            aspect = bounds.height() / bounds.width(),
            strokeWidth = strokePaint.strokeWidth * scale,
            color = strokePaint.color,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (loadedData != null) {
                    loadedData = null
                    loadedPath = null
                }
                capturedStrokes.add(mutableListOf(PointF(event.x, event.y)))
                parent?.requestDisallowInterceptTouchEvent(true)
                rebuildLivePath()
                invalidate()
                onInkChanged?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = capturedStrokes.lastOrNull() ?: return false
                val last = stroke.last()
                if (distance(last.x, last.y, event.x, event.y) >= decimationDistance) {
                    stroke.add(PointF(event.x, event.y))
                    rebuildLivePath()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val loaded = loadedData
        val path = loadedPath
        if (loaded != null && path != null && width > 0 && height > 0) {
            drawLoaded(canvas, loaded, path)
            return
        }
        canvas.drawPath(livePath, strokePaint)
    }

    private fun drawLoaded(canvas: Canvas, data: SignatureData, path: Path) {
        val padding = LOADED_PADDING_DP * resources.displayMetrics.density
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding
        if (availableWidth <= 0 || availableHeight <= 0 || data.aspect <= 0) {
            return
        }
        var drawWidth = availableWidth
        var drawHeight = drawWidth * data.aspect
        if (drawHeight > availableHeight) {
            drawHeight = availableHeight
            drawWidth = drawHeight / data.aspect
        }
        val left = (width - drawWidth) / 2
        val top = (height - drawHeight) / 2
        scratchMatrix.setScale(drawWidth, drawWidth)
        scratchMatrix.postTranslate(left, top)
        path.transform(scratchMatrix, scratchPath)
        val originalWidth = strokePaint.strokeWidth
        strokePaint.strokeWidth = data.strokeWidth * drawWidth
        canvas.drawPath(scratchPath, strokePaint)
        strokePaint.strokeWidth = originalWidth
    }

    private fun rebuildLivePath() {
        livePath.reset()
        for (points in capturedStrokes) {
            appendSmoothedStroke(livePath, points)
        }
    }

    private fun appendSmoothedStroke(path: Path, points: List<PointF>) {
        if (points.isEmpty()) {
            return
        }
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) {
            path.lineTo(points[0].x + DOT_EXTENT_PX, points[0].y)
            return
        }
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
            return
        }
        var midX = (points[0].x + points[1].x) / 2
        var midY = (points[0].y + points[1].y) / 2
        path.lineTo(midX, midY)
        for (i in 1 until points.size - 1) {
            val nextMidX = (points[i].x + points[i + 1].x) / 2
            val nextMidY = (points[i].y + points[i + 1].y) / 2
            path.quadTo(points[i].x, points[i].y, nextMidX, nextMidY)
            midX = nextMidX
            midY = nextMidY
        }
        path.lineTo(points.last().x, points.last().y)
    }

    private fun toCubicStroke(points: List<PointF>): FloatArray? {
        if (points.isEmpty()) {
            return null
        }
        if (points.size == 1) {
            val p = points[0]
            return floatArrayOf(
                p.x, p.y,
                p.x + DOT_EXTENT_PX, p.y,
                p.x + 2 * DOT_EXTENT_PX, p.y,
                p.x + 3 * DOT_EXTENT_PX, p.y,
            )
        }
        val segments = mutableListOf<Float>()
        segments.add(points[0].x)
        segments.add(points[0].y)
        if (points.size == 2) {
            addLineAsCubic(segments, points[0], points[1])
            return segments.toFloatArray()
        }
        var startX = points[0].x
        var startY = points[0].y
        val firstMidX = (points[0].x + points[1].x) / 2
        val firstMidY = (points[0].y + points[1].y) / 2
        addLineAsCubic(segments, startX, startY, firstMidX, firstMidY)
        startX = firstMidX
        startY = firstMidY
        for (i in 1 until points.size - 1) {
            val ctrlX = points[i].x
            val ctrlY = points[i].y
            val endX = (points[i].x + points[i + 1].x) / 2
            val endY = (points[i].y + points[i + 1].y) / 2
            segments.add(startX + QUAD_TO_CUBIC_RATIO * (ctrlX - startX))
            segments.add(startY + QUAD_TO_CUBIC_RATIO * (ctrlY - startY))
            segments.add(endX + QUAD_TO_CUBIC_RATIO * (ctrlX - endX))
            segments.add(endY + QUAD_TO_CUBIC_RATIO * (ctrlY - endY))
            segments.add(endX)
            segments.add(endY)
            startX = endX
            startY = endY
        }
        addLineAsCubic(segments, startX, startY, points.last().x, points.last().y)
        return segments.toFloatArray()
    }

    private fun addLineAsCubic(segments: MutableList<Float>, start: PointF, end: PointF) {
        addLineAsCubic(segments, start.x, start.y, end.x, end.y)
    }

    private fun addLineAsCubic(
        segments: MutableList<Float>, startX: Float, startY: Float, endX: Float, endY: Float,
    ) {
        segments.add(startX + ONE_THIRD * (endX - startX))
        segments.add(startY + ONE_THIRD * (endY - startY))
        segments.add(startX + QUAD_TO_CUBIC_RATIO * (endX - startX))
        segments.add(startY + QUAD_TO_CUBIC_RATIO * (endY - startY))
        segments.add(endX)
        segments.add(endY)
    }

    private fun strokeBounds(strokes: List<FloatArray>): RectF {
        val bounds = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (stroke in strokes) {
            var index = 0
            while (index + 1 < stroke.size) {
                bounds.left = minOf(bounds.left, stroke[index])
                bounds.right = maxOf(bounds.right, stroke[index])
                bounds.top = minOf(bounds.top, stroke[index + 1])
                bounds.bottom = maxOf(bounds.bottom, stroke[index + 1])
                index += FLOATS_PER_POINT
            }
        }
        return bounds
    }

    private fun buildNormalizedPath(strokes: List<FloatArray>): Path {
        val path = Path()
        for (stroke in strokes) {
            if (stroke.size < STROKE_HEADER_FLOATS ||
                (stroke.size - STROKE_HEADER_FLOATS) % FLOATS_PER_SEGMENT != 0
            ) {
                continue
            }
            path.moveTo(stroke[0], stroke[1])
            var k = STROKE_HEADER_FLOATS
            while (k + FLOATS_PER_SEGMENT - 1 < stroke.size) {
                path.cubicTo(stroke[k], stroke[k + 1], stroke[k + 2], stroke[k + 3],
                    stroke[k + 4], stroke[k + 5])
                k += FLOATS_PER_SEGMENT
            }
        }
        return path
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val STROKE_WIDTH_DP = 3f
        private const val DECIMATION_DP = 3f
        private const val LOADED_PADDING_DP = 12f
        private const val MIN_INK_EXTENT_PX = 1f
        private const val DOT_EXTENT_PX = 0.1f
        private const val QUAD_TO_CUBIC_RATIO = 2f / 3f
        private const val ONE_THIRD = 1f / 3f
        private const val STROKE_HEADER_FLOATS = 2
        private const val FLOATS_PER_SEGMENT = 6
        private const val FLOATS_PER_POINT = 2
        const val DEFAULT_INK_COLOR = 0xFF1A1A1A.toInt()
    }
}
