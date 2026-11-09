// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeFixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class RelocateTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()
    private val reader = ReaderRobot()
    private val picker = SafPickerRobot()

    @Test
    @TestId("HOME-58")
    @TestDuration(DurationCategory.SHORT)
    fun theLocateDialogOpensWhenTheFileCannotBeFound() {
        val library = seedLostBook()

        openHomeAskingToRelocate(library.hashOf("alpha"))

        HomeDialogs.awaitConfirmationDialog()
        assertEquals(
            "the dialog says the file cannot be found",
            TestEnvironment.targetString(R.string.home_relocate_title),
            HomeDialogs.dialogTitle(),
        )
        assertEquals(
            "the dialog names the book it means",
            TestEnvironment.targetContext.getString(R.string.home_relocate_message, "alpha"),
            HomeDialogs.confirmationMessage(),
        )

        val before = HomeLibrary.requireRecord(library.hashOf("alpha"))
        HomeDialogs.tapNegative()

        HomeLibrary.holdRecordUnchanged(before)
        assertTrue(
            "cancelling must not open the reader",
            !Screens.isInTask(MainActivity::class.java),
        )
    }

    @Test
    @TestId("HOME-59")
    fun theLocatePickerStillHealsTheRecordAfterTheScreenIsRecreated() {
        val library = seedLostBook(HomeFixtures.EIGHT_PAGE, HomeFixtures.EIGHT_PAGE_PAGES)
        val selectable = Fixtures.pickerFixture(HomeFixtures.EIGHT_PAGE)
        val hash = library.hashes.single()

        openHomeAskingToRelocate(hash)
        HomeDialogs.awaitConfirmationDialog()
        HomeDialogs.tapPositive()

        picker.awaitShown()
        TestEnvironment.uiDevice.setOrientationLeft()
        TestEnvironment.uiDevice.waitForIdle()
        picker.awaitShown()

        picker.navigateToDownloads().chooseFileNamed(selectable.name)
        picker.awaitPickerDismissed()

        val healed = HomeLibrary.awaitRecordUri(
            hash,
            Uri.fromFile(selectable).toString(),
            Await.LONG_TIMEOUT_MS,
        )
        assertEquals("the pending hash survived recreation with its reading position", 4, healed.pageNumber)

        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()
        reader.awaitPageIndex(4)

        val intent = TestEnvironment.onMainThread {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java)?.intent
                ?: throw AssertionError("MainActivity is not resumed, so it has no intent to read")
        }
        assertEquals("the reader opens the healed location", healed.uri, intent.data)
        assertEquals(
            "the reader is told which record it belongs to",
            hash,
            intent.getStringExtra(HomeActivity.EXTRA_RECORD_HASH),
        )
    }

    @Test
    @TestId("HOME-60")
    fun locatingTheWrongFileOffersToOpenItAndLeavesTheRecordAlone() {
        val library = seedLostBook()
        val hash = library.hashOf("alpha")
        val before = HomeLibrary.requireRecord(hash)

        openHomeAskingToRelocate(hash)
        HomeDialogs.awaitConfirmationDialog()
        HomeDialogs.tapPositive()
        picker.awaitShown()
        picker.navigateToDownloads().chooseFileNamed(HomeFixtures.BETA)
        picker.awaitPickerDismissed()

        HomeDialogs.awaitConfirmationDialog()
        assertEquals(
            "picking a different document must be called out",
            TestEnvironment.targetString(R.string.home_relocate_mismatch_title),
            HomeDialogs.dialogTitle(),
        )
        assertEquals(
            "the mismatch dialog explains what it will do",
            TestEnvironment.targetString(R.string.home_relocate_mismatch_message),
            HomeDialogs.confirmationMessage(),
        )

        HomeDialogs.tapNegative()
        HomeLibrary.holdRecordUnchanged(before)
        assertTrue(
            "cancelling the mismatch must not open anything",
            !Screens.isInTask(MainActivity::class.java),
        )

        openHomeAskingToRelocate(hash)
        HomeDialogs.awaitConfirmationDialog()
        HomeDialogs.tapPositive()
        picker.awaitShown()
        picker.navigateToDownloads().chooseFileNamed(HomeFixtures.BETA)
        picker.awaitPickerDismissed()

        HomeDialogs.awaitConfirmationDialog()
        HomeDialogs.tapPositive()

        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        val intent = TestEnvironment.onMainThread {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java)?.intent
                ?: throw AssertionError("MainActivity is not resumed, so it has no intent to read")
        }
        assertNull(
            "opening a different document must not attach the lost book's identity to it",
            intent.getStringExtra(HomeActivity.EXTRA_RECORD_HASH),
        )
        HomeLibrary.holdRecordUnchanged(before)
    }

    @Test
    @TestId("HOME-61")
    @TestDuration(DurationCategory.NORMAL)
    fun pickingAByteIdenticalCopyHealsTheRecord() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.ALPHA,
                pageNumber = 0,
                pages = 1,
                lastOpened = daysAgo(0),
                reading = ReadingStatus.READING,
                favorite = true,
            ),
        )
        val file = library.books.single().file
        HomeLibrary.deleteScannedEntries(listOf(file.absolutePath))
        assertTrue("the recorded file must be gone before the relocate flow runs", file.delete())
        val hash = library.hashes.single()

        openHomeAskingToRelocate(hash)
        HomeDialogs.awaitConfirmationDialog()
        HomeDialogs.tapPositive()
        picker.awaitShown()
        picker.navigateToDownloads().chooseFileNamed(HomeFixtures.ALPHA_COPY)
        picker.awaitPickerDismissed()

        val healed = HomeLibrary.awaitRecordUri(
            hash,
            Uri.fromFile(IntakeFixtures.seeded(HomeFixtures.ALPHA_COPY)).toString(),
            Await.LONG_TIMEOUT_MS,
        )
        assertEquals(
            "identity is decided by content, so the reading status follows the copy",
            ReadingStatus.READING,
            healed.reading,
        )
        assertTrue("identity is decided by content, so the favourite mark follows the copy", healed.favorite)
        assertTrue(
            "no mismatch is due when the bytes are the same",
            !HomeDialogs.isDialogTitleShowing(),
        )
        HomeLibrary.awaitRecordCount(1)
    }

    private fun seedLostBook(fixture: String = HomeFixtures.ALPHA, pages: Int = 10): SeededLibrary {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(fixture, pageNumber = 4, pages = pages, lastOpened = daysAgo(0)),
        )
        val file = library.books.single().file
        HomeLibrary.deleteScannedEntries(listOf(file.absolutePath))
        assertTrue("the recorded file must be gone before the relocate flow runs", file.delete())
        return library
    }

    private fun openHomeAskingToRelocate(hash: String) {
        home.sendLauncherIntent { intent ->
            intent.putExtra(HomeActivity.EXTRA_RELOCATE_HASH, hash)
        }
        home.awaitAppInForeground()
    }

}
