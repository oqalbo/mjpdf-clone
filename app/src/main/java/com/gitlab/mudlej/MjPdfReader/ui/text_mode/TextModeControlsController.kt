// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityTextModeBinding

class TextModeControlsController(
    private val binding: ActivityTextModeBinding,
    private val hideDelayMillis: Long,
) {

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    fun attachTapListener() {
        val tapDetector = GestureDetector(
            binding.root.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    showTemporarilyOrHide()
                    return true
                }
            },
        )
        binding.textPagesRecyclerView.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                    tapDetector.onTouchEvent(event)
                    return false
                }
            },
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setControlsTouchListeners(onSliderGestureCancelled: () -> Unit) {
        val listener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> keepVisible()
                MotionEvent.ACTION_UP -> scheduleHide()
                MotionEvent.ACTION_CANCEL -> {
                    if (view === binding.pageSlider) {
                        onSliderGestureCancelled()
                    }
                    scheduleHide()
                }
            }
            false
        }
        listOf(
            binding.readerControlsCard,
            binding.pageSlider,
            binding.previousPageButton,
            binding.pageButton,
            binding.nextPageButton,
            binding.tableOfContentsButton,
            binding.typographyButton,
            binding.backToPdfButton,
        ).forEach { it.setOnTouchListener(listener) }
    }

    fun showTemporarilyOrHide() {
        if (binding.readerControlsCard.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showTemporarily()
        }
    }

    fun showTemporarily() {
        binding.readerControlsCard.visibility = View.VISIBLE
        scheduleHide()
    }

    fun release() {
        hideHandler.removeCallbacksAndMessages(null)
    }

    private fun hideControls() {
        hideHandler.removeCallbacks(hideRunnable)
        binding.readerControlsCard.visibility = View.GONE
    }

    private fun keepVisible() {
        hideHandler.removeCallbacks(hideRunnable)
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, hideDelayMillis)
    }
}
