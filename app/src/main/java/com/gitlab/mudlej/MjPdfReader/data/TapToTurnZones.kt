// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

enum class TapToTurnZones(@StringRes val labelRes: Int) {
    LEFT_RIGHT(R.string.tap_turn_left_right),
    TOP_BOTTOM(R.string.tap_turn_top_bottom),
    OFF(R.string.tap_turn_off),
}
