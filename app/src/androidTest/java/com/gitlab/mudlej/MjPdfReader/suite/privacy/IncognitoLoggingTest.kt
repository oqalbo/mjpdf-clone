// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.net.LocalPdfServer
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.rules.TestDirectoryRule
import com.gitlab.mudlej.MjPdfReader.suite.security.SecurityLogcat
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class IncognitoLoggingTest {

    private val suite = SuiteRules.standard()
    private val directory = TestDirectoryRule()
    private val server = LocalPdfServer()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(directory).around(server)

    private val reader = PrivacyReader()

    @Test
    @TestId("PRIV-37")
    fun logcatContainsNoFilePathOrUriForAnIncognitoOpen() {
        val distinctive = PrivacyFixtures.copyInto(
            directory.directory(),
            PrivacyFixtures.NORMAL,
            PrivacyFixtures.DISTINCTIVE_NAME,
        )
        val fileUri = Uri.fromFile(distinctive)

        val boundary = CrashGate.timestampNow()

        reader.reader.withExternalIntent(PrivacyReader.incognitoViewIntent(fileUri)) {
            reader.awaitReady()
            reader.awaitIdentity()
            PAGE_STOPS.forEach { reader.jumpToPageIndex(it) }
        }

        val loopUrl = server.serveRedirectTo("/$DISTINCTIVE_SEGMENT", "/$DISTINCTIVE_SEGMENT")
        reader.reader.withExternalIntent(PrivacyReader.incognitoOnlineViewIntent(loopUrl)) {
            reader.reader.acceptCleartextWarning()
            Await.until("the redirect failure is logged", Await.VERY_LONG_TIMEOUT_MS) {
                logcatAfter(boundary).contains(REDACTED_MARKER)
            }
        }

        val droppedUrl = server.serveDisconnect("/$DROPPED_SEGMENT", SocketPolicy.DISCONNECT_AFTER_REQUEST)
        val droppedBoundary = CrashGate.timestampNow()
        reader.reader.withExternalIntent(PrivacyReader.incognitoOnlineViewIntent(droppedUrl)) {
            reader.reader.acceptCleartextWarning()
            Await.until("the server receives the download that it will disconnect") {
                server.wasRequested("/$DROPPED_SEGMENT")
            }
            Snackbars.awaitText(TestEnvironment.appString(R.string.toast_generic_download_error), Await.VERY_LONG_TIMEOUT_MS)
            Await.until("this dropped download logs its redacted failure", Await.LONG_TIMEOUT_MS) {
                val logged = logcatAfter(droppedBoundary)
                logged.contains("Error cannot get file at URL") && logged.contains(REDACTED_HOST) && logged.contains(REDACTED_MARKER)
            }
        }

        reader.reader.withExternalIntent(PrivacyReader.incognitoViewIntent(PrivacyFixtures.LINKS)) {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(UNOPENABLE_LINK_PAGE_INDEX)
            reader.tapPageCentre()
            TestEnvironment.uiDevice.waitForIdle()
        }

        val logcat = logcatAfter(boundary)

        assertFalse(
            "the fixture file name must never reach logcat",
            logcat.contains(PrivacyFixtures.DISTINCTIVE_TITLE),
        )
        assertFalse(
            "the full document URI must never reach logcat",
            logcat.contains(fileUri.toString()),
        )
        assertFalse(
            "the distinctive path segment of the http URL must never reach logcat",
            logcat.contains(DISTINCTIVE_SEGMENT),
        )
        assertFalse(
            "the path segment of the dropped download must never reach logcat either, since the IOException " +
                "branch passes the raw throwable alongside the redacted message",
            logcat.contains(DROPPED_SEGMENT),
        )
        assertTrue(
            "the redirect failure must be logged in the redacted scheme and authority form, so that the " +
                "absence of the other three strings is a redaction rather than a missing log line",
            logcat.contains(REDACTED_MARKER) && logcat.contains(REDACTED_HOST),
        )
    }

    private fun logcatAfter(boundary: String): String =
        SecurityLogcat.linesSince(boundary)
            .filterNot { line -> TEST_SCAFFOLDING_TAGS.any { line.contains(it) } }
            .joinToString("\n")

    private companion object {
        val TEST_SCAFFOLDING_TAGS = listOf("MockWebServer", "okhttp", "System.out", "System.err", "TestRunner")
        const val DISTINCTIVE_SEGMENT = "priv-redirect-marker-qxzv.pdf"
        const val DROPPED_SEGMENT = "priv-dropped-marker-qxzv.pdf"
        const val UNOPENABLE_LINK_PAGE_INDEX = 36
        const val REDACTED_MARKER = "…"
        const val REDACTED_HOST = "http://127.0.0.1"
        val PAGE_STOPS = listOf(1, 2, 3)
    }
}
