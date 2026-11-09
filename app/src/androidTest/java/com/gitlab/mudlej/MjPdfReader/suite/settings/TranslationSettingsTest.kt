// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.translation.TranslationEngine
import com.gitlab.mudlej.MjPdfReader.data.translation.TranslationLanguages
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TranslationSettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-33")
    @TestDuration(DurationCategory.SHORT)
    fun theEngineAndLanguageRowsBecomeUsableOnlyInWebTranslatorMode() {
        launchSettingsOn(SettingsPage.TRANSLATION).use {
            assertFalse("the Engine row does nothing outside web mode", SettingsScreen.row(engineTitle).enabled)
            assertFalse("the Target language row does nothing outside web mode", SettingsScreen.row(languageTitle).enabled)
            SettingsPrefs.assertUnset(Preferences.translationModeKey)

            openChoiceDialog(modeTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.translation_mode_web_translator))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.translationModeKey, Preferences.translationModeWeb)
            SettingsScreen.awaitEnabled(engineTitle, true)
            SettingsScreen.awaitEnabled(languageTitle, true)
        }
    }

    @Test
    @TestId("SET-34")
    @TestDuration(DurationCategory.SHORT)
    fun theChosenTranslationEngineIsStoredAndResetReturnsToGoogle() {
        SettingsSeeder.seed(Preferences.translationModeKey, Preferences.translationModeWeb)
        val engine = TranslationEngine.BING

        launchSettingsOn(SettingsPage.TRANSLATION).use {
            openChoiceDialog(engineTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(engine.titleRes))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.translationEngineKey, engine.id)
            assertFalse(
                "Bing does not declare itself unstable, so its label carries no marker",
                engine.unstable,
            )
            SettingsScreen.awaitRowSummaryIs(engineTitle, SettingsStrings.of(engine.titleRes))

            openChoiceDialog(engineTitle)
            SettingsDialog.reset()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitString(Preferences.translationEngineKey, Preferences.translationEngineDefault)
        }
    }

    @Test
    @TestId("SET-35")
    fun aMalformedCustomTranslationUrlIsRefusedAndAValidTemplateIsAccepted() {
        SettingsSeeder.seed(Preferences.translationModeKey, Preferences.translationModeWeb)
        val invalidError = SettingsStrings.of(R.string.translation_custom_url_invalid)

        launchSettingsOn(SettingsPage.TRANSLATION).use {
            openChoiceDialog(engineTitle)
            SettingsDialog.pickChoice(SettingsStrings.of(TranslationEngine.CUSTOM.titleRes))
            SettingsDialog.awaitShown()

            SettingsDialog.setInputText("example.com/?q={text}")
            SettingsDialog.apply()
            SettingsDialog.awaitInputError(invalidError)
            assertTrue("a URL with no scheme keeps the dialog open", SettingsDialog.isShowing())
            SettingsPrefs.assertUnset(Preferences.translationCustomUrlKey, Preferences.translationEngineKey)

            SettingsDialog.setInputText("https://example.com/search")
            SettingsDialog.apply()
            SettingsDialog.awaitInputError(invalidError)
            assertTrue("a URL with no text placeholder keeps the dialog open", SettingsDialog.isShowing())
            SettingsPrefs.assertUnset(Preferences.translationCustomUrlKey, Preferences.translationEngineKey)

            val valid = "https://example.org/translate?q={text}&to={lang}"
            SettingsDialog.setInputText(valid)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.translationCustomUrlKey, valid)
            SettingsPrefs.awaitString(Preferences.translationEngineKey, TranslationEngine.CUSTOM.id)
            SettingsScreen.awaitRowSummaryContains(engineTitle, "(example.org)")
        }
    }

    @Test
    @TestId("SET-36")
    fun theDeviceLanguageOptionIsStoredAsAnEmptyStringRatherThanAFakeCode() {
        SettingsSeeder.seed(Preferences.translationModeKey, Preferences.translationModeWeb)
        val code = "fr"
        val named = TranslationLanguages.displayName(code)
        val deviceLabel = SettingsStrings.of(
            R.string.translation_device_language,
            TranslationLanguages.displayName(TranslationLanguages.deviceLanguage()),
        )

        launchSettingsOn(SettingsPage.TRANSLATION).use {
            openChoiceDialog(languageTitle)
            SettingsDialog.pickChoice(named)
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.translationTargetLanguageKey, code)
            SettingsScreen.awaitRowSummaryIs(languageTitle, named)

            openChoiceDialog(languageTitle)
            SettingsDialog.pickChoice(deviceLabel)
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.translationTargetLanguageKey, "")
            SettingsScreen.awaitRowSummaryIs(languageTitle, deviceLabel)

            openChoiceDialog(languageTitle)
            assertEquals(
                "with nothing stored the device language row is the checked one",
                deviceLabel,
                SettingsDialog.checkedChoiceLabelOrNull(),
            )
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
        }
    }

    private val modeTitle: String get() = SettingsStrings.of(R.string.translate_with_title)
    private val engineTitle: String get() = SettingsStrings.of(R.string.translation_engine_title)
    private val languageTitle: String get() = SettingsStrings.of(R.string.translation_target_language_title)
}
