// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.state

object ReaderStateFields {

    const val PAIR_SEPARATOR = ";"
    const val KEY_VALUE_SEPARATOR = "="
    const val RECT_LIST_SEPARATOR = "|"
    const val NUMBER_LIST_SEPARATOR = ","

    const val ESCAPED_PERCENT = "%25"
    const val ESCAPED_PAIR_SEPARATOR = "%3B"
    const val ESCAPED_KEY_VALUE_SEPARATOR = "%3D"

    const val PAGE = "page"
    const val PAGE_COUNT = "pageCount"
    const val FOCUS_PAGE = "focusPage"
    const val LOAD_STATE = "loadState"
    const val FILE_HASH = "fileHash"
    const val DOC_URI = "docUri"

    const val STATE = "state"
    const val RENDER = "render"
    const val QUALITY = "quality"
    const val RENDER_PENDING = "renderPending"
    const val PAGE_GENERATION = "pageGen"

    const val ZOOM = "zoom"
    const val MIN_ZOOM = "minZoom"
    const val MID_ZOOM = "midZoom"
    const val MAX_ZOOM = "maxZoom"
    const val OFFSET_X = "offsetX"
    const val OFFSET_Y = "offsetY"
    const val POSITION_OFFSET = "positionOffset"
    const val PAGE_OFFSET_RATIO = "pageOffsetRatio"
    const val ANIMATING = "animating"
    const val FLINGING = "flinging"
    const val SNAP_ALIGNED = "snapAligned"
    const val SNAP_DELTA = "snapDelta"

    const val SWIPE_VERTICAL = "swipeVertical"
    const val PAGES_PER_ROW = "pagesPerRow"
    const val FIRST_PAGE_ALONE = "firstPageAlone"
    const val PAGE_SNAP = "pageSnap"
    const val PAGE_FLING = "pageFling"
    const val FREE_SCROLL = "freeScroll"
    const val FIT_POLICY = "fitPolicy"
    const val RTL = "rtl"
    const val SPACING = "spacing"
    const val ROW_FIRST = "rowFirst"
    const val ROW_LAST = "rowLast"
    const val ZOOM_LOCKED = "zoomLocked"
    const val HORIZONTAL_SWIPE_LOCKED = "hSwipeLocked"

    const val VIEW_WIDTH = "viewW"
    const val VIEW_HEIGHT = "viewH"
    const val DOCUMENT_LENGTH = "docLen"
    const val MAX_PAGE_WIDTH = "maxPageW"
    const val MAX_PAGE_HEIGHT = "maxPageH"
    const val PAGE_WIDTH = "pageW"
    const val PAGE_HEIGHT = "pageH"
    const val PAGE_RECT = "pageRect"
    const val SNAP_OFFSET = "snapOffset"

    const val ANNOTATIONS = "annotations"
    const val AUTO_SCROLLING = "autoScrolling"
    const val SAVING = "saving"
    const val DIRTY = "dirty"
    const val MARKER_PAGES = "markerPages"
    const val DWELL_MS = "dwellMs"
    const val HIGHLIGHT_BOUNDS_CALLS = "hlBoundsCalls"
    const val GROUP_CACHE_MISSES = "groupCacheMisses"

    const val SELECTION_PAGE = "selPage"
    const val SELECTION_START = "selStart"
    const val SELECTION_END = "selEnd"
    const val SELECTION_CHAR_COUNT = "selCharCount"
    const val SELECTION_RECTS = "selRects"
    const val SELECTION_START_HANDLE = "selStartHandle"
    const val SELECTION_END_HANDLE = "selEndHandle"
    const val SELECTION_DRAGGING = "selDragging"

    val documentAndLoad = listOf(PAGE, PAGE_COUNT, FOCUS_PAGE, LOAD_STATE, FILE_HASH, DOC_URI)

    val render = listOf(STATE, RENDER, QUALITY, RENDER_PENDING, PAGE_GENERATION)

    val positionAndMotion = listOf(
        ZOOM, MIN_ZOOM, MID_ZOOM, MAX_ZOOM, OFFSET_X, OFFSET_Y, POSITION_OFFSET,
        PAGE_OFFSET_RATIO, ANIMATING, FLINGING, SNAP_ALIGNED, SNAP_DELTA,
    )

    val readingLayout = listOf(
        SWIPE_VERTICAL, PAGES_PER_ROW, FIRST_PAGE_ALONE, PAGE_SNAP, PAGE_FLING, FREE_SCROLL,
        FIT_POLICY, RTL, SPACING, ROW_FIRST, ROW_LAST, ZOOM_LOCKED, HORIZONTAL_SWIPE_LOCKED,
    )

    val geometry = listOf(
        VIEW_WIDTH, VIEW_HEIGHT, DOCUMENT_LENGTH, MAX_PAGE_WIDTH, MAX_PAGE_HEIGHT,
        PAGE_WIDTH, PAGE_HEIGHT, PAGE_RECT, SNAP_OFFSET,
    )

    val readerAndContent = listOf(
        ANNOTATIONS, AUTO_SCROLLING, SAVING, DIRTY, MARKER_PAGES, DWELL_MS,
        HIGHLIGHT_BOUNDS_CALLS, GROUP_CACHE_MISSES,
    )

    val selection = listOf(
        SELECTION_PAGE, SELECTION_START, SELECTION_END, SELECTION_CHAR_COUNT,
        SELECTION_RECTS, SELECTION_START_HANDLE, SELECTION_END_HANDLE, SELECTION_DRAGGING,
    )

    val alwaysPresent: List<String> =
        documentAndLoad + render + positionAndMotion + readingLayout + geometry + readerAndContent

    val ordered: List<String> = alwaysPresent + selection

    val known: Set<String> = ordered.toSet()
}
