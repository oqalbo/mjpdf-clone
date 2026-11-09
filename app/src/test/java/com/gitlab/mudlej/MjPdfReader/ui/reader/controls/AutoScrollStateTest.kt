// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import org.junit.Assert.*
import org.junit.Test

class AutoScrollStateTest {
    @Test fun minimumSpeedCannotDecrease() {
        val state = state()
        state.setScrollBy(state.changedSpeed(false))
        assertEquals(1, state.speed)
        assertEquals(-0.1, state.scrollBy, 0.0)
    }

    @Test fun tenIncreasesDisplayElevenDespiteFloatingPointDrift() {
        val state = state()
        repeat(10) { state.setScrollBy(state.changedSpeed(true)) }
        assertEquals(11, state.speed)
        assertEquals(-1.1, state.scrollBy, 0.00000001)
    }

    @Test fun reversingDirectionKeepsTheSpeed() {
        val state = state()
        state.setScrollBy(-0.7)
        state.reverse()
        assertEquals(7, state.speed)
        assertEquals(0.7, state.scrollBy, 0.0)
    }

    @Test fun documentBoundariesStopOnlyTheMatchingDirection() {
        val state = state()
        assertFalse(state.shouldContinue(1f))
        assertTrue(state.shouldContinue(0f))
        assertTrue(state.shouldContinue(0.999f))
        state.reverse()
        assertFalse(state.shouldContinue(0f))
        assertTrue(state.shouldContinue(1f))
        assertTrue(state.shouldContinue(0.001f))
    }

    @Test fun secondPointerKeepsScrollingPausedUntilTheLastPointerLeaves() {
        val state = state()
        assertTrue(state.pointerDown(1, true))
        assertTrue(state.pausedByInteraction)
        assertTrue(state.pointerDown(2, true))
        assertFalse(state.pointerUp(1, true))
        assertTrue(state.pausedByInteraction)
        assertTrue(state.pointerUp(0, true))
        assertFalse(state.pausedByInteraction)
        assertFalse(state.pointerUp(0, true))
    }

    @Test fun stoppingDuringInteractionPreventsResuming() {
        val state = state()
        state.pointerDown(1, true)
        state.stop()
        assertFalse(state.pointerUp(0, false))
        assertFalse(state.pausedByInteraction)
        assertFalse(state.pointerDown(1, false))
    }

    @Test fun twoSecondFrameGapIsCappedAtOneTenthOfASecond() {
        var now = 1_000_000_000L
        val state = AutoScrollState { now }
        state.setScrollBy(-0.1)
        assertNull(state.frameDistance())
        now += 2_000_000_000L
        assertEquals(-0.5f, state.frameDistance()!!, 0.000001f)
        now += 20_000_000L
        assertEquals(-0.1f, state.frameDistance()!!, 0.000001f)
        state.resetFrame()
        now += 2_000_000_000L
        assertNull(state.frameDistance())
    }

    private fun state() = AutoScrollState { 1L }.apply { setScrollBy(-0.1) }
}
