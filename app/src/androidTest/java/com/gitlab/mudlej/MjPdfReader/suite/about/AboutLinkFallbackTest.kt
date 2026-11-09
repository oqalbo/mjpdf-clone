// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.AboutRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.PackageDisableRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class AboutLinkFallbackTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val packages = PackageDisableRule()

    private val home = HomeRobot()
    private val about = AboutRobot()

    @Test
    @TestId("ABOUT-37")
    fun aLinkRowWithNoBrowserInstalledShowsTheUrlInASnackbar() {
        val since = CrashGate.timestampNow()
        packages.disableHandlersOf(httpProbe())
        assertTrue(
            "a browser is still installed and enabled, the fallback path cannot be reached",
            packages.handlerPackagesOf(httpProbe()).isEmpty(),
        )

        about.openFromHome(home)
        about.openRow(R.id.sourceCodeRow)

        Snackbars.awaitText(AboutDetails.REPO_URL)
        Screens.assertTopIs(AboutActivity::class.java)
        CrashGate.assertNoFatalSince(since)
    }

    @Test
    @TestId("ABOUT-38")
    fun theEmailRowWithNoMailAppShowsTheAddressInASnackbar() {
        val since = CrashGate.timestampNow()
        packages.disableHandlersOf(mailtoProbe())
        assertTrue(
            "a mail composer is still installed and enabled, the fallback path cannot be reached",
            packages.handlerPackagesOf(mailtoProbe()).isEmpty(),
        )

        about.openFromHome(home)
        about.openRow(R.id.emailRow)

        Snackbars.awaitText(AboutDetails.EMAIL_ADDRESS)
        Screens.assertTopIs(AboutActivity::class.java)
        CrashGate.assertNoFatalSince(since)
    }

    private fun httpProbe(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))

    private fun mailtoProbe(): Intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AboutDetails.EMAIL_ADDRESS}"))
}
