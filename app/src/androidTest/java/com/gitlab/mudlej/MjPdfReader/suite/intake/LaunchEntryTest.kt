// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class LaunchEntryTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val reader = ReaderRobot()
    private val picker = DocumentPickerRobot()

    @Test
    @TestId("INTAKE-02")
    fun theLauncherEntryReachesTheLibraryWithNoOnboardingInTheWay() {
        seedSettledFirstRun()

        ActivityWatcher().use { watcher ->
            home.launchFromLauncher()
            home.awaitRendered()

            Screens.awaitTopIs(HomeActivity::class.java)
            holdWithoutOnboarding()

            watcher.assertNeverReached(MainIntroActivity::class.java, Stage.RESUMED)
            watcher.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)
        }
    }

    @Test
    @TestId("INTAKE-03")
    fun homeDisabledSendsTheLauncherStraightToTheReaderPicker() {
        seedSettledFirstRun()
        SettingsSeeder.seed(Preferences.homeDisabledKey, true)

        ActivityWatcher().use { watcher ->
            home.sendLauncherIntent()
            watcher.awaitReached(MainActivity::class.java, Stage.RESUMED, Await.LONG_TIMEOUT_MS)
            picker.awaitShown()

            assertTrue(
                "the system documents UI must be in the foreground, not the app",
                DocumentPickerRobot.DOCUMENTS_UI
                    .matcher(TestEnvironment.uiDevice.currentPackageName.orEmpty())
                    .matches(),
            )
            assertTrue(
                "the reader must be the activity standing behind the picker",
                Screens.isInTask(MainActivity::class.java),
            )
            assertFalse(
                "redirectToReaderIfHomeDisabled calls finish(), so home must not be in the task",
                Screens.isInTask(HomeActivity::class.java),
            )
            watcher.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)
        }

        picker.dismissIfShown()
    }

    @Test
    @TestId("INTAKE-49")
    fun backingOutOfThePickerExitsTheReader() {
        seedSettledFirstRun()
        SettingsSeeder.seed(Preferences.homeDisabledKey, true)
        val picked = Fixtures.pickerFixture(IntakeFixtures.NORMAL).name

        ActivityWatcher().use { watcher ->
            home.sendLauncherIntent()
            watcher.awaitReached(MainActivity::class.java, Stage.RESUMED, Await.LONG_TIMEOUT_MS)

            picker.pickFromDownloads(picked)
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitFullQualityRender()

            TestEnvironment.uiDevice.pressBack()
            picker.awaitShown(Await.LONG_TIMEOUT_MS)

            picker.cancel()
            picker.awaitGone()

            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            assertTrue(
                "cancelling the picker it raised must finish the reader, observed ${watcher.observed()}",
                watcher.wasFinishingWhenDestroyed(MainActivity::class.java),
            )
            Await.until("the app has no resumed activity left") { Screens.resumedName() == null }
        }
    }

    private fun seedSettledFirstRun() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.firstInstallKey to false,
                Preferences.showFeaturesDialogKey to false,
                Preferences.showExitFullscreenTipKey to false,
                Preferences.lastSeenVersionCodeKey to BuildConfig.VERSION_CODE,
            )
        )
    }

    private fun holdWithoutOnboarding() {
        Await.assertThroughout(
            condition = "no onboarding screen appears in front of the library",
            windowMs = ONBOARDING_WINDOW_MS,
        ) {
            TestEnvironment.resumedActivityOfType(MainIntroActivity::class.java) == null &&
                TestEnvironment.resumedActivityOfType(WhatsNewActivity::class.java) == null
        }
    }

    private companion object {
        const val ONBOARDING_WINDOW_MS = 2_500L
    }
}
