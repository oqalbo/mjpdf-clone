// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SettingsReachTheReaderTest : SettingsTestCase() {

    @Test
    @TestId("SET-85")
    fun aSettingChangedWhileReadingTakesEffectAsSoonAsTheReaderComesBack() {
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.SETTINGS.id)
        val fixture = SettingsFixtures.pdfFixture()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown(Await.LONG_TIMEOUT_MS)
            reader.awaitLoaded()
            reader.awaitFullQualityRender()
            assertEquals(
                "the document opens with crop margins off, which is the default",
                false,
                cropMarginsEnabled(),
            )

            onView(withId(R.id.toolbarPrimaryActionOption)).perform(click())
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)

            SettingsScreen.awaitRootList()
            SettingsScreen.openPage(SettingsPage.APPEARANCE)
            toggleSwitch(SettingsStrings.of(R.string.always_hide_margins), true)
            SettingsPrefs.awaitBoolean(Preferences.alwaysHideMarginsKey, true)

            pressBackUntilReaderResumed()

            Await.untilValue(
                "the reader picks up the margins preference on the way back",
                Await.LONG_TIMEOUT_MS,
                read = { runCatching { cropMarginsEnabled() }.getOrNull() },
            ) { it == true }

            val state = reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the document was reloaded and drawn again after the return",
                NORMAL_PAGE_COUNT,
                state.pageCount,
            )
        }
    }

    private fun cropMarginsEnabled(): Boolean = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as MainActivity
        ViewModelProvider(activity)[ReaderViewModel::class.java].cropMarginsEnabled
    }

    private fun pressBackUntilReaderResumed() {
        repeat(BACK_PRESS_BUDGET) {
            if (TestEnvironment.resumedActivityOfType(MainActivity::class.java) != null) return
            SettingsScreen.back()
        }
        Screens.awaitGone(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private companion object {
        const val NORMAL_PAGE_COUNT = 20
    }
}
