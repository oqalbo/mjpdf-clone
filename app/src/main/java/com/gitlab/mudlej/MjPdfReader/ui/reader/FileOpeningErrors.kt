// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader

import com.gitlab.mudlej.MjPdfReader.ui.reader.load.DocumentUnreachableException
import java.io.FileNotFoundException

internal fun sharedTextLink(text: String, isWebUrl: (String) -> Boolean): String? {
    val trimChars = charArrayOf(
        '<', '>', '(', ')', '[', ']', '{', '}', '"', '\'', '.', ',', ';', ':', '!', '?',
    )
    return text
        .split(Regex("\\s+"))
        .map { it.trim(*trimChars) }
        .firstOrNull { token ->
            (token.startsWith("http://", ignoreCase = true)
                    || token.startsWith("https://", ignoreCase = true))
                    && isWebUrl(token)
        }
}

internal fun isStaleDocumentFailure(exception: Throwable): Boolean {
    var cause: Throwable? = exception
    while (cause != null) {
        if (cause is SecurityException || cause is FileNotFoundException || cause is DocumentUnreachableException) {
            return true
        }
        cause = cause.cause
    }
    return false
}

internal fun couldNotOpenFileDueToMissingPermission(
    t: Throwable,
    granted: Boolean,
    permissionDeniedText: String,
): Boolean {
    if (granted) return false
    val exceptionMessage = t.message
    return t is FileNotFoundException && exceptionMessage != null && exceptionMessage.contains(permissionDeniedText)
}
