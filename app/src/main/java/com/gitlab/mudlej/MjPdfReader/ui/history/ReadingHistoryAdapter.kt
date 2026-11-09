// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.appDateFormatter
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.databinding.RowReadingHistoryBinding
import java.time.LocalDateTime

class ReadingHistoryAdapter(
    private val onDeleteClicked: (PdfRecord) -> Unit,
) : ListAdapter<PdfRecord, ReadingHistoryAdapter.ViewHolder>(RecordDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowReadingHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: RowReadingHistoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: PdfRecord) {
            val context = binding.root.context
            binding.readingHistoryTitle.text = displayNameOf(record)

            val lastOpenedText = if (record.lastOpened == unsetDate) {
                context.getString(R.string.reading_history_never_opened)
            } else {
                context.getString(R.string.home_last_opened, record.lastOpened.format(appDateFormatter))
            }
            binding.readingHistorySubtitle.text = if (record.length > 0) {
                val pageText = context.getString(R.string.page_x_of_y, record.pageNumber + 1, record.length)
                "$lastOpenedText · $pageText"
            } else {
                lastOpenedText
            }

            binding.readingHistoryDelete.setOnClickListener { onDeleteClicked(record) }
        }
    }

    companion object {
        private val unsetDate = LocalDateTime.parse(PdfRecord.UNSET_DATE)

        fun displayNameOf(record: PdfRecord): String {
            return record.fileName.ifBlank { record.documentTitle.orEmpty() }.ifBlank { PdfRecord.UNSET_NAME }
        }

        private object RecordDiff : DiffUtil.ItemCallback<PdfRecord>() {
            override fun areItemsTheSame(oldItem: PdfRecord, newItem: PdfRecord): Boolean {
                return oldItem.hash == newItem.hash
            }

            override fun areContentsTheSame(oldItem: PdfRecord, newItem: PdfRecord): Boolean {
                return oldItem == newItem
            }
        }
    }
}
