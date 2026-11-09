// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.fixtures

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StubSharerProviderUriTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var root: File
    private lateinit var provider: StubSharerProvider
    private val bytes = "published fixture".toByteArray()

    @Before
    fun setUp() {
        root = File(app.cacheDir, "provider-uri-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = root
        }
        provider = StubSharerProvider().apply {
            attachInfo(context, ProviderInfo().apply { authority = StubSharerProvider.AUTHORITY })
        }
        StubSharerProvider.clear()
    }

    @After
    fun tearDown() {
        try {
            if (::provider.isInitialized) StubSharerProvider.clear()
            if (::root.isInitialized) root.deleteRecursively()
        } finally {
            StubSharerProvider().attachInfo(
                app,
                ProviderInfo().apply { authority = StubSharerProvider.AUTHORITY },
            )
        }
    }

    @Test
    fun plainNamesKeepTheirUriAndReadOnlyBehavior() {
        val uri = StubSharerProvider.publish("report.pdf", bytes)
        assertEquals("content://mjpdf.test.sharer/report.pdf", uri.toString())
        assertServed(uri, "report.pdf")
        assertThrows(FileNotFoundException::class.java) { provider.openFile(uri, "w") }
    }

    @Test
    fun publishedPunctuationIsNotInterpretedAsUriSyntax() {
        for (name in listOf("report#1.pdf", "report?1.pdf", "report%20name.pdf", "report%2Fname.pdf",
            "report%name.pdf", "report name.pdf", "report+name.pdf")) {
            assertServed(StubSharerProvider.publish(name, bytes), name)
        }
    }

    @Test
    fun registeredAliasesRemainOneDecodedPathSegment() {
        val source = File(root, "source.pdf").apply { writeBytes(bytes) }
        for (name in listOf("folder/report.pdf", "/report.pdf", "folder/a?b#c%20.pdf")) {
            assertServed(StubSharerProvider.registerPath(name, source.absolutePath), name)
        }
    }

    @Test
    fun aFragmentLikeNameDoesNotSelectAnotherPublishedFile() {
        StubSharerProvider.publish("report", "wrong fixture".toByteArray())
        assertServed(StubSharerProvider.publish("report#1.pdf", bytes), "report#1.pdf")
    }

    private fun assertServed(uri: Uri, name: String) {
        ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri, "r")).use {
            assertArrayEquals(name, bytes, it.readBytes())
        }
        provider.query(uri, null, null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            assertEquals(bytes.size.toLong(), cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)))
            assertFalse(cursor.moveToNext())
        }
        assertEquals("content", uri.scheme)
        assertEquals(StubSharerProvider.AUTHORITY, uri.authority)
        assertEquals(listOf(name), uri.pathSegments)
        assertEquals(name, uri.lastPathSegment)
        assertNull(uri.query)
        assertNull(uri.fragment)
    }
}
