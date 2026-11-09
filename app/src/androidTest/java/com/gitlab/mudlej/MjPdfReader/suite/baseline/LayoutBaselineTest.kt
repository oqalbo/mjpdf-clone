// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.navigation.NavigationRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class LayoutBaselineTest : BaselineTestCase() {

    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("VIS-26")
    @TestDuration(DurationCategory.NORMAL)
    fun aLeftToRightDocumentAdvancesAndLaysOutToTheRight() {
        SettingsSeeder.seed(Preferences.horizontalScrollKey, true)
        openGoldenBookPage()

        advanceOnePage()

        val settled = state()
        assertEquals(
            "the page after one advance",
            (BaselineFixtures.BOOK_BODY_PAGE_INDEX + 1) as Int?,
            settled.page,
        )
        assertEquals("the resolved scroll direction is horizontal", false, settled.swipeVertical)

        assertScreenMatches(
            goldenName = "VIS-26-horizontal-ltr-page42.png",
            expectation = readerExpectation(
                "TLCL 25.12A page index ${BaselineFixtures.BOOK_BODY_PAGE_INDEX + 1} in horizontal scroll " +
                    "with left to right order",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX + 1,
            ),
        )
    }

    @Test
    @TestId("VIS-27")
    @TestDuration(DurationCategory.NORMAL)
    fun aRightToLeftBookAdvancesInTheCorrectDirection() {
        SettingsSeeder.seed(Preferences.horizontalScrollKey, true)
        openFullscreenPage(BaselineFixtures.ARABIC)

        val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)
        val record = Records.awaitRow(
            fileHash,
            "the record for arabic-rtl.pdf records a detected reading direction",
            Await.LONG_TIMEOUT_MS,
        ) { it?.detectedReadingDirection != null }
        assertNotNull("a reading direction was detected", record?.detectedReadingDirection)
        assertEquals(
            "the detected reading direction is right to left",
            RTL_DIRECTION,
            record?.detectedReadingDirection,
        )
        assertEquals("the reader lays the document out right to left", true, state().readingDirectionRtl)

        advanceOnePage()
        assertEquals("the page after one advance", 1 as Int?, state().page)

        assertScreenMatches(
            goldenName = "VIS-27-horizontal-rtl-page2.png",
            expectation = readerExpectation(
                "arabic-rtl.pdf page index 1 in horizontal right to left layout",
                pageIndex = 1,
            ),
        )
    }

    @Test
    @TestId("VIS-28")
    @TestDuration(DurationCategory.NORMAL)
    fun aStoredReadingDirectionOverrideWinsOverDetection() {
        SettingsSeeder.seed(Preferences.horizontalScrollKey, true)
        val file = baselineFixture(BaselineFixtures.ARABIC)

        launchAt(file)
        val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)
        Records.awaitRowExists(fileHash, Await.LONG_TIMEOUT_MS)
        Records.database.pdfRecordDao().updateReadingDirectionOverride(fileHash, LTR_DIRECTION)
        finishReader()

        launchAt(file)
        enterFullscreenAndSettle()
        jumpToPageIndex(0)
        assertEquals(
            "the stored override forces left to right",
            false,
            state().readingDirectionRtl,
        )

        advanceOnePage()
        assertEquals("the page after one advance", 1 as Int?, state().page)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-28-rtl-overridden-to-ltr.png",
            actual = capture,
            expectation = readerExpectation(
                "arabic-rtl.pdf page index 1 laid out left to right by a stored override",
                pageIndex = 1,
            ),
        )
        CrossGolden.assertDiffers(
            capture,
            "VIS-27-horizontal-rtl-page2.png",
            "an override that reverses the page order must visibly change the layout",
        )
    }

    @Test
    @TestId("VIS-29")
    fun singlePageModeShowsOneWholePageWithNoNeighbourVisible() {
        SettingsSeeder.seedReadingMode(singlePage = true, horizontalScroll = false, dualPage = false)
        openGoldenBookPage(0)

        val settled = state()
        assertEquals("the page the capture is of", 0 as Int?, settled.page)
        assertEquals("single page mode collapses the row to one page", 1 as Int?, settled.pagesPerRow)
        assertEquals("the single visible row starts at the cover", 0 as Int?, settled.rowFirstPage)
        assertEquals("the single visible row ends at the cover", 0 as Int?, settled.rowLastPage)
        assertNotNull("the hook reports the fit both zoom", settled.zoom)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-29-single-page-mode.png",
            actual = capture,
            expectation = readerExpectation("TLCL 25.12A cover alone in single page mode", pageIndex = 0),
        )
    }

    @Test
    @TestId("VIS-30")
    fun aTwoPageSpreadPairsTheCorrectPagesInTheCorrectOrder() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.dualPageModeKey to true,
                Preferences.dualPageFirstPageAloneKey to false,
                Preferences.horizontalScrollKey to false,
            )
        )
        openGoldenBookPage(0)

        val settled = state()
        assertEquals("dual page mode puts two pages in a row", 2 as Int?, settled.pagesPerRow)
        assertEquals("the first row starts at page index 0", 0 as Int?, settled.rowFirstPage)
        assertEquals("the first row ends at page index 1", 1 as Int?, settled.rowLastPage)

        assertFullTilesDoNotDependOnPreviews()

        assertScreenMatches(
            goldenName = "VIS-30-dual-page.png",
            expectation = readerExpectation("TLCL 25.12A pages 1 and 2 side by side", pageIndex = 0),
        )
    }

    @Test
    @TestId("VIS-31")
    fun theCoverPageConventionShiftsEverySubsequentPairing() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.dualPageModeKey to true,
                Preferences.dualPageFirstPageAloneKey to true,
                Preferences.horizontalScrollKey to false,
            )
        )
        openGoldenBookPage(0)

        val settled = state()
        assertEquals("first page alone is resolved", true, settled.firstPageAlone)
        assertEquals("the first row holds page index 0 alone", 0 as Int?, settled.rowFirstPage)
        assertEquals("and ends there too", 0 as Int?, settled.rowLastPage)

        assertFullTilesDoNotDependOnPreviews()

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-31-dual-page-first-alone.png",
            actual = capture,
            expectation = readerExpectation("TLCL 25.12A cover alone in the first row", pageIndex = 0),
        )
        CrossGolden.assertDiffers(
            capture,
            "VIS-30-dual-page.png",
            "the cover page convention must shift the pairing and so change the first row",
        )
    }

    @Test
    @TestId("VIS-32")
    fun pagesButtDirectlyAgainstEachOtherWhenTheGapIsOff() {
        SettingsSeeder.seed(Preferences.spaceBetweenPagesKey, false)
        openGoldenBookPage(0)

        assertEquals("the applied page spacing is zero", 0 as Int?, state().spacing)

        val boundaryOffset = firstPageBoundaryOffset()
        setViewport(state().zoom ?: 1f, 0f, boundaryOffset)
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-32-no-page-spacing.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A with the page 1 to page 2 boundary at the viewport centre and no gap",
            ),
        )

        val background = ViewUi.pdfViewBackgroundColor()
        val centre = capture.height / 2
        val longest = PixelRegions.longestRun(capture, centre, 0, capture.width) {
            PixelRegions.isNear(it, background)
        }
        assertTrue(
            "the scan line across the page boundary holds a background run $longest pixels long, which is a " +
                "visible gap between two pages that are supposed to butt directly together",
            longest <= MAX_BACKGROUND_RUN,
        )
    }

    private fun assertFullTilesDoNotDependOnPreviews() {
        val view = ViewUi.onView(R.id.pdfView) { it as PDFView }
        val field = PDFView::class.java.getDeclaredField("previewStore").apply {
            isAccessible = true
        }
        val original = TestEnvironment.onMainThread { field.get(view) }
        val withPreviews = captureScreen()
        val withoutPreviews = try {
            TestEnvironment.onMainThread { field.set(view, null) }
            captureScreen()
        } finally {
            TestEnvironment.onMainThread { field.set(view, original) }
        }
        try {
            Baselines.assertByteIdentical(
                withPreviews,
                withoutPreviews,
                "finished tiles render the same pixels regardless of their preview underlay",
            )
        } finally {
            withPreviews.recycle()
            withoutPreviews.recycle()
        }
    }

    private fun advanceOnePage() {
        val beforeState = state()
        val before = beforeState.page ?: 0
        if (beforeState.swipeVertical == false) {
            val (fromX, toX) = if (beforeState.readingDirectionRtl == true) {
                HORIZONTAL_NEAR to HORIZONTAL_FAR
            } else {
                HORIZONTAL_FAR to HORIZONTAL_NEAR
            }
            navigation.swipeWithinPage(fromX, HORIZONTAL_CENTRE, toX, HORIZONTAL_CENTRE)
        } else {
            reader.swipeForward()
        }
        reader.awaitPageIndex(before + 1, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    private fun firstPageBoundaryOffset(): Float {
        val settled = state()
        val pageHeight = settled.pageHeight
            ?: throw AssertionError("The reader state hook reports no scaled page height to find the boundary with")
        val viewHeight = settled.viewHeight
            ?: throw AssertionError("The reader state hook reports no view height to centre the boundary in")
        return -(pageHeight - viewHeight / 2f)
    }

    private companion object {
        const val RTL_DIRECTION = "rtl"
        const val LTR_DIRECTION = "ltr"
        const val MAX_BACKGROUND_RUN = 2
        const val HORIZONTAL_NEAR = 0.2f
        const val HORIZONTAL_FAR = 0.8f
        const val HORIZONTAL_CENTRE = 0.5f
    }
}
