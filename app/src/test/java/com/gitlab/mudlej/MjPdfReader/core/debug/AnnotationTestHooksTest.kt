// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.debug

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnnotationTestHooksTest {
    private val hooks = AnnotationTestHooks
    private val writers = Executors.newFixedThreadPool(2)

    @Before fun resetHooks() = hooks.reset()

    @After fun stopWriters() {
        hooks.reset()
        writers.shutdownNow()
        assertTrue(writers.awaitTermination(2, TimeUnit.SECONDS))
    }

    @Test fun resetEndsABlockedWriteWithoutCopyingBytes() {
        val output = ByteArrayOutputStream()
        val fault = hooks.saveFault(hooks.FAULT_SLOW_WRITE)
        val result = writers.submit<Throwable?> {
            runCatching {
                hooks.awaitSaveWrite(fault)
                hooks.copySaveBytes(fault, ByteArrayInputStream(byteArrayOf(1, 2, 3)), output)
            }.exceptionOrNull()
        }
        awaitBlockedWriter()

        hooks.reset()

        assertTrue(result.get(2, TimeUnit.SECONDS) is IOException)
        assertEquals(0, output.size())
        assertFalse(hooks.isSlowWriteBlocked())
    }

    @Test fun earlyReleaseIsConsumedOnceAndResetEndsTheNextWait() {
        val fault = hooks.saveFault(hooks.FAULT_SLOW_WRITE)
        val output = ByteArrayOutputStream()
        hooks.releaseSlowWrite()
        val first = write(fault, output)
        assertNull(first.get(2, TimeUnit.SECONDS))
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())

        val secondOutput = ByteArrayOutputStream()
        val second = write(fault, secondOutput)
        awaitBlockedWriter()
        hooks.reset()
        assertTrue(second.get(2, TimeUnit.SECONDS) is IOException)
        assertEquals(0, secondOutput.size())
    }

    @Test fun delayedOldWriteCannotUseAReleaseFromAfterReset() {
        val oldFault = hooks.saveFault(hooks.FAULT_SLOW_WRITE)
        hooks.releaseSlowWrite()
        hooks.reset()
        hooks.releaseSlowWrite()

        val oldOutput = ByteArrayOutputStream()
        assertTrue(write(oldFault, oldOutput).get(2, TimeUnit.SECONDS) is IOException)
        assertEquals(0, oldOutput.size())

        val newOutput = ByteArrayOutputStream()
        val newFault = hooks.saveFault(hooks.FAULT_SLOW_WRITE)
        assertNull(write(newFault, newOutput).get(2, TimeUnit.SECONDS))
        assertArrayEquals(byteArrayOf(1, 2, 3), newOutput.toByteArray())
    }

    @Test fun resetEndsEveryWriterFromTheOldGeneration() {
        val fault = hooks.saveFault(hooks.FAULT_SLOW_WRITE)
        val outputs = List(2) { ByteArrayOutputStream() }
        val results = outputs.map { write(fault, it) }
        awaitBlockedWriter()
        hooks.reset()

        results.forEach { assertTrue(it.get(2, TimeUnit.SECONDS) is IOException) }
        outputs.forEach { assertEquals(0, it.size()) }
        assertFalse(hooks.isSlowWriteBlocked())
    }

    @Test fun interruptionFailsTheWriteAndPreservesTheInterruptFlag() {
        val thread = AtomicReference<Thread>()
        val fault = hooks.saveFault(hooks.FAULT_SLOW_WRITE)
        val output = ByteArrayOutputStream()
        val result = writers.submit<Pair<Throwable?, Boolean>> {
            thread.set(Thread.currentThread())
            val failure = runCatching {
                hooks.awaitSaveWrite(fault)
                hooks.copySaveBytes(fault, ByteArrayInputStream(byteArrayOf(1, 2, 3)), output)
            }.exceptionOrNull()
            failure to Thread.currentThread().isInterrupted
        }
        awaitBlockedWriter()
        thread.get().interrupt()

        val (failure, interrupted) = result.get(2, TimeUnit.SECONDS)
        assertTrue(failure is InterruptedIOException)
        assertTrue(interrupted)
        assertEquals(0, output.size())
        assertFalse(hooks.isSlowWriteBlocked())
    }

    @Test fun releaseRacingAnInterruptDoesNotStrandAnotherWriter() {
        repeat(50) {
            hooks.reset()
            val fault = hooks.saveFault(hooks.FAULT_SLOW_WRITE)
            val threads = List(2) { AtomicReference<Thread>() }
            val results = threads.map { thread ->
                writers.submit<Throwable?> {
                    thread.set(Thread.currentThread())
                    runCatching { hooks.awaitSaveWrite(fault) }.exceptionOrNull()
                }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (threads.any { it.get()?.state != Thread.State.WAITING }) {
                check(System.nanoTime() < deadline) { "Both writers must reach the gate" }
                Thread.yield()
            }

            hooks.releaseSlowWrite()
            threads.first().get().interrupt()

            val first = results.first().get(2, TimeUnit.SECONDS)
            if (first is InterruptedIOException) {
                assertNull(results.last().get(2, TimeUnit.SECONDS))
            } else {
                assertNull(first)
                hooks.reset()
                assertTrue(results.last().get(2, TimeUnit.SECONDS) is IOException)
            }
            assertFalse(hooks.isSlowWriteBlocked())
        }
    }

    @Test fun repeatedIdentityHoldsShareTheSameRelease() = runBlocking {
        withTimeout(2_000) {
            hooks.holdIdentityResolution()
            val first = async(start = CoroutineStart.UNDISPATCHED) { hooks.awaitIdentityRelease() }
            hooks.holdIdentityResolution()
            val second = async(start = CoroutineStart.UNDISPATCHED) { hooks.awaitIdentityRelease() }
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)

            hooks.releaseIdentityResolution()

            first.await()
            second.await()
            assertFalse(hooks.isIdentityResolutionHeld())
        }
    }

    @Test fun resetReleasesOldIdentityWaitersWithoutReleasingANewHold() = runBlocking {
        withTimeout(2_000) {
            hooks.holdIdentityResolution()
            val old = async(start = CoroutineStart.UNDISPATCHED) { hooks.awaitIdentityRelease() }
            hooks.reset()
            hooks.holdIdentityResolution()
            val current = async(start = CoroutineStart.UNDISPATCHED) { hooks.awaitIdentityRelease() }

            old.await()
            assertFalse(current.isCompleted)
            assertTrue(hooks.isIdentityResolutionHeld())
            hooks.releaseIdentityResolution()
            current.await()
        }
    }

    @Test fun cancellingOneIdentityWaiterDoesNotReleaseTheOthers() = runBlocking {
        withTimeout(2_000) {
            hooks.holdIdentityResolution()
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { hooks.awaitIdentityRelease() }
            val retained = async(start = CoroutineStart.UNDISPATCHED) { hooks.awaitIdentityRelease() }
            cancelled.cancelAndJoin()
            assertTrue(hooks.isIdentityResolutionHeld())
            assertFalse(retained.isCompleted)
            hooks.releaseIdentityResolution()
            retained.await()
        }
    }

    @Test fun normalAndFailureModesRetainTheirContracts() {
        val bytes = byteArrayOf(1, 2, 3)
        val output = ByteArrayOutputStream()
        hooks.copySaveBytes(hooks.saveFault(hooks.FAULT_NONE), ByteArrayInputStream(bytes), output)
        assertArrayEquals(bytes, output.toByteArray())
        assertThrows(IOException::class.java) {
            hooks.copySaveBytes(hooks.saveFault(hooks.FAULT_FAIL_WRITE), ByteArrayInputStream(bytes), output)
        }
        assertArrayEquals(bytes, output.toByteArray())
        assertTrue(hooks.verificationFails(hooks.saveFault(hooks.FAULT_FAIL_VERIFY)))
        assertFalse(hooks.verificationFails(hooks.saveFault(hooks.FAULT_NONE)))
        hooks.failNextHighlightWrite()
        assertTrue(hooks.consumeHighlightWriteFailure())
        assertFalse(hooks.consumeHighlightWriteFailure())
        hooks.failNextHighlightWrite()
        hooks.reset()
        assertFalse(hooks.consumeHighlightWriteFailure())
    }

    @Test fun corruptionStreamsBeforeReadingTheWholeInput() {
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (pos >= 64 * 1024) assertTrue("The copy must stream before reaching EOF", output.size() > 0)
                return super.read(buffer, offset, minOf(length, 97))
            }
        }

        hooks.copySaveBytes(hooks.saveFault(hooks.FAULT_CORRUPT_WRITE), input, output)

        assertArrayEquals(bytes.copyOf(bytes.size - 512), output.toByteArray())
    }

    @Test fun corruptionDropsExactlyTheFinal512BytesAcrossShortReadsAndBufferBoundaries() {
        val sizes = listOf(0, 1, 511, 512, 513, 8191, 8192, 8193, 8704, 17409)
        for (size in sizes) {
            val bytes = ByteArray(size) { (it % 251).toByte() }
            for (chunkSize in listOf(1, 97, 8192)) {
                val input = object : ByteArrayInputStream(bytes) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int) =
                        super.read(buffer, offset, minOf(length, chunkSize))
                }
                val output = ByteArrayOutputStream()

                hooks.copySaveBytes(hooks.saveFault(hooks.FAULT_CORRUPT_WRITE), input, output)

                assertArrayEquals("size=$size chunk=$chunkSize", bytes.take((size - 512).coerceAtLeast(0)).toByteArray(), output.toByteArray())
            }
        }
    }

    private fun write(fault: AnnotationTestHooks.SaveFault, output: ByteArrayOutputStream) =
        writers.submit<Throwable?> {
            runCatching {
                hooks.awaitSaveWrite(fault)
                hooks.copySaveBytes(fault, ByteArrayInputStream(byteArrayOf(1, 2, 3)), output)
            }.exceptionOrNull()
        }

    private fun awaitBlockedWriter() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!hooks.isSlowWriteBlocked()) {
            check(System.nanoTime() < deadline) { "Writer did not reach the slow-write gate" }
            Thread.yield()
        }
    }
}
