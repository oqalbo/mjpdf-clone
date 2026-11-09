// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

object SelectionShare {

    const val CARD_SIZE = 1080
    const val NAME_PATTERN = "MJ_quote_\\d{4}_\\d{2}_\\d{2}-\\d{2}_\\d{2}_\\d{2}\\.png"
    const val DIFFERENCE_THRESHOLD = 0.001f

    val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
        0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun directory(): File = File(TestEnvironment.targetContext.cacheDir, "share")

    fun files(): List<File> = directory().listFiles()?.sortedBy { it.name }.orEmpty()

    fun pngFiles(): List<File> = files().filter { it.name.endsWith(".png") }

    fun clearCache() {
        directory().listFiles()?.forEach { it.delete() }
    }

    fun awaitFileCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<File> =
        Await.untilValue(
            condition = "the share cache holds exactly $expected file(s)",
            timeoutMs = timeoutMs,
            read = { files() },
        ) { it.size == expected }

    fun hasPngSignature(file: File): Boolean {
        val header = ByteArray(PNG_SIGNATURE.size)
        file.inputStream().use { stream ->
            if (stream.read(header) != header.size) return false
        }
        return header.contentEquals(PNG_SIGNATURE)
    }

    fun boundsOf(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw AssertionError("${file.name} does not decode as an image")
        }
        return options.outWidth to options.outHeight
    }

    fun decode(file: File): Bitmap = BitmapFactory.decodeFile(file.path)
        ?: throw AssertionError("${file.name} does not decode as a bitmap")

    fun differingFraction(first: File, second: File): Float {
        val a = decode(first)
        val b = decode(second)
        try {
            return differingFraction(a, b, null)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    fun differingFractionOutside(first: File, second: File, excluded: Rect): Float {
        val a = decode(first)
        val b = decode(second)
        try {
            return differingFraction(a, b, excluded)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    fun differingFraction(a: Bitmap, b: Bitmap, excluded: Rect?): Float {
        if (a.width != b.width || a.height != b.height) {
            throw AssertionError(
                "The two cards are ${a.width} by ${a.height} and ${b.width} by ${b.height}, so they cannot be " +
                    "compared pixel for pixel"
            )
        }
        var differing = 0L
        var counted = 0L
        val row = IntArray(a.width)
        val other = IntArray(b.width)
        for (y in 0 until a.height) {
            a.getPixels(row, 0, a.width, 0, y, a.width, 1)
            b.getPixels(other, 0, b.width, 0, y, b.width, 1)
            for (x in 0 until a.width) {
                if (excluded != null && excluded.contains(x, y)) continue
                counted++
                if (row[x] != other[x]) differing++
            }
        }
        return if (counted == 0L) 0f else differing.toFloat() / counted.toFloat()
    }

    fun hasInk(file: File, region: Rect): Boolean = !isUniformBackground(file, region)

    fun isUniformBackground(file: File, region: Rect): Boolean {
        val bitmap = decode(file)
        try {
            val background = bitmap.getPixel(BACKGROUND_PROBE_X, BACKGROUND_PROBE_Y)
            val pixels = IntArray(region.width())
            for (y in region.top until region.bottom) {
                bitmap.getPixels(pixels, 0, region.width(), region.left, y, region.width(), 1)
                if (pixels.any { it != background }) return false
            }
            return true
        } finally {
            bitmap.recycle()
        }
    }

    const val BACKGROUND_PROBE_X = 4
    const val BACKGROUND_PROBE_Y = 540

    val TOP_LEFT_DECOR: Rect = Rect(40, 40, 320, 300)

    val TOP_RIGHT_DECOR: Rect = Rect(CARD_SIZE - 320, 40, CARD_SIZE - 40, 300)

    val MADE_BY_CORNER: Rect = Rect(CARD_SIZE - 520, CARD_SIZE - 100, CARD_SIZE, CARD_SIZE)
}
