// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.resolveReadingLayout
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

class AutoScrollManager(
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val preferences: Preferences,
    private val onSpeedChanged: (Int) -> Unit,
) {

    private companion object {
        const val SPEED_UPDATE_DELAY = 100L
        const val PAGES_LOAD_INTERVAL_MS = 300L
    }

    private val speedUpdateHandler = Handler(Looper.getMainLooper())
    private var speedUpdateRunnable: Runnable? = null
    private val state = AutoScrollState(System::nanoTime)
    private var lastPagesLoadMs = 0L
    private var frameScheduled = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            frameScheduled = false
            onFrame()
        }
    }

    fun setup() {
        setSpeed(vm.doc.autoScrollSpeed ?: preferences.getScrollSpeed())

        binding.autoScrollButton.setOnClickListener { toggleControls() }
        binding.incScrollSpeedButton.setOnClickListener { increaseSpeed() }
        binding.decScrollSpeedButton.setOnClickListener { decreaseSpeed() }
        binding.incScrollSpeedButton.setOnLongClickListener { startRepeatingSpeedChange(isIncreasing = true) }
        binding.decScrollSpeedButton.setOnLongClickListener { startRepeatingSpeedChange(isIncreasing = false) }
        binding.reverseScrollDirectionButton.setOnClickListener { state.reverse() }
        binding.toggleAutoScrollButton.setOnClickListener { toggleAutoScroll() }
    }

    fun setSpeed(speed: Int) {
        setScrollBy(-Preferences.AUTO_SCROLL_UNIT * speed.coerceAtLeast(1), notify = false)
    }

    fun stop() {
        binding.toggleAutoScrollButton.setIconResource(R.drawable.ic_play_arrow)
        cancelFrame()
        state.stop()
        val wasScrolling = vm.isAutoScrolling
        vm.isAutoScrolling = false
        if (wasScrolling) {
            binding.pdfView.loadPages()
        }
    }

    fun hideControls() {
        binding.autoScrollLayout.visibility = View.GONE
        binding.autoScrollSpeedText.visibility = View.GONE
        binding.autoScrollButton.isChecked = false
        vm.isAutoScrollClicked = false
    }

    fun handleUserInteraction(motionEvent: MotionEvent) {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> pauseForInteraction(motionEvent)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> resumeAfterInteraction(motionEvent)
        }
    }

    private fun toggleControls() {
        binding.autoScrollButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (binding.autoScrollLayout.visibility == View.VISIBLE) {
            hideControls()
        }
        else {
            showControls()
        }
    }

    private fun showControls() {
        binding.autoScrollLayout.visibility = View.VISIBLE
        binding.autoScrollSpeedText.visibility = View.VISIBLE
        binding.autoScrollButton.isChecked = true
        vm.isAutoScrollClicked = true
    }

    private fun increaseSpeed() {
        setScrollBy(state.changedSpeed(isIncreasing = true))
    }

    private fun decreaseSpeed() {
        if (state.scrollBy.absoluteValue > Preferences.AUTO_SCROLL_UNIT) {
            setScrollBy(state.changedSpeed(isIncreasing = false))
        }
    }

    private fun startRepeatingSpeedChange(isIncreasing: Boolean): Boolean {
        speedUpdateRunnable = Runnable {
            if (!binding.incScrollSpeedButton.isPressed && !binding.decScrollSpeedButton.isPressed) {
                return@Runnable
            }

            setScrollBy(state.changedSpeed(isIncreasing))
            speedUpdateRunnable?.let { speedUpdateHandler.postDelayed(it, SPEED_UPDATE_DELAY) }
        }
        speedUpdateRunnable?.let { speedUpdateHandler.postDelayed(it, SPEED_UPDATE_DELAY) }
        return true
    }

    private fun toggleAutoScroll() {
        vm.isAutoScrolling = !vm.isAutoScrolling

        if (!vm.isAutoScrolling) {
            stop()
            return
        }

        binding.toggleAutoScrollButton.setIconResource(R.drawable.ic_pause)
        start()
    }

    private fun start() {
        cancelFrame()
        state.resetFrame()
        lastPagesLoadMs = 0L
        scheduleFrame()
    }

    private fun scheduleFrame() {
        if (frameScheduled) {
            return
        }
        frameScheduled = true
        binding.pdfView.postOnAnimation(frameRunnable)
    }

    private fun cancelFrame() {
        binding.pdfView.removeCallbacks(frameRunnable)
        frameScheduled = false
        state.resetFrame()
    }

    private fun onFrame() {
        if (!vm.isAutoScrolling || state.pausedByInteraction) {
            return
        }
        if (!state.shouldContinue(binding.pdfView.positionOffset)) {
            stop()
            return
        }

        state.frameDistance { distance ->
            if (resolveReadingLayout(preferences).swipeHorizontal) {
                binding.pdfView.moveRelativeTo(distance, 0F)
            }
            else {
                binding.pdfView.moveRelativeTo(0F, distance)
            }
            loadPagesThrottled()
        }

        if (vm.isAutoScrolling && state.shouldContinue(binding.pdfView.positionOffset)) {
            scheduleFrame()
        }
        else if (vm.isAutoScrolling) {
            stop()
        }
    }

    private fun loadPagesThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastPagesLoadMs < PAGES_LOAD_INTERVAL_MS) {
            return
        }
        lastPagesLoadMs = now
        binding.pdfView.loadPages()
    }

    private fun pauseForInteraction(motionEvent: MotionEvent) {
        if (state.pointerDown(motionEvent.pointerCount, vm.isAutoScrolling)) {
            cancelFrame()
        }
    }

    private fun resumeAfterInteraction(motionEvent: MotionEvent) {
        val remaining = when (motionEvent.actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> (motionEvent.pointerCount - 1).coerceAtLeast(0)
            else -> 0
        }
        if (state.pointerUp(remaining, vm.isAutoScrolling)) {
            start()
        }
    }

    private fun setScrollBy(newScrollBy: Double, notify: Boolean = true) {
        state.setScrollBy(newScrollBy)
        val speed = state.speed
        binding.autoScrollSpeedText.text = speed.toString()
        if (notify) {
            vm.doc.autoScrollSpeed = speed
            onSpeedChanged(speed)
        }
    }
}

