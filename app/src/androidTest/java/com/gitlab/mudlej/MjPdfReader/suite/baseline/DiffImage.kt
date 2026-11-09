// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import android.graphics.Color

object DiffImage {

    const val MAGENTA = 0xFFFF00FF.toInt()

    private const val ACTUAL_OPACITY = 0.30f

    fun render(actual: Bitmap, result: ComparisonResult): Bitmap {
        val width = result.width
        val height = result.height
        val source = PixelComparison.pixelsOf(actual)
        val output = IntArray(width * height)

        for (index in output.indices) {
            output[index] = fadeOverWhite(source[index])
        }

        val outline = outlineOf(result.unequalMask, width, height)
        for (index in output.indices) {
            if (result.unequalMask[index] || outline[index]) {
                output[index] = MAGENTA
            }
        }

        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    fun outlineOf(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val outline = BooleanArray(mask.size)
        for (index in mask.indices) {
            if (mask[index]) continue
            val x = index % width
            val y = index / width
            if (touchesUnequalNeighbour(mask, width, height, x, y)) {
                outline[index] = true
            }
        }
        return outline
    }

    private fun touchesUnequalNeighbour(
        mask: BooleanArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
    ): Boolean {
        var offsetY = -1
        while (offsetY <= 1) {
            var offsetX = -1
            while (offsetX <= 1) {
                val neighbourX = x + offsetX
                val neighbourY = y + offsetY
                val isSelf = offsetX == 0 && offsetY == 0
                val insideBounds = neighbourX in 0 until width && neighbourY in 0 until height
                if (!isSelf && insideBounds && mask[neighbourY * width + neighbourX]) {
                    return true
                }
                offsetX++
            }
            offsetY++
        }
        return false
    }

    private fun fadeOverWhite(pixel: Int): Int {
        val red = blend(Color.red(pixel))
        val green = blend(Color.green(pixel))
        val blue = blend(Color.blue(pixel))
        return Color.argb(255, red, green, blue)
    }

    private fun blend(channel: Int): Int {
        val faded = channel * ACTUAL_OPACITY + 255f * (1f - ACTUAL_OPACITY)
        return faded.toInt().coerceIn(0, 255)
    }
}
