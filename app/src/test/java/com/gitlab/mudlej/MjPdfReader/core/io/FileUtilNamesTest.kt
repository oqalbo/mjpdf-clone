// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.support.FakeFilesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8

class FileUtilNamesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun unsafeFileNameCharactersBecomeUnderscores() {
        assertEquals("a_b_c_.pdf", ensurePdfFileName("a/b:c?.pdf"))
    }

    @Test
    fun suffixOnlyNameKeepsThePdfWordAsItsBaseName() {
        assertEquals("pdf.pdf", ensurePdfFileName(".pdf"))
    }

    @Test
    fun dotsAndWhitespaceUseTheSharedPdfFallback() {
        assertEquals("Shared PDF.pdf", ensurePdfFileName("  ..  "))
    }

    @Test
    fun existingPdfSuffixKeepsItsCase() {
        assertEquals("Report.PDF", ensurePdfFileName("Report.PDF"))
    }

    @Test
    fun missingPdfSuffixIsAppended() {
        assertEquals("report.pdf", ensurePdfFileName("report"))
    }

    @Test
    fun arabicNamesStopAtTheUtf8ByteLimit() {
        assertUtf8Prefix("ع".repeat(200), "ع".repeat(60), 120)
    }

    @Test
    fun emojiNamesStopBeforeASurrogatePairWouldExceedTheByteLimit() {
        assertUtf8Prefix("a" + "😀".repeat(200), "a" + "😀".repeat(29), 117)
    }

    @Test
    @Ignore("Pending ruling: literal pluses become spaces, expected C++ Primer.pdf")
    fun smbNamesKeepLiteralPluses() {
        assertEquals("C++ Primer.pdf", decodeNameFromUrl("smb://h/C++ Primer.pdf"))
    }

    @Test
    @Ignore("Pending ruling: literal percent is removed, expected 100%.pdf")
    fun smbNamesKeepLiteralPercentSigns() {
        assertEquals("100%.pdf", decodeNameFromUrl("smb://h/100%.pdf"))
    }

    @Test
    fun anyNameContainingSmbIsDecodedEvenForALocalFile() {
        val context = FakeFilesContext(temporaryFolder.newFolder("files"), temporaryFolder.newFolder("cache"))
        assertEquals("Smbat notes.pdf", getFileName(context, Uri.parse("file:///books/Smbat+notes.pdf")))
    }

    @Test
    fun doubleEncodedSpacesAreDecodedTwice() {
        assertEquals("My File.pdf", decodeNameFromUrl("smb://h/My%2520File.pdf"))
    }

    private fun assertUtf8Prefix(name: String, expected: String, expectedBytes: Int) {
        val actual = name.takeUtf8Bytes(120)
        val bytes = actual.toByteArray(UTF_8)
        val decoded = UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()

        assertEquals(expected, actual)
        assertEquals(expected, decoded)
        assertEquals(expectedBytes, bytes.size)
        assertTrue(bytes.size <= 120)
        assertEquals(expected + ".pdf", ensurePdfFileName(name))
    }
}
