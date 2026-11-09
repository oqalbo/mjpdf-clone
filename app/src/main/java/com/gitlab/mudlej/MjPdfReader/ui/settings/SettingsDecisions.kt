// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import com.gitlab.mudlej.MjPdfReader.data.translation.TranslationUrlBuilder
import kotlin.math.roundToInt

internal fun targetLanguageCheckedIndex(stored: String, codes: List<String>): Int =
    if (stored.isBlank()) 0 else codes.indexOf(stored) + 1

internal fun isValidCustomTranslationUrl(raw: String): Boolean {
    val value = raw.trim()
    val hasScheme = value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
    return hasScheme && value.contains(TranslationUrlBuilder.textPlaceholder)
}

internal fun sliderRange(min: Float, max: Float, current: Float): Pair<Int, Int> {
    val lower = min.roundToInt()
    val upper = max.roundToInt()
    return (upper - lower) to (current.roundToInt().coerceIn(lower, upper) - lower)
}

internal fun resolveActionIds(default: String, current: String, actionIds: List<String>): Pair<String, String> {
    val reset = default.takeIf { it in actionIds } ?: actionIds.first()
    return reset to (current.takeIf { it in actionIds } ?: reset)
}

internal fun formatSummary(breadcrumb: String?, detail: String?): String? = when {
    breadcrumb.isNullOrBlank() -> detail
    detail.isNullOrBlank() -> breadcrumb
    else -> "$breadcrumb: $detail"
}

internal fun optionLabel(options: List<Int>, labels: List<String>, current: Int): Pair<Int, String?> {
    val index = options.indexOf(current)
    return index to labels.getOrNull(index)
}
