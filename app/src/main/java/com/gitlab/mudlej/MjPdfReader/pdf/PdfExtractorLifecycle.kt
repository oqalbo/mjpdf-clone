// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.gitlab.mudlej.MjPdfReader.core.io.urlForLog

fun Activity.openPdfExtractorFromIntent(): PdfExtractor? {
    val pdfPath = intent.getStringExtra(PDF.filePathKey)
    val pdfPassword = intent.getStringExtra(PDF.passwordKey)
    return try {
        createPdfExtractor(this, Uri.parse(pdfPath), pdfPassword)
    } catch (throwable: Throwable) {
        Log.e("PdfExtractorLifecycle", "Failed to open PdfExtractor for URI=${pdfPath.urlForLog()}", throwable)
        null
    }
}
