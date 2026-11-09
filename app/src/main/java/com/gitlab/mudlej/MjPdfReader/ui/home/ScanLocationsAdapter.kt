// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gitlab.mudlej.MjPdfReader.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.ItemScanLocationRowBinding

data class ScanLocationRow(
    val path: String,
    val name: String,
    val checkedState: Int,
    val checkboxEnabled: Boolean,
    val isUp: Boolean = false,
)

class ScanLocationsAdapter(
    private val onRowClicked: (ScanLocationRow) -> Unit,
    private val onCheckToggled: (ScanLocationRow) -> Unit,
) : ListAdapter<ScanLocationRow, ScanLocationsAdapter.ScanLocationViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanLocationViewHolder {
        val binding = ItemScanLocationRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScanLocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScanLocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ScanLocationViewHolder(
        private val binding: ItemScanLocationRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ScanLocationRow) {
            binding.locationName.text = row.name
            binding.locationIcon.setImageResource(
                if (row.isUp) R.drawable.ic_small_arrow_up else R.drawable.ic_folder
            )
            binding.locationCheckBox.visibility = if (row.isUp) View.GONE else View.VISIBLE
            binding.locationCheckBox.setOnClickListener(null)
            binding.locationCheckBox.checkedState = row.checkedState
            binding.locationCheckBox.isEnabled = row.checkboxEnabled
            binding.locationCheckBox.setOnClickListener { onCheckToggled(row) }
            binding.root.setOnClickListener { onRowClicked(row) }
        }
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<ScanLocationRow>() {
            override fun areItemsTheSame(oldItem: ScanLocationRow, newItem: ScanLocationRow): Boolean {
                return oldItem.path == newItem.path
            }

            override fun areContentsTheSame(oldItem: ScanLocationRow, newItem: ScanLocationRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}
