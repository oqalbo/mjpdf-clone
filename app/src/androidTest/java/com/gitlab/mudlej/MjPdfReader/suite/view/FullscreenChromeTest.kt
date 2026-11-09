// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FullscreenChromeTest : ViewTestCase() {

    @Test
    @TestId("VIEW-01")
    fun enteringFullscreenHidesEveryPieceOfChrome() {
        openReader()

        enterFullscreen()

        ViewUi.assertVisibility("entering fullscreen", R.id.appBarBottomShadow, View.GONE)
        ViewUi.assertVisibility("entering fullscreen", R.id.secondBarScrollView, View.GONE)
        ViewUi.assertVisibility(
            "the overlay panel is hidden on entry, because toggleFullscreen calls hideAll",
            R.id.fullScreenButtonsLayout,
            View.GONE,
        )
        ViewUi.awaitSystemBarsHidden()
        settledFullQuality()
    }

    @Test
    @TestId("VIEW-04")
    fun theExitFullscreenButtonRestoresTheChrome() {
        openReader()
        enterFullscreen()

        revealOverlay()
        tapOverlayButton(R.id.exitFullScreenButton)

        ViewUi.awaitActionBar(showing = true)
        ViewUi.awaitVisibility(R.id.appBarBottomShadow, View.VISIBLE)
        Await.until("the status bars report visible again") { ViewUi.statusBarsVisible() }
        ViewUi.awaitVisibility(R.id.fullScreenButtonsLayout, View.GONE, OVERLAY_TIMEOUT_MS)
    }

    @Test
    @TestId("VIEW-06")
    fun aDisabledShortcutBarStaysHiddenAfterLeavingFullscreen() {
        openReader()

        enterFullscreen()
        ViewUi.assertVisibility("in fullscreen", R.id.secondBarScrollView, View.GONE)

        revealOverlay()
        tapOverlayButton(R.id.exitFullScreenButton)
        ViewUi.awaitActionBar(showing = true)

        ViewUi.assertVisibility("after leaving fullscreen", R.id.secondBarScrollView, View.GONE)
        assertFalse(
            "fullscreen must not write the shortcut bar preference, but the store holds " +
                "${SettingsSeeder.preferences.all[Preferences.secondBarEnabledKey]}",
            SettingsSeeder.preferences.contains(Preferences.secondBarEnabledKey),
        )
    }

    @Test
    @TestId("VIEW-07")
    fun autoFullscreenEntersFullscreenWhenTheDocumentFinishesLoading() {
        SettingsSeeder.seed(Preferences.autoFullScreenKey, true)

        openReader()

        ViewUi.awaitActionBar(showing = false)
        ViewUi.awaitSystemBarsHidden()
        ViewUi.assertVisibility("after an automatic entry", R.id.fullScreenButtonsLayout, View.GONE)
    }

    @Test
    @TestId("VIEW-08")
    @TestDuration(DurationCategory.NORMAL)
    fun fullscreenSurvivesBackgroundingAndReturning() {
        val file = openReader()
        enterFullscreen()
        val identityBefore = readerIdentity()

        backgroundAndReturn(file)

        ViewUi.awaitActionBar(showing = false)
        assertEquals(
            "returning to the reader must resume the same activity rather than build a new one",
            identityBefore,
            readerIdentity(),
        )
        assertFalse("the action bar must still report not showing", ViewUi.actionBarShowing())
        ViewUi.awaitSystemBarsHidden()
    }

    @Test
    @TestId("VIEW-09")
    fun fullscreenSurvivesRotation() {
        openReader()
        enterFullscreen()
        val identityBefore = readerIdentity()

        TestEnvironment.uiDevice.setOrientationLeft()
        reader.awaitState("the reader reconfigures to a landscape viewport", Await.LONG_TIMEOUT_MS) {
            it.viewWidth != null && it.viewHeight != null && it.viewWidth!! > it.viewHeight!!
        }
        settledFullQuality()

        assertEquals(
            "MainActivity declares configChanges, so a rotation must hold fullscreen in place on the same instance",
            identityBefore,
            readerIdentity(),
        )
        assertFalse("the action bar must report not showing after the rotation", ViewUi.actionBarShowing())
        ViewUi.awaitSystemBarsHidden()
        ViewUi.assertVisibility("after the rotation", R.id.appBarBottomShadow, View.GONE)
    }

    @Test
    @TestId("VIEW-11")
    fun tappingThePageOutsideFullscreenNeverShowsTheOverlayPanel() {
        openReader()

        tapCanvas()
        Await.until("the scroll handle appears, which confirms the tap was handled", Await.SCROLL_HANDLE_TIMEOUT_MS) {
            TestEnvironment.uiDevice.hasObject(By.desc(DefaultScrollHandle.DEBUG_HANDLE_DESCRIPTION))
        }

        ViewUi.assertVisibility("outside fullscreen", R.id.fullScreenButtonsLayout, View.GONE)
        ViewUi.assertVisibility("outside fullscreen", R.id.brightnessLayout, View.GONE)
        ViewUi.assertVisibility("outside fullscreen", R.id.autoScrollLayout, View.GONE)
        assertTrue("the action bar must stay on screen outside fullscreen", ViewUi.actionBarShowing())
    }

    @Test
    @TestId("VIEW-58")
    fun fullscreenIsRestoredAfterAnotherWindowStealsAndReturnsFocus() {
        openReader()
        enterFullscreenWithOverlay()

        val tableOfContents = ViewUi.overlayButtonWithDescription(ViewUi.string(R.string.table_of_contents))
        assertTrue(
            "the table of contents button must be in the default overlay panel, which is the only entry point " +
                "to another window while the toolbar is hidden",
            tableOfContents != null,
        )
        TestEnvironment.onMainThread { tableOfContents!!.performClick() }
        Screens.awaitResumed(TableOfContentsActivity::class.java, Await.LONG_TIMEOUT_MS)

        TestEnvironment.uiDevice.pressBack()
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        Await.until("the reader reports window focus again", Await.LONG_TIMEOUT_MS) { ViewUi.hasWindowFocus() }

        ViewUi.awaitSystemBarsHidden()
        assertFalse("the action bar must still report not showing", ViewUi.actionBarShowing())
    }
}
