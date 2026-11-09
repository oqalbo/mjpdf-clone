// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CoverCacheObservationTest {
    @Test
    fun observingAnUninitializedCacheDoesNotCreateItOrTrimDisk() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val directory = File(app.cacheDir, "covers").apply { mkdirs() }
        val cover = File(directory, "cold-observation-${UUID.randomUUID()}.jpg")
        val length = 65L * 1024 * 1024
        try {
            RandomAccessFile(cover, "rw").use { it.setLength(length) }
            assertNull("Observation must not initialize the cover cache", CoverCache.debugCacheStatsIfInitialized())
            assertTrue("Observation must not remove disk covers", cover.isFile)
            assertEquals(length, cover.length())
        } finally {
            cover.delete()
        }
    }
}
