// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import android.net.Uri

sealed class LoadState {
    object Idle : LoadState()
    object Loading : LoadState()
    object PasswordRequired : LoadState()
    data class Loaded(val pageCount: Int) : LoadState()
    data class Failed(val reason: Throwable) : LoadState()
}

class DocumentUnreachableException(uri: Uri?) : Exception("Document is unreachable: $uri")
