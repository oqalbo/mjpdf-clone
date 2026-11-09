// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class MemoryPressureTest : ResilienceTestCase() {

    @Test
    @TestId("RES-26")
    @TestDuration(DurationCategory.VERY_LONG)
    fun aLargeDocumentAtBestQualitySurvivesAllocationPressure() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.highQualityKey to true,
                Preferences.partSizeKey to Preferences.maxPartSize,
            )
        )
        val file = fixture(ResilienceFixtures.LARGE)
        val since = CrashGate.timestampNow()

        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val ballast = allocateBallast()
        try {
            repeat(FLUNG_PAGES) { reader.swipeForward() }
            reader.awaitMotionSettled()
            jumpToPage(FINAL_PAGE)
        } finally {
            assertTrue("the ballast must have been held for the whole fling", ballast.isNotEmpty())
        }

        val settled = settledFullQuality()
        assertEquals(
            "the reader must still be able to reach and render the page it was flung to",
            FINAL_PAGE as Int?,
            settled.page,
        )
        CrashGate.assertNoFatalSince(since)
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-27")
    @TestDuration(DurationCategory.NORMAL)
    fun aCriticalTrimMemorySignalDoesNotLoseTheDocument() {
        val file = fixture(ResilienceFixtures.LARGE)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        jumpToPage(START_PAGE)
        settledFullQuality()

        Shell.execute("am send-trim-memory ${TestEnvironment.targetPackage} RUNNING_CRITICAL")

        val afterTrim = reader.awaitFullQualityRender(Await.VERY_LONG_TIMEOUT_MS)
        assertEquals(
            "the trimmed reader must redraw the page it was on rather than leave it blank",
            RenderQuality.FULL,
            afterTrim.quality,
        )

        scrollToPage(START_PAGE + PAGES_AFTER_TRIM)
        val moved = settledFullQuality()
        assertEquals(
            "reading must continue normally after the trim. scrollToPage swipes until the page index " +
                "reaches its target and throws if swiping stops moving the document, so it proves the " +
                "gesture path still works. A swipe is a fixed pixel drag rather than a page turn, so a " +
                "fixed number of swipes is not a fixed number of pages",
            (START_PAGE + PAGES_AFTER_TRIM) as Int?,
            moved.page,
        )
        assertEquals(
            "the trim must not have cost the reader its document handle",
            1,
            FileDescriptors.countFor(file),
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-28")
    fun aCorruptedPreviewEntryIsRerenderedRatherThanLeftBlank() {
        val file = fixture(ResilienceFixtures.NORMAL)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        repeat(WARMED_PAGES) { reader.swipeForward() }
        settledFullQuality()
        val previewDirectory = PreviewCache.directoryForOpenDocument()
        PreviewCache.awaitEntryFor(previewDirectory, DAMAGED_PAGE)
        ReaderProbe.finishAndRemoveReaderTasks()
        com.gitlab.mudlej.MjPdfReader.suite.core.Screens.awaitGone(
            com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity::class.java,
            Await.LONG_TIMEOUT_MS,
        )

        val damaged = PreviewCache.entriesFor(previewDirectory, DAMAGED_PAGE)
        assertTrue(
            "the warm up must have written a preview for page index $DAMAGED_PAGE into " +
                "${previewDirectory.absolutePath}, otherwise there is nothing to corrupt",
            damaged.isNotEmpty(),
        )
        damaged.forEach { entry ->
            entry.writeBytes(Random(RANDOM_SEED).nextBytes(entry.length().toInt()))
        }

        val since = ResilienceLogcat.since()
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        jumpToPage(DAMAGED_PAGE)
        val recovered = settledFullQuality()

        assertEquals(
            "the page whose cached preview was destroyed must come back at full quality",
            RenderQuality.FULL,
            recovered.quality,
        )
        val previews = ResilienceLogcat.awaitPreviewFor(DAMAGED_PAGE, since, Await.LONG_TIMEOUT_MS)
        assertTrue(
            "the damaged page must be re-rendered, but the preview render log carries only $previews",
            previews.contains(DAMAGED_PAGE),
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private fun allocateBallast(): List<ByteArray> {
        val runtime = Runtime.getRuntime()
        val free = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val target = (free * BALLAST_FRACTION).toLong()
        val blocks = mutableListOf<ByteArray>()
        var held = 0L
        while (held < target) {
            val size = minOf(BALLAST_BLOCK_BYTES.toLong(), target - held).toInt()
            if (size <= 0) break
            val block = runCatching { ByteArray(size) }.getOrNull() ?: break
            blocks += block
            held += size
        }
        return blocks
    }

    private companion object {
        const val FLUNG_PAGES = 50
        const val FINAL_PAGE = 49
        const val START_PAGE = 30
        const val PAGES_AFTER_TRIM = 5
        const val WARMED_PAGES = 10
        const val DAMAGED_PAGE = 5
        const val RANDOM_SEED = 20260807
        const val BALLAST_FRACTION = 0.85f
        const val BALLAST_BLOCK_BYTES = 4 * 1024 * 1024
    }
}
