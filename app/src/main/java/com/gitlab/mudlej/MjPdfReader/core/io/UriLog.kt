// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.net.Uri

private const val ELLIPSIS = "…"
private const val UNKNOWN = "?"

fun Uri?.forLog(): String {
    val scheme = this?.scheme ?: return UNKNOWN
    val authority = this.authority
    return if (authority.isNullOrEmpty()) {
        "$scheme://$ELLIPSIS"
    } else {
        "$scheme://$authority/$ELLIPSIS"
    }
}

fun String?.urlForLog(): String {
    if (isNullOrEmpty()) {
        return UNKNOWN
    }
    return runCatching { Uri.parse(this) }.getOrNull().forLog()
}
