// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.gitlab.mudlej.MjPdfReader.data.PageFitPolicy
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.ReadingMode
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import com.gitlab.mudlej.MjPdfReader.data.getReadingMode
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.RealOnboarding
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.ReaderFontFamily
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.ReaderTheme
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RealOnboarding
@MediumTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class MatrixDefaultsTest : SettingsTestCase() {

    @Test
    @TestId("MTX-01")
    fun theDocumentedDefaultsAreTheDefaultsTheAppActuallyStartsFrom() {
        val preferences = Preferences(SettingsPrefs.store)

        assertEquals("reading history is on", true, preferences.getHistoryEnabled())
        assertEquals("the home library is on", false, preferences.getHomeDisabled())
        assertEquals("documents open in separate windows", true, preferences.getOpenPdfsInSeparateWindows())
        assertEquals("the app theme follows the system", Preferences.themeSystem, preferences.getInterfaceTheme())
        assertEquals("pdf pages are drawn light", Preferences.themeLight, preferences.getPdfPagesTheme())

        assertEquals("the reading mode is continuous", ReadingMode.CONTINUOUS, preferences.getReadingMode())
        assertEquals("pages are fitted to the width", PageFitPolicy.WIDTH, preferences.getPageFitPolicy())
        assertEquals("page snap is off", false, preferences.getPageSnap())
        assertEquals("page fling is off", false, preferences.getPageFling())
        assertEquals("browser scrolling is off", false, preferences.getBrowserScrollMode())
        assertEquals("single page mode is off", false, preferences.getSinglePageMode())
        assertEquals("dual page mode is off", false, preferences.getDualPageMode())
        assertEquals("horizontal scrolling is off", false, preferences.getHorizontalScroll())
        assertEquals("pages are drawn with a gap", true, preferences.getSpaceBetweenPages())

        assertEquals("the zoom ceiling is ten", 10.0f, preferences.getMaxZoom(), TOLERANCE)
        assertEquals("the rendering tile is 512", 512f, preferences.getPartSize(), TOLERANCE)
        assertEquals("controls hide after three seconds", 3000, preferences.getHideDelay())
        assertEquals("auto scroll starts at speed three", 3, preferences.getScrollSpeed())
        assertEquals("the go to page grid is three columns", 3, preferences.getGoToPageGridColumns())

        assertEquals("shared files are copied", SharedCopyMode.ALWAYS_COPY, preferences.getSharedCopyMode())
        assertEquals("scanning is not configured", ScanMode.NOT_CONFIGURED, preferences.getScanMode())
        assertEquals("no scan location is chosen", emptySet<String>(), preferences.getScanLocations())

        assertEquals(
            "a clean install reads the palette back in defaultSelection order",
            HighlightPalette.defaultSelection.map { it.colorValue },
            preferences.getHighlightColors(),
        )
        assertEquals(
            "the fullscreen overlay ships with its declared actions",
            ConfigurableAction.defaultFullScreenOverlayActionIds,
            preferences.getFullScreenOverlayActions(),
        )
        assertTrue(
            "the fullscreen overlay always carries the way out of fullscreen",
            ConfigurableAction.EXIT_FULLSCREEN.id in preferences.getFullScreenOverlayActions(),
        )
        assertEquals(
            "the shortcut bar ships with its declared actions",
            ConfigurableAction.defaultShortcutBarActionIds,
            preferences.getShortcutBarActions(),
        )

        val textMode = TextModeSettings.load(SettingsPrefs.store)
        assertEquals("text mode starts at 18sp", 18f, textMode.fontSize, TOLERANCE)
        assertEquals("text mode line spacing starts at 1.35", 1.35f, textMode.lineSpacing, TOLERANCE)
        assertEquals("text mode margin starts at 20", 20, textMode.horizontalMargin)
        assertEquals("text mode follows the system theme", ReaderTheme.SYSTEM, textMode.theme)
        assertEquals("text mode uses the sans font", ReaderFontFamily.SANS, textMode.fontFamily)
        assertEquals("text mode caps the line length", true, textMode.readableLineLength)

        assertEquals(
            "reading a default must never write it back, the store holds ${SettingsPrefs.store.all}",
            emptySet<String>(),
            SettingsPrefs.store.all.keys,
        )
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
