// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FilenameTruncationTest {
    @Test
    fun gridTitlesFitAllLineCountsAndTruncationModes() = TestEnvironment.onMainThread {
        checkModes(R.layout.item_home_grid_cell)
    }

    @Test
    fun listTitlesFitAllLineCountsAndTruncationModes() = TestEnvironment.onMainThread {
        checkModes(R.layout.item_home_list_row)
    }

    @Test
    fun resizingRebindingAndChangingTheStyleRestoreTheCorrectText() = TestEnvironment.onMainThread {
        val view = title(R.layout.item_home_list_row)
        view.maxLines = 2
        view.ellipsize = TextUtils.TruncateAt.MIDDLE
        view.text = LONG_NAME
        measure(view, 150)
        val narrow = displayed(view)
        assertTrue(narrow.contains('…'))
        measure(view, 450)
        assertTrue("wider titles reveal more of the name", displayed(view).length > narrow.length)
        measure(view, view.paint.measureText(LONG_NAME).toInt() + 1)
        assertEquals("expanding restores the complete name", LONG_NAME, displayed(view))
        measure(view, 150)
        assertEquals("shrinking recalculates the same title", narrow, displayed(view))
        view.text = "short.pdf"
        measure(view, 150)
        assertEquals("a recycled cell must not keep the old name", "short.pdf", displayed(view))
        view.text = LONG_NAME
        view.ellipsize = TextUtils.TruncateAt.START
        measure(view, 150)
        assertTrue(displayed(view).startsWith('…'))
        view.ellipsize = TextUtils.TruncateAt.END
        measure(view, 150)
        assertFalse("native end truncation clears the old transformation", displayed(view).startsWith('…'))
        assertTrue(view.layout.getEllipsisCount(view.layout.lineCount - 1) > 0)
        view.ellipsize = TextUtils.TruncateAt.MIDDLE
        view.maxLines = 1
        measure(view, 150)
        assertEquals(1, view.layout.lineCount)
        assertTrue(view.layout.getEllipsisCount(0) > 0)
    }

    @Test
    fun unicodeTitlesKeepCompleteCharactersAndExposeTheFullAccessibleName() = TestEnvironment.onMainThread {
        val view = title(R.layout.item_home_grid_cell)
        view.maxLines = 2
        view.ellipsize = TextUtils.TruncateAt.MIDDLE
        for (name in listOf("اَلْمَلَفُ_".repeat(15) + ".pdf", "😀e\u0301_".repeat(30) + ".pdf")) {
            view.text = name
            view.layoutDirection = View.LAYOUT_DIRECTION_RTL
            measure(view, 190)
            val shown = displayed(view)
            assertTrue(shown.contains('…'))
            assertTrue(name.startsWith(shown.substringBefore('…')))
            assertTrue(name.endsWith(shown.substringAfter('…')))
            assertFalse("the prefix cannot end in half a surrogate", shown.substringBefore('…').last().isHighSurrogate())
            assertFalse("the suffix cannot start in half a surrogate", shown.substringAfter('…').first().isLowSurrogate())
            assertFalse("the suffix cannot start with a combining mark", Character.getType(shown.substringAfter('…').first()) == Character.NON_SPACING_MARK.toInt())
            assertEquals(name, view.text.toString())
            val node = view.createAccessibilityNodeInfo()
            assertEquals("accessibility receives the full filename", name, node.text.toString())
            node.recycle()
            assertFits(view, 190)
        }
    }

    private fun checkModes(layoutId: Int) {
        val view = title(layoutId)
        for (lines in 1..4) {
            for (mode in listOf(TextUtils.TruncateAt.START, TextUtils.TruncateAt.MIDDLE, TextUtils.TruncateAt.END)) {
                view.maxLines = lines
                view.ellipsize = mode
                view.text = LONG_NAME
                measure(view, 170)
                assertEquals(LONG_NAME, view.text.toString())
                assertFits(view, 170)
                val shown = displayed(view).replace("\uFEFF", "")
                val ellipsis = shown.indexOf('…')
                assertTrue("$lines lines, $mode must truncate: $shown", ellipsis >= 0)
                when (mode) {
                    TextUtils.TruncateAt.START -> assertEquals(0, ellipsis)
                    TextUtils.TruncateAt.MIDDLE -> {
                        assertTrue(ellipsis > 0 && ellipsis < shown.lastIndex)
                        assertTrue(LONG_NAME.startsWith(shown.substringBefore('…')))
                        assertTrue(LONG_NAME.endsWith(shown.substringAfter('…')))
                    }
                    else -> assertTrue("end truncation uses the native layout", view.layout.getEllipsisCount(view.layout.lineCount - 1) > 0)
                }
            }
        }
    }

    private fun title(layoutId: Int): TextView {
        val context = ContextThemeWrapper(TestEnvironment.targetContext, R.style.HomeTheme)
        return LayoutInflater.from(context).inflate(layoutId, null).findViewById(R.id.title)
    }

    private fun measure(view: TextView, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun displayed(view: TextView) = view.layout.text.toString()

    private fun assertFits(view: TextView, width: Int) {
        assertTrue("title must fit its line count", view.layout.lineCount <= view.maxLines)
        assertTrue("title must fit its height", view.layout.getLineBottom(view.layout.lineCount - 1) <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        for (line in 0 until view.layout.lineCount) {
            val lineWidth = if (view.layout.getEllipsisCount(line) > 0) {
                val visible = view.layout.text.subSequence(view.layout.getLineStart(line), view.layout.getLineEnd(line))
                    .toString().replace("\uFEFF", "").trimEnd()
                view.paint.measureText(visible)
            } else {
                view.layout.getLineWidth(line)
            }
            assertTrue("${view.maxLines} lines, ${view.ellipsize}: line $line width $lineWidth must fit $width", lineWidth <= width + 1)
        }
    }

    private companion object {
        val LONG_NAME = "a-document-with-a-very-long-name-".repeat(12) + "final-edition.pdf"
    }
}
