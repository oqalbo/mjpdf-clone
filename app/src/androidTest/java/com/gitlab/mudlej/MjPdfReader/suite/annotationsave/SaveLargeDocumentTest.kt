// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationGeometry
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ProducedPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SaveLargeDocumentTest : SaveTestCase() {

    @Test
    @TestId("SAVE-19")
    fun aSecondSaveKeepsBothHighlightsAndEveryPage() {
        grantAllFilesAccess()
        var pagesBefore = 0

        openReader(SaveFixtures.LARGE) { _, file ->
            pagesBefore = reader.readState().pageCount!!
            val journal = journal()

            makeOneHighlight(SaveFixtures.LARGE)
            AnnotationJournalFile.awaitLineCount(journal, 1)
            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            awaitSaveCompleted(journal)
            SaveDigest.awaitSettled(file)

            AnnotationGeometry.jumpToPage(FAR_PAGE_INDEX)
            reader.awaitPageIndex(FAR_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

            makeOneHighlight(SaveFixtures.LARGE)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            AnnotationReaderUi.tapSaveFab()
            SaveSheet.assertNeverShown()
            SavePicker.assertNeverShown()

            awaitSaveCompleted(journal)
            SaveDigest.awaitSettled(file)
            assertTrue(
                "no partial write may be left in Downloads",
                SaveFixtures.savingSiblingsWithPrefix(cleanup.prefix()).isEmpty(),
            )
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.LARGE))
        for (page in listOf(0, FAR_PAGE_INDEX)) {
            assertEquals(
                "the saved PDF itself carries one highlight group on page $page",
                1,
                ProducedPdf.highlightsOn(file, page).map { it.groupKey }.distinct().size,
            )
        }
        reopen(file) {
            assertEquals("the second save keeps every page", pagesBefore, reader.readState().pageCount)
            assertEquals("the first highlight survives the second save", 1, highlightGroupsOnPage(0).size)

            AnnotationGeometry.jumpToPage(FAR_PAGE_INDEX)
            reader.awaitPageIndex(FAR_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the second highlight is on the page it was made on",
                1,
                highlightGroupsOnPage(FAR_PAGE_INDEX).size,
            )
        }
    }

    @Test
    @TestId("SAVE-21")
    fun aVeryLargeDocumentSavesWithoutLosingPages() {
        grantAllFilesAccess()
        var pagesBefore = 0
        val lastPageIndex = SaveFixtures.LARGE_PAGES - 1

        openReader(SaveFixtures.LARGE) { _, file ->
            pagesBefore = reader.readState().pageCount!!
            val journal = journal()

            AnnotationGeometry.jumpToPage(lastPageIndex)
            reader.awaitPageIndex(lastPageIndex, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

            makeOneHighlight(SaveFixtures.LARGE)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseUpdateExisting()
            awaitSaveCompleted(journal)
            SaveDigest.awaitSettled(file)

            assertTrue(
                "no partial write may be left in Downloads",
                SaveFixtures.savingSiblingsWithPrefix(cleanup.prefix()).isEmpty(),
            )
        }

        val file = File(SaveFixtures.downloads, ownedName(SaveFixtures.LARGE))
        reopen(file) {
            assertEquals("the saved document keeps every page", pagesBefore, reader.readState().pageCount)

            AnnotationGeometry.jumpToPage(lastPageIndex)
            reader.awaitPageIndex(lastPageIndex, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the highlight is on the last page where it was made",
                1,
                highlightGroupsOnPage(lastPageIndex).size,
            )
        }
    }

    private companion object {
        const val FAR_PAGE_INDEX = 480
    }
}
