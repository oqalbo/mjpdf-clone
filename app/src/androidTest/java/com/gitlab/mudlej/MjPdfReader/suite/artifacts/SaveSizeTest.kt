// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionPageText
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SaveSizeTest : ArtifactTestCase() {

    @Test
    @TestId("ART-17")
    fun oneHighlightCostsABoundedNumberOfBytesAtEverySourceSize() {
        val measurements = measureOneHighlightPerFixture()

        measurements.forEach { measurement ->
            assertValidOneHighlight(measurement)
            assertTrue(
                "${measurement.name} expanded by ${measurement.expansion} bytes, over its " +
                    "${measurement.budget} byte allowance; a negative delta is valid rewrite compaction",
                measurement.expansion <= measurement.budget,
            )
        }
    }

    @Test
    @TestId("ART-18")
    fun saveGrowthIsAdditiveRatherThanProportionalToTheFileSize() {
        val measurements = measureOneHighlightPerFixture()
        val medium = measurements.first { it.name == ArtifactFixtures.MEDIUM }
        val large = measurements.first { it.name == ArtifactFixtures.LARGE }
        measurements.forEach(::assertValidOneHighlight)

        assertTrue(
            "the ratio for ${medium.name} is ${medium.ratio()}, which would sit near 2.0 if the output " +
                "carried a second copy of the document",
            medium.ratio() < MAX_RATIO,
        )
        assertTrue(
            "the ratio for ${large.name} is ${large.ratio()}",
            large.ratio() < MAX_RATIO,
        )
        assertTrue(
            "each full rewrite stays within the fixed per-edit expansion allowance even when it compacts: " +
                measurements.joinToString { "${it.name}=${it.delta}" },
            measurements.all { it.expansion <= it.budget },
        )
    }

    @Test
    @TestId("ART-19")
    @TestDuration(DurationCategory.LONG)
    fun fiveSaveAndReopenCyclesDoNotCompoundGrowth() {
        val source = fixture(ArtifactFixtures.MEDIUM)
        val originalLength = source.length()
        val sourcePages = ProducedPdf.pageCount(source)
        val pageText = SelectionPageText.of(source)
        val targets = validWordTargets(pageText, CYCLES)
        val lengths = mutableListOf<Long>()
        var current = source

        repeat(CYCLES) { cycle ->
            openReader(current).use {
                reader.highlightExtractedRangeOnCurrentPage(pageText, targets[cycle], HighlightPalette.YELLOW)
                current = saveCopyAs("cycle-${cycle + 1}.pdf")
                lengths.add(current.length())
                assertEquals(
                    "cycle ${cycle + 1} reparses with one persisted annotation per completed selection",
                    cycle + 1,
                    ProducedPdf.appOwnedHighlights(current).size,
                )
            }
            leaveReader()
        }

        val last = lengths.last()
        assertTrue(
            "cycle 5 is $last bytes against an original $originalLength, over the five save allowance",
            last <= originalLength + CYCLES * GROWTH_BUDGET,
        )
        assertTrue(
            "cycle 5 is ${last.toDouble() / originalLength} times the original",
            last.toDouble() / originalLength < MAX_CYCLE_RATIO,
        )
        lengths.forEachIndexed { index, length ->
            val previous = if (index == 0) originalLength else lengths[index - 1]
            assertTrue(
                "cycle ${index + 1} grew by ${length - previous} bytes over the previous one",
                length - previous <= GROWTH_BUDGET,
            )
        }
        assertEquals(
            "the last cycle still reparses with the original page count, so a truncated file cannot " +
                "satisfy the size assertions",
            sourcePages,
            ProducedPdf.pageCount(current),
        )
        assertEquals(
            "the last cycle carries one app owned annotation per cycle",
            CYCLES,
            ProducedPdf.appOwnedHighlights(current).size,
        )
    }

    @Test
    @TestId("ART-20")
    fun aSaveThatAddsAndRemovesEverythingWritesEssentiallyTheSameFile() {
        val source = fixture(ArtifactFixtures.MEDIUM)
        val originalLength = source.length()
        val recordedTexts = ProducedPdf.pageTexts(source)

        openReader(source).use {
            reader.selectWordOnCurrentPage()
            reader.highlightSelection()

            val pageRect = reader.state().pageRect
                ?: throw AssertionError("The reader reports no page rectangle to aim at")
            reader.tapHighlightAt(
                android.graphics.PointF(
                    pageRect.left + pageRect.width() * ArtifactReader.DEFAULT_WORD_X,
                    pageRect.top + pageRect.height() * ArtifactReader.DEFAULT_WORD_Y,
                )
            )
            reader.deleteActiveHighlight()
            assertTrue("the document is still dirty after the net zero edit", reader.hasUnsavedEdits())

            val produced = saveCopyAs(NET_ZERO_NAME)

            assertTrue(
                "a save with no net content change grew by ${produced.length() - originalLength} bytes",
                produced.length() - originalLength <= NO_CHANGE_BUDGET,
            )
            assertEquals(
                "the produced file carries no highlight annotation on any page",
                emptyList<ProducedHighlight>(),
                ProducedPdf.allHighlights(produced),
            )
            assertEquals(
                "every page's text is unchanged",
                recordedTexts,
                ProducedPdf.pageTexts(produced),
            )
        }
    }

    @Test
    @TestId("ART-21")
    @TestDuration(DurationCategory.SHORT)
    fun theUpdateInPlacePathHasTheSameSizeContractAsTheCopyPath() {
        val target = copyFixtureToDownloads(ArtifactFixtures.MEDIUM, IN_PLACE_NAME)
        val originalLength = target.length()
        val sourcePages = ProducedPdf.pageCount(target)
        val sourceSizes = ProducedPdf.pageSizes(target)
        val sourceDigest = ArtifactBytes.sha256(target)

        openReader(target).use {
            reader.highlightWordOnCurrentPage()
            saveInPlace()

            val newLength = target.length()
            val expansion = (newLength - originalLength).coerceAtLeast(0L)
            assertEquals(
                "the file reparses with its own page count",
                sourcePages,
                ProducedPdf.pageCount(target),
            )
            assertEquals("the file keeps every page size", sourceSizes, ProducedPdf.pageSizes(target))
            assertEquals(
                "the file carries exactly one app owned annotation",
                1,
                ProducedPdf.appOwnedHighlights(target).size,
            )
            assertTrue(
                "the in place rewrite must change the file digest",
                ArtifactBytes.sha256(target) != sourceDigest,
            )
            assertTrue(
                "the in place write expanded by $expansion bytes",
                expansion <= GROWTH_BUDGET,
            )
            assertTrue(
                "no partial write is left beside it, found ${leftoverWritesFor(target)}",
                leftoverWritesFor(target).isEmpty(),
            )
        }
    }

    @Test
    @TestId("ART-22")
    fun aRepeatSaveToTheSameTargetReplacesTheFileInsteadOfAppendingToIt() {
        val source = fixture(ArtifactFixtures.MEDIUM)
        val sourceLength = source.length()
        val sourcePages = ProducedPdf.pageCount(source)

        openReader(source).use {
            reader.highlightWordOnCurrentPage()
            val target = saveCopyAs(REPEAT_TARGET_NAME)
            val firstLength = target.length()
            assertTrue(
                "the first save is $firstLength bytes against a $sourceLength byte source",
                firstLength - sourceLength <= GROWTH_BUDGET,
            )

            padToTwentyMegabytes(target)
            assertTrue("the target now starts far larger than the source", target.length() > PADDED_FLOOR)

            reader.highlightWordOnCurrentPage(
                HighlightPalette.GREEN,
                ArtifactReader.DEFAULT_WORD_X,
                SECOND_WORD_Y,
            )
            saveInPlaceToRememberedDestination()

            val secondLength = target.length()
            assertTrue(
                "the second write truncated the padded target back to $secondLength bytes rather than " +
                    "appending to it",
                secondLength - firstLength <= GROWTH_BUDGET,
            )
            assertEquals(
                "the target carries the header exactly once, so no copy was stacked on another",
                1,
                ArtifactBytes.occurrences(target, ArtifactBytes.PDF_HEADER),
            )
            assertEquals(
                "the target reparses with the source page count",
                sourcePages,
                ProducedPdf.pageCount(target),
            )
            assertEquals(
                "the target carries both highlights",
                2,
                ProducedPdf.appOwnedHighlights(target).size,
            )
        }
    }

    @Test
    @TestId("ART-23")
    fun theTwoFullRewritePathsAreComparedOnTheSameDocument() {
        PrintFlow.seedAsPrimaryToolbarAction()
        val source = fixture(ArtifactFixtures.PROTECTED)
        val originalLength = source.length()
        val sourcePages = ProducedPdf.pageCount(source, ArtifactFixtures.PROTECTED_PASSWORD)

        openProtectedReader(source, ArtifactFixtures.PROTECTED_PASSWORD).use {
            PrintFlow.start()
            val staged = AppDirectories.awaitPrintStagedFile()
            val stagedLength = staged.length()
            assertTrue(
                "the decrypted staging copy is $stagedLength bytes against a $originalLength byte source, " +
                    "so removing encryption changed stream sizes without duplicating content",
                stagedLength <= (originalLength * DECRYPT_RATIO).toLong(),
            )
            assertTrue(
                "the staged file opens with no password",
                ProducedPdf.opensWithoutPassword(staged),
            )
            assertEquals(
                "the staged file keeps every page",
                sourcePages,
                ProducedPdf.pageCount(staged),
            )
            PrintFlow.dismiss()

            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.PROTECTED))
            assertTrue(
                "the saved copy grew by ${produced.length() - originalLength} bytes",
                produced.length() - originalLength <= GROWTH_BUDGET,
            )
            assertEquals(
                "the saved copy keeps every page",
                sourcePages,
                ProducedPdf.pageCount(produced, ArtifactFixtures.PROTECTED_PASSWORD),
            )
            assertTrue(
                "the saved copy still requires the password, unlike the staged one",
                ProducedPdf.refusesWithoutPassword(produced),
            )
        }
    }

    @Test
    @TestId("ART-24")
    @TestDuration(DurationCategory.SHORT)
    fun aVeryLargeDocumentKeepsEveryPageAfterASave() {
        val target = copyFixtureToDownloads(ArtifactFixtures.VERY_LARGE, VERY_LARGE_NAME)
        val recordedPages = ProducedPdf.pageCount(target)
        val sampled = listOf(0, recordedPages / 2, recordedPages - 1)
        val recordedSizes = ProducedPdf.pageSizes(target)
        val recordedTexts = sampled.associateWith { ProducedPdf.pageText(target, it) }

        openReader(target).use {
            reader.highlightWordOnCurrentPage()
            saveInPlace()

            assertEquals("the page count survives", recordedPages, ProducedPdf.pageCount(target))
            val savedSizes = ProducedPdf.pageSizes(target)
            sampled.forEach { index ->
                assertEquals(
                    "page $index keeps its size in points",
                    recordedSizes[index],
                    savedSizes[index],
                )
                assertEquals(
                    "page $index keeps its extracted text",
                    recordedTexts[index],
                    ProducedPdf.pageText(target, index),
                )
            }
            assertEquals(
                "the document carries exactly the one annotation that was made",
                1,
                ProducedPdf.appOwnedHighlights(target).size,
            )
            assertTrue(
                "no partial write is left beside the saved document, found ${leftoverWritesFor(target)}",
                leftoverWritesFor(target).isEmpty(),
            )
        }
    }

    private data class SizeMeasurement(
        val name: String,
        val originalLength: Long,
        val produced: File,
        val sourcePages: Int,
        val sourceSizes: List<PageSize>,
        val sourceDigest: String,
        val budget: Long,
    ) {
        val delta: Long get() = produced.length() - originalLength
        val expansion: Long get() = delta.coerceAtLeast(0L)

        fun ratio(): Double = produced.length().toDouble() / originalLength
    }

    private fun measureOneHighlightPerFixture(): List<SizeMeasurement> {
        val budgets = mapOf(
            ArtifactFixtures.SMALL to SMALL_GROWTH_BUDGET,
            ArtifactFixtures.MEDIUM to GROWTH_BUDGET,
            ArtifactFixtures.LARGE to GROWTH_BUDGET,
        )
        return budgets.map { (name, budget) ->
            val source = fixture(name)
            val originalLength = source.length()
            val sourcePages = ProducedPdf.pageCount(source)
            val sourceSizes = ProducedPdf.pageSizes(source)
            val sourceDigest = ArtifactBytes.sha256(source)
            val produced: File
            openReader(source).use {
                reader.highlightWordOnCurrentPage()
                produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(name))
            }
            leaveReader()
            SizeMeasurement(name, originalLength, produced, sourcePages, sourceSizes, sourceDigest, budget)
        }
    }

    private fun assertValidOneHighlight(measurement: SizeMeasurement) {
        assertEquals(
            "the copy of ${measurement.name} reparses with its source page count",
            measurement.sourcePages,
            ProducedPdf.pageCount(measurement.produced),
        )
        assertEquals(
            "the copy of ${measurement.name} keeps every page size",
            measurement.sourceSizes,
            ProducedPdf.pageSizes(measurement.produced),
        )
        assertEquals(
            "the copy of ${measurement.name} carries exactly one app owned annotation",
            1,
            ProducedPdf.appOwnedHighlights(measurement.produced).size,
        )
        assertTrue(
            "the copy of ${measurement.name} must differ from its source digest",
            ArtifactBytes.sha256(measurement.produced) != measurement.sourceDigest,
        )
    }

    private fun validWordTargets(page: SelectionPageText, count: Int): List<IntRange> {
        val targets = page.lineRanges.indices.flatMap(page::wordRanges).filter { range ->
            page.normalized(range).any { it.isLetterOrDigit() } && runCatching {
                val bounds = page.boundsOf(range)
                bounds.width > 0f && bounds.height > 0f
            }.getOrDefault(false)
        }.take(count)
        if (targets.size != count) {
            throw AssertionError(
                "Page ${page.pageIndex} exposes only ${targets.size} boxed extracted words for $count save cycles; " +
                    "its text reads ${page.readable()}"
            )
        }
        return targets
    }

    private fun saveInPlaceToRememberedDestination() {
        reader.tapSave()
        reader.awaitSaveSettled()
    }

    private fun padToTwentyMegabytes(target: File) {
        val padding = ByteArray(1 shl 16)
        java.io.FileOutputStream(target, true).use { stream ->
            var written = target.length()
            while (written < PADDED_FLOOR) {
                stream.write(padding)
                written += padding.size
            }
        }
    }

    private fun leftoverWritesFor(target: File): List<String> =
        AppDirectories.namesIn(target.parentFile).filter { name ->
            name == "${target.name}.saving" ||
                (name.startsWith("${target.name}.") && name.endsWith(".part"))
        }

    private companion object {
        const val CYCLES = 5
        const val MAX_RATIO = 1.10
        const val MAX_CYCLE_RATIO = 1.30
        const val DECRYPT_RATIO = 1.20
        const val SECOND_WORD_Y = 0.19f
        const val PADDED_FLOOR = 20L * 1024 * 1024
        const val NET_ZERO_NAME = "net-zero.pdf"
        const val IN_PLACE_NAME = "in-place-size.pdf"
        const val REPEAT_TARGET_NAME = "target.pdf"
        const val VERY_LARGE_NAME = "very-large-save.pdf"
    }
}
