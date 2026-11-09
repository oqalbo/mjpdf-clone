// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.github.barteksc.pdfviewer.util.FitPolicy
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingLayoutTest {
    @Test fun singlePageOverridesEveryConflictingLayoutSetting() {
        val preferences = Preferences(InMemorySharedPreferences(mapOf(
            Preferences.singlePageModeKey to true,
            Preferences.horizontalScrollKey to true,
            Preferences.dualPageModeKey to true,
            Preferences.browserScrollModeKey to true,
            Preferences.pageFitPolicyKey to "HEIGHT",
            Preferences.pageSnapKey to false,
            Preferences.pageFlingKey to false,
        )))

        assertEquals(
            ReadingLayout(
                fitPolicy = FitPolicy.BOTH,
                swipeHorizontal = true,
                autoSpacing = true,
                pageSnap = true,
                pageFling = true,
                dualPage = false,
                freeScroll = false,
                overridden = mapOf(
                    Preferences.pageFitPolicyKey to R.string.single_page_mode_title,
                    Preferences.horizontalScrollKey to R.string.single_page_mode_title,
                    Preferences.pageSnapKey to R.string.single_page_mode_title,
                    Preferences.pageFlingKey to R.string.single_page_mode_title,
                    Preferences.browserScrollModeKey to R.string.single_page_mode_title,
                    Preferences.dualPageModeKey to R.string.single_page_mode_title,
                    Preferences.dualPageFirstPageAloneKey to R.string.single_page_mode_title,
                ),
            ),
            resolveReadingLayout(preferences),
        )
    }

    @Test fun horizontalScrollDisablesBrowserScrollAndPreservesSnapAndFlingChoices() {
        for (snap in listOf(false, true)) {
            for (fling in listOf(false, true)) {
                val preferences = Preferences(InMemorySharedPreferences(mapOf(
                    Preferences.horizontalScrollKey to true,
                    Preferences.browserScrollModeKey to true,
                    Preferences.dualPageModeKey to true,
                    Preferences.pageFitPolicyKey to "HEIGHT",
                    Preferences.pageSnapKey to snap,
                    Preferences.pageFlingKey to fling,
                )))

                assertEquals(
                    "snap=$snap fling=$fling",
                    ReadingLayout(
                        fitPolicy = FitPolicy.HEIGHT,
                        swipeHorizontal = true,
                        autoSpacing = true,
                        pageSnap = snap,
                        pageFling = fling,
                        dualPage = false,
                        freeScroll = false,
                        overridden = mapOf(
                            Preferences.browserScrollModeKey to R.string.horizontal_scrolling_mode,
                            Preferences.dualPageModeKey to R.string.horizontal_scrolling_mode,
                            Preferences.dualPageFirstPageAloneKey to R.string.horizontal_scrolling_mode,
                        ),
                    ),
                    resolveReadingLayout(preferences),
                )
            }
        }
    }

    @Test fun browserScrollDisablesSnapAndFling() {
        val preferences = Preferences(InMemorySharedPreferences(mapOf(
            Preferences.browserScrollModeKey to true,
            Preferences.pageSnapKey to true,
            Preferences.pageFlingKey to true,
        )))

        assertEquals(
            ReadingLayout(
                fitPolicy = FitPolicy.WIDTH,
                swipeHorizontal = false,
                autoSpacing = false,
                pageSnap = false,
                pageFling = false,
                dualPage = false,
                freeScroll = true,
                overridden = mapOf(
                    Preferences.pageSnapKey to R.string.browser_scroll_mode_title,
                    Preferences.pageFlingKey to R.string.browser_scroll_mode_title,
                ),
            ),
            resolveReadingLayout(preferences),
        )
    }

    @Test fun conflictingReadingModesPreferSinglePage() {
        val preferences = Preferences(InMemorySharedPreferences(mapOf(
            Preferences.singlePageModeKey to true,
            Preferences.horizontalScrollKey to true,
            Preferences.dualPageModeKey to true,
        )))

        assertEquals(ReadingMode.SINGLE_PAGE, preferences.getReadingMode())
    }

    @Test fun everyReadingModeReplacesConflictingFlagsAndRoundTrips() {
        val expectedFlags = mapOf(
            ReadingMode.CONTINUOUS to Triple(false, false, false),
            ReadingMode.DUAL_PAGE to Triple(false, false, true),
            ReadingMode.HORIZONTAL to Triple(false, true, false),
            ReadingMode.SINGLE_PAGE to Triple(true, false, false),
        )
        for (mode in ReadingMode.entries) {
            val preferences = Preferences(InMemorySharedPreferences(mapOf(
                Preferences.singlePageModeKey to true,
                Preferences.horizontalScrollKey to true,
                Preferences.dualPageModeKey to true,
            )))

            preferences.setReadingMode(mode)

            assertEquals(mode, preferences.getReadingMode())
            assertEquals(mode.name, expectedFlags.getValue(mode), Triple(
                preferences.getSinglePageMode(),
                preferences.getHorizontalScroll(),
                preferences.getDualPageMode(),
            ))
        }
    }
}
