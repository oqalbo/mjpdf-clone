// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.input

import android.view.KeyEvent
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding

class VolumeKeyPager(
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val pref: Preferences,
) {

    fun handleKeyDown(keyCode: Int): Boolean {
        if (!pref.getTurnPageByVolumeButtons()) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                binding.pdfView.jumpTo(binding.pdfView.getPageAfterRowStep(pdf.pageNumber, 1))
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                binding.pdfView.jumpTo(binding.pdfView.getPageAfterRowStep(pdf.pageNumber, -1))
                true
            }
            else -> false
        }
    }
}
