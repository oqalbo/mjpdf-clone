// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.QuoteCardTheme
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.ShareQuoteDebug
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PrintStagingArtifactTest : ArtifactTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @After
    fun releaseTheHeldCard() {
        runCatching { ShareQuoteDebug.clear() }
    }

    @Test
    @TestId("ART-55")
    fun theStagedPrintDocumentCarriesTheSourcePageCountAndPageSizes() {
        PrintFlow.requireSpoolerInstalled()
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, PRINT_NAME)
        val sourcePages = ProducedPdf.pageCount(document)
        val sourceSizes = ProducedPdf.pageSizes(document)
        val sourceDigest = ArtifactBytes.sha256(document)
        PrintFlow.seedAsPrimaryToolbarAction()

        openReader(document).use {
            val staged = PrintFlow.startAndAwaitStagedFile()

            assertEquals(
                "print staging must leave exactly one file behind while the preview is up, it holds " +
                    AppDirectories.namesIn(AppDirectories.printStaging),
                listOf(staged.name),
                AppDirectories.printStagedFiles().map { it.name },
            )
            assertEquals(
                "the staged copy must carry every page of the document",
                sourcePages,
                ProducedPdf.pageCount(staged),
            )
            assertEquals(
                "the staged copy must carry the source's page sizes point for point, so what reaches the " +
                    "printer is the right paper size",
                sourceSizes,
                ProducedPdf.pageSizes(staged),
            )
            assertEquals(
                "an unprotected document is staged by a plain byte copy, so the staged file must be byte " +
                    "identical to the source",
                sourceDigest,
                ArtifactBytes.sha256(staged),
            )

            PrintFlow.awaitSpoolerShown()
            PrintFlow.dismiss()
            AppDirectories.awaitPrintStagingEmpty()

            assertEquals(
                "dismissing the print preview runs PdfDocumentAdapter.onFinish, which must delete the staged " +
                    "copy of the user's book",
                emptyList<String>(),
                AppDirectories.printStagedFiles().map { it.name },
            )
        }
    }

    @Test
    @TestId("ART-56")
    fun theStagedPrintDocumentOfAProtectedFileIsDecryptedAndComplete() {
        PrintFlow.requireSpoolerInstalled()
        val source = fixture(ArtifactFixtures.PROTECTED)
        val sourceDigest = ArtifactBytes.sha256(source)
        val sourcePages = ProducedPdf.pageCount(source, ArtifactFixtures.PROTECTED_PASSWORD)
        val sourceFirstPage = ProducedPdf.pageText(source, 0, ArtifactFixtures.PROTECTED_PASSWORD)
        PrintFlow.seedAsPrimaryToolbarAction()

        openProtectedReader(source, ArtifactFixtures.PROTECTED_PASSWORD).use {
            val staged = PrintFlow.startAndAwaitStagedFile(Await.VERY_LONG_TIMEOUT_MS)

            assertTrue(
                "the staged copy of a protected document must open with no password at all, otherwise the " +
                    "print framework cannot render it",
                ProducedPdf.opensWithoutPassword(staged),
            )
            assertEquals(
                "the decrypted staged copy must carry every page of the protected document",
                sourcePages,
                ProducedPdf.pageCount(staged),
            )
            assertEquals(
                "the decrypted staged copy must carry the protected document's own text, not an empty page",
                sourceFirstPage,
                ProducedPdf.pageText(staged, 0),
            )

            PrintFlow.awaitSpoolerShown()
            PrintFlow.dismiss()
            AppDirectories.awaitPrintStagingEmpty()

            assertEquals(
                "the decrypted copy must not outlive the print preview, it is an unprotected copy of a file " +
                    "the owner protected",
                emptyList<String>(),
                AppDirectories.printStagedFiles().map { it.name },
            )
            assertEquals(
                "printing must not touch the protected source",
                sourceDigest,
                ArtifactBytes.sha256(source),
            )
            assertTrue(
                "the protected source must still refuse to open without its password",
                ProducedPdf.refusesWithoutPassword(source),
            )
        }
    }

    @Test
    @TestId("ART-57")
    @TestDuration(DurationCategory.NORMAL)
    fun staleStagingAndShareFilesAreSweptWhileFreshOnesAreKept() {
        PrintFlow.requireSpoolerInstalled()
        IntentStubs.stubChooser()
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, SWEEP_NAME)
        PrintFlow.seedAsPrimaryToolbarAction()

        val stalePrint = AppDirectories.writePlaceholder(AppDirectories.printStaging, STALE_PRINT)
        val freshPrint = AppDirectories.writePlaceholder(AppDirectories.printStaging, FRESH_PRINT)
        val staleCard = AppDirectories.writePlaceholder(AppDirectories.shareCache, STALE_CARD)
        val freshCard = AppDirectories.writePlaceholder(AppDirectories.shareCache, FRESH_CARD)
        AppDirectories.ageFile(stalePrint, TWO_HOURS_MS)
        AppDirectories.ageFile(staleCard, TWENTY_FIVE_HOURS_MS)

        assertTrue("the seeded stale print file must exist before the sweep runs", stalePrint.isFile)
        assertTrue("the seeded stale quote card must exist before the sweep runs", staleCard.isFile)

        openReader(document).use {
            val staged = PrintFlow.startAndAwaitStagedFile()

            assertTrue(
                "a freshly staged print file must exist at the moment the sweep is judged, it is " +
                    "${staged.absolutePath}",
                staged.isFile && staged.length() > 0L,
            )
            assertFalse(
                "a staged print file older than the one hour cutoff must be swept, ${stalePrint.name} is still " +
                    "in ${AppDirectories.namesIn(AppDirectories.printStaging)}",
                stalePrint.exists(),
            )
            assertTrue(
                "a staged print file inside the one hour cutoff must be kept, ${freshPrint.name} is gone from " +
                    AppDirectories.namesIn(AppDirectories.printStaging),
                freshPrint.exists(),
            )

            PrintFlow.awaitSpoolerShown()
            PrintFlow.dismiss()

            val activity = reader.requireActivity()
            QuoteCardFlow.open(activity, SWEEP_QUOTE, BOOK_NAME, AUTHOR)
            QuoteCardFlow.selectTheme(QuoteCardTheme.LIGHT)
            val card = QuoteCardFlow.share()

            assertTrue(
                "a freshly written quote card must exist at the moment the sweep is judged, it is " +
                    "${card.absolutePath}",
                card.isFile && card.length() > 0L,
            )
            assertFalse(
                "a quote card older than the 24 hour cutoff must be swept, ${staleCard.name} is still in " +
                    AppDirectories.namesIn(AppDirectories.shareCache),
                staleCard.exists(),
            )
            assertTrue(
                "a quote card inside the 24 hour cutoff must be kept, ${freshCard.name} is gone from " +
                    AppDirectories.namesIn(AppDirectories.shareCache),
                freshCard.exists(),
            )
        }
    }

    private companion object {
        const val PRINT_NAME = "art55-print.pdf"
        const val SWEEP_NAME = "art57-sweep.pdf"

        const val STALE_PRINT = "print-old.pdf"
        const val FRESH_PRINT = "print-new.pdf"
        const val STALE_CARD = "MJ_quote_old.png"
        const val FRESH_CARD = "MJ_quote_new.png"

        const val TWO_HOURS_MS = 2L * 60 * 60 * 1000
        const val TWENTY_FIVE_HOURS_MS = 25L * 60 * 60 * 1000

        const val BOOK_NAME = "The Memory Model"
        const val AUTHOR = "A. Writer"
        const val SWEEP_QUOTE = "One sentence is enough to prove the sweep ran before the card was written."
    }
}
