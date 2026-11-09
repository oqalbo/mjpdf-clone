// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class BaselineHarnessTest : BaselineTestCase() {

    @Test
    @TestId("VIS-54")
    fun theGateRefusesAPrematureCaptureBeforeAnyComparisonHappens() {
        openGoldenBookPage()

        val flat = flatBitmap(Color.WHITE)
        val flatFailure = runCatching {
            Baselines.assertMatches(
                testId = testId(),
                goldenName = GOLDEN_THAT_DOES_NOT_EXIST,
                actual = flat,
                expectation = readerExpectation(
                    "a page that failed to decode",
                    pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
                ),
            )
        }.exceptionOrNull()

        assertTrue(
            "A flat capture must be refused by the gate, not compared. The golden name " +
                "$GOLDEN_THAT_DOES_NOT_EXIST is not committed, so a comparison would have thrown " +
                "BaselineMissingGolden instead. The helper threw $flatFailure",
            flatFailure is BaselineGateFailure,
        )
        assertTrue(
            "The refusal must name which of the four gate checks failed, it read: ${flatFailure?.message}",
            flatFailure?.message?.contains("Gate 3 of 4") == true,
        )

        val wrongPageFailure = runCatching {
            Baselines.assertMatches(
                testId = testId(),
                goldenName = GOLDEN_THAT_DOES_NOT_EXIST,
                actual = captureScreen(),
                expectation = readerExpectation(
                    "a capture of the wrong page",
                    pageIndex = BaselineFixtures.BOOK_WRONG_PAGE_INDEX,
                ),
            )
        }.exceptionOrNull()

        assertTrue(
            "A capture of the wrong page must be refused by the gate. The helper threw $wrongPageFailure",
            wrongPageFailure is BaselineGateFailure,
        )
        assertTrue(
            "The refusal must name the gate check and carry the hook value read at capture time, it read: " +
                "${wrongPageFailure?.message}",
            wrongPageFailure?.message?.contains("Gate 2 of 4") == true &&
                wrongPageFailure.message?.contains("hook value at capture time") == true,
        )
    }

    @Test
    @TestId("VIS-55")
    fun theSameStateCapturedTwiceIsByteIdentical() {
        openGoldenBookPage()

        val first = captureScreen()

        jumpToPageIndex(BaselineFixtures.BOOK_RETURN_TRIP_INDEX)
        jumpToPageIndex(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        awaitSettledFullQuality()

        val second = captureScreen()

        Baselines.assertByteIdentical(
            first,
            second,
            "TLCL 25.12A body page in fullscreen, captured before and after a trip to page index " +
                "${BaselineFixtures.BOOK_RETURN_TRIP_INDEX}",
        )

        val firstResult = assertBitmapMatches(
            goldenName = VIS_01_GOLDEN,
            actual = first,
            expectation = readerExpectation(
                "TLCL 25.12A body page in fullscreen",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
                allowStaleFrame = true,
            ),
        )
        val secondResult = assertBitmapMatches(
            goldenName = VIS_01_GOLDEN,
            actual = second,
            expectation = readerExpectation(
                "TLCL 25.12A body page in fullscreen",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
                allowStaleFrame = true,
            ),
        )

        assertEquals(
            "Both captures are the same bytes, so they must report the same mismatch against the golden",
            firstResult.unequalPixels,
            secondResult.unequalPixels,
        )
    }

    private fun awaitSettledFullQuality() {
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender(com.gitlab.mudlej.MjPdfReader.suite.core.Await.LONG_TIMEOUT_MS)
    }

    private fun flatBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(FLAT_BITMAP_SIDE, FLAT_BITMAP_SIDE, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    private companion object {
        const val GOLDEN_THAT_DOES_NOT_EXIST = "VIS-54-never-recorded.png"
        const val VIS_01_GOLDEN = "VIS-01-tlcl-page41.png"
        const val FLAT_BITMAP_SIDE = 64
    }
}
