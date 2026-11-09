// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.translation

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

enum class TranslationEngine(
    val id: String,
    @StringRes val titleRes: Int,
    val urlTemplate: String?,
    val langOverrides: Map<String, String> = emptyMap(),
    val unstable: Boolean = false,
    val forceBrowser: Boolean = false,
) {
    GOOGLE(
        "google",
        R.string.translation_engine_google,
        "https://translate.google.com/?sl=auto&tl={lang}&text={text}&op=translate",
        mapOf("zh" to "zh-CN"),
    ),
    DEEPL(
        "deepl",
        R.string.translation_engine_deepl,
        "https://www.deepl.com/en/translator?share=generic#auto/{lang}/{text}",
        mapOf("no" to "nb", "pt" to "pt-BR"),
        unstable = true,
        forceBrowser = true,
    ),
    BING(
        "bing",
        R.string.translation_engine_bing,
        "https://www.bing.com/translator/?from=auto&to={lang}&text={text}",
        mapOf("zh" to "zh-Hans"),
    ),
    LINGVA(
        "lingva",
        R.string.translation_engine_lingva,
        "https://lingva.ml/auto/{lang}/{text}",
        unstable = true,
    ),
    LIBRE_TRANSLATE(
        "libretranslate",
        R.string.translation_engine_libretranslate,
        "https://libretranslate.com/?q={text}&source=auto&target={lang}",
        unstable = true,
    ),
    CUSTOM(
        "custom",
        R.string.translation_engine_custom,
        null,
    );

    companion object {
        fun fromId(id: String): TranslationEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

data class TranslationSettings(
    val mode: String,
    val engine: TranslationEngine,
    val customTemplate: String,
    val targetLanguage: String,
)
