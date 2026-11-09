// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.data.PageFitPolicy
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.TapToTurnZones
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSortOrder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class MatrixSeedingTest : SettingsTestCase() {

    @Test
    @TestId("MTX-10")
    fun everySeededPreferenceKindReachesTheAppRatherThanFallingBackToItsDefault() {
        val seededShortcutBar = setOf(ConfigurableAction.SEARCH.id, ConfigurableAction.GO_TO_PAGE.id)
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.historyEnabledKey to false,
                Preferences.hideDelayKey to 8000,
                Preferences.maxZoomKey to 4.0f,
                Preferences.autoBackupEnabledAtKey to 1L,
                Preferences.pageFitPolicyKey to PageFitPolicy.HEIGHT.name,
                Preferences.shortcutBarActionsKey to seededShortcutBar,
            )
        )
        SettingsSeeder.seed(Preferences.secondBarEnabledKey, true)

        launchReader(SettingsFixtures.pdfFixture()).use {
            val preferences = Preferences(SettingsPrefs.store)
            assertEquals("historyEnabled reads the seeded false", false, preferences.getHistoryEnabled())
            assertEquals("hideDelay reads the seeded 8000", 8000, preferences.getHideDelay())
            assertEquals("maxZoom reads the seeded 4", 4.0f, preferences.getMaxZoom(), TOLERANCE)
            assertEquals("autoBackupEnabledAt reads the seeded 1", 1L, preferences.getAutoBackupEnabledAt())
            assertEquals(
                "pageFitPolicy reads the seeded HEIGHT",
                PageFitPolicy.HEIGHT,
                preferences.getPageFitPolicy(),
            )
            assertEquals(
                "shortcutBarActions reads the seeded pair",
                seededShortcutBar,
                preferences.getShortcutBarActions(),
            )

            val state = reader.awaitFullQualityRender()
            assertEquals("the viewer applied the seeded zoom ceiling", 4.0f, state.maxZoom ?: 0f, TOLERANCE)

            Await.untilValue(
                "the shortcut bar renders exactly the two seeded actions",
                Await.LONG_TIMEOUT_MS,
                read = { runCatching { shortcutBarLabels() }.getOrNull() },
            ) { it != null && it.size == seededShortcutBar.size }
            assertEquals(
                "the bar holds the two seeded actions and nothing else",
                setOf(
                    SettingsStrings.of(ConfigurableAction.SEARCH.titleRes),
                    SettingsStrings.of(ConfigurableAction.GO_TO_PAGE.titleRes),
                ),
                shortcutBarLabels().toSet(),
            )

            listOf(
                Preferences.historyEnabledKey,
                Preferences.hideDelayKey,
                Preferences.maxZoomKey,
                Preferences.autoBackupEnabledAtKey,
                Preferences.pageFitPolicyKey,
                Preferences.shortcutBarActionsKey,
            ).forEach { key ->
                assertEquals(
                    "$key must still be present, a removal would mean a wrongly typed seed silently fell back",
                    true,
                    SettingsPrefs.contains(key),
                )
            }
        }
    }

    @Test
    @TestId("MTX-11")
    fun aWronglyTypedPreferenceFallsBackToItsDefaultAndIsRemoved() {
        SettingsPrefs.seedLegacyString(Preferences.historyEnabledKey, "yes")
        SettingsPrefs.seedLegacyString(Preferences.hideDelayKey, "fast")
        val fixture = SettingsFixtures.pdfFixture()
        val identity = DocumentIdentity.of(fixture)?.identity
            ?: throw AssertionError("The fixture has no computable identity")

        launchReader(fixture).use {
            reader.awaitFullQualityRender()

            val preferences = Preferences(SettingsPrefs.store)
            assertEquals("a string where a boolean belongs falls back to on", true, preferences.getHistoryEnabled())
            assertEquals("a string where an int belongs falls back to 3000", 3000, preferences.getHideDelay())

            SettingsPrefs.awaitAbsent(Preferences.historyEnabledKey)
            SettingsPrefs.awaitAbsent(Preferences.hideDelayKey)

            Records.awaitRowExists(identity, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("MTX-12")
    fun anUnknownEnumValueFallsBackToItsDefaultAndIsRemoved() {
        SettingsPrefs.seedLegacyString(Preferences.pageFitPolicyKey, "DIAGONAL")
        SettingsPrefs.seedLegacyString(Preferences.tapToTurnKey, "CORNERS")
        SettingsPrefs.seedLegacyString(Preferences.homeSortKey, "RANDOM")

        launchReader(SettingsFixtures.pdfFixture()).use {
            reader.awaitFullQualityRender()

            val preferences = Preferences(SettingsPrefs.store)
            assertEquals("an unknown fit policy falls back to width", PageFitPolicy.WIDTH, preferences.getPageFitPolicy())
            assertEquals(
                "an unknown tap zone falls back to left and right",
                TapToTurnZones.LEFT_RIGHT,
                preferences.getTapToTurnZones(),
            )
            assertEquals("an unknown sort falls back to name", HomeSortOrder.NAME, preferences.getHomeSort())

            SettingsPrefs.awaitAbsent(Preferences.pageFitPolicyKey)
            SettingsPrefs.awaitAbsent(Preferences.tapToTurnKey)
            SettingsPrefs.awaitAbsent(Preferences.homeSortKey)

            assertFalse(
                "safeGetEnum removed the offending entries rather than leaving them behind",
                SettingsPrefs.contains(Preferences.pageFitPolicyKey) ||
                    SettingsPrefs.contains(Preferences.tapToTurnKey) ||
                    SettingsPrefs.contains(Preferences.homeSortKey),
            )
        }
    }

    private fun shortcutBarLabels(): List<String> = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: throw AssertionError("The reader is not resumed")
        val layout = activity.findViewById<ViewGroup>(R.id.secondBarLayout)
            ?: throw AssertionError("The reader has no shortcut bar layout")
        (0 until layout.childCount)
            .map { layout.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE }
            .mapNotNull { it.contentDescription?.toString() }
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
