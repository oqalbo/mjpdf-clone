// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeDebug
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModePageAdapter
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModePageState
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModePageViewHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class TextModeDebugViewIsolationTest {
    @Test
    fun unboundNewViewDoesNotReadRetainedOldPage() = TestEnvironment.onMainThread {
        assertTrue("This regression requires a debug build", BuildConfig.DEBUG)
        val context = ContextThemeWrapper(TestEnvironment.targetContext, R.style.HomeTheme)
        val oldPages = RecyclerView(context)
        val otherPages = RecyclerView(context)
        try {
            for ((pages, text) in listOf(oldPages to "retained old page", otherPages to "other page")) {
                pages.layoutManager = LinearLayoutManager(context)
                pages.itemAnimator = null
                pages.adapter = TextModePageAdapter {}.apply {
                    submitPageCount(1)
                    updatePageState(TextModePageState.Ready(0, text))
                }
                pages.measure(
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                )
                pages.layout(0, 0, 800, 1200)
                assertTrue(pages.findViewHolderForAdapterPosition(0) is TextModePageViewHolder)
            }
            assertEquals("retained old page", TextModeDebug.pageSnapshot(oldPages, 0)?.pageText?.toString())
            assertEquals("other page", TextModeDebug.pageSnapshot(otherPages, 0)?.pageText?.toString())
            assertNull(TextModeDebug.pageSnapshot(oldPages, -1))
            assertNull(TextModeDebug.pageSnapshot(otherPages, 1))

            val newPages = RecyclerView(context)
            assertNull(newPages.adapter)
            assertNull(newPages.findViewHolderForAdapterPosition(0))
            assertNull(
                "The new unbound view must not expose a retained page",
                TextModeDebug.pageSnapshot(newPages, 0),
            )

            otherPages.adapter = null
            assertNull(TextModeDebug.pageSnapshot(otherPages, 0))
            assertEquals("retained old page", TextModeDebug.pageSnapshot(oldPages, 0)?.pageText?.toString())
        } finally {
            otherPages.adapter = null
            oldPages.adapter = null
        }
    }
}
