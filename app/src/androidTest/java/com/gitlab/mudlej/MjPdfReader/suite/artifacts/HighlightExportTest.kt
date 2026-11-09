// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class HighlightExportTest : ArtifactTestCase() {

    @Test
    @TestId("ART-25")
    @TestDuration(DurationCategory.SHORT)
    fun theExportedHighlightsFileIsUtf8WithNoByteOrderMarkAndLineFeedEndings() {
        val flow = ExportFlow(ExportKind.HIGHLIGHTS)
        val name = flow.expectedFileName(RICH_TITLE)

        flow.launch(fixture(ArtifactFixtures.HIGHLIGHTS_RICH)).use {
            flow.awaitScanFinished()
            flow.awaitRowCount(RICH_HIGHLIGHTS)
            val written = ownVariants(flow.exportExactlyInto(downloads(), name), name)
            val bytes = written.readBytes()

            assertFalse(
                "the export must not open with a byte order mark",
                ArtifactBytes.startsWith(written, ArtifactBytes.UTF8_BOM),
            )
            assertEquals(
                "the export must carry no carriage return, so its line endings are line feeds alone",
                0,
                bytes.count { it == CARRIAGE_RETURN },
            )
            assertTrue(
                "the export must decode as UTF-8 in strict mode",
                ExportFile.decodesStrictly(bytes),
            )
            val export = ExportFile(written)
            assertFalse(
                "a strict decode that produced a replacement character means bytes were lost",
                export.hasReplacementCharacter(),
            )
            assertEquals(
                "the export must end with a line feed",
                LINE_FEED,
                bytes.last(),
            )
        }
    }

    @Test
    @TestId("ART-26")
    @TestDuration(DurationCategory.SHORT)
    fun theExportedItemCountAgreesWithTheHeaderTheSeparatorsAndTheList() {
        val flow = ExportFlow(ExportKind.HIGHLIGHTS)
        val name = flow.expectedFileName(RICH_TITLE)

        flow.launch(fixture(ArtifactFixtures.HIGHLIGHTS_RICH)).use {
            flow.awaitScanFinished()
            val rows = flow.awaitRowCount(RICH_HIGHLIGHTS)
            val written = ownVariants(flow.exportExactlyInto(downloads(), name), name)
            val export = ExportFile(written)
            val blocks = export.blocks()

            assertEquals(
                "the list shows one row per highlight group in the fixture",
                RICH_HIGHLIGHTS,
                rows,
            )
            assertEquals(
                "the header count line must read $RICH_HIGHLIGHTS highlights, the file reads " +
                    export.lines.take(HEADER_LINES_READ),
                1,
                export.linesStartingWith(highlightCountPrefix(RICH_HIGHLIGHTS)).size,
            )
            assertEquals(
                "the file must carry one separator line per exported item",
                RICH_HIGHLIGHTS,
                export.separatorLineCount(),
            )
            assertEquals(
                "every separator must be followed by an info line, so the parsed block count is the item count",
                RICH_HIGHLIGHTS,
                blocks.size,
            )
            assertEquals(
                "every block must open with a page info line",
                RICH_HIGHLIGHTS,
                blocks.count { it.infoLine.startsWith(pageLabelPrefix()) },
            )
            assertTrue(
                "each quote and note must have matching boundaries, the blocks read " +
                    blocks.map { it.lines },
                blocks.all { block ->
                    block.lines.count { it == sectionStart(quoteLabel()) } <= 1 &&
                        block.lines.count { it == sectionEnd(quoteLabel()) } ==
                        block.lines.count { it == sectionStart(quoteLabel()) } &&
                        block.lines.count { it == sectionEnd(noteLabel()) } ==
                        block.lines.count { it == sectionStart(noteLabel()) }
                },
            )
            assertEquals(
                "the fixture's three noted groups must each carry one note section",
                RICH_NOTES,
                blocks.count { it.notePayload() != null },
            )
        }
    }

    @Test
    @TestId("ART-27")
    fun aQuoteThatWrapsAcrossTwoLinesDoesNotBreakTheItemBlock() {
        val document = copyFixtureToDownloads(ArtifactFixtures.WRAPPED_QUOTE, WRAPPED_COPY_NAME)

        openReader(document).use {
            reader.selectWordOnCurrentPage(WRAPPED_SENTENCE_X, WRAPPED_SENTENCE_Y)
            reader.extendSelectionBy(WRAPPED_DRAG_X, WRAPPED_DRAG_Y)
            reader.highlightSelection()
            saveInPlace()
        }
        leaveReader()

        val stored = ProducedPdf.highlightsOn(document, 0)
        assertTrue(
            "the selection must span the rendered line break, otherwise this test is not exercising a " +
                "wrapped quote at all. The stored quotes read " + stored.map { it.quote },
            stored.any { it.quote.contains("\n") },
        )

        val flow = ExportFlow(ExportKind.HIGHLIGHTS)
        val name = flow.expectedFileName(WRAPPED_COPY_NAME)
        flow.launch(document).use {
            flow.awaitScanFinished()
            flow.awaitRowCount(1)
            val written = ownVariants(flow.exportExactlyInto(downloads(), name), name)
            val export = ExportFile(written)
            val block = export.blocks().singleOrNull()
                ?: throw AssertionError("The export holds ${export.blocks().size} blocks rather than one")

            assertEquals(
                "the header count line must read 1 highlights",
                1,
                export.linesStartingWith(highlightCountPrefix(1)).size,
            )
            assertEquals("the file must carry exactly one item separator", 1, export.separatorLineCount())
            assertEquals(
                "the exported quote keeps the wrapped line break inside its marked section",
                stored.first { it.quote.contains("\n") }.quote.trim(),
                block.section(quoteLabel()),
            )
            assertEquals(
                "exactly one quote section opens the block",
                1,
                block.lines.count { it == sectionStart(quoteLabel()) },
            )
            assertEquals(
                "exactly one quote section closes the block",
                1,
                block.lines.count { it == sectionEnd(quoteLabel()) },
            )
        }
    }

    @Test
    @TestId("ART-28")
    fun aNoteTypedAcrossTwoLinesDoesNotBreakTheItemBlock() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, NOTE_COPY_NAME)

        openReader(document).use {
            reader.selectWordOnCurrentPage()
            reader.addNoteToSelection(TWO_LINE_NOTE)
            saveInPlace()
        }
        leaveReader()

        assertTrue(
            "the stored note keeps the typed line feed, the stored notes read " +
                ProducedPdf.highlightsOn(document, 0).map { it.note },
            ProducedPdf.highlightsOn(document, 0).any { it.note.contains("\n") },
        )

        val flow = ExportFlow(ExportKind.NOTES)
        val name = flow.expectedFileName(NOTE_COPY_NAME)
        flow.launch(document).use {
            flow.awaitScanFinished()
            flow.awaitRowCount(1)
            val written = ownVariants(flow.exportExactlyInto(downloads(), name), name)
            val export = ExportFile(written)

            assertEquals(
                "the header count line must read 1 notes",
                1,
                export.linesStartingWith(noteCountPrefix(1)).size,
            )
            assertEquals(
                "exactly one section must open the note",
                1,
                export.lines.count { it == sectionStart(noteLabel()) },
            )
            assertEquals(
                "the exported note keeps both lines inside its marked section",
                TWO_LINE_NOTE,
                export.blocks().single().notePayload(),
            )
            assertEquals(
                "exactly one section must close the note",
                1,
                export.lines.count { it == sectionEnd(noteLabel()) },
            )
            assertEquals("the file must carry exactly one separator line", 1, export.separatorLineCount())
        }
    }

    @Test
    @TestId("ART-29")
    fun aNoteContainingTheSeparatorStringDoesNotCreateAPhantomItem() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, SEPARATOR_COPY_NAME)

        openReader(document).use {
            reader.selectWordOnCurrentPage()
            reader.addNoteToSelection(ExportFile.SEPARATOR)
            reader.selectWordOnCurrentPage(SECOND_WORD_X, SECOND_WORD_Y)
            reader.addNoteToSelection(PLAIN_NOTE)
            saveInPlace()
        }
        leaveReader()

        val flow = ExportFlow(ExportKind.NOTES)
        val name = flow.expectedFileName(SEPARATOR_COPY_NAME)
        flow.launch(document).use {
            flow.awaitScanFinished()
            flow.awaitRowCount(2)
            val written = ownVariants(flow.exportExactlyInto(downloads(), name), name)
            val export = ExportFile(written)

            assertEquals(
                "the header count line must read 2 notes",
                1,
                export.linesStartingWith(noteCountPrefix(2)).size,
            )
            assertEquals(
                "the separator typed inside a note must not create a third item",
                2,
                export.blocks().size,
            )
            assertEquals(
                "only the two item separators count outside marked note sections",
                2,
                export.separatorLineCount(),
            )
            assertTrue(
                "the note keeps the forty hyphens the user typed",
                export.blocks().any { it.notePayload() == ExportFile.SEPARATOR },
            )
        }
    }

    @Test
    @TestId("ART-30")
    fun specialCharactersInExportedContentSurviveTheExportCodePointForCodePoint() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, SPECIAL_COPY_NAME)

        openReader(document).use {
            reader.selectWordOnCurrentPage()
            reader.addNoteToSelection(SPECIAL_NOTE)
            saveInPlace()
        }
        leaveReader()

        val stored = ProducedPdf.highlightsOn(document, 0).firstOrNull { it.note.isNotBlank() }
            ?: throw AssertionError("The saved document carries no annotation with a note")
        assertEquals(
            "the special characters must reach the document unchanged before the export can be blamed for them",
            SPECIAL_NOTE,
            stored.note,
        )

        val flow = ExportFlow(ExportKind.NOTES)
        val name = flow.expectedFileName(SPECIAL_COPY_NAME)
        flow.launch(document).use {
            flow.awaitScanFinished()
            flow.awaitRowCount(1)
            val written = ownVariants(flow.exportExactlyInto(downloads(), name), name)
            val export = ExportFile(written)
            val block = export.blocks().single()
            val payload = block.notePayload()
            val quotePayload = block.quotePayload()

            assertEquals(
                "the exported note must equal the stored note code point for code point",
                SPECIAL_NOTE,
                payload,
            )
            assertTrue(
                "the emoji must survive as its surrogate pair rather than as a question mark or a " +
                    "replacement character",
                payload!!.contains(EMOJI),
            )
            assertTrue(
                "the right to left mark must survive unchanged",
                payload.contains(RIGHT_TO_LEFT_MARK),
            )
            assertEquals(
                "no backslash escaping may be introduced, the payload must carry the single backslash typed",
                1,
                payload.count { it == '\\' },
            )
            assertEquals(
                "the exported quote must equal the quote stored in the document",
                stored.quote,
                quotePayload,
            )
        }
    }

    @Test
    @TestId("ART-31")
    @TestDuration(DurationCategory.SHORT)
    fun exportingOverALongerExistingFileLeavesNoTrailingBytes() {
        val flow = ExportFlow(ExportKind.HIGHLIGHTS)
        val name = flow.expectedFileName(RICH_TITLE)
        val target = File(downloads(), name)
        ExportFlow.deleteAllVariants(downloads(), name)
        Fixtures.pushBytes(staleContent(), target)
        cleanup.own(target)
        assertTrue(
            "the destination must start longer than any export this test can produce",
            target.length() >= STALE_CONTENT_BYTES,
        )

        flow.launch(fixture(ArtifactFixtures.HIGHLIGHTS_RICH)).use {
            flow.awaitScanFinished()
            flow.awaitRowCount(RICH_HIGHLIGHTS)
            try {
                StubSharerProvider.clear()
                val destinationUri = StubSharerProvider.registerWritableDocumentPath(
                    CONTROLLED_EXPORT_KEY,
                    target.absolutePath,
                )
                assertTrue(
                    "the controlled destination must be a document URI",
                    DocumentsContract.isDocumentUri(TestEnvironment.targetContext, destinationUri),
                )
                assertEquals(
                    "the controlled picker result must resolve to the seeded collision target",
                    target.canonicalFile,
                    StubSharerProvider.resolveFile(CONTROLLED_EXPORT_KEY)?.canonicalFile,
                )

                Intents.init()
                try {
                    IntentStubs.stubUriResultFor(Intent.ACTION_CREATE_DOCUMENT, destinationUri)
                    flow.exportToStubbedDocument()
                    intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
                } finally {
                    Intents.release()
                }
            } finally {
                StubSharerProvider.clear()
            }

            val export = ExportFile(target)
            assertFalse(
                "no byte of the older, longer file may survive the export",
                ArtifactBytes.containsText(target, STALE_MARKER),
            )
            assertEquals("the file must end with a line feed", LINE_FEED, export.bytes.last())
            assertEquals(
                "the header count line must read $RICH_HIGHLIGHTS highlights",
                1,
                export.linesStartingWith(highlightCountPrefix(RICH_HIGHLIGHTS)).size,
            )
        }
    }

    private fun ownVariants(written: File, name: String): File {
        cleanup.ownAll(ExportFlow.variantsOf(downloads(), name))
        return written
    }

    private fun staleContent(): ByteArray {
        val builder = StringBuilder()
        while (builder.length < STALE_CONTENT_BYTES) {
            builder.append(STALE_MARKER).append('\n')
        }
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private fun highlightCountPrefix(count: Int): String =
        string(R.string.export_highlights_line).format(count, "")

    private fun noteCountPrefix(count: Int): String =
        string(R.string.export_notes_line).format(count, "")

    private fun quoteLabel(): String = string(R.string.export_quote_label)

    private fun noteLabel(): String = string(R.string.note)

    private fun sectionStart(label: String): String = "--- $label Start"

    private fun sectionEnd(label: String): String = "--- $label End"

    private fun pageLabelPrefix(): String =
        TestEnvironment.targetContext.resources.getString(R.string.bookmark_page_label).substringBefore("%1")

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val RICH_TITLE = "The Memory Model"
        const val RICH_HIGHLIGHTS = 9
        const val RICH_NOTES = 3

        const val WRAPPED_COPY_NAME = "art27-wrapped-quote.pdf"
        const val NOTE_COPY_NAME = "art28-two-line-note.pdf"
        const val SEPARATOR_COPY_NAME = "art29-separator-note.pdf"
        const val SPECIAL_COPY_NAME = "art30-special-characters.pdf"

        const val WRAPPED_SENTENCE_X = 0.25f
        const val WRAPPED_SENTENCE_Y = 0.17f
        const val WRAPPED_DRAG_X = 220f
        const val WRAPPED_DRAG_Y = 44f

        const val SECOND_WORD_X = 0.6f
        const val SECOND_WORD_Y = 0.19f

        const val NOTE_SECOND_LINE = "second line"
        const val TWO_LINE_NOTE = "first line\n$NOTE_SECOND_LINE"
        const val PLAIN_NOTE = "an ordinary note"

        const val EMOJI = "\uD83D\uDCD5"
        const val RIGHT_TO_LEFT_MARK = "\u200F"
        const val SPECIAL_NOTE = "“ ” \\ \t $EMOJI ${RIGHT_TO_LEFT_MARK}end"

        const val STALE_MARKER = "OLD CONTENT"
        const val STALE_CONTENT_BYTES = 200 * 1024
        const val CONTROLLED_EXPORT_KEY = "art-31-existing-export.txt"

        const val CARRIAGE_RETURN: Byte = 0x0D
        const val LINE_FEED: Byte = 0x0A

        const val HEADER_LINES_READ = 3
    }
}
