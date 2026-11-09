// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SaveInFlightTest : SaveTestCase() {

    @Test
    @TestId("SAVE-36")
    fun aSecondSaveTapWhileASaveIsRunningIsIgnored() {
        grantAllFilesAccess()
        SaveFaults.armSlowWrite()

        openReader(SaveFixtures.SMALL) { _, file ->
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            SaveFaults.awaitSlowWriteParked()

            runCatching { AnnotationReaderUi.tapSaveFab() }
            SaveSheet.assertNeverShown()
            SavePicker.assertNeverShown()

            SaveFaults.releaseSlowWrite()
            Snackbars.awaitText(string(R.string.highlights_saved), Await.VERY_LONG_TIMEOUT_MS)
            awaitSaveCompleted(journal)
            SaveDigest.awaitSettled(file)

            SaveDestinations.awaitRowCount(1)
            assertTrue(
                "no partial write may be left beside the file",
                SaveFixtures.savingSiblingsWithPrefix(cleanup.prefix()).isEmpty(),
            )
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SMALL))
        reopen(file) {
            assertEquals("one gesture produced exactly one highlight in the file", 1, highlightGroupsOnPage(0).size)
        }
    }

    @Test
    @TestId("SAVE-38")
    fun theScreenIsNotInteractiveWhileASaveIsRunning() {
        grantAllFilesAccess()
        SaveFaults.armSlowWrite()

        openReader(SaveFixtures.SMALL) { _, _ ->
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            val pageBefore = reader.readState().page
            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            SaveFaults.awaitSlowWriteParked()

            reader.swipeForward()
            repeat(CANVAS_TAPS) { tapCanvasCentre() }

            assertEquals("the page must not move while the save runs", pageBefore, reader.readState().page)

            SaveFaults.releaseSlowWrite()
            awaitSaveCompleted(journal)

            assertEquals("the page must still be where the save started", pageBefore, reader.readState().page)
        }
    }

    @Test
    @TestId("SAVE-39")
    fun rotationDuringASaveStillDeliversTheResult() {
        grantAllFilesAccess()
        SaveFaults.armSlowWrite()

        openReader(SaveFixtures.SMALL) { _, file ->
            val journal = journal()
            val sourceUri = documentUri()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            val instanceBefore = readerIdentity()

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            SaveFaults.awaitSlowWriteParked()

            try {
                uiDevice().setOrientationLeft()
                reader.awaitState("the reader reconfigures into landscape", Await.LONG_TIMEOUT_MS) {
                    (it.viewWidth ?: 0) > (it.viewHeight ?: 0)
                }

                assertEquals(
                    "the reader instance survives the rotation, so the result reaches a live view",
                    instanceBefore,
                    readerIdentity(),
                )
                assertTrue("the progress bar must not restart from zero", AnnotationReaderUi.isProgressShown())

                SaveFaults.releaseSlowWrite()
                Snackbars.awaitText(string(R.string.highlights_saved), Await.VERY_LONG_TIMEOUT_MS)
                awaitSaveCompleted(journal)
            } finally {
                uiDevice().setOrientationNatural()
            }

            SaveDigest.awaitSettled(file)
            val row = SaveDestinations.awaitRowForSource(sourceUri)
            assertEquals(
                "the save landed on the file itself",
                android.net.Uri.fromFile(file).toString(),
                row.destinationUri,
            )
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SMALL))
        reopen(file) {
            assertEquals("the saved file carries the highlight", 1, highlightGroupsOnPage(0).size)
        }
    }

    @Test
    @TestId("SAVE-40")
    fun leavingTheReaderDuringASaveStillWritesACompleteFile() {
        grantAllFilesAccess()
        SaveFaults.armSlowWrite()
        val file = place(SaveFixtures.SMALL)
        var pagesBefore = 0
        var journal: File

        launch(file).use {
            pagesBefore = reader.readState().pageCount!!
            journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            SaveFaults.awaitSlowWriteParked()
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        SaveFaults.releaseSlowWrite()
        SaveDigest.awaitSettled(file)

        assertTrue("the destination must be a complete pdf", SaveDigest.startsWithPdfMagic(file))
        assertTrue("the destination must carry bytes", file.length() > 0)
        assertTrue(
            "no partial write may be left beside it",
            SaveFixtures.savingSiblingsWithPrefix(cleanup.prefix()).isEmpty(),
        )
        AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)

        reopen(file) {
            assertEquals("the completed save keeps every page", pagesBefore, reader.readState().pageCount)
            assertEquals("the completed save carries the highlight", 1, highlightGroupsOnPage(0).size)
        }
    }

    @Test
    @TestId("SAVE-41")
    fun aQueuedSaveThenActionSurvivesRotationWhileThePickerIsOpen() {
        withholdAllFilesAccess()
        val copy = File(
            SaveFixtures.downloads,
            SaveFixtures.annotatedNameOf(ownedName(SaveFixtures.NORMAL)),
        )

        openReader(SaveFixtures.NORMAL) { scenario, _ ->
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            val instanceBefore = readerIdentity(scenario)

            uiDevice().pressBack()
            AnnotationDialogs.awaitTextShown(string(R.string.unsaved_highlights_prompt))
            AnnotationDialogs.pressPositive()

            SaveSheet.awaitShown()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.awaitShown()

            try {
                uiDevice().setOrientationLeft()
                uiDevice().waitForIdle()
                SavePicker.awaitShown()
                assertEquals(
                    "the reader waiting for the picker result is the instance that queued the action",
                    instanceBefore,
                    readerIdentity(scenario),
                )
                SavePicker.createInDownloads(copy.name)
            } finally {
                uiDevice().setOrientationNatural()
            }

            awaitSaveSettled()
            SaveDigest.awaitSettled(copy)
            assertTrue("the queued save wrote the copy", copy.isFile && copy.length() > 0)
            SaveDestinations.awaitRowCount(1)
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        reopen(copy) {
            assertEquals("the queued save copy carries the highlight", 1, highlightGroupsOnPage(0).size)
        }
    }

    private fun readerIdentity(): Int = TestEnvironment.onMainThread {
        System.identityHashCode(TestEnvironment.resumedActivityOfType(MainActivity::class.java))
    }

    private fun readerIdentity(scenario: ActivityScenario<MainActivity>): Int {
        var identity = 0
        scenario.onActivity { identity = System.identityHashCode(it) }
        return identity
    }

    private fun tapCanvasCentre() {
        val state = reader.readState()
        val origin = selection.readerViewOriginOnScreen()
        uiDevice().click(
            (origin.x + (state.viewWidth ?: 0) / 2).toInt(),
            (origin.y + (state.viewHeight ?: 0) / 2).toInt(),
        )
        TestEnvironment.waitForIdleSync()
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val CANVAS_TAPS = 2
    }
}
