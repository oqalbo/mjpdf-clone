// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.annotation

import android.net.Uri
import java.security.MessageDigest

object SourceKey {

    fun of(uri: Uri): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
