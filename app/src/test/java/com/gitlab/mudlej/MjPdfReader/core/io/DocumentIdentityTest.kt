// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.util.Random

class DocumentIdentityTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun oneByteHasTheSameIdentityThroughShortReads() {
        assertReadPathsAgree(1)
    }

    @Test
    fun justBelowOneWindowKeepsItsGoldenIdentity() {
        val identities = assertReadPathsAgree(PDF.HASH_SIZE - 1)
        assertEquals("h2043236393a0e0090320ffe5064b8148e", identities.identity)
        assertEquals("f3d278208fbb11d0fcff9bf5708974d3", identities.legacy)
    }

    @Test
    fun exactlyOneWindowHasTheSameIdentityThroughShortReads() {
        assertReadPathsAgree(PDF.HASH_SIZE)
    }

    @Test
    fun justAboveOneWindowKeepsItsGoldenIdentity() {
        val identities = assertReadPathsAgree(PDF.HASH_SIZE + 1)
        assertEquals("h2a6e9f096206ca9b6ce11c96d700eb495", identities.identity)
        assertEquals("79bc484181247d3299b1f17e44663d95", identities.legacy)
    }

    @Test
    fun exactlyTwoWindowsHasTheSameIdentityThroughShortReads() {
        assertReadPathsAgree(2 * PDF.HASH_SIZE)
    }

    @Test
    fun justAboveTwoWindowsKeepsItsGoldenIdentity() {
        val identities = assertReadPathsAgree(2 * PDF.HASH_SIZE + 1)
        assertEquals("h2975072e67f4edbf5da178ddb56d6a329", identities.identity)
        assertEquals("79bc484181247d3299b1f17e44663d95", identities.legacy)
    }

    @Test
    fun aWrappedTailHasTheSameIdentityThroughShortReads() {
        assertReadPathsAgree(3 * PDF.HASH_SIZE + 17)
    }

    @Test
    fun emptyFilesHaveNoIdentityThroughEitherPath() {
        val file = temporaryFolder.newFile()
        assertNull(DocumentIdentity.of(file))
        assertNull(DocumentIdentity.fromForwardRead(ByteArrayInputStream(byteArrayOf())))
        assertNull(computeHash(file))
    }

    @Test
    fun changesBetweenTheHeadAndTailDoNotChangeTheSampledIdentity() {
        val bytes = content(2 * PDF.HASH_SIZE + 17)
        val first = temporaryFolder.newFile().apply { writeBytes(bytes) }
        for (index in PDF.HASH_SIZE until PDF.HASH_SIZE + 17) {
            bytes[index] = (bytes[index].toInt() xor 0xff).toByte()
        }
        val second = temporaryFolder.newFile().apply { writeBytes(bytes) }

        val identities = DocumentIdentity.of(first)
        assertNotNull(identities)
        assertEquals(identities, DocumentIdentity.of(second))
    }

    @Test
    fun tailHashOfAShortFileIncludesTheWholeFile() {
        val file = temporaryFolder.newFile().apply { writeBytes(content(PDF.HASH_SIZE - 1)) }
        assertEquals("f3d278208fbb11d0fcff9bf5708974d3", computeTailHash(file))
    }

    private fun assertReadPathsAgree(length: Int): DocumentIdentities {
        val bytes = content(length)
        val file = temporaryFolder.newFile().apply { writeBytes(bytes) }
        val seekable = requireNotNull(DocumentIdentity.of(file))
        val stream = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, 7))
        }

        stream.use { assertEquals("length=$length", seekable, DocumentIdentity.fromForwardRead(it)) }
        assertEquals("length=$length", computeHash(file), seekable.legacy)
        return seekable
    }

    private fun content(length: Int): ByteArray =
        ByteArray(length).also { Random(20261103L).nextBytes(it) }
}
