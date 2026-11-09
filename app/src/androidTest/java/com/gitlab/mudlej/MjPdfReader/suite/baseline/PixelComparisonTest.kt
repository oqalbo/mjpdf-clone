// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PixelComparisonTest {

    @Test
    @TestId("VIS-TOL-01")
    fun identicalImagesReportNoMismatchAtAll() {
        val golden = solid(BASE_COLOUR)
        val capture = solid(BASE_COLOUR)

        val result = PixelComparison.compare(golden, capture)

        assertEquals("unequal pixels", 0, result.unequalPixels)
        assertTrue("the comparison must report an exact match", result.isExact)
        assertTrue("an exact match carries no verdict reasons", result.verdict().isEmpty())
        assertTrue("byte identical images must be reported as such", PixelComparison.areByteIdentical(golden, capture))
    }

    @Test
    @TestId("VIS-TOL-02")
    fun aChannelDeltaOfTwoIsAbsorbedEverywhere() {
        val golden = solid(BASE_COLOUR)
        val capture = solid(shifted(BASE_COLOUR, 2))

        val result = PixelComparison.compare(golden, capture)

        assertEquals("a delta inside the per channel tolerance counts no pixel as unequal", 0, result.unequalPixels)
        assertTrue("so there is no verdict against it", result.verdict().isEmpty())
        assertFalse("but the images are genuinely not byte identical", PixelComparison.areByteIdentical(golden, capture))
        assertEquals("and the largest delta is still reported honestly", 2, result.largestDelta?.delta)
    }

    @Test
    @TestId("VIS-TOL-03")
    fun theUnequalPixelBudgetPassesExactlyAtItsLimit() {
        val result = compareWithUnequalPixels(count = BUDGET_PIXELS, delta = 3)

        assertEquals("unequal pixels", BUDGET_PIXELS, result.unequalPixels)
        assertEquals("mismatch fraction", 0.001, result.mismatchFraction, 1e-9)
        assertTrue(
            "0.1 percent is the budget, not past it, so this must pass. Reasons were ${result.verdict()}",
            result.verdict().isEmpty(),
        )
    }

    @Test
    @TestId("VIS-TOL-04")
    fun onePixelPastTheBudgetFailsTheComparison() {
        val result = compareWithUnequalPixels(count = BUDGET_PIXELS + 1, delta = 3)

        val reasons = result.verdict()
        assertEquals("exactly one rule may fire here", 1, reasons.size)
        assertTrue(
            "the failure must name the budget it broke, it read: ${reasons.first()}",
            reasons.first().contains("percent of pixels are unequal"),
        )
    }

    @Test
    @TestId("VIS-TOL-05")
    fun aSingleLargeChannelChangeFailsRegardlessOfCount() {
        val result = compareWithUnequalPixels(count = 1, delta = 17)

        val reasons = result.verdict()
        assertTrue(
            "a delta of 17 is past the hard limit of 16 and must fail on its own, reasons were $reasons",
            reasons.any { it.contains("over the hard limit") },
        )
        assertEquals("one pixel is well inside the count budget", 1, result.unequalPixels)
    }

    @Test
    @TestId("VIS-TOL-06")
    fun aSingleChannelChangeAtTheHardLimitDoesNotFireTheHardRule() {
        val result = compareWithUnequalPixels(count = 1, delta = 16)

        assertTrue(
            "16 is the limit, not past it, so only the count rule could fire and one pixel is inside it. " +
                "Reasons were ${result.verdict()}",
            result.verdict().isEmpty(),
        )
    }

    @Test
    @TestId("VIS-TOL-07")
    fun aSizeMismatchIsReportedAsAPinFailureNotAPixelFailure() {
        val golden = solid(BASE_COLOUR)
        val capture = Bitmap.createBitmap(WIDTH, HEIGHT - 1, Bitmap.Config.ARGB_8888)

        val failure = runCatching { PixelComparison.compare(golden, capture) }.exceptionOrNull()

        assertTrue(
            "a dimension mismatch must be a pin failure, the comparison threw $failure",
            failure is BaselinePinMismatch,
        )
        assertTrue(
            "the message must say it is a pin failure rather than a rendering change, it read: " +
                "${failure?.message}",
            failure?.message?.contains("pin failure") == true,
        )
    }

    @Test
    @TestId("VIS-TOL-08")
    fun theDiffImageMarksEveryUnequalPixelAndOutlinesIt() {
        val golden = solid(BASE_COLOUR)
        val capture = solid(BASE_COLOUR).also { it.setPixel(10, 10, shifted(BASE_COLOUR, 40)) }

        val result = PixelComparison.compare(golden, capture)
        val diff = DiffImage.render(capture, result)

        assertEquals("the changed pixel is painted magenta", DiffImage.MAGENTA, diff.getPixel(10, 10))
        assertEquals(
            "and a one pixel outline surrounds it so a single pixel change stays findable by eye",
            DiffImage.MAGENTA,
            diff.getPixel(9, 9),
        )
        assertTrue(
            "the untouched background is faded towards white rather than left at full strength",
            Color.red(diff.getPixel(40, 40)) > Color.red(BASE_COLOUR),
        )
        assertEquals("the bounding box is the single changed pixel", 10, result.boundingBox?.left)
    }

    @Test
    @TestId("VIS-TOL-09")
    fun aFlatCaptureIsCountedAsOneDistinctColour() {
        assertEquals(
            "a page that failed to decode is one flat colour, which gate 3 refuses",
            1,
            PixelComparison.distinctColourCount(solid(BASE_COLOUR)),
        )
    }

    private fun compareWithUnequalPixels(count: Int, delta: Int): ComparisonResult {
        val golden = solid(BASE_COLOUR)
        val capture = solid(BASE_COLOUR)
        val changed = shifted(BASE_COLOUR, delta)
        repeat(count) { index ->
            capture.setPixel(index % WIDTH, index / WIDTH, changed)
        }
        return PixelComparison.compare(golden, capture)
    }

    private fun solid(colour: Int): Bitmap =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { it.eraseColor(colour) }

    private fun shifted(colour: Int, delta: Int): Int = Color.argb(
        Color.alpha(colour),
        (Color.red(colour) + delta).coerceIn(0, 255),
        Color.green(colour),
        Color.blue(colour),
    )

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 100
        const val BUDGET_PIXELS = 100
        val BASE_COLOUR = Color.rgb(100, 120, 140)
    }
}
