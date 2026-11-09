// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReadingModeOverrideTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @Test
    @TestId("NAV-27")
    fun firstPageAloneReadsAsOverriddenInSinglePageMode() {
        SettingsSeeder.seedReadingMode(singlePage = true, horizontalScroll = false, dualPage = false)

        openReadingSettings().use {
            val reason = overriddenBy(R.string.single_page_mode_title)

            assertOverridden(Preferences.dualPageFirstPageAloneKey, reason)
            assertOverridden(Preferences.browserScrollModeKey, reason)
            assertOverridden(Preferences.pageSnapKey, reason)
            assertOverridden(Preferences.pageFlingKey, reason)
        }
    }

    @Test
    @TestId("NAV-28")
    fun firstPageAloneReadsAsOverriddenInHorizontalMode() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = true, dualPage = false)

        openReadingSettings().use {
            val reason = overriddenBy(R.string.horizontal_scrolling_mode)

            assertOverridden(Preferences.dualPageFirstPageAloneKey, reason)
            assertOverridden(Preferences.browserScrollModeKey, reason)

            val pageSnap = preference(Preferences.pageSnapKey)
            assertTrue(
                "horizontal scrolling does not override Page Snap, so it must stay enabled",
                pageSnap.isEnabled,
            )
            assertFalse(
                "the Page Snap summary must carry no override reason, read: ${pageSnap.summary}",
                pageSnap.summary?.contains(overriddenPrefix()) == true,
            )
        }
    }

    private fun assertOverridden(key: String, reason: String) {
        val preference = preference(key)
        assertFalse(
            "the preference $key must be disabled while the reading mode overrides it",
            preference.isEnabled,
        )
        assertTrue(
            "the summary of $key must name the override reason \"$reason\", read: ${preference.summary}",
            preference.summary?.contains(reason) == true,
        )
        assertEquals(
            "the preference $key must still be shown rather than removed",
            true,
            preference.isVisible,
        )
    }

    private fun preference(key: String): Preference = Await.untilValue(
        "the reading settings page carries the preference $key",
        read = { findPreference(key) },
    ) { it != null }!!

    private fun findPreference(key: String): Preference? = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(SettingsActivity::class.java) as? SettingsActivity
            ?: return@onMainThread null
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container_view) as? PreferenceFragmentCompat
            ?: return@onMainThread null
        fragment.findPreference(key)
    }

    private fun openReadingSettings(): ActivityScenario<SettingsActivity> {
        val intent = Intent(TestEnvironment.targetContext, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_PAGE, SettingsPage.READING.name)
        val scenario = ActivityScenario.launch<SettingsActivity>(intent)
        Await.until("the reading settings page is built") {
            findPreference(Preferences.pageSnapKey) != null
        }
        return scenario
    }

    private fun overriddenBy(reasonRes: Int): String = TestEnvironment.targetContext
        .getString(R.string.overridden_by, TestEnvironment.targetContext.getString(reasonRes))

    private fun overriddenPrefix(): String = TestEnvironment.targetContext
        .getString(R.string.overridden_by, "")
        .trim()
        .ifEmpty { throw ConditionTimeoutException("The overridden_by string carries no literal text") }
}
