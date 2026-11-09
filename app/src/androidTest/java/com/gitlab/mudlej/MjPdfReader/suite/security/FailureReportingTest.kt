// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeReader
import com.gitlab.mudlej.MjPdfReader.suite.intake.StubSharer
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FailureReportingTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @After
    fun clearStagedShares() {
        HostileNames.clear()
        IntakeReader.deleteDurableCopies()
    }

    @Test
    @TestId("SEC-59")
    fun aFailedOpenReportsTheLocalizedErrorPath() {
        StubSharer.requireInstalled()
        StubSharer.clear()
        val since = SecurityLogcat.mark()

        ActivityScenario.launch<MainActivity>(
            StubSharer.ungrantedViewIntent(StubSharerProvider.uriFor(UNSERVED_NAME))
        ).use {
            reader.awaitState(
                "opening a URI the provider never published reports a failed load. This is the positive " +
                    "control: without a load that actually failed, the localized error assertion below could " +
                    "pass without exercising the exception path",
                Await.LONG_TIMEOUT_MS,
            ) { it.loadState == DocumentLoadState.FAILED }
            TestEnvironment.uiDevice.pressBack()
        }

        SecurityLogcat.awaitLineContaining(string(R.string.error_load_file_name), since)
    }

    @Test
    @TestId("SEC-60")
    fun aFailedLinkTapTellsTheUser() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()
            SecurityReader.jumpToPage(reader, SecurityFixtures.LINK_UNHANDLED_PAGE_INDEX)
            reader.awaitRenderIdle()
            SecurityReader.tapRect(
                reader,
                SecurityFixtures.LINK_SCHEME_RECTS[SecurityFixtures.LINK_UNHANDLED_PAGE_INDEX],
            )
            TestEnvironment.uiDevice.waitForIdle()

            val toldTheUser = runCatching {
                Await.until(
                    "a failed link tap tells the user something",
                    Await.DEFAULT_TIMEOUT_MS,
                ) {
                    Snackbars.currentText() != null ||
                        TestEnvironment.uiDevice.hasObject(By.text(string(R.string.no_app_to_open_link)))
                }
            }.isSuccess

            assertTrue(
                "a tap on a link no app can open must tell the user, but no snackbar or toast appeared",
                toldTheUser,
            )
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val UNSERVED_NAME = "mjsec59-unserved.pdf"
    }
}
