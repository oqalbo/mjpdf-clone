// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewRelease
import com.gitlab.mudlej.MjPdfReader.ui.home.LaunchPlan.PreferenceWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlanTest {
    @Test
    fun firstInstallQueuesTheIntroAndWritesPreferencesInLaunchOrder() {
        for (homeDisabled in listOf(false, true)) {
            val plan = LaunchPlan.decide(true, homeDisabled, false, 0, 58)

            assertTrue(plan.launchIntro)
            assertNull(plan.release)
            assertEquals(
                listOf(
                    PreferenceWrite.FirstInstall(false),
                    PreferenceWrite.ShowFeaturesDialog(true),
                    PreferenceWrite.LastSeenVersionCode(58),
                ),
                plan.writes,
            )
        }
    }

    @Test
    fun returningFromIntroShowsCurrentReleaseOnceAndClearsTheFlag() {
        val preferences = Preferences(InMemorySharedPreferences())
        apply(LaunchPlan.decide(true, false, false, 0, 58), preferences)

        val afterIntro = decide(preferences)

        assertFalse(afterIntro.launchIntro)
        assertEquals(WhatsNewRelease.CURRENT, afterIntro.release)
        assertEquals(listOf(PreferenceWrite.ShowFeaturesDialog(false)), afterIntro.writes)
        apply(afterIntro, preferences)
        val nextLaunch = decide(preferences)
        assertFalse(nextLaunch.launchIntro)
        assertNull(nextLaunch.release)
        assertEquals(emptyList<PreferenceWrite>(), nextLaunch.writes)
        assertEquals(58, preferences.getLastSeenVersionCode())
    }

    @Test
    fun upgradeWithHomeDisabledSelectsVersionThreeAndOnlyUpdatesLastSeen() {
        for (showFeaturesDialog in listOf(false, true)) {
            val plan = LaunchPlan.decide(false, true, showFeaturesDialog, 56, 58)

            assertFalse(plan.launchIntro)
            assertEquals(WhatsNewRelease.VERSION_3, plan.release)
            assertEquals(listOf(PreferenceWrite.LastSeenVersionCode(58)), plan.writes)
        }
    }

    @Test
    fun upgradeAtHomeTakesPrecedenceOverTheFirstRunFlagAndClearsItAfterLastSeen() {
        val plan = LaunchPlan.decide(false, false, true, 56, 58)

        assertFalse(plan.launchIntro)
        assertEquals(WhatsNewRelease.VERSION_3, plan.release)
        assertEquals(
            listOf(PreferenceWrite.LastSeenVersionCode(58), PreferenceWrite.ShowFeaturesDialog(false)),
            plan.writes,
        )
    }

    @Test
    fun sameVersionWithoutTheFirstRunFlagDoesNothing() {
        for (homeDisabled in listOf(false, true)) {
            val plan = LaunchPlan.decide(false, homeDisabled, false, 58, 58)

            assertFalse(plan.launchIntro)
            assertNull(plan.release)
            assertEquals(emptyList<PreferenceWrite>(), plan.writes)
        }
    }

    @Test
    fun downgradeLeavesTheSeenVersionUntouched() {
        for (homeDisabled in listOf(false, true)) {
            val plan = LaunchPlan.decide(false, homeDisabled, false, 59, 58)

            assertFalse(plan.launchIntro)
            assertNull(plan.release)
            assertEquals(emptyList<PreferenceWrite>(), plan.writes)
        }
    }

    @Test
    fun readerRedirectDoesNotConsumeTheFirstRunFlagWithoutAnUpgrade() {
        val plan = LaunchPlan.decide(false, true, true, 58, 58)

        assertFalse(plan.launchIntro)
        assertNull(plan.release)
        assertEquals(emptyList<PreferenceWrite>(), plan.writes)
    }

    private fun decide(preferences: Preferences) = LaunchPlan.decide(
        preferences.getFirstInstall(),
        preferences.getHomeDisabled(),
        preferences.getShowFeaturesDialog(),
        preferences.getLastSeenVersionCode(),
        58,
    )

    private fun apply(plan: LaunchPlan.Decision, preferences: Preferences) {
        plan.writes.forEach { write ->
            when (write) {
                is PreferenceWrite.FirstInstall -> preferences.setFirstInstall(write.value)
                is PreferenceWrite.ShowFeaturesDialog -> preferences.setShowFeaturesDialog(write.value)
                is PreferenceWrite.LastSeenVersionCode -> preferences.setLastSeenVersionCode(write.value)
            }
        }
    }
}
