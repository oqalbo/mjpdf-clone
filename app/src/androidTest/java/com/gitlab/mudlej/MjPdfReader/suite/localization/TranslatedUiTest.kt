// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.state.LayoutDirection
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class TranslatedUiTest : LocaleTestCase() {

    @Test
    @TestId("LOC-01")
    fun theHomeScreenRendersArabicRatherThanEnglish() {
        useLocale(ARABIC)
        LocaleRecords.seed("loc01")

        launchHome().use {
            assertEquals("the app process must be running in Arabic", "ar", appLanguageInProcess())

            home.openOverflowMenu()

            awaitText(translated(R.string.settings))
            awaitText(translated(R.string.home_sort))
            assertFalse(
                "the English settings label must not survive into an Arabic UI, visible text was ${visibleTexts()}",
                visibleTexts().contains(english(R.string.settings)),
            )
        }
    }

    @Test
    @TestId("LOC-02")
    fun theReaderRendersPersian() {
        useLocale(PERSIAN)

        openReader(LocaleFixtures.NORMAL) { _ ->
            assertEquals("the app process must be running in Persian", "fa", appLanguageInProcess())

            LocaleActions.openActionsDialog()
            awaitText(translated(R.string.search))
            awaitText(translated(R.string.bookmarks))
            awaitText(translated(R.string.file_metadata))

            LocaleActions.tapDialogEntry(translated(R.string.file_metadata))

            awaitText(translated(R.string.file_metadata))
            awaitText(translated(R.string.pdf_pages))
            awaitText(translated(R.string.pdf_title))
            awaitText(translated(R.string.pdf_author))
        }
    }

    @Test
    @TestId("LOC-03")
    fun settingsRendersRussianOnEveryCategory() {
        useLocale(RUSSIAN)

        launchSettings().use {
            val seen = mutableSetOf<String>()
            SETTINGS_CATEGORIES.forEach { category ->
                LocaleSettings.scrollTo(translated(category))
                awaitText(translated(category))
                seen += visibleTexts()
            }

            val english = SETTINGS_CATEGORIES.map(::english)
            assertTrue(
                "no settings category may still read in English, the text seen while scrolling the whole " +
                    "page was $seen",
                english.none { it in seen },
            )
        }
    }

    @Test
    @TestId("LOC-04")
    fun theUkrainianTranslationReachesTheDeleteConfirmation() {
        useLocale(UKRAINIAN)
        val record = LocaleRecords.seed("loc04")

        launchHome().use {
            LocaleHome.selectItems(home, listOf(record.fileName))
            awaitText(localized(R.string.home_selected_count, 1))

            LocaleHome.tapDelete(localizedPlural(R.plurals.home_delete_batch_confirm_message, 1, 1))

            awaitText(localizedPlural(R.plurals.home_delete_batch_confirm_message, 1, 1))
            awaitText(translated(R.string.delete))
            awaitText(translated(R.string.cancel))
        }
    }

    @Test
    @TestId("LOC-05")
    fun theBackupScreenRendersGerman() {
        useLocale(GERMAN)

        launchSettings().use {
            LocaleSettings.openPage(translated(R.string.settings_backup))
            awaitText(translated(R.string.settings_backup))

            LocaleSettings.openRow(translated(R.string.backup_export_title))
            awaitText(translated(R.string.backup_export_action))
            awaitText(translated(R.string.backup_export_option_settings))
            awaitText(translated(R.string.backup_export_option_history))
        }
    }

    @Test
    @TestId("LOC-06")
    fun hebrewFallsBackToEnglishTextAndStillMirrors() {
        useLocale(HEBREW)
        LocaleRecords.seed("loc06")

        launchHome().use {
            home.openOverflowMenu()

            awaitText(english(R.string.settings))
            val visible = visibleTexts()
            assertTrue("an untranslated locale must still render text", visible.isNotEmpty())
            assertTrue(
                "no visible text may be blank or a raw resource name, read $visible",
                visible.none { it.isBlank() || it.startsWith(RESOURCE_NAME_PREFIX) },
            )

            assertEquals(
                "mirroring comes from the locale, not from the translation",
                LayoutDirection.RTL,
                ScreenDirection.require(HomeActivity::class.java, Await.LONG_TIMEOUT_MS),
            )
        }
    }

    @Test
    @TestId("LOC-07")
    fun theHideDelaySummaryIsTranslatedUnderRussian() {
        useLocale(RUSSIAN)
        val russian = localizedPlural(R.plurals.hide_delay_seconds, HIDE_DELAY_SECONDS, HIDE_DELAY_SECONDS)
        assertNotEquals(
            "the hide delay plural must have a Russian form, or the summary check below could pass on English text",
            LocalizedStrings.englishPlural(R.plurals.hide_delay_seconds, HIDE_DELAY_SECONDS, HIDE_DELAY_SECONDS),
            russian,
        )

        launchSettings().use {
            LocaleSettings.openPage(translated(R.string.customize_controls))
            val summary = LocaleSettings.summaryUnder(translated(R.string.hide_delay_title))

            assertTrue(
                "the hide delay summary must carry the Russian \"$russian\", it read \"$summary\"",
                summary.contains(russian),
            )
            assertFalse(
                "the hide delay summary must not read English \"$ENGLISH_SECONDS\", it read \"$summary\"",
                summary.contains(ENGLISH_SECONDS, ignoreCase = true),
            )
            awaitText(translated(R.string.hide_delay_title))
        }
    }

    @Test
    @TestId("LOC-08")
    fun theGoToPageHintIsTranslatedUnderArabic() {
        useLocale(ARABIC)
        val expected = localized(R.string.go_to_page_current_hint, 1, LocaleFixtures.NORMAL_PAGES)
        assertNotEquals(
            "the current page hint must have an Arabic form, or the hint check below could pass on English text",
            LocalizedStrings.string(
                LocalizedStrings.ENGLISH_TAG,
                R.string.go_to_page_current_hint,
                1,
                LocaleFixtures.NORMAL_PAGES,
            ),
            expected,
        )

        openReader(LocaleFixtures.NORMAL) { _ ->
            LocaleGoToPage.open()

            assertEquals(
                "the Go To Page hint must use the Arabic current page wording",
                expected,
                LocaleGoToPage.hint(),
            )
            awaitText(translated(R.string.go_to_page))
            awaitText(translated(R.string.go_to))

            LocaleGoToPage.cancel()
        }
    }

    @Test
    @TestId("LOC-10")
    fun theWhatsNewTitleUsesArabicOnLaunchAndRecreation() {
        assertWhatsNewTitle(ARABIC)
    }

    @Test
    fun theWhatsNewTitleUsesGermanOnLaunchAndRecreation() {
        assertWhatsNewTitle(GERMAN)
    }

    private fun assertWhatsNewTitle(locale: String) {
        useLocale(locale)
        val expected = translated(R.string.whats_new_title)
        LocaleAbout.launchWhatsNew().use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("the activity title uses the app language", expected, activity.title)
                assertEquals("the action bar title uses the app language", expected, activity.supportActionBar?.title)
            }
            awaitText(expected)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("the recreated title uses the app language", expected, activity.title)
                assertEquals("the recreated action bar uses the app language", expected, activity.supportActionBar?.title)
            }
            awaitText(expected)
        }
    }

    private fun awaitText(expected: String) {
        if (TestEnvironment.uiDevice.wait(Until.hasObject(By.text(expected)), Await.LONG_TIMEOUT_MS) != true) {
            throw AssertionError(
                "No view showed \"$expected\" within ${Await.LONG_TIMEOUT_MS}ms. Visible text was ${visibleTexts()}"
            )
        }
    }

    private companion object {
        const val RESOURCE_NAME_PREFIX = "@"
        const val ENGLISH_SECONDS = "seconds"
        const val HIDE_DELAY_SECONDS = 3
        val SETTINGS_CATEGORIES = listOf(
            R.string.settings_home,
            R.string.settings_appearance,
            R.string.settings_reading,
            R.string.customize_controls,
            R.string.settings_text,
            R.string.settings_highlighting,
            R.string.settings_translation,
            R.string.settings_history_privacy,
            R.string.settings_backup,
            R.string.advanced,
        )
    }
}
