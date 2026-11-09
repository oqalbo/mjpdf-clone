// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executors

class ScreenshotFileTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun twoCapturesWithTheSameTimestampPreserveBothImages() {
        val directory = temporary.newFolder()
        val name = "normal - 2026_09_30-04_53_33.jpg"
        val first = reserveScreenshotFile(directory, name)
        val firstImage = byteArrayOf(1, 2, 3)
        first.writeBytes(firstImage)

        val second = reserveScreenshotFile(directory, name)
        val secondImage = byteArrayOf(4, 5, 6)
        second.writeBytes(secondImage)

        assertNotEquals(first.canonicalPath, second.canonicalPath)
        assertArrayEquals(firstImage, first.readBytes())
        assertArrayEquals(secondImage, second.readBytes())
        assertEquals(2, directory.listFiles()!!.size)
    }

    @Test
    fun concurrentReservationsPreserveAnExistingImage() {
        val directory = temporary.newFolder()
        val name = "normal - 2026_09_30-04_53_33.jpg"
        val existing = java.io.File(directory, name)
        val original = byteArrayOf(7, 8, 9)
        existing.writeBytes(original)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val reservations = (1..8).map {
                workers.submit<java.io.File> { reserveScreenshotFile(directory, name) }
            }.map { it.get() }
            assertEquals(8, reservations.map { it.canonicalPath }.distinct().size)
            assertEquals(9, directory.listFiles()!!.size)
            assertArrayEquals(original, existing.readBytes())
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun aFailedWriteRemovesTheReservedImage() {
        val directory = temporary.newFolder()
        val image = reserveScreenshotFile(directory, "normal - 2026_09_30-04_53_33.jpg")

        assertThrows(IOException::class.java) {
            writeScreenshotFile(image) {
                it.write(byteArrayOf(1, 2))
                throw IOException("disk full")
            }
        }

        assertFalse(image.exists())
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test
    fun aSuccessfulWriteKeepsTheImageBytes() {
        val directory = temporary.newFolder()
        val image = reserveScreenshotFile(directory, "normal - 2026_09_30-04_53_33.jpg")
        val bytes = byteArrayOf(1, 2, 3, 4)

        writeScreenshotFile(image) { it.write(bytes) }

        assertArrayEquals(bytes, image.readBytes())
    }
}
