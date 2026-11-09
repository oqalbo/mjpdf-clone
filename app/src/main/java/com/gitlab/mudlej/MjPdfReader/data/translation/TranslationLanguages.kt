// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.translation

import java.util.Locale

object TranslationLanguages {

    val codes = listOf(
        "ar", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fi", "fr", "he", "hi", "hu",
        "id", "it", "ja", "ko", "ms", "nl", "no", "pl", "pt", "ro", "ru", "sv", "th", "tr",
        "uk", "vi", "zh",
    )

    private val legacyCodes = mapOf("iw" to "he", "in" to "id")

    fun displayName(code: String): String {
        val name = Locale(code).getDisplayLanguage(Locale.getDefault())
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun deviceLanguage(): String = normalizeDeviceLanguage(Locale.getDefault().language)

    private fun normalizeDeviceLanguage(language: String): String {
        val normalized = legacyCodes[language] ?: language
        return if (normalized in codes) normalized else "en"
    }

    fun resolve(storedValue: String, deviceLanguage: String = Locale.getDefault().language): String =
        storedValue.ifBlank { normalizeDeviceLanguage(deviceLanguage) }
}
