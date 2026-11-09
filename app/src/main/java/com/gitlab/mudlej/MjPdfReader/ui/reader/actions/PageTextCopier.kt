// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import android.util.Log
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.showCopyPageTextDialog
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.google.android.material.snackbar.Snackbar
import com.shockwave.pdfium.PageTextTooLargeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageTextCopier(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val scope: CoroutineScope,
) {

    private val shouldStopExtracting: MutableMap<Int, Boolean> = mutableMapOf()
    private var showNoTextInPage = true

    fun resetForNewDocument() {
        shouldStopExtracting.clear()
        showNoTextInPage = true
    }

    fun copyPageText() {
        val pageNumber = pdf.pageNumber
        if (shouldStopExtracting.getOrElse(pageNumber) { false }) {
            return
        }

        scope.launch(Dispatchers.IO) {
            var pageText = ""
            var extractionFailed = false
            var pageTooLarge = false
            try {
                pageText = binding.pdfView.getPageText(pageNumber)
            }
            catch (e: PageTextTooLargeException) {
                Log.w("PDFium", "extractPageText($pageNumber): ${e.message}")
                pageTooLarge = true
            }
            catch (e: Throwable) {
                Log.e("PDFium", "extractPageText($pageNumber): error while extracting text", e)
                extractionFailed = true
            }

            withContext(Dispatchers.Main) {
                if (pageTooLarge) {
                    AppSnackbar.make(binding.root, R.string.page_text_too_large, Snackbar.LENGTH_LONG).show()
                }
                else if (extractionFailed) {
                    showFailedExtractTextSnackbar(pageNumber)
                }
                else if (pageText.isBlank()) {
                    showNoTextInPageMessage()
                }
                else {
                    showCopyPageTextDialog(activity, binding, pageNumber, pageText)
                }
            }
        }
    }

    private fun showFailedExtractTextSnackbar(pageNumber: Int) {
        AppSnackbar.make(binding.root, "Failed to extract text of this file.", Snackbar.LENGTH_SHORT)
            .setAction("Stop this message") { shouldStopExtracting[pageNumber] = true }
            .show()
    }

    private fun showNoTextInPageMessage() {
        if (showNoTextInPage) {
            AppSnackbar.make(binding.root, "Couldn't find text in this page.", Snackbar.LENGTH_LONG).show()
            showNoTextInPage = false
        }
    }
}
