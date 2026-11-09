// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.Preference

internal class SettingEntry(
    val page: SettingsPage,
    @StringRes val titleRes: Int,
    @StringRes private val summaryRes: Int? = null,
    private val keywords: List<String> = emptyList(),
    @StringRes val sectionRes: Int? = null,
    private val preferenceBuilder: SettingsPreferenceFactory.(breadcrumb: String?) -> Preference,
) {
    fun createPreference(factory: SettingsPreferenceFactory, breadcrumb: String?): Preference {
        return preferenceBuilder.invoke(factory, breadcrumb)
    }

    fun matches(context: Context, query: String): Boolean = matches(context::getString, query)

    fun matches(resolve: (Int) -> String, query: String): Boolean {
        val terms = query.lowercase().split(" ").filter { it.isNotBlank() }
        val searchableText = buildList {
            add(resolve(titleRes))
            add(resolve(page.titleRes))
            summaryRes?.let { add(resolve(it)) }
            addAll(keywords)
        }.joinToString(" ").lowercase()

        return terms.all { term -> searchableText.contains(term) }
    }
}
