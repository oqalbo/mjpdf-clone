// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.links

import androidx.recyclerview.widget.DiffUtil
import com.gitlab.mudlej.MjPdfReader.pdf.Link

class LinkComparator : DiffUtil.ItemCallback<Link>() {
    override fun areItemsTheSame(oldItem: Link, newItem: Link): Boolean
            = oldItem.hashCode() == newItem.hashCode()

    override fun areContentsTheSame(oldItem: Link, newItem: Link): Boolean
            = oldItem == newItem
}