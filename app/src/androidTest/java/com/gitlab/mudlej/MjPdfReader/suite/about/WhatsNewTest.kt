// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.AboutRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.WhatsNewRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class WhatsNewTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val about = AboutRobot()
    private val whatsNew = WhatsNewRobot()

    @Test
    @TestId("ABOUT-20")
    fun whatsNewAppearsAfterAVersionCodeIncrease() {
        Onboarding.seedSettled(lastSeenVersionCode = BuildConfig.VERSION_CODE - 1)

        ActivityWatcher().use { watcher ->
            home.startFromLauncher()
            Screens.awaitResumed(WhatsNewActivity::class.java, Await.LONG_TIMEOUT_MS)
            whatsNew.awaitShown()

            watcher.assertNeverReached(MainIntroActivity::class.java, Stage.RESUMED)
        }

        assertEquals(
            "the seen version code must be advanced to the installed one",
            BuildConfig.VERSION_CODE,
            Onboarding.lastSeenVersionCode(),
        )
    }

    @Test
    @TestId("ABOUT-21")
    @TestDuration(DurationCategory.NORMAL)
    fun whatsNewDoesNotReappearAfterTheUpgradeShowing() {
        Onboarding.seedSettled(lastSeenVersionCode = BuildConfig.VERSION_CODE - 1)

        home.startFromLauncher()
        Screens.awaitResumed(WhatsNewActivity::class.java, Await.LONG_TIMEOUT_MS)
        whatsNew.awaitShown()
        whatsNew.pressUp()
        Screens.awaitTopIs(HomeActivity::class.java)

        ActivityWatcher().use { watcher ->
            home.launchFromLauncher()
            home.awaitRendered()
            Onboarding.assertNothingResumedFor("no release notes screen appears") {
                TestEnvironment.resumedActivityOfType(WhatsNewActivity::class.java) != null
            }

            watcher.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)
        }

        assertEquals(
            "the seen version code must stay at the installed one",
            BuildConfig.VERSION_CODE,
            Onboarding.lastSeenVersionCode(),
        )
    }

    @Test
    @TestId("ABOUT-22")
    fun aSameVersionLaunchDoesNotShowWhatsNew() {
        Onboarding.seedSettled(lastSeenVersionCode = BuildConfig.VERSION_CODE)

        ActivityWatcher().use { watcher ->
            home.launchFromLauncher()
            home.awaitRendered()
            Onboarding.assertNothingResumedFor("no release notes screen appears") {
                TestEnvironment.resumedActivityOfType(WhatsNewActivity::class.java) != null
            }

            watcher.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)
            watcher.assertNeverReached(MainIntroActivity::class.java, Stage.RESUMED)
        }
    }

    @Test
    @TestId("ABOUT-23")
    fun aDowngradeDoesNotShowWhatsNew() {
        val seeded = BuildConfig.VERSION_CODE + 10
        Onboarding.seedSettled(lastSeenVersionCode = seeded)

        ActivityWatcher().use { watcher ->
            home.launchFromLauncher()
            home.awaitRendered()
            Onboarding.assertNothingResumedFor("no release notes screen appears") {
                TestEnvironment.resumedActivityOfType(WhatsNewActivity::class.java) != null
            }

            watcher.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)
        }

        assertEquals(
            "consumeWhatsNewDue must return before writing on a downgrade",
            seeded,
            Onboarding.lastSeenVersionCode(),
        )
    }

    @Test
    @TestId("ABOUT-24")
    fun upgradeWithHomeDisabledShowsWhatsNewOverTheReader() {
        Onboarding.seedSettled(lastSeenVersionCode = BuildConfig.VERSION_CODE - 1)
        SettingsSeeder.seed(Preferences.homeDisabledKey, true)

        home.sendLauncherIntent()
        Screens.awaitResumed(WhatsNewActivity::class.java, Await.LONG_TIMEOUT_MS)
        whatsNew.awaitShown()

        assertEquals(
            "the redirect path must consume the release notes too",
            BuildConfig.VERSION_CODE,
            Onboarding.lastSeenVersionCode(),
        )
        Screens.awaitGone(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
        assertFalse(
            "home calls finish() on the redirect so it must not be left in the task",
            Screens.isInTask(HomeActivity::class.java),
        )

        whatsNew.pressUp()
        Screens.awaitGone(WhatsNewActivity::class.java)

        assertTrue(
            "the reader was started with no data, so its document picker is what the user now sees",
            awaitDocumentPicker(),
        )
        assertTrue(
            "the reader must be what is left standing behind the release notes",
            Screens.isInTask(MainActivity::class.java),
        )
        Screens.awaitGone(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
        assertFalse(
            "home must still be absent from the task once the release notes are dismissed",
            Screens.isInTask(HomeActivity::class.java),
        )

        dismissDocumentPickerIfShown()
    }

    @Test
    @TestId("ABOUT-25")
    fun homeDisabledAndNoUpgradeGoesStraightToTheReader() {
        Onboarding.seedSettled(lastSeenVersionCode = BuildConfig.VERSION_CODE)
        SettingsSeeder.seed(Preferences.homeDisabledKey, true)

        ActivityWatcher().use { watcher ->
            home.sendLauncherIntent()
            watcher.awaitReached(MainActivity::class.java, Stage.RESUMED, Await.LONG_TIMEOUT_MS)

            assertTrue(
                "the reader was started with no data so the system document picker must be on screen",
                awaitDocumentPicker(),
            )
            assertTrue(
                "the reader must be the activity underneath the picker",
                Screens.isInTask(MainActivity::class.java),
            )
            watcher.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)
        }

        dismissDocumentPickerIfShown()
    }

    @Test
    @TestId("ABOUT-26")
    fun whatsNewShowsTheCurrentVersionAndItsFullChangeList() {
        openWhatsNewFromAbout()

        assertEquals(
            "the What's New version chip carries no build suffix",
            "Version ${BuildConfig.VERSION_NAME}",
            whatsNew.versionChipText(),
        )

        assertTrue("the intro card must be displayed", whatsNew.isIntroCardDisplayed())
        val titleBeforeTap = Screens.resumedName()
        whatsNew.tapIntroCard()
        assertEquals("tapping the intro card must do nothing", titleBeforeTap, Screens.resumedName())

        whatsNew.scrollToBottom()

        assertEquals(
            "the What's New section titles",
            SECTION_TITLE_RES.map { string(it) },
            whatsNew.sectionTitles(),
        )
        assertEquals("the rows per section", listOf(4, 2, 3, 2), whatsNew.rowCountsPerSection())

        val rows = whatsNew.rows()
        assertEquals("the total number of change rows", 11, rows.size)
        rows.forEachIndexed { index, (title, body) ->
            assertTrue("change row $index has an empty title", title.isNotBlank())
            assertTrue("change row $index has an empty body", body.isNotBlank())
        }
    }

    @Test
    @TestId("ABOUT-27")
    @TestDuration(DurationCategory.NORMAL)
    fun rotationOnWhatsNewDoesNotDuplicateTheChangeList() {
        openWhatsNewFromAbout()
        assertChangeListShape()

        Rotation.toLandscape(WhatsNewActivity::class.java)
        whatsNew.awaitShown()
        assertChangeListShape()

        Rotation.toPortrait(WhatsNewActivity::class.java)
        whatsNew.awaitShown()
        assertChangeListShape()
    }

    @Test
    @TestId("ABOUT-28")
    @TestDuration(DurationCategory.NORMAL)
    fun theWhatsNewUpArrowReturnsToAbout() {
        openWhatsNewFromAbout()
        whatsNew.pressUp()
        Screens.awaitTopIs(AboutActivity::class.java)
        Screens.awaitGone(WhatsNewActivity::class.java)

        openWhatsNewFromAbout(reachAbout = false)
        whatsNew.pressBack()
        Screens.awaitTopIs(AboutActivity::class.java)
        Screens.awaitGone(WhatsNewActivity::class.java)
    }

    private fun assertChangeListShape() {
        assertEquals("the number of sections", 4, whatsNew.sectionCount())
        assertEquals("the total number of change rows", 11, whatsNew.rows().size)
        assertEquals(
            "the What's New version chip",
            "Version ${BuildConfig.VERSION_NAME}",
            whatsNew.versionChipText(),
        )
    }

    private fun openWhatsNewFromAbout(reachAbout: Boolean = true) {
        if (reachAbout) {
            about.openFromHome(home)
        } else {
            about.awaitShown()
        }
        about.openRow(R.id.whatsNewRow)
        Screens.awaitResumed(WhatsNewActivity::class.java)
        whatsNew.awaitShown()
    }

    private fun awaitDocumentPicker(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Boolean =
        TestEnvironment.uiDevice.wait(Until.hasObject(By.pkg(DOCUMENTS_UI).depth(0)), timeoutMs) == true

    private fun dismissDocumentPickerIfShown() {
        if (TestEnvironment.uiDevice.hasObject(By.pkg(DOCUMENTS_UI).depth(0))) {
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.uiDevice.waitForIdle()
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {

        val DOCUMENTS_UI: Pattern = Pattern.compile(".*\\.documentsui")

        val SECTION_TITLE_RES = listOf(
            R.string.whats_new_section_reading,
            R.string.whats_new_section_search,
            R.string.whats_new_section_saving,
            R.string.whats_new_section_performance,
        )
    }
}
