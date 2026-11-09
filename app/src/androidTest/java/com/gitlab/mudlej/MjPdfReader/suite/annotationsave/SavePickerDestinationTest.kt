// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.UriCanonicalizer
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SavePickerDestinationTest : SaveTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @Test
    @TestId("SAVE-03")
    fun updatingTheSameFileOpensTheOpenPickerWhenAllFilesAccessIsDenied() {
        val file = place(SaveFixtures.NORMAL)
        withholdAllFilesAccess()
        val fileUri = controlledDocument(file)

        launch(fileUri).use {
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            IntentStubs.stubUriResultFor(Intent.ACTION_OPEN_DOCUMENT, fileUri)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()

            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            awaitSaveCompleted(journal)

            SaveDestinations.awaitRowCount(1)
        }
    }

    @Test
    @TestId("SAVE-09")
    fun choosingTheOpenDocumentAsTheCopyTargetIsRefused() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            val sourceUri = Uri.parse(documentUri())
            assertEquals(
                "the reader must have the source file open through its file URI",
                Uri.fromFile(file),
                sourceUri,
            )
            val pickerResultUri = StubSharerProvider.registerDocumentPath(
                ownedName("same-document.pdf"),
                file.absolutePath,
            )
            assertTrue(
                "the controlled picker result must be a content document URI",
                DocumentsContract.isDocumentUri(TestEnvironment.targetContext, pickerResultUri),
            )
            assertNotEquals(
                "the picker must return a lexically different URI for the open document",
                sourceUri.toString(),
                pickerResultUri.toString(),
            )
            assertEquals(
                "the controlled picker result must resolve to the document actually open in the reader",
                file.canonicalFile,
                UriCanonicalizer.canonicalize(TestEnvironment.targetContext, pickerResultUri)?.canonicalFile,
            )
            IntentStubs.stubUriResultFor(Intent.ACTION_CREATE_DOCUMENT, pickerResultUri)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_same_file_title), Await.LONG_TIMEOUT_MS)
            AnnotationDialogs.awaitTextShown(string(R.string.annotation_save_same_file_message))

            SaveDigest.assertUnchanged("a refused copy must not touch the source", file, before)
            SaveDestinations.assertRowCountStays(0)

            AnnotationDialogs.pressNegative()
            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationJournalFile.assertLineCountStays(journal, 1)
        }
    }

    @Test
    @TestId("SAVE-10")
    fun overwritingADifferentFileAsksFirstAndCancelWritesNothing() {
        val source = place(SaveFixtures.NORMAL)
        val decoy = place(SaveFixtures.DECOY)
        withholdAllFilesAccess()
        val sourceUri = controlledDocument(source)
        val decoyUri = controlledDocument(decoy)

        launch(sourceUri).use {
            val decoyBefore = SaveDigest.of(decoy)
            val sourceBefore = SaveDigest.of(source)
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            IntentStubs.stubUriResultFor(Intent.ACTION_OPEN_DOCUMENT, decoyUri)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_different_file_title), Await.LONG_TIMEOUT_MS)
            assertTrue(
                "the warning must name the file it is about to replace",
                AnnotationDialogs.messageText().contains(decoy.name),
            )

            AnnotationDialogs.pressNegative()

            SaveDigest.assertStaysUnchanged("a cancelled overwrite must not touch the other file", decoy, decoyBefore)
            SaveDigest.assertUnchanged("a cancelled overwrite must not touch the source", source, sourceBefore)
            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationJournalFile.assertLineCountStays(journal, 1)
        }
    }

    @Test
    @TestId("SAVE-11")
    fun confirmingTheOverwriteWritesToThatFileAndLeavesTheSourceAlone() {
        val source = place(SaveFixtures.NORMAL)
        val decoy = place(SaveFixtures.DECOY)
        withholdAllFilesAccess()
        val sourceUri = controlledDocument(source)
        val decoyUri = controlledDocument(decoy)

        var sourcePages = 0
        launch(sourceUri).use {
            sourcePages = reader.readState().pageCount!!
            val decoyBefore = SaveDigest.of(decoy)
            val sourceBefore = SaveDigest.of(source)
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            IntentStubs.stubUriResultFor(Intent.ACTION_OPEN_DOCUMENT, decoyUri)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_different_file_title), Await.LONG_TIMEOUT_MS)
            AnnotationDialogs.pressPositive()

            awaitSaveCompleted(journal)
            val decoyAfter = SaveDigest.awaitSettled(decoy)
            assertTrue(
                "a confirmed overwrite must replace the chosen file, read $decoyBefore then $decoyAfter",
                decoyAfter != decoyBefore,
            )
            SaveDigest.assertUnchanged("a confirmed overwrite must not touch the source", source, sourceBefore)
        }

        reopen(decoy) {
            assertEquals(
                "the overwritten file now carries the source document's pages",
                sourcePages,
                reader.readState().pageCount,
            )
            assertEquals(
                "the overwritten file carries the highlight that was saved into it",
                1,
                highlightGroupsOnPage(0).size,
            )
            assertTrue(
                "the library records the document at its new location",
                Records.all().any { it.uri.toString().contains(decoy.name) },
            )
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
