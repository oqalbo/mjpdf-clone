// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import com.github.barteksc.pdfviewer.PDFView
import java.io.File

object PreviewDiskCoordinator {

    private const val PREVIEW_DIR = "previews"

    fun docKey(fileHash: String, pageCount: Int, sizeBytes: Long?): String {
        val size = sizeBytes?.toString() ?: "ns"
        return "$fileHash-$pageCount-$size"
    }

    fun attach(
        pdfView: PDFView,
        cacheDir: File,
        fileHash: String,
        pageCount: Int,
        sizeBytes: Long?,
        incognito: Boolean,
        hasPassword: Boolean,
    ) {
        if (incognito || hasPassword || pageCount <= 0) {
            return
        }
        pdfView.attachPreviewDisk(File(cacheDir, PREVIEW_DIR), docKey(fileHash, pageCount, sizeBytes))
    }
}
