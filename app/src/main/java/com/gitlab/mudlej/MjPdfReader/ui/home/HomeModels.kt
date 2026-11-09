// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.text.TextUtils
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

enum class HomeTab { RECENT, LIBRARY, FOLDERS }

enum class HomeViewMode { GRID, LIST }

enum class HomeTitleEllipsize(@StringRes val labelRes: Int, val truncateAt: TextUtils.TruncateAt) {
    START(R.string.home_title_ellipsize_start, TextUtils.TruncateAt.START),
    MIDDLE(R.string.home_title_ellipsize_middle, TextUtils.TruncateAt.MIDDLE),
    END(R.string.home_title_ellipsize_end, TextUtils.TruncateAt.END),
}

enum class HomeProgressStyle { RING, BAR }

enum class HomeGridSize(val targetCellDp: Int) { SMALL(96), MEDIUM(120), LARGE(150) }

enum class HomeSortOrder { LAST_OPENED, NAME }

enum class ListFilter { RECENT, ALL, FAVORITE, TO_READ, READING, ON_HOLD, COMPLETED, ABANDONED }

enum class ScanMode { NOT_CONFIGURED, WHOLE_DEVICE, SELECTED_LOCATIONS }
