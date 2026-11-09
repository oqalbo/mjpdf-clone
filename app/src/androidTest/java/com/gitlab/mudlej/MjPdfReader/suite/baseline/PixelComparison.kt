// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import android.graphics.Rect
import java.util.Locale

data class ChannelDelta(val x: Int, val y: Int, val delta: Int, val expected: Int, val actual: Int) {

    fun describe(): String = String.format(
        Locale.ROOT,
        "delta %d at (%d, %d), golden #%08X against capture #%08X",
        delta,
        x,
        y,
        expected,
        actual,
    )
}

data class ComparisonResult(
    val width: Int,
    val height: Int,
    val unequalPixels: Int,
    val largestDelta: ChannelDelta?,
    val boundingBox: Rect?,
    val unequalMask: BooleanArray,
) {

    val totalPixels: Int get() = width * height

    val mismatchFraction: Double
        get() = if (totalPixels == 0) 0.0 else unequalPixels.toDouble() / totalPixels

    val mismatchPercent: Double get() = mismatchFraction * 100.0

    val isExact: Boolean get() = unequalPixels == 0

    fun formattedPercent(): String = String.format(Locale.ROOT, "%.6f", mismatchPercent)

    fun verdict(policy: TolerancePolicy = TolerancePolicy.DEFAULT): List<String> {
        val reasons = mutableListOf<String>()
        val worst = largestDelta
        if (worst != null && worst.delta > policy.hardChannelDelta) {
            reasons.add(
                "a single pixel differs by ${worst.delta}, over the hard limit of ${policy.hardChannelDelta}: " +
                    worst.describe()
            )
        }
        if (mismatchFraction > policy.maxUnequalFraction) {
            reasons.add(
                "${formattedPercent()} percent of pixels are unequal, over the budget of " +
                    "${policy.maxUnequalPercentText()} percent, which is $unequalPixels of $totalPixels pixels"
            )
        }
        return reasons
    }

    fun summary(): String = buildString {
        appendLine("mismatchPercent: ${formattedPercent()}")
        appendLine("unequalPixels: $unequalPixels")
        appendLine("totalPixels: $totalPixels")
        appendLine("dimensions: ${width}x$height")
        appendLine("largestChannelDelta: ${largestDelta?.describe() ?: "none, the images are identical"}")
        appendLine(
            "unequalBoundingBox: " + (boundingBox?.let { "${it.left},${it.top} to ${it.right},${it.bottom}" }
                ?: "none")
        )
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

data class TolerancePolicy(
    val perChannelTolerance: Int,
    val maxUnequalFraction: Double,
    val hardChannelDelta: Int,
) {

    fun maxUnequalPercentText(): String =
        String.format(Locale.ROOT, "%.3f", maxUnequalFraction * 100.0)

    companion object {
        val DEFAULT = TolerancePolicy(
            perChannelTolerance = 2,
            maxUnequalFraction = 0.001,
            hardChannelDelta = 16,
        )

        val EXACT = TolerancePolicy(
            perChannelTolerance = 0,
            maxUnequalFraction = 0.0,
            hardChannelDelta = 0,
        )
    }
}

object PixelComparison {

    fun compare(
        expected: Bitmap,
        actual: Bitmap,
        policy: TolerancePolicy = TolerancePolicy.DEFAULT,
    ): ComparisonResult {
        requireSameDimensions(expected, actual)

        val width = expected.width
        val height = expected.height
        val expectedPixels = pixelsOf(expected)
        val actualPixels = pixelsOf(actual)

        val mask = BooleanArray(width * height)
        var unequal = 0
        var worst: ChannelDelta? = null
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        for (index in expectedPixels.indices) {
            val goldenPixel = expectedPixels[index]
            val capturedPixel = actualPixels[index]
            if (goldenPixel == capturedPixel) continue

            val delta = largestChannelDelta(goldenPixel, capturedPixel)
            val x = index % width
            val y = index / width
            if (worst == null || delta > worst.delta) {
                worst = ChannelDelta(x, y, delta, goldenPixel, capturedPixel)
            }
            if (delta <= policy.perChannelTolerance) continue

            mask[index] = true
            unequal++
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        val box = if (unequal == 0) null else Rect(minX, minY, maxX, maxY)
        return ComparisonResult(width, height, unequal, worst, box, mask)
    }

    fun areByteIdentical(first: Bitmap, second: Bitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        return pixelsOf(first).contentEquals(pixelsOf(second))
    }

    fun distinctColourCount(bitmap: Bitmap, cap: Int = DISTINCT_COLOUR_CAP): Int {
        val seen = HashSet<Int>()
        for (pixel in pixelsOf(bitmap)) {
            seen.add(pixel)
            if (seen.size >= cap) return seen.size
        }
        return seen.size
    }

    fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    fun largestChannelDelta(first: Int, second: Int): Int {
        var largest = 0
        var shift = 0
        while (shift <= 24) {
            val delta = Math.abs(((first shr shift) and 0xFF) - ((second shr shift) and 0xFF))
            if (delta > largest) largest = delta
            shift += 8
        }
        return largest
    }

    private fun requireSameDimensions(expected: Bitmap, actual: Bitmap) {
        if (expected.width == actual.width && expected.height == actual.height) return
        throw BaselinePinMismatch(
            "The golden is ${expected.width}x${expected.height} and the capture is " +
                "${actual.width}x${actual.height}. A size mismatch is a pin failure, not a rendering change: " +
                "the display size, density, navigation mode or orientation of this device does not match the one " +
                "the golden was recorded on. The device resolves to profile ${BaselineProfile.current()}."
        )
    }

    const val DISTINCT_COLOUR_CAP = 4096
}
