// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.userbookmarks.UserBookmarksActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class MatrixHistoryGateTest : SettingsTestCase() {

    @Test
    @TestId("MTX-04")
    fun turningReadingHistoryOffStopsEveryWriteWithoutIncognito() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.BOOKMARK_PAGE.id)
        val fixture = SettingsFixtures.pdfFixture()
        val identity = identityOf(fixture)

        launchReader(fixture).use {
            val state = swipeToPageAtLeast(TARGET_PAGE_INDEX)
            assertTrue(
                "the document itself worked, the reader reports page index ${state.page}",
                (state.page ?: 0) >= TARGET_PAGE_INDEX,
            )

            tapPrimaryToolbarAction()
            TestEnvironment.waitForIdleSync()
        }

        Records.assertNoRowFor(identity)
        assertEquals("the bookmark table stays empty", 0, SettingsRoom.bookmarkCount())
        assertTrue(
            "no annotation journal is written for a document the app is not recording",
            journalFor(fixture).isEmpty(),
        )
    }

    @Test
    @TestId("MTX-05")
    fun incognitoStopsEveryWriteWhileTheHistoryPreferenceStaysOn() {
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.BOOKMARK_PAGE.id)
        val fixture = SettingsFixtures.pdfFixture()
        val identity = identityOf(fixture)

        launchReader(fixture, incognito = true).use {
            assertEquals(
                "the incognito indicator is showing for the whole session",
                View.VISIBLE,
                incognitoIndicatorVisibility(),
            )

            swipeToPageAtLeast(TARGET_PAGE_INDEX)
            tapPrimaryToolbarAction()
            TestEnvironment.waitForIdleSync()
        }

        Records.assertNoRowFor(identity)
        assertEquals("the bookmark table stays empty", 0, SettingsRoom.bookmarkCount())
        assertTrue(
            "incognito never rewrites the preference it stands in for",
            !SettingsPrefs.contains(Preferences.historyEnabledKey) ||
                SettingsPrefs.boolean(Preferences.historyEnabledKey) == true,
        )
    }

    @Test
    @TestId("MTX-06")
    @TestDuration(DurationCategory.SHORT)
    fun incognitoForcesDarkChromeOverALightInterfaceTheme() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.interfaceThemeKey to Preferences.themeLight,
                Preferences.appFollowSystemThemeKey to false,
            )
        )
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.USER_BOOKMARKS.id)

        launchReader(SettingsFixtures.pdfFixture(), incognito = true).use {
            assertEquals(
                "the reader forces night mode before super.onCreate whenever the incognito flag is set",
                AppCompatDelegate.MODE_NIGHT_YES,
                localNightModeOf(MainActivity::class.java),
            )

            tapPrimaryToolbarAction()
            Screens.awaitResumed(UserBookmarksActivity::class.java, Await.LONG_TIMEOUT_MS)
            assertEquals(
                "a screen opened from an incognito reader is incognito too",
                AppCompatDelegate.MODE_NIGHT_YES,
                localNightModeOf(UserBookmarksActivity::class.java),
            )

            assertEquals(
                "the default night mode still resolves from the light preference",
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.getDefaultNightMode(),
            )
            SettingsPrefs.assertString(Preferences.interfaceThemeKey, Preferences.themeLight)

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }
    }

    private fun identityOf(file: File): String =
        DocumentIdentity.of(file)?.identity
            ?: throw AssertionError("The fixture ${file.absolutePath} has no computable identity")

    private fun journalFor(file: File): List<String> {
        val directory = File(TestEnvironment.targetContext.filesDir, "annotations")
        val key = SourceKey.of(android.net.Uri.fromFile(file))
        return directory.listFiles()?.map { it.name }?.filter { it.startsWith(key) }.orEmpty()
    }

    private fun incognitoIndicatorVisibility(): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: throw AssertionError("The reader is not resumed")
        activity.findViewById<View>(R.id.actionbarIncognitoIcon)?.visibility ?: View.GONE
    }

    private fun localNightModeOf(type: Class<out android.app.Activity>): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(type) as? AppCompatActivity
            ?: throw AssertionError("${type.simpleName} is not resumed")
        activity.delegate.localNightMode
    }

    private companion object {
        const val TARGET_PAGE_INDEX = 4
    }
}
