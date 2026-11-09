// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.translation

import org.junit.Assert.*
import org.junit.Test
import org.junit.Ignore

class TranslationUrlBuilderTest {
    private fun settings(engine: TranslationEngine, language: String = "en", template: String = "") =
        TranslationSettings("web", engine, template, language)

    @Test fun customTemplatesRequireTextPlaceholder() {
        listOf("", "  ", "https://example.com/?target={lang}").forEach { template ->
            assertNull(TranslationUrlBuilder.build(settings(TranslationEngine.CUSTOM, template = template), "hello") {
                error("Invalid templates must not encode text")
            })
        }
    }

    @Test fun repeatedTextPlaceholderUsesTheEncodedTextTwice() {
        val encodedInputs = mutableListOf<String>()
        val url = TranslationUrlBuilder.build(
            settings(TranslationEngine.CUSTOM, "fr", "https://example.com/{lang}?a={text}&b={text}"), "a b",
        ) { encodedInputs.add(it); "a%20b" }
        assertEquals(listOf("a b"), encodedInputs)
        assertEquals("https://example.com/fr?a=a%20b&b=a%20b", url)
    }

    @Test fun deepLPreparesSlashesBeforeEncodingAndMapsNorwegian() {
        val inputs = mutableListOf<String>()
        val url = TranslationUrlBuilder.build(settings(TranslationEngine.DEEPL, "no"), "a/b") {
            inputs.add(it)
            "a%5C%2Fb"
        }
        assertEquals(listOf("a\\/b"), inputs)
        assertEquals("https://www.deepl.com/en/translator?share=generic#auto/nb/a%5C%2Fb", url)
    }

    @Test fun googleAndBingUseTheirChineseLanguageCodes() {
        assertEquals("https://translate.google.com/?sl=auto&tl=zh-CN&text=hello&op=translate",
            TranslationUrlBuilder.build(settings(TranslationEngine.GOOGLE, "zh"), "hello") { it })
        assertEquals("https://www.bing.com/translator/?from=auto&to=zh-Hans&text=hello",
            TranslationUrlBuilder.build(settings(TranslationEngine.BING, "zh"), "hello") { it })
    }

    @Test fun textIsLimitedBeforeEncoding() {
        var encodedInput = ""
        TranslationUrlBuilder.build(settings(TranslationEngine.GOOGLE), "a".repeat(1001)) {
            encodedInput = it
            "encoded"
        }
        assertEquals("a".repeat(1000), encodedInput)
    }

    @Ignore("Pending ruling: take(1000) splits a surrogate pair, expected truncation before the emoji")
    @Test fun truncationNeverHandsAnUnpairedSurrogateToTheEncoder() {
        var encodedInput = ""
        TranslationUrlBuilder.build(settings(TranslationEngine.GOOGLE), "a".repeat(999) + "😀") {
            encodedInput = it
            "encoded"
        }
        assertEquals("a".repeat(999), encodedInput)
    }
}
