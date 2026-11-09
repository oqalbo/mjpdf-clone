// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReaderPageBaselineTest : BaselineTestCase() {

    @Test
    @TestId("VIS-01")
    fun theGoldenBooksDenseBodyPageRendersTheWayItDidBefore() {
        openGoldenBookPage()

        assertEquals("page count", BaselineFixtures.GOLDEN_BOOK_PAGES as Int?, state().pageCount)

        assertScreenMatches(
            goldenName = "VIS-01-tlcl-page41.png",
            expectation = readerExpectation(
                "TLCL 25.12A page index ${BaselineFixtures.BOOK_BODY_PAGE_INDEX} in fullscreen",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
    }

    @Test
    @TestId("VIS-02")
    fun aPageReachedByNavigationRendersLikeAFirstPage() {
        openGoldenBookPage(BaselineFixtures.BOOK_SECOND_BODY_PAGE_INDEX)

        assertScreenMatches(
            goldenName = "VIS-02-tlcl-page61.png",
            expectation = readerExpectation(
                "TLCL 25.12A page index ${BaselineFixtures.BOOK_SECOND_BODY_PAGE_INDEX} in fullscreen",
                pageIndex = BaselineFixtures.BOOK_SECOND_BODY_PAGE_INDEX,
            ),
        )
    }

    @Test
    @TestId("VIS-03")
    fun anArabicPageShapesAndAlignsItsGlyphsCorrectly() {
        openFullscreenPage(BaselineFixtures.ARABIC)

        assertEquals("page count", BaselineFixtures.ARABIC_PAGES as Int?, state().pageCount)

        assertScreenMatches(
            goldenName = "VIS-03-arabic-page1.png",
            expectation = readerExpectation("arabic-rtl.pdf page index 0 in fullscreen", pageIndex = 0),
        )
    }

    @Test
    @TestId("VIS-04")
    fun arabicRenderingHoldsBeyondTheFirstPage() {
        openFullscreenPage(BaselineFixtures.ARABIC, BaselineFixtures.ARABIC_MID_PAGE_INDEX)

        assertScreenMatches(
            goldenName = "VIS-04-arabic-page9.png",
            expectation = readerExpectation(
                "arabic-rtl.pdf page index ${BaselineFixtures.ARABIC_MID_PAGE_INDEX} in fullscreen",
                pageIndex = BaselineFixtures.ARABIC_MID_PAGE_INDEX,
            ),
        )
    }

    @Test
    @TestId("VIS-05")
    fun anImageOnlyPageDecodesAndScalesCorrectly() {
        openFullscreenPage(BaselineFixtures.SCANNED)

        assertEquals("page count", BaselineFixtures.SCANNED_PAGES as Int?, state().pageCount)

        assertScreenMatches(
            goldenName = "VIS-05-scanned-page1.png",
            expectation = readerExpectation("scanned.pdf page index 0 in fullscreen", pageIndex = 0),
        )
    }

    @Test
    @TestId("VIS-06")
    fun anImagePageReachedByNavigationLandsAtFullQualityNotAsAPreview() {
        openFullscreenPage(BaselineFixtures.SCANNED, BaselineFixtures.SCANNED_MID_PAGE_INDEX)

        assertScreenMatches(
            goldenName = "VIS-06-scanned-page7.png",
            expectation = readerExpectation(
                "scanned.pdf page index ${BaselineFixtures.SCANNED_MID_PAGE_INDEX} in fullscreen",
                pageIndex = BaselineFixtures.SCANNED_MID_PAGE_INDEX,
            ),
        )
    }

    @Test
    @TestId("VIS-07")
    fun formFieldIndicatorsAreDrawnOverThePage() {
        openFullscreenPage(BaselineFixtures.FORMS)

        val fields = AnnotationDump.formFields(0)
        assertTrue("forms.pdf page index 0 must expose a text field", fields.any { it.isTextField })
        assertTrue("forms.pdf page index 0 must expose a checkbox", fields.any { it.isCheckbox })
        assertEquals(
            "forms.pdf page index 0 must expose both radio controls",
            2,
            fields.count { it.isRadioButton },
        )

        assertScreenMatches(
            goldenName = "VIS-07-forms-indicators.png",
            expectation = readerExpectation("forms.pdf page index 0 with field indicators", pageIndex = 0),
        )
    }

    @Test
    @TestId("VIS-08")
    fun linkVisualsRenderAroundTheAuthoredHitRegions() {
        openFullscreenPage(BaselineFixtures.LINKS)

        assertScreenMatches(
            goldenName = "VIS-08-links-page1.png",
            expectation = readerExpectation(
                "links.pdf page index 0 with authored link rectangles and labels, no link tapped",
                pageIndex = 0,
            ),
        )
    }

    @Test
    @TestId("VIS-09")
    fun theGoldenBooksCoverIsLaidOutWithoutLeakingItsPageTableSize() {
        openGoldenBookPage(0)

        assertEquals("page count", BaselineFixtures.GOLDEN_BOOK_PAGES as Int?, state().pageCount)

        assertScreenMatches(
            goldenName = "VIS-09-tlcl-cover.png",
            expectation = readerExpectation("TLCL 25.12A cover at page index 0 in fullscreen", pageIndex = 0),
        )
    }

    @Test
    @TestId("VIS-10")
    fun aDeepPageLandsWhereItShouldRatherThanDriftingByAFraction() {
        openGoldenBookPage(BaselineFixtures.BOOK_DEEP_PAGE_INDEX)

        assertScreenMatches(
            goldenName = "VIS-10-tlcl-page250.png",
            expectation = readerExpectation(
                "TLCL 25.12A page index ${BaselineFixtures.BOOK_DEEP_PAGE_INDEX} in fullscreen",
                pageIndex = BaselineFixtures.BOOK_DEEP_PAGE_INDEX,
            ),
        )
    }
}
