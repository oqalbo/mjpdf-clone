// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SaveDestinationSheetTest : SaveTestCase() {

    @Test
    @TestId("SAVE-01")
    fun theSaveSheetOffersExactlyTwoDestinationsInOrder() {
        withholdAllFilesAccess()

        openReader(SaveFixtures.NORMAL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()

            assertEquals(
                "the sheet lists replacing the file and making a copy, in that order",
                listOf(SaveSheet.updateExistingLabel(), SaveSheet.saveAsNewCopyLabel()),
                SaveSheet.optionLabels(),
            )
            SaveDigest.assertUnchanged("opening the sheet must write nothing", file, before)
            SaveDestinations.assertRowCountStays(0)
        }
    }

    @Test
    @TestId("SAVE-04")
    fun saveAsANewFileOpensTheCreatePickerWithTheAnnotatedNamePrefilled() {
        openReader(SaveFixtures.NORMAL) { _, file ->
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()

            SavePicker.awaitShown()
            assertEquals(
                "the create picker suggests the annotated name",
                SaveFixtures.annotatedNameOf(file.name),
                SavePicker.prefilledFileName(),
            )

            SavePicker.cancel()
        }
    }

    @Test
    @TestId("SAVE-05")
    fun theAnnotatedSuffixIsNotAppliedTwice() {
        val alreadyAnnotated = SaveFixtures.annotatedNameOf(ownedName(SaveFixtures.NORMAL))
        val file = place(SaveFixtures.NORMAL, alreadyAnnotated)

        launch(file).use {
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()

            SavePicker.awaitShown()
            assertEquals(
                "a name that already carries the suffix keeps it exactly once",
                alreadyAnnotated,
                SavePicker.prefilledFileName(),
            )

            SavePicker.cancel()
        }
    }

    @Test
    @TestId("SAVE-06")
    fun cancellingTheDestinationSheetKeepsEveryEdit() {
        openReader(SaveFixtures.NORMAL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.dismissWithBack()

            val save = AnnotationReaderUi.saveFab()
            val discard = AnnotationReaderUi.discardFab()
            assertTrue("the save button stays after a cancelled sheet", save.visible && save.enabled)
            assertTrue("the discard button stays after a cancelled sheet", discard.visible && discard.enabled)
            AnnotationJournalFile.assertLineCountStays(journal, 1)
            SaveDigest.assertUnchanged("a cancelled sheet must write nothing", file, before)
            SaveDestinations.assertRowCountStays(0)
        }
    }

    @Test
    @TestId("SAVE-07")
    fun cancellingTheCreatePickerKeepsEveryEditAndRemembersNoDestination() {
        openReader(SaveFixtures.NORMAL) { _, file ->
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.awaitShown()
            SavePicker.cancel()

            reader.awaitShown(Await.LONG_TIMEOUT_MS)
            AnnotationReaderUi.awaitSaveFabVisible()
            assertTrue("the save button comes back enabled", AnnotationReaderUi.saveFab().enabled)
            AnnotationJournalFile.assertLineCountStays(journal, 1)
            SaveDestinations.assertRowCountStays(0)
            assertEquals(
                "a cancelled picker must leave no new file beside the source",
                listOf(file.name),
                SaveFixtures.downloadsFilesWithPrefix(cleanup.prefix()).map { it.name },
            )

            tapSaveAndAwaitSheet()
            SaveSheet.dismissWithBack()
        }
    }

    @Test
    @TestId("SAVE-08")
    fun cancellingTheUpdatePickerKeepsEveryEdit() {
        openReader(SaveFixtures.NORMAL) { _, file ->
            withholdAllFilesAccess()
            val before = SaveDigest.of(file)
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            SavePicker.awaitShown()
            SavePicker.cancel()

            reader.awaitShown(Await.LONG_TIMEOUT_MS)
            AnnotationReaderUi.awaitSaveFabVisible()
            assertTrue("the save button comes back enabled", AnnotationReaderUi.saveFab().enabled)
            AnnotationJournalFile.assertLineCountStays(journal, 1)
            SaveDigest.assertUnchanged("a cancelled picker must write nothing", file, before)
            SaveDestinations.assertRowCountStays(0)
        }
    }

    @Test
    @TestId("SAVE-27")
    @TestDuration(DurationCategory.LONG)
    fun openingAnotherDocumentClearsTheRememberedDestination() {
        withholdAllFilesAccess()
        val decoy = place(SaveFixtures.DECOY)

        openReader(SaveFixtures.NORMAL) { _, file ->
            val journal = journal()
            val sourceKey = SaveDestinations.sourceKeyOf(documentUri())
            val copyName = SaveFixtures.annotatedNameOf(file.name)
            val copy = java.io.File(SaveFixtures.downloads, copyName)

            makeOneHighlight(SaveFixtures.NORMAL)
            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(copyName)
            awaitSaveCompleted(journal)
            val copyAfterFirstSave = SaveDigest.awaitSettled(copy)

            openThroughDocumentPicker(decoy.name)

            val decoyJournal = journal()
            makeOneHighlight(SaveFixtures.DECOY)
            AnnotationJournalFile.awaitLineCount(decoyJournal, 1)

            tapSaveAndAwaitSheet()

            SaveDigest.assertUnchanged(
                "the first document's copy must not be touched by the second document's save",
                copy,
                copyAfterFirstSave,
            )
            SaveDestinations.awaitRowCount(1)
            assertEquals(
                "the only remembered destination belongs to the first document",
                listOf(sourceKey),
                SaveDestinations.sourceKeys(),
            )

            SaveSheet.dismissWithBack()
        }
    }

    @Test
    @TestId("SAVE-35")
    fun readingWithoutEditingNeverOffersToSaveAndNeverWritesTheFile() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)

            reader.swipeForward()
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

            AnnotationReaderUi.assertBothFabsStayGone()

            uiDevice().pressBack()
            TestEnvironment.waitForIdleSync()

            assertFalse(
                "reading alone must not raise the unsaved changes prompt",
                uiDevice().hasObject(
                    androidx.test.uiautomator.By.text(
                        TestEnvironment.targetContext.getString(R.string.unsaved_highlights_prompt)
                    )
                ),
            )
            SaveDigest.assertUnchanged("reading alone must not write the file", file, before)
            assertTrue(
                "reading alone must leave no journal behind",
                AnnotationJournalFile.files().isEmpty(),
            )
        }
    }
}
