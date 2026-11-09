// Written by Mudlej. License is GPLv3.
package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.graphics.Rect
import android.graphics.Point
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.uiautomator.UiObject2
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object PickerClicks {
    fun click(node: UiObject2): Boolean {
        val target = clickableTarget(node)
        val visible = touchableBounds(target) ?: return false
        target.setGestureMargin(0)
        target.click(Point(visible.centerX(), visible.centerY()))
        return true
    }

    fun touchableBounds(node: UiObject2): Rect? {
        val windows = TestEnvironment.instrumentation.uiAutomation.windows
        val ownLayer = windows.filter { it.root?.packageName?.toString() == node.applicationPackage }
            .maxOfOrNull { it.layer } ?: return null
        var regions = listOf(node.visibleBounds).filterNot { it.isEmpty }
        windows.filter {
            it.layer > ownLayer &&
                (it.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
                    it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        }.forEach { window ->
            val blocked = Rect().also(window::getBoundsInScreen)
            regions = regions.flatMap { subtract(it, blocked) }
        }
        return regions.maxByOrNull { it.width().toLong() * it.height() }
    }

    private fun clickableTarget(node: UiObject2): UiObject2 {
        var current: UiObject2? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return node
    }

    private fun subtract(bounds: Rect, blocked: Rect): List<Rect> {
        val overlap = Rect(bounds)
        if (!overlap.intersect(blocked)) return listOf(bounds)
        return listOf(
            Rect(bounds.left, bounds.top, bounds.right, overlap.top),
            Rect(bounds.left, overlap.bottom, bounds.right, bounds.bottom),
            Rect(bounds.left, overlap.top, overlap.left, overlap.bottom),
            Rect(overlap.right, overlap.top, bounds.right, overlap.bottom),
        ).filterNot { it.isEmpty }
    }
}
