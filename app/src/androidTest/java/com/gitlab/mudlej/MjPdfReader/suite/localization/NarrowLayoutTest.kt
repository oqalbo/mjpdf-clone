// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.settings.SettingsScreen
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTitleEllipsize
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeViewMode
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class NarrowLayoutTest : LocaleTestCase() {

    @Test
    @TestId("LOC-46")
    fun longGermanSettingsSummariesDoNotClipOnTheNarrowestScreen() {
        useLocale(GERMAN)
        displaySize.set(NARROW_WIDTH_PX, NARROW_HEIGHT_PX, NARROW_DENSITY_DPI)

        ActivityScenario.launch<SettingsActivity>(SettingsScreen.pageIntent(SettingsPage.READING)).use {
            Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
            LocaleViews.awaitPreferenceSummaries(1)

            val seen = mutableSetOf<String>()
            repeat(SCROLL_STEPS) { step ->
                LocaleViews.preferenceSummaries().forEach { view ->
                    val metrics = LocaleViews.metricsOf(view)
                    seen.add(metrics.text)
                    assertFalse(
                        "a German summary must fit the height it was given rather than being cut off. " +
                            "\"${metrics.text}\" laid out ${metrics.lineCount} lines reaching " +
                            "${metrics.lineBottom}px inside a ${metrics.viewHeight}px tall view at " +
                            "${NARROW_WIDTH_PX}x$NARROW_HEIGHT_PX",
                        metrics.isVerticallyClipped,
                    )
                    assertFalse(
                        "a German summary must not be shortened with an ellipsis. \"${metrics.text}\" " +
                            "reported ${metrics.ellipsisCount} ellipsized characters on line " +
                            "${metrics.ellipsisLine}",
                        metrics.isEllipsized,
                    )
                }
                if (step < SCROLL_STEPS - 1) scrollDown()
            }

            PLAIN_GERMAN_SUMMARIES.forEach { resId ->
                val german = translated(resId)
                assertTrue(
                    "the summary ${LocalizedStrings.nameOf(resId)} must reach the screen in full German. " +
                        "Expected to find \"$german\" among $seen.",
                    seen.any { it.contains(german) },
                )
            }
        }
    }

    @Test
    @TestId("LOC-47")
    @TestDuration(DurationCategory.SHORT)
    fun longTranslatedTitlesHonourTheTruncationSettingsUnderRussian() {
        useLocale(RUSSIAN)
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.homeViewModeKey to HomeViewMode.GRID.name,
                Preferences.homeGridTitleLinesKey to GRID_TITLE_LINES,
                Preferences.homeTitleEllipsizeKey to HomeTitleEllipsize.MIDDLE.name,
            )
        )
        LocaleRecords.seed(
            LocaleFixtures.LONG_TITLE,
            LocaleRecords.DEFAULT_PAGES,
        )

        launchHome().use {
            assertTrue(
                "the fixture name must be long enough to overflow a grid cell, it is " +
                    "${LocaleFixtures.LONG_TITLE.length} characters",
                LocaleFixtures.LONG_TITLE.length >= LocaleFixtures.LONG_TITLE_NAME_CHARACTERS,
            )

            LocaleHome.awaitLibrary(home, SINGLE_RECORD)
            val cell = home.library().itemViewOf(0)
            val title = TestEnvironment.onMainThread {
                cell.findViewById<TextView>(R.id.title)
                    ?: throw ConditionTimeoutException(
                        "The grid cell carries no title text view, so the truncation settings cannot be read"
                    )
            }
            assertEquals(
                "the measured view must be the grid cell title and not the continue reading card, whose " +
                    "own layout caps at three lines",
                LocaleFixtures.LONG_TITLE,
                TestEnvironment.onMainThread { title.text.toString() },
            )
            val metrics = Await.untilValue(
                "the grid cell title lays out its lines",
                Await.LONG_TIMEOUT_MS,
                read = { runCatching { LocaleViews.metricsOf(title) }.getOrNull() },
            ) { it != null && it.lineCount > 0 }!!

            assertEquals(
                "homeGridTitleLines must cap the title at $GRID_TITLE_LINES lines",
                GRID_TITLE_LINES,
                metrics.maxLines,
            )
            val displayed = TestEnvironment.onMainThread { title.layout.text.toString() }
            val ellipsis = displayed.indexOf('…')
            assertTrue("middle truncation must retain the beginning: $displayed", ellipsis > 0)
            assertTrue("middle truncation must retain the ending: $displayed", ellipsis < displayed.lastIndex)
            assertTrue("the displayed title must be shortened", displayed.length < LocaleFixtures.LONG_TITLE.length)
            assertTrue(LocaleFixtures.LONG_TITLE.startsWith(displayed.substringBefore('…')))
            assertTrue(LocaleFixtures.LONG_TITLE.endsWith(displayed.substringAfter('…')))
            assertTrue("the title must fit the configured lines", metrics.lineCount <= GRID_TITLE_LINES)
            assertFalse("the shortened title must fit its height", metrics.isVerticallyClipped)

        }
    }

    private fun scrollDown() {
        val scrollable = TestEnvironment.uiDevice.findObject(By.scrollable(true)) ?: return
        scrollable.scroll(Direction.DOWN, SCROLL_FRACTION)
        TestEnvironment.uiDevice.waitForIdle()
    }

    private companion object {
        const val NARROW_WIDTH_PX = 320
        const val NARROW_HEIGHT_PX = 640
        const val NARROW_DENSITY_DPI = 160
        const val SCROLL_STEPS = 6
        const val SCROLL_FRACTION = 0.7f
        const val GRID_TITLE_LINES = 2
        const val SINGLE_RECORD = 1

        val PLAIN_GERMAN_SUMMARIES = listOf(
            R.string.dual_page_first_page_alone_summary,
            R.string.browser_scroll_mode_summary,
            R.string.snap_summary,
            R.string.fling_summary,
            R.string.keep_screen_on_summary,
            R.string.always_open_first_page_summary,
            R.string.always_horizontal_summary,
        )
    }
}
