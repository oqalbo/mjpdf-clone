// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class DiskBoundTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("MEM-13")
    fun theCoverCacheEnforcesItsSixtyFourMegabyteDiskBound() {
        val covers = MemoryReader.coverCacheDirectory()
        val seeded = seedPaddingFiles(covers, COVER_SEED_FILES) { index ->
            "seeded%03d-w%d.jpg".format(index, CacheStats.COVER_WIDTH_BUCKETS.first())
        }
        val oldest = seeded.take(EVICTION_SAMPLE).map { it.name }
        val newest = seeded.takeLast(EVICTION_SAMPLE).map { it.name }

        assertTrue(
            "the seeded directory must start over the ${bytesToMegabytes(COVER_DISK_BUDGET_BYTES)} MB budget, " +
                "otherwise the trim under test has nothing to do",
            MemoryReader.directoryBytes(covers) > COVER_DISK_BUDGET_BYTES,
        )

        TestEnvironment.targetContext.startActivity(
            Intent(TestEnvironment.targetContext, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitDirectorySizeStable(covers)

        val total = MemoryReader.directoryBytes(covers)
        assertTrue(
            "trimDisk stops as soon as the running total drops to the budget, so the directory may exceed " +
                "${bytesToMegabytes(COVER_DISK_BUDGET_BYTES)} MB by at most one file. It holds " +
                "${bytesToMegabytes(total)} MB",
            total <= COVER_DISK_BUDGET_BYTES + PADDING_FILE_BYTES,
        )

        val survivingOldest = oldest.filter { File(covers, it).exists() }
        assertEquals(
            "eviction is oldest first by lastModified, so the oldest $EVICTION_SAMPLE files must be gone. " +
                "These survived: $survivingOldest",
            emptyList<String>(),
            survivingOldest,
        )
        val missingNewest = newest.filterNot { File(covers, it).exists() }
        assertEquals(
            "the newest $EVICTION_SAMPLE files must survive the trim. These were deleted: $missingNewest",
            emptyList<String>(),
            missingNewest,
        )
    }

    @Test
    @TestId("MEM-16")
    fun thePreviewDiskCacheEnforcesItsNinetySixMegabyteBudget() {
        val previews = File(MemoryReader.previewCacheDirectory(), SEEDED_DOC_KEY)
        val seeded = seedPaddingFiles(previews, PREVIEW_SEED_FILES) { index ->
            "p$index-w${CacheStats.THUMBNAIL_WIDTH_BUCKETS.first()}-t0.jpg"
        }
        val oldest = seeded.take(EVICTION_SAMPLE).map { it.name }

        assertTrue(
            "the seeded directory must start over the ${bytesToMegabytes(PREVIEW_DISK_BUDGET_BYTES)} MB " +
                "budget, otherwise the trim under test has nothing to do",
            MemoryReader.directoryBytes(MemoryReader.previewCacheDirectory()) > PREVIEW_DISK_BUDGET_BYTES,
        )

        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.NORMAL)
        ).use {
            reader.awaitReady()
            awaitDirectorySizeStable(
                MemoryReader.previewCacheDirectory(),
                MemoryReader.DESCRIPTOR_STABLE_LONG_MS,
            )
        }

        val total = MemoryReader.directoryBytes(MemoryReader.previewCacheDirectory())
        assertTrue(
            "the preview cache may exceed ${bytesToMegabytes(PREVIEW_DISK_BUDGET_BYTES)} MB by at most one " +
                "file, because trim stops as soon as the running total drops to the budget. It holds " +
                "${bytesToMegabytes(total)} MB",
            total <= PREVIEW_DISK_BUDGET_BYTES + PADDING_FILE_BYTES,
        )
        val survivingOldest = oldest.filter { File(previews, it).exists() }
        assertEquals(
            "eviction is oldest first by lastModified, so the oldest $EVICTION_SAMPLE files must be gone. " +
                "These survived: $survivingOldest",
            emptyList<String>(),
            survivingOldest,
        )

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun seedPaddingFiles(directory: File, count: Int, name: (Int) -> String): List<File> {
        directory.mkdirs()
        val padding = ByteArray(PADDING_FILE_BYTES.toInt())
        val base = System.currentTimeMillis() - count.toLong() * MODIFIED_STEP_MS
        return (0 until count).map { index ->
            val file = File(directory, name(index))
            file.writeBytes(padding)
            file.setLastModified(base + index.toLong() * MODIFIED_STEP_MS)
            file
        }
    }

    private fun awaitDirectorySizeStable(
        directory: File,
        windowMs: Long = MemoryReader.DESCRIPTOR_STABLE_MS,
    ) {
        var last = -1L
        Await.untilTrueContinuously(
            condition = "the size of ${directory.name} is stable",
            stableForMs = windowMs,
            timeoutMs = windowMs + Await.VERY_LONG_TIMEOUT_MS,
        ) {
            val current = MemoryReader.directoryBytes(directory)
            val stable = current == last
            last = current
            stable
        }
    }

    private fun bytesToMegabytes(bytes: Long): Long = bytes / (1024 * 1024)

    private companion object {
        const val COVER_DISK_BUDGET_BYTES = 64L * 1024 * 1024
        const val PREVIEW_DISK_BUDGET_BYTES = 96L * 1024 * 1024
        const val PADDING_FILE_BYTES = 1L * 1024 * 1024
        const val COVER_SEED_FILES = 100
        const val PREVIEW_SEED_FILES = 120
        const val EVICTION_SAMPLE = 20
        const val MODIFIED_STEP_MS = 1_000L
        const val SEEDED_DOC_KEY = "mjpdf-memory-seeded-doc"
    }
}
