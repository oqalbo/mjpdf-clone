// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.graphics.Color
import android.util.Log
import android.view.Menu
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.color.MaterialColors


@SuppressLint("RestrictedApi")
fun Menu.showOptionalIcons(context: Context? = null) {
    if (this is MenuBuilder) {
        setOptionalIconsVisible(true)
    }
    context?.let(::tintIconsForChrome)
}

fun Menu.tintIconsForChrome(context: Context) {
    val color = MaterialColors.getColor(context, R.attr.colorOnSurface, 0)
    for (index in 0 until size()) {
        getItem(index).icon?.mutate()?.setTint(color)
    }
}

fun configureSearchIcon(menu: Menu, show: Boolean) {
    val searchItem = menu.findItem(R.id.search_in_search_activity)
    searchItem?.isVisible = show
}

fun toggleViewStartConstraint(dynamicView: LinearLayoutCompat, staticView: Int) {
    val constraintLayout = dynamicView.parent as? ConstraintLayout
    if (constraintLayout == null) {
        Log.e("UiUtil", "toggleViewStartConstraint: constraintLayout is null for the view!!")
        return
    }

    val constraintSet = ConstraintSet()
    constraintSet.clone(constraintLayout)

    val currentConstraint = constraintSet.getConstraint(dynamicView.id)
    val isCurrentlyAlignedToParent = currentConstraint.layout.startToStart == ConstraintSet.PARENT_ID

    if (isCurrentlyAlignedToParent) {
        constraintSet.clear(dynamicView.id, ConstraintSet.START)
        constraintSet.connect(dynamicView.id, ConstraintSet.START, staticView, ConstraintSet.END)
    } else {
        constraintSet.clear(dynamicView.id, ConstraintSet.START)
        constraintSet.connect(dynamicView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
    }

    constraintSet.applyTo(constraintLayout)
}

fun copyToClipboard(activity: Activity, label: String, text: String) {
    val clipboard: ClipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE)
            as ClipboardManager
    val clip: ClipData = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

fun Int.divideToPercent(divideTo: Int): Int {
    return if (divideTo == 0) 0
    else ((this / divideTo.toDouble()) * 100).toInt()
}

val Number.inPx get() = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this.toFloat(),
    Resources.getSystem().displayMetrics
).toInt()
