// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.pdf.createPdfExtractor
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

object SearchPageText {

    const val TATWEEL = '\u0640'

    fun of(file: File, oneBasedPage: Int, password: String? = null): String {
        val extractor = createPdfExtractor(TestEnvironment.targetContext, Uri.fromFile(file), password)
        return try {
            extractor.getPageText(oneBasedPage)
        } finally {
            runCatching { extractor.close() }
        }
    }

    fun rangeOfWordContainingIndex(text: String, index: Int): IntRange {
        var start = index
        while (start > 0 && !text[start - 1].isWhitespace()) start -= 1
        var end = index
        while (end < text.length - 1 && !text[end + 1].isWhitespace()) end += 1
        return start..end
    }

    fun rangesOfWordsCarrying(text: String, predicate: (Char) -> Boolean): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var index = 0
        while (index < text.length) {
            if (predicate(text[index])) {
                val range = rangeOfWordContainingIndex(text, index)
                if (ranges.none { it.first == range.first }) ranges.add(range)
                index = range.last + 1
            } else {
                index += 1
            }
        }
        return ranges
    }

    fun isHarakat(character: Char): Boolean =
        character.code in ARABIC_BLOCK &&
            Character.getType(character) == Character.NON_SPACING_MARK.toInt()

    fun matchedRange(originalIndex: Int, matchLength: Int): IntRange =
        originalIndex until originalIndex + matchLength

    fun rangeCarries(text: String, range: IntRange, predicate: (Char) -> Boolean): Boolean =
        range.any { it in text.indices && predicate(text[it]) }

    private val ARABIC_BLOCK = 0x0600..0x06FF
}
