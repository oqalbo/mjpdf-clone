// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanLocationsActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.gitlab.mudlej.MjPdfReader.R

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ScanLocationsPickerTest : ScanTestCase() {

    @Test
    @TestId("SCAN-04")
    fun addingAScanLocationSavesItAndTriggersAScan() {
        grantAccess()
        val root = testDirectory()
        val bulk = ScanFixtures.copyTree(ScanFixtures.BULK, root)
        val outside = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "outside.pdf")

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()

        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.tapCheckbox(ScanFixtures.BULK)
        picker.awaitSaveEnabled(true)
        val baseline = scanBaseline()
        picker.tapSave()
        awaitPickerGone()

        ScanPrefs.awaitLocations(setOf(bulk.absolutePath))
        ScanPrefs.awaitMode(ScanMode.SELECTED_LOCATIONS)

        awaitScanComplete(baseline)
        val rows = ScanCache.awaitAllHashedUnder(bulk, ScanFixtures.BULK_COUNT)
        rows.forEach { ScanCache.assertCurrentIdentity(it, "The row for ${it.path}") }

        val outsideRow = ScanCache.awaitRowExists(outside)
        ScanCache.assertUnhashed(
            outsideRow,
            "The MediaStore pass caches ${outside.absolutePath} but it sits outside the selected location",
        )

        home.library().awaitItemCount(ScanFixtures.BULK_COUNT, Await.VERY_LONG_TIMEOUT_MS)
        assertFalse(
            "A file outside the selected location must not reach the library grid",
            home.library().hasItem("outside"),
        )
    }

    @Test
    @TestId("SCAN-05")
    fun saveIsDisabledUntilSomethingIsSelected() {
        grantAccess()
        val root = testDirectory()
        ScanFixtures.copyTree(ScanFixtures.BULK, root)

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()

        assertFalse("Save is disabled on entry with nothing selected", picker.isSaveEnabled())

        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.tapCheckbox(ScanFixtures.BULK)
        picker.awaitSaveEnabled(true)

        picker.tapCheckbox(ScanFixtures.BULK)
        picker.awaitSaveEnabled(false)

        ScanPrefs.assertLocationsEmpty("Nothing was saved, so no scan location may have been written")
    }

    @Test
    @TestId("SCAN-06")
    fun removingOneOfTwoScanLocations() {
        grantAccess()
        val root = testDirectory()
        val bulk = ScanFixtures.copyTree(ScanFixtures.BULK, root)
        val nested = ScanFixtures.copyTree(ScanFixtures.NESTED, root)
        val deep = File(nested, ScanFixtures.NESTED_DEEP_RELATIVE)
        ScanPrefs.seedSelected(bulk, nested)

        launchHomeAndScan()
        home.library().awaitItemCount(ScanFixtures.BULK_COUNT + 1, Await.VERY_LONG_TIMEOUT_MS)
        assertTrue("The deep file starts out listed", home.library().hasItem("deep"))

        openPickerFromMenu()
        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.tapCheckbox(ScanFixtures.NESTED)
        picker.awaitCheckedState(ScanFixtures.NESTED, ScanLocationsRobot.UNCHECKED)
        val secondScan = scanBaseline()
        picker.tapSave()
        awaitPickerGone()

        ScanPrefs.awaitLocations(setOf(bulk.absolutePath))
        ScanPrefs.awaitMode(ScanMode.SELECTED_LOCATIONS)
        awaitScanComplete(secondScan)

        home.library().awaitAbsent("deep", Await.VERY_LONG_TIMEOUT_MS)
        assertTrue(
            "The cache row for ${deep.absolutePath} survives because the file is still on disk, " +
                "and scope only filters what the Library shows",
            ScanCache.byPath(deep) != null,
        )
    }

    @Test
    @TestId("SCAN-07")
    fun theLastScanLocationCannotBeRemovedBySaving() {
        grantAccess()
        val root = testDirectory()
        val bulk = ScanFixtures.copyTree(ScanFixtures.BULK, root)
        ScanPrefs.seedSelected(bulk)

        launchHomeAndScan()
        openPickerFromMenu()

        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.awaitCheckedState(ScanFixtures.BULK, ScanLocationsRobot.CHECKED)
        picker.tapCheckbox(ScanFixtures.BULK)
        picker.awaitSaveEnabled(false)

        picker.tapSaveExpectingNoEffect()
        picker.leaveWithBack()
        awaitPickerGone()

        ScanPrefs.awaitLocations(setOf(bulk.absolutePath))
        ScanPrefs.awaitMode(ScanMode.SELECTED_LOCATIONS)
    }

    @Test
    @TestId("SCAN-08")
    fun selectingAParentFolderLocksOutItsDescendants() {
        grantAccess()
        val root = testDirectory()
        ScanFixtures.copyTree(ScanFixtures.NESTED, root)

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()
        picker.navigateIntoPathOf(root, Fixtures.externalRoot)

        picker.tapCheckbox(ScanFixtures.NESTED)
        picker.awaitCheckedState(ScanFixtures.NESTED, ScanLocationsRobot.CHECKED)
        picker.navigateInto(ScanFixtures.NESTED)

        picker.awaitCheckedState("a", ScanLocationsRobot.CHECKED)
        assertFalse(
            "A child of a selected folder cannot be toggled, so the saved scope stays minimal",
            picker.isCheckboxEnabled("a"),
        )

        picker.goUp()
        picker.awaitRow(ScanFixtures.NESTED)
        picker.tapCheckbox(ScanFixtures.NESTED)
        picker.awaitCheckedState(ScanFixtures.NESTED, ScanLocationsRobot.UNCHECKED)
        picker.navigateInto(ScanFixtures.NESTED)

        picker.awaitCheckedState("a", ScanLocationsRobot.UNCHECKED)
        assertTrue(
            "With the parent unselected the child is selectable again",
            picker.isCheckboxEnabled("a"),
        )
    }

    @Test
    @TestId("SCAN-09")
    fun anAncestorOfASelectedFolderShowsTheIndeterminateState() {
        grantAccess()
        val root = testDirectory()
        ScanFixtures.copyTree(ScanFixtures.NESTED, root)

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()
        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.navigateInto(ScanFixtures.NESTED, "a")

        picker.tapCheckbox("b")
        picker.awaitCheckedState("b", ScanLocationsRobot.CHECKED)

        picker.goUp()
        picker.goUp()
        picker.awaitRow(ScanFixtures.NESTED)

        picker.awaitCheckedState(ScanFixtures.NESTED, ScanLocationsRobot.INDETERMINATE)
        assertTrue(
            "An ancestor holding a selection stays selectable itself",
            picker.isCheckboxEnabled(ScanFixtures.NESTED),
        )
    }

    @Test
    @TestId("SCAN-10")
    fun selectingAParentAfterAChildCollapsesTheSavedScope() {
        grantAccess()
        val root = testDirectory()
        val nested = ScanFixtures.copyTree(ScanFixtures.NESTED, root)

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()
        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.navigateInto(ScanFixtures.NESTED)

        picker.tapCheckbox("a")
        picker.awaitCheckedState("a", ScanLocationsRobot.CHECKED)

        picker.goUp()
        picker.awaitRow(ScanFixtures.NESTED)
        picker.tapCheckbox(ScanFixtures.NESTED)
        picker.awaitCheckedState(ScanFixtures.NESTED, ScanLocationsRobot.CHECKED)

        picker.tapSave()
        awaitPickerGone()

        ScanPrefs.awaitLocations(setOf(nested.absolutePath))
        assertFalse(
            "A saved path inside another saved path is dropped",
            ScanPrefs.locations().any { it == File(nested, "a").absolutePath },
        )
    }

    @Test
    @TestId("SCAN-11")
    fun rotationPreservesThePickersSelectionAndLocation() {
        grantAccess()
        val root = testDirectory()
        val nested = ScanFixtures.copyTree(ScanFixtures.NESTED, root)
        ScanFixtures.copyTree(ScanFixtures.BULK, root)

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()
        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.navigateInto(ScanFixtures.NESTED)
        picker.tapCheckbox("a")
        picker.awaitCheckedState("a", ScanLocationsRobot.CHECKED)

        val crumbsBefore = picker.crumbs()

        Rotation.toLandscape(ScanLocationsActivity::class.java, Await.LONG_TIMEOUT_MS)
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
        Rotation.toPortrait(ScanLocationsActivity::class.java, Await.LONG_TIMEOUT_MS)
        picker.awaitShown(Await.LONG_TIMEOUT_MS)

        picker.awaitCrumbs(crumbsBefore)
        picker.awaitCheckedState("a", ScanLocationsRobot.CHECKED)
        assertTrue("Save stays enabled across the rotation", picker.isSaveEnabled())

        picker.tapSave()
        awaitPickerGone()
        ScanPrefs.awaitLocations(setOf(File(nested, "a").absolutePath))
    }

    @Test
    @TestId("SCAN-12")
    fun backNavigatesUpOneLevelAndExitsAtTheVolumeRoot() {
        grantAccess()
        val volumes = volumeRoots()
        val root = testDirectory()
        ScanFixtures.copyTree(ScanFixtures.NESTED, root)

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()

        val storage = TestEnvironment.targetString(R.string.home_folder_storage)
        val up = TestEnvironment.targetString(R.string.home_scan_locations_up)
        assertEquals(
            "The picker opens straight into the volume root with one volume and on the volume list with more",
            volumes.size > 1,
            picker.isOnVolumeList(),
        )
        picker.enterVolume(Fixtures.externalRoot)
        picker.awaitCrumbs(listOf(storage))

        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.navigateInto(ScanFixtures.NESTED)
        val deepCrumbs = picker.crumbs()
        assertEquals("The trail ends at the nested folder", ScanFixtures.NESTED, deepCrumbs.last())

        picker.goUp()
        picker.awaitCrumbs(deepCrumbs.dropLast(1))
        assertTrue("An Up row is offered below the volume root", picker.hasUpRow())
        assertTrue("The Up row carries the home_scan_locations_up label", picker.hasRow(up))

        repeat(deepCrumbs.size - 2) { picker.goUp() }
        picker.awaitCrumbs(listOf(storage))

        if (volumes.size == 1) {
            assertFalse(
                "With a single volume there is nowhere above the volume root, so no Up row is offered",
                picker.hasUpRow(),
            )
        } else {
            assertTrue(
                "With more than one volume the volume root offers an Up row back to the volume list",
                picker.hasUpRow(),
            )
            picker.goUp()
            Await.until("the picker steps back out to the volume list", Await.LONG_TIMEOUT_MS) {
                picker.isOnVolumeList()
            }
            assertEquals(
                "The volume list offers one row per mounted volume",
                volumes.map { it.absolutePath }.toSet(),
                picker.rows().map { it.path }.toSet(),
            )
            assertFalse("There is nothing above the volume list, so no Up row is offered", picker.hasUpRow())
        }

        picker.goUp()
        Screens.awaitGone(ScanLocationsActivity::class.java, Await.LONG_TIMEOUT_MS)
        Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("SCAN-13")
    fun theBrowserListsOnlyVisibleDirectoriesAndHidesSystemTrees() {
        grantAccess()
        val root = testDirectory()
        ScanFixtures.copySharedAs(ScanFixtures.NORMAL, File(root, "visible"), "keep.pdf")
        ScanFixtures.copySharedAs(ScanFixtures.NORMAL, File(root, ".hiddenfolder"), "x.pdf")
        ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "loose.pdf")

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()

        picker.enterVolume(Fixtures.externalRoot)
        val rootNames = picker.folderNames()
        assertFalse("The volume root hides the Android tree", rootNames.contains("Android"))
        assertFalse("The volume root hides the data tree", rootNames.contains("data"))

        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        val names = picker.folderNames()
        assertTrue("A normal directory is listed, it shows $names", names.contains("visible"))
        assertFalse("A dot prefixed directory is hidden, it shows $names", names.contains(".hiddenfolder"))
        assertFalse("A file is not a folder row, it shows $names", names.contains("loose.pdf"))
    }

    @Test
    @TestId("SCAN-14")
    fun savedLocationsArePreCheckedWhenThePickerIsReopenedAfterARestart() {
        grantAccess()
        val root = testDirectory()
        val bulk = ScanFixtures.copyTree(ScanFixtures.BULK, root)
        ScanFixtures.copyTree(ScanFixtures.NESTED, root)

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openPickerFromMenu()
        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.tapCheckbox(ScanFixtures.BULK)
        picker.tapSave()
        awaitPickerGone()
        ScanPrefs.awaitLocations(setOf(bulk.absolutePath))

        val relaunched = scanBaseline()
        relaunchHome()
        awaitScanComplete(relaunched)
        assertEquals(
            "The saved scope survives tearing the task down and launching again. True process death is " +
                "host driven only, because instrumentation shares the app process, so this asserts the " +
                "persisted preference and the fresh activity read rather than a killed process",
            setOf(bulk.absolutePath),
            ScanPrefs.locations(),
        )

        openPickerFromMenu()
        picker.navigateIntoPathOf(root, Fixtures.externalRoot)
        picker.awaitCheckedState(ScanFixtures.BULK, ScanLocationsRobot.CHECKED)
        picker.awaitCheckedState(ScanFixtures.NESTED, ScanLocationsRobot.UNCHECKED)
        assertTrue(
            "An unrelated folder stays selectable",
            picker.isCheckboxEnabled(ScanFixtures.NESTED),
        )
        assertTrue(
            "Save is enabled on entry because a saved selection is already loaded",
            picker.isSaveEnabled(),
        )
    }
}
