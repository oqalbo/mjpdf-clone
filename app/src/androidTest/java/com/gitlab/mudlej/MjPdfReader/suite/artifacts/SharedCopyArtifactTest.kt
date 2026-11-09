// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import com.gitlab.mudlej.MjPdfReader.suite.annotationsave.SaveFaults
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SharedCopyArtifactTest : ArtifactTestCase() {

    @Before
    fun startFromAKnownCopyMode() {
        SharedDocumentFlow.clear()
        MediaStoreProbe.deleteUnder(MediaStoreProbe.DOCUMENTS_PATH)
        runCatching { AppDirectories.publicDurableCopies.deleteRecursively() }
        cleanup.ownMediaUnder(MediaStoreProbe.DOCUMENTS_PATH)
    }

    @After
    fun releaseTheStubProvider() {
        SharedDocumentFlow.clear()
        SaveFaults.clear()
        MediaStoreProbe.deleteUnder(MediaStoreProbe.DOCUMENTS_PATH)
        runCatching { AppDirectories.publicDurableCopies.deleteRecursively() }
    }

    @Test
    @TestId("ART-51")
    fun aDurableCopyOfASharedDocumentIsByteIdenticalAndFullyPublished() {
        SharedDocumentFlow.requireProviderInstalled()
        seedCopyMode(SharedCopyMode.ALWAYS_COPY)

        val source = fixture(ArtifactFixtures.SMALL)
        val sourceDigest = ArtifactBytes.sha256(source)
        val sourceLength = source.length()
        val sourcePages = ProducedPdf.pageCount(source)
        val served = SharedDocumentFlow.serve(SHARED_NAME, source)

        openReader(served).use {
            val hash = awaitFileHash()
            val row = MediaStoreProbe.awaitPublishedFileUnder(
                MediaStoreProbe.DOCUMENTS_PATH,
                sourceLength,
                PDF_MIME_TYPE,
                Await.VERY_LONG_TIMEOUT_MS,
            )
            val record = Records.awaitRow(
                hash,
                "the record for $hash is repointed at the durable copy",
                Await.LONG_TIMEOUT_MS,
            ) { it != null && it.sourceUri == served.toString() }

            assertTrue(
                "the durable copy must be named as a PDF, it reads ${row.displayName}",
                row.displayName.endsWith(PDF_SUFFIX),
            )
            assertEquals("the durable copy must declare the PDF type", PDF_MIME_TYPE, row.mimeType)
            assertEquals(
                "the published row must report the complete durable copy size",
                sourceLength,
                row.size,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                assertEquals(
                    "the durable copy must be published rather than left pending, a pending row is invisible " +
                        "to every file manager on the device",
                    0,
                    row.pending,
                )
            }

            val copied = MediaStoreProbe.bytesOf(row)
            assertEquals("the durable copy must be the same length as the shared document", sourceLength, copied.size.toLong())
            assertEquals(
                "the durable copy must be byte identical to the document the user opened",
                sourceDigest,
                ArtifactBytes.sha256(copied),
            )

            assertNotNull("a record must exist for the shared document", record)
            assertEquals(
                "the record must remember where the document originally came from",
                served.toString(),
                record?.sourceUri,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertEquals(
                    "the record must now open the MediaStore copy the app owns rather than the one shot " +
                        "provider URI, it points at ${record?.uri}",
                    MEDIA_AUTHORITY,
                    record?.uri?.authority,
                )
            } else {
                assertEquals(
                    "the record must now open the public durable file the app owns rather than the one shot " +
                        "provider URI",
                    Uri.fromFile(File(AppDirectories.publicDurableCopies, row.displayName)),
                    record?.uri,
                )
            }
            assertEquals(
                "reading the record's URI must give back the same document",
                sourceDigest,
                ArtifactBytes.sha256(readBytes(record!!.uri)),
            )
            assertEquals("the record must be keyed on the hash the reader resolved", hash, record.hash)

            assertEquals(
                "the private fallback must stay empty because the public path succeeded",
                emptyList<String>(),
                AppDirectories.namesIn(AppDirectories.importedCopies),
            )
            assertEquals(
                "no partial copy may survive in the private fallback",
                emptyList<String>(),
                AppDirectories.partialFilesIn(AppDirectories.importedCopies),
            )
            assertEquals(
                "no partial copy may survive in the public folder",
                emptyList<String>(),
                AppDirectories.partialFilesIn(AppDirectories.publicDurableCopies),
            )

            val reparsed = MediaStoreProbe.writeTo(row, scratch("art51-copy.pdf"))
            assertEquals(
                "the durable copy must reparse with the source's page count",
                sourcePages,
                ProducedPdf.pageCount(reparsed),
            )
        }
    }

    @Test
    @TestId("ART-53")
    @TestDuration(DurationCategory.NORMAL)
    fun anIncognitoSessionWritesTheSavedPdfAndNothingElse() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, INCOGNITO_NAME)
        val sourcePages = ProducedPdf.pageCount(document)
        var incognitoHash = ""

        openReader(document, incognito = true).use {
            incognitoHash = awaitFileHash()
            reader.highlightWordOnCurrentPage()
            val produced = saveCopyAs(INCOGNITO_COPY_NAME)

            assertEquals(
                "the file the user asked for must be written in full",
                sourcePages,
                ProducedPdf.pageCount(produced),
            )
            assertEquals(
                "the saved copy must carry the one highlight the incognito session made",
                1,
                ProducedPdf.appOwnedHighlights(produced).size,
            )

            SaveFaults.armFailWrite()
            reader.highlightWordOnCurrentPage(verticalFraction = SECOND_WORD_Y)
            failSaveToRememberedDestination()
        }
        SaveFaults.clear()

        assertEquals(
            "an incognito failure must write no rescue copy, that copy is history the user did not ask for. " +
                "The rescue directory holds ${AppDirectories.namesIn(AppDirectories.annotationRescue)}",
            emptyList<String>(),
            AppDirectories.namesIn(AppDirectories.annotationRescue),
        )
        MediaStoreProbe.assertNoFilesUnder(MediaStoreProbe.DOCUMENTS_PATH)
        assertEquals(
            "an incognito session must leave no durable copy in the private fallback either",
            emptyList<String>(),
            AppDirectories.namesIn(AppDirectories.importedCopies),
        )
        AppDirectories.assertNoStagingLeftovers()
        assertNull(
            "an incognito session must write no reading history row",
            Records.byHash(incognitoHash),
        )
        assertFalse(
            "an incognito session must create no online download cache",
            AppDirectories.onlineCache.exists(),
        )

        val control = copyFixtureToDownloads(ArtifactFixtures.SMALL, CONTROL_SAVE_NAME)
        SaveFaults.armFailWrite()
        openReader(control).use {
            val controlHash = awaitFileHash()
            reader.highlightWordOnCurrentPage()
            failASaveInto(CONTROL_FAILED_NAME)

            val rescued = SaveFaults.awaitRescueFileCount(1, Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the same failing save outside incognito must write exactly one rescue copy, which is what " +
                    "proves the incognito assertion above is a suppression and not a save that never ran",
                1,
                rescued.size,
            )
            assertNotNull(
                "the same document opened outside incognito must write a reading history row, which is what " +
                    "proves the absent row above is the incognito rule and not a missing identity",
                Records.awaitRowExists(controlHash, Await.LONG_TIMEOUT_MS),
            )
        }
    }

    @Test
    @TestId("ART-54")
    fun aHostileDisplayNameBecomesASafeFileName() {
        SharedDocumentFlow.requireProviderInstalled()
        seedCopyMode(SharedCopyMode.ALWAYS_COPY)

        val source = fixture(ArtifactFixtures.SMALL)
        val sourcePages = ProducedPdf.pageCount(source)
        val hostile = HOSTILE_PREFIX + "\t" + "a".repeat(HOSTILE_TAIL_LETTERS) + PDF_SUFFIX
        val served = SharedDocumentFlow.serveUnder(HOSTILE_KEY, hostile, source)

        openReader(served).use {
            awaitFileHash()
            val row = MediaStoreProbe.awaitFilesUnder(MediaStoreProbe.DOCUMENTS_PATH, 1, Await.VERY_LONG_TIMEOUT_MS)
                .single()
            val name = row.displayName

            FORBIDDEN_NAME_CHARACTERS.forEach { forbidden ->
                assertFalse(
                    "a copy's file name may not carry $forbidden, the produced name reads \"$name\"",
                    name.contains(forbidden),
                )
            }
            assertFalse(
                "a copy's file name may carry no control character, the produced name reads \"$name\"",
                name.any { it.code < FIRST_PRINTABLE_CODE },
            )
            assertEquals(
                "the produced name must end in exactly one .pdf, it reads \"$name\"",
                1,
                name.windowed(PDF_SUFFIX.length).count { it == PDF_SUFFIX },
            )
            assertFalse("the produced name may not start with a dot, it reads \"$name\"", name.startsWith("."))
            assertFalse(
                "the produced name may not end with a dot, it reads \"$name\"",
                name.removeSuffix(PDF_SUFFIX).endsWith("."),
            )
            assertTrue(
                "the base name must be capped at $MAX_BASE_NAME characters so a 400 character display name " +
                    "cannot produce an unopenable file, the produced name is ${name.length} characters long",
                name.removeSuffix(PDF_SUFFIX).length <= MAX_BASE_NAME,
            )
            assertEquals(
                "the copy must land in the app's own folder, a name carrying path separators must not walk out " +
                    "of it",
                MediaStoreProbe.DOCUMENTS_PATH,
                row.relativePath,
            )

            val reparsed = MediaStoreProbe.writeTo(row, scratch("art54-copy.pdf"))
            assertEquals(
                "the copy written under a hostile name must still be the whole document",
                sourcePages,
                ProducedPdf.pageCount(reparsed),
            )
        }
    }

    private fun seedCopyMode(mode: SharedCopyMode) {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.keepSharedCopiesKey to true,
                Preferences.sharedCopyModeKey to mode.name,
            )
        )
    }

    private fun awaitFileHash(): String = Await.untilValue(
        "the reader resolves the document identity",
        Await.LONG_TIMEOUT_MS,
        read = { reader.state().fileHash },
    ) { !it.isNullOrEmpty() }!!

    private fun failASaveInto(name: String) {
        val target = File(downloads(), name)
        cleanup.own(target)
        reader.chooseSaveAsCopy()
        picker.awaitShown()
        picker.navigateToDownloads()
        picker.typeFileNameAndSave(name)
        AnnotationDialogs.awaitTitle(string(R.string.annotation_save_failed_title), Await.VERY_LONG_TIMEOUT_MS)
        AnnotationDialogs.pressNegative()
    }

    private fun failSaveToRememberedDestination() {
        reader.tapSave()
        AnnotationDialogs.awaitTitle(string(R.string.annotation_save_incomplete_title), Await.VERY_LONG_TIMEOUT_MS)
        AnnotationDialogs.pressNegative()
    }

    private fun readBytes(uri: Uri): ByteArray =
        TestEnvironment.targetContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw AssertionError("The record URI $uri could not be opened for reading")

    private fun scratch(name: String): File = suite.testDirectory.fileNamed(name)

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val SHARED_NAME = "art51-shared.pdf"
        const val HOSTILE_KEY = "art54-hostile.pdf"
        const val INCOGNITO_NAME = "art53-incognito.pdf"
        const val INCOGNITO_COPY_NAME = "art53-incognito-copy.pdf"
        const val CONTROL_SAVE_NAME = "art53-control.pdf"
        const val CONTROL_FAILED_NAME = "art53-control-failed.pdf"

        const val PDF_SUFFIX = ".pdf"
        const val PDF_MIME_TYPE = "application/pdf"
        const val MEDIA_AUTHORITY = "media"
        const val SECOND_WORD_Y = 0.19f
        const val MAX_BASE_NAME = 120
        const val FIRST_PRINTABLE_CODE = 0x20
        const val HOSTILE_TAIL_LETTERS = 400
        const val HOSTILE_PREFIX = "../../evil:name*?<>|"

        val FORBIDDEN_NAME_CHARACTERS = listOf("/", "\\", ":", "*", "?", "\"", "<", ">", "|")
    }
}
