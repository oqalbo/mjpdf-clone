// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.gitlab.mudlej.MjPdfReader.pdf.TableOfContentsEntry
import com.gitlab.mudlej.MjPdfReader.databinding.TableOfContentsRowItemBinding


class TableOfContentsAdapter(
    val bookmarkFunctions: TableOfContentsFunctions,
    val activity: TableOfContentsActivity
) : ListAdapter<TableOfContentsRow, TableOfContentsViewHolder>(TableOfContentsComparator()) {

    private val tree = TableOfContentsTree()

    var query: String?
        get() = tree.query
        set(value) { tree.query = value }
    var currentEntryPath: String? = null

    fun submitEntries(newRoots: List<TableOfContentsEntry>, commitCallback: (() -> Unit)? = null) {
        tree.roots = newRoots
        refresh(commitCallback)
    }

    fun refresh(commitCallback: (() -> Unit)? = null) {
        submitList(tree.buildRows(), commitCallback)
    }

    fun onToggleClicked(entry: TableOfContentsEntry) {
        if (isFiltering()) return
        toggleExpanded(entry)
        refresh()
    }

    fun expandAll() {
        tree.expandAll()
        refresh()
    }

    fun collapseAll() {
        tree.collapseAll()
        refresh()
    }

    fun expandAncestors(path: String) = tree.expandAncestors(path)
    fun setExpandedEntryPaths(paths: Collection<String>) = tree.setExpandedEntryPaths(paths)
    fun getExpandedEntryPaths(): ArrayList<String> = tree.getExpandedEntryPaths()
    fun toggleExpanded(entry: TableOfContentsEntry): Boolean = tree.toggleExpanded(entry)
    fun isExpanded(entry: TableOfContentsEntry): Boolean = tree.isExpanded(entry)
    fun visibleChildren(entry: TableOfContentsEntry): List<TableOfContentsEntry> = tree.visibleChildren(entry)
    fun matchesSelfOrDescendant(entry: TableOfContentsEntry): Boolean = tree.matchesSelfOrDescendant(entry)
    fun visibleEntryCount(entries: List<TableOfContentsEntry>): Int = tree.visibleEntryCount(entries)
    fun isFiltering(): Boolean = tree.isFiltering()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TableOfContentsViewHolder {
        return TableOfContentsViewHolder(
            TableOfContentsRowItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            this,
        )
    }

    override fun onBindViewHolder(holder: TableOfContentsViewHolder, i: Int) {
        getItem(i)?.let { holder.bind(it) }
    }

}
