// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.AlwaysFinishActivitiesRule
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SaveDestinationMemoryTest : SaveTestCase() {

    @get:Rule
    val alwaysFinishActivities = AlwaysFinishActivitiesRule()

    @Test
    @TestId("SAVE-23")
    fun theDestinationRowIsKeyedBySourceAndCarriesTheNewIdentity() {
        grantAllFilesAccess()

        openReader(SaveFixtures.SMALL) { _, file ->
            val journal = journal()
            val sourceUri = documentUri()
            val copyName = SaveFixtures.annotatedNameOf(file.name)
            val copy = java.io.File(SaveFixtures.downloads, copyName)

            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(copyName)
            awaitSaveCompleted(journal)
            SaveDigest.awaitSettled(copy)

            SaveDestinations.awaitRowCount(1)
            val row = SaveDestinations.awaitRowForSource(sourceUri)
            assertEquals(
                "the row is keyed by the sha-256 of the source uri",
                SaveDigest.sha256Hex(sourceUri),
                row.sourceKey,
            )
            assertTrue(
                "the row names a document uri the picker returned, read ${row.destinationUri}",
                row.destinationUri.isNotBlank() && row.destinationUri != sourceUri,
            )
            assertTrue(
                "the recorded identity uses the current scheme, read ${row.lastSavedHash}",
                row.lastSavedHash.orEmpty().startsWith(IDENTITY_PREFIX),
            )
            assertEquals(
                "the recorded identity is the identity of the file that was written",
                DocumentIdentity.of(copy)?.identity,
                row.lastSavedHash,
            )
            assertTrue(
                "the save is stamped with the moment it happened, read ${row.lastSavedAt}",
                SaveDestinations.withinOneMinuteOfNow(row.lastSavedAt),
            )
        }
    }

    @Test
    @TestId("SAVE-24")
    fun noDestinationIsRememberedWhenTheWriteGrantCannotBePersisted() {
        grantAllFilesAccess()

        val saved = saveOneHighlightAsNewCopy(SaveFixtures.SMALL)
        val destination = Uri.parse(saved.destinationUri)
        assertTrue(
            "the create picker must leave a persisted write grant for the flow this test then removes",
            SaveDestinations.persistedWriteGrantExists(destination),
        )

        TestEnvironment.targetContext.contentResolver.releasePersistableUriPermission(
            destination,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        assertTrue(
            "the persisted grant must be gone before the reopen",
            !SaveDestinations.persistedGrantExists(destination),
        )

        ActivityScenario.launch<MainActivity>(intentFor(saved.source)).use {
            awaitReaderReady()
            makeOneHighlight(SaveFixtures.SMALL)

            tapSaveAndAwaitSheet()

            assertTrue(
                "a destination the app can no longer write to must not be reused silently",
                SaveSheet.isShown(),
            )
            SaveSheet.dismissWithBack()
        }
    }

    @Test
    @TestId("SAVE-25")
    @TestDuration(DurationCategory.LONG)
    fun reopeningASavedCopyTargetsItselfForTheNextSave() {
        grantAllFilesAccess()

        val saved = saveOneHighlightAsNewCopy(SaveFixtures.SMALL)
        val sourceBefore = SaveDigest.of(saved.source)
        val copyBefore = SaveDigest.awaitSettled(saved.copy)
        val destination = Uri.parse(saved.destinationUri)

        ActivityScenario.launch<MainActivity>(intentFor(destination)).use {
            awaitReaderReady()
            val journal = journal()

            makeOneHighlight(SaveFixtures.SMALL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            AnnotationReaderUi.tapSaveFab()
            SaveSheet.assertNeverShown()
            SavePicker.assertNeverShown()

            awaitSaveCompleted(journal)
        }

        val copyAfter = SaveDigest.awaitSettled(saved.copy)
        assertTrue("the copy is the file that gets rewritten", copyAfter != copyBefore)
        SaveDigest.assertUnchanged("the original must stay untouched", saved.source, sourceBefore)
        assertTrue(
            "the persisted write grant on the copy must survive the second save",
            SaveDestinations.persistedWriteGrantExists(destination),
        )

        reopen(saved.copy) {
            assertEquals("the copy carries both highlights", 2, highlightGroupsOnPage(0).size)
        }
    }

    @Test
    @TestId("SAVE-26")
    fun aRememberedDestinationNeverLeaksToADifferentDocument() {
        grantAllFilesAccess()
        val decoy = place(SaveFixtures.DECOY)
        val decoyBefore = SaveDigest.of(decoy)

        openReader(SaveFixtures.NORMAL) { _, file ->
            alwaysFinishActivities.enable()
            val sourceBefore = SaveDigest.of(file)
            val sourceKey = SaveDestinations.sourceKeyOf(documentUri())
            val journal = journal()

            makeOneHighlight(SaveFixtures.NORMAL)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.awaitShown()
            SavePicker.createInDownloads(decoy.name)

            awaitSaveSettled()

            val decoyAfter = SaveDigest.awaitSettled(decoy)
            val sameDocumentRestored = reader.readStateOrNull()?.documentUri
                ?.contains(file.name) == true
            if (sameDocumentRestored) {
                assertTrue(
                    "a save that returns to the document that started it must land on the picked file",
                    decoyAfter != decoyBefore,
                )
            } else {
                assertEquals(
                    "a save that returns to another document must be dropped, not applied",
                    decoyBefore,
                    decoyAfter,
                )
            }
            SaveDigest.assertUnchanged("the source document is never the destination here", file, sourceBefore)

            val keys = SaveDestinations.sourceKeys()
            assertTrue(
                "no destination may be recorded against a document that did not start the save, read $keys",
                keys.all { it == sourceKey },
            )
        }

        SavePicker.robot().let { picker ->
            if (picker.isShowing()) {
                picker.navigateToDownloads()
                picker.cancel()
            }
        }
    }

    private companion object {
        const val IDENTITY_PREFIX = "h2"
    }
}
