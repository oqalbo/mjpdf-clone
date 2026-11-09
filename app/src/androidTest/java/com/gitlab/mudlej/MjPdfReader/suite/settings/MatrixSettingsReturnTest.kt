// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class MatrixSettingsReturnTest : SettingsTestCase() {

    @Test
    @TestId("MTX-07")
    fun aLoadTimeSettingChangedInSettingsAppliesOnTheWayBackWithoutLosingThePosition() {
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.SETTINGS.id)

        launchReader(SettingsFixtures.pdfFixture()).use {
            val settled = swipeToPageAtLeast(TARGET_PAGE_INDEX)
            val page = settled.page ?: 0
            val spacingBefore = reader.awaitFullQualityRender().spacing

            openSettingsFromReader()
            SettingsScreen.awaitRootList()
            SettingsScreen.openPage(SettingsPage.APPEARANCE)
            toggleSwitch(SettingsStrings.of(R.string.space_between_pages), false)
            SettingsPrefs.awaitBoolean(Preferences.spaceBetweenPagesKey, false)

            returnToReader()

            val after = reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
            assertEquals("the reload preserved the reading position", page, after.page)
            assertTrue(
                "the applied page spacing changed, it was $spacingBefore and reads ${after.spacing}",
                after.spacing != spacingBefore,
            )
            assertEquals("the gap between pages is gone", 0, after.spacing)
        }
    }

    @Test
    @TestId("MTX-08")
    fun theMarginsPreferenceFlipsCropMarginsOnTheDocumentAlreadyOpen() {
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.SETTINGS.id)

        launchReader(SettingsFixtures.pdfFixture()).use {
            assertEquals("the document opens with crop margins off", false, cropMarginsEnabled())

            changeMarginsPreference(true)
            Await.untilValue(
                "the reader turns crop margins on for the document already open",
                Await.LONG_TIMEOUT_MS,
                read = { runCatching { cropMarginsEnabled() }.getOrNull() },
            ) { it == true }

            changeMarginsPreference(false)
            Await.untilValue(
                "turning the preference back off flips the reader back",
                Await.LONG_TIMEOUT_MS,
                read = { runCatching { cropMarginsEnabled() }.getOrNull() },
            ) { it == false }

            setCropMarginsFromTheReader(true)

            openSettingsFromReader()
            SettingsScreen.awaitRootList()
            returnToReader()

            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "leaving settings without touching the preference must not undo the reader's own choice",
                true,
                cropMarginsEnabled(),
            )
        }
    }

    private fun changeMarginsPreference(target: Boolean) {
        openSettingsFromReader()
        SettingsScreen.awaitRootList()
        SettingsScreen.openPage(SettingsPage.APPEARANCE)
        toggleSwitch(SettingsStrings.of(R.string.always_hide_margins), target)
        SettingsPrefs.awaitBoolean(Preferences.alwaysHideMarginsKey, target)
        returnToReader()
    }

    private fun setCropMarginsFromTheReader(enabled: Boolean) {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as MainActivity
            ViewModelProvider(activity)[ReaderViewModel::class.java].cropMarginsEnabled = enabled
        }
        TestEnvironment.waitForIdleSync()
    }

    private fun openSettingsFromReader() {
        tapPrimaryToolbarAction()
        SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
    }

    private fun returnToReader() {
        repeat(BACK_PRESS_BUDGET) {
            if (TestEnvironment.resumedActivityOfType(MainActivity::class.java) != null) return
            SettingsScreen.back()
        }
        Screens.awaitGone(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun cropMarginsEnabled(): Boolean = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as MainActivity
        ViewModelProvider(activity)[ReaderViewModel::class.java].cropMarginsEnabled
    }

    private companion object {
        const val TARGET_PAGE_INDEX = 2
    }
}
