// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.metadata

import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataFailure
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.support.FakeFilesContext
import com.shockwave.pdfium.PdfPasswordException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfMetadataExtractorFailureTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun passwordAndMissingSourceFailuresTakePrecedenceOverInvalidPdf() {
        for ((error, failure) in listOf(
            PdfPasswordException("password") to ScannedPdfMetadataFailure.PASSWORD_REQUIRED,
            FileNotFoundException("missing") to ScannedPdfMetadataFailure.SOURCE_MISSING,
            IOException("unreadable") to ScannedPdfMetadataFailure.INVALID_PDF,
        )) {
            assertEquals(failure, PdfMetadataExtractor.pageFailure(error))
        }
    }

    @Test
    fun invalidArgumentsAndUnexpectedRuntimeFailuresRemainDistinct() {
        assertEquals(ScannedPdfMetadataFailure.INVALID_PDF, PdfMetadataExtractor.pageFailure(IllegalArgumentException()))
        assertEquals(ScannedPdfMetadataFailure.INTERNAL_ERROR, PdfMetadataExtractor.pageFailure(RuntimeException()))
        assertEquals(ScannedPdfMetadataFailure.ACCESS_DENIED, PdfMetadataExtractor.pageFailure(SecurityException()))
    }

    @Test
    fun fingerprintTimestampTruncatesMillisecondsButRejectsTheNextSecond() {
        val file = sourceFile()
        val fingerprint = SourceFingerprint(file.path, 3, 1_000)
        assertTrue(file.setLastModified(1_999))

        assertTrue(PdfMetadataExtractor.matches(file, fingerprint))

        assertTrue(file.setLastModified(2_000))
        assertFalse(PdfMetadataExtractor.matches(file, fingerprint))
    }

    @Test
    fun matchingTimestampCannotHideASizeChange() {
        val file = sourceFile()
        assertTrue(file.setLastModified(1_999))

        assertFalse(PdfMetadataExtractor.matches(file, SourceFingerprint(file.path, 2, 1_000)))
        assertFalse(PdfMetadataExtractor.matches(file, SourceFingerprint(file.path, 4, 1_000)))
        assertTrue(PdfMetadataExtractor.matches(file, SourceFingerprint(file.path, 3, 1_000)))
    }

    private fun sourceFile(): File {
        val context = FakeFilesContext(temporaryFolder.newFolder("files"), temporaryFolder.newFolder("cache"))
        return File(context.filesDir, "source.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }
}
