// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.net

import android.os.Build
import java.net.URLConnection

fun URLConnection.contentLengthCompat(): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        contentLengthLong
    } else {
        contentLength.toLong()
    }
}
