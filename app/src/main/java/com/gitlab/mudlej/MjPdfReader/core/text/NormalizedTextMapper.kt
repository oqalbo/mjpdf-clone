// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

import com.shockwave.pdfium.PdfiumCore
import java.text.Normalizer

object NormalizedTextMapper {

    fun toRawRange(rawText: String, normalizedStart: Int, normalizedLength: Int): IntRange? {
        if (rawText.isEmpty() || normalizedStart < 0 || normalizedLength <= 0) {
            return null
        }
        val normalizedEnd = normalizedStart + normalizedLength
        var rawIndex = 0
        var normalizedCount = 0
        var rawStart = -1
        while (rawIndex < rawText.length) {
            val chunkEnd = chunkEnd(rawText, rawIndex)
            val chunkLength = normalizedLength(rawText.substring(rawIndex, chunkEnd))
            if (rawStart < 0 && normalizedCount + chunkLength > normalizedStart) {
                rawStart = rawIndex
            }
            normalizedCount += chunkLength
            rawIndex = chunkEnd
            if (rawStart >= 0 && normalizedCount >= normalizedEnd) {
                return rawStart until rawIndex
            }
        }
        return if (rawStart >= 0) rawStart until rawText.length else null
    }

    private fun chunkEnd(text: String, start: Int): Int {
        if (text[start] == '￾') {
            return when {
                text.startsWith("￾\r\n", start) -> start + 3
                start + 1 < text.length && (text[start + 1] == '\r' || text[start + 1] == '\n') -> start + 2
                else -> start + 1
            }
        }
        if (text.startsWith("\r\n", start)) {
            return start + 2
        }
        var end = start + Character.charCount(text.codePointAt(start))
        while (end < text.length) {
            val codePoint = text.codePointAt(end)
            if (!isCombining(codePoint)) {
                break
            }
            end += Character.charCount(codePoint)
        }
        return end
    }

    private fun isCombining(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun normalizedLength(chunk: String): Int {
        return Normalizer.normalize(PdfiumCore.mapPresentationFormMarks(chunk), Normalizer.Form.NFKC)
            .replace("\uFFFE\r\n", "")
            .replace("\uFFFE\n", "")
            .replace("\uFFFE\r", "")
            .replace("\uFFFE", "")
            .replace("\u200B", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .length
    }
}
