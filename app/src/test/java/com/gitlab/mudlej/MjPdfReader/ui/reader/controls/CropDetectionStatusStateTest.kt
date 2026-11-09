// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropDetectionStatusStateTest {

    @Test
    fun progressBeyondTotalIsClampedToTotal() {
        val state = CropDetectionStatusState()
        state.process(CropDetectionStatusEvent.Started(runId = 1))

        assertEquals(
            CropDetectionStatusView.Progress(done = 4, total = 4),
            state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 5, total = 4)),
        )
        assertEquals(CropDetectionStatusView.Progress(done = 4, total = 4), state.view)
    }

    @Test
    fun nonPositiveTotalsAreIgnoredWithoutChangingVisibleProgress() {
        val state = CropDetectionStatusState()
        state.process(CropDetectionStatusEvent.Started(runId = 1))
        state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 2, total = 4))

        for (total in listOf(0, -1, Int.MIN_VALUE)) {
            assertNull(
                "total=$total",
                state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 3, total = total)),
            )
            assertEquals("total=$total", CropDetectionStatusView.Progress(done = 2, total = 4), state.view)
        }
    }

    @Test
    fun finishedBeforeQueuedFinalProgressKeepsCardHidden() {
        val state = CropDetectionStatusState()
        state.process(CropDetectionStatusEvent.Started(runId = 1))
        state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 5, total = 6))

        assertEquals(
            CropDetectionStatusView.Hidden,
            state.process(CropDetectionStatusEvent.Finished(runId = 1)),
        )
        assertNull(state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 6, total = 6)))
        assertEquals(CropDetectionStatusView.Hidden, state.view)
    }

    @Test
    fun oldRunProgressCannotAffectNewRun() {
        val state = CropDetectionStatusState()
        state.process(CropDetectionStatusEvent.Started(runId = 1))
        state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 1, total = 6))
        state.process(CropDetectionStatusEvent.Finished(runId = 1))

        assertEquals(
            CropDetectionStatusView.Hidden,
            state.process(CropDetectionStatusEvent.Started(runId = 2)),
        )
        assertNull(state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 6, total = 6)))
        assertEquals(CropDetectionStatusView.Hidden, state.view)
        assertEquals(
            CropDetectionStatusView.Progress(done = 1, total = 4),
            state.process(CropDetectionStatusEvent.Progress(runId = 2, done = 1, total = 4)),
        )
    }

    @Test
    fun progressIsIgnoredUntilItsRunStarts() {
        val state = CropDetectionStatusState()

        assertNull(state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 1, total = 6)))
        assertEquals(CropDetectionStatusView.Hidden, state.view)
    }

    @Test
    fun skippedRequestInvalidatesActiveRunAndItsQueuedProgress() {
        val state = CropDetectionStatusState()
        state.process(CropDetectionStatusEvent.Started(runId = 1))
        state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 5, total = 6))

        assertEquals(CropDetectionStatusView.Hidden, state.process(CropDetectionStatusEvent.Skipped))
        assertNull(state.process(CropDetectionStatusEvent.Progress(runId = 1, done = 6, total = 6)))
        assertEquals(CropDetectionStatusView.Hidden, state.view)
    }

    @Test
    fun staleFinishedCannotHideCurrentRun() {
        val state = CropDetectionStatusState()
        state.process(CropDetectionStatusEvent.Started(runId = 1))
        state.process(CropDetectionStatusEvent.Started(runId = 2))
        state.process(CropDetectionStatusEvent.Progress(runId = 2, done = 2, total = 4))

        assertNull(state.process(CropDetectionStatusEvent.Finished(runId = 1)))
        assertEquals(CropDetectionStatusView.Progress(done = 2, total = 4), state.view)
    }
}
