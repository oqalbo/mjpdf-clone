// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

import java.util.Locale

object StringUtil {

    fun String.toTitleCase(): String = split(" ").joinToString(" ") {
        it.lowercase(Locale.ROOT).replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
    }


    fun String.formatEnumToTitle() = this.replace("_", " ").toTitleCase()

    fun String.formatTitleToEnum() = this.replace(" ", "_").uppercase(Locale.ROOT)

}