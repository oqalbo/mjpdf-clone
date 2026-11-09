// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar

object AppSnackbar {

    fun make(view: View, @StringRes resId: Int, duration: Int): Snackbar {
        return make(view, view.resources.getText(resId), duration)
    }

    fun make(view: View, text: CharSequence, duration: Int): Snackbar {
        return Snackbar.make(view.context, view, text, duration).also { applyCardStyle(it, view) }
    }

    private fun applyCardStyle(snackbar: Snackbar, anchor: View) {
        val view = snackbar.view
        val density = view.resources.displayMetrics.density
        val backgroundColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val outlineColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutlineVariant)
        val textColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
        val actionColor = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)

        view.background = MaterialShapeDrawable(
            ShapeAppearanceModel.builder().setAllCornerSizes(16 * density).build()
        ).apply {
            fillColor = ColorStateList.valueOf(backgroundColor)
            strokeColor = ColorStateList.valueOf(outlineColor)
            strokeWidth = density
        }
        view.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        ViewCompat.setElevation(view, 6 * density)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets -> insets }
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            val margin = (16 * density).toInt()
            params.setMargins(margin, margin, margin, margin + systemBarOverlap(anchor))
            view.layoutParams = params
        }
        val extraVerticalPadding = (6 * density).toInt()
        view.setPadding(
            view.paddingLeft,
            view.paddingTop + extraVerticalPadding,
            view.paddingRight,
            view.paddingBottom + extraVerticalPadding,
        )
        snackbar.setTextColor(textColor)
        snackbar.setActionTextColor(actionColor)
    }

    private fun systemBarOverlap(anchor: View): Int {
        val parent = findSnackbarParent(anchor) ?: return 0
        if (parent.height == 0) {
            return 0
        }
        val insets = ViewCompat.getRootWindowInsets(parent) ?: return 0
        val barBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        if (barBottom == 0) {
            return 0
        }
        val location = IntArray(2)
        parent.getLocationInWindow(location)
        val gapBelowParent = (parent.rootView.height - location[1] - parent.height).coerceAtLeast(0)
        return (barBottom - gapBelowParent).coerceAtLeast(0)
    }

    private fun findSnackbarParent(view: View): View? {
        var current: View? = view
        var fallback: View? = null
        while (current != null) {
            if (current is CoordinatorLayout) {
                return current
            }
            if (current is FrameLayout) {
                if (current.id == android.R.id.content) {
                    return current
                }
                fallback = current
            }
            current = current.parent as? View
        }
        return fallback
    }
}
