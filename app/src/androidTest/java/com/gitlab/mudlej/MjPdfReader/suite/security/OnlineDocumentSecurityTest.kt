// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeReader
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class OnlineDocumentSecurityTest {
    private val suite = SuiteRules.standard()
    private val server = SecurityServer()
    private val downloads = com.gitlab.mudlej.MjPdfReader.suite.intake.DownloadsGuardRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(downloads).around(server)

    private val reader = ReaderRobot()
    private val online = OnlineDocumentRobot()
    private var downloadsBefore: Set<String>? = null

    @Before
    fun rememberDownloads() {
        downloadsBefore = downloadsListing().toSet()
    }

    @After
    fun sweepTheOnlineCache() {
        runCatching { IntakeReader.cacheDirectory(IntakeReader.ONLINE_CACHE_DIRECTORY).deleteRecursively() }
        val before = downloadsBefore ?: return
        downloadsDirectory().listFiles().orEmpty()
            .filter { it.isFile && it.name !in before }
            .forEach { Fixtures.delete(it) }
    }

    @Test
    @TestId("SEC-49")
    fun anHttpOnlineDocumentWarnsBeforeDownloading() {
        val url = server.servePdf(PATH, SecurityFixtures.file(SecurityFixtures.NORMAL))

        openReader {
            online.open()
            online.enterUrl(url)
            online.pressOpen()

            assertTrue("the dialog must stay open until the warning is acknowledged", online.isShown())
            online.awaitErrorText(string(R.string.http_online_pdf_warning))
            online.awaitButtonLabel(string(R.string.proceed_anyway))
            server.assertRequestLogEmpty("a cleartext link that has only been offered once")

            online.pressOpen()
            online.awaitDismissed()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            server.awaitRequestCount(1)
        }
    }

    @Test
    @TestId("SEC-50")
    @TestDuration(DurationCategory.LONG)
    fun anHttpUrlArrivingByIntentIsNotDownloadedWithoutTheSameWarning() {
        assumeTrue(
            "SEC-50 needs a shell that can run am start, so the launch comes from outside the app uid.",
            ShellIntent.isAvailable(),
        )
        val url = server.servePdf(PATH, SecurityFixtures.file(SecurityFixtures.NORMAL))
        server.clearRequestLog()

        ShellIntent.startView(url, READER_CLASS)
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        online.awaitShown(Await.LONG_TIMEOUT_MS)
        online.awaitErrorText(string(R.string.http_online_pdf_warning), Await.LONG_TIMEOUT_MS)
        online.awaitButtonLabel(string(R.string.proceed_anyway), Await.LONG_TIMEOUT_MS)
        server.holdRequestLogEmpty("a cleartext link supplied by intent before the user confirmed it")

        val previous = Screens.liveActivitiesOfType(MainActivity::class.java).single()
        TestEnvironment.onMainThread { previous.recreate() }
        Screens.awaitReplacement(previous, MainActivity::class.java)

        online.awaitShown(Await.LONG_TIMEOUT_MS)
        online.awaitErrorText(string(R.string.http_online_pdf_warning), Await.LONG_TIMEOUT_MS)
        online.awaitButtonLabel(string(R.string.proceed_anyway), Await.LONG_TIMEOUT_MS)
        server.holdRequestLogEmpty("a restored cleartext warning before the user confirmed it")

        online.pressOpen()
        online.awaitDismissed()
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        server.awaitRequestCount(1)

        val confirmed = Screens.liveActivitiesOfType(MainActivity::class.java).single()
        TestEnvironment.onMainThread { confirmed.recreate() }
        Screens.awaitReplacement(confirmed, MainActivity::class.java)
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        Await.assertThroughout(
            condition = "the handled HTTP intent does not reopen its warning or issue another request",
            windowMs = Await.SHORT_TIMEOUT_MS,
        ) {
            !online.isShown() && server.requestPaths().size == 1
        }
    }

    @Test
    @TestId("SEC-51")
    @TestDuration(DurationCategory.SHORT)
    fun aRedirectChainIsFollowedAtMostFiveTimes() {
        val url = server.serveRedirectLoop(LOOP_PATH)

        openReader {
            online.open()
            online.openCleartext(url)

            Snackbars.awaitText(string(R.string.toast_http_code_error), Await.LONG_TIMEOUT_MS)
            val requests = server.requestPaths()
            assertTrue(
                "MAX_REDIRECTS is 5, so the original plus its redirects may reach the server at most " +
                    "$MAX_REQUESTS times, it made ${requests.size}: $requests",
                requests.size <= MAX_REQUESTS,
            )
            assertNotEquals(
                "a redirect loop must not end in a loaded document",
                DocumentLoadState.LOADED,
                reader.readStateOrNull()?.loadState,
            )
            assertTrue(
                "a refused redirect chain must leave nothing in the online cache",
                IntakeReader.onlineCacheFiles().isEmpty(),
            )
        }
    }

    @Test
    @TestId("SEC-53")
    fun anHttpsUrlRedirectedToHttpDoesNotSilentlyDowngrade() {
        server.startHttps(trusted = true)
        val cleartextTarget = server.servePdf(PATH, SecurityFixtures.file(SecurityFixtures.NORMAL))
        val url = server.serveHttpsRedirectTo(DOWNGRADE_PATH, cleartextTarget)
        server.clearRequestLog()

        openReader {
            online.open()
            online.openWithoutWarning(url)

            Dialogs.awaitDialogTitled(R.string.insecure_redirect_title, Await.LONG_TIMEOUT_MS)
            Dialogs.awaitText(
                string(R.string.insecure_redirect_message, SecurityServer.LOOPBACK),
                Await.LONG_TIMEOUT_MS,
            )
            assertEquals(
                "the secure endpoint must be the request that produced the downgrade warning",
                listOf(DOWNGRADE_PATH),
                server.httpsRequestPaths(),
            )
            server.holdRequestLogEmpty("the cleartext redirect before the user acknowledges its warning")

            assertNotEquals(
                "a silent downgrade must not end in a loaded document",
                DocumentLoadState.LOADED,
                reader.readStateOrNull()?.loadState,
            )

            Dialogs.tapNegative()
            Dialogs.awaitDialogTitleGone(R.string.insecure_redirect_title)
            Snackbars.awaitText(string(R.string.toast_insecure_redirect_refused))
            server.holdRequestLogEmpty("the cleartext redirect after the user refuses it")
        }
    }

    @Test
    @TestId("SEC-54")
    @TestDuration(DurationCategory.SHORT)
    fun anOversizedResponseIsBounded() {
        val partDirectory = IntakeReader.cacheDirectory(IntakeReader.ONLINE_CACHE_DIRECTORY)
        val url = server.serveThrottledOversizedBody(
            OVERSIZED_PATH,
            OVERSIZED_BODY_BYTES,
            THROTTLE_BYTES_PER_PERIOD,
            THROTTLE_PERIOD_MS,
        )

        openReader {
            online.open()
            online.openCleartext(url)

            var peakPartBytes = 0L
            Await.until(
                "the download ends, or its part file crosses ${SIZE_BUDGET_BYTES / MEGABYTE} MB",
                OVERSIZED_TIMEOUT_MS,
                pollMs = SAMPLE_INTERVAL_MS,
            ) {
                val part = partDirectory.listFiles().orEmpty().firstOrNull { it.name.endsWith(PART_SUFFIX) }
                peakPartBytes = maxOf(peakPartBytes, part?.length() ?: 0L)
                peakPartBytes > SIZE_BUDGET_BYTES ||
                    Snackbars.currentText() != null ||
                    (part == null && peakPartBytes > 0L)
            }
            assertTrue(
                "an oversized download must be cut off, the part file reached ${peakPartBytes / MEGABYTE} MB " +
                    "against a budget of ${SIZE_BUDGET_BYTES / MEGABYTE} MB",
                peakPartBytes <= SIZE_BUDGET_BYTES,
            )
        }

        Await.until("no part file is left behind in the online cache", Await.LONG_TIMEOUT_MS) {
            partDirectory.listFiles().orEmpty().none { it.name.endsWith(PART_SUFFIX) }
        }
    }

    @Test
    @TestId("SEC-55")
    fun aResponseThatIsNotAPdfIsRefused() {
        val pdfBytes = SecurityFixtures.file(SecurityFixtures.NORMAL).readBytes()
        val htmlBytes = HTML_BODY.toByteArray()
        val cases = listOf(
            Triple("html body declared as html", htmlBytes, SecurityServer.HTML_MIME_TYPE),
            Triple("html body declared as pdf", htmlBytes, SecurityServer.PDF_MIME_TYPE),
            Triple("pdf body declared as html", pdfBytes, SecurityServer.HTML_MIME_TYPE),
        )

        openReader {
            for ((index, case) in cases.withIndex()) {
                val (label, body, contentType) = case
                val url = server.serveBytes("$SNIFF_PATH$index.pdf", body, contentType)
                online.open()
                online.openCleartext(url)

                if (body.contentEquals(pdfBytes)) {
                    reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
                    assertEquals(
                        "the bytes are what matter, so a valid PDF must load whatever Content-Type was " +
                            "declared ($label)",
                        DocumentLoadState.LOADED,
                        reader.readState().loadState,
                    )
                } else {
                    Await.until("the $label case reports an error", Await.LONG_TIMEOUT_MS) {
                        Snackbars.currentText() != null ||
                            reader.readStateOrNull()?.loadState == DocumentLoadState.FAILED
                    }
                    assertNotEquals(
                        "the $label case must not end in a loaded document",
                        DocumentLoadState.LOADED,
                        reader.readStateOrNull()?.loadState,
                    )
                    assertTrue(
                        "nothing may be left in the online cache for the $label case, it holds " +
                            IntakeReader.onlineCacheFiles().map { it.name },
                        IntakeReader.onlineCacheFiles().isEmpty(),
                    )
                }
            }
        }
    }

    @Test
    @TestId("SEC-56")
    @TestDuration(DurationCategory.LONG)
    fun aServerThatNeverFinishesTheResponseTimesOut() {
        val url = server.serveStalledBody(STALL_PATH, STALLED_LENGTH_BYTES)

        openReader {
            online.open()
            online.openCleartext(url)

            Await.until(
                "an error is reported within ${STALL_TIMEOUT_MS}ms, consistent with a 30 second read timeout",
                STALL_TIMEOUT_MS,
                pollMs = SAMPLE_INTERVAL_MS,
            ) { Snackbars.currentText() != null }

            assertNotEquals(
                "a stalled download must not leave the reader loading",
                DocumentLoadState.LOADING,
                reader.readStateOrNull()?.loadState,
            )
            assertTrue(
                "no part file may be left behind",
                IntakeReader.cacheDirectory(IntakeReader.ONLINE_CACHE_DIRECTORY)
                    .listFiles().orEmpty().none { it.name.endsWith(PART_SUFFIX) },
            )
        }
    }

    @Test
    @TestId("SEC-57")
    @TestDuration(DurationCategory.SHORT)
    fun anInvalidTlsCertificateIsRefused() {
        server.startHttps(trusted = false)
        val url = server.serveOverHttps(PATH, SecurityFixtures.file(SecurityFixtures.NORMAL).readBytes())
        val downloadsBefore = downloadsListing()

        openReader {
            online.open()
            online.openWithoutWarning(url)

            Snackbars.awaitText(string(R.string.toast_ssl_error), Await.LONG_TIMEOUT_MS)
            assertNotEquals(
                "a connection that cannot be verified must not end in a loaded document",
                DocumentLoadState.LOADED,
                reader.readStateOrNull()?.loadState,
            )
            assertTrue(
                "nothing may be written to the online cache",
                IntakeReader.onlineCacheFiles().isEmpty(),
            )
            assertEquals(
                "a refused connection must add nothing to Downloads. The runner seeds every fixture into " +
                    "Downloads, normal.pdf among them, so this compares the listing rather than the name",
                emptyList<String>(),
                downloadsListing() - downloadsBefore,
            )
            assertFalse(
                "the dialog must offer no way to proceed past a certificate it cannot verify",
                online.isShown(),
            )
        }
    }

    private fun downloadsListing(): List<String> =
        downloadsDirectory().list().orEmpty().sorted()

    private fun downloadsDirectory(): File =
        android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )

    private fun openReader(block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.NORMAL)
        ).use {
            reader.awaitReady()
            reader.awaitFullQualityRender()
            block()
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun string(resId: Int, vararg formatArgs: Any): String =
        TestEnvironment.targetContext.getString(resId, *formatArgs)

    private companion object {
        const val READER_CLASS = ".ui.reader.MainActivity"
        const val PATH = "/normal.pdf"
        const val LOOP_PATH = "/loop.pdf"
        const val OVERSIZED_PATH = "/oversized.pdf"
        const val SNIFF_PATH = "/sniff"
        const val STALL_PATH = "/stall.pdf"
        const val PART_SUFFIX = ".part"
        const val MAX_REQUESTS = 6
        const val MEGABYTE = 1024L * 1024
        const val SIZE_BUDGET_BYTES = 512L * MEGABYTE
        const val OVERSIZED_BODY_BYTES = 600L * MEGABYTE
        const val THROTTLE_BYTES_PER_PERIOD = 8L * MEGABYTE
        const val THROTTLE_PERIOD_MS = 100L
        const val OVERSIZED_TIMEOUT_MS = 120_000L
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val STALLED_LENGTH_BYTES = 100L * MEGABYTE
        const val STALL_TIMEOUT_MS = 40_000L
        const val DOWNGRADE_PATH = "/downgrade.pdf"
        const val HTML_BODY = "<html><body>not a document</body></html>"
    }
}
