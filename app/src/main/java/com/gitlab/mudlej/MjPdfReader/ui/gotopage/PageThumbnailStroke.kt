// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.gotopage

import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

fun applyCurrentPageStroke(card: MaterialCardView, isCurrentPage: Boolean) {
    val density = card.resources.displayMetrics.density
    if (isCurrentPage) {
        card.strokeColor = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary)
        card.strokeWidth = (2 * density).toInt()
    } else {
        card.strokeColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)
        card.strokeWidth = density.toInt().coerceAtLeast(1)
    }
}
