// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState

object ViewportBounds {

    fun scrollAxisContent(state: ReaderState): Float = state.documentLength!!

    fun crossAxisContent(state: ReaderState): Float =
        if (state.swipeVertical == true) state.maxPageWidth!! * state.zoom!!
        else state.maxPageHeight!! * state.zoom!!

    fun scrollAxisViewport(state: ReaderState): Float =
        if (state.swipeVertical == true) state.viewHeight!!.toFloat() else state.viewWidth!!.toFloat()

    fun crossAxisViewport(state: ReaderState): Float =
        if (state.swipeVertical == true) state.viewWidth!!.toFloat() else state.viewHeight!!.toFloat()

    fun scrollAxisOffset(state: ReaderState): Float =
        if (state.swipeVertical == true) state.offsetY!! else state.offsetX!!

    fun crossAxisOffset(state: ReaderState): Float =
        if (state.swipeVertical == true) state.offsetX!! else state.offsetY!!

    fun scrollAxisLowerBound(state: ReaderState): Float =
        scrollAxisViewport(state) - scrollAxisContent(state)

    fun crossAxisLowerBound(state: ReaderState): Float =
        crossAxisViewport(state) - crossAxisContent(state)

    fun scrollAxisCentred(state: ReaderState): Boolean =
        scrollAxisContent(state) < scrollAxisViewport(state)

    fun crossAxisCentred(state: ReaderState): Boolean =
        crossAxisContent(state) < crossAxisViewport(state)

    fun expectedScrollAxisOffset(state: ReaderState, wanted: Float): Float = when {
        scrollAxisCentred(state) -> scrollAxisLowerBound(state) / 2f
        wanted > 0f -> 0f
        wanted < scrollAxisLowerBound(state) -> scrollAxisLowerBound(state)
        else -> wanted
    }

    fun expectedCrossAxisOffset(state: ReaderState, wanted: Float): Float = when {
        crossAxisCentred(state) -> crossAxisLowerBound(state) / 2f
        wanted > 0f -> 0f
        wanted < crossAxisLowerBound(state) -> crossAxisLowerBound(state)
        else -> wanted
    }

    fun offsetXLowerBound(state: ReaderState): Float =
        if (state.swipeVertical == true) crossAxisLowerBound(state) else scrollAxisLowerBound(state)

    fun offsetYLowerBound(state: ReaderState): Float =
        if (state.swipeVertical == true) scrollAxisLowerBound(state) else crossAxisLowerBound(state)
}
