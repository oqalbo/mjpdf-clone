// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.state

import android.graphics.PointF
import android.graphics.RectF
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderStateFields as Field

class ReaderStateFormatException(message: String) : AssertionError(message)

class ReaderState internal constructor(
    val raw: String,
    private val values: Map<String, String>,
) {

    val page: Int? get() = int(Field.PAGE)
    val pageCount: Int? get() = int(Field.PAGE_COUNT)
    val focusPage: Int? get() = int(Field.FOCUS_PAGE)
    val loadState: DocumentLoadState? get() = enum(Field.LOAD_STATE, DocumentLoadState::ofToken)
    val fileHash: String? get() = text(Field.FILE_HASH)
    val documentUri: String? get() = text(Field.DOC_URI)

    val viewerState: ViewerState? get() = enum(Field.STATE, ViewerState::ofToken)
    val render: RenderActivity? get() = enum(Field.RENDER, RenderActivity::ofToken)
    val quality: RenderQuality? get() = enum(Field.QUALITY, RenderQuality::ofToken)
    val renderPending: Int? get() = int(Field.RENDER_PENDING)
    val pageGeneration: Int? get() = int(Field.PAGE_GENERATION)

    val zoom: Float? get() = float(Field.ZOOM)
    val minZoom: Float? get() = float(Field.MIN_ZOOM)
    val midZoom: Float? get() = float(Field.MID_ZOOM)
    val maxZoom: Float? get() = float(Field.MAX_ZOOM)
    val offsetX: Float? get() = float(Field.OFFSET_X)
    val offsetY: Float? get() = float(Field.OFFSET_Y)
    val positionOffset: Float? get() = float(Field.POSITION_OFFSET)
    val pageOffsetRatio: Float? get() = float(Field.PAGE_OFFSET_RATIO)
    val animating: Boolean? get() = boolean(Field.ANIMATING)
    val flinging: Boolean? get() = boolean(Field.FLINGING)
    val snapAligned: Boolean? get() = boolean(Field.SNAP_ALIGNED)
    val snapDelta: Float? get() = float(Field.SNAP_DELTA)

    val swipeVertical: Boolean? get() = boolean(Field.SWIPE_VERTICAL)
    val pagesPerRow: Int? get() = int(Field.PAGES_PER_ROW)
    val firstPageAlone: Boolean? get() = boolean(Field.FIRST_PAGE_ALONE)
    val pageSnap: Boolean? get() = boolean(Field.PAGE_SNAP)
    val pageFling: Boolean? get() = boolean(Field.PAGE_FLING)
    val freeScroll: Boolean? get() = boolean(Field.FREE_SCROLL)
    val fitPolicy: PageFitPolicy? get() = enum(Field.FIT_POLICY, PageFitPolicy::ofToken)
    val readingDirectionRtl: Boolean? get() = boolean(Field.RTL)
    val spacing: Int? get() = int(Field.SPACING)
    val rowFirstPage: Int? get() = int(Field.ROW_FIRST)
    val rowLastPage: Int? get() = int(Field.ROW_LAST)
    val zoomLocked: Boolean? get() = boolean(Field.ZOOM_LOCKED)
    val horizontalSwipeLocked: Boolean? get() = boolean(Field.HORIZONTAL_SWIPE_LOCKED)

    val viewWidth: Int? get() = int(Field.VIEW_WIDTH)
    val viewHeight: Int? get() = int(Field.VIEW_HEIGHT)
    val documentLength: Float? get() = float(Field.DOCUMENT_LENGTH)
    val maxPageWidth: Float? get() = float(Field.MAX_PAGE_WIDTH)
    val maxPageHeight: Float? get() = float(Field.MAX_PAGE_HEIGHT)
    val pageWidth: Float? get() = float(Field.PAGE_WIDTH)
    val pageHeight: Float? get() = float(Field.PAGE_HEIGHT)
    val pageRect: RectF? get() = rect(Field.PAGE_RECT)
    val snapOffset: Float? get() = float(Field.SNAP_OFFSET)

    val annotationCount: Int? get() = int(Field.ANNOTATIONS)
    val autoScrolling: Boolean? get() = boolean(Field.AUTO_SCROLLING)
    val savingAnnotations: Boolean? get() = boolean(Field.SAVING)
    val hasUnsavedAnnotations: Boolean? get() = boolean(Field.DIRTY)
    val markerPages: List<Int>? get() = intList(Field.MARKER_PAGES)
    val dwellMs: Long? get() = long(Field.DWELL_MS)
    val highlightBoundsCalls: Int? get() = int(Field.HIGHLIGHT_BOUNDS_CALLS)
    val groupCacheMisses: Int? get() = int(Field.GROUP_CACHE_MISSES)

    val selectionPage: Int? get() = int(Field.SELECTION_PAGE)
    val selectionStart: Int? get() = int(Field.SELECTION_START)
    val selectionEnd: Int? get() = int(Field.SELECTION_END)
    val selectionCharCount: Int? get() = int(Field.SELECTION_CHAR_COUNT)
    val selectionRects: List<RectF>? get() = rectList(Field.SELECTION_RECTS)
    val selectionStartHandle: PointF? get() = point(Field.SELECTION_START_HANDLE)
    val selectionEndHandle: PointF? get() = point(Field.SELECTION_END_HANDLE)
    val selectionDrag: SelectionDrag? get() = enum(Field.SELECTION_DRAGGING, SelectionDrag::ofToken)

    val hasSelection: Boolean get() = values.containsKey(Field.SELECTION_PAGE)

    val isFullyRendered: Boolean
        get() = render == RenderActivity.IDLE && quality == RenderQuality.FULL

    val isSettled: Boolean
        get() = animating == false && flinging == false

    val presentKeys: Set<String> get() = values.keys

    val unknownKeys: List<String> get() = values.keys.filterNot { it in Field.known }

    fun rawValue(key: String): String? = values[key]

    fun require(key: String): String =
        values[key]?.takeIf { it.isNotEmpty() }
            ?: throw ReaderStateFormatException("Field $key is missing or empty in reader state: $raw")

    private fun text(key: String): String? = values[key]?.takeIf { it.isNotEmpty() }

    private fun int(key: String): Int? = text(key)?.let {
        it.toIntOrNull() ?: fail(key, it, "an integer")
    }

    private fun long(key: String): Long? = text(key)?.let {
        it.toLongOrNull() ?: fail(key, it, "a long")
    }

    private fun float(key: String): Float? = text(key)?.let {
        it.toFloatOrNull() ?: fail(key, it, "a float")
    }

    private fun boolean(key: String): Boolean? = when (text(key)) {
        null -> null
        "true" -> true
        "false" -> false
        else -> fail(key, values.getValue(key), "true or false")
    }

    private fun <T> enum(key: String, lookup: (String) -> T?): T? = text(key)?.let {
        lookup(it) ?: fail(key, it, "a known enum token")
    }

    private fun intList(key: String): List<Int>? = values[key]?.let { encoded ->
        if (encoded.isEmpty()) emptyList()
        else encoded.split(Field.NUMBER_LIST_SEPARATOR).map { part ->
            part.toIntOrNull() ?: fail(key, encoded, "a comma separated list of integers")
        }
    }

    private fun rect(key: String): RectF? = text(key)?.let { parseRect(key, it) }

    private fun rectList(key: String): List<RectF>? = values[key]?.let { encoded ->
        if (encoded.isEmpty()) emptyList()
        else encoded.split(Field.RECT_LIST_SEPARATOR).map { parseRect(key, it) }
    }

    private fun point(key: String): PointF? = text(key)?.let { encoded ->
        val parts = encoded.split(Field.NUMBER_LIST_SEPARATOR)
        if (parts.size != 2) fail(key, encoded, "a point as x,y")
        PointF(
            parts[0].toFloatOrNull() ?: fail(key, encoded, "a point as x,y"),
            parts[1].toFloatOrNull() ?: fail(key, encoded, "a point as x,y"),
        )
    }

    private fun parseRect(key: String, encoded: String): RectF {
        val parts = encoded.split(Field.NUMBER_LIST_SEPARATOR)
        if (parts.size != 4) fail(key, encoded, "a rectangle as left,top,right,bottom")
        val numbers = parts.map { it.toFloatOrNull() ?: fail(key, encoded, "a rectangle as left,top,right,bottom") }
        return RectF(numbers[0], numbers[1], numbers[2], numbers[3])
    }

    private fun fail(key: String, value: String, expected: String): Nothing =
        throw ReaderStateFormatException("Field $key holds \"$value\" which is not $expected, in reader state: $raw")

    override fun toString(): String = raw
}
