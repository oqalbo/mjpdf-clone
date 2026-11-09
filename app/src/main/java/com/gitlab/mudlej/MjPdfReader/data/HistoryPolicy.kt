// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

class HistoryPolicy(
    private val pref: Preferences,
    private val isIncognito: () -> Boolean = { false },
) {

    fun canRecord(): Boolean = pref.getHistoryEnabled() && !isIncognito()
}
