// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PageFitPolicy
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SettingsNavigationTest : SettingsTestCase() {

    @After
    fun releaseTask() {
        runCatching { Screens.finishAppTask() }
    }

    @Test
    @TestId("SET-01")
    fun theRootListOffersOneRowPerSettingsPageInDeclarationOrder() {
        openSettingsFromHome()
        SettingsScreen.awaitRootList()

        val rows = SettingsScreen.rows()
        assertEquals(
            "the root list holds one navigation row per SettingsPage value",
            SettingsPage.entries.map { SettingsStrings.pageTitle(it) },
            rows.map { it.title },
        )
        rows.forEachIndexed { index, row ->
            val page = SettingsPage.entries[index]
            assertEquals(
                "the ${row.title} row carries its declared summary",
                SettingsStrings.pageSummary(page),
                row.summary,
            )
            assertTrue("the ${row.title} row summary must not be empty", row.summaryOrEmpty().isNotBlank())
            assertEquals(
                "the ${row.title} row carries the chevron widget layout",
                R.layout.preference_widget_chevron,
                row.widgetLayoutResource,
            )
        }
        assertEquals("the settings screen title", SettingsStrings.of(R.string.settings), SettingsScreen.title())
    }

    @Test
    @TestId("SET-02")
    @TestDuration(DurationCategory.NORMAL)
    fun everyPageOpensShowsItsSectionsAndBackReturnsToTheRoot() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            assertTrue("the search action is offered on the root list", SettingsScreen.isSearchActionPresent())

            SettingsPage.entries.forEach { page ->
                SettingsScreen.openPage(page)
                assertEquals(
                    "the ${page.name} page carries its own title",
                    SettingsStrings.pageTitle(page),
                    SettingsScreen.title(),
                )
                assertFalse(
                    "the settings search action must be hidden while the ${page.name} page is open",
                    SettingsScreen.isSearchActionPresent(),
                )
                assertEquals(
                    "the ${page.name} page shows its declared section headers in order",
                    expectedSections(page),
                    SettingsScreen.sectionTitles(),
                )
                backToRoot()
                Await.until("the settings search action comes back on the root list") {
                    SettingsScreen.isSearchActionPresent()
                }
            }
        }
    }

    @Test
    @TestId("SET-03")
    fun theUpArrowStepsBackToTheRootAndThenClosesSettings() {
        openSettingsFromHome()
        SettingsScreen.awaitRootList()
        SettingsScreen.openPage(SettingsPage.APPEARANCE)

        SettingsScreen.tapUpArrow()
        SettingsScreen.awaitTitle(SettingsStrings.of(R.string.settings))
        SettingsScreen.awaitRootList()

        SettingsScreen.tapUpArrow()
        Screens.awaitGone(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("SET-04")
    fun rotatingWhileAPageIsOpenKeepsThatPageRatherThanTheRootList() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.openPage(SettingsPage.READING)

            Rotation.toLandscape(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitTitle(SettingsStrings.pageTitle(SettingsPage.READING), Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitRow(SettingsStrings.of(R.string.reading_mode_title))
            assertFalse(
                "currentSettingsPage was restored, so the search action stays hidden in landscape",
                SettingsScreen.isSearchActionPresent(),
            )

            Rotation.toPortrait(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitTitle(SettingsStrings.pageTitle(SettingsPage.READING), Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitRow(SettingsStrings.of(R.string.reading_mode_title))
            assertFalse(
                "currentSettingsPage survived the second turn too",
                SettingsScreen.isSearchActionPresent(),
            )
        }
    }

    @Test
    @TestId("SET-05")
    fun theAutoBackupFailureNoticeOpensSettingsStraightOnTheBackupPage() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.autoBackupEnabledKey to true,
                Preferences.autoBackupLastRunKey to System.currentTimeMillis(),
                Preferences.autoBackupLastErrorKey to "Folder unavailable",
                Preferences.autoBackupErrorAcknowledgedRunKey to 0L,
            )
        )

        HomeRobot().launchFromLauncher(Await.LONG_TIMEOUT_MS)
        val action = SettingsStrings.of(R.string.auto_backup_notice_action)
        Await.until("the auto backup failure snackbar offers its action", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.hasObject(androidx.test.uiautomator.By.text(action))
        }
        onView(withText(action)).perform(click())

        SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
        SettingsScreen.awaitTitle(SettingsStrings.pageTitle(SettingsPage.BACKUP))
        SettingsScreen.awaitRow(SettingsStrings.of(R.string.backup_folder_title))
        SettingsScreen.awaitRow(SettingsStrings.of(R.string.auto_backup_title))
        assertFalse(
            "the deep link lands on the Backup page, not on the root list",
            SettingsScreen.hasRow(SettingsStrings.pageTitle(SettingsPage.APPEARANCE)),
        )
        assertEquals(
            "no folder was ever granted, so the folder row explains how to choose one",
            SettingsStrings.of(R.string.backup_folder_summary_unset),
            SettingsScreen.row(SettingsStrings.of(R.string.backup_folder_title)).summary,
        )
    }

    @Test
    @TestId("SET-06")
    fun leavingAPageAndComingBackRebuildsItFromTheStoredValue() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.openPage(SettingsPage.READING)

            openChoiceDialog(SettingsStrings.of(R.string.page_fit_title))
            SettingsDialog.pickChoice(SettingsStrings.of(R.string.page_fit_height))
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitString(Preferences.pageFitPolicyKey, PageFitPolicy.HEIGHT.name)

            backToRoot()
            SettingsScreen.openPage(SettingsPage.READING)

            SettingsPrefs.assertString(Preferences.pageFitPolicyKey, PageFitPolicy.HEIGHT.name)
            assertEquals(
                "the Page Fit row reads back the stored policy rather than a stale summary",
                SettingsStrings.of(R.string.page_fit_height),
                SettingsScreen.row(SettingsStrings.of(R.string.page_fit_title)).summary,
            )
        }
    }

    private fun expectedSections(page: SettingsPage): List<String> = when (page) {
        SettingsPage.APPEARANCE -> listOf(
            R.string.appearance_section_theme,
            R.string.appearance_section_rendering,
            R.string.appearance_section_page,
        )
        SettingsPage.READING -> listOf(
            R.string.reading_section_layout,
            R.string.reading_section_scrolling,
            R.string.reading_section_session,
        )
        SettingsPage.TRANSLATION -> listOf(
            R.string.translation_section_translation,
            R.string.translation_section_dictionary,
        )
        SettingsPage.HISTORY_PRIVACY -> listOf(
            R.string.privacy_section_privacy,
            R.string.privacy_section_clear,
        )
        else -> emptyList()
    }.map { SettingsStrings.of(it) }
}
