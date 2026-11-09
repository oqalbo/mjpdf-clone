// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder

object Onboarding {

    const val WHATS_NEW_DELAY_WINDOW_MS = 2_500L

    private val liveStages =
        listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)

    fun preferences(): Preferences = Preferences(SettingsSeeder.preferences)

    fun contains(key: String): Boolean = SettingsSeeder.preferences.contains(key)

    fun firstInstall(): Boolean = preferences().getFirstInstall()

    fun showFeaturesDialog(): Boolean = preferences().getShowFeaturesDialog()

    fun lastSeenVersionCode(): Int = preferences().getLastSeenVersionCode()

    fun seedSettled(lastSeenVersionCode: Int = BuildConfig.VERSION_CODE) {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.firstInstallKey to false,
                Preferences.showFeaturesDialogKey to false,
                Preferences.lastSeenVersionCodeKey to lastSeenVersionCode,
            )
        )
    }

    fun finishAppTask() {
        Screens.finishAppTask(liveStages)
        awaitNoAppActivities()
    }

    fun awaitNoAppActivities(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("every activity of the app is gone from every task", timeoutMs) {
            TestEnvironment.onMainThread {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                liveStages.all { monitor.getActivitiesInStage(it).isEmpty() }
            }
        }
    }

    fun assertNothingResumedFor(condition: String, windowMs: Long = WHATS_NEW_DELAY_WINDOW_MS, resumed: () -> Boolean) {
        Await.assertThroughout(
            condition = condition,
            windowMs = windowMs,
        ) { !resumed() }
    }
}
