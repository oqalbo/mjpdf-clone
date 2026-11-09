// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.annotationsave.SaveSearch
import com.gitlab.mudlej.MjPdfReader.suite.localization.SearchHighlight
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SavedPdfIntegrityTest : ArtifactTestCase() {

    @Test
    @TestId("ART-01")
    fun aSavedCopyIsAStructurallyValidPdfThatPdfiumReparsesOnItsOwn() {
        val source = fixture(ArtifactFixtures.SMALL)
        val sourcePages = ProducedPdf.pageCount(source)

        openReader(source).use {
            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.SMALL))

            assertTrue(
                "the produced file starts with the PDF header",
                ArtifactBytes.startsWith(produced, ArtifactBytes.PDF_HEADER),
            )
            assertTrue(
                "the produced file ends with a trailer inside its last kilobyte",
                ArtifactBytes.tailContains(produced, ArtifactBytes.PDF_TRAILER),
            )
            assertEquals(
                "pdfium reparses the produced file with the source page count",
                sourcePages,
                ProducedPdf.pageCount(produced),
            )
            assertTrue(
                "every page of the produced file opens",
                ProducedPdf.everyPageOpens(produced),
            )
            assertEquals(
                "reading the produced file leaves no open descriptor pointing at it",
                0,
                OpenDescriptors.countPointingAt(produced),
            )
        }
    }

    @Test
    @TestId("ART-02")
    fun aSaveLeavesEveryPagesTextAndSizeUntouched() {
        val source = fixture(ArtifactFixtures.SMALL)
        val recordedTexts = ProducedPdf.pageTexts(source)
        val recordedSizes = ProducedPdf.pageSizes(source)

        openReader(source).use {
            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.SMALL))

            assertEquals(
                "the produced file keeps the page count",
                recordedTexts.size,
                ProducedPdf.pageCount(produced),
            )
            assertEquals(
                "every page's extracted text is unchanged character for character",
                recordedTexts,
                ProducedPdf.pageTexts(produced),
            )
            assertEquals(
                "every page keeps its width and height in points",
                recordedSizes,
                ProducedPdf.pageSizes(produced),
            )
        }
    }

    @Test
    @TestId("ART-03")
    fun documentMetadataSurvivesASave() {
        val source = fixture(ArtifactFixtures.META_RICH)
        val recorded = ProducedPdf.meta(source)

        openReader(source).use {
            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.META_RICH))
            val saved = ProducedPdf.meta(produced)

            assertEquals("the title survives", recorded.title, saved.title)
            assertEquals("the author survives", recorded.author, saved.author)
            assertEquals("the subject survives", recorded.subject, saved.subject)
            assertEquals("the keywords survive", recorded.keywords, saved.keywords)
            assertEquals("the creator survives", recorded.creator, saved.creator)
            assertEquals("the producer survives", recorded.producer, saved.producer)
            assertEquals("the creation date survives", recorded.creationDate, saved.creationDate)
            assertEquals("the page count survives", recorded.totalPages, saved.totalPages)
        }
    }

    @Test
    @TestId("ART-04")
    fun theSavedFileIsACleanSingleGenerationFullRewrite() {
        val source = fixture(ArtifactFixtures.MEDIUM)
        val sourceLength = source.length()
        val sourcePages = ProducedPdf.pageCount(source)

        openReader(source).use {
            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.MEDIUM))
            val bytes = produced.readBytes()

            assertEquals(
                "a full rewrite emits the header exactly once",
                1,
                ArtifactBytes.occurrences(bytes, ArtifactBytes.PDF_HEADER),
            )
            assertEquals(
                "and the header sits at offset zero",
                0,
                ArtifactBytes.indexOf(bytes, ArtifactBytes.PDF_HEADER),
            )
            assertEquals(
                "a full rewrite emits a single generation with a single trailer, so an incremental " +
                    "save that appended every parsed object would read more than one",
                1,
                ArtifactBytes.occurrences(bytes, ArtifactBytes.PDF_TRAILER),
            )
            assertEquals(
                "the produced file reparses with the source page count",
                sourcePages,
                ProducedPdf.pageCount(produced),
            )
            assertEquals(
                "the produced file carries exactly the one highlight that was made",
                1,
                ProducedPdf.appOwnedHighlights(produced).size,
            )
            assertTrue(
                "a doubled body cannot pass the growth bound, it grew by ${produced.length() - sourceLength}",
                produced.length() - sourceLength <= GROWTH_BUDGET,
            )
        }
    }

    @Test
    @TestId("ART-05")
    fun aNewHighlightIsPresentInTheSavedFileWhenReparsed() {
        val source = fixture(ArtifactFixtures.SMALL)
        assertEquals(
            "the untouched fixture carries no highlight on page 1",
            0,
            ProducedPdf.highlightsOn(source, 0).size,
        )

        openReader(source).use {
            reader.selectWordOnCurrentPage()
            val selected = reader.copySelectionToClipboard()
            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.SMALL))

            val onPageOne = ProducedPdf.highlightsOn(produced, 0)
            assertEquals("page 1 of the produced file carries exactly one annotation", 1, onPageOne.size)
            val annotation = onPageOne.single()
            assertEquals("the annotation quotes the text that was selected", selected, annotation.quote)
            assertTrue("the annotation is app owned", annotation.appOwned)
            assertFalse("the annotation is not a search result", annotation.searchResult)
            assertTrue("the annotation carries bounds", annotation.hasBounds)
            assertTrue("its bounds have a positive width", annotation.boundsWidth > 0f)
            assertTrue("its bounds have a positive height", annotation.boundsHeight > 0f)

            val elsewhere = ProducedPdf.allHighlights(produced).filter { it.pageIndex != 0 }
            assertEquals("no other page carries an annotation", emptyList<ProducedHighlight>(), elsewhere)
        }
    }

    @Test
    @TestId("ART-06")
    fun aNoteAttachedToAHighlightRoundTripsIntoTheSavedFile() {
        val source = fixture(ArtifactFixtures.SMALL)

        openReader(source).use {
            reader.highlightWordOnCurrentPage()
            reader.selectWordOnCurrentPage(SECOND_WORD_X, SECOND_WORD_Y)
            reader.addNoteToSelection(NOTE_TEXT)
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.SMALL))

            val noted = ProducedPdf.highlightsOn(produced, 0).firstOrNull { it.note.isNotBlank() }
                ?: throw AssertionError(
                    "The produced file carries no annotation with a note, it carries " +
                        ProducedPdf.highlightsOn(produced, 0)
                )
            assertEquals("the note text round trips into the document", NOTE_TEXT, noted.note)
            assertTrue("the annotation carries a creation date", noted.creationDate.isNotBlank())
            assertTrue(
                "the creation date is a PDF date string, it reads ${noted.creationDate}",
                PDF_DATE.matches(noted.creationDate),
            )
            assertTrue("the annotation carries a group key", noted.groupKey.isNotBlank())
        }
    }

    @Test
    @TestId("ART-07")
    fun theHighlightColourWrittenIntoTheFileIsTheExactPaletteValue() {
        val source = fixture(ArtifactFixtures.SMALL)
        val palette = HighlightPalette.defaultSelection

        openReader(source).use {
            palette.forEachIndexed { index, colour ->
                val verticalFraction = FIRST_WORD_Y + index * WORD_ROW_STEP
                reader.highlightWordOnCurrentPage(colour, ArtifactReader.DEFAULT_WORD_X, verticalFraction)
            }
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.SMALL))

            val annotations = ProducedPdf.highlightsOn(produced, 0)
            assertEquals(
                "one annotation per palette colour reaches the file",
                palette.size,
                annotations.size,
            )
            assertEquals(
                "the stored colours are the palette values exactly, alpha byte included",
                palette.map { it.colorValue }.sorted(),
                annotations.map { it.color }.sorted(),
            )
            assertTrue(
                "every annotation carries the text it was drawn over",
                annotations.all { it.quote.isNotBlank() },
            )
        }
    }

    @Test
    @TestId("ART-08")
    fun highlightsWrittenByAnotherAppSurviveOurSave() {
        val source = fixture(FOREIGN_HIGHLIGHTS)
        val recorded = ProducedPdf.allHighlights(source)
        assertTrue(
            "the fixture carries annotations written by another tool",
            recorded.isNotEmpty() && recorded.none { it.appOwned },
        )

        openReader(source).use {
            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(FOREIGN_HIGHLIGHTS))

            val saved = ProducedPdf.allHighlights(produced)
            recorded.forEach { original ->
                assertTrue(
                    "the pre-existing annotation quoting \"${original.quote}\" survives with its colour, " +
                        "the saved file carries $saved",
                    saved.any { it.quote == original.quote && it.color == original.color },
                )
            }
            assertEquals(
                "the saved file carries the pre-existing annotations plus the one that was added",
                recorded.size + 1,
                saved.size,
            )
            assertEquals(
                "the pre-existing annotations are still not app owned",
                recorded.size,
                saved.count { !it.appOwned },
            )
        }
    }

    @Test
    @TestId("ART-09")
    fun aDeletedHighlightIsReallyGoneFromTheSavedFile() {
        val source = fixture(ArtifactFixtures.SMALL)
        val firstCopy = ArtifactFixtures.annotatedNameFor(ArtifactFixtures.SMALL)
        val deletedQuote: String
        val recordedCount: Int

        openReader(source).use {
            reader.selectWordOnCurrentPage()
            deletedQuote = reader.copySelectionToClipboard()
            reader.highlightWordOnCurrentPage()
            reader.highlightWordOnCurrentPage(
                HighlightPalette.GREEN,
                ArtifactReader.DEFAULT_WORD_X,
                SECOND_WORD_Y,
            )
            val produced = saveCopyAs(firstCopy)
            recordedCount = ProducedPdf.highlightsOn(produced, 0).size
            assertTrue("the first save produced two annotations to choose between", recordedCount >= 2)
        }
        leaveReader()

        val carrying = java.io.File(downloads(), firstCopy)
        openReader(carrying).use {
            val pageRect = reader.state().pageRect
                ?: throw AssertionError("The reader reports no page rectangle to aim at")
            reader.tapHighlightAt(
                android.graphics.PointF(
                    pageRect.left + pageRect.width() * ArtifactReader.DEFAULT_WORD_X,
                    pageRect.top + pageRect.height() * ArtifactReader.DEFAULT_WORD_Y,
                )
            )
            reader.deleteActiveHighlight()
            val second = saveCopyAs(SECOND_COPY_NAME)

            val remaining = ProducedPdf.highlightsOn(second, 0)
            assertFalse(
                "no annotation in the saved file carries the deleted quote, it carries $remaining",
                remaining.any { it.quote == deletedQuote },
            )
            assertEquals(
                "the saved file carries one annotation fewer than before the deletion",
                recordedCount - 1,
                remaining.size,
            )
        }
    }

    @Test
    @TestId("ART-10")
    @TestDuration(DurationCategory.NORMAL)
    fun searchResultShadingNeverBecomesAnAnnotationInTheFile() {
        val source = copyFixtureToDownloads(ArtifactFixtures.SMALL, IN_PLACE_NAME)

        openReader(source).use {
            reader.selectWordOnCurrentPage()
            val highlighted = reader.copySelectionToClipboard()
            reader.highlightWordOnCurrentPage()
            SaveSearch.runSearchOnCurrentDocument("guard")
            val liveReader = ReaderRobot()
            val pageIndex = checkNotNull(liveReader.readState().page)
            SearchHighlight.awaitRectsOnPage(pageIndex)
            assertTrue("search shading is active before saving", liveReader.readState().markerPages?.contains(pageIndex) == true)
            saveInPlace()

            val annotations = ProducedPdf.allHighlights(source)
            assertTrue(
                "no annotation in the file is a search result, it carries $annotations",
                annotations.none { it.searchResult },
            )
            assertEquals("the document carries exactly one annotation", 1, annotations.size)
            assertEquals(
                "and it quotes the text the user highlighted",
                highlighted,
                annotations.single().quote,
            )
        }
    }

    @Test
    @TestId("ART-14")
    fun aSavedCopyOfAnEncryptedDocumentIsStillEncrypted() {
        val source = fixture(ArtifactFixtures.PROTECTED)
        val recordedDigest = ArtifactBytes.sha256(source)
        val sourcePages = ProducedPdf.pageCount(source, ArtifactFixtures.PROTECTED_PASSWORD)

        openProtectedReader(source, ArtifactFixtures.PROTECTED_PASSWORD).use {
            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.PROTECTED))

            assertTrue(
                "the produced copy refuses to open without a password",
                ProducedPdf.refusesWithoutPassword(produced),
            )
            assertEquals(
                "the produced copy opens with the correct password and keeps its page count",
                sourcePages,
                ProducedPdf.pageCount(produced, ArtifactFixtures.PROTECTED_PASSWORD),
            )
            assertEquals(
                "the decrypted copy carries the one app owned annotation",
                1,
                ProducedPdf.appOwnedHighlights(produced, ArtifactFixtures.PROTECTED_PASSWORD).size,
            )
            assertEquals(
                "the protected source is left byte identical",
                recordedDigest,
                ArtifactBytes.sha256(source),
            )
        }
    }

    @Test
    @TestId("ART-15")
    @TestDuration(DurationCategory.LONG)
    fun theOriginalFileIsByteIdenticalAfterAWholeEditingSession() {
        val source = fixture(ArtifactFixtures.MEDIUM)
        val recordedDigest = ArtifactBytes.sha256(source)
        val recordedLength = source.length()
        val recordedModified = source.lastModified()
        val sourcePages = ProducedPdf.pageCount(source)
        val produced = mutableListOf<java.io.File>()
        val preExistingPartialWrites = leftoverWritesInDownloads().toSet()

        openReader(source).use {
            repeat(SESSION_ROUNDS) { round ->
                reader.awaitSelectionActionSettled()
                reader.highlightWordOnCurrentPage(
                    HighlightPalette.YELLOW,
                    ArtifactReader.DEFAULT_WORD_X,
                    FIRST_WORD_Y + round * WORD_ROW_STEP,
                )
                reader.selectWordOnCurrentPage(SECOND_WORD_X, FIRST_WORD_Y + round * WORD_ROW_STEP)
                reader.addNoteToSelection("round $round")
                produced.add(saveCopyAs("session-$round.pdf"))
                reader.clearCurrentSaveDestination()
            }
        }

        assertEquals("the source digest is unchanged", recordedDigest, ArtifactBytes.sha256(source))
        assertEquals("the source length is unchanged", recordedLength, source.length())
        assertEquals("the source modification time is unchanged", recordedModified, source.lastModified())
        assertEquals("each round produced its own copy", SESSION_ROUNDS, produced.size)
        produced.forEach { copy ->
            assertEquals(
                "the copy ${copy.name} reparses with the source page count",
                sourcePages,
                ProducedPdf.pageCount(copy),
            )
        }
        val newPartialWrites = leftoverWritesInDownloads().filterNot(preExistingPartialWrites::contains)
        assertTrue(
            "this editing session must leave no new partial write in Downloads, it added $newPartialWrites",
            newPartialWrites.isEmpty(),
        )
    }

    @Test
    @TestId("ART-16")
    fun noStagingFileIsLeftInTheCacheAfterASave() {
        val source = fixture(ArtifactFixtures.MEDIUM)

        openReader(source).use {
            reader.highlightWordOnCurrentPage()
            saveCopyAs(ArtifactFixtures.annotatedNameFor(ArtifactFixtures.MEDIUM))

            assertEquals(
                "a successful save deletes its staging file in the finally block, the cache holds " +
                    AppDirectories.stagingLeftovers(),
                emptyList<String>(),
                AppDirectories.stagingLeftovers(),
            )
            AppDirectories.assertNoStagingLeftovers()
        }
    }

    private fun leftoverWritesInDownloads(): List<String> =
        AppDirectories.namesIn(downloads()).filter { it.endsWith(".saving") || it.endsWith(".part") }

    private companion object {
        const val NOTE_TEXT = "check this claim"
        const val FOREIGN_HIGHLIGHTS = "highlights-thirdparty.pdf"
        const val SECOND_COPY_NAME = "deleted-highlight.pdf"
        const val IN_PLACE_NAME = "in-place-search.pdf"
        const val FIRST_WORD_Y = 0.13f
        const val SECOND_WORD_Y = 0.19f
        const val SECOND_WORD_X = 0.6f
        const val WORD_ROW_STEP = 0.015f
        const val SESSION_ROUNDS = 3
        val PDF_DATE = Regex("D:\\d{14}.*")
    }
}
