// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ContentDestinationRetryTest {
    private val success = WriteResult.Success("hash", "identity", 100)

    @Test
    fun failedWriteWithMatchingDestinationSucceedsWithoutRetry() {
        val sink = ScriptedSink(listOf(Result.failure(IOException("close failed"))), listOf(true))

        assertEquals(success, retry(sink))
        assertEquals(listOf("write", "matches"), sink.calls)
    }

    @Test
    fun failedWriteWithMismatchingDestinationRetriesAndVerifies() {
        val sink = ScriptedSink(
            listOf(Result.failure(IOException("interrupted")), Result.success(true)),
            listOf(false, true),
        )

        assertEquals(success, retry(sink))
        assertEquals(listOf("write", "matches", "write", "matches"), sink.calls)
    }

    @Test
    fun nullOutputStreamFailsWithoutRetryOrVerification() {
        val sink = ScriptedSink(listOf(Result.success(false)), emptyList())

        assertEquals(WriteResult.Failure("rescue.pdf", false, true), retry(sink))
        assertEquals(listOf("write"), sink.calls)
    }

    @Test
    fun successfulWriteWithMismatchingDestinationIsUnconfirmed() {
        val sink = ScriptedSink(listOf(Result.success(true)), listOf(false))

        assertEquals(WriteResult.Failure("rescue.pdf", true, true), retry(sink))
        assertEquals(listOf("write", "matches"), sink.calls)
    }

    @Test
    fun secondWriteFailureStopsWithoutAnotherVerification() {
        val sink = ScriptedSink(
            listOf(Result.failure(IOException("first")), Result.failure(IOException("second"))),
            listOf(false),
        )

        assertEquals(WriteResult.Failure("rescue.pdf", false, true), retry(sink))
        assertEquals(listOf("write", "matches", "write"), sink.calls)
    }

    @Test
    fun successfulWriteWithMatchingDestinationSucceedsWithoutRetry() {
        val sink = ScriptedSink(listOf(Result.success(true)), listOf(true))

        assertEquals(success, retry(sink))
        assertEquals(listOf("write", "matches"), sink.calls)
    }

    private fun retry(sink: ContentSink): WriteResult = runBlocking {
        retryContentDestination(sink, success) { unconfirmed, incomplete ->
            WriteResult.Failure("rescue.pdf", unconfirmed, incomplete)
        }
    }

    private class ScriptedSink(writes: List<Result<Boolean>>, matches: List<Boolean>) : ContentSink {
        private val writes = writes.iterator()
        private val matches = matches.iterator()
        val calls = mutableListOf<String>()

        override fun write(): Result<Boolean> {
            calls += "write"
            return writes.next()
        }

        override suspend fun matches(): Boolean {
            calls += "matches"
            return matches.next()
        }
    }
}
