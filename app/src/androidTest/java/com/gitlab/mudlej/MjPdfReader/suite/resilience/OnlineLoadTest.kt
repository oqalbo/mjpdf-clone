// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.os.Environment
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ArtifactBytes
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.net.LocalPdfServer
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class OnlineLoadTest : ResilienceTestCase() {

    private val server = LocalPdfServer()
    private val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServerAndRemoveDownloads() {
        runCatching { server.shutdown() }
        downloads.listFiles().orEmpty()
            .filter { it.name.startsWith(DOWNLOAD_PREFIX) }
            .forEach { Fixtures.delete(it) }
    }

    @Test
    @TestId("RES-24")
    fun backgroundingDuringAThrottledDownloadKeepsIt() {
        val fixture = fixture(ResilienceFixtures.LARGE)
        val url = server.serveThrottledPdfBytes(
            THROTTLED_PATH,
            fixture.readBytes(),
            THROTTLE_BYTES_PER_PERIOD,
            THROTTLE_PERIOD_MS,
        )

        launchReaderOutsideScenarioOnline(url)
        awaitDownloadProgressAbove(MINIMUM_PROGRESS_PERCENT)

        backgroundReader()
        val progressWhileAway = awaitDownloadProgressAdvance()
        assertTrue(
            "the download must keep advancing while the app is in the background, but it stayed at " +
                "$progressWhileAway percent",
            progressWhileAway > MINIMUM_PROGRESS_PERCENT,
        )

        returnToReaderTask(url)
        val loaded = reader.awaitState("the downloaded document reports its page count", Await.VERY_LONG_TIMEOUT_MS) {
            (it.pageCount ?: 0) > 0
        }
        assertEquals(
            "the whole document must be downloaded and opened, not the part that arrived before the pause",
            ResilienceFixtures.LARGE_PAGE_COUNT as Int?,
            loaded.pageCount,
        )
        assertEquals(
            "the online document store must hold exactly one file for this URL",
            1,
            onlineStoreFileCount(),
        )
        val publicCopy = File(downloads, "$DOWNLOAD_PREFIX.pdf")
        Await.until("the completed PDF copy is published in Downloads", Await.LONG_TIMEOUT_MS) {
            publicCopy.isFile && publicCopy.length() == fixture.length()
        }
        assertEquals(
            "the completed public copy must contain exactly the downloaded PDF",
            ArtifactBytes.sha256(fixture),
            ArtifactBytes.sha256(publicCopy),
        )
        assertTrue(
            "no partial download may be left in the public Downloads directory, found ${partFiles()}",
            partFiles().isEmpty(),
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-25")
    @TestDuration(DurationCategory.SHORT)
    fun losingTheConnectionMidDownloadReportsAnError() {
        val fixture = fixture(ResilienceFixtures.LARGE)
        val url = server.serveThrottledPdfBytes(
            THROTTLED_PATH,
            fixture.readBytes(),
            THROTTLE_BYTES_PER_PERIOD,
            THROTTLE_PERIOD_MS,
        )

        reader.withExternalIntent(ResilienceFixtures.onlineViewIntent(url)) {
            reader.acceptCleartextWarning()
            awaitDownloadProgressAbove(MINIMUM_PROGRESS_PERCENT)

            server.shutdown()

            ReaderProbe.awaitVisibility(R.id.progressBar, View.GONE, Await.VERY_LONG_TIMEOUT_MS)
            Snackbars.awaitText(
                TestEnvironment.targetContext.getString(R.string.toast_generic_download_error),
                Await.LONG_TIMEOUT_MS,
            )
            assertEquals(
                "with nothing loaded the pick file button must come back so the user is not stranded",
                View.VISIBLE,
                ReaderProbe.visibility(R.id.pickFileButton),
            )
            assertEquals(
                "a download cut mid body must leave nothing behind in the online document store",
                0,
                onlineStoreFileCount(),
            )
        }
    }

    private fun launchReaderOutsideScenarioOnline(url: String) {
        ReaderProbe.startReader(ResilienceFixtures.onlineViewIntent(url))
        com.gitlab.mudlej.MjPdfReader.suite.core.Screens.awaitResumed(
            MainActivity::class.java,
            Await.LONG_TIMEOUT_MS,
        )
        reader.acceptCleartextWarning()
    }

    private fun returnToReaderTask(url: String) {
        ReaderProbe.startReader(ResilienceFixtures.onlineViewIntent(url))
        com.gitlab.mudlej.MjPdfReader.suite.core.Screens.awaitResumed(
            MainActivity::class.java,
            Await.LONG_TIMEOUT_MS,
        )
    }

    private fun downloadProgress(): Int = runCatching {
        ReaderProbe.onReaderView(R.id.progressBar) { (it as android.widget.ProgressBar).progress }
    }.getOrDefault(-1)

    private fun awaitDownloadProgressAbove(percent: Int): Int = Await.untilValue(
        condition = "the download progress bar passes $percent percent",
        timeoutMs = Await.VERY_LONG_TIMEOUT_MS,
        read = { downloadProgress() },
    ) { it > percent }

    private fun awaitDownloadProgressAdvance(): Int {
        val start = downloadProgressWhileAway()
        return Await.untilValue(
            condition = "the download keeps advancing past $start percent while the reader is away",
            timeoutMs = Await.VERY_LONG_TIMEOUT_MS,
            read = { downloadProgressWhileAway() },
        ) { it > start }
    }

    private fun downloadProgressWhileAway(): Int = TestEnvironment.onMainThread {
        val activity = ReaderProbe.liveReaderOrNull() ?: return@onMainThread -1
        activity.findViewById<android.widget.ProgressBar>(R.id.progressBar)?.progress ?: -1
    }

    private fun onlineStoreFileCount(): Int =
        File(TestEnvironment.targetContext.cacheDir, ONLINE_STORE_DIRECTORY)
            .listFiles().orEmpty()
            .count { it.isFile && it.length() > 0 }

    private fun partFiles(): List<String> =
        downloads.listFiles().orEmpty()
            .filter { it.name.startsWith(DOWNLOAD_PREFIX) && it.name.endsWith(PART_SUFFIX) }
            .map { it.name }

    private companion object {
        const val THROTTLED_PATH = "/resilience-res24.pdf"
        const val DOWNLOAD_PREFIX = "resilience-res24"
        const val THROTTLE_BYTES_PER_PERIOD = 200L * 1024
        const val THROTTLE_PERIOD_MS = 1_000L
        const val MINIMUM_PROGRESS_PERCENT = 10
        const val ONLINE_STORE_DIRECTORY = "online-pdf"
        const val PART_SUFFIX = ".part"
    }
}
