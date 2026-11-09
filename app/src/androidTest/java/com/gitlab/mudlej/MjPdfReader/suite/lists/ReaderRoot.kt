// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import android.graphics.PointF
import android.graphics.RectF
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.RootDescription
import com.gitlab.mudlej.MjPdfReader.suite.state.RootDescriptionParser
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object ReaderRoot {

    const val HISTORY_GROUP = "hist"
    const val BACK_KEY = "back"
    const val FORWARD_KEY = "forward"

    fun description(): RootDescription? = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) ?: return@onMainThread null
        val root = activity.findViewById<View>(R.id.mainLayout)
            ?: throw ConditionTimeoutException("The reader has no root view carrying the debug description")
        root.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let(RootDescriptionParser::parse)
    }

    fun backStack(): List<Int> = pageList(BACK_KEY)

    fun forwardStack(): List<Int> = pageList(FORWARD_KEY)

    private fun pageList(key: String): List<Int> {
        val raw = description()?.value(HISTORY_GROUP, key) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }.map { it.trim().toInt() }
    }
}

object PageProjection {

    fun viewPointOf(state: ReaderState, pdfX: Float, pdfY: Float): PointF {
        val page = state.pageRect
            ?: throw ConditionTimeoutException("The reader state hook reports no page rectangle: $state")
        return PointF(
            page.left + (pdfX / ListsFixtures.PAGE_WIDTH_POINTS) * page.width(),
            page.top + ((ListsFixtures.PAGE_HEIGHT_POINTS - pdfY) / ListsFixtures.PAGE_HEIGHT_POINTS) * page.height(),
        )
    }

    fun isOnScreen(state: ReaderState, point: PointF): Boolean {
        val width = state.viewWidth ?: return false
        val height = state.viewHeight ?: return false
        return point.x >= 0f && point.x <= width && point.y >= 0f && point.y <= height
    }

    fun describe(state: ReaderState, rect: RectF): String {
        val topLeft = viewPointOf(state, rect.left, rect.top)
        val bottomRight = viewPointOf(state, rect.right, rect.bottom)
        return "the passage maps to view rectangle (${topLeft.x}, ${topLeft.y}) to " +
            "(${bottomRight.x}, ${bottomRight.y}) inside a ${state.viewWidth} by ${state.viewHeight} view, " +
            "with page rectangle ${state.pageRect} at zoom ${state.zoom}"
    }
}
