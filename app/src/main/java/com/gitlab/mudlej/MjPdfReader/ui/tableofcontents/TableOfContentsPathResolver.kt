// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import android.app.Activity
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.pdf.TableOfContentsEntry
import com.gitlab.mudlej.MjPdfReader.pdf.createPdfExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TableOfContentsPathResolver private constructor(
    private val entries: List<Entry>,
) {

    data class Entry(
        val pageIndex: Int,
        val path: String,
    )

    fun resolve(pageIndex: Int): String? {
        if (entries.isEmpty() || pageIndex < 0) {
            return null
        }
        return entries.lastOrNull { it.pageIndex <= pageIndex }?.path
    }

    companion object {
        val EMPTY = TableOfContentsPathResolver(emptyList())

        private const val PATH_SEPARATOR = " ▶ "

        suspend fun load(activity: Activity, pdfPath: String?, password: String?): TableOfContentsPathResolver {
            if (pdfPath.isNullOrBlank()) {
                return EMPTY
            }
            return withContext(Dispatchers.IO) {
                try {
                    val extractor = createPdfExtractor(activity, Uri.parse(pdfPath), password)
                    try {
                        fromBookmarks(extractor.getTableOfContents())
                    } finally {
                        runCatching { extractor.close() }
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    EMPTY
                }
            }
        }

        internal fun fromBookmarks(entries: List<TableOfContentsEntry>): TableOfContentsPathResolver {
            val paths = mutableListOf<Pair<Int, List<String>>>()
            entries.forEach { entry -> addBookmark(paths, entry, emptyList()) }
            return TableOfContentsPathResolver(
                paths
                    .sortedWith(compareBy({ it.first }, { it.second.size }))
                    .map { (pageIndex, titles) -> Entry(pageIndex, titles.joinToString(PATH_SEPARATOR)) }
            )
        }

        private fun addBookmark(paths: MutableList<Pair<Int, List<String>>>, entry: TableOfContentsEntry, parentTitles: List<String>) {
            val title = entry.title?.trim().orEmpty()
            val titles = if (title.isBlank()) parentTitles else parentTitles + title
            if (entry.pageIdx >= 0 && titles.isNotEmpty()) {
                paths.add(entry.pageIdx.toInt() to titles)
            }
            entry.subEntries.forEach { child -> addBookmark(paths, child, titles) }
        }
    }
}
