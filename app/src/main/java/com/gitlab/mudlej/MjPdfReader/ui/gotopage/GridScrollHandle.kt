// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.gotopage

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

@SuppressLint("ClickableViewAccessibility")
class GridScrollHandle(
    private val grid: RecyclerView,
    private val thumb: View,
) {

    private var dragging = false
    private var downRawY = 0f
    private var downTranslationY = 0f

    private val hideRunnable = Runnable {
        thumb.animate().alpha(0f).setDuration(FADE_MILLIS).start()
    }

    init {
        thumb.alpha = 0f
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dragging) {
                    return
                }
                syncThumbPosition()
                if (dy != 0 && isScrollable()) {
                    showThumb()
                    scheduleHide()
                }
            }
        })
        grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncThumbPosition() }
        thumb.setOnTouchListener { _, event -> onThumbTouch(event) }
    }

    private fun onThumbTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isScrollable()) {
                    return false
                }
                dragging = true
                downRawY = event.rawY
                downTranslationY = thumb.translationY
                thumb.isPressed = true
                showThumb()
                grid.removeCallbacks(hideRunnable)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    return false
                }
                val trackHeight = trackHeight()
                if (trackHeight <= 0) {
                    return true
                }
                val newTranslationY = (downTranslationY + event.rawY - downRawY)
                    .coerceIn(0f, trackHeight.toFloat())
                thumb.translationY = newTranslationY
                scrollGridToFraction(newTranslationY / trackHeight)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                thumb.isPressed = false
                scheduleHide()
            }
            else -> return false
        }
        return true
    }

    private fun scrollGridToFraction(fraction: Float) {
        val range = grid.computeVerticalScrollRange()
        val extent = grid.computeVerticalScrollExtent()
        val offset = grid.computeVerticalScrollOffset()
        val maxOffset = range - extent
        if (maxOffset <= 0) {
            return
        }
        val targetOffset = (fraction * maxOffset).toInt()
        grid.scrollBy(0, targetOffset - offset)
    }

    private fun syncThumbPosition() {
        val range = grid.computeVerticalScrollRange()
        val extent = grid.computeVerticalScrollExtent()
        val maxOffset = range - extent
        if (maxOffset <= 0) {
            thumb.translationY = 0f
            return
        }
        val fraction = grid.computeVerticalScrollOffset().toFloat() / maxOffset
        thumb.translationY = fraction.coerceIn(0f, 1f) * trackHeight()
    }

    private fun trackHeight() = (grid.height - thumb.height).coerceAtLeast(0)

    private fun isScrollable(): Boolean {
        return grid.computeVerticalScrollRange() > grid.computeVerticalScrollExtent()
    }

    private fun showThumb() {
        thumb.animate().cancel()
        thumb.alpha = 1f
    }

    private fun scheduleHide() {
        grid.removeCallbacks(hideRunnable)
        grid.postDelayed(hideRunnable, HIDE_DELAY_MILLIS)
    }

    companion object {
        private const val FADE_MILLIS = 250L
        private const val HIDE_DELAY_MILLIS = 1400L
    }
}
