// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.translation.TranslationLanguages
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.settings.SettingsDialog
import com.gitlab.mudlej.MjPdfReader.suite.settings.SettingsScreen
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class LocaleSettingsSurfaceTest : LocaleTestCase() {

    @Test
    @TestId("LOC-53")
    fun theTranslationTargetLanguageDefaultsToTheDeviceLanguageUnderArabic() {
        useLocale(ARABIC)
        SettingsSeeder.seed(Preferences.translationModeKey, Preferences.translationModeWeb)

        launchSettingsPage(SettingsPage.TRANSLATION).use {
            val rowTitle = translated(R.string.translation_target_language_title)
            val deviceLanguage = TranslationLanguages.deviceLanguage()
            assertEquals(
                "TranslationLanguages.deviceLanguage reads Locale.getDefault, which the applied app locale " +
                    "sets, so it must resolve to Arabic",
                ARABIC_LANGUAGE,
                deviceLanguage,
            )

            val deviceLabel = localized(
                R.string.translation_device_language,
                TranslationLanguages.displayName(deviceLanguage),
            )
            SettingsScreen.awaitRowSummaryIs(rowTitle, deviceLabel, Await.LONG_TIMEOUT_MS)

            SettingsScreen.tapRow(rowTitle)
            SettingsDialog.awaitChoiceList()
            val labels = SettingsDialog.choiceLabels()

            assertEquals(
                "the device language entry is built first and is what an unset preference selects",
                deviceLabel,
                labels.first(),
            )
            assertEquals(
                "an unset preference must show the device language row as the checked one",
                0,
                SettingsDialog.checkedChoiceIndex(),
            )

            val arabicName = TranslationLanguages.displayName(SAMPLE_LANGUAGE)
            val englishName = Locale(SAMPLE_LANGUAGE).getDisplayLanguage(Locale.ENGLISH)
            assertNotEquals(
                "the sample language must genuinely have a different Arabic name, or this proves nothing",
                englishName,
                arabicName,
            )
            assertTrue(
                "languages must be named in the current locale, so the list must offer \"$arabicName\" " +
                    "rather than \"$englishName\". It offered $labels.",
                labels.contains(arabicName),
            )
            assertFalse(
                "no language may still be named in English, the list offered $labels",
                labels.contains(englishName),
            )

            SettingsDialog.cancel()
            assertFalse(
                "cancelling the dialog must leave the preference unset, so the device language keeps " +
                    "following the phone",
                TestEnvironment.defaultPreferences.contains(Preferences.translationTargetLanguageKey),
            )
        }
    }

    @Test
    @TestId("LOC-54")
    fun settingsSearchMatchesRussianTitlesAndEnglishKeywords() {
        useLocale(RUSSIAN)

        launchSettings().use {
            val rowTitle = translated(R.string.translation_target_language_title)
            val russianTerm = rowTitle.split(" ").first { it.length >= MIN_TERM_LENGTH }

            SettingsScreen.expandSearch()
            SettingsScreen.typeSearchQuery(russianTerm)
            SettingsScreen.awaitRow(rowTitle, Await.LONG_TIMEOUT_MS)

            SettingsScreen.typeSearchQuery(ENGLISH_KEYWORD)
            SettingsScreen.awaitRow(rowTitle, Await.LONG_TIMEOUT_MS)

            SettingsScreen.typeSearchQuery(NONSENSE_QUERY)
            SettingsScreen.awaitRow(translated(R.string.settings_search_no_results), Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("LOC-55")
    fun theSinglePageBadgeAndTheDualPageOffActionAreSeparateArabicResources() {
        useLocale(ARABIC)

        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = false, dualPage = true)
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.DUAL_PAGE.id)
        openReader(LocaleFixtures.NORMAL) { _ ->
            assertEquals(
                "ReaderMenu never lists DUAL_PAGE, so dual_page_off_action reaches the screen only through " +
                    "a configurable button. The primary toolbar button is seeded to it here.",
                translated(R.string.dual_page_off_action),
                LocaleActions.toolbarItemLabel(R.id.toolbarPrimaryActionOption),
            )
        }

        SettingsSeeder.seedReadingMode(singlePage = true, horizontalScroll = false, dualPage = true)
        launchSettingsPage(SettingsPage.READING).use {
            val overriddenRow = translated(R.string.dual_page_first_page_alone_title)
            val badge = localized(R.string.overridden_by, translated(R.string.single_page_mode_title))
            SettingsScreen.awaitRowSummaryContains(overriddenRow, badge, Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitEnabled(overriddenRow, false, Await.LONG_TIMEOUT_MS)
        }
    }

    private fun launchSettingsPage(page: SettingsPage): ActivityScenario<SettingsActivity> {
        val scenario = ActivityScenario.launch<SettingsActivity>(SettingsScreen.pageIntent(page))
        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        return scenario
    }

    private companion object {
        const val ARABIC_LANGUAGE = "ar"
        const val SAMPLE_LANGUAGE = "fr"
        const val ENGLISH_KEYWORD = "translation"
        const val NONSENSE_QUERY = "zzzqqqxxx"
        const val MIN_TERM_LENGTH = 4
    }
}
