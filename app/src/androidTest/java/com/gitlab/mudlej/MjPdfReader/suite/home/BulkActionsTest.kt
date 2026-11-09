// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import android.graphics.RectF
import android.graphics.Color
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class BulkActionsTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-45")
    fun bulkStatusWritesEverySelectedRecordAndOnlyThose() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
            BookSeed(HomeFixtures.BETA, pages = 1),
            BookSeed(HomeFixtures.GAMMA, pages = 1),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta", "gamma"))

        grid.cell("alpha").performLongClickForSetupAndAwaitChecked()
        home.awaitSelectionStarted()
        grid.cell("beta").click()
        home.awaitSelectionTitle(selectedCount(2))

        home.tapSelectionAction(R.id.statusBatchOption)
        HomeDialogs.chooseListItem(ON_HOLD_LABEL)

        HomeLibrary.awaitReading(library.hashOf("alpha"), ReadingStatus.ON_HOLD)
        HomeLibrary.awaitReading(library.hashOf("beta"), ReadingStatus.ON_HOLD)
        assertEquals(
            "an unselected book keeps its status",
            ReadingStatus.UNSET,
            HomeLibrary.requireRecord(library.hashOf("gamma")).reading,
        )
        home.awaitSelectionEnded()
    }

    @Test
    @TestId("HOME-46")
    fun bulkHideWritesTheHiddenColumnAndClearsTheGridRows() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
            BookSeed(HomeFixtures.BETA, pages = 1),
            BookSeed(HomeFixtures.GAMMA, pages = 1),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta", "gamma"))

        grid.cell("alpha").performLongClickForSetupAndAwaitChecked()
        home.awaitSelectionStarted()
        grid.cell("beta").click()
        home.awaitSelectionTitle(selectedCount(2))

        home.tapSelectionAction(R.id.hideBatchOption)

        HomeLibrary.awaitHidden(library.hashOf("alpha"), true)
        HomeLibrary.awaitHidden(library.hashOf("beta"), true)
        assertTrue(
            "an unselected book stays visible",
            !HomeLibrary.requireRecord(library.hashOf("gamma")).hidden,
        )
        grid.awaitTitles(listOf("gamma"))
        library.books.forEach {
            assertTrue("hiding must not delete ${it.fileName}", it.file.exists())
        }
    }

    @Test
    @TestId("HOME-47")
    fun bulkRemoveFromRecentResetsLastOpenedWithoutTouchingTheFile() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pageNumber = 4, pages = 10, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, pages = 10, lastOpened = daysAgo(1)),
            BookSeed(HomeFixtures.GAMMA, pages = 10, lastOpened = daysAgo(2)),
        )

        home.launchFromLauncher()

        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitTitles(listOf("alpha", "beta", "gamma"))

        recent.cell("alpha").performLongClickForSetupAndAwaitChecked()
        home.awaitSelectionStarted()
        recent.cell("beta").click()
        home.awaitSelectionTitle(selectedCount(2))

        home.tapSelectionAction(R.id.removeRecentBatchOption)

        val alpha = HomeLibrary.awaitNeverOpened(library.hashOf("alpha"))
        HomeLibrary.awaitNeverOpened(library.hashOf("beta"))
        assertEquals("removing from Recent must not move the reading position", 4, alpha.pageNumber)
        assertEquals("removing from Recent must not change the status", ReadingStatus.UNSET, alpha.reading)

        recent.awaitTitles(listOf("gamma"))
        home.selectTab(HomeTab.LIBRARY).awaitTitles(listOf("alpha", "beta", "gamma"))
    }

    @Test
    @TestId("HOME-48")
    @TestDuration(DurationCategory.NORMAL)
    fun cancellingTheBulkDeleteConfirmationChangesNothing() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
            BookSeed(HomeFixtures.BETA, pages = 1),
            BookSeed(HomeFixtures.GAMMA, pages = 1),
        )

        home.launchFromLauncher()

        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.openTestFolder(rules.testDirectory)
        folders.awaitTitles(listOf("alpha", "beta", "gamma"))

        folders.cell("alpha").performLongClickForSetupAndAwaitChecked()
        home.awaitSelectionStarted()
        folders.cell("beta").click()
        home.awaitSelectionTitle(selectedCount(2))

        home.tapSelectionAction(R.id.deleteBatchOption)
        HomeDialogs.awaitConfirmationDialog()
        assertEquals(
            "the confirmation counts the books that were picked",
            plural(R.plurals.home_delete_batch_confirm_message, 2),
            HomeDialogs.confirmationMessage(),
        )

        HomeDialogs.tapNegative()

        library.books.forEach {
            assertTrue("cancelling must leave ${it.fileName} on disk", it.file.exists())
        }
        HomeLibrary.holdRecordCount(3)
        home.awaitSelectionTitle(selectedCount(2))
        assertTrue("cancelling leaves the selection active", home.isSelectionToolbarShown())
    }

    @Test
    @TestId("HOME-49")
    fun confirmedBulkDeleteRemovesFilesRecordsBookmarksAndCovers() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, bookmarks = listOf(0, 1)),
            BookSeed(HomeFixtures.BETA, pages = 1),
            BookSeed(HomeFixtures.GAMMA, pages = 1),
        )
        assertEquals("baseline: the first book carries two bookmarks", 2, HomeLibrary.bookmarks(library.hashOf("alpha")).size)
        val journal = AnnotationJournal(TestEnvironment.targetContext)
        val edits = listOf("alpha", "beta", "gamma").associateWith { name ->
            AnnotationEdit.Add(0, "home-delete-$name", listOf(RectF(72f, 72f, 144f, 90f)), Color.YELLOW, name)
        }
        edits.forEach { (name, edit) ->
            val uri = Uri.fromFile(library.fileOf(name))
            journal.append(uri, edit)
            assertEquals("the $name journal must exist before deletion", listOf(edit), journal.readAll(uri))
            assertTrue(journalFileFor(library.fileOf(name)).isFile)
        }

        home.launchFromLauncher()

        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.openTestFolder(rules.testDirectory)
        folders.awaitTitles(listOf("alpha", "beta", "gamma"))

        folders.cell("alpha").awaitCover()
        folders.cell("beta").awaitCover()

        folders.cell("alpha").performLongClickForSetupAndAwaitChecked()
        home.awaitSelectionStarted()
        folders.cell("beta").click()
        home.awaitSelectionTitle(selectedCount(2))

        home.tapSelectionAction(R.id.deleteBatchOption)
        HomeDialogs.awaitConfirmationDialog()
        HomeDialogs.tapPositive()

        Await.until("both picked files leave the disk") {
            !library.fileOf("alpha").exists() && !library.fileOf("beta").exists()
        }
        assertTrue("the book that was not picked survives", library.fileOf("gamma").exists())

        val records = HomeLibrary.awaitRecordCount(1)
        assertEquals("only the surviving book keeps a record", library.hashOf("gamma"), records.single().hash)

        HomeLibrary.awaitNoBookmarks(library.hashOf("alpha"))
        HomeLibrary.awaitNoScannedEntry(library["alpha"].path)
        HomeLibrary.awaitNoScannedEntry(library["beta"].path)

        listOf("alpha", "beta").forEach { name ->
            Await.until(
                "no cover survives for $name, cache holds ${Covers.names()}"
            ) { Covers.filesFor(library.hashOf(name)).isEmpty() }
            Await.until("the annotation journal for $name is deleted") { !journalFileFor(library.fileOf(name)).exists() }
        }
        assertEquals("the unselected document keeps its journal", listOf(edits.getValue("gamma")), journal.readAll(Uri.fromFile(library.fileOf("gamma"))))
    }

    @Test
    @TestId("HOME-50")
    fun aDeleteThatCannotHappenIsReportedAndKeepsTheRecord() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pageNumber = 0, pages = 1),
            BookSeed(HomeFixtures.BETA, pages = 1),
        )

        home.launchFromLauncher()

        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.openTestFolder(rules.testDirectory)
        folders.awaitTitles(listOf("alpha", "beta"))

        val before = HomeLibrary.requireRecord(library.hashOf("alpha"))

        folders.cell("alpha").performLongClickForSetupAndAwaitChecked()
        home.awaitSelectionStarted()
        folders.cell("beta").click()
        home.awaitSelectionTitle(selectedCount(2))

        assertTrue(
            "the first book's file must be removable behind the app's back",
            library.fileOf("alpha").delete(),
        )

        home.tapSelectionAction(R.id.deleteBatchOption)
        HomeDialogs.awaitConfirmationDialog()
        HomeDialogs.tapPositive()

        Toasts.awaitText(TestEnvironment.targetString(R.string.home_delete_failed))

        val alpha = HomeLibrary.requireRecord(library.hashOf("alpha"))
        assertEquals("a failed delete keeps the record pointing where it did", before.uri, alpha.uri)
        assertEquals("a failed delete keeps the reading position", before.pageNumber, alpha.pageNumber)

        HomeLibrary.awaitNoRecord(library.hashOf("beta"))
        assertTrue("the book that could be deleted is gone", !library.fileOf("beta").exists())
    }

    private fun journalFileFor(file: File): File {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(Uri.fromFile(file).toString().toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(File(TestEnvironment.targetContext.filesDir, "annotations"), "$key.journal")
    }

    private fun selectedCount(count: Int): String =
        TestEnvironment.targetContext.getString(R.string.home_selected_count, count)

    private fun plural(id: Int, count: Int): String =
        TestEnvironment.targetContext.resources.getQuantityString(id, count, count)

    private companion object {
        const val ON_HOLD_LABEL = "On Hold"
    }
}
