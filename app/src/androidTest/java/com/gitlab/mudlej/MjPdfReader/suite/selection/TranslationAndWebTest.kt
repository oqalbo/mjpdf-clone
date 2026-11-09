// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.SearchManager
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TouchActions
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.PackageDisableRule
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class TranslationAndWebTest : SelectionTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @get:Rule
    val packages = PackageDisableRule()

    @Test
    @TestId("SEL-39")
    fun webTranslationBuildsTheEngineUrlWithTheRightTextAndLanguage() {
        seedWebTranslation(engine = "google", targetLanguage = "zh")
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)

        openLatin { _, page ->
            driver.longPressWord(page, "quick")
            SelectionCard.awaitShown()
            val projection = driver.projection(page.pageIndex)
            driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.caretJustAfter(page, page.rangeOf("fox"), projection),
            )

            SelectionCard.tap(R.id.textSelectionTranslateButton)

            val launched = SelectionIntents.awaitSingle(Intent.ACTION_VIEW)
            val url = SelectionIntents.dataString(launched)
            assertTrue(
                "the google engine must apply its own language override, the url reads $url",
                url.startsWith("https://translate.google.com/?sl=auto&tl=zh-CN"),
            )
            assertEquals(
                "the translator must receive exactly the selected phrase",
                "quick brown fox",
                SelectionIntents.decodedQuery(launched, "text"),
            )
            SelectionCard.awaitGone()
        }
    }

    @Test
    @TestId("SEL-40")
    @TestDuration(DurationCategory.SHORT)
    fun deeplForcesABrowserAndEscapesSlashes() {
        seedWebTranslation(engine = "deepl", targetLanguage = "pt")
        IntentStubs.stubEveryOutgoingIntent()

        openReader(SelectionFixtures.URL) { _, file ->
            val page = pageTextOf(file)
            val host = page.rangeOfFragment(SelectionFixtures.URL_BARE_HOST)
            driver.longPressRange(page, host.first until (host.first + HOST_WORD_LENGTH))
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            driver.dragCaretTo(SelectionHandle.END, SelectionTargets.caretJustAfter(page, host, projection))
            assertEquals(
                "the selection must cover the whole address including the slash and docs",
                SelectionFixtures.URL_BARE_HOST,
                page.normalized(driver.selectedRange()),
            )

            SelectionCard.tap(R.id.textSelectionTranslateButton)

            val launched = SelectionIntents.awaitSingle(Intent.ACTION_MAIN)
            val browserSelector = launched.selector
            assertEquals(
                "the browser selector must use ACTION_MAIN",
                Intent.ACTION_MAIN,
                browserSelector?.action,
            )
            assertTrue(
                "the deepl engine must open the browser selector, the intent reads ${SelectionIntents.describe()}",
                browserSelector?.categories?.contains(Intent.CATEGORY_APP_BROWSER) == true,
            )
            val url = SelectionIntents.dataString(launched)
            assertTrue(
                "the deepl language override must be applied, the url reads $url",
                url.contains("#auto/pt-BR/"),
            )
            assertTrue(
                "the slash inside the text must be escaped rather than left bare, the url reads $url",
                url.contains("%5C%2F") || url.contains("%5C/"),
            )
        }
    }

    @Test
    @TestId("SEL-41")
    fun aCustomTemplateWithoutATextPlaceholderIsRejected() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.translationModeKey to Preferences.translationModeWeb,
                Preferences.translationEngineKey to "custom",
                Preferences.translationCustomUrlKey to CUSTOM_TEMPLATE_WITHOUT_TEXT,
            )
        )

        openLatin { _, page ->
            driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            SelectionCard.tap(R.id.textSelectionTranslateButton)

            Snackbars.awaitText(SelectionViews.string(R.string.translation_custom_url_invalid))
            SelectionIntents.assertNone(Intent.ACTION_VIEW)
            assertTrue("a refused action must leave the card up", SelectionCard.isShown())
        }
    }

    @Test
    @TestId("SEL-42")
    fun appsModeWithNoTranslationAppShowsTheGuidanceDialog() {
        packages.disableHandlersOf(processTextProbe())
        assertTrue(
            "a handler for ACTION_PROCESS_TEXT is still enabled",
            packages.handlerPackagesOf(processTextProbe()).isEmpty(),
        )

        openLatin { _, page ->
            driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            SelectionCard.tap(R.id.textSelectionTranslateButton)

            val title = SelectionViews.string(R.string.no_translate_app_found)
            Dialogs.awaitDialogTitled(R.string.no_translate_app_found)
            Await.untilNoThrow("the missing translation app dialog is shown") {
                onView(withText(title)).inRoot(isDialog()).check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(
                        androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
                    )
                )
            }
            onView(withText(SelectionViews.string(R.string.no_translate_app_message))).inRoot(isDialog()).check(
                androidx.test.espresso.assertion.ViewAssertions.matches(
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
                )
            )
            SelectionIntents.assertNone(Intent.ACTION_PROCESS_TEXT)

            onView(withText(SelectionViews.string(R.string.settings))).inRoot(isDialog()).perform(TouchActions.click())
            Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("SEL-43")
    @TestDuration(DurationCategory.NORMAL)
    fun webSearchSendsTheSelectionAsASearchQuery() {
        IntentStubs.stubOkFor(Intent.ACTION_WEB_SEARCH)

        openLatin { _, page ->
            driver.longPressWord(page, "quick")
            SelectionCard.awaitShown()
            val projection = driver.projection(page.pageIndex)
            driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.caretJustAfter(page, page.rangeOf("fox"), projection),
            )
            val intentMark = SelectionIntents.mark()

            SelectionCard.tap(R.id.textSelectionSearchWebButton)

            val launched = SelectionIntents.awaitSingleSince(Intent.ACTION_WEB_SEARCH, intentMark)
            assertEquals(
                "the search must carry exactly the selected words",
                "quick brown fox",
                launched.getStringExtra(SearchManager.QUERY),
            )
            SelectionIntents.assertNoneSince(Intent.ACTION_VIEW, intentMark)
            SelectionCard.awaitGone()
        }
    }

    @Test
    @TestId("SEL-44")
    fun webSearchWithNoSearchAppAndNoBrowserReportsIt() {
        packages.disableHandlersOf(Intent(Intent.ACTION_WEB_SEARCH))
        packages.disableHandlersOf(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=probe")),
        )
        assertTrue(
            "a handler for ACTION_WEB_SEARCH is still enabled",
            packages.handlerPackagesOf(Intent(Intent.ACTION_WEB_SEARCH)).isEmpty(),
        )
        assertTrue(
            "a browser is still enabled",
            packages.handlerPackagesOf(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=probe")),
            ).isEmpty(),
        )

        openLatin { _, page ->
            val before = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            SelectionCard.tap(R.id.textSelectionSearchWebButton)

            Snackbars.awaitText(SelectionViews.string(R.string.no_app_to_open_link))
            assertTrue("a failed action must leave the card up", SelectionCard.isShown())
            val after = state()
            assertEquals("the failed action must not clear the selection", before.selectionStart, after.selectionStart)
            assertEquals("the failed action must not clear the selection", before.selectionEnd, after.selectionEnd)
            Screens.assertTopIs(MainActivity::class.java)
        }
    }

    @Test
    @TestId("SEL-45")
    @TestDuration(DurationCategory.NORMAL)
    fun aUrlInTheSelectionTurnsTheButtonIntoOpenLink() {
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)

        openReader(SelectionFixtures.URL) { _, file ->
            val page = pageTextOf(file)
            val host = page.rangeOfFragment(SelectionFixtures.URL_BARE_HOST)
            driver.longPressRange(page, host.first until (host.first + HOST_WORD_LENGTH))
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            driver.dragCaretTo(SelectionHandle.END, SelectionTargets.caretJustAfter(page, host, projection))
            assertEquals(
                "the selection must cover the whole address",
                SelectionFixtures.URL_BARE_HOST,
                page.normalized(driver.selectedRange()),
            )

            Await.until("the web button turns into an open link button") {
                SelectionCard.contentDescriptionOf(R.id.textSelectionSearchWebButton) ==
                    SelectionViews.string(R.string.open_link)
            }

            SelectionCard.tap(R.id.textSelectionSearchWebButton)

            val launched = SelectionIntents.awaitSingle(Intent.ACTION_VIEW)
            assertEquals(
                "a bare address must be opened over https",
                "https://${SelectionFixtures.URL_BARE_HOST}",
                SelectionIntents.dataString(launched),
            )
            SelectionIntents.assertNone(Intent.ACTION_WEB_SEARCH)
        }
    }

    private fun processTextProbe(): Intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")

    private companion object {
        const val CUSTOM_TEMPLATE_WITHOUT_TEXT = "https://example.com/translate"
        const val HOST_WORD_LENGTH = 3
    }
}
