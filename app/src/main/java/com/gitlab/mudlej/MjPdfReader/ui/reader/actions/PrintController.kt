// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import android.app.Activity
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PrintManager
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.data.OnlineDocumentStore
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrintController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val scope: CoroutineScope,
    private val onShareInstead: () -> Unit,
) {

    private var staging = false

    fun printFile() {
        if (staging) {
            return
        }
        val documentUri = pdf.uri ?: return
        staging = true
        binding.progressBar.isIndeterminate = true
        binding.progressBar.visibility = View.VISIBLE
        scope.launch {
            val staged = withContext(Dispatchers.IO) {
                runCatching { stagePrintFile(documentUri) }.getOrNull()
            }
            staging = false
            binding.progressBar.visibility = View.GONE
            if (staged == null) {
                AppSnackbar.make(binding.root, R.string.print_cannot_print, Snackbar.LENGTH_LONG)
                    .setAction(R.string.share_file) { onShareInstead() }
                    .show()
                return@launch
            }
            printStagedFile(staged)
        }
    }

    private fun printStagedFile(staged: File) {
        val printManager = activity.getSystemService(PrintManager::class.java)
        if (printManager == null) {
            staged.delete()
            AppSnackbar.make(binding.root, R.string.print_failed, Snackbar.LENGTH_LONG).show()
            return
        }
        try {
            printManager.print(
                pdf.name,
                PdfDocumentAdapter(activity, Uri.fromFile(staged), staged), null
            )
        }
        catch (e: Throwable) {
            staged.delete()
            AppSnackbar.make(binding.root, R.string.print_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun stagePrintFile(documentUri: Uri): File? {
        val directory = File(activity.cacheDir, STAGING_DIRECTORY)
        directory.mkdirs()
        sweepOldFiles(directory)
        val staged = File(directory, "print-${System.currentTimeMillis()}.pdf")
        if (!pdf.password.isNullOrEmpty()) {
            return stageDecryptedCopy(staged)
        }
        if (copyDocumentTo(documentUri, staged) && verifyPrintable(staged)) {
            return staged
        }
        return stageDecryptedCopy(staged)
    }

    private fun stageDecryptedCopy(staged: File): File? {
        val saved = runCatching { binding.pdfView.saveDecryptedCopy(staged) }.getOrDefault(false)
        if (saved && verifyPrintable(staged)) {
            return staged
        }
        staged.delete()
        return null
    }

    private fun copyDocumentTo(documentUri: Uri, target: File): Boolean {
        return runCatching {
            val heldFile = OnlineDocumentStore.fileFor(activity, documentUri.toString())
            val input = if (heldFile != null) {
                heldFile.inputStream()
            } else {
                activity.contentResolver.openInputStream(documentUri)
            } ?: return@runCatching false
            input.use { source ->
                FileOutputStream(target).use { output -> source.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }

    private fun verifyPrintable(file: File): Boolean {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount <= 0) {
                        return@runCatching false
                    }
                    renderer.openPage(0).use { }
                    true
                }
            }
        }.getOrDefault(false)
    }

    private fun sweepOldFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_STAGING_AGE_MILLIS
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    companion object {
        fun sweepStaging(context: Context) {
            File(context.cacheDir, STAGING_DIRECTORY).listFiles()?.forEach { it.delete() }
        }

        private const val STAGING_DIRECTORY = "print"
        private const val MAX_STAGING_AGE_MILLIS = 60L * 60 * 1000
    }
}
