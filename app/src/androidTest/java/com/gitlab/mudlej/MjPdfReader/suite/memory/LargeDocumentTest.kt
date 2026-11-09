// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.FixtureResource
import com.gitlab.mudlej.MjPdfReader.suite.core.RequiresFixture
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Bitmap
import android.os.Environment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.baseline.BaselineCapture
import com.gitlab.mudlej.MjPdfReader.suite.baseline.PixelComparison
import com.gitlab.mudlej.MjPdfReader.suite.baseline.TolerancePolicy
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.GoToPageRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.ui.gotopage.GoToPageActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class LargeDocumentTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val goToPage = GoToPageRobot()

    @Test
    @TestId("MEM-42")
    @RequiresFixture(FixtureResource.LARGE_PDF)
    fun aFourHundredMegabytePdfOpensWithoutLoadingItselfIntoTheHeap() {
        val small = MemoryFixtures.file(MemoryFixtures.NORMAL)
        val huge = MemoryFixtures.generatedOnDevice(MemoryFixtures.HUGE_500MB)
        assertTrue(
            "the fixture must genuinely be a large file for this comparison to mean anything, it is " +
                "${Heap.megabytes(huge.length())} MB",
            huge.length() > MINIMUM_HUGE_BYTES,
        )

        val cacheBefore = largestFileBytes(TestEnvironment.targetContext.cacheDir)
        val copiesBefore = durableCopyBytes()

        val reference: Long
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(small)).use {
            reader.awaitReady()
            reference = Heap.settledJavaHeap()
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(huge)).use {
            val opened = reader.awaitReady()
            assertEquals(
                "the fixture must be the two hundred page huge document",
                MemoryFixtures.HUGE_500MB_PAGES as Int?,
                opened.pageCount,
            )
            val afterOpen = Heap.settledJavaHeap()
            assertEquals(
                "a document is read through a descriptor, so exactly one may be open for it",
                1,
                FileDescriptors.countFor(huge),
            )

            MemoryReader.jumpToPage(reader, MemoryFixtures.HUGE_500MB_PAGES - 1, Await.VERY_LONG_TIMEOUT_MS)
            reader.awaitRenderIdle(Await.VERY_LONG_TIMEOUT_MS)
            val afterLastPage = Heap.settledJavaHeap()

            assertTrue(
                Heap.describeGrowthBytes(
                    "the heap with a ${Heap.megabytes(huge.length())} MB document open, against the heap " +
                        "with ${MemoryFixtures.NORMAL} open",
                    reference,
                    afterOpen,
                    HUGE_DOCUMENT_HEAP_GROWTH_BYTES,
                ),
                Heap.withinGrowthBytes(reference, afterOpen, HUGE_DOCUMENT_HEAP_GROWTH_BYTES),
            )
            assertTrue(
                Heap.describeGrowthBytes(
                    "the heap on the last page of the huge document, against the same reference",
                    reference,
                    afterLastPage,
                    HUGE_DOCUMENT_HEAP_GROWTH_BYTES,
                ),
                Heap.withinGrowthBytes(reference, afterLastPage, HUGE_DOCUMENT_HEAP_GROWTH_BYTES),
            )

            MemoryReader.closeReaderAndAwaitGone(reader)
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(huge, 0)

        val cacheAfter = largestFileBytes(TestEnvironment.targetContext.cacheDir)
        val copiesAfter = durableCopyBytes()
        assertTrue(
            "opening a file through a VIEW intent must not copy it, and the largest file under cacheDir " +
                "grew from ${Heap.megabytes(cacheBefore)} MB to ${Heap.megabytes(cacheAfter)} MB",
            cacheAfter < COPY_EVIDENCE_BYTES,
        )
        assertEquals(
            "no durable copy of the document may have been made either, the copy directories held " +
                "${Heap.megabytes(copiesBefore)} MB and now hold ${Heap.megabytes(copiesAfter)} MB",
            copiesBefore,
            copiesAfter,
        )
    }

    @Test
    @TestId("MEM-43")
    fun aPdfOfVeryLargeImagesPagesWithoutUnboundedGrowth() {
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.BIG_IMAGES)
        ).use {
            val opened = reader.awaitReady()
            assertEquals(
                "the fixture must be the twelve page large image document",
                MemoryFixtures.BIG_IMAGES_PAGES as Int?,
                opened.pageCount,
            )
            reader.awaitFullQualityRender(Await.VERY_LONG_TIMEOUT_MS)
            val firstCapture = capturePage()
            val nativeSamples = mutableListOf<Long>()

            repeat(CYCLES) {
                for (page in 1 until MemoryFixtures.BIG_IMAGES_PAGES) {
                    MemoryReader.jumpToPage(reader, page, Await.VERY_LONG_TIMEOUT_MS)
                }
                for (page in MemoryFixtures.BIG_IMAGES_PAGES - 2 downTo 0) {
                    MemoryReader.jumpToPage(reader, page, Await.VERY_LONG_TIMEOUT_MS)
                }
                reader.awaitFullQualityRender(Await.VERY_LONG_TIMEOUT_MS)
                nativeSamples += Heap.settledNativeHeap()
            }

            val settled = reader.awaitFullQualityRender(Await.VERY_LONG_TIMEOUT_MS)
            assertEquals(
                "the traversal must end back on the first page",
                0 as Int?,
                settled.page,
            )
            assertEquals(
                "and it must be fully rendered before the capture is compared",
                RenderActivity.IDLE,
                settled.render,
            )

            val finalNative = Heap.settledNativeHeap()
            val peakNative = maxOf(finalNative, nativeSamples.maxOrNull() ?: 0L)
            assertTrue(
                "the highest sampled native heap was ${Heap.megabytes(peakNative)} MiB after traversing the image book, " +
                    "above the ${Heap.megabytes(NATIVE_HEAP_CEILING_BYTES)} MiB fixture ceiling",
                peakNative <= NATIVE_HEAP_CEILING_BYTES,
            )
            val totalPssKb = Heap.totalPssKilobytes()
            assertTrue(
                "the image book uses ${totalPssKb / 1024} MiB total PSS, above the " +
                    "${TOTAL_PSS_CEILING_KB / 1024} MiB fixture ceiling",
                totalPssKb <= TOTAL_PSS_CEILING_KB,
            )

            val openPages = CacheStats.openPageStats(MemoryReader.pdfView())
            assertTrue(
                "the open native page count must stay at or below ${CacheStats.MAX_OPEN_RENDER_PAGES}, it " +
                    "read $openPages",
                openPages.renderPageOrder <= CacheStats.MAX_OPEN_RENDER_PAGES,
            )
            assertTrue(
                "and above zero, because a document holding no open page satisfies the cap without testing " +
                    "it. It read $openPages",
                openPages.renderPageOrder > 0,
            )

            val secondCapture = capturePage()
            assertTrue(
                "the capture must carry real page content rather than a flat field, otherwise two blank " +
                    "images would match and prove nothing",
                PixelComparison.distinctColourCount(firstCapture) > DISTINCT_COLOUR_FLOOR,
            )
            val comparison = PixelComparison.compare(firstCapture, secondCapture, TolerancePolicy.DEFAULT)
            val reasons = comparison.verdict(TolerancePolicy.DEFAULT)
            assertTrue(
                "page one must render identically after $CYCLES passes:\n${comparison.summary()}" +
                    "\n${reasons.joinToString("\n")}",
                reasons.isEmpty(),
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("MEM-44")
    fun aThreeThousandPagePdfKeepsPerPageBookkeepingBounded() {
        val long = MemoryFixtures.file(MemoryFixtures.THREE_THOUSAND_PAGES)

        val reference: Long
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.LARGE)
        ).use {
            reader.awaitReady()
            reference = Heap.settledJavaHeap()
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(long)).use {
            val opened = reader.awaitReady()
            assertEquals(
                "the fixture must report three thousand pages",
                MemoryFixtures.THREE_THOUSAND_PAGES_COUNT as Int?,
                opened.pageCount,
            )
            val afterOpen = Heap.settledJavaHeap()

            val lastPage = MemoryFixtures.THREE_THOUSAND_PAGES_COUNT - 1
            MemoryReader.jumpToPage(reader, lastPage, Await.VERY_LONG_TIMEOUT_MS)
            reader.awaitRenderIdle(Await.VERY_LONG_TIMEOUT_MS)
            val afterJump = Heap.settledJavaHeap()

            val openPages = CacheStats.openPageStats(MemoryReader.pdfView())
            assertTrue(
                "a jump to page ${lastPage + 1} must not leave behind pages it never closed, the open " +
                    "count read $openPages",
                openPages.renderPageOrder <= CacheStats.MAX_OPEN_RENDER_PAGES,
            )
            assertTrue(
                "and the count must be above zero, because a document holding no open page satisfies the " +
                    "cap without testing it. It read $openPages",
                openPages.renderPageOrder > 0,
            )
            assertEquals(
                "all open pages must belong to the render or text cache; observed $openPages",
                0,
                openPages.openPages - openPages.renderPageOrder - openPages.textOpenedPages,
            )

            assertTrue(
                Heap.describeGrowthBytes(
                    "the heap with a three thousand page document open, against ${MemoryFixtures.LARGE}",
                    reference,
                    afterOpen,
                    PAGE_COUNT_HEAP_GROWTH_BYTES,
                ),
                Heap.withinGrowthBytes(reference, afterOpen, PAGE_COUNT_HEAP_GROWTH_BYTES),
            )
            assertTrue(
                Heap.describeGrowthBytes(
                    "the heap on the last page of a three thousand page document, against the same reference",
                    reference,
                    afterJump,
                    PAGE_COUNT_HEAP_GROWTH_BYTES,
                ),
                Heap.withinGrowthBytes(reference, afterJump, PAGE_COUNT_HEAP_GROWTH_BYTES),
            )

            goToPage.openDialogFromReader()
            goToPage.tapAllPages()
            goToPage.awaitGridShown()
            goToPage.scrollGridTo(lastPage)
            goToPage.awaitGridScrollIdle()
            goToPage.pressBack()
            Screens.awaitGone(GoToPageActivity::class.java, Await.LONG_TIMEOUT_MS)

            MemoryReader.awaitDescriptorCount(long, 1)
            MemoryReader.holdDescriptorCount(long, 1)
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(long, 0)
    }

    private fun capturePage(): Bitmap = BaselineCapture.view(R.id.pdfView)

    private fun largestFileBytes(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.maxOfOrNull { it.length() } ?: 0L

    private fun durableCopyBytes(): Long {
        val privateCopies = File(TestEnvironment.targetContext.filesDir, PRIVATE_COPY_FOLDER)
        val publicCopies = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            PUBLIC_COPY_FOLDER,
        )
        return MemoryReader.directoryBytes(privateCopies) + MemoryReader.directoryBytes(publicCopies)
    }

    private companion object {
        const val NATIVE_HEAP_CEILING_BYTES = 512L * 1024 * 1024
        const val TOTAL_PSS_CEILING_KB = 768L * 1024
        const val CYCLES = 5
        const val DISTINCT_COLOUR_FLOOR = 16
        const val MINIMUM_HUGE_BYTES = 400L * 1024 * 1024
        const val COPY_EVIDENCE_BYTES = 100L * 1024 * 1024
        const val HUGE_DOCUMENT_HEAP_GROWTH_BYTES = 32L * 1024 * 1024
        const val PAGE_COUNT_HEAP_GROWTH_BYTES = 16L * 1024 * 1024
        const val PRIVATE_COPY_FOLDER = "imported"
        const val PUBLIC_COPY_FOLDER = "MJ PDF"
    }
}
