// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.github.barteksc.pdfviewer.source.FileSource
import com.gitlab.mudlej.MjPdfReader.pdf.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfPasswordException
import com.shockwave.pdfium.PdfiumCore
import java.io.File

data class PageSize(val widthPoints: Int, val heightPoints: Int)

data class ProducedHighlight(
    val pageIndex: Int,
    val annotationIndex: Int,
    val groupKey: String,
    val quote: String,
    val note: String,
    val color: Int,
    val appOwned: Boolean,
    val searchResult: Boolean,
    val creationDate: String,
    val hasBounds: Boolean,
    val boundsWidth: Float,
    val boundsHeight: Float,
)

object ProducedPdf {

    fun <T> read(file: File, password: String? = null, block: (PdfExtractor) -> T): T {
        requireFile(file)
        val core = PdfiumCore(TestEnvironment.targetContext)
        val descriptor = FileSource.openReadDescriptor(TestEnvironment.targetContext, file)
        val document = try {
            core.newDocument(descriptor, password)
        } catch (throwable: Throwable) {
            runCatching { descriptor.close() }
            throw throwable
        }
        val extractor = PdfExtractor(core, document)
        return try {
            block(extractor)
        } finally {
            runCatching { extractor.close() }
        }
    }

    fun opensWithoutPassword(file: File): Boolean = runCatching { read(file) { it.getPageCount() } }.isSuccess

    fun refusesWithoutPassword(file: File): Boolean {
        requireFile(file)
        val core = PdfiumCore(TestEnvironment.targetContext)
        return try {
            FileSource.openReadDescriptor(TestEnvironment.targetContext, file).use { descriptor ->
                val document = core.newDocument(descriptor, null)
                runCatching { core.closeDocument(document) }
            }
            false
        } catch (expected: PdfPasswordException) {
            true
        }
    }

    fun pageCount(file: File, password: String? = null): Int = read(file, password) { it.getPageCount() }

    fun pageTexts(file: File, password: String? = null): List<String> = read(file, password) { extractor ->
        (0 until extractor.getPageCount()).map { extractor.getPageTextOrThrow(it + 1) }
    }

    fun pageText(file: File, pageIndex: Int, password: String? = null): String =
        read(file, password) { it.getPageTextOrThrow(pageIndex + 1) }

    fun pageSizes(file: File, password: String? = null): List<PageSize> = withCore(file, password) { core, document ->
        (0 until core.getPageCount(document)).map { index ->
            core.openPage(document, index)
            PageSize(core.getPageWidthPoint(document, index), core.getPageHeightPoint(document, index))
        }
    }

    fun meta(file: File, password: String? = null): PdfDocument.Meta = read(file, password) { extractor ->
        extractor.getMeta() ?: throw AssertionError("${file.absolutePath} carries no document information")
    }

    fun highlightsOn(file: File, pageIndex: Int, password: String? = null): List<ProducedHighlight> =
        read(file, password) { extractor -> extractor.readHighlights(pageIndex) }

    fun allHighlights(file: File, password: String? = null): List<ProducedHighlight> =
        read(file, password) { extractor ->
            (0 until extractor.getPageCount()).flatMap { extractor.readHighlights(it) }
        }

    fun appOwnedHighlights(file: File, password: String? = null): List<ProducedHighlight> =
        allHighlights(file, password).filter { it.appOwned }

    fun formFieldAt(
        file: File,
        pageIndex: Int,
        x: Float,
        y: Float,
        password: String? = null,
        tolerance: Float = FIELD_TOLERANCE,
    ): PdfDocument.FormField? = withCore(file, password) { core, document ->
        core.openPage(document, pageIndex)
        core.getFormFieldAtPoint(document, pageIndex, x, y, tolerance)
    }

    fun formFieldRectCount(file: File, pageIndex: Int, password: String? = null): Int =
        formFieldRects(file, pageIndex, password).size

    fun formFieldRects(file: File, pageIndex: Int, password: String? = null): List<FloatArray> =
        withCore(file, password) { core, document ->
            core.openPage(document, pageIndex)
            val flat = core.getFormFieldRects(document, pageIndex) ?: FloatArray(0)
            (0 until flat.size / RECT_FLOATS).map { index ->
                flat.copyOfRange(index * RECT_FLOATS, index * RECT_FLOATS + RECT_FLOATS)
            }
        }

    fun everyPageOpens(file: File, password: String? = null): Boolean = withCore(file, password) { core, document ->
        runCatching {
            (0 until core.getPageCount(document)).forEach { core.openPage(document, it) }
        }.isSuccess
    }

    private fun <T> withCore(file: File, password: String?, block: (PdfiumCore, PdfDocument) -> T): T {
        requireFile(file)
        val core = PdfiumCore(TestEnvironment.targetContext)
        val descriptor = FileSource.openReadDescriptor(TestEnvironment.targetContext, file)
        val document = try {
            core.newDocument(descriptor, password)
        } catch (throwable: Throwable) {
            runCatching { descriptor.close() }
            throw throwable
        }
        return try {
            block(core, document)
        } finally {
            runCatching { core.closeDocument(document) }
        }
    }

    private fun PdfExtractor.readHighlights(pageIndex: Int): List<ProducedHighlight> =
        getPageHighlights(pageIndex).map { annotation ->
            ProducedHighlight(
                pageIndex = pageIndex,
                annotationIndex = annotation.annotationIndex,
                groupKey = annotation.groupKey,
                quote = annotation.quote,
                note = annotation.note,
                color = annotation.color,
                appOwned = annotation.isAppOwned,
                searchResult = annotation.isSearchResult,
                creationDate = annotation.creationDate,
                hasBounds = annotation.bounds != null,
                boundsWidth = annotation.bounds?.width() ?: 0f,
                boundsHeight = annotation.bounds?.let { it.top - it.bottom } ?: 0f,
            )
        }

    private fun requireFile(file: File) {
        if (!file.isFile) {
            throw AssertionError("No produced file at ${file.absolutePath}")
        }
        if (file.length() == 0L) {
            throw AssertionError("The produced file ${file.absolutePath} is zero bytes long")
        }
    }

    private const val RECT_FLOATS = 4
    const val FIELD_TOLERANCE = 1f
}
