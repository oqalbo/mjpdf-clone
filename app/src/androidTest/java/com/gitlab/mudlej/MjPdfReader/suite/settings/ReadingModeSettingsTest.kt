// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PageFitPolicy
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.TapToTurnZones
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReadingModeSettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-22")
    fun choosingDualPageWritesAllThreeLayoutBooleansAtOnce() {
        launchSettingsOn(SettingsPage.READING).use {
            openChoiceDialog(readingModeTitle)
            assertNull(
                "the reading mode picker commits on tap, so it offers no confirm button",
                SettingsDialog.buttonLabel(SettingsDialog.POSITIVE),
            )

            SettingsDialog.pickChoice(SettingsStrings.of(R.string.reading_mode_dual_page))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitBoolean(Preferences.dualPageModeKey, true)
            SettingsPrefs.assertBoolean(Preferences.horizontalScrollKey, false)
            SettingsPrefs.assertBoolean(Preferences.singlePageModeKey, false)

            val summary = SettingsScreen.awaitRow(readingModeTitle).summaryOrEmpty()
            assertEquals(
                "the row leads with the chosen mode and follows it with that mode's own summary line",
                SettingsStrings.of(R.string.reading_mode_dual_page) + "\n" +
                    SettingsStrings.of(R.string.dual_page_mode_summary),
                summary,
            )
        }
    }

    @Test
    @TestId("SET-23")
    fun singlePageModeMarksEverySettingItTakesOverAndFreesTapToTurn() {
        launchSettingsOn(SettingsPage.READING).use {
            assertFalse(
                "Tap to Turn is unusable before single page mode is chosen",
                SettingsScreen.row(tapToTurnTitle).enabled,
            )

            openChoiceDialog(readingModeTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.reading_mode_single_page))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitBoolean(Preferences.singlePageModeKey, true)
            SettingsPrefs.assertBoolean(Preferences.horizontalScrollKey, false)
            SettingsPrefs.assertBoolean(Preferences.dualPageModeKey, false)

            val reason = SettingsStrings.overriddenBy(R.string.single_page_mode_title)
            listOf(
                R.string.dual_page_first_page_alone_title,
                R.string.snap,
                R.string.fling,
                R.string.browser_scroll_mode_title,
                R.string.page_fit_title,
            ).forEach { titleRes -> assertOverridden(SettingsStrings.of(titleRes), reason) }

            SettingsScreen.awaitEnabled(tapToTurnTitle, true)
        }
    }

    @Test
    @TestId("SET-24")
    fun horizontalModeMarksTheRowsItTakesOverAndLeavesTapToTurnUnusable() {
        launchSettingsOn(SettingsPage.READING).use {
            openChoiceDialog(readingModeTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.reading_mode_horizontal))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitBoolean(Preferences.horizontalScrollKey, true)
            SettingsPrefs.assertBoolean(Preferences.singlePageModeKey, false)
            SettingsPrefs.assertBoolean(Preferences.dualPageModeKey, false)

            val reason = SettingsStrings.overriddenBy(R.string.horizontal_scrolling_mode)
            assertOverridden(SettingsStrings.of(R.string.browser_scroll_mode_title), reason)
            assertOverridden(SettingsStrings.of(R.string.dual_page_first_page_alone_title), reason)

            assertFalse(
                "only single page mode enables Tap to Turn, so horizontal leaves it disabled",
                SettingsScreen.row(tapToTurnTitle).enabled,
            )
        }
    }

    @Test
    @TestId("SET-25")
    @TestDuration(DurationCategory.NORMAL)
    fun cancellingTheReadingModePickerChangesNothingAndResetReturnsToContinuous() {
        launchSettingsOn(SettingsPage.READING).use {
            openChoiceDialog(readingModeTitle)
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.assertUnset(
                Preferences.singlePageModeKey,
                Preferences.horizontalScrollKey,
                Preferences.dualPageModeKey,
            )

            openChoiceDialog(readingModeTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.reading_mode_dual_page))
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitBoolean(Preferences.dualPageModeKey, true)

            openChoiceDialog(readingModeTitle)
            SettingsDialog.reset()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitBoolean(Preferences.dualPageModeKey, false)
            SettingsPrefs.assertBoolean(Preferences.horizontalScrollKey, false)
            SettingsPrefs.assertBoolean(Preferences.singlePageModeKey, false)
            assertTrue(
                "the row falls back to the Continuous label",
                SettingsScreen.awaitRow(readingModeTitle).summaryOrEmpty()
                    .startsWith(SettingsStrings.of(R.string.reading_mode_continuous)),
            )
        }
    }

    @Test
    @TestId("SET-26")
    fun browserLikeScrollingDisablesSnapAndFlingWithoutLeavingThePage() {
        launchSettingsOn(SettingsPage.READING).use {
            toggleSwitch(SettingsStrings.of(R.string.browser_scroll_mode_title), true)
            SettingsPrefs.awaitBoolean(Preferences.browserScrollModeKey, true)

            val reason = SettingsStrings.overriddenBy(R.string.browser_scroll_mode_title)
            SettingsScreen.awaitEnabled(SettingsStrings.of(R.string.snap), false)
            SettingsScreen.awaitEnabled(SettingsStrings.of(R.string.fling), false)
            assertOverridden(SettingsStrings.of(R.string.snap), reason)
            assertOverridden(SettingsStrings.of(R.string.fling), reason)

            assertEquals(
                "the rebuild happens in place, so the Reading page is still the one on screen",
                SettingsStrings.pageTitle(SettingsPage.READING),
                SettingsScreen.title(),
            )
            assertEquals(
                "the switch that caused the rebuild renders on",
                true,
                SettingsScreen.row(SettingsStrings.of(R.string.browser_scroll_mode_title)).checked,
            )
        }
    }

    @Test
    @TestId("SET-27")
    @TestDuration(DurationCategory.NORMAL)
    fun thePageFitChoicesPersistAsTheEnumNameTheReaderReads() {
        launchSettingsOn(SettingsPage.READING).use {
            pickPageFit(R.string.page_fit_height, PageFitPolicy.HEIGHT)
            assertCheckedChoiceOnReopen(pageFitTitle, SettingsStrings.of(R.string.page_fit_height))

            pickPageFit(R.string.page_fit_whole, PageFitPolicy.BOTH)
            assertCheckedChoiceOnReopen(pageFitTitle, SettingsStrings.of(R.string.page_fit_whole))

            openChoiceDialog(pageFitTitle)
            SettingsDialog.reset()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitString(Preferences.pageFitPolicyKey, PageFitPolicy.WIDTH.name)
            assertCheckedChoiceOnReopen(pageFitTitle, SettingsStrings.of(R.string.page_fit_width))
        }
    }

    @Test
    @TestId("SET-28")
    @TestDuration(DurationCategory.NORMAL)
    fun tapToTurnIsOfferedOnlyInSinglePageModeAndStoresTheChosenZones() {
        launchSettingsOn(SettingsPage.READING).use {
            assertFalse("the Tap to Turn row is disabled by default", SettingsScreen.row(tapToTurnTitle).enabled)
            SettingsScreen.tapRow(tapToTurnTitle)
            SettingsDialog.assertNoneWithin()
            SettingsPrefs.assertUnset(Preferences.tapToTurnKey)

            openChoiceDialog(readingModeTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.reading_mode_single_page))
            SettingsDialog.awaitDismissed()
            SettingsScreen.awaitEnabled(tapToTurnTitle, true)

            openChoiceDialog(tapToTurnTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.tap_turn_off))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.tapToTurnKey, TapToTurnZones.OFF.name)
            SettingsScreen.awaitRowSummaryIs(tapToTurnTitle, SettingsStrings.of(R.string.tap_turn_off))
        }
    }

    private fun pickPageFit(labelRes: Int, expected: PageFitPolicy) {
        openChoiceDialog(pageFitTitle)
        SettingsDialog.pickChoice(SettingsStrings.of(labelRes))
        SettingsDialog.awaitDismissed()
        SettingsPrefs.awaitString(Preferences.pageFitPolicyKey, expected.name)
        SettingsScreen.awaitRowSummaryIs(pageFitTitle, SettingsStrings.of(labelRes))
    }

    private fun assertCheckedChoiceOnReopen(rowTitle: String, expectedLabel: String) {
        openChoiceDialog(rowTitle)
        Await.untilValue(
            "the reopened picker checks the row matching the stored value",
            Await.DEFAULT_TIMEOUT_MS,
            read = { runCatching { SettingsDialog.checkedChoiceLabelOrNull() }.getOrNull() },
        ) { it == expectedLabel }
        SettingsDialog.cancel()
        SettingsDialog.awaitDismissed()
    }

    private fun assertOverridden(title: String, reason: String) {
        val row = SettingsScreen.awaitRow(title)
        assertFalse("the \"$title\" row must be disabled while it is overridden", row.enabled)
        assertTrue(
            "the \"$title\" row summary must end with \"$reason\", it reads \"${row.summary}\"",
            row.summaryOrEmpty().endsWith(reason),
        )
    }

    private val readingModeTitle: String get() = SettingsStrings.of(R.string.reading_mode_title)
    private val tapToTurnTitle: String get() = SettingsStrings.of(R.string.tap_to_turn_title)
    private val pageFitTitle: String get() = SettingsStrings.of(R.string.page_fit_title)
}
