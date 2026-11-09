// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.annotationsave.SaveDigest
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.net.LocalPdfServer
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class OnlineIntakeTest {

    private val suite = SuiteRules.standard()
    private val downloads = DownloadsGuardRule()
    private val server = LocalPdfServer()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(downloads).around(server)

    private val reader = ReaderRobot()

    @Test
    @TestId("INTAKE-09")
    fun anHttpViewIntentDownloadsAndOpensTheDocumentAfterConfirmation() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val url = server.servePdfBytes("/intake-09.pdf", fixture.readBytes())

        reader.withExternalIntent(IntakeReader.viewIntent(Uri.parse(url), null)) {
            reader.acceptCleartextWarning()
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            val state = reader.awaitFullQualityRender()
            assertEquals(IntakeFixtures.NORMAL_PAGES as Int?, state.pageCount)

            val cached = Await.untilValue(
                "the downloaded document lands in the online cache",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeReader.onlineCacheFiles() },
            ) { it.size == 1 }.single()

            assertEquals(
                "the cache entry must be keyed by the SHA-256 of the requested URL",
                "${SaveDigest.sha256Hex(url)}.pdf",
                cached.name,
            )
            assertEquals("the cached copy must be the whole document", fixture.length(), cached.length())
            assertTrue(
                "a normal download must not touch the incognito cache",
                IntakeReader.incognitoCacheFiles().isEmpty(),
            )

            val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
            assertEquals("the record must remember the link that was opened", url, record.uri.toString())
        }
    }

    @Test
    @TestId("INTAKE-10")
    fun sharedTextContainingALinkOpensThatLink() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val url = server.servePdfBytes("/intake-10.pdf", fixture.readBytes())

        IntakeReader.startFromOutside(IntakeReader.sendTextIntent("look at this ($url), it is good."))
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        reader.acceptCleartextWarning()
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        val state = reader.awaitFullQualityRender()
        assertEquals(IntakeFixtures.NORMAL_PAGES as Int?, state.pageCount)

        val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
        assertEquals(
            "the surrounding punctuation must be trimmed off the link",
            url,
            record.uri.toString(),
        )
    }

    @Test
    @TestId("INTAKE-38")
    fun aFourOhFourReportsAServerError() {
        val url = server.urlFor("/intake-38-missing.pdf")

        reader.withExternalIntent(IntakeReader.viewIntent(Uri.parse(url), null)) {
            reader.acceptCleartextWarning()
            reader.awaitShown()
            Snackbars.awaitText(IntakeDialogs.string(R.string.toast_http_code_error), Await.LONG_TIMEOUT_MS)

            assertTrue(
                "a failed download must leave nothing in the online cache",
                IntakeReader.onlineCacheFiles().isEmpty(),
            )
            IntakeReader.awaitPickFileButton()
            IntakeDb.holdEmpty()
        }
    }

    @Test
    @TestId("INTAKE-39")
    fun aRedirectedLinkIsFollowedToTheDocument() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        server.servePdfBytes("/intake-39-target.pdf", fixture.readBytes())
        val url = server.serveRedirectTo("/intake-39-go.pdf", "/intake-39-target.pdf")

        reader.withExternalIntent(IntakeReader.viewIntent(Uri.parse(url), null)) {
            reader.acceptCleartextWarning()
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            val state = reader.awaitFullQualityRender()
            assertEquals(IntakeFixtures.NORMAL_PAGES as Int?, state.pageCount)

            val cached = Await.untilValue(
                "the redirected document lands in the online cache",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeReader.onlineCacheFiles() },
            ) { it.size == 1 }.single()
            assertEquals(
                "the cache is keyed by the link that was requested, not the one it resolved to",
                "${SaveDigest.sha256Hex(url)}.pdf",
                cached.name,
            )

            val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
            assertEquals("the record must remember the original link", url, record.uri.toString())
        }
    }

    @Test
    @TestId("INTAKE-40")
    fun anUnreachableServerReportsADownloadError() {
        val url = LocalPdfServer.closedPortUrl("/intake-40.pdf")

        reader.withExternalIntent(IntakeReader.viewIntent(Uri.parse(url), null)) {
            reader.acceptCleartextWarning()
            reader.awaitShown()
            Snackbars.awaitText(IntakeDialogs.string(R.string.toast_generic_download_error), Await.LONG_TIMEOUT_MS)

            assertFalse(
                "the progress bar must not be left spinning after a failed download",
                IntakeReader.isViewVisible(R.id.progressBar),
            )
            IntakeReader.awaitPickFileButton()
        }
    }

    @Test
    @TestId("INTAKE-41")
    fun aDownloadedDocumentIsServedFromCacheOnTheSecondOpen() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val url = server.servePdfBytes("/intake-41.pdf", fixture.readBytes())

        reader.withExternalIntent(IntakeReader.viewIntent(Uri.parse(url), null)) {
            reader.acceptCleartextWarning()
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()
            Await.until(
                "the downloaded document lands in the online cache",
                Await.LONG_TIMEOUT_MS,
            ) { IntakeReader.onlineCacheFiles().size == 1 }
        }

        server.close()

        reader.withExternalIntent(IntakeReader.viewIntent(Uri.parse(url), null)) {
            reader.acceptCleartextWarning()
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            val state = reader.awaitFullQualityRender()
            assertEquals(
                "the cached copy must open with the server gone",
                IntakeFixtures.NORMAL_PAGES as Int?,
                state.pageCount,
            )

            Await.assertThroughout(
                condition = "no download error is reported while reading from cache",
                windowMs = IntakeDb.QUIET_WINDOW_MS,
            ) { Snackbars.currentText() != IntakeDialogs.string(R.string.toast_generic_download_error) }

            assertEquals(
                "reopening must not add a second cache entry",
                1,
                IntakeReader.onlineCacheFiles().size,
            )
        }
    }

    @Test
    @TestId("INTAKE-42")
    fun anIncognitoDownloadIsKeptApartAndSweptAway() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val other = IntakeFixtures.seeded(IntakeFixtures.LARGE)
        val url = server.servePdfBytes("/intake-42.pdf", fixture.readBytes())

        reader.withExternalIntent(IntakeReader.incognitoViewIntent(Uri.parse(url))) {
            reader.acceptCleartextWarning()
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()

            Await.untilValue(
                "the incognito download lands in the incognito cache only",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeReader.incognitoCacheFiles() },
            ) { it.size == 1 }
            assertTrue(
                "an incognito download must never reach the normal cache",
                IntakeReader.onlineCacheFiles().isEmpty(),
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        reader.withExternalIntent(IntakeReader.fileViewIntent(other)) {
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()

            Await.until("the incognito cache is swept clean", Await.LONG_TIMEOUT_MS) {
                IntakeReader.incognitoCacheFiles().isEmpty()
            }
        }

        assertTrue(
            "an incognito online read must leave no library entry behind",
            IntakeDb.records().none { it.uri.toString() == url },
        )
    }
}
