// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.util.Locale

object LocalizedStrings {

    const val ENGLISH_TAG = "en-US"

    fun contextFor(tag: String): Context {
        val base = TestEnvironment.targetContext
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }

    fun string(tag: String, @StringRes resId: Int): String = contextFor(tag).getString(resId)

    fun string(tag: String, @StringRes resId: Int, vararg formatArgs: Any): String =
        contextFor(tag).getString(resId, *formatArgs)

    fun plural(tag: String, @PluralsRes resId: Int, quantity: Int): String =
        contextFor(tag).resources.getQuantityString(resId, quantity)

    fun plural(tag: String, @PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String =
        contextFor(tag).resources.getQuantityString(resId, quantity, *formatArgs)

    fun english(@StringRes resId: Int): String = string(ENGLISH_TAG, resId)

    fun englishPlural(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String =
        plural(ENGLISH_TAG, resId, quantity, *formatArgs)

    fun requireTranslated(tag: String, @StringRes resId: Int): String {
        val translated = string(tag, resId)
        if (translated == english(resId)) {
            throw AssertionError(
                "Resource ${nameOf(resId)} reads \"$translated\" in both $tag and $ENGLISH_TAG, so this " +
                    "assertion could pass on an untranslated string and prove nothing. Pick a resource that " +
                    "genuinely differs, or assert the fallback deliberately as LOC-06 does."
            )
        }
        return translated
    }

    fun nameOf(resId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(resId) }
            .getOrDefault(resId.toString())
}
