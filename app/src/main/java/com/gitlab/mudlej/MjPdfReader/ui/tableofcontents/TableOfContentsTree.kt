// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import com.gitlab.mudlej.MjPdfReader.pdf.TableOfContentsEntry

internal class TableOfContentsTree {
    private val expandedEntryPaths = mutableSetOf<String>()
    var roots: List<TableOfContentsEntry> = emptyList()

    var query: String? = null

    fun buildRows(): List<TableOfContentsRow> {
        val rows = mutableListOf<TableOfContentsRow>()
        roots.filter(::matchesSelfOrDescendant).forEach { addRow(it, rows) }
        return rows
    }

    private fun addRow(entry: TableOfContentsEntry, rows: MutableList<TableOfContentsRow>) {
        val children = visibleChildren(entry)
        val expandable = children.isNotEmpty()
        val expanded = expandable && isExpanded(entry)
        rows.add(TableOfContentsRow(entry, expandable, expanded))
        if (expanded) children.forEach { addRow(it, rows) }
    }

    fun expandAll() {
        addExpandablePaths(roots)
    }

    fun collapseAll() {
        expandedEntryPaths.clear()
    }

    private fun addExpandablePaths(entries: List<TableOfContentsEntry>) {
        entries.forEach { entry ->
            if (entry.hasSubEntries()) {
                expandedEntryPaths.add(entry.path)
                addExpandablePaths(entry.subEntries)
            }
        }
    }

    fun expandAncestors(path: String) {
        val parts = path.split('.')
        for (length in 1 until parts.size) {
            expandedEntryPaths.add(parts.take(length).joinToString("."))
        }
    }

    fun setExpandedEntryPaths(paths: Collection<String>) {
        expandedEntryPaths.clear()
        expandedEntryPaths.addAll(paths)
    }

    fun getExpandedEntryPaths(): ArrayList<String> {
        return ArrayList(expandedEntryPaths)
    }

    fun toggleExpanded(entry: TableOfContentsEntry): Boolean {
        if (isFiltering()) return isExpanded(entry)

        if (!expandedEntryPaths.add(entry.path)) {
            expandedEntryPaths.remove(entry.path)
        }
        return isExpanded(entry)
    }

    fun isExpanded(entry: TableOfContentsEntry): Boolean {
        return hasMatchingVisibleChild(entry) || expandedEntryPaths.contains(entry.path)
    }

    fun visibleChildren(entry: TableOfContentsEntry): List<TableOfContentsEntry> {
        return if (query.isNullOrBlank()) {
            entry.subEntries
        } else {
            entry.subEntries.filter(::matchesSelfOrDescendant)
        }
    }

    fun matchesSelfOrDescendant(entry: TableOfContentsEntry): Boolean {
        if (!isFiltering()) return true
        return matchesSelf(entry) || entry.subEntries.any(::matchesSelfOrDescendant)
    }

    fun visibleEntryCount(entries: List<TableOfContentsEntry>): Int {
        return entries.sumOf { entry -> 1 + visibleEntryCount(visibleChildren(entry)) }
    }

    fun isFiltering(): Boolean {
        return !query.isNullOrBlank()
    }

    private fun hasMatchingVisibleChild(entry: TableOfContentsEntry): Boolean {
        return isFiltering() && entry.subEntries.any(::matchesSelfOrDescendant)
    }

    private fun matchesSelf(entry: TableOfContentsEntry): Boolean {
        val activeQuery = query?.trim() ?: return true
        if (activeQuery.isBlank()) return true

        return entry.title.orEmpty().contains(activeQuery, ignoreCase = true)
                || (entry.pageIdx + 1).toString().contains(activeQuery)
    }

}
