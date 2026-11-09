// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.view.View
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.AboutRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.intent.VerificationModes.times

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class AboutLinksTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val intents = IntentsRule()

    private val home = HomeRobot()
    private val about = AboutRobot()

    @Test
    @TestId("ABOUT-33")
    fun theFeaturedCardOpensTheOfficialSite() {
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)
        about.openFromHome(home)

        about.tapFeaturedCard()

        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(OFFICIAL_SITE_URL)), times(1))
        Snackbars.assertNoneWithin()
    }

    @Test
    @TestId("ABOUT-34")
    @TestDuration(DurationCategory.SHORT)
    fun theAppInfoLinkRowsOpenTheirExactUrls() {
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)
        about.openFromHome(home)

        assertEquals("the License row title", string(R.string.myLicense), about.rowTitle(R.id.licenseRow))
        assertEquals("the Source Code row title", string(R.string.code), about.rowTitle(R.id.sourceCodeRow))
        assertEquals(
            "the License row must show no subtitle",
            View.GONE,
            about.rowSubtitleVisibility(R.id.licenseRow),
        )
        assertEquals(
            "the Source Code row must show no subtitle",
            View.GONE,
            about.rowSubtitleVisibility(R.id.sourceCodeRow),
        )

        about.openRow(R.id.licenseRow)
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(LICENSE_URL)), times(1))

        about.openRow(R.id.sourceCodeRow)
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(AboutDetails.REPO_URL)), times(1))
    }

    @Test
    @TestId("ABOUT-35")
    fun theAuthorInfoLinkRowsOpenTheirExactUrls() {
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)
        about.openFromHome(home)

        about.scrollToRow(R.id.websiteRow)
        assertEquals(
            "the Website row subtitle must be visible",
            View.VISIBLE,
            about.rowSubtitleVisibility(R.id.websiteRow),
        )
        assertEquals("the Website row subtitle", AboutDetails.AUTHOR_SITE_NAME, about.rowSubtitle(R.id.websiteRow))

        about.openRow(R.id.websiteRow)
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(AUTHOR_SITE_URL)), times(1))

        about.openRow(R.id.gitlabRow)
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(GITLAB_URL)), times(1))

        about.openRow(R.id.githubRow)
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(GITHUB_URL)), times(1))
    }

    @Test
    @TestId("ABOUT-36")
    @TestDuration(DurationCategory.SHORT)
    fun theEmailRowOpensAPreFilledMailComposer() {
        IntentStubs.stubOkFor(Intent.ACTION_SENDTO)
        about.openFromHome(home)

        about.scrollToRow(R.id.emailRow)
        assertEquals("the Email row subtitle", AboutDetails.EMAIL_ADDRESS, about.rowSubtitle(R.id.emailRow))

        about.openRow(R.id.emailRow)

        intended(
            allOf(
                hasAction(Intent.ACTION_SENDTO),
                hasData("mailto:${AboutDetails.EMAIL_ADDRESS}"),
                hasExtra(Intent.EXTRA_SUBJECT, string(R.string.mj_app_name)),
                hasExtra(Intent.EXTRA_TEXT, AboutDetails.versionText()),
            ),
            times(1),
        )
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val OFFICIAL_SITE_URL = "https://mudlej.com/projects/mj-pdf/"
        const val AUTHOR_SITE_URL = "https://mudlej.com"
        const val GITLAB_URL = "https://gitlab.com/mudlej"
        const val GITHUB_URL = "https://github.com/mudlej"
        const val LICENSE_URL = "https://gitlab.com/mudlej_android/mj_pdf_reader/-/blob/main/LICENSE"
    }
}
