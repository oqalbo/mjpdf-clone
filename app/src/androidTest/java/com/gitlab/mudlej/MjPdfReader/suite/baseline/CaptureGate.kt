// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderStateSource
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderQuality

data class GateExpectation(
    val contentKey: String,
    val requireReaderIdle: Boolean = true,
    val pageIndex: Int? = null,
    val zoom: Float? = null,
    val zoomTolerance: Float = 0.001f,
    val distinctColourFloor: Int = DEFAULT_DISTINCT_COLOUR_FLOOR,
    val allowStaleFrame: Boolean = false,
) {

    companion object {
        const val DEFAULT_DISTINCT_COLOUR_FLOOR = 32

        fun forProducedImage(contentKey: String, distinctColourFloor: Int = DEFAULT_DISTINCT_COLOUR_FLOOR) =
            GateExpectation(
                contentKey = contentKey,
                requireReaderIdle = false,
                distinctColourFloor = distinctColourFloor,
            )

        fun forScreenOutsideTheReader(
            contentKey: String,
            distinctColourFloor: Int = DEFAULT_DISTINCT_COLOUR_FLOOR,
        ) = GateExpectation(
            contentKey = contentKey,
            requireReaderIdle = false,
            distinctColourFloor = distinctColourFloor,
        )
    }
}

object CaptureGate {

    private val framesSeen = mutableMapOf<Long, String>()

    fun reset() {
        synchronized(framesSeen) { framesSeen.clear() }
    }

    fun check(bitmap: Bitmap, expectation: GateExpectation) {
        val state = if (expectation.requireReaderIdle) requireReaderIdle(expectation) else null
        requireMatchingSetup(state, expectation)
        requireDrawnContent(bitmap, expectation)
        requireFreshFrame(bitmap, expectation)
    }

    private fun requireReaderIdle(expectation: GateExpectation): ReaderState {
        val state = ReaderStateSource.read()
            ?: throw BaselineGateFailure(
                "Gate 1 of 4 failed for ${expectation.contentKey}: the reader state hook produced nothing, so " +
                    "the capture cannot be proven to be of a finished render. Either no reader is resumed or " +
                    "the debug hook is absent from this build."
            )
        if (state.render != RenderActivity.IDLE || state.quality != RenderQuality.FULL) {
            throw BaselineGateFailure(
                "Gate 1 of 4 failed for ${expectation.contentKey}: rendering is not idle at full quality, the " +
                    "hook reads render=${state.render} quality=${state.quality}. Capturing now records preview " +
                    "bitmaps, and a golden recorded from a preview blesses a blurry image forever. " +
                    "The full hook value at capture time was: $state"
            )
        }
        return state
    }

    private fun requireMatchingSetup(state: ReaderState?, expectation: GateExpectation) {
        if (state == null) return
        val wantedPage = expectation.pageIndex
        if (wantedPage != null && state.page != wantedPage) {
            throw BaselineGateFailure(
                "Gate 2 of 4 failed for ${expectation.contentKey}: the test set up page index $wantedPage but " +
                    "the hook reports page index ${state.page}. This is a capture of the right looking wrong " +
                    "page. The full hook value at capture time was: $state"
            )
        }
        val wantedZoom = expectation.zoom
        if (wantedZoom != null) {
            val actual = state.zoom
            if (actual == null || Math.abs(actual - wantedZoom) > expectation.zoomTolerance) {
                throw BaselineGateFailure(
                    "Gate 2 of 4 failed for ${expectation.contentKey}: the test set up zoom $wantedZoom but " +
                        "the hook reports zoom $actual. The full hook value at capture time was: $state"
                )
            }
        }
    }

    private fun requireDrawnContent(bitmap: Bitmap, expectation: GateExpectation) {
        val distinct = PixelComparison.distinctColourCount(bitmap)
        if (distinct <= 1) {
            throw BaselineGateFailure(
                "Gate 3 of 4 failed for ${expectation.contentKey}: the capture is a single flat colour, which " +
                    "is what a page that failed to decode looks like. A flat capture matches a flat golden, so " +
                    "it is refused rather than compared."
            )
        }
        if (distinct < expectation.distinctColourFloor) {
            throw BaselineGateFailure(
                "Gate 3 of 4 failed for ${expectation.contentKey}: the capture holds $distinct distinct " +
                    "colours, below the floor of ${expectation.distinctColourFloor} recorded for it. Either " +
                    "the page did not draw, or the floor is wrong for this content and should be lowered " +
                    "deliberately rather than by accident."
            )
        }
    }

    private fun requireFreshFrame(bitmap: Bitmap, expectation: GateExpectation) {
        if (expectation.allowStaleFrame) return
        val hash = frameHash(bitmap)
        synchronized(framesSeen) {
            val previous = framesSeen[hash]
            if (previous != null && previous != expectation.contentKey) {
                throw BaselineGateFailure(
                    "Gate 4 of 4 failed for ${expectation.contentKey}: this capture is byte identical to an " +
                        "earlier capture in this process taken for \"$previous\", which expects different " +
                        "content. That is a stale frame, not a rendering match. Pass allowStaleFrame when two " +
                        "captures are meant to be identical, as VIS-55 does."
                )
            }
            framesSeen[hash] = expectation.contentKey
        }
    }

    fun frameHash(bitmap: Bitmap): Long {
        var hash = FNV_OFFSET_BASIS
        for (pixel in PixelComparison.pixelsOf(bitmap)) {
            hash = hash xor pixel.toLong()
            hash *= FNV_PRIME
        }
        return hash
    }

    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}
