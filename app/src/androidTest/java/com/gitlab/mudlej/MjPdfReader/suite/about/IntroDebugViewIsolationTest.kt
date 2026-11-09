// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.intro.IntroDebug
import com.gitlab.mudlej.MjPdfReader.ui.intro.IntroPage
import com.gitlab.mudlej.MjPdfReader.ui.intro.IntroPagerAdapter
import com.gitlab.mudlej.MjPdfReader.ui.intro.IntroPagerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class IntroDebugViewIsolationTest {
    @Test
    fun pagerStateAndSnapshotsStayWithTheSuppliedInstance() = TestEnvironment.onMainThread {
        assertTrue("this test requires a debug build", BuildConfig.DEBUG)
        val context = ContextThemeWrapper(TestEnvironment.targetContext, R.style.HomeTheme)
        val first = ViewPager2(context)
        val second = ViewPager2(context)
        try {
            first.adapter = IntroPagerAdapter(
                listOf(IntroPage(R.drawable.ic_logo, R.string.intro_welcome_title, emptyList())),
            )
            second.adapter = IntroPagerAdapter(
                List(2) { IntroPage(R.drawable.ic_home, R.string.intro_library_title, emptyList()) },
            )
            second.setCurrentItem(1, false)
            for (pager in listOf(first, second)) {
                pager.measure(
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                )
                pager.layout(0, 0, 800, 1200)
            }

            val firstState = IntroPagerState(0, 1, ViewPager2.SCROLL_STATE_IDLE, true)
            assertEquals(firstState, IntroDebug.pagerState(first))
            assertEquals(IntroPagerState(1, 2, ViewPager2.SCROLL_STATE_IDLE, true), IntroDebug.pagerState(second))
            assertEquals(
                context.getString(R.string.intro_welcome_title),
                IntroDebug.pageSnapshot(first, 0)?.title?.toString(),
            )
            assertEquals(
                context.getString(R.string.intro_library_title),
                IntroDebug.pageSnapshot(second, 1)?.title?.toString(),
            )

            val empty = ViewPager2(context)
            assertEquals(IntroPagerState(0, 0, ViewPager2.SCROLL_STATE_IDLE, true), IntroDebug.pagerState(empty))
            assertNull(IntroDebug.pageSnapshot(empty, 0))
            assertNull(IntroDebug.pageSnapshot(first, -1))
            assertNull(IntroDebug.pageSnapshot(second, 2))

            second.adapter = null
            assertNull(IntroDebug.pageSnapshot(second, 1))
            assertEquals(firstState, IntroDebug.pagerState(first))
            assertEquals(
                context.getString(R.string.intro_welcome_title),
                IntroDebug.pageSnapshot(first, 0)?.title?.toString(),
            )
        } finally {
            second.adapter = null
            first.adapter = null
        }
    }
}
