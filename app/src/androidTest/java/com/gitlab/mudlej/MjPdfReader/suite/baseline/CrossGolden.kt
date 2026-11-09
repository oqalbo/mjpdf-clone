// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap

object CrossGolden {

    fun assertMatches(
        actual: Bitmap,
        goldenName: String,
        why: String,
        policy: TolerancePolicy = TolerancePolicy.DEFAULT,
    ): ComparisonResult? {
        if (BaselineRecording.isRecording()) return null
        val result = compare(actual, goldenName, policy)
        val reasons = result.verdict(policy)
        if (reasons.isEmpty()) return result
        throw BaselineImageMismatch(
            buildString {
                appendLine("$why, but the capture differs from ${BaselineAssets.withPngSuffix(goldenName)}.")
                reasons.forEach { appendLine("  $it") }
                appendLine()
                append(result.summary())
            }
        )
    }

    fun assertDiffers(
        actual: Bitmap,
        goldenName: String,
        why: String,
        policy: TolerancePolicy = TolerancePolicy.DEFAULT,
    ): ComparisonResult? {
        if (BaselineRecording.isRecording()) return null
        val result = compare(actual, goldenName, policy)
        if (result.verdict(policy).isNotEmpty()) return result
        throw BaselineImageMismatch(
            buildString {
                appendLine(
                    "$why, but the capture is indistinguishable from " +
                        "${BaselineAssets.withPngSuffix(goldenName)} under the tolerance policy."
                )
                appendLine(
                    "Two renderings that the app is supposed to draw differently came out the same, which " +
                        "means the setting under test is not reaching the draw path at all."
                )
                appendLine()
                append(result.summary())
            }
        )
    }

    fun pixel(bitmap: Bitmap, x: Int, y: Int): Int {
        requireInside(bitmap, x, y, "the capture")
        return bitmap.getPixel(x, y)
    }

    fun assertRoughlyInverted(
        actual: Bitmap,
        goldenName: String,
        points: List<Point>,
        tolerance: Int = INVERSION_TOLERANCE,
    ) {
        if (BaselineRecording.isRecording()) return
        val golden = BaselineAssets.readGolden(BaselineProfile.current(), goldenName)
        val offenders = points.mapNotNull { point ->
            requireInside(golden, point.x, point.y, BaselineAssets.withPngSuffix(goldenName))
            requireInside(actual, point.x, point.y, "the capture")
            val light = golden.getPixel(point.x, point.y)
            val dark = actual.getPixel(point.x, point.y)
            val worst = worstInversionDelta(light, dark)
            if (worst <= tolerance) null else describe(point, light, dark, worst)
        }
        if (offenders.isEmpty()) return
        throw BaselineImageMismatch(
            buildString {
                appendLine(
                    "The night mode capture is not the inverse of " +
                        "${BaselineAssets.withPngSuffix(goldenName)} at ${offenders.size} of " +
                        "${points.size} sampled points, allowing a per channel tolerance of $tolerance."
                )
                offenders.forEach { appendLine("  $it") }
                appendLine()
                append(
                    "An inverting colour matrix maps each channel c to 255 minus c. A point that fails here " +
                        "is either not inverted at all, which means the filter never reached the page paint, " +
                        "or inverted twice."
                )
            }
        )
    }

    data class Point(val x: Int, val y: Int)

    private fun compare(actual: Bitmap, goldenName: String, policy: TolerancePolicy): ComparisonResult {
        val golden = BaselineAssets.readGolden(BaselineProfile.current(), goldenName)
        return PixelComparison.compare(golden, actual, policy)
    }

    private fun worstInversionDelta(light: Int, dark: Int): Int {
        val red = Math.abs((MAX_CHANNEL - ((light shr 16) and MAX_CHANNEL)) - ((dark shr 16) and MAX_CHANNEL))
        val green = Math.abs((MAX_CHANNEL - ((light shr 8) and MAX_CHANNEL)) - ((dark shr 8) and MAX_CHANNEL))
        val blue = Math.abs((MAX_CHANNEL - (light and MAX_CHANNEL)) - (dark and MAX_CHANNEL))
        return maxOf(red, green, blue)
    }

    private fun describe(point: Point, light: Int, dark: Int, worst: Int): String = String.format(
        "(%d,%d) golden #%08X inverts to #%08X but the capture reads #%08X, worst channel off by %d",
        point.x, point.y, light, invert(light), dark, worst,
    )

    private fun invert(pixel: Int): Int {
        val alpha = (pixel shr 24) and MAX_CHANNEL
        val red = MAX_CHANNEL - ((pixel shr 16) and MAX_CHANNEL)
        val green = MAX_CHANNEL - ((pixel shr 8) and MAX_CHANNEL)
        val blue = MAX_CHANNEL - (pixel and MAX_CHANNEL)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun requireInside(bitmap: Bitmap, x: Int, y: Int, described: String) {
        if (x in 0 until bitmap.width && y in 0 until bitmap.height) return
        throw BaselineGateFailure(
            "The sample point ($x,$y) is outside $described, which is ${bitmap.width}x${bitmap.height}"
        )
    }

    private const val MAX_CHANNEL = 0xFF
    private const val INVERSION_TOLERANCE = 24
}
