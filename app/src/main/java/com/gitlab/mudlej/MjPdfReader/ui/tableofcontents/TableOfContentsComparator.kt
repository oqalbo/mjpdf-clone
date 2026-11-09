// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import androidx.recyclerview.widget.DiffUtil

class TableOfContentsComparator : DiffUtil.ItemCallback<TableOfContentsRow>() {
    override fun areItemsTheSame(oldItem: TableOfContentsRow, newItem: TableOfContentsRow): Boolean
            = oldItem.entry.path == newItem.entry.path

    override fun areContentsTheSame(oldItem: TableOfContentsRow, newItem: TableOfContentsRow): Boolean
            = oldItem.entry.path == newItem.entry.path
            && oldItem.entry.level == newItem.entry.level
            && oldItem.entry.title == newItem.entry.title
            && oldItem.entry.pageIdx == newItem.entry.pageIdx
            && oldItem.expandable == newItem.expandable
            && oldItem.expanded == newItem.expanded
}
