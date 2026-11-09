// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.state

import android.view.View
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object ReaderStateSource {

    fun readRaw(): String? = TestEnvironment.onMainThread {
        val view = findReaderView() ?: return@onMainThread null
        debugStateOf(view) ?: view.contentDescription?.toString()
    }

    fun read(): ReaderState? = readRaw()?.takeIf { it.isNotEmpty() }?.let(ReaderStateParser::parse)

    private fun findReaderView(): View? =
        TestEnvironment.resumedActivity()?.findViewById(R.id.pdfView)

    private fun debugStateOf(view: View): String? =
        (view as? PDFView)?.let { pdfView -> runCatching { pdfView.debugState() }.getOrNull() }
}
