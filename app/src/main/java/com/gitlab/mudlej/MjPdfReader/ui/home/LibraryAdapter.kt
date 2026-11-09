// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeGridCellBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeListRowBinding
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.core.io.formatRelativeDate
import com.gitlab.mudlej.MjPdfReader.core.text.StringUtil.formatEnumToTitle
import kotlinx.coroutines.CoroutineScope

enum class ListMetaStyle { FOLDERS, LIBRARY, RECENT }

class LibraryAdapter(
    private val coverCache: CoverCache,
    private val scope: CoroutineScope,
    private val functions: HomeItemFunctions,
    private val pref: Preferences,
    private val selection: () -> Set<String> = { emptySet() },
) : ListAdapter<HomeItem, RecyclerView.ViewHolder>(HomeItemComparator()) {

    var viewMode: HomeViewMode = HomeViewMode.GRID
    var coverWidthPx: Int = DEFAULT_COVER_WIDTH_PX
    var metaStyle: ListMetaStyle = ListMetaStyle.LIBRARY
    var progressStyle: HomeProgressStyle = HomeProgressStyle.RING

    private var appliedTitleSignature: String? = null

    fun applyTitleStyle() {
        val signature = titleSignature()
        val firstApply = appliedTitleSignature == null
        appliedTitleSignature = signature
        if (!firstApply) {
            notifyDataSetChanged()
        }
    }

    private fun titleSignature(): String {
        return "${pref.getHomeGridTitleLines()}|${pref.getHomeListTitleLines()}|${pref.getHomeTitleEllipsize().name}" +
            "|${pref.getHomeBadgePages()}|${pref.getHomeBadgeProgress()}|${pref.getHomeBadgeLastOpened()}" +
            "|${pref.getHomeBadgeFileSize()}|${pref.getHomeBadgeStatus()}"
    }

    private fun TextView.applyTitleLines(maxLines: Int) {
        this.maxLines = maxLines
        ellipsize = pref.getHomeTitleEllipsize().truncateAt
    }

    override fun getItemViewType(position: Int): Int {
        return if (viewMode == HomeViewMode.GRID) TYPE_GRID else TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            GridViewHolder(ItemHomeGridCellBinding.inflate(inflater, parent, false))
        } else {
            ListViewHolder(ItemHomeListRowBinding.inflate(inflater, parent, false))
        }
    }
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.itemAnimator = null
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is GridViewHolder -> holder.bind(item)
            is ListViewHolder -> holder.bind(item)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isNotEmpty() && payloads.all { it == SELECTION_PAYLOAD }) {
            val item = getItem(position)
            when (holder) {
                is GridViewHolder -> holder.applySelection(item)
                is ListViewHolder -> holder.applySelection(item)
            }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun notifySelectionChanged() {
        notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
    }

    private fun isSelected(item: HomeItem) = item.stableKey in selection()

    inner class GridViewHolder(
        private val binding: ItemHomeGridCellBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeItem) {
            binding.title.text = item.title
            binding.title.applyTitleLines(pref.getHomeGridTitleLines())

            if (item.hasBeenOpened) {
                binding.lastOpenedLabel.visibility = View.VISIBLE
                binding.lastOpenedLabel.text =
                    formatRelativeDate(binding.root.context, item.lastOpened)
            } else {
                binding.lastOpenedLabel.visibility = View.GONE
            }

            val showBar = item.progressPercent > 0 && progressStyle == HomeProgressStyle.BAR
            val showRing = item.progressPercent > 0 && progressStyle == HomeProgressStyle.RING
            binding.progress.visibility = if (showBar) View.VISIBLE else View.GONE
            binding.progressBadge.visibility = if (showRing) View.VISIBLE else View.GONE
            if (showBar) {
                binding.progress.progress = item.progressPercent
            }
            if (showRing) {
                binding.progressCircle.progress = item.progressPercent
                binding.progressPercent.text = item.progressPercent.toString()
            }

            applySelection(item)
            coverCache.bind(binding.cover, item.coverKey, item.uri, coverWidthPx, scope)

            binding.coverCard.setOnClickListener { functions.onItemClicked(item) }
            binding.coverCard.setOnLongClickListener { functions.onItemLongClicked(item) }
            binding.optionsButton.setOnClickListener { functions.onItemOptionsClicked(item) }
        }

        fun applySelection(item: HomeItem) {
            binding.root.alpha = if (item.available) 1f else UNAVAILABLE_ALPHA
            binding.coverCard.isChecked = isSelected(item)
        }
    }

    inner class ListViewHolder(
        private val binding: ItemHomeListRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeItem) {
            binding.title.text = item.title
            binding.title.applyTitleLines(pref.getHomeListTitleLines())
            bindMetaBadges(item)

            applySelection(item)
            coverCache.bind(binding.cover, item.coverKey, item.uri, LIST_COVER_WIDTH_PX, scope)

            binding.listCard.setOnClickListener { functions.onItemClicked(item) }
            binding.listCard.setOnLongClickListener { functions.onItemLongClicked(item) }
            binding.optionsButton.setOnClickListener { functions.onItemOptionsClicked(item) }
        }

        fun applySelection(item: HomeItem) {
            binding.root.alpha = if (item.available) 1f else UNAVAILABLE_ALPHA
            binding.listCard.isChecked = isSelected(item)
        }

        private fun bindMetaBadges(item: HomeItem) {
            val container = binding.metaBadges
            container.removeAllViews()

            val parts = buildMetaParts(item)
            if (parts.isEmpty()) {
                container.visibility = View.GONE
                return
            }
            container.visibility = View.VISIBLE

            val inflater = LayoutInflater.from(container.context)
            parts.forEach { (text, accent) ->
                val layout = if (accent) R.layout.home_meta_badge_accent else R.layout.home_meta_badge
                val badge = inflater.inflate(layout, container, false) as TextView
                badge.text = text
                container.addView(badge)
            }
        }

        private fun buildMetaParts(item: HomeItem): List<Badge> {
            val context = binding.root.context
            return metaBadges(
                metaStyle,
                BadgeToggles(
                    pages = pref.getHomeBadgePages(),
                    fileSize = metaStyle == ListMetaStyle.FOLDERS && pref.getHomeBadgeFileSize(),
                    progress = metaStyle != ListMetaStyle.FOLDERS && pref.getHomeBadgeProgress(),
                    lastOpened = metaStyle != ListMetaStyle.FOLDERS && pref.getHomeBadgeLastOpened(),
                    status = pref.getHomeBadgeStatus(),
                ),
                item.pageNumber, item.length, item.readingStarted, item.progressPercent,
                item.hasBeenOpened, item.sizeBytes, item.readingStatus,
                BadgeFormatters(
                    pages = { context.resources.getQuantityString(R.plurals.home_pages, it, it) },
                    size = { Formatter.formatShortFileSize(context, it) },
                    lastOpened = { formatRelativeDate(context, item.lastOpened) },
                    status = { it.name.formatEnumToTitle() },
                ),
            )
        }
    }

    companion object {
        private const val TYPE_GRID = 0
        private const val TYPE_LIST = 1
        private const val DEFAULT_COVER_WIDTH_PX = 320
        private const val LIST_COVER_WIDTH_PX = 192
        private const val SELECTION_PAYLOAD = "selection"
        private const val UNAVAILABLE_ALPHA = 0.45f
    }
}

