// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReaderRecreationTest : ResilienceTestCase() {

    @Test
    @TestId("RES-09")
    fun recreatingTheReaderRestoresDocumentPageAndZoom() {
        val file = fixture(ResilienceFixtures.NORMAL)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use { scenario ->
            awaitReaderReady()
            jumpToPage(RECREATED_PAGE)
            zoomKeepingPage(RESTORED_ZOOM)
            settledFullQuality()

            ClearedStoreRecreation.recreate(scenario, MainActivity::class.java)
            awaitReaderReady()
            val restored = settledFullQuality()

            assertEquals(
                "the recreated reader must come back on the page it was left on",
                RECREATED_PAGE as Int?,
                restored.page,
            )
            assertEquals(
                "the zoom must be restored from the saved view state",
                RESTORED_ZOOM.toDouble(),
                (restored.zoom ?: 0f).toDouble(),
                ZOOM_TOLERANCE.toDouble(),
            )
            assertEquals(
                "the action bar must name the document that was open, which PDF.getTitle renders without " +
                    "the file extension",
                file.nameWithoutExtension,
                ReaderProbe.actionBarTitle(),
            )
            FileDescriptors.awaitSettledCountOf(file, 1)
        }
    }

    @Test
    @TestId("RES-10")
    fun recreatingAnIncognitoReaderStaysIncognito() {
        val file = fixture(ResilienceFixtures.NORMAL)
        val hash = ResilienceSeed.identityOf(file)
        ActivityScenario.launch<MainActivity>(ResilienceFixtures.incognitoViewIntent(file)).use { scenario ->
            awaitReaderReady()
            jumpToPage(INCOGNITO_PAGE)

            ClearedStoreRecreation.recreate(scenario, MainActivity::class.java)
            awaitReaderReady()
            settledFullQuality()

            assertEquals(
                "the incognito badge in the action bar must come back with the session",
                View.VISIBLE,
                ReaderProbe.visibility(R.id.actionbarIncognitoIcon),
            )
            assertTrue(
                "the view model must still report the session as incognito",
                ReaderProbe.viewModel().incognito,
            )
            assertEquals(
                "the recreated incognito reader must come back on the page it was left on",
                INCOGNITO_PAGE as Int?,
                state().page,
            )
            Records.assertNoRowFor(hash)
        }
    }

    @Test
    @TestId("RES-11")
    fun alwaysOpenAtFirstPageDoesNotWinOverARestore() {
        SettingsSeeder.seed(Preferences.alwaysOpenAtFirstPageKey, true)
        val file = fixture(ResilienceFixtures.NORMAL)
        val hash = ResilienceSeed.identityOf(file)
        ResilienceSeed.insertRecord(hash, android.net.Uri.fromFile(file), file.name, pageNumber = STORED_PAGE)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use { scenario ->
            awaitReaderReady()
            assertEquals(
                "the first page override must win over the stored page when the document is opened",
                0 as Int?,
                state().page,
            )
            Records.assertRowKeeps(
                hash,
                "the stored page stays at $STORED_PAGE while the reader sits on the suppressed first page",
            ) { it?.pageNumber == STORED_PAGE }

            jumpToPage(PAGE_AFTER_OVERRIDE)
            Records.awaitStoredPage(hash, PAGE_AFTER_OVERRIDE, Await.LONG_TIMEOUT_MS)

            ClearedStoreRecreation.recreate(scenario, MainActivity::class.java)
            awaitReaderReady()
            settledFullQuality()

            assertEquals(
                "the restore must return to the page the session was on, not re-apply the first page override",
                PAGE_AFTER_OVERRIDE as Int?,
                state().page,
            )
            assertEquals(
                "the stored page must be the one the reader reached after the suppression cleared",
                PAGE_AFTER_OVERRIDE,
                Records.byHash(hash)?.pageNumber,
            )
        }
    }

    @Test
    @TestId("RES-12")
    fun restoringWithHistoryOffFallsBackToTheSavedState() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        val file = fixture(ResilienceFixtures.NORMAL)
        val hash = ResilienceSeed.identityOf(file)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use { scenario ->
            awaitReaderReady()
            jumpToPage(HISTORY_OFF_PAGE)

            ClearedStoreRecreation.recreate(scenario, MainActivity::class.java)
            awaitReaderReady()
            settledFullQuality()

            assertEquals(
                "with no record to read the page from, the restore must take it from the saved instance state",
                HISTORY_OFF_PAGE as Int?,
                state().page,
            )
            val record = Records.byHash(hash)
            assertTrue(
                "history recording is off, so no record may carry the page the reader was on, but it reads " +
                    "${record?.pageNumber}",
                record == null || record.pageNumber != HISTORY_OFF_PAGE,
            )
        }
    }

    @Test
    @TestId("RES-16")
    fun textModeRestoresItsPageAfterRecreation() {
        val file = fixture(ResilienceFixtures.LARGE)
        val handlesBefore = FileDescriptors.countFor(file)
        ActivityScenario.launch<com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity>(
            TextModeProbe.intentFor(file, 0)
        ).use { scenario ->
            TextModeProbe.awaitPagePopulated(0)
            com.gitlab.mudlej.MjPdfReader.suite.text.TextModeRobot().goToPage(DEEP_TEXT_PAGE + 1)
            TextModeProbe.awaitPagePopulated(DEEP_TEXT_PAGE)
            val handlesWithTextMode = FileDescriptors.countFor(file)
            assertTrue(
                "text mode must hold a descriptor on ${file.absolutePath}, otherwise the handle assertion " +
                    "below measures nothing",
                handlesWithTextMode > handlesBefore,
            )

            scenario.recreate()
            TextModeProbe.awaitPagePopulated(DEEP_TEXT_PAGE)

            assertEquals(
                "the recreated text mode screen must come back on the page it was left on",
                DEEP_TEXT_PAGE,
                TextModeProbe.firstVisiblePosition(),
            )
            FileDescriptors.awaitSettledCountOf(file, handlesWithTextMode)
        }
    }

    private companion object {
        const val INCOGNITO_PAGE = 3
        const val HISTORY_OFF_PAGE = 8
        const val RECREATED_PAGE = 11
        const val PAGE_AFTER_OVERRIDE = 19
        const val STORED_PAGE = 30
        const val RESTORED_ZOOM = 1.8f
        const val DEEP_TEXT_PAGE = 59
    }
}
