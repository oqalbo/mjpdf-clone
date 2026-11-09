// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageDiscoveryWalkTest {
    private val root = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "discovery-walk-${UUID.randomUUID()}",
    )

    @Before fun createRoot() {
        check(root.mkdirs())
    }

    @After fun removeRoot() {
        root.deleteRecursively()
    }

    @Test fun wholeDeviceWalkIncludesVisibleNomediaDirectories() {
        val visible = pdf("visible/book.pdf")
        val private = pdf("private/book.pdf")
        File(root, "private/.nomedia").createNewFile()

        assertEquals(setOf(visible, private), pdfs(listOf(root)).toSet())
        assertEquals(listOf(private), pdfs(listOf(File(root, "private"))))
    }

    @Test fun selectedRootIncludesBothItsOwnAndNestedNomediaDirectories() {
        val selected = pdf("selected/book.pdf")
        val nested = pdf("selected/nested/book.pdf")
        File(root, "selected/.nomedia").createNewFile()
        File(root, "selected/nested/.nomedia").createNewFile()

        assertEquals(setOf(selected, nested), pdfs(listOf(File(root, "selected"))).toSet())
    }

    @Test fun explicitlySelectedDescendantCanBeReachedBelowAnAncestorMarker() {
        val selected = pdf("private/selected/book.pdf")
        File(root, "private/.nomedia").createNewFile()

        assertEquals(listOf(selected), pdfs(listOf(File(root, "private/selected"))))
    }

    @Test fun parentExpansionExcludesRetainedDescendantsAndExactRoots() {
        pdf("books/old/book.pdf")
        val new = pdf("books/new/book.pdf")
        val old = File(root, "books/old")
        val files = StorageManager().readAllFiles(listOf(File(root, "books")), listOf(old)).toList()

        assertEquals(listOf(new), files.filter { it.isFile })
        assertFalse(files.any { it == old || it.path.startsWith("${old.path}/") })
        assertEquals(emptyList<File>(), StorageManager().readAllFiles(listOf(old), listOf(old)).toList())
        assertEquals(
            emptyList<File>(),
            StorageManager().readAllFiles(listOf(old), listOf(File(root, "books"))).toList(),
        )
    }

    @Test fun suppliedRootOrderIsNotSorted() {
        val z = pdf("z.pdf")
        val a = pdf("a.pdf")

        assertEquals(listOf(z, a), StorageManager().readAllFiles(listOf(z, a)).toList())
    }

    @Test fun everyVolumeExcludesOnlyItsTopLevelSystemDirectories() {
        val volumes = listOf(File(root, "primary"), File(root, "secondary"))
        val expected = volumes.flatMap { volume ->
            pdf("${volume.name}/Android/data/app/private.pdf")
            pdf("${volume.name}/data/private.pdf")
            pdf("${volume.name}/.hidden/private.pdf")
            val ordinary = listOf(
                pdf("${volume.name}/Books/Android/book.pdf"),
                pdf("${volume.name}/Books/data/book.pdf"),
                pdf("${volume.name}/Books/nomedia/book.pdf"),
            )
            File(volume, "Books/nomedia/.nomedia").createNewFile()
            ordinary
        }.toSet()

        for (roots in listOf(volumes, volumes.map { File(it, "Books") })) {
            val found = StorageManager().readAllFiles(roots, volumeRoots = volumes)
                .filter { it.isFile && it.extension == "pdf" }.toSet()
            assertEquals(expected, found)
        }
    }

    @Test fun directoryListingFailureIsNotASuccessfulEmptyWalk() {
        val unreadable = object : File(root, "unreadable") {
            override fun isDirectory() = true
            override fun isHidden() = false
            override fun listFiles(): Array<File>? = null
        }

        assertThrows(IOException::class.java) {
            StorageManager().readAllFiles(listOf(unreadable)).toList()
        }
    }

    @Test fun failedSubtreeDoesNotHideReadableSiblingsOrLaterRoots() {
        val sibling = pdf("parent/sibling/book.pdf")
        val later = pdf("later/book.pdf")
        for (denied in listOf(false, true)) {
            val securityError = SecurityException("directory listing denied")
            val unreadable = object : File(root, "parent/unreadable") {
                override fun isDirectory() = true
                override fun isHidden() = false
                override fun listFiles(): Array<File>? {
                    if (denied) throw securityError
                    return null
                }
            }
            val parent = object : File(root, "parent") {
                override fun listFiles(): Array<File> = arrayOf(unreadable, sibling.parentFile!!)
            }
            val failures = mutableListOf<Exception>()

            val files = StorageManager().readAllFiles(
                listOf(parent, later.parentFile!!), onError = { failures.add(it) },
            ).filter { it.extension == "pdf" }.toList()

            assertEquals(listOf(sibling, later), files)
            assertEquals(1, failures.size)
            if (denied) assertSame(securityError, failures.single())
            else assertTrue(failures.single() is IOException)
        }
    }

    @Test fun directoryInspectionFailureDoesNotAbortLaterRoots() {
        val failure = SecurityException("directory inspection denied")
        val unreadable = object : File(root, "unreadable") {
            override fun isDirectory(): Boolean = throw failure
        }
        val later = pdf("later/book.pdf")
        val failures = mutableListOf<Exception>()

        val files = StorageManager().readAllFiles(
            listOf(unreadable, later.parentFile!!), onError = { failures.add(it) },
        ).filter { it.extension == "pdf" }.toList()

        assertEquals(listOf(later), files)
        assertEquals(listOf(failure), failures)
    }

    @Test fun errorCallbackCancellationStopsTraversal() {
        val cancellation = CancellationException("cancel failed walk")
        val unreadable = object : File(root, "unreadable") {
            override fun isDirectory() = true
            override fun isHidden() = false
            override fun listFiles(): Array<File>? = null
        }
        val later = pdf("later/book.pdf")
        val visited = mutableListOf<File>()

        val thrown = assertThrows(CancellationException::class.java) {
            StorageManager().readAllFiles(
                listOf(unreadable, later.parentFile!!), onError = { throw cancellation },
            ).forEach { visited.add(it) }
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf(unreadable), visited)
    }

    private fun pdf(path: String): File = File(root, path).also {
        it.parentFile!!.mkdirs()
        it.writeText("pdf fixture")
    }

    private fun pdfs(roots: List<File>): List<File> =
        StorageManager().readAllFiles(roots)
            .filter { it.isFile && it.extension == "pdf" }.toList()
}
