// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.translation

import android.net.Uri

object TranslationUrlBuilder {

    const val textPlaceholder = "{text}"
    const val langPlaceholder = "{lang}"
    private const val maxTextLength = 1000

    fun build(settings: TranslationSettings, text: String, encode: (String) -> String = Uri::encode): String? {
        val template = if (settings.engine == TranslationEngine.CUSTOM) {
            settings.customTemplate
        } else {
            settings.engine.urlTemplate
        }
        if (template.isNullOrBlank() || !template.contains(textPlaceholder)) {
            return null
        }
        val code = TranslationLanguages.resolve(settings.targetLanguage)
        val language = settings.engine.langOverrides[code] ?: code
        val truncated = text.take(maxTextLength)
        val prepared = if (settings.engine == TranslationEngine.DEEPL) {
            truncated.replace("/", "\\/")
        } else {
            truncated
        }
        return template
            .replace(langPlaceholder, language)
            .replace(textPlaceholder, encode(prepared))
    }
}
