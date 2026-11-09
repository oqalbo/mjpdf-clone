// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

enum class SharedCopyMode(@StringRes val labelRes: Int, @StringRes val summaryRes: Int) {
    ALWAYS_COPY(R.string.shared_copy_mode_always, R.string.shared_copy_mode_always_summary),
    ASK(R.string.shared_copy_mode_ask, R.string.shared_copy_mode_ask_summary),
}
