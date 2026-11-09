// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class BookmarkListTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val bookmarks = BookmarksRobot()

    @Test
    @TestId("BMK-04")
    fun renameABookmarkToACustomLabel() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        Bookmarks.seed(hash, 9)

        openList(BookmarksFixtures.NORMAL) {
            bookmarks.awaitRowCount(1)
            bookmarks.tapRename(0)
            bookmarks.typeLabel("Proof of lemma 3")
            bookmarks.applyRename()

            Await.untilValue(
                condition = "the bookmark row stores the new label",
                read = { Bookmarks.row(hash, 9) },
            ) { it?.label == "Proof of lemma 3" }

            bookmarks.awaitRowTitle(0, "Proof of lemma 3")
            val subtitle = bookmarks.rowSubtitle(0).orEmpty()
            assertTrue(
                "the subtitle must start with the page label, it reads \"$subtitle\"",
                subtitle.startsWith(bookmarks.pageLabel(10)),
            )
            assertEquals("the list still shows one row", 1, bookmarks.rowCount())
        }
    }

    @Test
    @TestId("BMK-05")
    fun renamingWithBlankTextClearsTheLabel() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        Bookmarks.seed(hash, 9, label = "Old name")

        openList(BookmarksFixtures.NORMAL) {
            bookmarks.awaitRowCount(1)
            bookmarks.longPressRow(0)
            bookmarks.typeLabel("   ")
            bookmarks.applyRename()

            Await.untilValue(
                condition = "the bookmark row clears its label",
                read = { Bookmarks.row(hash, 9) },
            ) { it != null && it.label == null }
            bookmarks.awaitRowTitle(0, bookmarks.pageLabel(10))
        }
    }

    @Test
    @TestId("BMK-06")
    @TestDuration(DurationCategory.NORMAL)
    fun cancellingTheRenameDialogChangesNothing() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        Bookmarks.seed(hash, 9, label = "Old name")

        openList(BookmarksFixtures.NORMAL) {
            bookmarks.awaitRowCount(1)
            bookmarks.tapRename(0)
            bookmarks.typeLabel("New name")
            bookmarks.cancelRename()

            Bookmarks.assertCountStays(hash, 1)
            assertEquals("a cancelled rename must not be written", "Old name", Bookmarks.row(hash, 9)?.label)
            assertEquals("the row title is unchanged", "Old name", bookmarks.rowTitle(0))
        }
    }

    @Test
    @TestId("BMK-07")
    fun deleteABookmarkFromTheList() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        Bookmarks.seedAll(
            listOf(
                UserBookmark(hash, 2, "Intro", LocalDateTime.now(), 0),
                UserBookmark(hash, 7, null, LocalDateTime.now(), 1),
            )
        )

        openList(BookmarksFixtures.NORMAL) {
            bookmarks.awaitRowCount(2)
            bookmarks.tapDelete(0)

            assertEquals(
                "the confirmation body names the row being deleted",
                TestEnvironment.targetContext.getString(R.string.delete_bookmark_confirm_message, "Intro"),
                bookmarks.confirmMessage(),
            )
            assertEquals(
                "the confirmation title",
                TestEnvironment.targetContext.getString(R.string.remove_bookmark),
                bookmarks.confirmTitle(),
            )

            bookmarks.confirmDelete()

            val rows = Bookmarks.awaitCount(hash, 1)
            assertEquals("the surviving row", 7, rows.single().pageIndex)
            bookmarks.awaitRowCount(1)
            assertTrue("the empty state stays hidden while a row remains", !bookmarks.emptyMessageShown())
        }
    }

    @Test
    @TestId("BMK-08")
    fun cancellingTheDeleteConfirmationKeepsTheBookmark() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        Bookmarks.seed(hash, 7)

        openList(BookmarksFixtures.NORMAL) {
            bookmarks.awaitRowCount(1)
            bookmarks.tapDelete(0)

            assertEquals(
                "an unlabelled row is named by its page label",
                TestEnvironment.targetContext.getString(
                    R.string.delete_bookmark_confirm_message,
                    bookmarks.pageLabel(8),
                ),
                bookmarks.confirmMessage(),
            )

            bookmarks.cancelDelete()

            Bookmarks.assertCountStays(hash, 1)
            assertEquals("the row survives a cancelled delete", 7, Bookmarks.row(hash, 7)?.pageIndex)
            assertEquals("the list still shows one row", 1, bookmarks.rowCount())
        }
    }

    @Test
    @TestId("BMK-09")
    fun listOrderIsSortOrderFirstThenPageIndex() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        Bookmarks.seedAll(
            listOf(
                UserBookmark(hash, 12, null, LocalDateTime.now(), 0),
                UserBookmark(hash, 3, null, LocalDateTime.now(), 1),
                UserBookmark(hash, 19, null, LocalDateTime.now(), 1),
                UserBookmark(hash, 1, null, LocalDateTime.now(), 2),
            )
        )

        openList(BookmarksFixtures.NORMAL) {
            bookmarks.awaitRowCount(4)
            assertEquals(
                "sort order ascending with the page index breaking the tie",
                listOf(
                    bookmarks.pageLabel(13),
                    bookmarks.pageLabel(4),
                    bookmarks.pageLabel(20),
                    bookmarks.pageLabel(2),
                ),
                bookmarks.rowTitles(),
            )
        }
    }

    @Test
    @TestId("BMK-10")
    @TestDuration(DurationCategory.NORMAL)
    fun dragReorderWritesAContiguousSortOrderAndSurvivesReopen() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        Bookmarks.seedAll(
            listOf(
                UserBookmark(hash, 1, null, LocalDateTime.now(), 0),
                UserBookmark(hash, 5, null, LocalDateTime.now(), 1),
                UserBookmark(hash, 9, null, LocalDateTime.now(), 2),
            )
        )

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()

            bookmarks.openFromReader()
            bookmarks.awaitRowCount(3)
            bookmarks.dragRowOnto(2, 0)
            bookmarks.awaitRowTitle(0, bookmarks.pageLabel(10))

            Await.untilValue(
                condition = "the reordered sort order reaches the database",
                read = { Bookmarks.of(hash).map { it.pageIndex } },
            ) { it == listOf(9, 1, 5) }

            val rows = Bookmarks.of(hash)
            assertEquals("the stored order", listOf(9, 1, 5), rows.map { it.pageIndex })
            assertEquals("the stored sort orders are contiguous", listOf(0, 1, 2), rows.map { it.sortOrder })

            bookmarks.pressBack()
            bookmarks.awaitClosed()
            bookmarks.openFromReader()
            bookmarks.awaitRowCount(3)
            assertEquals(
                "the reopened list keeps the arranged order",
                listOf(bookmarks.pageLabel(10), bookmarks.pageLabel(2), bookmarks.pageLabel(6)),
                bookmarks.rowTitles(),
            )
        }
    }

    @Test
    @TestId("BMK-11")
    fun tappingABookmarkJumpsTheReaderToThatPage() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.LARGE)
        Bookmarks.seed(hash, 30)

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.LARGE)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()

            bookmarks.openFromReader()
            bookmarks.awaitRowCount(1)
            bookmarks.awaitRowTitle(0, bookmarks.pageLabel(31))
            bookmarks.tapRow(0)

            bookmarks.awaitClosed()
            reader.awaitShown()
            reader.awaitPageIndex(30)
        }
    }

    @Test
    @TestId("BMK-16")
    fun emptyBookmarksList() {
        openList(BookmarksFixtures.NORMAL) {
            bookmarks.awaitRowCount(0)
            assertTrue("the empty state message is visible", bookmarks.emptyMessageShown())
            assertEquals(
                "the empty state text",
                TestEnvironment.targetContext.getString(R.string.no_user_bookmarks),
                bookmarks.emptyMessageText(),
            )
            assertEquals("the list holds no rows", 0, bookmarks.rowCount())
            assertEquals(
                "the screen title",
                TestEnvironment.targetContext.getString(R.string.bookmarks),
                bookmarks.title(),
            )
        }
    }

    @Test
    @TestId("BMK-21")
    fun bookmarkRowsShowTheTableOfContentsPath() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.TOC)
        Bookmarks.seed(hash, TOC_TWO_LEVEL_PAGE_INDEX)

        openList(BookmarksFixtures.TOC) {
            bookmarks.awaitRowCount(1)
            bookmarks.awaitRowTitle(0, TOC_TWO_LEVEL_PATH)

            assertEquals(
                "an unlabelled row shows the page label in its top row",
                bookmarks.pageLabel(TOC_TWO_LEVEL_PAGE_INDEX + 1),
                bookmarks.rowTopPage(0),
            )
            assertTrue(
                "an unlabelled row shows a relative time beside the page label",
                !bookmarks.rowTopTime(0).isNullOrBlank(),
            )
        }

        Bookmarks.seed(hash, TOC_TWO_LEVEL_PAGE_INDEX, label = "Named place")

        openList(BookmarksFixtures.TOC) {
            bookmarks.awaitRowCount(1)
            bookmarks.awaitRowTitle(0, "Named place")
            bookmarks.awaitRowSubtitle(0, TOC_TWO_LEVEL_PATH)
            assertTrue(
                "a labelled row with a path shows the page info line",
                !bookmarks.rowPageInfo(0).isNullOrBlank(),
            )
        }
    }

    private fun openList(fixture: String, block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(fixture)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()
            bookmarks.openFromReader()
            block()
        }
    }

    private companion object {
        const val TOC_TWO_LEVEL_PAGE_INDEX = 11
        const val TOC_TWO_LEVEL_PATH = "Background ▶ Prior work"
    }
}
