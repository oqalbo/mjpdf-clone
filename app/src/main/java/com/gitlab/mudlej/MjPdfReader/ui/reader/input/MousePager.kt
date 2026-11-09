// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding

class MousePager(
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val pref: Preferences,
) {

    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (!pref.getTurnPageByMouseButtons()) {
            return false
        }
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_BUTTON_PRESS && action != MotionEvent.ACTION_BUTTON_RELEASE) {
            return false
        }
        val delta = when (event.actionButton) {
            MotionEvent.BUTTON_FORWARD -> 1
            MotionEvent.BUTTON_BACK -> -1
            else -> return false
        }
        if (action == MotionEvent.ACTION_BUTTON_PRESS) {
            turnPage(delta)
        }
        return true
    }

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!pref.getTurnPageByMouseButtons()) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_FORWARD -> {
                if (event?.isFromSource(InputDevice.SOURCE_MOUSE) == true) {
                    turnPage(1)
                    true
                }
                else {
                    false
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                if (event?.isFromSource(InputDevice.SOURCE_MOUSE) == true) {
                    turnPage(-1)
                    true
                }
                else {
                    false
                }
            }
            else -> false
        }
    }

    private fun turnPage(delta: Int) {
        binding.pdfView.jumpTo(binding.pdfView.getPageAfterRowStep(pdf.pageNumber, delta))
    }
}
