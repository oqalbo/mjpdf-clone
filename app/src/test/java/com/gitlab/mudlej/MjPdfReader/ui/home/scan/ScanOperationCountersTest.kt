// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanOperationCountersTest {
    @Test fun operationCountersRemainIndependentAndLoseNoConcurrentIncrements() {
        val signals = ScanDebugSignals
        val directories = signals.filesystemDirectories()
        val queries = signals.mediaStoreQueries()
        val probes = signals.pathProbes()
        val start = CyclicBarrier(4)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val calls = List(4) {
                executor.submit {
                    start.await(5, SECONDS)
                    repeat(50) {
                        signals.noteFilesystemDirectory()
                        repeat(2) { signals.noteMediaStoreQuery() }
                        repeat(3) { signals.notePathProbe() }
                    }
                }
            }
            calls.forEach { it.get(5, SECONDS) }
            assertEquals(directories + 200, signals.filesystemDirectories())
            assertEquals(queries + 400, signals.mediaStoreQueries())
            assertEquals(probes + 600, signals.pathProbes())
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, SECONDS))
        }
    }
}
