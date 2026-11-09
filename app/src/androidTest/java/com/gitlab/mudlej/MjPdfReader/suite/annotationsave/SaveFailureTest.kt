// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class SaveFailureTest : SaveTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @Test
    @TestId("SAVE-28")
    @TestDuration(DurationCategory.NORMAL)
    fun aDestinationThatCannotBeWrittenFailsSafelyAndLeavesARescueCopy() {
        grantAllFilesAccess()
        SaveFaults.armFailWrite()

        openReader(SaveFixtures.SMALL) { _, file ->
            val before = SaveDigest.of(file)
            val journal = journal()
            val copyName = SaveFixtures.annotatedNameOf(file.name)

            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(copyName)
            awaitSaveSettled()

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_failed_title), Await.LONG_TIMEOUT_MS)
            AnnotationDialogs.awaitTextShown(
                TestEnvironment.targetContext.getString(R.string.annotation_save_failed_rescue_message, copyName)
            )

            val rescue = SaveFaults.awaitRescueFileCount(1).single()
            assertTrue("the rescue copy is named for the annotated file", rescue.name.endsWith(copyName))
            assertTrue("the rescue copy must carry bytes", rescue.length() > 0)
            assertTrue("the rescue copy must be a pdf", SaveDigest.startsWithPdfMagic(rescue))

            SaveDigest.assertUnchanged("a failed save must not touch the source", file, before)
            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationJournalFile.assertLineCountStays(journal, 1)
            SaveDestinations.assertRowCountStays(0)

            AnnotationDialogs.pressNegative()
        }
    }

    @Test
    @TestId("SAVE-29")
    @TestDuration(DurationCategory.NORMAL)
    fun aFailedWriteOverTheSourceReportsThatTheFileMayBeIncomplete() {
        val file = place(SaveFixtures.NORMAL)
        withholdAllFilesAccess()
        SaveFaults.armFailWrite()
        val fileUri = controlledDocument(file)

        launch(fileUri).use {
            val journal = journal()
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            IntentStubs.stubUriResultFor(Intent.ACTION_OPEN_DOCUMENT, fileUri)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            awaitSaveSettled()

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_incomplete_title), Await.LONG_TIMEOUT_MS)
            AnnotationDialogs.awaitTextShown(
                TestEnvironment.targetContext.getString(
                    R.string.annotation_save_incomplete_rescue_message,
                    SaveFixtures.annotatedNameOf(file.name),
                )
            )

            val rescue = SaveFaults.awaitRescueFileCount(1).single()
            assertTrue("the rescue copy must be a complete pdf", SaveDigest.startsWithPdfMagic(rescue))

            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationJournalFile.assertLineCountStays(journal, 1)

            AnnotationDialogs.pressNegative()
        }
    }

    @Test
    @TestId("SAVE-30")
    fun aWriteWhoseBytesDoNotMatchIsReportedAsUnconfirmed() {
        val file = place(SaveFixtures.NORMAL)
        withholdAllFilesAccess()
        SaveFaults.armCorruptWrite()

        openThroughDocumentPicker(file.name).use {
            val journal = journal()
            val copyName = SaveFixtures.annotatedNameOf(file.name)
            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(copyName)
            awaitSaveSettled()

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_unconfirmed_title), Await.LONG_TIMEOUT_MS)
            AnnotationDialogs.awaitTextShown(string(R.string.annotation_save_unconfirmed_message))

            Await.assertThroughout(
                condition = "no saved confirmation is ever shown for an unconfirmed write",
                windowMs = SaveSheet.ABSENCE_WINDOW_MS,
            ) { Snackbars.currentText() != string(R.string.highlights_saved) }
            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationJournalFile.assertLineCountStays(journal, 1)
            SaveDestinations.assertRowCountStays(0)
            assertTrue(
                "an unconfirmed write must not become a library row",
                Records.all().none { it.uri.toString().contains(copyName) },
            )

            AnnotationDialogs.pressNegative()
        }
    }

    @Test
    @TestId("SAVE-31")
    fun theFailureDialogOffersACopyAndCompletesTheSave() {
        grantAllFilesAccess()
        SaveFaults.armFailWrite()

        openReader(SaveFixtures.NORMAL) { _, file ->
            val journal = journal()
            val firstName = SaveFixtures.annotatedNameOf(file.name)
            val secondName = "${cleanup.prefix()}second-copy.pdf"

            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(firstName)
            awaitSaveSettled()

            AnnotationDialogs.awaitTitle(string(R.string.annotation_save_failed_title), Await.LONG_TIMEOUT_MS)
            AnnotationDialogs.pressPositive()

            SavePicker.awaitShown()
            SaveFaults.clear()
            SavePicker.createInDownloads(secondName)

            Snackbars.awaitText(string(R.string.highlights_saved), Await.VERY_LONG_TIMEOUT_MS)
            awaitSaveCompleted(journal)

            val second = File(SaveFixtures.downloads, secondName)
            SaveDigest.awaitSettled(second)
            assertTrue("the second destination must carry bytes", second.length() > 0)
            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)

            reopen(second) {
                assertEquals("the recovered save carries the highlight", 1, highlightGroupsOnPage(0).size)
            }
        }
    }

    @Test
    @TestId("SAVE-32")
    fun theRescueDirectoryKeepsAtMostTwoFiles() {
        grantAllFilesAccess()
        SaveFaults.armFailWrite()

        openReader(SaveFixtures.NORMAL) { _, file ->
            val journal = journal()

            val newestFirst = mutableListOf<String>()
            repeat(FAILED_ATTEMPTS) { attempt ->
                makeOneHighlight(SaveFixtures.NORMAL)
                AnnotationJournalFile.awaitLineCount(journal, attempt + 1)

                tapSaveAndAwaitSheet()
                SaveSheet.chooseSaveAsNewCopy()
                SavePicker.createInDownloads("${cleanup.prefix()}attempt-$attempt.pdf")
                awaitSaveSettled()

                AnnotationDialogs.awaitTitle(string(R.string.annotation_save_failed_title), Await.LONG_TIMEOUT_MS)
                AnnotationDialogs.pressNegative()
                AnnotationDialogs.awaitTitleGone(string(R.string.annotation_save_failed_title))

                val written = SaveFaults.rescueFiles().maxByOrNull { it.lastModified() }
                    ?: throw AssertionError("Attempt $attempt produced no rescue copy at all")
                newestFirst.add(0, written.name)
            }

            val retained = SaveFaults.awaitRescueFileCount(SaveFaults.RESCUE_KEEP)
            assertEquals(
                "the two files kept are the two most recent ones",
                newestFirst.take(SaveFaults.RESCUE_KEEP).sorted(),
                retained.map { it.name }.sorted(),
            )
            retained.forEach { rescue ->
                assertTrue("every retained rescue copy must be a pdf", SaveDigest.startsWithPdfMagic(rescue))
            }
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val FAILED_ATTEMPTS = 3
    }
}
