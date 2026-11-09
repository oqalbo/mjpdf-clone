// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding

class ZoomSwipeLockController(
    private val binding: ActivityMainBinding,
    private val drawableOf: (Int) -> Drawable?,
) {

    fun toggleZoomLock() {
        binding.toggleZoomLockButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (binding.pdfView.isZoomDisabled) {
            enableZooming()
        } else {
            disableZooming()
        }
    }

    fun toggleHorizontalSwipeLock() {
        binding.toggleHorizontalSwipeButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (binding.pdfView.isHorizontalSwipeDisabled) {
            enableHorizontalSwiping()
        } else {
            disableHorizontalSwiping()
        }
    }

    fun enableZooming() {
        binding.toggleZoomLockButton.icon = drawableOf(R.drawable.ic_zoom_out)
        binding.toggleZoomLockButton.isChecked = false
        binding.pdfView.isZoomDisabled = false
    }

    fun disableZooming() {
        binding.toggleZoomLockButton.icon = drawableOf(R.drawable.ic_lock)
        binding.toggleZoomLockButton.isChecked = true
        binding.pdfView.isZoomDisabled = true
    }

    fun enableHorizontalSwiping() {
        binding.toggleHorizontalSwipeButton.icon = drawableOf(R.drawable.ic_allow_horizontal_swipe)
        binding.toggleHorizontalSwipeButton.isChecked = false
        binding.pdfView.isHorizontalSwipeDisabled = false
    }

    fun disableHorizontalSwiping() {
        binding.toggleHorizontalSwipeButton.icon = drawableOf(R.drawable.ic_horizontal_swipe_locked)
        binding.toggleHorizontalSwipeButton.isChecked = true
        binding.pdfView.isHorizontalSwipeDisabled = true
    }
}
