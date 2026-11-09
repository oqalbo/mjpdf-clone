// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class PdfDocumentAdapter(
    private val context: Context,
    private val documentUri: Uri?,
    private val deleteOnFinish: File? = null,
) : PrintDocumentAdapter() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        executor.submit {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
            } else {
                val info = PrintDocumentInfo.Builder("document.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, newAttributes != oldAttributes)
            }
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        executor.submit {
            try {
                val uri = requireNotNull(documentUri)
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (!cancellationSignal.isCanceled) {
                            val size = input.read(buffer)
                            if (size < 0) {
                                break
                            }
                            output.write(buffer, 0, size)
                        }
                    }
                }
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                } else {
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
                Log.e(TAG, "Exception printing PDF", e)
            }
        }
    }

    override fun onFinish() {
        executor.shutdown()
        deleteOnFinish?.delete()
        super.onFinish()
    }

    private companion object {
        const val TAG = "PdfDocumentAdapter"
        const val COPY_BUFFER_SIZE = 16384
    }
}
