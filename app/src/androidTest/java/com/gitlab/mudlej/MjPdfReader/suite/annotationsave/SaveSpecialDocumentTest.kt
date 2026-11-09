// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.annotations.SignaturePad
import com.gitlab.mudlej.MjPdfReader.suite.baseline.BaselineCapture
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.PdfRenderOracle
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.net.LocalPdfServer
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SaveSpecialDocumentTest : SaveTestCase() {

    @Test
    @TestId("SAVE-54")
    fun anOnlineDocumentSavesIntoTheDownloadsCopyAndRefreshesTheCachedCopy() {
        grantAllFilesAccess()
        val servedName = "${cleanup.prefix()}online.pdf"
        val downloadsCopy = place(SaveFixtures.SMALL, servedName)
        val before = SaveDigest.of(downloadsCopy)

        val server = LocalPdfServer()
        try {
            server.start()
            val url = server.servePdfBytes("/$servedName", downloadsCopy.readBytes())

            reader.withExternalIntent(ReaderRobot.viewIntent(Uri.parse(url))) {
                reader.acceptCleartextWarning()
                awaitReaderReady()
                val journal = journal()

                makeOneHighlight(SaveFixtures.SMALL)
                AnnotationJournalFile.awaitLineCount(journal, 1)

                tapSaveAndAwaitSheet()
                SaveSheet.chooseUpdateExisting()
                Snackbars.awaitText(string(R.string.highlights_saved), Await.VERY_LONG_TIMEOUT_MS)
                awaitSaveSettled()
                AnnotationReaderUi.awaitSaveFabGone(Await.LONG_TIMEOUT_MS)
            }

            SaveDigest.awaitSettled(downloadsCopy)
            assertTrue(
                "the annotated document must land in the Downloads copy the user can find",
                SaveDigest.of(downloadsCopy) != before,
            )

            val cached = onlineCacheEntries().maxByOrNull { it.lastModified() }
                ?: throw AssertionError("The online document store holds no cached copy for the served url")
            Await.until("the cached copy matches the Downloads file", Await.LONG_TIMEOUT_MS) {
                cached.length() == downloadsCopy.length()
            }
        } finally {
            runCatching { server.shutdown() }
        }

        reopen(downloadsCopy) {
            assertEquals("the saved file carries the highlight", 1, highlightGroupsOnPage(0).size)
        }
    }

    @Test
    @TestId("SAVE-55")
    fun aSaveOfAScannedImageOnlyDocumentKeepsItsPagesIntact() {
        grantAllFilesAccess()
        var pagesBefore = 0
        var sourcePageDigest = ""
        var sourceReaderDigest = ""

        openReader(SaveFixtures.SCANNED) { _, file ->
            pagesBefore = reader.readState().pageCount!!
            sourcePageDigest = PdfRenderOracle.pageDigest(file)
            sourceReaderDigest = PdfRenderOracle.digest(BaselineCapture.view(R.id.pdfView))
            val journal = journal()

            SignaturePad.openFromReaderActions()
            SignaturePad.awaitDialog()
            SignaturePad.drawOneStroke()
            SignaturePad.awaitPlaceEnabled(true)
            SignaturePad.pressPlace()
            AnnotationDump.awaitPendingStamp()

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            awaitSaveSettled()
            AnnotationReaderUi.awaitSaveFabGone(Await.LONG_TIMEOUT_MS)
            AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)
            SaveDigest.awaitSettled(file)

            assertTrue("the saved file must be a pdf", SaveDigest.startsWithPdfMagic(file))
            assertTrue(
                "no partial write may be left beside it",
                SaveFixtures.savingSiblingsWithPrefix(cleanup.prefix()).isEmpty(),
            )
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.SCANNED))
        val savedPageDigest = PdfRenderOracle.pageDigest(file)
        assertNotEquals(
            "page 1 rendered from the saved PDF must contain the placed signature",
            sourcePageDigest,
            savedPageDigest,
        )
        reopen(file) {
            assertEquals("an image only document keeps every page", pagesBefore, reader.readState().pageCount)
            reader.assertFullyRendered()
            assertEquals(
                "the reopened reader must be displaying the file that was saved in place",
                Uri.fromFile(file).toString(),
                reader.readState().documentUri,
            )
            assertNotEquals(
                "the reopened reader must visibly render the signature on page 1",
                sourceReaderDigest,
                PdfRenderOracle.digest(BaselineCapture.view(R.id.pdfView)),
            )
            assertEquals(
                "reparsing the reopened file keeps the signed page rendering",
                savedPageDigest,
                PdfRenderOracle.pageDigest(file),
            )
        }
    }

    private fun onlineCacheEntries(): List<File> =
        File(TestEnvironment.targetContext.cacheDir, "online-pdf").walkTopDown().filter { it.isFile }.toList()

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
