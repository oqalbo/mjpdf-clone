// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.support.FakeFilesContext
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnlineDocumentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private lateinit var context: FakeFilesContext

    @Before
    fun createContext() {
        context = FakeFilesContext(temporaryFolder.newFolder("files"), temporaryFolder.newFolder("cache"))
    }

    @Test
    fun exactlyTheSizeLimitIsStoredWithAllBytes() {
        val bytes = pdfBytes(1024)
        val progress = mutableListOf<Long>()
        val result = OnlineDocumentStore.write(context, "https://example.com/exact.pdf", false,
            bytes.inputStream(), 1024, progress::add) as OnlineDocumentStore.WriteResult.Stored

        assertArrayEquals(bytes, result.file.readBytes())
        assertEquals(listOf(1024L), progress)
        assertEquals(result.file, OnlineDocumentStore.fileFor(context, "https://example.com/exact.pdf"))
        assertNoPartialFiles()
    }

    @Test
    fun oneByteBeyondTheLimitIsRejectedAndRemovesThePartialFile() {
        val result = OnlineDocumentStore.write(context, "https://example.com/large.pdf", false,
            pdfBytes(8193).inputStream(), 8192)

        assertSame(OnlineDocumentStore.WriteResult.TooLarge, result)
        assertNull(OnlineDocumentStore.fileFor(context, "https://example.com/large.pdf"))
        assertNoPartialFiles()
    }

    @Test
    fun anEmptyStreamIsRejectedAsNotPdf() {
        val result = OnlineDocumentStore.write(context, "empty", false, byteArrayOf().inputStream(), 1024)

        assertSame(OnlineDocumentStore.WriteResult.NotPdf, result)
        assertNull(OnlineDocumentStore.fileFor(context, "empty"))
        assertNoPartialFiles()
    }

    @Test
    fun aPdfHeaderAfterFiveHundredJunkBytesIsAccepted() {
        val bytes = pdfBytes(1500, 500)
        val result = OnlineDocumentStore.write(context, "prefixed", false, bytes.inputStream(), 1500)
            as OnlineDocumentStore.WriteResult.Stored

        assertArrayEquals(bytes, result.file.readBytes())
        assertNoPartialFiles()
    }

    @Test
    fun aPdfHeaderEndingAtTheHeadWindowBoundaryIsAccepted() {
        val bytes = pdfBytes(1100, 1019)
        val result = OnlineDocumentStore.write(context, "boundary", false, bytes.inputStream(), 1100)
            as OnlineDocumentStore.WriteResult.Stored

        assertArrayEquals(bytes, result.file.readBytes())
    }

    @Test
    fun aPdfHeaderStraddlingTheHeadWindowBoundaryIsRejected() {
        val result = OnlineDocumentStore.write(context, "straddling", false,
            pdfBytes(1100, 1020).inputStream(), 1100)

        assertSame(OnlineDocumentStore.WriteResult.NotPdf, result)
        assertNull(OnlineDocumentStore.fileFor(context, "straddling"))
        assertNoPartialFiles()
    }

    @Test
    fun aLimitBelowTheHeadWindowRejectsJunkBeforeReadingAgain() {
        val input = object : ByteArrayInputStream(ByteArray(33) { 'x'.code.toByte() }) {
            var reads = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++
                return super.read(buffer, offset, minOf(length, 32))
            }
        }
        val progress = mutableListOf<Long>()
        val result = OnlineDocumentStore.write(context, "small-junk", false, input, 32, progress::add)

        assertSame(OnlineDocumentStore.WriteResult.NotPdf, result)
        assertEquals(1, input.reads)
        assertEquals(emptyList<Long>(), progress)
        assertNoPartialFiles()
    }

    @Test
    fun aPdfSmallerThanTheHeadWindowIsStoredAtTheSizeLimit() {
        val bytes = pdfBytes(32)
        val result = OnlineDocumentStore.write(context, "small-pdf", false, bytes.inputStream(), 32)
            as OnlineDocumentStore.WriteResult.Stored

        assertArrayEquals(bytes, result.file.readBytes())
        assertNoPartialFiles()
    }

    @Test
    fun theNextWritePrunesAnOldFileAndKeepsARecentFile() {
        val directory = File(context.cacheDir, "online-pdf").apply { mkdirs() }
        val old = File(directory, "old.pdf").apply { writeText("%PDF-old") }
        val recent = File(directory, "recent.pdf").apply { writeText("%PDF-recent") }
        assertTrue(old.setLastModified(1L))
        assertTrue(recent.setLastModified(4102444800000L))

        val result = OnlineDocumentStore.write(context, "next", false, pdfBytes(32).inputStream(), 32)
            as OnlineDocumentStore.WriteResult.Stored

        assertFalse(old.exists())
        assertEquals("%PDF-recent", recent.readText())
        assertEquals(32L, result.file.length())
    }

    @Test
    fun lookupFindsAFileWrittenInIncognito() {
        val bytes = pdfBytes(32)
        val result = OnlineDocumentStore.write(context, "private", true, bytes.inputStream(), 32)
            as OnlineDocumentStore.WriteResult.Stored

        assertEquals("online-pdf-incognito", result.file.parentFile!!.name)
        assertEquals(result.file, OnlineDocumentStore.fileFor(context, "private"))
        assertArrayEquals(bytes, OnlineDocumentStore.fileFor(context, "private")!!.readBytes())
    }

    private fun pdfBytes(size: Int, headerOffset: Int = 0): ByteArray =
        ByteArray(size) { 'x'.code.toByte() }.apply { "%PDF-".toByteArray().copyInto(this, headerOffset) }

    private fun assertNoPartialFiles() {
        assertEquals(emptyList<File>(), context.cacheDir.walkTopDown().filter { it.extension == "part" }.toList())
    }
}
