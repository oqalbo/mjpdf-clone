// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import androidx.annotation.StringRes
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.gitlab.mudlej.MjPdfReader.R

enum class PageFitPolicy(@StringRes val labelRes: Int, val libraryPolicy: FitPolicy) {
    WIDTH(R.string.page_fit_width, FitPolicy.WIDTH),
    HEIGHT(R.string.page_fit_height, FitPolicy.HEIGHT),
    BOTH(R.string.page_fit_whole, FitPolicy.BOTH),
}
