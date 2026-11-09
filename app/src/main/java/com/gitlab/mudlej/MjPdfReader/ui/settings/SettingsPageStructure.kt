// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

internal fun groupBySection(entries: List<SettingEntry>): List<Pair<Int?, List<SettingEntry>>> {
    val groups = mutableListOf<Pair<Int?, MutableList<SettingEntry>>>()
    entries.forEach { entry ->
        if (groups.isEmpty() || groups.last().first != entry.sectionRes) {
            groups.add(entry.sectionRes to mutableListOf())
        }
        groups.last().second.add(entry)
    }
    return groups
}

internal fun mergeOverrideSummary(summary: CharSequence?, reason: String): String =
    if (summary.isNullOrBlank()) reason else "$summary\n$reason"
