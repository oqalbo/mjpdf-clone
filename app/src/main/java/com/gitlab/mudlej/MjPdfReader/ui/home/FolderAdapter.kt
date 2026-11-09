// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeFolderRowBinding

class FolderAdapter(
    private val onFolderClicked: (FolderNode) -> Unit,
) : ListAdapter<FolderNode, FolderAdapter.FolderViewHolder>(FolderComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemHomeFolderRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(
        private val binding: ItemHomeFolderRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(node: FolderNode) {
            binding.folderName.text = node.name
            binding.folderCount.text = node.count.toString()

            if (node.subtitle != null) {
                binding.folderSubtitle.visibility = View.VISIBLE
                binding.folderSubtitle.text = node.subtitle
            } else {
                binding.folderSubtitle.visibility = View.GONE
            }

            binding.folderCard.setOnClickListener { onFolderClicked(node) }
        }
    }

    class FolderComparator : DiffUtil.ItemCallback<FolderNode>() {

        override fun areItemsTheSame(oldItem: FolderNode, newItem: FolderNode): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FolderNode, newItem: FolderNode): Boolean {
            return oldItem == newItem
        }
    }
}
