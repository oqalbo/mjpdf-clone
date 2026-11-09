// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ExitFullscreenTipTest : ViewTestCase() {

    @Test
    @TestId("VIEW-02")
    fun theFirstEntryToFullscreenShowsTheExitTipAndThePositiveButtonSilencesIt() {
        leaveTheExitTipAtItsDefault()
        val file = openReader()

        enterFullscreen()
        awaitDialogWithTitle(R.string.exit_fullscreen_title)

        tapDialogPositive()
        awaitDialogWithTitleGone(R.string.exit_fullscreen_title)
        Await.untilPreference(Preferences.showExitFullscreenTipKey) { it == false }

        finishReader()
        launchAt(file)
        enterFullscreen()

        assertNoDialogWithTitle("a silenced tip must not come back", R.string.exit_fullscreen_title)
    }

    @Test
    @TestId("VIEW-03")
    @TestDuration(DurationCategory.SHORT)
    fun theOkButtonOnTheExitTipLeavesThePreferenceAlone() {
        leaveTheExitTipAtItsDefault()
        val file = openReader()

        enterFullscreen()
        awaitDialogWithTitle(R.string.exit_fullscreen_title)
        tapDialogNegative()
        awaitDialogWithTitleGone(R.string.exit_fullscreen_title)

        val stored = SettingsSeeder.preferences.all[Preferences.showExitFullscreenTipKey]
        assertEquals(
            "dismissing with OK must leave the preference at its true default, but the store holds $stored",
            true,
            stored ?: true,
        )

        finishReader()
        launchAt(file)
        enterFullscreen()

        awaitDialogWithTitle(R.string.exit_fullscreen_title)
        tapDialogNegative()
        awaitDialogWithTitleGone(R.string.exit_fullscreen_title)
    }

    private fun leaveTheExitTipAtItsDefault() {
        SettingsSeeder.remove(Preferences.showExitFullscreenTipKey)
    }
}
