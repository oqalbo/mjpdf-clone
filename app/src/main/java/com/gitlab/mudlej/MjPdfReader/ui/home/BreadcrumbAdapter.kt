// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeBreadcrumbBinding
import com.google.android.material.color.MaterialColors

class BreadcrumbAdapter(
    private val onCrumbClicked: (String?) -> Unit,
) : RecyclerView.Adapter<BreadcrumbAdapter.BreadcrumbViewHolder>() {

    private var crumbs: List<Crumb> = emptyList()

    fun submit(newCrumbs: List<Crumb>) {
        crumbs = newCrumbs
        notifyDataSetChanged()
    }

    override fun getItemCount() = if (crumbs.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreadcrumbViewHolder {
        val binding = ItemHomeBreadcrumbBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BreadcrumbViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BreadcrumbViewHolder, position: Int) {
        holder.bind(crumbs)
    }

    inner class BreadcrumbViewHolder(
        private val binding: ItemHomeBreadcrumbBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(crumbs: List<Crumb>) {
            val container = binding.breadcrumbContainer
            container.removeAllViews()
            val context = container.context
            val onSurface = MaterialColors.getColor(
                container, com.google.android.material.R.attr.colorOnSurface
            )
            val onSurfaceVariant = MaterialColors.getColor(
                container, com.google.android.material.R.attr.colorOnSurfaceVariant
            )

            crumbs.forEachIndexed { index, crumb ->
                val isLast = index == crumbs.lastIndex

                if (index > 0) {
                    container.addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_chevron_right)
                        imageTintList = ColorStateList.valueOf(onSurfaceVariant)
                        layoutParams = LinearLayout.LayoutParams(
                            dp(context, 18),
                            dp(context, 18),
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            marginStart = dp(context, 2)
                            marginEnd = dp(context, 2)
                        }
                    })
                }

                container.addView(TextView(context).apply {
                    text = crumb.label
                    maxLines = 1
                    setTextColor(if (isLast) onSurface else onSurfaceVariant)
                    setTextAppearance(R.style.TextAppearance_Material3_BodyLarge)
                    if (isLast) {
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    } else {
                        val pad = dp(context, 4)
                        setPadding(pad, pad, pad, pad)
                        val outValue = TypedValue()
                        context.theme.resolveAttribute(
                            android.R.attr.selectableItemBackgroundBorderless, outValue, true
                        )
                        setBackgroundResource(outValue.resourceId)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onCrumbClicked(crumb.path) }
                    }
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                })
            }
        }

        private fun dp(context: android.content.Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
        }
    }
}
