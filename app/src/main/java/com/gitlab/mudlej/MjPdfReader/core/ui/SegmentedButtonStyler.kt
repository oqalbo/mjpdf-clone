// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors

object SegmentedButtonStyler {

    fun attach(group: MaterialButtonToggleGroup) {
        style(group)
        group.addOnButtonCheckedListener { _, _, _ -> style(group) }
    }

    fun style(group: MaterialButtonToggleGroup) {
        val checkedId = group.checkedButtonId
        val selectedBackground =
            MaterialColors.getColor(group, com.google.android.material.R.attr.colorSecondary)
        val selectedText =
            MaterialColors.getColor(group, com.google.android.material.R.attr.colorOnSecondary)
        val unselectedText =
            MaterialColors.getColor(group, com.google.android.material.R.attr.colorOnSurface)
        for (index in 0 until group.childCount) {
            val button = group.getChildAt(index) as? MaterialButton ?: continue
            val selected = button.id == checkedId
            button.backgroundTintList =
                ColorStateList.valueOf(if (selected) selectedBackground else Color.TRANSPARENT)
            button.setTextColor(if (selected) selectedText else unselectedText)
            button.setTypeface(button.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
