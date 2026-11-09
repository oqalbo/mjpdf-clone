// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.text.format.Formatter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryDebugConfig
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryInstaller
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ArtifactBytes
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class DictionarySettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-57")
    fun theDownloadSizeIsDisclosedBeforeAnyNetworkUseAndDecliningCostsNothing() {
        launchSettingsOn(SettingsPage.TRANSLATION).use {
            assertEquals(
                "the row discloses the download size while nothing is installed",
                SettingsStrings.of(
                    R.string.dictionary_summary_not_installed,
                    shortSize(DictionaryInstaller.downloadSizeBytes),
                ),
                SettingsScreen.awaitRow(dictionaryTitle).summary,
            )

            SettingsScreen.tapRow(dictionaryTitle)
            SettingsDialog.awaitShown()

            assertEquals(
                "the confirmation asks before downloading",
                SettingsStrings.of(R.string.dictionary_download_title),
                SettingsDialog.title(),
            )
            val message = SettingsDialog.message().orEmpty()
            assertTrue(
                "the message names the download size, it reads \"$message\"",
                message.contains(shortSize(DictionaryInstaller.downloadSizeBytes)),
            )
            assertTrue(
                "the message names the installed size, it reads \"$message\"",
                message.contains(shortSize(DictionaryInstaller.installedSizeBytes)),
            )

            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()

            assertFalse("declining installs nothing", SettingsFixtures.dictionaryFile().exists())
            SettingsDialog.assertNoTextWithin(SettingsStrings.of(R.string.dictionary_downloading))
            SettingsScreen.awaitEnabled(defineWordsTitle, false)
        }
    }

    @Test
    @TestId("SET-58")
    @TestDuration(DurationCategory.NORMAL)
    fun theOfflineDictionaryInstallsEndToEnd() {
        DictionaryDebugConfig.chunkDelayMillis = OBSERVABLE_CHUNK_DELAY_MS
        val archive = gzip(deterministicDictionary())
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(archive)))
        server.start()
        DictionaryDebugConfig.urlOverride = server.url(DICTIONARY_PATH).toString()
        DictionaryDebugConfig.sha256Override = ArtifactBytes.sha256(archive)

        try {
            launchSettingsOn(SettingsPage.TRANSLATION).use {
                startDownload()

                assertEquals(
                    "the progress dialog names what it is doing",
                    SettingsStrings.of(R.string.dictionary_downloading),
                    SettingsDialog.title(),
                )
                assertTrue("the progress dialog carries a progress indicator", SettingsDialog.hasProgressIndicator())
                tapOutsideTheDialog()
                assertTrue(
                    "the progress dialog is not cancelable by tapping outside it",
                    SettingsDialog.isShowing(),
                )
                SettingsDialog.awaitProgressAbove(0, DOWNLOAD_TIMEOUT_MS)
                assertFalse(
                    "the indicator becomes determinate once bytes arrive",
                    SettingsDialog.isProgressIndeterminate(),
                )

                SettingsDialog.awaitDismissed(DOWNLOAD_TIMEOUT_MS)

                Await.until("the dictionary lands at its installed size", Await.LONG_TIMEOUT_MS) {
                    SettingsFixtures.dictionaryFile().length() == DictionaryInstaller.installedSizeBytes
                }
                assertEquals(
                    "no partial download file is left behind",
                    listOf("en-wordnet.db"),
                    SettingsFixtures.dictionaryDirectoryNames().sorted(),
                )
                SettingsScreen.awaitRowSummaryIs(
                    dictionaryTitle,
                    SettingsStrings.of(
                        R.string.dictionary_summary_installed,
                        shortSize(SettingsFixtures.dictionaryFile().length()),
                    ),
                )
                SettingsScreen.awaitEnabled(defineWordsTitle, true)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    @TestId("SET-59")
    @TestDuration(DurationCategory.NORMAL)
    fun aCancelledDownloadLeavesNoPartialFileAndReportsNoFailure() {
        val barrier = CompletableDeferred<Unit>()
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(128 * 1024))))
        server.start()
        DictionaryDebugConfig.urlOverride = server.url(DICTIONARY_PATH).toString()
        DictionaryDebugConfig.chunkBarrier = barrier

        try {
            assertTrue(
                "cancellation must use a local response, independent of public network access",
                DictionaryDebugConfig.urlOverride?.startsWith("http://localhost:") == true,
            )
            launchSettingsOn(SettingsPage.TRANSLATION).use {
                startDownload()
                SettingsDialog.awaitProgressAbove(0, Await.LONG_TIMEOUT_MS)
                assertEquals("the local server received the download", 1, server.requestCount)
                assertFalse("the download stays held until cancellation", barrier.isCompleted)
                assertTrue(
                    "bytes are present in the partial file before cancellation",
                    java.io.File(SettingsFixtures.dictionaryFile().path + ".gz.tmp").length() > 0L,
                )

                SettingsDialog.cancel()
                SettingsDialog.awaitDismissed()
                Await.until("cancellation removes all partial dictionary files", Await.LONG_TIMEOUT_MS) {
                    SettingsFixtures.dictionaryDirectoryNames().none {
                        it == "en-wordnet.db" || it.endsWith(".gz.tmp") || it.endsWith(".tmp")
                    }
                }
                SettingsDialog.assertNoTextWithin(
                    SettingsStrings.of(R.string.dictionary_download_failed),
                    FAILURE_ABSENCE_WINDOW_MS,
                )
                SettingsScreen.awaitRowSummaryIs(
                    dictionaryTitle,
                    SettingsStrings.of(
                        R.string.dictionary_summary_not_installed,
                        shortSize(DictionaryInstaller.downloadSizeBytes),
                    ),
                )
            }
        } finally {
            barrier.complete(Unit)
            DictionaryDebugConfig.chunkBarrier = null
            server.shutdown()
        }
    }

    @Test
    @TestId("SET-60")
    fun aFailedDownloadIsReportedAndInstallsNothing() {
        airplaneMode.enable()

        launchSettingsOn(SettingsPage.TRANSLATION).use {
            beginDownload()

            Dialogs.awaitText(SettingsStrings.of(R.string.dictionary_download_failed), Await.LONG_TIMEOUT_MS)
            assertTrue(
                "the failure dialog explains itself rather than showing an empty body",
                SettingsDialog.message().orEmpty().isNotBlank(),
            )
            SettingsDialog.tap(SettingsDialog.POSITIVE)
            SettingsDialog.awaitDismissed()

            assertFalse("a failed download installs nothing", SettingsFixtures.dictionaryFile().exists())
            SettingsScreen.awaitRowSummaryIs(
                dictionaryTitle,
                SettingsStrings.of(
                    R.string.dictionary_summary_not_installed,
                    shortSize(DictionaryInstaller.downloadSizeBytes),
                ),
            )
            SettingsScreen.awaitEnabled(defineWordsTitle, false)
        }
    }

    @Test
    @TestId("SET-61")
    fun theInstalledDictionaryCanBeRemovedAndTheRowGoesBackToOfferingADownload() {
        SettingsFixtures.installFakeDictionary()

        launchSettingsOn(SettingsPage.TRANSLATION).use {
            SettingsScreen.tapRow(dictionaryTitle)
            SettingsDialog.awaitShown()
            assertEquals(
                "an installed dictionary offers removal rather than a download",
                SettingsStrings.of(R.string.dictionary_delete_title),
                SettingsDialog.title(),
            )
            SettingsDialog.confirm()
            SettingsDialog.awaitDismissed()

            Await.until("the dictionary file is deleted", Await.LONG_TIMEOUT_MS) {
                !SettingsFixtures.dictionaryFile().exists()
            }
            SettingsScreen.awaitRowSummaryIs(
                dictionaryTitle,
                SettingsStrings.of(
                    R.string.dictionary_summary_not_installed,
                    shortSize(DictionaryInstaller.downloadSizeBytes),
                ),
            )

            SettingsScreen.tapRow(dictionaryTitle)
            SettingsDialog.awaitShown()
            assertEquals(
                "the row now offers the download confirmation again",
                SettingsStrings.of(R.string.dictionary_download_title),
                SettingsDialog.title(),
            )
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
        }
    }

    @Test
    @TestId("SET-62")
    fun theRowShowsTheRealFileSizeAndDefineWordsIsGatedOnTheFileExisting() {
        val installed = SettingsFixtures.installFakeDictionary()

        launchSettingsOn(SettingsPage.TRANSLATION).use {
            assertEquals(
                "the summary measures the file on disk rather than the constant install size",
                SettingsStrings.of(R.string.dictionary_summary_installed, shortSize(installed.length())),
                SettingsScreen.awaitRow(dictionaryTitle).summary,
            )
            assertEquals(
                "the fake dictionary is exactly one mebibyte",
                FAKE_DICTIONARY_BYTES,
                installed.length(),
            )

            SettingsScreen.awaitEnabled(defineWordsTitle, true)
            toggleSwitch(defineWordsTitle, false)
            SettingsPrefs.awaitBoolean(Preferences.dictionaryDefineWordsKey, false)

            SettingsScreen.tapRow(dictionaryTitle)
            SettingsDialog.awaitShown()
            SettingsDialog.confirm()
            SettingsDialog.awaitDismissed()
            Await.until("the dictionary file is deleted", Await.LONG_TIMEOUT_MS) {
                !SettingsFixtures.dictionaryFile().exists()
            }

            SettingsScreen.awaitEnabled(defineWordsTitle, false)
            SettingsScreen.tapRow(defineWordsTitle)
            SettingsPrefs.assertStays(Preferences.dictionaryDefineWordsKey, false)
        }
    }

    private fun startDownload() {
        beginDownload()
        Await.until("the download progress dialog appears", Await.LONG_TIMEOUT_MS) {
            Dialogs.hasText(SettingsStrings.of(R.string.dictionary_downloading))
        }
    }

    private fun beginDownload() {
        SettingsScreen.tapRow(dictionaryTitle)
        SettingsDialog.awaitShown()
        SettingsDialog.tap(SettingsDialog.POSITIVE)
    }

    private fun tapOutsideTheDialog() {
        val device = TestEnvironment.uiDevice
        device.click(device.displayWidth / 2, OUTSIDE_TAP_Y)
        TestEnvironment.waitForIdleSync()
    }

    private fun shortSize(bytes: Long): String =
        Formatter.formatShortFileSize(TestEnvironment.targetContext, bytes)

    private fun deterministicDictionary(): ByteArray =
        ByteArray(DictionaryInstaller.installedSizeBytes.toInt()) { index -> (index * 31).toByte() }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }

    private val dictionaryTitle: String get() = SettingsStrings.of(R.string.dictionary_title)
    private val defineWordsTitle: String get() = SettingsStrings.of(R.string.dictionary_define_words_title)

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 300_000L
        const val OBSERVABLE_CHUNK_DELAY_MS = 1_000L
        const val FAILURE_ABSENCE_WINDOW_MS = 3_000L
        const val FAKE_DICTIONARY_BYTES = 1_048_576L
        const val OUTSIDE_TAP_Y = 40
        const val DICTIONARY_PATH = "/dictionary.db.gz"
    }
}
