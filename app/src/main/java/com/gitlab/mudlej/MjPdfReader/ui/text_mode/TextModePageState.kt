// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

sealed class TextModePageState(open val pageIndex: Int) {
    data class NotLoaded(override val pageIndex: Int) : TextModePageState(pageIndex)
    data class Loading(override val pageIndex: Int) : TextModePageState(pageIndex)
    data class Ready(override val pageIndex: Int, val text: CharSequence) : TextModePageState(pageIndex)
    data class Empty(override val pageIndex: Int) : TextModePageState(pageIndex)
    data class Error(override val pageIndex: Int, val message: String) : TextModePageState(pageIndex)
    data class TooLarge(override val pageIndex: Int) : TextModePageState(pageIndex)
}
