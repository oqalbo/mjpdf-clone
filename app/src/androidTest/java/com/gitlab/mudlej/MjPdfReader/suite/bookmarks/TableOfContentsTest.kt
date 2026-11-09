// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.JumpBackSnackbars
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TableOfContentsTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val contents = TableOfContentsRobot()

    @Test
    @TestId("BMK-22")
    fun aMultiLevelTableOfContentsLoadsCollapsed() {
        openContents(BookmarksFixtures.TOC) {
            assertEquals(
                "the screen title once loading finished",
                TestEnvironment.targetContext.getString(R.string.table_of_contents),
                contents.title(),
            )
            contents.awaitRowCount(BookmarksFixtures.TOC_TOP_LEVEL_ENTRIES)

            assertTrue(
                "a row with children carries a clickable toggle",
                contents.rowActionState(0, R.id.toggleButton).clickable,
            )
            assertEquals(
                "the first top level entry",
                BookmarksFixtures.TOC_FIRST_ENTRY_TITLE,
                contents.rowTitle(0),
            )
            assertEquals(
                "each row shows its destination page one based",
                "1",
                contents.rowPageNumber(0),
            )

            assertTrue("the search icon is offered", contents.isSearchShown())
            assertTrue("Expand All is offered", contents.isOverflowItemShown(R.string.expand_all))
            assertTrue("Collapse All is offered", contents.isOverflowItemShown(R.string.collapse_all))

            val parentIndent = contents.rowIndentMarginStart(0)
            val parentTextSize = contents.rowTitleTextSize(0)
            contents.tapToggle(0)
            contents.awaitRowCount(
                BookmarksFixtures.TOC_TOP_LEVEL_ENTRIES + BookmarksFixtures.TOC_FIRST_ENTRY_CHILDREN
            )

            assertEquals(
                "a child row is indented by exactly one step",
                parentIndent + indentStep(),
                contents.rowIndentMarginStart(1),
            )
            assertTrue(
                "a child row is drawn no larger than its parent",
                contents.rowTitleTextSize(1) <= parentTextSize,
            )
        }
    }

    @Test
    @TestId("BMK-23")
    fun expandingAndCollapsingANode() {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.TOC)).use {
            reader.awaitReady()
            val pageBefore = reader.readState().page
            contents.openFromReader()
            contents.awaitRowCount(BookmarksFixtures.TOC_TOP_LEVEL_ENTRIES)

            contents.tapToggle(0)
            contents.awaitRowCount(
                BookmarksFixtures.TOC_TOP_LEVEL_ENTRIES + BookmarksFixtures.TOC_FIRST_ENTRY_CHILDREN
            )
            assertEquals(
                "the children are indented one step further than the parent",
                contents.rowIndentMarginStart(0) + indentStep(),
                contents.rowIndentMarginStart(1),
            )

            contents.tapToggle(0)
            contents.awaitRowCount(BookmarksFixtures.TOC_TOP_LEVEL_ENTRIES)

            contents.pressBack()
            contents.awaitClosed()
            reader.awaitShown()
            assertEquals("toggling a node must not navigate the reader", pageBefore, reader.readState().page)
        }
    }

    @Test
    @TestId("BMK-24")
    fun expandAllAndCollapseAll() {
        openContents(BookmarksFixtures.TOC_DEEP) {
            contents.awaitRowCount(BookmarksFixtures.TOC_DEEP_TOP_LEVEL_ENTRIES)

            contents.expandAll()
            contents.awaitRowCount(BookmarksFixtures.TOC_DEEP_TOTAL_ENTRIES, Await.VERY_LONG_TIMEOUT_MS)

            contents.collapseAll()
            contents.awaitRowCount(BookmarksFixtures.TOC_DEEP_TOP_LEVEL_ENTRIES)
        }

        openContents(BookmarksFixtures.TOC_FLAT) {
            contents.awaitRowCount(BookmarksFixtures.TOC_FLAT_TOP_LEVEL_ENTRIES)
            assertEquals(
                "the flat outline lists its entries",
                BookmarksFixtures.TOC_FLAT_FIRST_TITLE,
                contents.rowTitle(0),
            )
            assertTrue(
                "a leaf row carries no clickable toggle",
                !contents.rowActionState(0, R.id.toggleButton).clickable,
            )
            assertTrue(
                "Expand All is hidden when no entry has children",
                !contents.isOverflowItemShown(R.string.expand_all),
            )
            assertTrue(
                "Collapse All is hidden when no entry has children",
                !contents.isOverflowItemShown(R.string.collapse_all),
            )
        }
    }

    @Test
    @TestId("BMK-25")
    @TestDuration(DurationCategory.SHORT)
    fun jumpingToAContentsEntryLandsOnTheRightPage() {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.TOC)).use {
            reader.awaitReady()
            contents.openFromReader()
            contents.awaitRowCount(BookmarksFixtures.TOC_TOP_LEVEL_ENTRIES)

            val position = contents.positionOfTitle(BookmarksFixtures.TOC_RESULTS_TITLE)
            assertEquals(
                "the entry shows its destination page one based",
                (BookmarksFixtures.TOC_RESULTS_PAGE_INDEX + 1).toString(),
                contents.rowPageNumber(position),
            )
            contents.tapRowTitle(position)

            contents.awaitClosed()
            reader.awaitShown()
            reader.awaitPageIndex(BookmarksFixtures.TOC_RESULTS_PAGE_INDEX, Await.LONG_TIMEOUT_MS)

            val backText = TestEnvironment.targetContext.getString(R.string.back_to_table_of_contents)
            Snackbars.awaitText(backText)
            JumpBackSnackbars.tapMessage()
            contents.awaitLoaded()
            assertTrue("the snackbar action reopens the contents screen", contents.isShown())
        }
    }

    @Test
    @TestId("BMK-27")
    @TestDuration(DurationCategory.LONG)
    fun aDocumentWithNoTableOfContents() {
        openContents(BookmarksFixtures.NORMAL) {
            assertTrue("the empty state is visible", contents.messageShown())
            assertEquals(
                "the empty state text",
                TestEnvironment.targetContext.getString(R.string.no_table_of_contents),
                contents.messageText(),
            )
            assertEquals("no rows are listed", 0, contents.rowCount())
            assertTrue("the search icon stays hidden", !contents.isSearchShown())
            assertTrue("Locate me stays hidden", !contents.isLocateMeShown())
            assertTrue("Expand All stays hidden", !contents.isOverflowItemShown(R.string.expand_all))
            assertTrue("Collapse All stays hidden", !contents.isOverflowItemShown(R.string.collapse_all))
        }
    }

    @Test
    @TestId("BMK-28")
    fun anUnresolvableContentsEntryShowsNothingAndDoesNothing() {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.TOC_UNRESOLVED)).use {
            reader.awaitReady()
            BookmarksReader.jumpToPage(reader, BookmarksFixtures.TOC_UNRESOLVED_OPEN_PAGE_INDEX)
            contents.openFromReader()
            contents.awaitRowCount(BookmarksFixtures.TOC_UNRESOLVED_TOP_LEVEL_ENTRIES)

            val position = contents.positionOfTitle(BookmarksFixtures.TOC_UNRESOLVED_TITLE)
            assertEquals(
                "an unresolvable entry shows no page number at all",
                "",
                contents.rowPageNumber(position),
            )
            assertTrue(
                "an unresolvable entry is drawn at reduced alpha, it reads ${contents.rowTitleAlpha(position)}",
                contents.rowTitleAlpha(position) < 1f,
            )

            val inert = TableOfContentsRobot.RowActionState(
                clickable = false,
                focusable = false,
                hasAccessibilityClickAction = false,
            )
            listOf(
                R.id.bookmarkCard,
                R.id.bookmarkText,
                R.id.bookmarkPageNumber,
                R.id.toggleButton,
            ).forEach { viewId ->
                assertEquals(
                    "the unresolved row target ${BookmarksRows.resourceName(viewId)} is inert before recycling",
                    inert,
                    contents.rowActionState(position, viewId),
                )
            }

            val actionable = TableOfContentsRobot.RowActionState(
                clickable = true,
                focusable = true,
                hasAccessibilityClickAction = true,
            )
            listOf(R.id.bookmarkCard, R.id.bookmarkText, R.id.bookmarkPageNumber).forEach { viewId ->
                assertEquals(
                    "the resolvable row target ${BookmarksRows.resourceName(viewId)} is actionable",
                    actionable,
                    contents.rowActionState(0, viewId),
                )
            }
            contents.tapToggle(0)
            contents.awaitRowCount(10)
            val positionAfterFirstExpansion = contents.positionOfTitle(BookmarksFixtures.TOC_UNRESOLVED_TITLE)
            assertEquals(
                "expanding the first entry moves the unresolved row to position 9",
                9,
                positionAfterFirstExpansion,
            )
            contents.expandAll()
            contents.awaitRowCount(BookmarksFixtures.TOC_UNRESOLVED_TOTAL_ENTRIES)
            val recycledPosition = contents.positionOfTitle(BookmarksFixtures.TOC_UNRESOLVED_TITLE)
            assertEquals(
                "expanding the whole tree moves the unresolved row to the final adapter position",
                BookmarksFixtures.TOC_UNRESOLVED_TOTAL_ENTRIES - 1,
                recycledPosition,
            )
            contents.scrollTo(0)
            contents.awaitRowUnbound(recycledPosition)
            contents.scrollTo(recycledPosition)

            listOf(
                R.id.bookmarkCard,
                R.id.bookmarkText,
                R.id.bookmarkPageNumber,
                R.id.toggleButton,
            ).forEach { viewId ->
                assertEquals(
                    "the recycled unresolved target ${BookmarksRows.resourceName(viewId)} stays inert",
                    inert,
                    contents.rowActionState(recycledPosition, viewId),
                )
            }

            contents.tapRowTitle(recycledPosition)
            assertEquals("the unresolved row has no page-number target", "", contents.rowPageNumber(recycledPosition))
            contents.tapRow(recycledPosition)
            contents.tapToggle(recycledPosition)
            assertTrue("the contents screen stays in the foreground", contents.isShown())

            contents.pressBack()
            contents.awaitClosed()
            reader.awaitShown()
            assertEquals(
                "the reader never moved",
                BookmarksFixtures.TOC_UNRESOLVED_OPEN_PAGE_INDEX as Int?,
                reader.readState().page,
            )
        }
    }

    @Test
    @TestId("BMK-31")
    fun filteringTheContentsByTitleAndByPageNumber() {
        openContents(BookmarksFixtures.TOC) {
            contents.awaitRowCount(BookmarksFixtures.TOC_TOP_LEVEL_ENTRIES)

            contents.openSearch()
            contents.typeQuery(BookmarksFixtures.TOC_CASE_INSENSITIVE_QUERY)

            Await.until("the filtered tree lists the matching entries and their ancestors") {
                contents.rowTitles().contains(BookmarksFixtures.TOC_LOWERCASE_TITLE)
            }
            val filteredTitles = contents.rowTitles()
            assertTrue(
                "the case insensitive match is listed, the rows read $filteredTitles",
                filteredTitles.contains(BookmarksFixtures.TOC_LOWERCASE_TITLE),
            )
            assertTrue(
                "its ancestor chain is expanded without the user tapping, the rows read $filteredTitles",
                filteredTitles.contains(BookmarksFixtures.TOC_UPPERCASE_TITLE),
            )
            assertTrue(
                "the toggle arrows are inert while filtering",
                !contents.rowActionState(0, R.id.toggleButton).clickable,
            )

            contents.typeQuery((BookmarksFixtures.TOC_RESULTS_PAGE_INDEX + 1).toString())
            Await.until("the page number query lists the entry whose page cell contains it") {
                contents.rowTitles().contains(BookmarksFixtures.TOC_RESULTS_TITLE)
            }

            contents.closeSearch()
            contents.awaitRowCount(BookmarksFixtures.TOC_TOP_LEVEL_ENTRIES)
            Await.until("the contents search is collapsed") { !contents.isSearchExpanded() }
            contents.awaitImeHidden()
        }
    }

    @Test
    @TestId("BMK-26")
    fun contentsExpansionAndScrollSurviveTheRoundTrip() {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.TOC_DEEP)).use {
            reader.awaitReady()
            contents.openFromReader()
            contents.awaitRowCount(BookmarksFixtures.TOC_DEEP_TOP_LEVEL_ENTRIES)

            TOC_DEEP_EXPANDED_NODES.forEach { title ->
                contents.tapToggle(contents.positionOfTitle(title))
            }
            val expandedRowCount = contents.rowCount()
            assertTrue(
                "expanding three top level nodes must add rows, the list holds $expandedRowCount",
                expandedRowCount > BookmarksFixtures.TOC_DEEP_TOP_LEVEL_ENTRIES,
            )

            val anchorTitle = contents.rowTitle(0)
            contents.scrollTo(SCROLL_ROWS)
            val anchorPosition = contents.firstCompletelyVisiblePosition()

            val jumpPosition = contents.firstCompletelyVisiblePosition()
            val jumpTitle = contents.rowTitle(jumpPosition)
            contents.tapRowTitle(jumpPosition)
            contents.awaitClosed()
            reader.awaitShown()

            val backText = TestEnvironment.targetContext.getString(R.string.back_to_table_of_contents)
            Snackbars.awaitText(backText)
            JumpBackSnackbars.tapMessage()
            contents.awaitLoaded()

            contents.awaitRowCount(expandedRowCount)
            assertEquals(
                "the reopened list restores the recorded scroll anchor",
                anchorPosition,
                contents.firstCompletelyVisiblePosition(),
            )

            contents.scrollTo(0)
            assertEquals("the row at adapter position 0 is unchanged", anchorTitle, contents.rowTitle(0))

            val currentChainPosition = contents.positionOfTitle(jumpTitle.orEmpty())
            assertEquals(
                "the entry just jumped to is highlighted as the current chain",
                secondaryContainerColor(),
                contents.rowCardColor(currentChainPosition),
            )
        }
    }

    @Test
    @TestId("BMK-29")
    @TestDuration(DurationCategory.NORMAL)
    fun locateMeScrollsToTheEntryForTheCurrentPage() {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.TOC_DEEP)).use {
            reader.awaitReady()
            BookmarksReader.jumpToPage(reader, BookmarksFixtures.TOC_DEEP_LEVEL_THREE_PAGE_INDEX)
            contents.openFromReader()
            contents.awaitRowCount(BookmarksFixtures.TOC_DEEP_TOP_LEVEL_ENTRIES)
            assertTrue("Locate me is offered while an entry matches the page", contents.isLocateMeShown())
            val chain = BookmarksFixtures.TOC_DEEP_LEVEL_THREE_PATH.split(PATH_SEPARATOR)
            val targetTitle = chain.last()
            contents.expandAll()
            contents.awaitRowCount(BookmarksFixtures.TOC_DEEP_TOTAL_ENTRIES)
            contents.tapToggle(0)
            Await.until("the target's ancestor is collapsed") { targetTitle !in contents.adapterTitles() }
            contents.scrollTo(contents.rowCount() - 1)
            assertTrue("the target's ancestor must begin off screen", !contents.isVisible(0))

            contents.locateMe()

            Await.until("the located entry is listed once its ancestors expanded") {
                contents.adapterTitles().contains(targetTitle)
            }
            val targetPosition = contents.adapterTitles().indexOf(targetTitle)
            Await.until("Locate me brings the target into view") { contents.isVisible(targetPosition) }
            assertTrue("the located row is on screen", contents.isVisible(targetPosition))

            val highlight = secondaryContainerColor()
            chain.forEach { title ->
                val position = contents.positionOfTitle(title)
                assertEquals(
                    "the row \"$title\" on the current chain is drawn with the secondary container colour",
                    highlight,
                    contents.rowCardColor(position),
                )
            }

            val offChainPosition = contents.positionOfTitle(TOC_DEEP_OFF_CHAIN_TITLE)
            assertTrue(
                "a row off the current chain keeps the default card colour",
                contents.rowCardColor(offChainPosition) != highlight,
            )
        }
    }

    @Test
    @TestId("BMK-32")
    fun rotationPreservesContentsState() {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.TOC_DEEP)).use {
            reader.awaitReady()
            contents.openFromReader()
            contents.awaitRowCount(BookmarksFixtures.TOC_DEEP_TOP_LEVEL_ENTRIES)

            TOC_DEEP_EXPANDED_NODES.forEach { title ->
                contents.tapToggle(contents.positionOfTitle(title))
            }
            val expandedRowCount = contents.rowCount()
            contents.scrollTo(SCROLL_ROWS)
            val firstVisibleBefore = contents.firstVisiblePosition()

            contents.openSearch()
            contents.typeQuery(TOC_DEEP_QUERY)
            Await.until("the query filters the tree") { contents.rowCount() < expandedRowCount }
            val filteredRowCount = contents.rowCount()

            Rotation.toLandscape(TableOfContentsActivity::class.java)
            contents.awaitLoaded()

            assertEquals("the query text survives the rotation", TOC_DEEP_QUERY, contents.queryText())
            contents.awaitRowCount(filteredRowCount)

            contents.closeSearch()
            contents.awaitRowCount(expandedRowCount)
            Await.until("the rotated contents search is collapsed") { !contents.isSearchExpanded() }
            contents.awaitImeHidden()
            assertEquals(
                "clearing the query restores the same expanded tree",
                expandedRowCount,
                contents.rowCount(),
            )
            assertEquals(
                "the first visible row is the one recorded before the rotation",
                firstVisibleBefore,
                contents.firstVisiblePosition(),
            )
        }
    }

    private fun secondaryContainerColor(): Int = TestEnvironment.onMainThread {
        val resolved = TypedValue()
        contents.activity().theme.resolveAttribute(
            com.google.android.material.R.attr.colorSecondaryContainer,
            resolved,
            true,
        )
        if (resolved.resourceId != 0) {
            ContextCompat.getColor(contents.activity(), resolved.resourceId)
        } else {
            resolved.data
        }
    }

    private fun indentStep(): Int =
        TestEnvironment.targetContext.resources.getDimensionPixelSize(R.dimen.bookmark_indent_step)

    private fun openContents(fixture: String, block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(fixture)).use {
            reader.awaitReady()
            contents.openFromReader()
            block()
        }
    }

    private companion object {
        const val SCROLL_ROWS = 10
        const val PATH_SEPARATOR = " ▶ "
        const val TOC_DEEP_OFF_CHAIN_TITLE = "Part 2"
        const val TOC_DEEP_QUERY = "Subsection 1.1"
        val TOC_DEEP_EXPANDED_NODES = listOf("Part 1", "Part 2", "Part 3")
    }
}
