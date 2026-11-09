// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class BookmarkWriteTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val bookmarks = BookmarksRobot()

    @After
    fun releaseIdentityHold() {
        AnnotationTestHooks.reset()
    }

    @Test
    @TestId("BMK-01")
    @TestDuration(DurationCategory.SHORT)
    fun bookmarkingTheCurrentPageStoresThatPage() {
        val startedAt = LocalDateTime.now()
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            val hash = reader.awaitFileHashResolved()
            BookmarksReader.jumpToPage(reader, 4)

            BookmarksReader.toggleBookmark()

            val rows = Bookmarks.awaitTotalCount(1)
            val row = rows.single()
            assertEquals("the stored bookmark page index", 4, row.pageIndex)
            assertEquals("the stored bookmark file hash", hash, row.fileHash)
            assertEquals(
                "the stored hash must be the content identity of the fixture",
                BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL),
                row.fileHash,
            )
            assertNull("a bookmark created from the reader carries no label", row.label)
            assertEquals("the first bookmark of a document sorts first", 0, row.sortOrder)
            assertTrue(
                "the row was written by this test rather than seeded, createdAt ${row.createdAt}",
                row.createdAt.isAfter(startedAt),
            )
        }
    }

    @Test
    @TestId("BMK-02")
    @TestDuration(DurationCategory.SHORT)
    fun unbookmarkingTheCurrentPageRemovesTheRow() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        val otherHash = BookmarksIdentities.identityOf(BookmarksFixtures.LARGE)
        Bookmarks.seed(hash, 4)
        Bookmarks.seed(otherHash, 1)

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()
            BookmarksReader.jumpToPage(reader, 4)

            BookmarksReader.awaitBookmarkActionLabel(BookmarksReader.removeBookmarkLabel())
            BookmarksReader.toggleBookmark()

            Bookmarks.awaitCount(hash, 0)
            assertEquals(
                "removing a bookmark must not touch another document's rows",
                listOf(1),
                Bookmarks.pageIndexes(otherHash),
            )
        }
    }

    @Test
    @TestId("BMK-03")
    fun bookmarkingAPageTwiceNeverCreatesASecondRow() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        val createdAt = LocalDateTime.of(2024, 2, 3, 4, 5, 6)
        val sortOrder = 7

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()
            BookmarksReader.awaitBookmarkActionLabel(BookmarksReader.addBookmarkLabel())

            Bookmarks.seedAll(
                listOf(
                    UserBookmark(
                        fileHash = hash,
                        pageIndex = 0,
                        label = "Chapter one",
                        createdAt = createdAt,
                        sortOrder = sortOrder,
                    )
                )
            )
            BookmarksReader.toggleBookmark()
            val rows = Bookmarks.awaitStableCount(hash)

            assertEquals(
                "page index 0 must hold exactly one bookmark row, it holds ${rows.map { it.pageIndex }}",
                1,
                rows.count { it.pageIndex == 0 },
            )
            assertEquals("the document must hold exactly one bookmark row", 1, rows.size)
            assertEquals("the surviving row is the seeded page", 0, rows.single().pageIndex)
            assertEquals(
                "re-adding a bookmark for a page that already has one must keep the user written label",
                "Chapter one",
                rows.single().label,
            )
            assertEquals("re-adding must keep the original creation time", createdAt, rows.single().createdAt)
            assertEquals("re-adding must keep the original sort order", sortOrder, rows.single().sortOrder)
            assertEquals(
                "the reader action and the stored row must agree once both have settled",
                BookmarksReader.removeBookmarkLabel(),
                BookmarksReader.bookmarkActionLabel(),
            )
        }
    }

    @Test
    @TestId("BMK-12")
    fun bookmarksSurviveClosingAndReopeningTheDocument() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()
            BookmarksReader.jumpToPage(reader, 2)
            BookmarksReader.toggleBookmark()
            Bookmarks.awaitRow(hash, 2)
            BookmarksReader.jumpToPage(reader, 10)
            BookmarksReader.toggleBookmark()
            Bookmarks.awaitRow(hash, 10)
        }

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()

            val rows = Bookmarks.awaitCount(hash, 2)
            assertEquals("the stored page indexes", listOf(2, 10), rows.map { it.pageIndex })
            assertEquals(
                "both rows belong to the same document identity",
                setOf(hash),
                rows.map { it.fileHash }.toSet(),
            )

            BookmarksReader.jumpToPage(reader, 2)
            BookmarksReader.awaitBookmarkActionLabel(BookmarksReader.removeBookmarkLabel())

            bookmarks.openFromReader()
            bookmarks.awaitRowCount(2)
            assertEquals(
                "the reopened list order",
                listOf(bookmarks.pageLabel(3), bookmarks.pageLabel(11)),
                bookmarks.rowTitles(),
            )
        }
    }

    @Test
    @TestId("BMK-13")
    fun twoDocumentsSharingTheirFirstMegabyteKeepSeparateBookmarks() {
        val identityA = BookmarksIdentities.of(BookmarksFixtures.SHARED_PREFIX_A)
        val identityB = BookmarksIdentities.of(BookmarksFixtures.SHARED_PREFIX_B)

        assertTrue("the two fixtures must not share an identity", identityA.identity != identityB.identity)
        assertTrue("identity a carries the h2 identity prefix", identityA.identity.startsWith("h2"))
        assertTrue("identity b carries the h2 identity prefix", identityB.identity.startsWith("h2"))

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.SHARED_PREFIX_A)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()
            BookmarksReader.jumpToPage(reader, 1)
            BookmarksReader.toggleBookmark()
            Bookmarks.awaitRow(identityA.identity, 1)
        }

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.SHARED_PREFIX_B)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()
            BookmarksReader.jumpToPage(reader, 1)

            BookmarksReader.awaitBookmarkActionLabel(BookmarksReader.addBookmarkLabel())

            bookmarks.openFromReader()
            bookmarks.awaitRowCount(0)
            assertTrue("the second document shows the empty bookmarks state", bookmarks.emptyMessageShown())
            assertEquals(
                "the empty state text",
                TestEnvironment.targetContext.getString(R.string.no_user_bookmarks),
                bookmarks.emptyMessageText(),
            )

            val rows = Bookmarks.all()
            assertEquals("only the first document holds a bookmark", 1, rows.size)
            assertEquals("and it is keyed on the first document's identity", identityA.identity, rows.single().fileHash)
        }
    }

    @Test
    @TestId("BMK-14")
    @TestDuration(DurationCategory.SHORT)
    fun aLegacyKeyedBookmarkIsCarriedOverOnFirstOpen() {
        val identities = BookmarksIdentities.of(BookmarksFixtures.NORMAL)
        val uri = BookmarksFixtures.uri(BookmarksFixtures.NORMAL)
        Bookmarks.seedRecord(identities.legacy, uri, fileName = BookmarksFixtures.NORMAL)
        Bookmarks.seed(identities.legacy, 6, label = "Legacy mark")

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()

            val rows = Bookmarks.awaitCount(identities.identity, 1)
            assertEquals("the carried over page index", 6, rows.single().pageIndex)
            assertEquals("the carried over label", "Legacy mark", rows.single().label)
            assertTrue(
                "no bookmark may remain under the legacy hash",
                Bookmarks.of(identities.legacy).isEmpty(),
            )

            bookmarks.openFromReader()
            bookmarks.awaitRowCount(1)
            bookmarks.awaitRowTitle(0, "Legacy mark")
        }
    }

    @Test
    @TestId("BMK-15")
    fun bookmarksFollowTheFileToANewPath() {
        val source = Fixtures.copyDeviceFixture(BookmarksFixtures.NORMAL, rules.testDirectory.directory())
        val identity = BookmarksIdentities.of(source).identity

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(source)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()
            BookmarksReader.jumpToPage(reader, 2)
            BookmarksReader.toggleBookmark()
            Bookmarks.awaitRow(identity, 2)
            BookmarksReader.jumpToPage(reader, 8)
            BookmarksReader.toggleBookmark()
            Bookmarks.awaitRow(identity, 8)
        }

        val moved = File(rules.testDirectory.directory(), "moved-under-another-name.pdf")
        assertTrue("the fixture copy must move on device storage", source.renameTo(moved))
        Fixtures.scanAndWait(moved)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(moved)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()

            val rows = Bookmarks.awaitCount(identity, 2)
            assertEquals("the bookmarks follow the moved file", listOf(2, 8), rows.map { it.pageIndex })
            assertEquals("no second identity appeared", 2, Bookmarks.all().size)

            Records.awaitRow(
                identity,
                "the record for $identity points at the moved file",
            ) { it?.uri?.toString() == Uri.fromFile(moved).toString() }

            bookmarks.openFromReader()
            bookmarks.awaitRowCount(2)
            assertEquals(
                "the list titles after the move",
                listOf(bookmarks.pageLabel(3), bookmarks.pageLabel(9)),
                bookmarks.rowTitles(),
            )
        }
    }

    @Test
    @TestId("BMK-18")
    fun historySavingOffRefusesToAddButStillAllowsRemoving() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        Bookmarks.seed(hash, 4)

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()

            BookmarksReader.jumpToPage(reader, 7)
            BookmarksReader.toggleBookmark()
            Snackbars.awaitText(TestEnvironment.targetContext.getString(R.string.history_action_blocked))
            Bookmarks.assertCountStays(hash, 1)
            assertTrue(
                "no row may exist for the refused page",
                Bookmarks.row(hash, 7) == null,
            )

            BookmarksReader.jumpToPage(reader, 4)
            BookmarksReader.awaitBookmarkActionLabel(BookmarksReader.removeBookmarkLabel())
            BookmarksReader.toggleBookmark()
            Bookmarks.awaitEmpty()
        }
    }

    @Test
    @TestId("BMK-19")
    fun bookmarkingBeforeTheDocumentIdentityIsKnown() {
        val expectedHash = BookmarksIdentities.identityOf(BookmarksFixtures.LARGE)
        AnnotationTestHooks.holdIdentityResolution()

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.LARGE)).use {
            reader.awaitShown()
            reader.awaitState("the document is loading while its identity is unresolved") {
                it.loadState == DocumentLoadState.LOADING && it.fileHash.isNullOrEmpty()
            }
            ReaderProbe.awaitVisibility(R.id.progressBar, View.VISIBLE)

            BookmarksReader.toggleBookmark()
            Snackbars.awaitText(TestEnvironment.targetContext.getString(R.string.bookmark_hash_unavailable))
            Bookmarks.awaitEmpty()

            BookmarksReader.openAction(R.string.bookmarks)
            Snackbars.awaitText(TestEnvironment.targetContext.getString(R.string.bookmark_hash_unavailable))
            assertTrue(
                "the bookmarks screen must not start while the identity is unknown",
                !bookmarks.isShown(),
            )
            Records.assertHolds("identity-gated loading keeps progress visible and writes no rows") {
                ReaderProbe.visibility(R.id.progressBar) == View.VISIBLE &&
                    Records.rowCount() == 0 &&
                    Bookmarks.all().isEmpty()
            }

            AnnotationTestHooks.releaseIdentityResolution()
            reader.awaitState("the document loads with the expected identity", Await.LONG_TIMEOUT_MS) {
                it.loadState == DocumentLoadState.LOADED && it.fileHash == expectedHash
            }
            ReaderProbe.awaitVisibility(R.id.progressBar, View.GONE)
            ReaderProbe.assertVisibilityHolds(R.id.progressBar, View.GONE)

            BookmarksReader.awaitBookmarkActionLabel(BookmarksReader.addBookmarkLabel())
            BookmarksReader.toggleBookmark()
            val rows = Bookmarks.awaitCount(expectedHash, 1)
            assertEquals("the page bookmarked after the release", 0, rows.single().pageIndex)
        }
    }

    @Test
    @TestId("BMK-20")
    fun inDualPageModeOneBookmarkCoversTheVisibleSpread() {
        val hash = BookmarksIdentities.identityOf(BookmarksFixtures.NORMAL)
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = false, dualPage = true)

        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(BookmarksFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitFileHashResolved()
            reader.awaitState("the viewer resolved two pages per row") { it.pagesPerRow == 2 }

            BookmarksReader.jumpToPage(reader, 4)
            reader.awaitState("the spread of page index 4 is current") {
                it.page == 4 && it.rowFirstPage == 4 && it.rowLastPage == 5
            }

            BookmarksReader.toggleBookmark()
            val rows = Bookmarks.awaitCount(hash, 1)
            assertEquals("the stored page index is the left page of the spread", 4, rows.single().pageIndex)

            BookmarksReader.jumpToPage(reader, 5)
            BookmarksReader.awaitBookmarkActionLabel(BookmarksReader.removeBookmarkLabel())

            BookmarksReader.toggleBookmark()
            Bookmarks.awaitEmpty()
        }
    }
}
