// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class NotesExportTest : ListsTestCase() {

    @Test
    @TestId("LIST-38")
    fun theNotesExportWritesTheNotesFormat() {
        val name = exports.claimNotesOf(ListsFixtures.RICH_TITLE)
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openNotesAndScan()

            list.export()
            ExportPicker.saveIntoDownloadsKeepingName()
            Snackbars.awaitText(ListExport.exportedDoneMessage())

            val exported = ListExport.awaitWritten(name)
            ListExport.requireNoVariantBeside(name)

            val noted = ListsFixtures.richNotedGroups
            assertTrue(
                "line 3 reads \"${exported.lines[2]}\" rather than the note count and export date",
                COUNT_LINE.matches(exported.lines[2]),
            )
            assertEquals("one separator line per note", noted.size, exported.separatorLineCount())
            assertEquals("one marked note section per note", noted.size, ListExport.noteLines(exported).size)

            val colourNames = HighlightPalette.entries.map { list.string(it.nameRes) }
            ListExport.infoLines(exported).forEach { info ->
                assertTrue(
                    "the info line \"$info\" does not read as page, chapter and date",
                    INFO_LINE.matches(info),
                )
                colourNames.forEach { colour ->
                    assertFalse(
                        "the notes export named the colour $colour on the info line \"$info\", " +
                            "which is the highlights export's shape and not this one",
                        info.contains(colour),
                    )
                }
            }
        }
    }

    @Test
    @TestId("LIST-39")
    fun aNoteExportItemWithNoQuoteOmitsTheQuoteLine() {
        val name = exports.claimNotesOf(ListsFixtures.SCANNED_TITLE)
        openReader(ListsFixtures.SCANNED) { _, _ ->
            val list = openNotesAndScan()
            list.awaitItemCount(1)

            list.export()
            ExportPicker.saveIntoDownloadsKeepingName()
            Snackbars.awaitText(ListExport.exportedDoneMessage())

            val exported = ListExport.awaitWritten(name)
            ListExport.requireNoVariantBeside(name)

            assertEquals("separator lines", 1, exported.separatorLineCount())
            assertEquals(
                "the note content must be inside its marked section",
                ListsFixtures.SCANNED_NOTE,
                exported.blocks().single().notePayload(),
            )
            assertEquals(
                "a quote section was written for a highlight with no extractable text",
                emptyList<String>(),
                ListExport.quoteLines(exported),
            )
        }
    }

    private companion object {
        val COUNT_LINE = Regex("""3 notes · exported \d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
        val INFO_LINE = Regex("""Page \d+ \| .+ \| \d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
    }
}
