// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import android.net.Uri

data class CopyConsentRequest(
    val fileHash: String,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val fitsOnDisk: Boolean,
)
