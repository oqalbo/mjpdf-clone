// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import android.graphics.Rect
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

data class HighlightBounds(val raw: String, val page: Int, val count: Int, val rects: List<Rect>) {

    val widest: Rect? get() = rects.maxByOrNull { it.width() }
}

object SearchHighlight {

    fun readRaw(): String = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: throw ConditionTimeoutException("No reader is resumed to read the search highlight bounds from")
        val view = activity.findViewById<PDFView>(R.id.pdfView)
            ?: throw ConditionTimeoutException("The reader carries no pdfView")
        view.debugSearchHighlightBounds()
    }

    fun read(): HighlightBounds = parse(readRaw())

    fun parse(raw: String): HighlightBounds {
        if (raw.isBlank()) {
            throw ConditionTimeoutException(
                "The search highlight bounds hook returned nothing, which means the viewer debug flag is off. " +
                    "MainActivity installs it, so a blank reading means the reader never started."
            )
        }
        val values = raw.split(PAIR_SEPARATOR)
            .filter { it.isNotBlank() }
            .associate { pair ->
                val index = pair.indexOf(KEY_VALUE_SEPARATOR)
                if (index < 0) {
                    throw ConditionTimeoutException("The pair \"$pair\" in \"$raw\" carries no separator")
                }
                pair.substring(0, index) to pair.substring(index + 1)
            }
        val page = values[KEY_PAGE]?.toIntOrNull()
            ?: throw ConditionTimeoutException("\"$raw\" carries no readable $KEY_PAGE")
        val count = values[KEY_COUNT]?.toIntOrNull()
            ?: throw ConditionTimeoutException("\"$raw\" carries no readable $KEY_COUNT")
        val rects = values[KEY_RECTS].orEmpty()
            .split(RECT_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { rect ->
                val parts = rect.split(NUMBER_SEPARATOR).mapNotNull { it.trim().toIntOrNull() }
                if (parts.size != RECT_VALUES) {
                    throw ConditionTimeoutException("The rectangle \"$rect\" in \"$raw\" is not four integers")
                }
                Rect(parts[0], parts[1], parts[2], parts[3])
            }
        return HighlightBounds(raw, page, count, rects)
    }

    fun awaitRectsOnPage(pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): HighlightBounds =
        Await.untilValue(
            "the search highlight bounds hook reports rectangles on page index $pageIndex",
            timeoutMs,
            read = { runCatching { read() }.getOrNull() },
        ) { it != null && it.page == pageIndex && it.rects.isNotEmpty() }!!

    private const val PAIR_SEPARATOR = ";"
    private const val KEY_VALUE_SEPARATOR = '='
    private const val RECT_SEPARATOR = "|"
    private const val NUMBER_SEPARATOR = ","
    private const val KEY_PAGE = "page"
    private const val KEY_COUNT = "count"
    private const val KEY_RECTS = "rects"
    private const val RECT_VALUES = 4
}
