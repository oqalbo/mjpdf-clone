// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.metadata

import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataWorkerStateTest {
    private val fingerprint = SourceFingerprint("/test.pdf", 100, 1_000)
    private val state = MetadataWorkerState()

    @Test
    fun completionWinsAndDisarmsEvenAnAlreadyRunningTimeout() {
        val attempt = state.begin(fingerprint)

        assertTrue(state.complete(attempt))
        assertFalse(state.expire(attempt))
        assertFalse(state.complete(attempt))
        assertFalse(state.isIdle())
        assertTrue(state.acknowledge(fingerprint, continueWork = true))
        assertTrue(state.isIdle())
    }

    @Test
    fun timeoutWinsAndLateCompletionCannotPublishOrReviveWorker() {
        val attempt = state.begin(fingerprint)

        assertTrue(state.expire(attempt))
        assertFalse(state.complete(attempt))
        assertFalse(state.expire(attempt))
        assertFalse(state.acknowledge(fingerprint, continueWork = true))
        assertFalse(state.isIdle())
        assertThrows(IllegalStateException::class.java) { state.begin(fingerprint) }
    }

    @Test
    fun oldTimerCannotExpireTheNextAttemptEvenForTheSameFingerprint() {
        val previous = state.begin(fingerprint)
        assertTrue(state.complete(previous))
        assertTrue(state.acknowledge(fingerprint, continueWork = true))
        val next = state.begin(fingerprint)

        assertFalse(state.expire(previous))
        assertFalse(state.complete(previous))
        assertTrue(state.expire(next))
    }

    @Test
    fun shutdownDuringExtractionRejectsBothCompletionAndTimeout() {
        val attempt = state.begin(fingerprint)

        state.exit()

        assertFalse(state.complete(attempt))
        assertFalse(state.expire(attempt))
        assertFalse(state.isIdle())
        assertThrows(IllegalStateException::class.java) { state.begin(fingerprint) }
    }

    @Test
    fun shutdownWhileAwaitingAckCannotBeUndoneByContinue() {
        val attempt = state.begin(fingerprint)
        assertTrue(state.complete(attempt))

        state.exit()

        assertFalse(state.acknowledge(fingerprint, continueWork = true))
        assertFalse(state.expire(attempt))
        assertFalse(state.isIdle())
    }

    @Test
    fun stopAcknowledgementIsTerminal() {
        val attempt = state.begin(fingerprint)
        assertTrue(state.complete(attempt))

        assertTrue(state.acknowledge(fingerprint, continueWork = false))

        assertFalse(state.expire(attempt))
        assertFalse(state.isIdle())
        assertThrows(IllegalStateException::class.java) { state.begin(fingerprint) }
    }

    @Test
    fun overlappingRequestsAndWrongAcknowledgementsAreRejected() {
        val attempt = state.begin(fingerprint)
        assertThrows(IllegalStateException::class.java) { state.begin(fingerprint) }
        assertThrows(IllegalStateException::class.java) { state.acknowledge(fingerprint, continueWork = true) }
        assertTrue(state.complete(attempt))
        assertThrows(IllegalStateException::class.java) { state.begin(fingerprint) }
        assertThrows(IllegalStateException::class.java) {
            state.acknowledge(fingerprint.copy(size = 101), continueWork = true)
        }
        assertTrue(state.acknowledge(fingerprint, continueWork = true))
    }
}
