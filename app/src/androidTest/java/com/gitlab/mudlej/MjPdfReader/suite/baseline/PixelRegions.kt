// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import android.graphics.Rect

object PixelRegions {

    fun isNear(pixel: Int, other: Int, tolerance: Int = COLOUR_TOLERANCE): Boolean =
        Math.abs(red(pixel) - red(other)) <= tolerance &&
            Math.abs(green(pixel) - green(other)) <= tolerance &&
            Math.abs(blue(pixel) - blue(other)) <= tolerance

    fun countMatching(bitmap: Bitmap, region: Rect, predicate: (Int) -> Boolean): Int {
        var count = 0
        forEachIn(bitmap, region) { _, _, pixel -> if (predicate(pixel)) count++ }
        return count
    }

    fun longestRun(bitmap: Bitmap, y: Int, from: Int, to: Int, predicate: (Int) -> Boolean): Int {
        var longest = 0
        var current = 0
        for (x in from until to) {
            if (predicate(bitmap.getPixel(x, y))) {
                current++
                if (current > longest) longest = current
            } else {
                current = 0
            }
        }
        return longest
    }

    private fun forEachIn(bitmap: Bitmap, region: Rect, block: (Int, Int, Int) -> Unit) {
        val bounded = clamp(bitmap, region)
        for (y in bounded.top until bounded.bottom) {
            for (x in bounded.left until bounded.right) {
                block(x, y, bitmap.getPixel(x, y))
            }
        }
    }

    private fun clamp(bitmap: Bitmap, region: Rect): Rect = Rect(
        region.left.coerceIn(0, bitmap.width),
        region.top.coerceIn(0, bitmap.height),
        region.right.coerceIn(0, bitmap.width),
        region.bottom.coerceIn(0, bitmap.height),
    )

    private fun red(pixel: Int) = (pixel shr 16) and 0xFF

    private fun green(pixel: Int) = (pixel shr 8) and 0xFF

    private fun blue(pixel: Int) = pixel and 0xFF

    const val COLOUR_TOLERANCE = 24
}
