// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.gotopage

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageView
import com.gitlab.mudlej.MjPdfReader.data.Preferences

private val invertedPageColorFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

fun applyPdfThemeToThumbnail(image: ImageView, pdfDarkTheme: Boolean) {
    if (pdfDarkTheme) {
        image.colorFilter = invertedPageColorFilter
        image.setBackgroundColor(Preferences.pdfLightBackgroundColor)
    }
}
