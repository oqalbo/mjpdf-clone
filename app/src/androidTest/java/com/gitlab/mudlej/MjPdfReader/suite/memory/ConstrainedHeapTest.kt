// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.github.barteksc.pdfviewer.PreviewDecodeAssertions
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.resilience.PreviewCache
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class ConstrainedHeapTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("MEM-39")
    fun aConstrainedHeapOnAScannedBookDegradesInsteadOfCrashing() {
        SettingsSeeder.seed(Preferences.highQualityKey, true)
        val fixture = MemoryFixtures.file(MemoryFixtures.SCANNED_LONG)
        val since = CrashGate.timestampNow()
        val logSince = AppLogcat.since()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()

            var ballast: ByteArray? = Heap.allocateBallastLeavingFree(FREE_FRACTION_MEM39)
            assertTrue(
                "a ballast of at least one megabyte must be held, otherwise the constrained path this test " +
                    "exists to drive was never reached",
                (ballast?.size ?: 0) > 0,
            )
            try {
                repeat(PAGES_TO_FLING) { step ->
                    TestEnvironment.onMainThread { MemoryReader.pdfView().jumpTo(step, false) }
                    TestEnvironment.uiDevice.waitForIdle()
                }
                Log.i(
                    LOG_TAG,
                    "MEM-39 held a ballast of ${Heap.megabytes((ballast?.size ?: 0).toLong())} MB across " +
                        "$PAGES_TO_FLING pages",
                )
            } finally {
                ballast = null
            }
            assertEquals("the ballast must have been released before the settle", null, ballast)
            Heap.settledJavaHeap()

            MemoryReader.jumpToPage(reader, LAST_FLUNG_PAGE, Await.VERY_LONG_TIMEOUT_MS)
            val settled = reader.awaitRenderIdle(Await.VERY_LONG_TIMEOUT_MS)
            assertEquals(
                "once the ballast is gone the reader must reach the page it was driven to",
                LAST_FLUNG_PAGE as Int?,
                settled.page,
            )
            assertEquals(
                "and it must finish rendering it rather than stay degraded",
                RenderActivity.IDLE,
                settled.render,
            )

            assertEquals(
                "the reader must still hold exactly one document handle after the pressure",
                1,
                FileDescriptors.countFor(fixture),
            )

            val failures = allocationFailureCount(logSince)
            Log.i(LOG_TAG, "MEM-39 saw $failures allocation failure lines under pressure")
            if (failures > 0) {
                val tombstones = TOMBSTONE_TOKENS.sumOf {
                    AppLogcat.appLinesSince(logSince).count { line -> line.contains(it) }
                }
                assertEquals(
                    "the JNI null checks added in 8118460 mean a failed scratch buffer or bitmap " +
                        "allocation returns rather than dereferencing null, so $failures allocation " +
                        "failures must produce no native tombstone",
                    0,
                    tombstones,
                )
            }
            CrashGate.assertNoFatalSince(since)
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("MEM-40")
    @TestDuration(DurationCategory.NORMAL)
    fun aFailedPreviewDecodeNeverOrphansTheReuseBuffer() {
        val fixture = MemoryFixtures.file(MemoryFixtures.NORMAL)
        val lastPage = MemoryFixtures.NORMAL_PAGES - 1

        var previewDirectory: File? = null
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            for (page in 0..lastPage) {
                MemoryReader.jumpToPage(reader, page)
                reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            }
            previewDirectory = PreviewCache.directoryForOpenDocument()
            PreviewCache.awaitEntryFor(previewDirectory!!, lastPage)
            MemoryReader.closeReaderAndAwaitGone(reader)
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        val directory = previewDirectory
            ?: throw AssertionError("The warm up wrote no preview cache directory, so nothing can be corrupted")
        val entries = directory.listFiles().orEmpty().filter { it.isFile && it.length() > 0 }.sortedBy { it.name }
        assertTrue(
            "expected at least $MINIMUM_CORRUPTIONS nonempty previews in ${directory.absolutePath}; " +
                "found ${entries.size}",
            entries.size >= MINIMUM_CORRUPTIONS,
        )
        val corrupted = entries.filter { previewPage(it) > 0 }.take(CORRUPTIONS)
        val corruptedPages = corrupted.map(::previewPage)
        val previewDimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(corrupted.first().absolutePath, previewDimensions)
        assertTrue("the warm cache supplies real preview dimensions", previewDimensions.outWidth > 0 && previewDimensions.outHeight > 0)
        val failedDecodeBytes = ByteArray(CORRUPT_PREVIEW_BYTES) { CORRUPT_PREVIEW_BYTE }
        val decoded = BitmapFactory.decodeByteArray(
            failedDecodeBytes,
            0,
            failedDecodeBytes.size,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 },
        )
        assertNull(
            "the bytes injected into every preview must deterministically fail the same BitmapFactory " +
                "fallback decode used by AndroidPreviewBitmaps; otherwise disappearance of a damaged cache " +
                "entry is not evidence that PreviewStore observed a null decode",
            decoded,
        )
        corrupted.forEach { entry ->
            entry.writeBytes(failedDecodeBytes)
            assertTrue(
                "the invalid preview payload must be present before the reader is reopened: ${entry.name}",
                entry.readBytes().contentEquals(failedDecodeBytes),
            )
        }

        val startingNative = Heap.settledNativeHeap()
        CacheStats.forgetPreviewReuseRecycles()
        CacheStats.forgetPreviewDecodeFailures()
        PreviewDecodeAssertions.assertFailedReuseReleased(
            File(rules.testDirectory.directory(), "controlled-reuse"),
            previewDimensions.outWidth,
            previewDimensions.outHeight,
            failedDecodeBytes,
            corrupted.size,
        )
        assertEquals("every controlled failed decode borrows and recycles a tracked bitmap", corrupted.size, CacheStats.previewReuseRecycles())
        assertEquals("every controlled invalid payload fails the real codec", corrupted.size, CacheStats.previewDecodeFailures())
        CacheStats.forgetPreviewReuseRecycles()
        CacheStats.forgetPreviewDecodeFailures()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            corruptedPages.forEach { page ->
                TestEnvironment.onMainThread { MemoryReader.pdfView().debugInvalidatePreviewPage(page) }
            }
            for (page in corruptedPages) {
                MemoryReader.jumpToPage(reader, page)
                reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            }
            val settled = reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "every page whose cached preview was destroyed must be re-rendered rather than left blank",
                RenderActivity.IDLE,
                settled.render,
            )

            val stats = CacheStats.previewStats(MemoryReader.pdfView())
            val overCap = stats.poolSizes.filterValues { it > CacheStats.PREVIEW_POOL_PER_BUCKET_CAP }
            assertEquals(
                "a decode that returns null must recycle its reuse bitmap and hand it back rather than " +
                    "abandon it, so the pool must still respect its per bucket cap of " +
                    "${CacheStats.PREVIEW_POOL_PER_BUCKET_CAP}. It held $overCap",
                emptyMap<Int, Int>(),
                overCap,
            )

            Await.until(
                "every damaged reader preview is replaced by a decodable cache entry",
                Await.LONG_TIMEOUT_MS,
                pollMs = POLL_MS,
            ) {
                corrupted.all { entry ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(entry.absolutePath, bounds)
                    bounds.outWidth > 0 && bounds.outHeight > 0
                }
            }

            Log.i(
                LOG_TAG,
                "MEM-40 verified ${corrupted.size} tracked reuse buffers were recycled; " +
                    "the reader observed ${CacheStats.previewDecodeFailures()} additional corrupted-cache decode failures",
            )
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        val settledNative = Heap.settledNativeHeap()
        assertTrue(
            Heap.describe(
                "the native heap across ${corrupted.size} induced decode failures",
                startingNative,
                settledNative,
                NATIVE_BAND,
            ),
            Heap.withinBand(startingNative, settledNative, NATIVE_BAND),
        )
    }


    @Test
    @TestId("MEM-41")
    @TestDuration(DurationCategory.VERY_LONG)
    fun repeatedRenderingUnderMemoryPressureStaysBounded() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.highQualityKey to true,
                Preferences.partSizeKey to Preferences.maxPartSize,
            )
        )
        val since = CrashGate.timestampNow()
        val logSince = AppLogcat.since()

        var startingNative = 0L
        var settledNative = 0L
        var peakNative = 0L
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.BIG_IMAGES)
        ).use {
            val opened = reader.awaitReady()
            assertEquals(
                "the fixture must be the twelve page large image document, which is where the scratch " +
                    "buffers are largest",
                MemoryFixtures.BIG_IMAGES_PAGES as Int?,
                opened.pageCount,
            )
            MemoryReader.pageThrough(reader, 0 until MemoryFixtures.BIG_IMAGES_PAGES)
            MemoryReader.jumpToPage(reader, 0, Await.VERY_LONG_TIMEOUT_MS)
            reader.awaitRenderIdle(Await.VERY_LONG_TIMEOUT_MS)
            startingNative = Heap.settledNativeHeap()
            peakNative = startingNative

            var ballast: ByteArray? = Heap.allocateBallastLeavingFree(FREE_FRACTION_MEM41)
            Log.i(
                LOG_TAG,
                "MEM-41 holds a ballast of ${Heap.megabytes((ballast?.size ?: 0).toLong())} MB against a " +
                    "max memory of ${Heap.megabytes(Heap.maxMemory())} MB",
            )
            try {
                repeat(CYCLES) {
                    for (page in 0 until MemoryFixtures.BIG_IMAGES_PAGES) {
                        rapidJump(page)
                    }
                    for (page in MemoryFixtures.BIG_IMAGES_PAGES - 1 downTo 0) {
                        rapidJump(page)
                    }
                    peakNative = maxOf(peakNative, Heap.nativeHeapAllocated())
                }
            } finally {
                ballast = null
            }
            assertEquals("the ballast must have been released before the settle", null, ballast)

            MemoryReader.jumpToPage(reader, 0, Await.VERY_LONG_TIMEOUT_MS)
            reader.awaitRenderIdle(Await.VERY_LONG_TIMEOUT_MS)
            settledNative = Heap.settledNativeHeap()
            peakNative = maxOf(peakNative, settledNative)
            CrashGate.assertNoFatalSince(since)
            val tombstones = TOMBSTONE_TOKENS.sumOf {
                AppLogcat.appLinesSince(logSince).count { line -> line.contains(it) }
            }
            assertEquals(
                "no native tombstone may appear from the app pid during the pressure run",
                0,
                tombstones,
            )

            Log.i(LOG_TAG, "MEM-41 saw ${allocationFailureCount(logSince)} allocation failure lines under pressure")
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        assertTrue(
            "the highest sampled native heap was ${Heap.megabytes(peakNative)} MiB under render pressure, " +
                "above the ${Heap.megabytes(NATIVE_HEAP_CEILING_BYTES)} MiB fixture ceiling",
            peakNative <= NATIVE_HEAP_CEILING_BYTES,
        )
        assertTrue(
            Heap.describeGrowthBytes(
                "the native heap after ${CYCLES * 2 * MemoryFixtures.BIG_IMAGES_PAGES} render " +
                    "attempts under pressure, compared with a warmed cache",
                startingNative,
                settledNative,
                MAX_POST_WARM_GROWTH_BYTES,
            ),
            Heap.withinGrowthBytes(startingNative, settledNative, MAX_POST_WARM_GROWTH_BYTES),
        )
    }

    private fun rapidJump(pageIndex: Int) {
        TestEnvironment.onMainThread { MemoryReader.pdfView().jumpTo(pageIndex, false) }
        TestEnvironment.uiDevice.waitForIdle()
    }

    private fun allocationFailureCount(since: String): Int =
        AppLogcat.appLinesSince(since).count { line -> line.contains(SCRATCH_FAILURE) } +
            AppLogcat.appLinesSince(since).count { line -> line.contains(BITMAP_FAILURE) }

    private fun previewPage(file: File): Int =
        Regex("^p(\\d+)-").find(file.name)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw AssertionError("Preview cache entry ${file.name} does not identify its page")

    private companion object {
        const val LOG_TAG = "MjPdfMemory"
        const val FREE_FRACTION_MEM39 = 0.12
        const val FREE_FRACTION_MEM41 = 0.08
        const val PAGES_TO_FLING = 80
        const val LAST_FLUNG_PAGE = PAGES_TO_FLING - 1
        const val CYCLES = 10
        const val CORRUPTIONS = 20
        const val MINIMUM_CORRUPTIONS = 10
        const val CORRUPT_PREVIEW_BYTES = 4_096
        const val CORRUPT_PREVIEW_BYTE: Byte = -91
        const val NATIVE_BAND = 0.25
        const val NATIVE_HEAP_CEILING_BYTES = 512L * 1024 * 1024
        const val MAX_POST_WARM_GROWTH_BYTES = 64L * 1024 * 1024
        const val POLL_MS = 250L
        const val SCRATCH_FAILURE = "Scratch buffer allocation failed"
        const val BITMAP_FAILURE = "FPDFBitmap_CreateEx failed"

        val TOMBSTONE_TOKENS = listOf("SIGSEGV", "SIGABRT")
    }
}
