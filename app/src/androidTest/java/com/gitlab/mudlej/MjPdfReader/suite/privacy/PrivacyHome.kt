// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.robot.Taps
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab

class PrivacyHome(val home: HomeRobot = HomeRobot()) {

    val picker = DocumentPickerRobot()

    fun listByFileName(): PrivacyHome {
        SettingsSeeder.seed(Preferences.homeShowPdfTitleKey, false)
        return this
    }

    fun launch(): PrivacyHome {
        home.launchFromLauncher(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        return this
    }

    fun reopen(): PrivacyHome {
        home.startFromLauncher(Await.LONG_TIMEOUT_MS)
        home.awaitShown(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        return this
    }

    fun openFabView(): View = Await.untilNotNull("the open document button is on screen") {
        TestEnvironment.onMainThread {
            TestEnvironment.resumedActivityOfType(HomeActivity::class.java)
                ?.findViewById<View>(R.id.openPdfFab)
                ?.takeIf { it.isShown && it.width > 0 && it.height > 0 }
        }
    }

    fun tapOpenFab() {
        onView(withId(R.id.openPdfFab)).perform(click())
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
    }

    fun longPressOpenFab() {
        val point = Taps.centreOf(openFabView())
        val downTime = RawInput.down(point)
        try {
            Await.until("the held open button launches the document picker", Await.LONG_TIMEOUT_MS) {
                picker.isShown()
            }
        } finally {
            RawInput.up(downTime, point)
        }
    }

    fun startIncognitoPick() {
        longPressOpenFab()
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
    }

    fun pickFromDownloads(fileName: String) {
        picker.pickFromDownloads(fileName)
    }

    fun openIncognitoFromDownloads(fileName: String) {
        startIncognitoPick()
        pickFromDownloads(fileName)
    }

    fun openNormallyFromDownloads(fileName: String) {
        tapOpenFab()
        pickFromDownloads(fileName)
    }

    fun recentTitles(): List<String> {
        home.selectTab(HomeTab.RECENT)
        return home.recent().titles()
    }

    fun recentHasEmptyState(): Boolean {
        home.selectTab(HomeTab.RECENT)
        return home.recent().emptyState() != null
    }

    fun openInIncognitoFromOptions(title: String) {
        home.selectTab(HomeTab.RECENT)
        home.recent().cell(title).tapOptions()
        Await.untilNoThrow("the record options dialog offers Open in Incognito") {
            onView(withId(R.id.openIncognitoRow)).perform(click())
        }
        TestEnvironment.waitForIdleSync()
    }
}
