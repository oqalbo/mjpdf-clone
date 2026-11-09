// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import android.graphics.RectF

data class SweptHighlight(
    val pageIndex: Int,
    val annotationIndex: Int,
    val groupKey: String,
    val color: Int,
    val quotedText: String,
    val note: String,
    val creationDate: String?,
    val bounds: RectF? = null,
)

fun sweepPageHighlights(extractor: PdfExtractor, pageIndex: Int): List<SweptHighlight> {
    val seenGroups = mutableSetOf<String>()
    val swept = mutableListOf<SweptHighlight>()
    for (annotation in extractor.getPageHighlights(pageIndex)) {
        val group = annotation.groupKey
        if (group.isNotEmpty() && !seenGroups.add(group)) {
            continue
        }
        swept.add(
            SweptHighlight(
                pageIndex = pageIndex,
                annotationIndex = annotation.annotationIndex,
                groupKey = group,
                color = annotation.color,
                quotedText = annotation.quote,
                note = annotation.note,
                creationDate = annotation.creationDate.takeIf { it.isNotBlank() },
                bounds = annotation.bounds?.let { RectF(it) },
            )
        )
    }
    return swept
}
