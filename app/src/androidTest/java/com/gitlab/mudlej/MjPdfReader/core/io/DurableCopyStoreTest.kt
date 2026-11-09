// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 29)
class DurableCopyStoreTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(app.cacheDir, "durable-copy-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(app) {
        override fun getFilesDir(): File = directory

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE) PackageManager.PERMISSION_DENIED
            else super.checkPermission(permission, pid, uid)
    }
    private val imports get() = File(directory, "imported")

    @After
    fun cleanUp() {
        StubSharerProvider.clear()
        directory.deleteRecursively()
    }

    @Test
    fun concurrentDifferentDocumentsKeepBothCopiesAndTheirBytes() {
        val firstBytes = "first document".toByteArray()
        val secondBytes = "different document".toByteArray()

        val (first, second) = overlappingCopies(firstBytes, secondBytes)

        assertNotEquals("different documents must have different durable URIs", first.uri, second.uri)
        assertArrayEquals(firstBytes, File(requireNotNull(first.uri.path)).readBytes())
        assertArrayEquals(secondBytes, File(requireNotNull(second.uri.path)).readBytes())
        assertEquals(2, imports.listFiles()!!.size)
        assertFalse(imports.listFiles()!!.any { it.name.endsWith(".part") })
    }

    @Test
    fun concurrentIdenticalDocumentsReuseTheCompletedCopy() {
        val bytes = "same document".toByteArray()

        val (first, second) = overlappingCopies(bytes, bytes)

        assertEquals(first.uri, second.uri)
        assertArrayEquals(bytes, File(requireNotNull(first.uri.path)).readBytes())
        assertEquals(1, imports.listFiles()!!.size)
        assertFalse(imports.listFiles()!!.any { it.name.endsWith(".part") })
    }

    @Test
    fun failedSourceLeavesNeitherPublishedFileNorPartialCopy() {
        val copy = DurableCopyStore.saveCopy(
            context, StubSharerProvider.uriFor("missing.pdf"), "report.pdf", null,
        )

        assertNull(copy)
        assertTrue(imports.listFiles().orEmpty().isEmpty())
    }

    private fun overlappingCopies(
        firstBytes: ByteArray,
        secondBytes: ByteArray,
    ): Pair<DurableCopyStore.SavedCopy, DurableCopyStore.SavedCopy> {
        val firstName = "durable-first.pdf"
        val secondName = "durable-second.pdf"
        val firstSource = StubSharerProvider.publish(firstName, firstBytes)
        val secondSource = StubSharerProvider.publish(secondName, secondBytes)
        val firstHash = requireNotNull(DocumentIdentity.of(StubSharerProvider.resolveFile(firstName)!!)).identity
        val secondHash = requireNotNull(DocumentIdentity.of(StubSharerProvider.resolveFile(secondName)!!)).identity
        val workers = Executors.newFixedThreadPool(2)
        StubSharerProvider.blockFirstOpenMatching(firstName) { true }
        try {
            val first = workers.submit<DurableCopyStore.SavedCopy?> {
                DurableCopyStore.saveCopy(context, firstSource, "report.pdf", firstHash)
            }
            assertTrue("the first copy must be blocked in source I/O", StubSharerProvider.awaitOpenBlocked(firstName, 5_000))
            val second = workers.submit<DurableCopyStore.SavedCopy?> {
                DurableCopyStore.saveCopy(context, secondSource, "report.pdf", secondHash)
            }.get(5, TimeUnit.SECONDS)
            assertFalse("a slow source must not block another import", first.isDone)
            StubSharerProvider.releaseBlockedOpen(firstName)
            return requireNotNull(first.get(5, TimeUnit.SECONDS)) to requireNotNull(second)
        } finally {
            StubSharerProvider.releaseBlockedOpen(firstName)
            workers.shutdownNow()
            assertTrue("copy workers must finish before cleanup", workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
