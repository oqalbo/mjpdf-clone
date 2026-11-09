// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.actions.FullScreenOverlay
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FullscreenOverlayTest : ViewTestCase() {

    @Test
    @TestId("VIEW-05")
    @TestDuration(DurationCategory.NORMAL)
    fun exitingFullscreenReleasesTheLocksAndTheTransientControls() {
        seedLongOverlayDelay()
        openReader()
        enterFullscreenWithOverlay()
        val rowHeight = ViewUi.onView(R.id.exitFullScreenButton) { it.height }
        ViewUi.onView(R.id.fullScreenButtonsLayout) { panel ->
            panel.layoutParams = panel.layoutParams.apply { height = rowHeight * 3 }
        }

        tapOverlayButton(R.id.toggleZoomLockButton)
        reader.awaitState("the viewer reports the zoom lock") { it.zoomLocked == true }
        tapOverlayButton(R.id.toggleHorizontalSwipeButton)
        reader.awaitState("the viewer reports the horizontal swipe lock") { it.horizontalSwipeLocked == true }
        tapOverlayButton(R.id.brightnessButton)
        ViewUi.awaitVisibility(R.id.brightnessLayout, View.VISIBLE)
        tapOverlayButton(R.id.autoScrollButton)
        ViewUi.awaitVisibility(R.id.autoScrollLayout, View.VISIBLE)

        tapOverlayButton(R.id.exitFullScreenButton)
        ViewUi.awaitActionBar(showing = true)

        val released = state()
        assertEquals("the zoom lock must be released on exit", false as Boolean?, released.zoomLocked)
        assertEquals("the horizontal swipe lock must be released on exit", false as Boolean?, released.horizontalSwipeLocked)
        ViewUi.awaitVisibility(R.id.brightnessLayout, View.GONE)
        assertFalse("the brightness button must report unchecked", ViewUi.isChecked(R.id.brightnessButton))
        ViewUi.awaitVisibility(R.id.autoScrollLayout, View.GONE)
        assertFalse("the auto scroll button must report unchecked", ViewUi.isChecked(R.id.autoScrollButton))
    }

    @Test
    @TestId("VIEW-10")
    fun tappingThePageTogglesTheOverlayPanelAndItAutoHidesAfterTheDelay() {
        SettingsSeeder.seed(Preferences.hideDelayKey, SHORT_HIDE_DELAY_MS)
        openReader()
        enterFullscreen()

        tapCanvas()
        ViewUi.awaitVisibility(R.id.fullScreenButtonsLayout, View.VISIBLE, OVERLAY_TIMEOUT_MS)
        ViewUi.awaitVisibility(R.id.fullScreenButtonsLayout, View.GONE, AUTO_HIDE_TIMEOUT_MS)

        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)
        tapCanvas()
        ViewUi.awaitVisibility(R.id.fullScreenButtonsLayout, View.VISIBLE, OVERLAY_TIMEOUT_MS)
        tapCanvas()
        ViewUi.awaitVisibility(R.id.fullScreenButtonsLayout, View.GONE, OVERLAY_TIMEOUT_MS)
    }

    @Test
    @TestId("VIEW-12")
    fun theDefaultOverlayButtonSetIsExactlyTheDocumentedTen() {
        seedLongOverlayDelay()
        openReader()
        enterFullscreenWithOverlay()

        val visibleIds = ViewUi.visibleOverlayButtonIds().toSet()
        val visibleDynamic = ViewUi.visibleOverlayDynamicDescriptions().toSet()
        val visibleCount = ViewUi.visibleOverlayButtonDescriptions().size

        assertEquals(
            "the fixed buttons of the default overlay panel",
            setOf(
                ViewUi.name(R.id.exitFullScreenButton),
                ViewUi.name(R.id.rotateScreenButton),
                ViewUi.name(R.id.brightnessButton),
                ViewUi.name(R.id.autoScrollButton),
                ViewUi.name(R.id.toggleHorizontalSwipeButton),
                ViewUi.name(R.id.toggleZoomLockButton),
                ViewUi.name(R.id.screenshotButton),
                ViewUi.name(R.id.toggleLabelButton),
            ),
            visibleIds,
        )
        assertEquals(
            "the dynamic buttons of the default overlay panel, matched by content description",
            setOf(ViewUi.string(R.string.crop_margins), ViewUi.string(R.string.table_of_contents)),
            visibleDynamic,
        )
        assertEquals(
            "no other child of the overlay list may be visible, read $visibleIds plus $visibleDynamic",
            DEFAULT_OVERLAY_BUTTON_COUNT,
            visibleCount,
        )
    }

    @Test
    @TestId("VIEW-14")
    fun deselectingBrightnessClosesAnOpenBrightnessControl() {
        seedLongOverlayDelay()
        val file = openReader()
        enterFullscreenWithOverlay()

        tapOverlayButton(R.id.brightnessButton)
        ViewUi.awaitVisibility(R.id.brightnessLayout, View.VISIBLE)
        assertTrue("the brightness button must report checked before the change", ViewUi.isChecked(R.id.brightnessButton))

        SettingsSeeder.seed(
            Preferences.fullScreenOverlayActionsKey,
            ConfigurableAction.defaultFullScreenOverlayActionIds - ConfigurableAction.BRIGHTNESS.id,
        )
        backgroundAndReturn(file)

        ViewUi.awaitVisibility(R.id.brightnessLayout, View.GONE)
        ViewUi.awaitVisibility(R.id.brightnessButton, View.GONE)
        assertFalse("the brightness button must report unchecked", ViewUi.isChecked(R.id.brightnessButton))
    }

    @Test
    @TestId("VIEW-15")
    @TestDuration(DurationCategory.LONG)
    fun deselectingAutoScrollStopsARunningScroll() {
        seedLongOverlayDelay()
        SettingsSeeder.seed(Preferences.scrollSpeedKey, BRISK_SCROLL_SPEED)
        SettingsSeeder.seedAll(mapOf(
            Preferences.pdfPagesThemeKey to Preferences.themeLight,
            Preferences.pdfDarkThemeKey to false,
            Preferences.pdfFollowSystemThemeKey to false,
        ))
        val initialActions = ConfigurableAction.defaultFullScreenOverlayActionIds + ConfigurableAction.SWITCH_THEME.id
        SettingsSeeder.seed(Preferences.fullScreenOverlayActionsKey, initialActions)
        openReader()
        enterFullscreenWithOverlay()

        tapOverlayButton(R.id.autoScrollButton)
        ViewUi.awaitVisibility(R.id.autoScrollLayout, View.VISIBLE)
        tapOverlayButton(R.id.toggleAutoScrollButton)
        reader.awaitState("the reader reports auto scrolling") { it.autoScrolling == true }
        ViewMotion.awaitMonotonic(
            condition = "the page advances before the overlay action is removed",
            steps = MOTION_SAMPLES,
            increasing = false,
        ) { state().offsetY!! }

        ActivityWatcher().use { lifecycle ->
            FullScreenOverlay.tap(ViewUi.string(R.string.switch_to_dark_mode))
            Await.untilPreference(Preferences.pdfPagesThemeKey) { it == Preferences.themeDark }
            assertEquals("ordinary foreground reconfiguration keeps auto-scroll running", true, state().autoScrolling)

            SettingsSeeder.seed(
                Preferences.fullScreenOverlayActionsKey,
                initialActions - ConfigurableAction.AUTO_SCROLL.id,
            )
            assertEquals("auto-scroll is still active before the configuration is applied", true, state().autoScrolling)
            FullScreenOverlay.tap(ViewUi.string(R.string.switch_to_light_mode))
            Await.untilPreference(Preferences.pdfPagesThemeKey) { it == Preferences.themeLight }
            reader.awaitState("removing auto-scroll during foreground reconfiguration stops it") { it.autoScrolling == false }
            lifecycle.assertNeverReached(MainActivity::class.java, Stage.STOPPED)
        }

        ViewUi.awaitVisibility(R.id.autoScrollLayout, View.GONE)
        ViewUi.awaitVisibility(R.id.autoScrollButton, View.GONE)
        assertEquals(
            "a removed auto scroll control must not keep running",
            false as Boolean?,
            state().autoScrolling,
        )
        ViewMotion.assertStable(
            condition = "the vertical offset stops moving once auto scroll is deselected",
            windowMs = STABLE_WINDOW_MS,
        ) { state().offsetY!! }
        ViewMotion.assertStable(
            condition = "the document scroll ratio stops moving once auto scroll is deselected",
            windowMs = STABLE_WINDOW_MS,
        ) { state().positionOffset!! }
    }

    @Test
    @TestId("VIEW-17")
    fun theLabelToggleCollapsesTheButtonsAndPersistsTheChoice() {
        seedLongOverlayDelay()
        val file = openReader()
        enterFullscreenWithOverlay()

        assertEquals(
            "the rotate button carries its label while labels are shown",
            ViewUi.string(R.string.rotate),
            ViewUi.textOf(R.id.rotateScreenButton),
        )

        tapOverlayButton(R.id.toggleLabelButton)

        Await.untilValue(
            condition = "the rotate button loses its text",
            read = { ViewUi.textOf(R.id.rotateScreenButton) },
        ) { it.isEmpty() }
        assertEquals(
            "the label toggle must persist its choice",
            true,
            SettingsSeeder.preferences.all[Preferences.hideButtonsLabelsKey],
        )

        finishReader()
        launchAt(file)
        enterFullscreenWithOverlay()

        assertEquals(
            "the collapsed labels must be reapplied on the next open with no further tap",
            "",
            ViewUi.textOf(R.id.rotateScreenButton),
        )
    }

    @Test
    @TestId("VIEW-18")
    fun theFullscreenInfoLineReflectsTheFourInfoPreferences() {
        seedLongOverlayDelay()
        openReader()
        enterFullscreen()

        seedInfoPreferences(time = false, name = false, page = true, percentage = true)
        revealOverlay()
        ViewUi.awaitVisibility(R.id.fullScreenInfoLayout, View.VISIBLE)
        ViewUi.assertVisibility("run A", R.id.fullScreenInfoPage, View.VISIBLE)
        assertEquals(
            "run A must format page one of ${ViewFixtures.NORMAL_PAGE_COUNT} with its reading percentage",
            ViewUi.string(R.string.fullscreen_page_info, 1, ViewFixtures.NORMAL_PAGE_COUNT, FIRST_PAGE_PERCENTAGE),
            ViewUi.textOf(R.id.fullScreenInfoPage),
        )
        ViewUi.assertVisibility("run A", R.id.fullScreenInfoTime, View.GONE)
        ViewUi.assertVisibility("run A", R.id.fullScreenInfoTitle, View.GONE)

        hideOverlay()
        seedInfoPreferences(time = true, name = true, page = true, percentage = true)
        revealOverlay()
        ViewUi.awaitVisibility(R.id.fullScreenInfoLayout, View.VISIBLE)
        ViewUi.awaitVisibility(R.id.fullScreenInfoTime, View.VISIBLE)
        ViewUi.assertVisibility("run B", R.id.fullScreenInfoTitle, View.VISIBLE)
        ViewUi.assertVisibility("run B", R.id.fullScreenInfoPage, View.VISIBLE)
        assertEquals(
            "run B must show the document title, which is the file name without its extension",
            NORMAL_DOCUMENT_TITLE,
            ViewUi.textOf(R.id.fullScreenInfoTitle),
        )

        hideOverlay()
        seedInfoPreferences(time = false, name = false, page = false, percentage = false)
        revealOverlay()

        ViewUi.awaitVisibility(R.id.fullScreenInfoLayout, View.GONE)
    }

    private fun seedInfoPreferences(time: Boolean, name: Boolean, page: Boolean, percentage: Boolean) {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.fullScreenInfoShowTimeKey to time,
                Preferences.fullScreenInfoShowPdfNameKey to name,
                Preferences.fullScreenInfoShowPageNumberKey to page,
                Preferences.fullScreenInfoShowReadingPercentageKey to percentage,
            )
        )
    }

    private companion object {
        const val DEFAULT_OVERLAY_BUTTON_COUNT = 10
        const val AUTO_HIDE_TIMEOUT_MS = 8_000L
        const val BRISK_SCROLL_SPEED = 60
        const val FIRST_PAGE_PERCENTAGE = 5
        const val NORMAL_DOCUMENT_TITLE = "normal"
    }
}
