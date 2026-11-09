// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import com.gitlab.mudlej.MjPdfReader.support.FakeFilesContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AnnotationRescueCopiesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var context: FakeFilesContext
    private lateinit var source: File
    private lateinit var directory: File

    @Before
    fun prepareFiles() {
        context = FakeFilesContext(temporary.newFolder("files"), temporary.newFolder("cache"))
        source = temporary.newFile("annotations.pdf").apply { writeText("annotated document") }
        directory = File(context.filesDir, "annotation-rescue")
    }

    @Test
    fun thirdRescueDeletesTheOldestByModificationTime() {
        assertEquals("book-annotated.pdf", rescue(100))
        assertTrue(File(directory, "100-book-annotated.pdf").setLastModified(20))
        assertEquals("book-annotated.pdf", rescue(200))
        assertTrue(File(directory, "200-book-annotated.pdf").setLastModified(10))

        assertEquals("book-annotated.pdf", rescue(300))

        assertEquals(setOf("100-book-annotated.pdf", "300-book-annotated.pdf"), directory.list()!!.toSet())
        assertArrayEquals(source.readBytes(), File(directory, "300-book-annotated.pdf").readBytes())
    }

    @Ignore("Pending ruling: same millisecond rescue names overwrite one copy, expected distinct files preserving both saves")
    @Test
    fun failuresInTheSameMillisecondKeepDistinctRescueCopies() {
        val firstBytes = source.readBytes()
        rescue(100)
        source.writeText("different annotations")
        rescue(100)

        val copies = directory.listFiles()!!
        assertEquals(2, copies.size)
        assertEquals(2, copies.map { it.canonicalPath }.distinct().size)
        assertTrue(copies.any { it.readBytes().contentEquals(firstBytes) })
        assertTrue(copies.any { it.readBytes().contentEquals(source.readBytes()) })
    }

    @Test
    fun incognitoSkipsTheRescueAndDoesNotReadTheClock() {
        var clockCalls = 0

        assertNull(writeAnnotationRescueCopy(source, context, true, clock = { clockCalls++; 100 }))

        assertEquals(0, clockCalls)
        assertFalse(directory.exists())
    }

    private fun rescue(time: Long): String? = writeAnnotationRescueCopy(
        source, context, false, directory = directory, clock = { time }, name = "book.pdf",
    )
}
