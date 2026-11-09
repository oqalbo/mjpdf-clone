// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.ActionBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.color.MaterialColors


object ColorUtil {

    private const val STATUS_BAR_BACKGROUND_TAG = "mj_pdf_status_bar_background"
    private const val NAVIGATION_BAR_BACKGROUND_TAG = "mj_pdf_navigation_bar_background"

    fun colorize(context: Context, window: Window, actionBar: ActionBar?) {
        val color = getBarColor(context)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setCutoutMode(window, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT)
        window.statusBarColor = color
        window.navigationBarColor = color
        showSystemBars(window)
        drawSystemBarBackgrounds(window, color)
        setSystemBarIconColors(window, color)
        fitContentBelowSystemBars(window)

        actionBar?.setBackgroundDrawable(ColorDrawable(color))
        // Flatten the app bar so it blends into the status bar.
        actionBar?.elevation = 0f
    }

    fun enterFullscreen(window: Window) {
        setCutoutMode(window, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
        setSystemBarBackgroundsVisible(window, false)
        setContentFitsSystemBars(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun getBarColor(context: Context): Int {
        return MaterialColors.getColor(
            context,
            R.attr.colorSurfaceContainerHigh,
            0
        )
    }

    fun applySystemBarIconColors(context: Context, window: Window) {
        setSystemBarIconColors(window, getBarColor(context))
    }

    private fun setSystemBarIconColors(window: Window, color: Int) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val isLight = MaterialColors.isColorLight(color)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
    }

    private fun drawSystemBarBackgrounds(window: Window, color: Int) {
        ensureSystemBarBackground(
            window = window,
            tag = STATUS_BAR_BACKGROUND_TAG,
            color = color,
            gravity = Gravity.TOP,
            fallbackResourceName = "status_bar_height"
        ) { insets -> insets.getInsets(WindowInsetsCompat.Type.statusBars()).top }

        ensureSystemBarBackground(
            window = window,
            tag = NAVIGATION_BAR_BACKGROUND_TAG,
            color = color,
            gravity = Gravity.BOTTOM,
            fallbackResourceName = "navigation_bar_height"
        ) { insets -> insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom }
    }

    private fun ensureSystemBarBackground(
        window: Window,
        tag: String,
        color: Int,
        gravity: Int,
        fallbackResourceName: String,
        getHeight: (WindowInsetsCompat) -> Int
    ) {
        val decor = window.decorView as? ViewGroup ?: return
        val background = decor.findViewWithTag<View>(tag) ?: View(decor.context).also { view ->
            view.tag = tag
            view.visibility = View.GONE
            decor.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getSystemBarHeight(decor, fallbackResourceName),
                    gravity
                )
            )
        }

        if (decor.indexOfChild(background) != decor.childCount - 1) {
            val layoutParams = background.layoutParams
            decor.removeView(background)
            decor.addView(background, layoutParams)
        }

        background.setBackgroundColor(color)
        ViewCompat.setOnApplyWindowInsetsListener(background) { view, insets ->
            val height = getHeight(insets)
            val decorPadding = if (gravity == Gravity.TOP) decor.paddingTop else decor.paddingBottom
            setSystemBarBackgroundHeight(view, if (decorPadding > 0) 0 else height)
            insets
        }
        ViewCompat.requestApplyInsets(background)
    }

    private fun setSystemBarBackgroundHeight(view: View, height: Int) {
        view.visibility = if (height > 0) View.VISIBLE else View.GONE
        val layoutParams = view.layoutParams
        if (layoutParams.height != height) {
            layoutParams.height = height
            view.layoutParams = layoutParams
        }
    }

    private fun getSystemBarHeight(decor: ViewGroup, resourceName: String): Int {
        val resourceId = decor.resources.getIdentifier(resourceName, "dimen", "android")
        if (resourceId == 0) {
            return 0
        }
        return decor.resources.getDimensionPixelSize(resourceId)
    }

    private fun fitContentBelowSystemBars(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return
        }

        setContentFitsSystemBars(window, true)
    }

    private fun setContentFitsSystemBars(window: Window, fitsSystemWindows: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return
        }

        val content = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        root.fitsSystemWindows = fitsSystemWindows
        if (!fitsSystemWindows) {
            root.setPadding(0, 0, 0, 0)
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setCutoutMode(window: Window, mode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }
        val attributes = window.attributes
        if (attributes.layoutInDisplayCutoutMode == mode) {
            return
        }
        attributes.layoutInDisplayCutoutMode = mode
        window.attributes = attributes
    }

    private fun showSystemBars(window: Window) {
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun setSystemBarBackgroundsVisible(window: Window, visible: Boolean) {
        val decor = window.decorView as? ViewGroup ?: return
        val visibility = if (visible) View.VISIBLE else View.GONE
        decor.findViewWithTag<View>(STATUS_BAR_BACKGROUND_TAG)?.visibility = visibility
        decor.findViewWithTag<View>(NAVIGATION_BAR_BACKGROUND_TAG)?.visibility = visibility
    }

}
