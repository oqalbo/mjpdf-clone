// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupCleanupRule
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupDialog
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupFixtures
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupPage
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupStrings
import com.gitlab.mudlej.MjPdfReader.suite.backup.ImportSignals
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.AboutRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.IntroRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.WhatsNewRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class OnboardingRestoreTest {

    private val suite = SuiteRules.standard()
    private val backupCleanup = BackupCleanupRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(backupCleanup)

    private val home = HomeRobot()
    private val intro = IntroRobot()
    private val about = AboutRobot()
    private val whatsNew = WhatsNewRobot()

    @Test
    @TestId("ABOUT-54")
    fun restoringABackupDoesNotRetriggerOnboarding() {
        Onboarding.seedSettled()
        val directory = suite.testDirectory.directory()
        val fixture = writeOnboardingBackup(directory)
        assertTrue("the crafted backup fixture was not written to ${fixture.absolutePath}", fixture.isFile)

        val outcome = BackupPage.launch().use {
            ImportSignals.awaitImport {
                BackupPage.startImport()
                BackupFixtures.openInDirectory(directory, FIXTURE_NAME)
            }
        }
        assertEquals(
            "the crafted backup must be accepted, or the excluded key filter is never reached",
            ImportSignals.OUTCOME_IMPORTED,
            outcome,
        )

        assertFalse(
            "a restore that rewrote firstInstall would replay the whole intro on a settled install",
            Onboarding.firstInstall(),
        )
        assertFalse(
            "a restore that rewrote showFeaturesDialog would replay the release notes",
            Onboarding.showFeaturesDialog(),
        )
        assertEquals(
            "a restore that rewrote lastSeenVersionCode would make this install look like an upgrade",
            BuildConfig.VERSION_CODE,
            Onboarding.lastSeenVersionCode(),
        )
        assertTrue(
            "the three onboarding keys must survive the wipe that precedes an import, not merely be absent",
            Onboarding.contains(Preferences.firstInstallKey) &&
                Onboarding.contains(Preferences.showFeaturesDialogKey) &&
                Onboarding.contains(Preferences.lastSeenVersionCodeKey),
        )

        Onboarding.finishAppTask()

        ActivityWatcher().use { relaunch ->
            home.startFromLauncher(Await.LONG_TIMEOUT_MS)
            Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)

            BackupDialog.awaitTitle(BackupStrings.importTitle, Await.LONG_TIMEOUT_MS)
            val summary = Await.untilNotNull("the import summary dialog carries a message") {
                BackupDialog.message()
            }
            assertTrue(
                "the summary omits the finished line, it read \"$summary\"",
                summary.contains(BackupStrings.importFinished),
            )
            assertTrue(
                "the three onboarding keys are dropped by BackupManager.excludedSettingKeys before the skip " +
                    "counter is reached, so the import applies none of them and counts none of them as " +
                    "skipped. The summary read \"$summary\"",
                summary.contains(BackupStrings.importedSettings(0)),
            )
            assertFalse(
                "an excluded key must not be reported as a skipped setting, the summary read \"$summary\"",
                summary.contains(BackupStrings.skippedSettings(SETTING_COUNT)),
            )
            BackupDialog.dismissWithOk()

            home.awaitRendered(Await.LONG_TIMEOUT_MS)
            Onboarding.assertNothingResumedFor("neither the intro nor the release notes appear after a restore") {
                TestEnvironment.resumedActivityOfType(MainIntroActivity::class.java) != null ||
                    TestEnvironment.resumedActivityOfType(WhatsNewActivity::class.java) != null
            }

            relaunch.assertNeverReached(MainIntroActivity::class.java, Stage.RESUMED)
            relaunch.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)

            openAbout()
            about.openRow(R.id.whatsNewRow)
            Screens.awaitResumed(WhatsNewActivity::class.java)
            whatsNew.awaitShown()
            assertTrue(
                "the watcher must be able to see the release notes resume, or its silence above means nothing",
                relaunch.reached(WhatsNewActivity::class.java, Stage.RESUMED),
            )
            whatsNew.pressUp()
            Screens.awaitTopIs(AboutActivity::class.java)

            about.openRow(R.id.replayIntroRow)
            Screens.awaitResumed(MainIntroActivity::class.java)
            intro.awaitShown()
            assertTrue(
                "the watcher must be able to see the intro resume, or its silence above means nothing",
                relaunch.reached(MainIntroActivity::class.java, Stage.RESUMED),
            )
            intro.tapSkip()
            Screens.awaitTopIs(AboutActivity::class.java)
        }
    }

    private fun openAbout() {
        home.openOverflowMenu()
        home.tapMenuRow(R.id.aboutRow)
        Screens.awaitResumed(AboutActivity::class.java)
        about.awaitShown()
    }

    private fun writeOnboardingBackup(directory: File): File {
        val fixture = BackupFixtures.writeInto(directory, FIXTURE_NAME, FIXTURE_JSON)
        Fixtures.scanAndWait(listOf(fixture))
        return fixture
    }

    private companion object {

        const val FIXTURE_NAME = "onboarding-keys.json"
        const val SETTING_COUNT = 3

        val FIXTURE_JSON = """
            {
              "appVersionCode": 1,
              "exportedAt": "2026-01-15T09:30:00Z",
              "schemaVersion": 1,
              "settings": [
                {
                  "key": "firstInstall",
                  "type": "boolean",
                  "value": "true"
                },
                {
                  "key": "lastSeenVersionCode",
                  "type": "int",
                  "value": "1"
                },
                {
                  "key": "showFeaturesDialog",
                  "type": "boolean",
                  "value": "true"
                }
              ]
            }
        """.trimIndent()
    }
}
