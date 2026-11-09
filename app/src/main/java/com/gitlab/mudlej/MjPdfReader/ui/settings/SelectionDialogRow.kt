// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView

internal fun ViewGroup.selectionDialogRow(children: List<View>): LinearLayout {
    val rippleValue = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, rippleValue, true)
    val density = resources.displayMetrics.density
    return LinearLayout(context).apply {
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = (48 * density).toInt()
        setBackgroundResource(rippleValue.resourceId)
        setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
        children.forEach(::addView)
    }
}
