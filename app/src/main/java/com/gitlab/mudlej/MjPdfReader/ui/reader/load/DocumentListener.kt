// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import android.net.Uri
import com.github.barteksc.pdfviewer.model.CropMargins

class DocumentLoadedEvent(
    val pageCount: Int,
    val cachedCropMargins: CropMargins?,
    val fileHash: String?,
    val loadToken: Long,
    val documentUri: Uri?,
    val applyDocumentLoadDefaults: Boolean,
)

interface DocumentListener {
    fun onDocumentReset() {}
    fun onDocumentLoaded(event: DocumentLoadedEvent) {}
    fun onPageChanged(pageIndex: Int) {}
    fun onFileHashComputed() {}
    fun onRecordAvailable(fileHash: String) {}
    fun onLoadFailed(reason: Throwable) {}
}
