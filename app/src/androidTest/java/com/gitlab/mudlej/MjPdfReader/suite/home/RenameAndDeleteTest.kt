// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class RenameAndDeleteTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-51")
    fun singleDeleteNamesTheFileAndRemovesIt() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
            BookSeed(HomeFixtures.BETA, pages = 1),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta"))

        grid.cell("alpha").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        HomeDialogs.tapRow(R.id.deleteRow)

        HomeDialogs.awaitConfirmationDialog()
        assertEquals(
            "the confirmation names the file it is about",
            TestEnvironment.targetContext.getString(R.string.home_delete_confirm_message, "alpha"),
            HomeDialogs.confirmationMessage(),
        )
        HomeDialogs.tapPositive()
        HomeDialogs.awaitConfirmationDialogGone()

        Await.until("the deleted file leaves the disk") { !library.fileOf("alpha").exists() }
        assertTrue("the other book survives", library.fileOf("beta").exists())

        val records = HomeLibrary.awaitRecordCount(1)
        assertEquals("only the surviving book keeps a record", library.hashOf("beta"), records.single().hash)
        grid.awaitTitles(listOf("beta"))
    }

    @Test
    @TestId("HOME-52")
    fun renameMovesTheFileAndRepointsTheRecord() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = 10,
                documentTitle = "Meta Title Document",
            ),
        )
        val original = library.fileOf("alpha")
        val renamed = File(original.parentFile, "$NEW_NAME.pdf")

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitContains("Meta Title Document")

        grid.cell("Meta Title Document").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        HomeDialogs.tapRow(R.id.renameRow)
        HomeDialogs.enterRenameText(NEW_NAME)
        HomeDialogs.tapPositive()

        Await.until("the file is renamed on disk") { renamed.exists() && !original.exists() }

        val hash = library.hashOf("alpha")
        HomeLibrary.awaitRecordUri(hash, Uri.fromFile(renamed).toString())
        val record = HomeLibrary.awaitRecordFileName(hash, NEW_NAME)
        assertNull(
            "renaming clears the embedded title so the new file name is what shows",
            record.documentTitle,
        )
        assertEquals("renaming keeps the reading position", 4, record.pageNumber)

        HomeLibrary.awaitScannedEntry(renamed.absolutePath)
        HomeLibrary.awaitNoScannedEntry(original.absolutePath)
        grid.awaitTitles(listOf(NEW_NAME))
    }

    @Test
    @TestId("HOME-53")
    fun renameToANameAlreadyInUseIsRefused() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
            BookSeed(HomeFixtures.BETA, pages = 1),
        )
        val alphaBytes = library.fileOf("alpha").length()
        val betaBytes = library.fileOf("beta").length()

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta"))

        grid.cell("alpha").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        HomeDialogs.tapRow(R.id.renameRow)
        HomeDialogs.enterRenameText("beta")
        HomeDialogs.tapPositive()

        Toasts.awaitText(TestEnvironment.targetString(R.string.home_rename_exists))

        assertTrue("the renamed file must stay where it was", library.fileOf("alpha").exists())
        assertTrue("the collision target must stay where it was", library.fileOf("beta").exists())
        assertEquals("the renamed file must be untouched", alphaBytes, library.fileOf("alpha").length())
        assertEquals("the collision target must be untouched", betaBytes, library.fileOf("beta").length())

        assertEquals(
            "the record must still point at its own file",
            Uri.fromFile(library.fileOf("alpha")),
            HomeLibrary.requireRecord(library.hashOf("alpha")).uri,
        )
        assertEquals(
            "the other record must be untouched",
            Uri.fromFile(library.fileOf("beta")),
            HomeLibrary.requireRecord(library.hashOf("beta")).uri,
        )
    }

    @Test
    @TestId("HOME-54")
    @TestDuration(DurationCategory.NORMAL)
    fun aPathSeparatorIsRejectedInARename() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        home.library().awaitTitles(listOf("alpha"))

        val library = home.selectTab(HomeTab.LIBRARY)
        library.awaitContains("alpha")
        val before = HomeLibrary.recordNamed("alpha")
            ?: throw AssertionError("the file backed book lost its record")
        val original = File(before.uri.path!!)

        library.cell("alpha").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        HomeDialogs.tapRow(R.id.renameRow)
        HomeDialogs.enterRenameText("sub/alpha")
        HomeDialogs.tapPositive()

        HomeDialogs.awaitOptionsDialogGone()
        assertTrue("rename must not move the file out of its folder", original.exists())
        assertTrue(
            "rename must not create a directory of its own",
            !File(original.parentFile, "sub").exists(),
        )
        HomeLibrary.holdRecordUnchanged(before)
    }

    private companion object {
        const val NEW_NAME = "renamed-alpha"
    }
}
