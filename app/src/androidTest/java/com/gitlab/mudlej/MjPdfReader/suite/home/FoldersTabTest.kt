// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
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
class FoldersTabTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-62")
    fun foldersHierarchyShowsChildFoldersWithRecursiveCounts() {
        seedTree()

        home.launchFromLauncher()
        home.awaitRendered()
        val folders = home.selectTab(HomeTab.FOLDERS)

        folders.awaitFolders(listOf(HomeFixtures.TREE_DIRECTORY))
        folders.tapFolder(HomeFixtures.TREE_DIRECTORY)

        folders.awaitFolders(listOf("Fiction", "Science"))
        assertEquals("Fiction counts its subfolders too", 3, folders.folder("Fiction").count)
        assertEquals("Science holds one book", 1, folders.folder("Science").count)
        assertNull(
            "hierarchy mode passes a null subtitle, so no folder row carries one",
            folders.folder("Fiction").subtitle,
        )

        folders.tapFolder("Fiction")
        folders.awaitFolders(listOf("Short"))
        assertEquals("Short counts the two books inside it", 2, folders.folder("Short").count)
        folders.awaitTitles(listOf("a"))
    }

    @Test
    @TestId("HOME-63")
    fun breadcrumbsNavigateBackToAnAncestorFolder() {
        seedTree()

        home.launchFromLauncher()
        home.awaitRendered()
        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.openFolderPath(HomeFixtures.TREE_DIRECTORY, "Fiction", "Short")

        val storage = TestEnvironment.targetString(R.string.home_folder_storage)
        folders.awaitCrumbs(listOf(storage, HomeFixtures.TREE_DIRECTORY, "Fiction", "Short"))
        assertTrue("the folder you are in is the bold crumb", folders.crumbIsBold("Short"))
        assertTrue("the folder you are in is not a link", !folders.crumbIsClickable("Short"))

        folders.tapCrumb("Fiction")

        folders.awaitFolders(listOf("Short"))
        folders.awaitTitles(listOf("a"))
        folders.awaitCrumbs(listOf(storage, HomeFixtures.TREE_DIRECTORY, "Fiction"))
    }

    @Test
    @TestId("HOME-64")
    @TestDuration(DurationCategory.NORMAL)
    fun systemBackWalksUpOneFolderLevelAndStopsAtTheRoot() {
        seedTree()

        home.launchFromLauncher()
        home.awaitRendered()
        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.openFolderPath(HomeFixtures.TREE_DIRECTORY, "Fiction", "Short")

        home.pressBack()
        folders.awaitFolders(listOf("Short"))
        folders.awaitTitles(listOf("a"))

        home.pressBack()
        folders.awaitFolders(listOf("Fiction", "Science"))

        home.pressBack()
        folders.awaitFolders(listOf(HomeFixtures.TREE_DIRECTORY))
        assertEquals("the root listing carries no breadcrumbs", emptyList<String>(), folders.crumbs())

        home.pressBack()
        Await.until("HomeActivity leaves the foreground at the folder root") {
            Screens.resumedName() != HomeActivity::class.java.name
        }
    }

    @Test
    @TestId("HOME-65")
    @TestDuration(DurationCategory.NORMAL)
    fun flatFolderModeListsEveryFolderWithItsParentPath() {
        seedTree()

        home.launchFromLauncher()
        home.awaitRendered()
        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.awaitFolders(listOf(HomeFixtures.TREE_DIRECTORY))

        home.openOverflowMenu()
        home.tapMenuRow(R.id.foldersFlatButton)
        home.dismissOverflowMenu()

        HomePrefs.awaitBoolean(Preferences.homeFolderFlatKey, true)
        folders.awaitFolders(listOf("Fiction", "Science", "Short"))
        assertEquals("Fiction holds one book of its own in flat mode", 1, folders.folder("Fiction").count)
        assertEquals("Science holds one book", 1, folders.folder("Science").count)
        assertEquals("Short holds two books", 2, folders.folder("Short").count)

        val storage = TestEnvironment.targetString(R.string.home_folder_storage)
        assertEquals(
            "a flat row says where the folder lives",
            "$storage › ${HomeFixtures.TREE_DIRECTORY}",
            folders.folder("Fiction").subtitle,
        )
        assertEquals(
            "a nested flat row spells out its whole parent path",
            "$storage › ${HomeFixtures.TREE_DIRECTORY} › Fiction",
            folders.folder("Short").subtitle,
        )

        folders.tapFolder("Short")
        folders.awaitTitles(listOf("b", "c"))
        assertEquals("a flat folder lists files only", emptyList<String>(), folders.folderNames())

        home.pressBack()
        folders.awaitFolders(listOf("Fiction", "Science", "Short"))
    }

    @Test
    @TestId("HOME-66")
    fun selectedScanLocationsLimitTheLibraryGrid() {
        val directory = rules.testDirectory.directory()
        val root = File(directory, HomeFixtures.TREE_DIRECTORY)
        HomeBaselines.scopedLibrary(
            directory,
            BookSeed(HomeFixtures.ALPHA, name = "${HomeFixtures.TREE_DIRECTORY}/Fiction/a.pdf", record = false, pages = 1),
            BookSeed(HomeFixtures.BETA, name = "${HomeFixtures.TREE_DIRECTORY}/Fiction/Short/b.pdf", record = false, pages = 1),
            BookSeed(HomeFixtures.GAMMA, name = "${HomeFixtures.TREE_DIRECTORY}/Fiction/Short/c.pdf", record = false, pages = 1),
            BookSeed(HomeFixtures.APPLE, name = "${HomeFixtures.TREE_DIRECTORY}/Science/d.pdf", record = false, pages = 1),
        )
        SettingsSeeder.seed(Preferences.scanLocationsKey, setOf(File(root, "Fiction").absolutePath))

        home.launchFromLauncher()

        home.library().awaitTitles(listOf("a", "b", "c"))
    }

    private fun seedTree() {
        HomeBaselines.indexedTree(
            *HomeFixtures.treeMembers.map { BookSeed(it, record = false, pages = 1) }.toTypedArray()
        )
    }
}
