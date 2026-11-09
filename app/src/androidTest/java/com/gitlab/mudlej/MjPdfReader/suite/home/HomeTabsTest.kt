// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.res.Configuration
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.controls.PdfThemeController
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HomeTabsTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-01")
    fun libraryIsTheOpeningTab() {
        HomeBaselines.cleanHome()

        home.launchFromLauncher()
        home.awaitRendered()

        assertEquals(
            "the tab strip",
            listOf(
                TestEnvironment.targetString(R.string.home_tab_recent),
                TestEnvironment.targetString(R.string.home_tab_library),
                TestEnvironment.targetString(R.string.home_tab_folders),
            ),
            home.tabTexts(),
        )
        assertEquals("the opening pager position", HomeTab.LIBRARY.ordinal, home.currentPagerItem())
        assertNull(
            "opening on the Library default must not write a stored tab, only a tab the user picks is stored",
            HomePrefs.homeTab(),
        )
    }

    @Test
    @TestId("HOME-02")
    fun theChosenTabIsRememberedAcrossARestart() {
        HomeBaselines.cleanHome()

        home.launchFromLauncher()
        home.awaitRendered()
        home.selectTab(HomeTab.FOLDERS)

        HomePrefs.awaitString(Preferences.homeTabKey, HomeTab.FOLDERS.name)

        home.restartHome()

        assertEquals("the reopened pager position", HomeTab.FOLDERS.ordinal, home.currentPagerItem())
        assertEquals("the selected tab", HomeTab.FOLDERS.ordinal, home.selectedTabIndex())
    }

    @Test
    @TestId("HOME-03")
    fun recentTabEmptyState() {
        HomeBaselines.cleanHome()

        home.launchFromLauncher()
        home.awaitRendered()
        val recent = home.selectTab(HomeTab.RECENT)

        val empty = recent.awaitEmptyState(R.string.home_empty_recent_title)
        assertEquals(
            "the recent empty state message",
            R.string.home_empty_recent_message,
            empty.messageRes,
        )
        assertEquals("rows on an empty Recent tab", 0, recent.itemCount())
        assertTrue("an empty Recent tab must show no carousel", !recent.hasHero())
    }

    @Test
    @TestId("HOME-04")
    fun libraryTabWithoutStorageAccessShowsThePermissionCardAndTheEmptyState() {
        HomeBaselines.cleanHome()

        home.launchFromLauncher()
        val library = home.library()
        home.awaitRendered()

        val card = library.permissionCard()
        assertNotNull("the Library tab must offer the permission card without a storage grant", card)
        assertEquals(
            "the permission card message",
            R.string.home_permission_card_message,
            card!!.messageRes,
        )

        onView(allOf(withId(R.id.grantAccessButton), isDisplayed())).check(matches(isEnabled()))
        assertTrue("the status chip row must be present", library.hasChipRow())
        onView(allOf(withId(R.id.chipAllFiles), isDisplayed())).check(matches(isChecked()))

        library.awaitEmptyState(R.string.home_empty_all_title)
        assertTrue("no scan setup card is due without a storage grant", !library.hasScanSetupCard())
    }

    @Test
    @TestId("HOME-05")
    fun scanSetupCardReplacesThePermissionCardOnceAccessIsGranted() {
        HomeBaselines.cleanHome()
        HomeBaselines.grantAccess()

        home.launchFromLauncher()
        val library = home.library()

        home.awaitRendered()

        Await.until("the scan setup card replaces the permission card") { library.hasScanSetupCard() }
        assertNull(
            "the permission card must be gone once access is granted",
            library.permissionCard(),
        )
        assertNull(
            "the empty state stays hidden while the scan setup card shows",
            library.emptyState(),
        )
    }

    @Test
    @TestId("HOME-06")
    fun foldersTabEmptyState() {
        HomeBaselines.cleanHome()

        home.launchFromLauncher()
        home.awaitRendered()
        val folders = home.selectTab(HomeTab.FOLDERS)

        folders.awaitEmptyState(R.string.home_empty_all_title)
        assertEquals("breadcrumbs over an empty index", emptyList<String>(), folders.crumbs())
        assertEquals("folder rows over an empty index", emptyList<String>(), folders.folderNames())
        assertEquals("file rows over an empty index", 0, folders.itemCount())
    }

    @Test
    @TestId("HOME-07")
    fun disablingHomeFinishesItAndRedirectsToTheReader() {
        HomeBaselines.cleanHome()
        SettingsSeeder.seed(Preferences.homeDisabledKey, true)

        ActivityWatcher().use { watcher ->
            home.sendLauncherIntent()
            watcher.awaitReached(HomeActivity::class.java, Stage.DESTROYED, Await.LONG_TIMEOUT_MS)

            assertTrue(
                "HomeActivity must finish itself when the home screen is turned off, observed ${watcher.observed()}",
                watcher.wasFinishingWhenDestroyed(HomeActivity::class.java),
            )
            watcher.assertNeverReached(HomeActivity::class.java, Stage.RESUMED)
            watcher.awaitReached(MainActivity::class.java, Stage.RESUMED, Await.LONG_TIMEOUT_MS)
        }

        closeRedirectTarget()
    }

    @Test
    @TestId("HOME-75")
    fun theDarkInterfaceThemeResolvesOnHome() {
        HomeBaselines.cleanHome()
        SettingsSeeder.seed(Preferences.interfaceThemeKey, Preferences.themeDark)
        AppCompatDelegate.setDefaultNightMode(
            PdfThemeController.interfaceNightMode(Preferences(SettingsSeeder.preferences))
        )

        home.launchFromLauncher()
        home.awaitRendered()
        val activity = Screens.awaitResumed(HomeActivity::class.java)

        assertEquals(
            "Home's resources carry the night configuration",
            Configuration.UI_MODE_NIGHT_YES,
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
        )
        val lightStatusBar = TestEnvironment.onMainThread {
            val value = TypedValue()
            if (!activity.theme.resolveAttribute(android.R.attr.windowLightStatusBar, value, true)) {
                throw AssertionError("Home's theme does not resolve android:windowLightStatusBar")
            }
            value.data != 0
        }
        assertFalse("dark Home chrome must use light status-bar icons", lightStatusBar)
    }

    private fun closeRedirectTarget() {
        if (!Screens.isInTask(MainActivity::class.java)) {
            return
        }
        TestEnvironment.onMainThread {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.entries
                .flatMap { stage -> monitor.getActivitiesInStage(stage) }
                .filterIsInstance<MainActivity>()
                .distinct()
                .forEach { it.finishAndRemoveTask() }
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }
}
