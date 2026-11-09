// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class OrientationTest : ViewTestCase() {

    @Test
    @TestId("VIEW-52")
    fun theRotateButtonFlipsTheOrientationAndLocksItThere() {
        assertRotateTogglesAndLocks()
    }

    @Test
    fun theRotateButtonTogglesAndLocksFromALandscapeStart() {
        SettingsSeeder.seed(Preferences.alwaysHorizontalKey, true)
        assertRotateTogglesAndLocks()
    }

    @Test
    @TestId("VIEW-53")
    @TestDuration(DurationCategory.SHORT)
    fun theRotationLockClearsWhenADifferentDocumentIsOpened() {
        seedLongOverlayDelay()
        openReader()
        lockToLandscape()

        val threePage = fixture(ViewFixtures.THREE_PAGE)
        launchAt(threePage)
        reader.awaitState("the second document is the one on screen", Await.LONG_TIMEOUT_MS) {
            it.pageCount == ViewFixtures.THREE_PAGE_PAGE_COUNT
        }

        awaitRequestedOrientation(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            "an orientation chosen for one book does not follow you into the next one",
        )
    }

    @Test
    @TestId("VIEW-54")
    @TestDuration(DurationCategory.NORMAL)
    fun theRotationLockIsNotCarriedAcrossAnAppRestart() {
        seedLongOverlayDelay()
        for (start in listOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE)) {
            val file = openReader()
            if (start == Configuration.ORIENTATION_LANDSCAPE) {
                TestEnvironment.uiDevice.setOrientationLeft()
            } else {
                TestEnvironment.uiDevice.setOrientationNatural()
            }
            awaitConfigurationOrientation(start)
            lockToLandscape()

            finishReader()
            launchAt(file)

            awaitRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                "the rotation lock is not restored after an app restart",
            )
            finishReader()
        }
        val offenders = SettingsSeeder.preferences.all.keys.filter {
            it.contains("orient", ignoreCase = true) || it.contains("rotat", ignoreCase = true)
        }
        assertTrue(
            "no SharedPreferences key may hold an orientation lock, but the store holds $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    @TestId("VIEW-55")
    fun alwaysHorizontalRequestsLandscapeForEveryDocumentAtOpen() {
        SettingsSeeder.seed(Preferences.alwaysHorizontalKey, true)
        seedLongOverlayDelay()

        openReader()

        awaitRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        awaitConfigurationOrientation(Configuration.ORIENTATION_LANDSCAPE)

        TestEnvironment.uiDevice.setOrientationNatural()
        Await.assertThroughout(
            condition = "the sensor cannot undo the Always Horizontal setting",
            windowMs = STABLE_WINDOW_MS,
        ) { ViewUi.configurationOrientation() == Configuration.ORIENTATION_LANDSCAPE }

        enterFullscreenWithOverlay()
        tapOverlayButton(R.id.rotateScreenButton)

        awaitRequestedOrientation(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            "the user lock takes precedence over the Always Horizontal setting",
        )
    }

    private fun assertRotateTogglesAndLocks() {
        seedLongOverlayDelay()
        openReader()
        enterFullscreenWithOverlay()

        TestEnvironment.uiDevice.freezeRotation()
        val startedLandscape = ViewUi.configurationOrientation() == Configuration.ORIENTATION_LANDSCAPE
        val firstRequest = if (startedLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        val firstConfiguration = if (startedLandscape) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
        tapOverlayButton(R.id.rotateScreenButton)

        awaitRequestedOrientation(firstRequest)
        awaitConfigurationOrientation(firstConfiguration)

        if (startedLandscape) {
            TestEnvironment.uiDevice.setOrientationLeft()
        } else {
            TestEnvironment.uiDevice.setOrientationNatural()
        }
        Await.assertThroughout(
            condition = "the user lock keeps the orientation chosen by Rotate",
            windowMs = STABLE_WINDOW_MS,
        ) { ViewUi.configurationOrientation() == firstConfiguration }

        revealOverlay()
        tapOverlayButton(R.id.rotateScreenButton)

        val secondRequest = if (startedLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        awaitRequestedOrientation(secondRequest, "a second tap restores the starting orientation")
        awaitConfigurationOrientation(
            if (startedLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        )
    }

    private fun lockToLandscape() {
        TestEnvironment.uiDevice.freezeRotation()
        enterFullscreenWithOverlay()
        if (ViewUi.configurationOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
            tapOverlayButton(R.id.rotateScreenButton)
            awaitRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            awaitConfigurationOrientation(Configuration.ORIENTATION_PORTRAIT)
            revealOverlay()
        }
        tapOverlayButton(R.id.rotateScreenButton)
        awaitRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        awaitConfigurationOrientation(Configuration.ORIENTATION_LANDSCAPE)
        settledFullQuality()
    }

    private fun awaitRequestedOrientation(
        expected: Int,
        condition: String = "the reader requests orientation $expected",
    ) {
        Await.untilValue(
            condition = condition,
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { ViewUi.requestedOrientation() },
        ) { it == expected }
    }

    private fun awaitConfigurationOrientation(expected: Int) {
        Await.untilValue(
            condition = "the reader configuration reports orientation $expected",
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { ViewUi.configurationOrientation() },
        ) { it == expected }
    }
}
