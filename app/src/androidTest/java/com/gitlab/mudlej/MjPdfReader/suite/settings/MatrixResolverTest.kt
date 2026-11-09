// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PageFitPolicy
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.resolveReadingLayout
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class MatrixResolverTest : SettingsTestCase() {

    @Test
    @TestId("MTX-02")
    fun singlePageModeGivesTheSameLayoutWhateverTheOtherKeysWereLeftAt() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.singlePageModeKey to true,
                Preferences.horizontalScrollKey to false,
                Preferences.browserScrollModeKey to true,
                Preferences.dualPageModeKey to true,
                Preferences.dualPageFirstPageAloneKey to true,
                Preferences.pageSnapKey to false,
                Preferences.pageFlingKey to false,
                Preferences.pageFitPolicyKey to PageFitPolicy.WIDTH.name,
            )
        )

        val layout = resolveReadingLayout(Preferences(SettingsPrefs.store))
        assertEquals("single page fits the whole page", FitPolicy.BOTH, layout.fitPolicy)
        assertEquals("single page swipes horizontally", true, layout.swipeHorizontal)
        assertEquals("single page spaces pages automatically", true, layout.autoSpacing)
        assertEquals("single page snaps", true, layout.pageSnap)
        assertEquals("single page flings", true, layout.pageFling)
        assertEquals("single page is never a dual page spread", false, layout.dualPage)
        assertEquals("single page is never free scrolling", false, layout.freeScroll)
        assertEquals(
            "single page mode announces exactly the seven keys it takes over",
            setOf(
                Preferences.pageFitPolicyKey,
                Preferences.horizontalScrollKey,
                Preferences.pageSnapKey,
                Preferences.pageFlingKey,
                Preferences.browserScrollModeKey,
                Preferences.dualPageModeKey,
                Preferences.dualPageFirstPageAloneKey,
            ),
            layout.overridden.keys,
        )

        launchReader(SettingsFixtures.pdfFixture()).use {
            val state = reader.awaitFullQualityRender()
            assertEquals("the viewport shows one page, not a spread", 1, state.pagesPerRow)
            assertEquals("the reader resolved the horizontal swipe", false, state.swipeVertical)
            assertEquals("the reader resolved the snap", true, state.pageSnap)
            assertEquals("the reader resolved the fling", true, state.pageFling)
            assertEquals(
                "the reader resolved the whole page fit",
                com.gitlab.mudlej.MjPdfReader.suite.state.PageFitPolicy.BOTH,
                state.fitPolicy,
            )

            SettingsPrefs.assertBoolean(Preferences.browserScrollModeKey, true)
            SettingsPrefs.assertBoolean(Preferences.dualPageModeKey, true)
            SettingsPrefs.assertBoolean(Preferences.pageSnapKey, false)
            SettingsPrefs.assertBoolean(Preferences.pageFlingKey, false)
        }
    }

    @Test
    @TestId("MTX-03")
    fun browserScrollModeExplainsWhySnapAndFlingAreNotTakingEffect() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.browserScrollModeKey to true,
                Preferences.pageSnapKey to true,
                Preferences.pageFlingKey to true,
            )
        )

        launchSettingsOn(SettingsPage.READING).use {
            val reason = SettingsStrings.overriddenBy(R.string.browser_scroll_mode_title)
            assertTrue(
                "the Page Snap row explains what took it over",
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.snap)).summaryOrEmpty().endsWith(reason),
            )
            assertTrue(
                "the Page Fling row explains what took it over",
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.fling)).summaryOrEmpty().endsWith(reason),
            )

            toggleSwitch(SettingsStrings.of(R.string.browser_scroll_mode_title), false)
            SettingsPrefs.awaitBoolean(Preferences.browserScrollModeKey, false)

            SettingsScreen.awaitEnabled(SettingsStrings.of(R.string.snap), true)
            SettingsScreen.awaitEnabled(SettingsStrings.of(R.string.fling), true)
            assertFalse(
                "the overridden line goes away in place",
                SettingsScreen.row(SettingsStrings.of(R.string.snap)).summaryOrEmpty().endsWith(reason),
            )
            assertEquals(
                "the rebuild happened without leaving the Reading page",
                SettingsStrings.pageTitle(SettingsPage.READING),
                SettingsScreen.title(),
            )

            SettingsPrefs.assertBoolean(Preferences.pageSnapKey, true)
            SettingsPrefs.assertBoolean(Preferences.pageFlingKey, true)
        }
    }

    @Test
    @TestId("MTX-09")
    fun tapToTurnIsNotOfferedWhileTheLayoutCannotUseIt() {
        launchSettingsOn(SettingsPage.READING).use {
            val tapToTurn = SettingsStrings.of(R.string.tap_to_turn_title)
            assertFalse("the row is unusable in the default layout", SettingsScreen.row(tapToTurn).enabled)

            SettingsScreen.tapRow(tapToTurn)
            SettingsDialog.assertNoneWithin()
            SettingsPrefs.assertUnset(Preferences.tapToTurnKey)

            openChoiceDialog(SettingsStrings.of(R.string.reading_mode_title))
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.reading_mode_single_page))
            SettingsDialog.awaitDismissed()

            SettingsScreen.awaitEnabled(tapToTurn, true)
            SettingsPrefs.assertUnset(Preferences.tapToTurnKey)
        }
    }
}
