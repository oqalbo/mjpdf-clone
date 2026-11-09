// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.github.barteksc.pdfviewer.util.FitPolicy
import com.gitlab.mudlej.MjPdfReader.R

data class ReadingLayout(
    val fitPolicy: FitPolicy,
    val swipeHorizontal: Boolean,
    val autoSpacing: Boolean,
    val pageSnap: Boolean,
    val pageFling: Boolean,
    val dualPage: Boolean,
    val freeScroll: Boolean,
    val overridden: Map<String, Int>,
)

fun resolveReadingLayout(pref: Preferences): ReadingLayout {
    var fitPolicy = pref.getPageFitPolicy().libraryPolicy
    var swipeHorizontal = pref.getHorizontalScroll()
    var autoSpacing = swipeHorizontal
    var pageSnap = pref.getPageSnap()
    var pageFling = pref.getPageFling()
    var dualPage = pref.getDualPageMode()
    var freeScroll = pref.getBrowserScrollMode()
    val overridden = mutableMapOf<String, Int>()

    if (swipeHorizontal) {
        freeScroll = false
        dualPage = false
        overridden[Preferences.browserScrollModeKey] = R.string.horizontal_scrolling_mode
        overridden[Preferences.dualPageModeKey] = R.string.horizontal_scrolling_mode
        overridden[Preferences.dualPageFirstPageAloneKey] = R.string.horizontal_scrolling_mode
    }

    if (freeScroll) {
        pageSnap = false
        pageFling = false
        overridden[Preferences.pageSnapKey] = R.string.browser_scroll_mode_title
        overridden[Preferences.pageFlingKey] = R.string.browser_scroll_mode_title
    }

    if (pref.getSinglePageMode()) {
        fitPolicy = FitPolicy.BOTH
        swipeHorizontal = true
        autoSpacing = true
        pageSnap = true
        pageFling = true
        dualPage = false
        freeScroll = false
        overridden[Preferences.pageFitPolicyKey] = R.string.single_page_mode_title
        overridden[Preferences.horizontalScrollKey] = R.string.single_page_mode_title
        overridden[Preferences.pageSnapKey] = R.string.single_page_mode_title
        overridden[Preferences.pageFlingKey] = R.string.single_page_mode_title
        overridden[Preferences.browserScrollModeKey] = R.string.single_page_mode_title
        overridden[Preferences.dualPageModeKey] = R.string.single_page_mode_title
        overridden[Preferences.dualPageFirstPageAloneKey] = R.string.single_page_mode_title
    }

    return ReadingLayout(
        fitPolicy = fitPolicy,
        swipeHorizontal = swipeHorizontal,
        autoSpacing = autoSpacing,
        pageSnap = pageSnap,
        pageFling = pageFling,
        dualPage = dualPage,
        freeScroll = freeScroll,
        overridden = overridden,
    )
}
