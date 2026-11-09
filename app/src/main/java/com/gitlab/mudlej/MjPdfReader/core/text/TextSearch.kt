// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

fun ignoreCaseOpt(ignoreCase: Boolean) =
    if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()

fun String?.indexesOf(pat: String, ignoreCase: Boolean = true): List<Int> =
    Regex.escape(pat)       // to disable any special meaning of query's characters
        .toRegex(ignoreCaseOpt(ignoreCase))
        .findAll(this?: "")
        .map { it.range.first }
        .toList()

class FoldedText private constructor(
    private val original: String,
    private val folded: String,
    private val originalIndices: IntArray,
) {
    fun findMatchRanges(foldedPattern: String): List<IntRange> {
        if (folded.isEmpty() || foldedPattern.isEmpty()) return emptyList()

        val ranges = mutableListOf<IntRange>()
        var foldedIndex = folded.indexOf(foldedPattern)
        while (foldedIndex != -1) {
            val foldedEnd = foldedIndex + foldedPattern.length
            val start = originalIndices[foldedIndex]
            var end = if (foldedEnd < originalIndices.size) originalIndices[foldedEnd] else original.length
            if (end <= start) {
                end = start + Character.charCount(original.codePointAt(start))
            }
            ranges.add(start until end)
            foldedIndex = folded.indexOf(foldedPattern, foldedEnd)
        }
        return ranges
    }

    companion object {
        private const val ARABIC_TATWEEL = 'ـ'

        fun of(text: String): FoldedText {
            val folded = StringBuilder(text.length)
            val originalIndices = ArrayList<Int>(text.length)
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                val charCount = Character.charCount(codePoint)
                if (codePoint < 0x80) {
                    folded.append(codePoint.toChar().lowercaseChar())
                    originalIndices.add(i)
                } else {
                    val decomposed = java.text.Normalizer.normalize(
                        text.substring(i, i + charCount),
                        java.text.Normalizer.Form.NFKD,
                    )
                    for (ch in decomposed) {
                        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
                        if (ch == ARABIC_TATWEEL) continue
                        folded.append(ch.lowercaseChar())
                        originalIndices.add(i)
                    }
                }
                i += charCount
            }
            return FoldedText(text, folded.toString(), originalIndices.toIntArray())
        }

        fun foldPattern(pattern: String): String = of(pattern).folded
    }
}

fun String.accentInsensitiveRanges(pattern: String): List<IntRange> {
    if (isEmpty() || pattern.isEmpty()) return emptyList()
    return FoldedText.of(this).findMatchRanges(FoldedText.foldPattern(pattern))
}

fun String.containsAccentInsensitive(pattern: String): Boolean =
    accentInsensitiveRanges(pattern).isNotEmpty()
