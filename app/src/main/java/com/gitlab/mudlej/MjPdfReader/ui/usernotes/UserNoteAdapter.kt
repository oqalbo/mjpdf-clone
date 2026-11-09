// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.usernotes

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.convertDateString
import com.gitlab.mudlej.MjPdfReader.databinding.RowUserNoteBinding
import com.gitlab.mudlej.MjPdfReader.pdf.SweptHighlight
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsPathResolver

class UserNoteAdapter(
    private val onClick: (SweptHighlight) -> Unit,
    private val notesMode: Boolean,
) : ListAdapter<SweptHighlight, UserNoteAdapter.UserNoteViewHolder>(SweptHighlightComparator) {

    private val expandedKeys = mutableSetOf<String>()

    @SuppressLint("NotifyDataSetChanged")
    var tableOfContentsPathResolver: TableOfContentsPathResolver = TableOfContentsPathResolver.EMPTY
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserNoteViewHolder {
        val binding = RowUserNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserNoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserNoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun keyOf(item: SweptHighlight): String {
        val idPart = if (item.groupKey.isNotEmpty()) item.groupKey else "i${item.annotationIndex}"
        return "${item.pageIndex}:$idPart"
    }

    inner class UserNoteViewHolder(
        private val binding: RowUserNoteBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SweptHighlight) {
            val context = binding.root.context
            binding.colorStrip.background = GradientDrawable().apply {
                cornerRadius = 3f * context.resources.displayMetrics.density
                setColor(item.color)
            }

            val quoted = item.quotedText.trim().let { if (it.isBlank()) "" else "“$it”" }
            val note = item.note.trim()
            val primary = if (notesMode) note else quoted
            val secondary = if (notesMode) quoted else note

            val expanded = keyOf(item) in expandedKeys

            binding.primaryText.text = primary
            binding.primaryText.maxLines = if (expanded) Int.MAX_VALUE else 2
            binding.primaryText.setTextIsSelectable(false)

            val hasSecondary = secondary.isNotBlank()
            binding.secondaryText.visibility = if (expanded && hasSecondary) View.VISIBLE else View.GONE
            binding.secondaryText.text = secondary
            binding.secondaryText.setTypeface(null, if (notesMode) android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL)

            binding.notePagePill.text = context.getString(R.string.bookmark_page_label, item.pageIndex + 1)

            val tocPath = tableOfContentsPathResolver.resolve(item.pageIndex)
            binding.noteLocationPill.visibility = if (expanded && tocPath != null) View.VISIBLE else View.GONE
            binding.noteLocationPill.text = tocPath

            val date = convertDateString(item.creationDate)
            binding.noteDate.visibility = if (expanded && date != null) View.VISIBLE else View.GONE
            binding.noteDate.text = date

            val canExpand = hasSecondary || tocPath != null || date != null
            binding.expandArrow.visibility = if (canExpand) View.VISIBLE else View.INVISIBLE
            binding.expandArrow.setImageResource(
                if (expanded) R.drawable.ic_small_arrow_up else R.drawable.ic_small_arrow_down
            )
            binding.expandArrow.contentDescription =
                context.getString(if (expanded) R.string.collapse else R.string.expand)
            binding.expandArrow.setOnClickListener {
                val key = keyOf(item)
                if (key in expandedKeys) expandedKeys.remove(key) else expandedKeys.add(key)
                notifyItemChanged(bindingAdapterPosition)
            }

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object SweptHighlightComparator : DiffUtil.ItemCallback<SweptHighlight>() {
        override fun areItemsTheSame(oldItem: SweptHighlight, newItem: SweptHighlight): Boolean {
            if (oldItem.pageIndex != newItem.pageIndex) {
                return false
            }
            return if (oldItem.groupKey.isEmpty() && newItem.groupKey.isEmpty()) {
                oldItem.annotationIndex == newItem.annotationIndex
            } else {
                oldItem.groupKey == newItem.groupKey
            }
        }

        override fun areContentsTheSame(oldItem: SweptHighlight, newItem: SweptHighlight): Boolean {
            return oldItem == newItem
        }
    }
}
