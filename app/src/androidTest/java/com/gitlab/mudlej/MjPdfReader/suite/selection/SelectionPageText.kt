// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.os.ParcelFileDescriptor
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File

data class PdfBox(val left: Float, val bottom: Float, val right: Float, val top: Float) {
    val width: Float get() = right - left
    val height: Float get() = top - bottom
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (bottom + top) / 2f
}

class SelectionPageText internal constructor(
    val pageIndex: Int,
    val rawText: String,
    val charCount: Int,
    val widthPoints: Float,
    val heightPoints: Float,
    private val left: FloatArray,
    private val bottom: FloatArray,
    private val right: FloatArray,
    private val top: FloatArray,
) {

    val text: String get() = normalize(rawText)

    fun normalized(range: IntRange): String = normalize(rawText.substring(range.first, range.last + 1))

    fun boundsOf(range: IntRange): PdfBox {
        var minLeft = Float.MAX_VALUE
        var minBottom = Float.MAX_VALUE
        var maxRight = -Float.MAX_VALUE
        var maxTop = -Float.MAX_VALUE
        var found = false
        for (index in range) {
            if (right[index] <= left[index] || top[index] <= bottom[index]) continue
            found = true
            minLeft = minOf(minLeft, left[index])
            minBottom = minOf(minBottom, bottom[index])
            maxRight = maxOf(maxRight, right[index])
            maxTop = maxOf(maxTop, top[index])
        }
        if (!found) {
            throw AssertionError(
                "Characters ${range.first} to ${range.last} of page $pageIndex carry no usable box, so there is " +
                    "nothing on the page to aim at. The range reads \"${normalized(range)}\"."
            )
        }
        return PdfBox(minLeft, minBottom, maxRight, maxTop)
    }

    fun charBoxOf(index: Int): PdfBox = PdfBox(left[index], bottom[index], right[index], top[index])

    fun lineIndexContaining(fragment: String): Int {
        val index = lines().indexOfFirst { normalize(it).contains(fragment) }
        if (index < 0) {
            throw AssertionError(
                "No extracted line of page $pageIndex contains \"$fragment\". The page reads \"${readable()}\"."
            )
        }
        return index
    }

    fun lastTextLineIndex(): Int {
        val lines = lines()
        val index = lines.indexOfLast { it.isNotBlank() }
        if (index < 0) throw AssertionError("Page $pageIndex holds no non blank extracted line")
        return index
    }

    fun rangeOf(word: String, occurrence: Int = 1): IntRange {
        var found = 0
        var from = 0
        while (true) {
            val at = rawText.indexOf(word, from)
            if (at < 0) break
            if (isWholeWordAt(at, word.length)) {
                found++
                if (found == occurrence) return at until (at + word.length)
            }
            from = at + 1
        }
        throw AssertionError(
            "Page $pageIndex of the fixture holds no occurrence $occurrence of the whole word \"$word\". " +
                "Its extracted text reads \"${readable()}\"."
        )
    }

    fun rangeOfFragment(fragment: String, occurrence: Int = 1): IntRange {
        var found = 0
        var from = 0
        while (true) {
            val at = rawText.indexOf(fragment, from)
            if (at < 0) break
            found++
            if (found == occurrence) return at until (at + fragment.length)
            from = at + 1
        }
        throw AssertionError(
            "Page $pageIndex holds no occurrence $occurrence of \"$fragment\" in \"${readable()}\""
        )
    }

    val lineRanges: List<IntRange> by lazy { scanLines() }

    fun lines(): List<String> = lineRanges.map { rawText.substring(it.first, it.last + 1) }

    fun lineRange(lineIndex: Int): IntRange {
        if (lineIndex < 0 || lineIndex >= lineRanges.size) {
            throw AssertionError(
                "Page $pageIndex holds ${lineRanges.size} extracted lines, so line $lineIndex does not exist in " +
                    "\"${readable()}\""
            )
        }
        return lineRanges[lineIndex]
    }

    fun wordRanges(lineIndex: Int): List<IntRange> {
        val line = lineRange(lineIndex)
        val ranges = mutableListOf<IntRange>()
        var index = line.first
        while (index <= line.last) {
            if (rawText[index].isWhitespace()) {
                index++
                continue
            }
            var end = index
            while (end <= line.last && !rawText[end].isWhitespace()) end++
            ranges.add(index until end)
            index = end
        }
        return ranges
    }

    fun wordsOf(lineIndex: Int): List<String> = wordRanges(lineIndex).map { normalized(it) }

    fun rangeOfWordOnLine(lineIndex: Int, wordIndex: Int): IntRange {
        val ranges = wordRanges(lineIndex)
        if (wordIndex < 0 || wordIndex >= ranges.size) {
            throw AssertionError(
                "Line $lineIndex of page $pageIndex holds ${ranges.size} words, so word $wordIndex does not exist. " +
                    "The line reads \"${normalized(lineRange(lineIndex))}\"."
            )
        }
        return ranges[wordIndex]
    }

    fun rangeOfWordsOnLine(lineIndex: Int, fromWord: Int, toWord: Int): IntRange =
        rangeOfWordOnLine(lineIndex, fromWord).first..rangeOfWordOnLine(lineIndex, toWord).last

    private fun scanLines(): List<IntRange> {
        if (rawText.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = 0
        var index = 0
        while (index < rawText.length) {
            val character = rawText[index]
            if (character == '\r' || character == '\n') {
                if (index > start) ranges.add(start until index)
                index += if (character == '\r' && index + 1 < rawText.length && rawText[index + 1] == '\n') 2 else 1
                start = index
                continue
            }
            index++
        }
        if (start < rawText.length) ranges.add(start until rawText.length)
        return ranges
    }

    fun readable(): String = rawText.replace("\r", "\\r").replace("\n", "\\n")

    private fun isWholeWordAt(at: Int, length: Int): Boolean {
        val before = if (at == 0) null else rawText[at - 1]
        val after = if (at + length >= rawText.length) null else rawText[at + length]
        return (before == null || !isWordChar(before.code)) && (after == null || !isWordChar(after.code))
    }

    companion object {

        private val core: PdfiumCore by lazy { PdfiumCore(TestEnvironment.targetContext) }

        fun normalize(raw: String): String = core.normalizeExtractedText(raw)

        fun isWordChar(codePoint: Int): Boolean =
            Character.isLetterOrDigit(codePoint) ||
                codePoint == '_'.code ||
                Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()

        fun of(file: File, pageIndex: Int = 0): SelectionPageText {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val document: PdfDocument = core.newDocument(descriptor)
            try {
                core.openPage(document, pageIndex)
                val size = core.getPageSizePoint(document, pageIndex)
                val textPage = core.openTextPage(document, pageIndex)
                val count = if (textPage == 0L) 0 else core.textCountChars(document, pageIndex)
                if (count <= 0) {
                    return SelectionPageText(
                        pageIndex, "", 0, size.width, size.height,
                        FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0),
                    )
                }
                val raw = core.textRange(document, pageIndex, 0, count)
                if (raw.length != count) {
                    throw AssertionError(
                        "pdfium reports $count characters on page $pageIndex of ${file.name} but its text range " +
                            "is ${raw.length} UTF-16 units long. Every character index the selection hook reports " +
                            "is a pdfium index, so the two must agree for a test to convert between them."
                    )
                }
                val metrics = core.textCharMetrics(document, pageIndex, 0, count)
                if (metrics.size != count * VALUES_PER_CHAR) {
                    throw AssertionError(
                        "pdfium returned ${metrics.size} metric values for $count characters on page $pageIndex " +
                            "of ${file.name}, which is not $VALUES_PER_CHAR per character"
                    )
                }
                val left = FloatArray(count)
                val bottom = FloatArray(count)
                val right = FloatArray(count)
                val top = FloatArray(count)
                for (index in 0 until count) {
                    val base = index * VALUES_PER_CHAR
                    left[index] = metrics[base].toFloat()
                    bottom[index] = metrics[base + 1].toFloat()
                    right[index] = metrics[base + 2].toFloat()
                    top[index] = metrics[base + 3].toFloat()
                }
                return SelectionPageText(pageIndex, raw, count, size.width, size.height, left, bottom, right, top)
            } finally {
                core.closeTextPage(document, pageIndex)
                core.closePage(document, pageIndex)
                core.closeDocument(document)
                runCatching { descriptor.close() }
            }
        }

        private const val VALUES_PER_CHAR = 9
    }
}
