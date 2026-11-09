// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.gotopage.GoToPageActivity
import com.gitlab.mudlej.MjPdfReader.ui.history.NavigationHistoryActivity
import com.gitlab.mudlej.MjPdfReader.ui.links.LinksActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.ui.userbookmarks.UserBookmarksActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class IncognitoChildScreensTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()
    private val text = PrivacyTextMode(reader)

    @Test
    @TestId("PRIV-24")
    fun incognitoPropagatesToEveryChildScreen() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.appFollowSystemThemeKey to false,
                Preferences.interfaceThemeKey to Preferences.themeLight,
            )
        )
        PrivacyFixtures.file(PrivacyFixtures.HIGHLIGHTS_RICH)

        ActivityScenario.launch<MainActivity>(
            PrivacyReader.incognitoViewIntent(PrivacyFixtures.HIGHLIGHTS_RICH)
        ).use {
            reader.awaitReady()
            reader.awaitIdentity()

            openAndAssert(string(R.string.table_of_contents), TableOfContentsActivity::class.java)
            openAndAssert(string(R.string.links_in_file), LinksActivity::class.java)
            openAndAssert(string(R.string.bookmarks), UserBookmarksActivity::class.java)
            openAndAssert(string(R.string.user_notes_title), UserNotesActivity::class.java)
            openAndAssert(string(R.string.user_highlights_title), UserHighlightsActivity::class.java)
            openAndAssert(string(R.string.settings), SettingsActivity::class.java)

            openTextModeAndAssert()
            openAllPagesGridAndAssert()
            openNavigationHistoryAndAssert()
            openSearchResultsAndAssert()

            PrivacyStores.holdNoRecords()
            assertEquals(
                "no child screen may create a record for an incognito document",
                0,
                PrivacyStores.recordCount(),
            )
        }
    }

    private fun openAndAssert(tileLabel: String, type: Class<out Activity>) {
        reader.openChildScreen(tileLabel)
        assertScreen(type)
        leave(type)
    }

    private fun openAllPagesGridAndAssert() {
        reader.openChildScreen(string(R.string.go_to_page))
        Dialogs.awaitDialogTitled(R.string.go_to_page)
        onView(withText(string(R.string.all_pages))).inRoot(isDialog()).perform(click())
        assertScreen(GoToPageActivity::class.java)
        leave(GoToPageActivity::class.java)
    }

    private fun openNavigationHistoryAndAssert() {
        reader.goToPage(HISTORY_JUMP_ONE_BASED)
        reader.openChildScreen(string(R.string.navigation_history))
        assertScreen(NavigationHistoryActivity::class.java)
        leave(NavigationHistoryActivity::class.java)
    }

    private fun openSearchResultsAndAssert() {
        reader.openChildScreen(string(R.string.search))
        Dialogs.awaitDialogTitled(R.string.search)
        onView(withId(R.id.editText)).inRoot(isDialog())
            .perform(replaceText(SEARCH_QUERY), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

        val counter = Await.untilNotNull("the inline search counter appears", Await.VERY_LONG_TIMEOUT_MS) {
            TestEnvironment.onMainThread {
                TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                    ?.findViewById<android.widget.TextView>(
                        com.google.android.material.R.id.snackbar_text
                    )
                    ?.takeIf { it.isShown && it.isClickable }
            }
        }
        com.gitlab.mudlej.MjPdfReader.suite.robot.Taps.tapCentreOf(counter)
        assertScreen(SearchActivity::class.java)
        leave(SearchActivity::class.java)
    }

    private fun openTextModeAndAssert() {
        reader.openChildScreen(string(R.string.text_mode))
        text.awaitShown()
        assertScreen(TextModeActivity::class.java)
        leave(TextModeActivity::class.java)
    }

    private fun assertScreen(type: Class<out Activity>) {
        val activity = Screens.awaitResumed(type, Await.LONG_TIMEOUT_MS)
        assertTrue(
            "${type.simpleName} must be launched with the incognito extra set",
            activity.intent.getBooleanExtra(PDF.incognitoKey, false),
        )
        assertTrue(
            "${type.simpleName} must paint the incognito surface despite the seeded light theme, " +
                PrivacyChrome.describe(activity),
            PrivacyChrome.isIncognitoChrome(activity),
        )
        if (type == SettingsActivity::class.java) {
            assertEquals(
                "Settings must force night mode before painting the incognito overlay",
                AppCompatDelegate.MODE_NIGHT_YES,
                (activity as AppCompatActivity).delegate.localNightMode,
            )
        }
    }

    private fun leave(type: Class<out Activity>) {
        TestEnvironment.uiDevice.pressBack()
        TestEnvironment.waitForIdleSync()
        Screens.awaitGone(type, Await.LONG_TIMEOUT_MS)
        Screens.awaitTopIs(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        reader.awaitReady()
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val HISTORY_JUMP_ONE_BASED = 6
        const val SEARCH_QUERY = "the"
    }
}
