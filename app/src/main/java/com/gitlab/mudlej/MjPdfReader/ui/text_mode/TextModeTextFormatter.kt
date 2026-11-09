// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

object TextModeTextFormatter {

    private const val MIN_JOINABLE_LINES = 4
    private const val MIN_TYPICAL_LINE_LENGTH = 35
    private const val FULL_LINE_FACTOR = 0.70f
    private const val SENTENCE_JOIN_FACTOR = 0.85f
    private const val HEADING_MAX_LENGTH = 60
    private const val TERMINALS = ".!?؟۔。…:;؛"
    private const val CLOSERS = "\"'»”’)]"

    private val TRAILING_WHITESPACE = Regex("[ \t]+\n")
    private val HYPHEN_LINE_BREAK = Regex("(?<=\\p{L})-\n(?=\\p{L})")
    private val EXCESS_NEWLINES = Regex("\n{3,}")
    private val CODE_SPACING = Regex("\\S {3,}\\S")
    private val LIST_STARTS = listOf(
        Regex("^[•◦▪●·⁃–—*-]\\s+"),
        Regex("^\\(?[0-9٠-٩۰-۹]{1,3}[.)]\\s+"),
        Regex("^\\(?\\p{Nd}{1,3}\\)\\s*"),
        Regex("^\\(?[a-zA-Z][.)]\\s+"),
        Regex("^\\(?[ivxlcIVXLC]{1,5}[.)]\\s+"),
    )

    fun format(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("(?<=\\p{L})-\n(?=\\p{L})"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun formatJoined(text: String): String {
        val normalized = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(TRAILING_WHITESPACE, "\n")
        val lines = normalized.split('\n')
        val lengths = lines.mapNotNull { line -> line.trim().length.takeIf { it > 0 } }
        if (lengths.size < MIN_JOINABLE_LINES) return finalize(normalized)
        val typicalLen = percentile80(lengths)
        if (typicalLen < MIN_TYPICAL_LINE_LENGTH) return finalize(normalized)

        val result = StringBuilder()
        var lastFragment: String? = null
        for (line in lines) {
            if (line.isBlank()) {
                if (result.isNotEmpty()) result.append('\n')
                lastFragment = null
                continue
            }
            val fragment = line.trim()
            if (lastFragment != null && shouldJoin(lastFragment, line, fragment, typicalLen)) {
                appendJoined(result, fragment)
            } else {
                if (result.isNotEmpty()) result.append('\n')
                result.append(line.trimEnd())
            }
            lastFragment = fragment
        }
        return finalize(result.toString())
    }

    internal fun joinFragments(first: String, second: String): String {
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first
        val builder = StringBuilder(first)
        appendJoined(builder, second)
        return builder.toString()
    }

    internal fun isListStart(fragment: String): Boolean {
        return LIST_STARTS.any { it.containsMatchIn(fragment) }
    }

    internal fun endsWithTerminal(fragment: String): Boolean {
        var text = fragment
        if (text.isNotEmpty() && text.last() in CLOSERS) text = text.dropLast(1)
        return text.isNotEmpty() && text.last() in TERMINALS
    }

    internal fun endsHyphenAfterLetter(fragment: String): Boolean {
        if (fragment.length < 2) return false
        val last = fragment.last()
        return (last == '-' || last == '‐' || last == '\u00AD') && fragment[fragment.length - 2].isLetter()
    }

    internal fun isCjk(codePoint: Int): Boolean {
        return codePoint in 0x2E80..0x9FFF || codePoint in 0xF900..0xFAFF || codePoint in 0xFF00..0xFFEF
    }

    private fun finalize(text: String): String {
        return text
            .replace(HYPHEN_LINE_BREAK, "")
            .replace(EXCESS_NEWLINES, "\n\n")
            .trim()
    }

    private fun shouldJoin(previousFragment: String, rawLine: String, fragment: String, typicalLen: Int): Boolean {
        if (isCodeLike(previousFragment) || isCodeLike(fragment)) return false
        if (rawLine.startsWith("  ")) return false
        if (isListStart(fragment)) return false
        if (isProbableHeading(previousFragment)) return false
        val fullEnough = previousFragment.length >= FULL_LINE_FACTOR * typicalLen ||
            endsHyphenAfterLetter(previousFragment)
        if (!fullEnough) return false
        if (endsWithTerminal(previousFragment)) {
            val allowSentenceJoin = previousFragment.last() == '.' &&
                previousFragment.length >= SENTENCE_JOIN_FACTOR * typicalLen &&
                Character.isLowerCase(fragment.codePointAt(0))
            if (!allowSentenceJoin) return false
        }
        return true
    }

    private fun appendJoined(result: StringBuilder, fragment: String) {
        val last = result.last()
        val beforeLast = if (result.length >= 2) result[result.length - 2] else null
        val firstCodePoint = fragment.codePointAt(0)
        when {
            last == '\u00AD' -> {
                result.deleteCharAt(result.length - 1)
                result.append(fragment)
            }
            (last == '-' || last == '‐') && beforeLast?.isLetter() == true && Character.isLetter(firstCodePoint) -> {
                if (!Character.isUpperCase(firstCodePoint)) {
                    result.deleteCharAt(result.length - 1)
                }
                result.append(fragment)
            }
            isCjk(last.code) && isCjk(firstCodePoint) -> result.append(fragment)
            else -> {
                result.append(' ')
                result.append(fragment)
            }
        }
    }

    private fun isCodeLike(fragment: String): Boolean {
        return fragment.contains('\t') || CODE_SPACING.containsMatchIn(fragment)
    }

    private fun isProbableHeading(fragment: String): Boolean {
        if (fragment.length > HEADING_MAX_LENGTH) return false
        var cased = 0
        var lower = 0
        for (character in fragment) {
            if (character.isLowerCase()) {
                lower++
                cased++
            } else if (character.isUpperCase()) {
                cased++
            }
        }
        return cased > 0 && lower == 0
    }

    private fun percentile80(lengths: List<Int>): Int {
        val sorted = lengths.sorted()
        return sorted[((sorted.size - 1) * 8) / 10]
    }
}
