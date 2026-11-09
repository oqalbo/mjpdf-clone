// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
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
class HighlightsExportTest : ListsTestCase() {

    @Test
    @TestId("LIST-32")
    @TestDuration(DurationCategory.SHORT)
    fun theExportWritesTheDocumentedPlainTextFormat() {
        val name = exports.claimHighlightsOf(ListsFixtures.RICH_TITLE)
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            list.export()
            ExportPicker.saveIntoDownloadsKeepingName()
            Snackbars.awaitText(ListExport.exportedDoneMessage())

            val exported = ListExport.awaitWritten(name)
            ListExport.requireNoVariantBeside(name)

            assertEquals("line 1 is the document title", ListsFixtures.RICH_TITLE, exported.lines[0])
            assertEquals(
                "line 2 is the author line",
                list.string(R.string.export_by_author).format(ListsFixtures.RICH_AUTHOR),
                exported.lines[1],
            )
            assertTrue(
                "line 3 reads \"${exported.lines[2]}\" rather than the count and export date",
                COUNT_LINE.matches(exported.lines[2]),
            )
            assertEquals(
                "one separator line per highlight group",
                ListsFixtures.richGroups.size,
                exported.separatorLineCount(),
            )

            val first = ListsFixtures.richGroups.first()
            assertEquals(
                "the first item's info line",
                listOf(
                    "Page ${first.pageIndex + 1}",
                    ListsFixtures.PART_ONE,
                    first.rowDate,
                    list.string(HighlightPalette.YELLOW.nameRes),
                ).joinToString(" | "),
                exported.blocks().first().infoLine,
            )

            exported.blocks().forEachIndexed { index, block ->
                assertEquals(
                    "item block $index needs one quote opening and one closing mark, it reads ${block.lines}",
                    1,
                    block.lines.count { it == ListExport.quoteStart() },
                )
                assertEquals(
                    "item block $index never closes its quote, it reads ${block.lines}",
                    1,
                    block.lines.count { it == ListExport.quoteEnd() },
                )
            }

            assertEquals(
                "item blocks carrying a note section",
                ListsFixtures.richNotedGroups.size,
                exported.blocks().count { block -> block.notePayload() != null },
            )

            val nonPaletteInfo = ListExport.infoLines(exported)
                .single { it.startsWith("Page ${ListsFixtures.nonPaletteGroup.pageIndex + 1} |") }
            assertTrue(
                "the non palette item's info line reads \"$nonPaletteInfo\" rather than ending with its hex colour",
                nonPaletteInfo.endsWith(ListsFixtures.NON_PALETTE_CHIP),
            )
        }
    }

    @Test
    @TestId("LIST-33")
    fun theExportFallsBackToTheFileNameAndOmitsTheAuthorLine() {
        val name = exports.claimHighlightsOf(ListsFixtures.PLAIN)
        openReader(ListsFixtures.PLAIN) { _, _ ->
            val list = openHighlightsAndScan()

            list.export()
            val saved = ExportPicker.saveIntoDownloadsKeepingName()
            Snackbars.awaitText(ListExport.exportedDoneMessage())

            assertEquals("the name the picker was given", name, saved)
            val exported = ListExport.awaitWritten(name)
            ListExport.requireNoVariantBeside(name)

            assertEquals("line 1 falls back to the file name", ListsFixtures.PLAIN, exported.lines[0])
            assertTrue(
                "line 2 reads \"${exported.lines[1]}\" rather than the count line",
                PLAIN_COUNT_LINE.matches(exported.lines[1]),
            )
            assertFalse(
                "an author line was written for a document whose metadata carries no author",
                exported.lines.any { it.startsWith(BY_PREFIX) },
            )
            ListExport.infoLines(exported).forEach { info ->
                assertEquals(
                    "the info line \"$info\" carries more than the page and the colour, but this document has " +
                        "no outline and its highlights have no creation date",
                    1,
                    info.split(" | ").size - 1,
                )
            }
        }
    }

    @Test
    @TestId("LIST-34")
    fun theExportIgnoresTheActiveColourFilterAndSearchQuery() {
        val name = exports.claimHighlightsOf(ListsFixtures.RICH_TITLE)
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            list.tapChip(list.string(HighlightPalette.BLUE.nameRes))
            list.awaitItemCount(2)
            list.openSearch()
            list.typeQuery(ListsFixtures.WORD_IN_A_YELLOW_AND_A_BLUE_QUOTE)
            list.awaitItemCount(1)
            assertEquals(listOf("mj-group-02"), list.groupKeys())

            list.export()
            ExportPicker.saveIntoDownloadsKeepingName()
            Snackbars.awaitText(ListExport.exportedDoneMessage())

            val exported = ListExport.awaitWritten(name)
            ListExport.requireNoVariantBeside(name)

            assertTrue(
                "the header count line reads \"${exported.lines[2]}\" rather than the whole document's count",
                COUNT_LINE.matches(exported.lines[2]),
            )
            assertEquals(
                "one separator per highlight in the document, not per visible row",
                ListsFixtures.richGroups.size,
                exported.separatorLineCount(),
            )

            val filteredOutYellow = ListsFixtures.richGroups.first { it.color == ListsFixtures.YELLOW }
            assertTrue(
                "the export omits the quote of a yellow highlight the colour filter had hidden",
                exported.text.contains(filteredOutYellow.quote.lines().first()),
            )
        }
    }

    @Test
    @TestId("LIST-35")
    fun theExportFollowsTheDateSortWhenItIsEnabled() {
        val name = exports.claimHighlightsOf(ListsFixtures.RICH_TITLE)
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            list.sortByDate()
            list.awaitItemCount(ListsFixtures.richGroups.size)
            val onScreenOrder = list.allPagePills()

            list.export()
            ExportPicker.saveIntoDownloadsKeepingName()
            Snackbars.awaitText(ListExport.exportedDoneMessage())

            val exported = ListExport.awaitWritten(name)
            val writtenOrder = ListExport.infoLines(exported).map { it.substringBefore(" | ") }

            assertEquals(
                "the first item block is the newest highlight",
                ListsFixtures.newestGroup.pagePill,
                writtenOrder.first(),
            )
            assertEquals("the written order matches the order on screen", onScreenOrder, writtenOrder)
        }
    }

    @Test
    @TestId("LIST-37")
    fun cancellingTheExportPickerWritesNothing() {
        val name = exports.claimHighlightsOf(ListsFixtures.RICH_TITLE)
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            list.export()
            ExportPicker.awaitShown()
            assertEquals("the prefilled export name", name, ExportPicker.prefilledName())

            ExportPicker.cancel()
            list.awaitShown()

            Snackbars.assertNoneWithin()
            assertEquals(
                "files matching the export name in Downloads",
                emptyList<String>(),
                ListExport.variantsOf(name).map { it.name },
            )
            assertEquals("the list still shows every row", ListsFixtures.richGroups.size, list.itemCount())
        }
    }

    @Test
    @TestId("LIST-40")
    fun theExportIsUnreachableWhenThereIsNothingToExport() {
        openReader(ListsFixtures.NONE) { _, _ ->
            val list = openHighlightsAndScan()
            assertFalse(
                "the highlights overflow offers an export on an empty list",
                list.isOverflowItemShown(R.string.export),
            )
            assertFalse(
                "the highlights export menu item is visible",
                list.isMenuItemVisible(R.id.export_list),
            )
            list.pressBack()
            awaitBackInReader()

            val noteList = openNotesAndScan()
            assertFalse(
                "the notes overflow offers an export on an empty list",
                noteList.isOverflowItemShown(R.string.export),
            )
            assertFalse(
                "the notes export menu item is visible",
                noteList.isMenuItemVisible(R.id.export_list),
            )
        }
    }

    private companion object {
        val COUNT_LINE = Regex("""9 highlights · exported \d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
        val PLAIN_COUNT_LINE = Regex("""2 highlights · exported \d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
        const val BY_PREFIX = "by "
    }
}