class HomeItemComparator : DiffUtil.ItemCallback<HomeItem>() {

    override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
        return oldItem.stableKey == newItem.stableKey
    }

    override fun areContentsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
        return oldItem == newItem
    }
}

internal data class Badge(val text: String, val accent: Boolean = false)

internal data class BadgeToggles(
    val pages: Boolean,
    val fileSize: Boolean,
    val progress: Boolean,
    val lastOpened: Boolean,
    val status: Boolean,
)

internal data class BadgeFormatters(
    val pages: (Int) -> String,
    val size: (Long) -> String,
    val lastOpened: () -> String,
    val status: (ReadingStatus) -> String,
)

internal fun metaBadges(
    style: ListMetaStyle,
    toggles: BadgeToggles,
    pageNumber: Int,
    length: Int,
    readingStarted: Boolean,
    progressPercent: Int,
    hasBeenOpened: Boolean,
    sizeBytes: Long,
    status: ReadingStatus,
    formatters: BadgeFormatters,
): List<Badge> {
    val parts = mutableListOf<Badge>()
    if (toggles.pages && length > 0) {
        parts.add(Badge(if (readingStarted) "${pageNumber + 1}/$length" else formatters.pages(length)))
    }
    if (style == ListMetaStyle.FOLDERS) {
        if (toggles.fileSize && sizeBytes > 0) parts.add(Badge(formatters.size(sizeBytes)))
    } else {
        if (toggles.progress && progressPercent > 0) parts.add(Badge("$progressPercent%"))
        if (toggles.lastOpened && hasBeenOpened) parts.add(Badge(formatters.lastOpened()))
    }
    if (toggles.status && style == ListMetaStyle.RECENT && status != ReadingStatus.UNSET) {
        parts.add(Badge(formatters.status(status), true))
    }
    return parts
}