internal class AutoScrollState(private val clock: () -> Long) {
    var scrollBy = 0.0
        private set
    private var interactionPointerCount = 0
    var pausedByInteraction = false
        private set
    private var lastFrameTimeNanos = 0L

    val speed: Int
        get() = (scrollBy.absoluteValue * (1 / Preferences.AUTO_SCROLL_UNIT)).roundToInt().coerceAtLeast(1)

    fun setScrollBy(newScrollBy: Double) {
        val direction = if (newScrollBy > 0) 1.0 else -1.0
        scrollBy = direction * newScrollBy.absoluteValue.coerceAtLeast(Preferences.AUTO_SCROLL_UNIT)
    }

    fun reverse() {
        scrollBy = -scrollBy
    }

    fun changedSpeed(isIncreasing: Boolean): Double {
        val interval = Preferences.AUTO_SCROLL_UNIT
        return if (isIncreasing) {
            (scrollBy.absoluteValue + interval) * scrollBy.sign
        } else if (scrollBy.absoluteValue > interval) {
            (scrollBy.absoluteValue - interval) * scrollBy.sign
        } else {
            scrollBy
        }
    }

    fun shouldContinue(positionOffset: Float): Boolean =
        if (scrollBy < 0) positionOffset < 1F else positionOffset > 0F

    fun pointerDown(pointerCount: Int, scrolling: Boolean): Boolean {
        interactionPointerCount = pointerCount
        if (!scrolling) return false
        pausedByInteraction = true
        return true
    }

    fun pointerUp(remaining: Int, scrolling: Boolean): Boolean {
        interactionPointerCount = remaining
        if (interactionPointerCount > 0 || !pausedByInteraction || !scrolling) return false
        pausedByInteraction = false
        return true
    }

    fun stop() {
        interactionPointerCount = 0
        pausedByInteraction = false
    }

    fun resetFrame() {
        lastFrameTimeNanos = 0L
    }

    fun frameDistance(onDistance: (Float) -> Unit = {}): Float? {
        val now = clock()
        val distance = if (lastFrameTimeNanos != 0L) {
            val seconds = ((now - lastFrameTimeNanos) / 1_000_000_000.0).coerceAtMost(0.1)
            (scrollBy * 50 * seconds).toFloat()
        } else null
        if (distance != null) onDistance(distance)
        lastFrameTimeNanos = now
        return distance
    }
}
