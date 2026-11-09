// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.graphics.Bitmap
import android.graphics.Color
import com.github.barteksc.pdfviewer.source.FileSource
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

object PdfRenderOracle {

    fun pageDigest(file: File, pageIndex: Int = 0, password: String? = null): String {
        if (!file.isFile || file.length() == 0L) {
            throw AssertionError("No non-empty PDF exists at ${file.absolutePath}")
        }
        val core = PdfiumCore(TestEnvironment.targetContext)
        val descriptor = FileSource.openReadDescriptor(TestEnvironment.targetContext, file)
        val document: PdfDocument = try {
            core.newDocument(descriptor, password)
        } catch (throwable: Throwable) {
            runCatching { descriptor.close() }
            throw throwable
        }
        var pageOpen = false
        var bitmap: Bitmap? = null
        try {
            val pageCount = core.getPageCount(document)
            if (pageIndex !in 0 until pageCount) {
                throw AssertionError("${file.name} has $pageCount pages, so page index $pageIndex cannot be rendered")
            }
            core.openPage(document, pageIndex)
            pageOpen = true
            val widthPoints = core.getPageWidthPoint(document, pageIndex)
            val heightPoints = core.getPageHeightPoint(document, pageIndex)
            if (widthPoints <= 0 || heightPoints <= 0) {
                throw AssertionError("${file.name} page $pageIndex has invalid size ${widthPoints}x$heightPoints")
            }
            val heightPx = (RENDER_WIDTH_PX.toFloat() * heightPoints / widthPoints).roundToInt().coerceAtLeast(1)
            val rendered = Bitmap.createBitmap(RENDER_WIDTH_PX, heightPx, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
            bitmap = rendered
            core.renderPageBitmap(document, rendered, pageIndex, 0, 0, rendered.width, rendered.height, false)
            return digest(rendered)
        } finally {
            bitmap?.recycle()
            if (pageOpen) runCatching { core.closePage(document, pageIndex) }
            runCatching { core.closeDocument(document) }
            runCatching { descriptor.close() }
        }
    }

    fun digest(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val pixels = IntArray(bitmap.width)
        val bytes = ByteArray(bitmap.width * Int.SIZE_BYTES)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            pixels.forEachIndexed { index, pixel ->
                val offset = index * Int.SIZE_BYTES
                bytes[offset] = (pixel ushr 24).toByte()
                bytes[offset + 1] = (pixel ushr 16).toByte()
                bytes[offset + 2] = (pixel ushr 8).toByte()
                bytes[offset + 3] = pixel.toByte()
            }
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val RENDER_WIDTH_PX = 720
}
