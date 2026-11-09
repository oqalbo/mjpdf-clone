// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.ui.reader.showHowToExitFullscreenDialog
import com.gitlab.mudlej.MjPdfReader.core.ui.ColorUtil

class FullscreenController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val pref: Preferences,
    private val fullScreenOptionsManager: FullScreenOptionsManager,
    private val autoScrollManager: AutoScrollManager,
    private val zoomSwipeLockController: ZoomSwipeLockController,
    private val brightnessController: BrightnessController,
    private val updateShortcutBarVisibility: () -> Unit,
) {

    init {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fullScreenButtonsLayout) { _, insets ->
            applyOverlayColumnInsets()
            insets
        }
    }

    fun toggleFullscreen() {
        if (!vm.isFullScreenToggled) {
            hideSystemUi()
            vm.isFullScreenToggled = true
            fullScreenOptionsManager.hideAll()

            if (pref.getShowExitFullscreenTip()) {
                showHowToExitFullscreenDialog(activity, pref)
            }
        }
        else {
            vm.isFullScreenToggled = false
            showSystemUi()
            fullScreenOptionsManager.showAllTemporarilyOrHide()
        }
    }

    fun exitFullscreen() {
        toggleFullscreen()
        autoScrollManager.stop()
        zoomSwipeLockController.enableZooming()
        brightnessController.hideControl()
        autoScrollManager.hideControls()
        zoomSwipeLockController.enableHorizontalSwiping()
    }

    fun reapplyStateAfterLoad() {
        if (vm.isFullScreenToggled) {
            hideSystemUi()
        }
    }

    fun checkAutoFullScreen() {
        if (pref.getAutoFullScreen() && !vm.isFullScreenToggled) {
            toggleFullscreen()
        }
    }

    fun restoreFullScreenIfNeeded() {
        if (vm.isFullScreenToggled) {
            vm.isFullScreenToggled = false
            toggleFullscreen()
        }
    }

    fun refreshOnWindowFocus(hasFocus: Boolean) {
        if (hasFocus && vm.isFullScreenToggled) {
            ColorUtil.enterFullscreen(activity.window)
        }
    }

    private fun showSystemUi() {
        ColorUtil.colorize(activity, activity.window, activity.supportActionBar)
        activity.supportActionBar?.show()
        binding.appBarBottomShadow.visibility = View.VISIBLE
        if (pref.getSecondBarEnabled()) {
            updateShortcutBarVisibility()
        }
        binding.pdfView.scrollHandle?.setTopReachLimit(0)
        applyOverlayColumnInsets()
    }

    private fun hideSystemUi() {
        activity.supportActionBar?.hide()
        binding.appBarBottomShadow.visibility = View.GONE
        binding.secondBarScrollView.visibility = View.GONE
        ColorUtil.enterFullscreen(activity.window)
        binding.pdfView.scrollHandle?.setTopReachLimit(statusBarInset())
        applyOverlayColumnInsets()
    }

    private fun statusBarInset(): Int {
        val insets = ViewCompat.getRootWindowInsets(binding.root) ?: return 0
        return insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
    }

    private fun overlayCutoutInsets(): Insets {
        val insets = ViewCompat.getRootWindowInsets(binding.root) ?: return Insets.NONE
        return insets.getInsets(WindowInsetsCompat.Type.displayCutout())
    }

    private fun applyOverlayColumnInsets() {
        val base = activity.resources.getDimensionPixelSize(R.dimen.fs_panel_margin)
        val topBase = activity.resources.getDimensionPixelSize(R.dimen.fs_panel_margin_top)
        val cutout = if (vm.isFullScreenToggled) overlayCutoutInsets() else Insets.NONE
        val isRtl = binding.root.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val startInset = if (isRtl) cutout.right else cutout.left
        val topInset = if (topCutoutOverlapsPanel(cutout.top, base + startInset)) cutout.top + base else 0
        val params = binding.fullScreenButtonsLayout.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.marginStart == base + startInset && params.topMargin == maxOf(topBase, topInset)) {
            return
        }
        params.marginStart = base + startInset
        params.topMargin = maxOf(topBase, topInset)
        binding.fullScreenButtonsLayout.requestLayout()
    }

    private fun topCutoutOverlapsPanel(cutoutTop: Int, panelStart: Int): Boolean {
        if (cutoutTop <= 0) {
            return false
        }
        val insets = ViewCompat.getRootWindowInsets(binding.root) ?: return true
        val rects = insets.displayCutout?.boundingRects ?: return true
        val panelWidth = panelWidth()
        if (panelWidth <= 0) {
            return true
        }
        val isRtl = binding.root.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rootWidth = binding.root.width
        if (isRtl && rootWidth <= 0) {
            return true
        }
        val left = if (isRtl) rootWidth - panelStart - panelWidth else panelStart
        val right = left + panelWidth
        return rects.any { it.top < cutoutTop && it.right > left && it.left < right }
    }

    private fun panelWidth(): Int {
        val panel = binding.fullScreenButtonsLayout
        if (panel.width > 0) {
            return panel.width
        }
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return panel.measuredWidth
    }
}
