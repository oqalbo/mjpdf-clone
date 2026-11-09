// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal fun showActionSelectionPreferenceDialog(
    context: Context,
    @StringRes titleRes: Int,
    rows: MutableList<ActionSelectionRow>,
    defaultRows: List<ActionSelectionRow>,
    onApply: (List<ActionSelectionRow>) -> Unit,
) {
    val adapter = ActionSelectionAdapter(rows)
    val recyclerView = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    val touchHelper = ItemTouchHelper(ActionSelectionTouchCallback(adapter))
    adapter.onDragRequested = touchHelper::startDrag
    touchHelper.attachToRecyclerView(recyclerView)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(titleRes)
        .setView(recyclerView)
        .setPositiveButton(R.string.apply) { _, _ -> onApply(rows) }
        .setNegativeButton(R.string.cancel, null)
        .setNeutralButton(R.string.reset, null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            rows.resetTo(defaultRows)
            adapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(0)
        }
    }
    dialog.show()
}

internal data class ActionSelectionRow(
    val action: ConfigurableAction,
    var enabled: Boolean,
    val locked: Boolean = false,
) {
    init {
        if (locked) {
            enabled = true
        }
    }
}

internal fun List<ActionSelectionRow>.enabledActionIds(): Set<String> {
    return filter { it.enabled || it.locked }.map { it.action.id }.toSet()
}

internal fun List<ActionSelectionRow>.actionIds(): List<String> {
    return map { it.action.id }
}

private fun MutableList<ActionSelectionRow>.resetTo(defaultRows: List<ActionSelectionRow>) {
    clear()
    addAll(defaultRows.map { it.copy() })
}

internal fun MutableList<ActionSelectionRow>.move(from: Int, to: Int): Boolean {
    if (from !in indices || to !in indices) {
        return false
    }
    if (this[from].locked || this[to].locked) {
        return false
    }
    add(to, removeAt(from))
    return true
}

private class ActionSelectionAdapter(
    private val rows: MutableList<ActionSelectionRow>,
) : RecyclerView.Adapter<ActionSelectionViewHolder>() {

    var onDragRequested: ((RecyclerView.ViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionSelectionViewHolder {
        return ActionSelectionViewHolder(parent.createRowView())
    }

    override fun onBindViewHolder(holder: ActionSelectionViewHolder, position: Int) {
        holder.bind(rows[position], onDragRequested)
    }

    override fun getItemCount() = rows.size

    fun move(from: Int, to: Int): Boolean {
        if (!rows.move(from, to)) return false
        notifyItemMoved(from, to)
        return true
    }
}

private class ActionSelectionViewHolder(
    view: View,
) : RecyclerView.ViewHolder(view) {

    private val checkbox = view.findViewWithTag<MaterialCheckBox>(CHECKBOX_TAG)
    private val title = view.findViewWithTag<TextView>(TITLE_TAG)
    private val dragHandle = view.findViewWithTag<AppCompatImageView>(DRAG_HANDLE_TAG)

    @SuppressLint("ClickableViewAccessibility")
    fun bind(
        row: ActionSelectionRow,
        onDragRequested: ((RecyclerView.ViewHolder) -> Unit)?,
    ) {
        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = row.enabled || row.locked
        checkbox.isEnabled = !row.locked
        checkbox.setOnCheckedChangeListener { _, isChecked -> row.enabled = isChecked }
        title.text = itemView.context.getString(row.action.titleRes)
        itemView.setOnClickListener(
            if (row.locked) {
                null
            }
            else {
                View.OnClickListener { checkbox.performClick() }
            }
        )
        dragHandle.visibility = if (row.locked) View.INVISIBLE else View.VISIBLE
        dragHandle.setOnTouchListener(
            if (row.locked) {
                null
            }
            else {
                View.OnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onDragRequested?.invoke(this)
                    }
                    true
                }
            }
        )
    }
}

private class ActionSelectionTouchCallback(
    private val adapter: ActionSelectionAdapter,
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    override fun isLongPressDragEnabled() = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        return adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
}

private fun ViewGroup.createRowView(): View {
    val context = context
    return selectionDialogRow(
        listOf(
            createDragHandle(context).apply { tag = DRAG_HANDLE_TAG },
            MaterialCheckBox(context).apply {
                tag = CHECKBOX_TAG
                isClickable = false
                isFocusable = false
            },
            TextView(context).apply {
                tag = TITLE_TAG
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
    )
}

private fun createDragHandle(context: Context): AppCompatImageView {
    return AppCompatImageView(context).apply {
        contentDescription = context.getString(R.string.drag_to_reorder)
        setImageResource(R.drawable.ic_burger_menu)
        setColorFilter(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
    }
}

private fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private const val CHECKBOX_TAG = "checkbox"
private const val TITLE_TAG = "title"
private const val DRAG_HANDLE_TAG = "dragHandle"
