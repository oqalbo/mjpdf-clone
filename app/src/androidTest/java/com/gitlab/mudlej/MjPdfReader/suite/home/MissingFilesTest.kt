// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class MissingFilesTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()
    private val reader = ReaderRobot()

    @Test
    @TestId("HOME-55")
    fun aMissingFileDropsOutOfTheListsButKeepsItsRow() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pageNumber = 4, pages = 10, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, pageNumber = 2, pages = 10, lastOpened = daysAgo(1)),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta"), Await.LONG_TIMEOUT_MS)

        assertTrue(
            "HOME-55 requires sufficient access before the file is deleted",
            PermissionManager.hasFullAccess(
                TestEnvironment.targetContext
            ),
        )
        val retainedBefore = HomeLibrary.requireRecord(library.hashOf("alpha"))

        assertTrue("the file must be removable behind the app's back", library.fileOf("alpha").delete())

        grid.pullToRefresh()

        grid.awaitTitles(listOf("beta"), Await.LONG_TIMEOUT_MS)
        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitTitles(listOf("beta"), Await.LONG_TIMEOUT_MS)
        assertNull("a definitively missing record must not raise a Recent permission card", recent.permissionCard())

        val record = HomeLibrary.requireRecord(library.hashOf("alpha"))
        assertEquals(
            "a missing file is filtered at render time only, so the exact historical row survives",
            retainedBefore,
            record,
        )
        assertEquals(4, record.pageNumber)
        assertTrue("a missing file must not be treated as hidden", !record.hidden)
    }

    @Test
    @TestId("HOME-56")
    fun aRestoredFileReappearsWithItsProgressIntact() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pageNumber = 4, pages = 10, lastOpened = daysAgo(0)),
        )
        val target = library.fileOf("alpha")
        val bytes = target.readBytes()
        assertTrue("the file must be removable before the launch", target.delete())

        home.launchFromLauncher()
        home.awaitRendered(Await.LONG_TIMEOUT_MS)

        val grid = home.library()
        grid.awaitAbsent("alpha")

        target.writeBytes(bytes)
        Fixtures.scanAndWait(target)

        grid.pullToRefresh()

        grid.awaitContains("alpha", Await.LONG_TIMEOUT_MS)

        assertEquals(
            "the record that was never deleted supplies the progress",
            "50",
            grid.cell("alpha").progressPercentText(),
        )
    }

    @Test
    @TestId("HOME-57")
    fun aMovedFileIsAlreadyHealedByTheTimeTheListIsDrawn() {
        val directory = rules.testDirectory.directory()
        val library = HomeBaselines.scopedLibrary(
            directory,
            BookSeed(
                HomeFixtures.EIGHT_PAGE,
                name = HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = HomeFixtures.EIGHT_PAGE_PAGES,
                lastOpened = daysAgo(0),
            ),
        )

        val original = library.fileOf("alpha")
        val moved = File(File(directory, "Science").also { it.mkdirs() }, "alpha.pdf")
        original.copyTo(moved, overwrite = true)
        assertTrue("the file must leave its recorded location", original.delete())

        HomeLibrary.deleteScannedEntries(listOf(original.absolutePath))
        HomeLibrary.insertScannedEntry(
            SeededBook(library["alpha"].seed, moved, library.hashOf("alpha"))
        )

        home.launchFromLauncher()

        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitContains("alpha", Await.LONG_TIMEOUT_MS)

        val hash = library.hashOf("alpha")
        val healed = HomeLibrary.awaitRecordUri(hash, Uri.fromFile(moved).toString())
        assertEquals("the silent heal must keep the reading position", 4, healed.pageNumber)
        assertEquals(
            "a healed record resolves to a readable file, so its row is not dimmed",
            1.0f,
            recent.cell("alpha").alpha(),
            0.001f,
        )
        Toasts.assertNoneWithin(TestEnvironment.targetString(R.string.home_relocate_found))
        assertTrue(
            "nothing is left for the user to resolve, so no relocate dialog is due",
            !HomeDialogs.isDialogTitleShowing(),
        )

        recent.cell("alpha").click()
        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()
        reader.awaitPageIndex(4)
    }
}
