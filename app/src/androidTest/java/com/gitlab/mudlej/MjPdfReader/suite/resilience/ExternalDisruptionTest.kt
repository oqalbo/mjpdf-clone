// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.HomeStateSource
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ExternalDisruptionTest : ResilienceTestCase() {

    @After
    fun restoreSimulatedAccess() {
        runCatching { Fixtures.stopSimulatingAccess() }
    }

    @Test
    @TestId("RES-43")
    fun losingStorageAccessWhileRunningDegradesGracefully() {
        val since = CrashGate.timestampNow()
        ActivityScenario.launch<HomeActivity>(Intent(TestEnvironment.targetContext, HomeActivity::class.java)).use {
            Await.until("Home renders its first pass", Await.LONG_TIMEOUT_MS) {
                (HomeStateSource.read()?.generation ?: -1) >= 0
            }
            assertTrue(
                "the app must believe it has storage access before the loss is simulated",
                PermissionManager.hasFullAccess(TestEnvironment.targetContext),
            )

            Fixtures.simulateNoAllFilesAccess()

            TestEnvironment.uiDevice.pressHome()
            Screens.awaitStage(
                HomeActivity::class.java,
                androidx.test.runner.lifecycle.Stage.STOPPED,
                Await.LONG_TIMEOUT_MS,
            )
            TestEnvironment.targetContext.startActivity(
                Intent(TestEnvironment.targetContext, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)

            assertFalse(
                "the recheck in onResume must report the access as gone",
                PermissionManager.hasFullAccess(TestEnvironment.targetContext),
            )
            Await.until("Home renders again after the access loss", Await.LONG_TIMEOUT_MS) {
                HomeStateSource.read() != null
            }
            val state = HomeStateSource.read()
            assertTrue(
                "with no storage access Home must not keep listing files it can no longer read, but it reports " +
                    "${state?.renderedItems} items on tab ${state?.tab}",
                state?.tab != HomeTab.FOLDERS || (state.renderedItems ?: 0) == 0,
            )
            CrashGate.assertNoFatalSince(since)
        }
    }

    @Test
    @TestId("RES-44")
    @TestDuration(DurationCategory.SHORT)
    fun anInterruptingActivityReturnsTheReaderExactlyWhereItWas() {
        val file = fixture(ResilienceFixtures.NORMAL)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        jumpToPage(INTERRUPTED_PAGE)
        enterFullscreen()
        val identityBefore = ReaderProbe.resumedReaderIdentity()

        Shell.execute("am start -a android.settings.SETTINGS")
        Await.until("the system settings app takes the foreground", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.currentPackageName != TestEnvironment.targetPackage
        }

        TestEnvironment.uiDevice.pressBack()
        ReaderProbe.awaitResumedReader()
        val restored = settledFullQuality()

        assertEquals(
            "the interruption must return the reader to the page it was on",
            INTERRUPTED_PAGE as Int?,
            restored.page,
        )
        assertTrue(
            "fullscreen must be restored on resume, so the system bars stay hidden",
            ReaderProbe.systemBarsHidden(),
        )
        assertEquals(
            "no configuration change happened, so the same reader instance must be resumed",
            identityBefore,
            ReaderProbe.resumedReaderIdentity(),
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-48")
    @TestDuration(DurationCategory.SHORT)
    fun aMissingDocumentAddressIsLoggedAsUnknown() {
        val since = ResilienceLogcat.since()

        val intent = Intent(TestEnvironment.targetContext, com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity::class.java)
        ActivityScenario.launch<com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity>(intent).use {
            Await.until("the search screen finishes itself after failing to open the document", Await.LONG_TIMEOUT_MS) {
                !Screens.isInTask(com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity::class.java)
            }
        }

        val line = ResilienceLogcat.awaitLineContaining(since, EXTRACTOR_URI_MARKER, Await.LONG_TIMEOUT_MS)
        assertTrue(
            "a missing document address must be logged as the unknown marker, but the line read \"$line\"",
            line.trimEnd().endsWith(UNKNOWN_URI_SUFFIX),
        )
        assertFalse(
            "no log line may claim the document address is the literal word null",
            ResilienceLogcat.containsSince(since, LITERAL_NULL_URI),
        )
    }

    @Test
    @TestId("RES-49")
    fun theCorruptDocumentFailsCleanlyAndLeavesNothingOpen() {
        val file = fixture(ResilienceFixtures.CORRUPT)
        assertEquals(
            "nothing may hold the corrupt fixture before it is opened",
            0,
            FileDescriptors.countFor(file),
        )

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            Await.until("the file opening error is shown", Await.LONG_TIMEOUT_MS) {
                TestEnvironment.uiDevice.hasObject(
                    By.text(TestEnvironment.targetContext.getString(R.string.file_opening_error))
                )
            }
            ReaderProbe.awaitVisibility(R.id.pickFileButton, android.view.View.VISIBLE, Await.LONG_TIMEOUT_MS)
            FileDescriptors.awaitSettledCountOf(file, 0)
        }

        val uri = android.net.Uri.fromFile(file).toString()
        com.gitlab.mudlej.MjPdfReader.suite.data.Records.assertHolds(
            "no record is written for a document that never opened"
        ) {
            com.gitlab.mudlej.MjPdfReader.suite.data.Records.all().none { it.uri.toString() == uri }
        }
    }

    private fun enterFullscreen() {
        ReaderProbe.onReaderView(R.id.toolbarPrimaryActionOption) { it.performClick() }
        Await.until("the system bars are hidden by fullscreen", Await.LONG_TIMEOUT_MS) {
            runCatching { ReaderProbe.systemBarsHidden() }.getOrDefault(false)
        }
    }

    private companion object {
        const val INTERRUPTED_PAGE = 7
        const val EXTRACTOR_URI_MARKER = "Failed to open PdfExtractor for URI="
        const val UNKNOWN_URI_SUFFIX = "URI=?"
        const val LITERAL_NULL_URI = "URI=null"
    }
}
