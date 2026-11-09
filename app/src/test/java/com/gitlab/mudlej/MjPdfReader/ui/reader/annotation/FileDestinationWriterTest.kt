// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import com.gitlab.mudlej.MjPdfReader.core.io.computeHash
import com.gitlab.mudlej.MjPdfReader.core.io.computeTailHash
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream

class FileDestinationWriterTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var target: File
    private lateinit var source: File
    private val original = "original document".toByteArray()
    private val annotated = "%PDF-1.7 annotated document".toByteArray()

    @Before
    fun prepareFiles() {
        target = temporary.newFile("document.pdf").apply { writeBytes(original) }
        source = temporary.newFile("annotations.pdf").apply { writeBytes(annotated) }
    }

    @Test
    fun hashMismatchPreservesTheOriginalAndRemovesTheSibling() {
        val corrupted = annotated.clone().apply { this[0] = 0 }

        assertFalse(write { it.write(corrupted) })

        assertOriginalPreserved()
    }

    @Test
    fun tailMismatchPreservesTheOriginalAndRemovesTheSibling() {
        val bytes = ByteArray(PDF.HASH_SIZE + 16) { 7 }
        source.writeBytes(bytes)
        bytes[bytes.lastIndex] = 8

        assertFalse(write { it.write(bytes) })

        assertOriginalPreserved()
    }

    @Test
    fun sizeMismatchPreservesTheOriginalAndRemovesTheSibling() {
        assertFalse(write { it.write(annotated.copyOf(annotated.size - 1)) })

        assertOriginalPreserved()
    }

    @Test
    fun renameFailurePreservesTheOriginalAndRemovesTheSibling() {
        var renameCalls = 0
        val writer = FileDestinationWriter(target) { sibling, destination ->
            assertEquals(File(target.parentFile, "document.pdf.saving"), sibling)
            assertEquals(target, destination)
            assertArrayEquals(annotated, sibling.readBytes())
            renameCalls++
            false
        }

        assertFalse(write(writer) { it.write(annotated) })

        assertEquals(1, renameCalls)
        assertOriginalPreserved()
    }

    @Test
    fun successReplacesTheOriginalAndRemovesTheSibling() {
        assertTrue(write { it.write(annotated) })

        assertArrayEquals(annotated, target.readBytes())
        assertFalse(File(target.parentFile, "document.pdf.saving").exists())
    }

    @Test
    fun throwingWritePreservesTheOriginalAndRemovesThePartialSibling() {
        assertFalse(write {
            it.write(annotated, 0, 5)
            throw IOException("disk full")
        })

        assertOriginalPreserved()
    }

    @Test
    fun forcedVerificationFailurePreservesTheOriginalAndRemovesTheSibling() {
        assertFalse(FileDestinationWriter(target).write(
            computeHash(source)!!, computeTailHash(source)!!, source.length(),
            verificationFails = { true },
        ) { it.write(annotated) })

        assertOriginalPreserved()
    }

    private fun write(
        writer: FileDestinationWriter = FileDestinationWriter(target),
        bytes: (OutputStream) -> Unit,
    ): Boolean = writer.write(computeHash(source)!!, computeTailHash(source)!!, source.length(), writeBytes = bytes)

    private fun assertOriginalPreserved() {
        assertArrayEquals(original, target.readBytes())
        assertFalse(File(target.parentFile, "document.pdf.saving").exists())
    }
}
