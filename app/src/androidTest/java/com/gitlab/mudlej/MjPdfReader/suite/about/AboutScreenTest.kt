// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.AboutRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.about.OpenSourceLibrariesDialog
import com.gitlab.mudlej.MjPdfReader.ui.about.PrivacyInfoDialog
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class AboutScreenTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val about = AboutRobot()

    @Test
    @TestId("ABOUT-29")
    fun aboutOpensFromTheHomeMenu() {
        home.launchFromLauncher()
        home.awaitRendered()
        home.openOverflowMenu()

        onView(allOf(withId(R.id.aboutRow), hasDescendant(withText(string(R.string.action_about)))))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        home.tapMenuRow(R.id.aboutRow)
        Screens.awaitResumed(AboutActivity::class.java)
        about.awaitShown()

        Screens.assertTopIs(AboutActivity::class.java)
        assertEquals("the About action bar title", string(R.string.action_about), Screens.titleOfResumed())

        about.pressUp()
        Screens.awaitTopIs(HomeActivity::class.java)
        home.awaitMenuRowGone(R.id.aboutRow)
    }

    @Test
    @TestId("ABOUT-30")
    fun aboutOpensFromTheReaderMenu() {
        val fixture = pushNormalFixture()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            val reader = ReaderRobot()
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitFullQualityRender()
            val pageBefore = reader.readState().page

            openReaderAbout()
            Screens.awaitResumed(AboutActivity::class.java)
            about.awaitShown()
            about.assertShown()

            about.pressUp()
            Screens.awaitTopIs(MainActivity::class.java)
            reader.awaitShown()

            assertEquals(
                "the reader must come back on the page it was left on",
                pageBefore,
                reader.awaitState("the reader republishes its page") { it.page != null }.page,
            )
        }
    }

    @Test
    @TestId("ABOUT-31")
    fun theVersionChipShowsTheRealBuildVersion() {
        about.openFromHome(home)

        assertEquals("the version chip text", AboutDetails.versionText(), about.versionChipText())

        val info = TestEnvironment.targetContext.packageManager
            .getPackageInfo(TestEnvironment.targetPackage, 0)
        assertEquals(
            "BuildConfig.VERSION_NAME must match the installed package",
            info.versionName,
            BuildConfig.VERSION_NAME,
        )
        assertEquals(
            "BuildConfig.VERSION_CODE must match the installed package",
            info.longVersionCode,
            BuildConfig.VERSION_CODE.toLong(),
        )
    }

    @Test
    @TestId("ABOUT-32")
    fun tappingTheVersionChipCopiesTheVersionToTheClipboard() {
        IntentStubs.clearClipboard()
        about.openFromHome(home)

        about.tapVersionChip()

        Snackbars.awaitText(string(R.string.copied))
        assertEquals("the copied clip text", AboutDetails.versionText(), IntentStubs.clipboardText())
        assertEquals("the clip description label", string(R.string.mj_app_name), IntentStubs.clipboardLabel())
    }

    @Test
    @TestId("ABOUT-39")
    @TestDuration(DurationCategory.NORMAL)
    fun thePrivacyRowOpensTheDialogAndSurvivesRotation() {
        about.openFromHome(home)

        about.openRow(R.id.privacyRow)
        Dialogs.awaitShown(PrivacyInfoDialog.TAG)

        onView(withId(androidx.appcompat.R.id.alertTitle)).check(matches(withText(string(R.string.privacy))))
        assertEquals(
            "the privacy dialog body",
            TestEnvironment.targetContext.getText(R.string.privacy_info).toString(),
            Dialogs.textOf(PrivacyInfoDialog.TAG, android.R.id.message),
        )

        Rotation.toLandscape(AboutActivity::class.java)
        assertSingleLiveDialog(PrivacyInfoDialog.TAG)

        Rotation.toPortrait(AboutActivity::class.java)
        assertSingleLiveDialog(PrivacyInfoDialog.TAG)

        Dialogs.tapPositive()
        Dialogs.awaitGone(PrivacyInfoDialog.TAG)
        Screens.assertTopIs(AboutActivity::class.java)
    }

    @Test
    @TestId("ABOUT-40")
    fun theLibrariesRowListsAllFiveAttributions() {
        about.openFromHome(home)

        about.openRow(R.id.librariesRow)
        Dialogs.awaitShown(OpenSourceLibrariesDialog.TAG)

        onView(withId(androidx.appcompat.R.id.alertTitle)).check(matches(withText(string(R.string.libs))))

        val message = Dialogs.textOf(OpenSourceLibrariesDialog.TAG, android.R.id.message).orEmpty()
        for (library in LIBRARY_NAMES) {
            assertTrue("the attribution for \"$library\" is missing from: $message", library in message)
        }
        assertEquals(
            "four libraries must be credited under the Apache License, read: $message",
            4,
            occurrencesOf("License: Apache License", message),
        )
        assertEquals(
            "one library must be credited under the SIL Open Font licence, read: $message",
            1,
            occurrencesOf("License: SIL Open Font", message),
        )

        Rotation.toLandscape(AboutActivity::class.java)
        assertSingleLiveDialog(OpenSourceLibrariesDialog.TAG)

        Dialogs.tapPositive()
        Dialogs.awaitGone(OpenSourceLibrariesDialog.TAG)
        Screens.assertTopIs(AboutActivity::class.java)
    }

    @Test
    @TestId("ABOUT-42")
    @TestDuration(DurationCategory.NORMAL)
    fun theAboutUpArrowReturnsToTheScreenThatOpenedIt() {
        ActivityWatcher().use { watcher ->
            about.openFromHome(home)
            about.pressUp()
            Screens.awaitTopIs(HomeActivity::class.java)
            Screens.awaitGone(AboutActivity::class.java)

            home.openOverflowMenu()
            home.tapMenuRow(R.id.aboutRow)
            Screens.awaitResumed(AboutActivity::class.java)
            about.awaitShown()

            about.pressBack()
            Screens.awaitTopIs(HomeActivity::class.java)
            Screens.awaitGone(AboutActivity::class.java)

            watcher.assertNeverReached(MainActivity::class.java, Stage.RESUMED)
        }
    }

    @Test
    @TestId("ABOUT-43")
    fun rotationOnAboutKeepsTheFullRowListAndSubtitles() {
        about.openFromHome(home)

        assertAboutStructure()

        Rotation.toLandscape(AboutActivity::class.java)
        about.awaitShown()
        assertAboutStructure()

        Rotation.toPortrait(AboutActivity::class.java)
        about.awaitShown()
        assertAboutStructure()
    }

    private fun assertAboutStructure() {
        assertEquals(
            "the eleven About row titles",
            AboutRobot.expectedRowTitles(),
            AboutRobot.ROW_IDS.map { about.rowTitle(it) },
        )
        assertEquals("the Website row subtitle", AboutDetails.AUTHOR_SITE_NAME, about.rowSubtitle(R.id.websiteRow))
        assertEquals("the Email row subtitle", AboutDetails.EMAIL_ADDRESS, about.rowSubtitle(R.id.emailRow))

        val leaked = AboutRobot.ROW_IDS
            .filterNot { it in AboutRobot.SUBTITLED_ROW_IDS }
            .filter { about.rowSubtitleVisibility(it) != View.GONE }
        assertTrue(
            "these rows must have no subtitle but one is visible: ${leaked.map { resourceName(it) }}",
            leaked.isEmpty(),
        )

        assertEquals("the version chip text", AboutDetails.versionText(), about.versionChipText())
    }

    private fun assertSingleLiveDialog(tag: String) {
        assertEquals("exactly one fragment tagged $tag must exist", 1, Dialogs.countWithTag(tag))
        assertTrue("the dialog tagged $tag must still be showing", Dialogs.isShowing(tag))
    }

    private fun openReaderAbout() {
        onView(withId(R.id.readerActionsOption)).perform(click())
        Await.untilNoThrow("the reader actions dialog offers About") {
            onView(withContentDescription(string(R.string.action_about)))
                .perform(scrollTo(), click())
        }
    }

    private fun pushNormalFixture(): File {
        Fixtures.ensureAllFilesAccess()
        return Fixtures.pushAsset(FIXTURE_NAME, rules.testDirectory.fileNamed(FIXTURE_NAME))
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun resourceName(viewId: Int): String =
        TestEnvironment.targetContext.resources.getResourceEntryName(viewId)

    private fun occurrencesOf(needle: String, haystack: String): Int =
        Regex(Regex.escape(needle)).findAll(haystack).count()

    private companion object {

        const val FIXTURE_NAME = "normal.pdf"

        val LIBRARY_NAMES = listOf(
            "MJ PDF's fork of Android PdfViewer",
            "MJ PDF's fork of PdfiumAndroid",
            "Android Open Source Project",
            "AndroidX Libraries",
            "Material Design Icons",
        )
    }
}
