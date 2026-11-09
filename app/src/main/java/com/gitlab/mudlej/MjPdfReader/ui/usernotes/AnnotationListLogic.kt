// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.usernotes

import com.gitlab.mudlej.MjPdfReader.core.io.appendExportSection
import com.gitlab.mudlej.MjPdfReader.pdf.SweptHighlight
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import java.util.Locale

internal val annotationComparator = compareBy<SweptHighlight> { it.creationDate == null }
    .thenByDescending { it.creationDate ?: "" }

internal fun filterAnnotations(items: List<SweptHighlight>, query: String, color: Int?): List<SweptHighlight> {
    var displayed = items
    color?.let { selected -> displayed = displayed.filter { it.color == selected } }
    if (query.isNotBlank()) {
        displayed = displayed.filter { it.quotedText.contains(query, true) || it.note.contains(query, true) }
    }
    return displayed
}

internal fun annotationColorLabel(color: Int, paletteName: (HighlightPalette) -> String): String {
    val palette = HighlightPalette.fromColor(color)
    return if (palette != null) paletteName(palette) else String.format(Locale.US, "#%06X", color and 0xFFFFFF)
}

internal data class AnnotationExportLabels(
    val author: (String) -> String,
    val summary: (Int) -> String,
    val page: (Int) -> String,
    val date: (String?) -> String?,
    val quote: String,
    val note: String,
    val color: ((Int) -> String)? = null,
)

internal fun buildAnnotationExport(
    title: String,
    author: String?,
    items: List<SweptHighlight>,
    resolver: (Int) -> String?,
    labels: AnnotationExportLabels,
    sortByDate: Boolean,
    includeBlankNotes: Boolean,
): String {
    val builder = StringBuilder()
    builder.appendLine(title)
    author?.takeIf { it.isNotBlank() }?.let { builder.appendLine(labels.author(it)) }
    builder.appendLine(labels.summary(items.size))
    val exported = if (sortByDate) items.sortedWith(annotationComparator) else items
    for (item in exported) {
        builder.appendLine()
        builder.appendLine("----------------------------------------")
        val infoParts = mutableListOf(labels.page(item.pageIndex + 1))
        resolver(item.pageIndex)?.let(infoParts::add)
        labels.date(item.creationDate)?.let(infoParts::add)
        labels.color?.let { infoParts.add(it(item.color)) }
        builder.appendLine(infoParts.joinToString(" | "))
        if (item.quotedText.isNotBlank()) {
            builder.appendExportSection(labels.quote, item.quotedText.trim())
        }
        if (includeBlankNotes || item.note.isNotBlank()) {
            builder.appendExportSection(labels.note, item.note)
        }
    }
    return builder.toString()
}
