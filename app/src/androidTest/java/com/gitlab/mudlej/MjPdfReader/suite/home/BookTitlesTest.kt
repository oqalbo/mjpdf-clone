// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class BookTitlesTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-13")
    fun documentTitleReplacesTheFileNameWhenTheSettingIsOn() {
        val library = HomeBaselines.recordsOnly(
            BookSeed(
                HomeFixtures.FILENAME_DIFFERS,
                documentTitle = META_TITLE,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        val recent = home.selectTab(HomeTab.RECENT)

        recent.awaitTitles(listOf(META_TITLE))
        assertEquals(
            "the stored file name is untouched by what the row displays",
            "filename-differs",
            HomeLibrary.requireRecord(library.hashOf("filename-differs")).fileName,
        )
    }

    @Test
    @TestId("HOME-14")
    fun turningOffShowPdfTitleFallsBackToTheFileName() {
        SettingsSeeder.seed(Preferences.homeShowPdfTitleKey, false)
        HomeBaselines.recordsOnly(
            BookSeed(
                HomeFixtures.FILENAME_DIFFERS,
                documentTitle = META_TITLE,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        val recent = home.selectTab(HomeTab.RECENT)

        recent.awaitTitles(listOf("filename-differs"))
    }

    @Test
    @TestId("HOME-15")
    fun anAnnotatedCopyIsLabelledAsSuch() {
        HomeBaselines.recordsOnly(
            BookSeed(
                HomeFixtures.REPORT_ANNOTATED,
                documentTitle = REPORT_TITLE,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        val recent = home.selectTab(HomeTab.RECENT)

        val expected = TestEnvironment.targetContext
            .getString(R.string.home_title_annotated, REPORT_TITLE)
        recent.awaitTitles(listOf(expected))
    }

    private companion object {
        const val META_TITLE = "Meta Title Document"
        const val REPORT_TITLE = "Quarterly Report"
    }
}
