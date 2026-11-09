// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.ActivityManager
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.PdfExtractorDebug
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.AlwaysFinishActivitiesRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeLifecycleTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val alwaysFinishActivities = AlwaysFinishActivitiesRule()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @After
    fun releaseExtractionGate() {
        PdfExtractorDebug.releaseTextModeSetup()
    }

    @Test
    @TestId("TXT-45")
    @TestDuration(DurationCategory.SHORT)
    fun leavingTextModeWhileItIsStillLoadingClosesCleanly() {
        TextFixtures.launchReader(TextFixtures.LARGE).use {
            text.awaitReaderSettled()
            val pageBefore = reader.readState().page
            val liveBefore = PdfExtractorDebug.liveInstances()

            openTextModeWithoutWaiting()
            awaitStillLoading()
            assertTrue(
                "text mode has opened an extractor while setup is held",
                PdfExtractorDebug.liveInstances() > liveBefore,
            )
            try {
                TestEnvironment.uiDevice.pressBack()
                text.awaitFinished(Await.LONG_TIMEOUT_MS)
            } finally {
                PdfExtractorDebug.releaseTextModeSetup()
            }
            Screens.awaitTopIs(MainActivity::class.java)
            Await.untilValue(
                "the live PdfExtractor count returns to $liveBefore",
                timeoutMs = Await.LONG_TIMEOUT_MS,
                read = { PdfExtractorDebug.liveInstances() },
            ) { it == liveBefore }
            assertEquals(
                "closing text mode early must leave the reader where it was",
                pageBefore,
                reader.readState().page,
            )
        }
    }

    @Test
    @TestId("TXT-46")
    fun leavingWhileLoadingDoesNotShowTheFailureMessage() {
        val failure = text.string(R.string.failed_to_extract_text)

        TextFixtures.launchReader(TextFixtures.LARGE).use {
            text.awaitReaderSettled()

            val notices = TextNotices.record()
            try {
                openTextModeWithoutWaiting()
                awaitStillLoading()
                try {
                    TestEnvironment.uiDevice.pressBack()
                    text.awaitFinished(Await.LONG_TIMEOUT_MS)
                } finally {
                    PdfExtractorDebug.releaseTextModeSetup()
                }
                Screens.awaitTopIs(MainActivity::class.java)
                Await.assertThroughout(
                    condition = "no extraction failure notice appears after closing text mode early",
                    windowMs = SILENCE_WINDOW_MS,
                ) { !notices.seen().contains(failure) }
                notices.assertNeverSeen(failure)
            } finally {
                notices.stop()
            }

            assertFalse(
                "no view carrying the extraction failure string may be on screen",
                TestEnvironment.uiDevice.hasObject(androidx.test.uiautomator.By.text(failure)),
            )
        }
    }

    @Test
    @TestId("TXT-47")
    fun rotationKeepsThePageAndTheTypography() {
        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()
            text.goToPage(ROTATION_PAGE)
            text.awaitPageCounter(text.counterFor(ROTATION_PAGE, TextFixtures.NORMAL_PAGES))

            val before = text.awaitFirstVisiblePage(ROTATION_PAGE)

            Rotation.toLandscape(TextModeActivity::class.java)
            text.awaitPageCounter(text.counterFor(ROTATION_PAGE, TextFixtures.NORMAL_PAGES))

            val after = text.awaitFirstVisiblePage(ROTATION_PAGE)
            assertTrue("the rotated page must reach non empty text", !after.pageText.isNullOrBlank())
            assertEquals(
                "the text size must survive the turn",
                before.textSizePx,
                after.textSizePx,
                PIXEL_TOLERANCE,
            )
            assertEquals(
                "the line spacing must survive the turn",
                before.lineSpacingMultiplier,
                after.lineSpacingMultiplier,
                FLOAT_TOLERANCE,
            )

            Rotation.toPortrait(TextModeActivity::class.java)
            text.awaitPageCounter(text.counterFor(ROTATION_PAGE, TextFixtures.NORMAL_PAGES))
            text.awaitFirstVisiblePage(ROTATION_PAGE)

            text.finish()
        }
    }

    @Test
    @TestId("TXT-48")
    fun recreatingTextModeWhileBackgroundedKeepsItsPage() {
        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()
            text.goToPage(RESTORE_PAGE)
            text.awaitPageCounter(text.counterFor(RESTORE_PAGE, TextFixtures.NORMAL_PAGES))

            val previous = Screens.awaitResumed(TextModeActivity::class.java) as TextModeActivity
            TestEnvironment.uiDevice.pressHome()
            Screens.awaitStage(previous, androidx.test.runner.lifecycle.Stage.STOPPED, Await.LONG_TIMEOUT_MS)
            TestEnvironment.onMainThread { previous.recreate() }

            returnToTask()
            text.awaitShown(Await.LONG_TIMEOUT_MS)
            Screens.awaitReplacement(previous, TextModeActivity::class.java)

            text.awaitPageCounter(text.counterFor(RESTORE_PAGE, TextFixtures.NORMAL_PAGES))
            val first = text.firstVisiblePosition()
            assertEquals(
                "the restored view must show the page it was left on",
                text.labelFor(RESTORE_PAGE),
                text.pageLabel(first),
            )
            assertTrue(
                "the restored page must reach non empty text",
                !text.awaitPageText(first).pageText.isNullOrBlank(),
            )

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitTopIs(MainActivity::class.java)
            reader.awaitPageIndex(RESTORE_PAGE - 1)
            val restoredPageIndex: Int? = RESTORE_PAGE - 1
            assertEquals(
                "the restored page must carry back into the reader",
                restoredPageIndex,
                reader.readState().page,
            )
        }
    }

    @Test
    @TestId("TXT-49")
    fun theControlsHideAfterTheDelayAndATapTogglesThem() {
        SettingsSeeder.seed(Preferences.hideDelayKey, SHORT_HIDE_DELAY_MS)

        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.awaitReaderSettled()
            text.openFromReaderMenu()

            Await.until("the controls card is visible after the initial load", Await.LONG_TIMEOUT_MS) {
                text.controlsVisibility() == View.VISIBLE
            }
            text.awaitControlsGone(HIDE_WINDOW_MS)

            text.tapPageArea()
            Await.until("the controls card returns after a tap") { text.controlsVisibility() == View.VISIBLE }

            text.tapPageArea()
            Await.until("the controls card hides again on the next tap") {
                text.controlsVisibility() == View.GONE
            }

            text.finish()
        }
    }

    private fun openTextModeWithoutWaiting() {
        PdfExtractorDebug.blockNextTextModeSetup()
        text.openReaderActionsDialog()
        text.tapReaderTile(text.string(R.string.text_mode))
        text.awaitShown(Await.LONG_TIMEOUT_MS)
        Await.until("the loading text mode window has input focus", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.onMainThread {
                TestEnvironment.resumedActivityOfType(TextModeActivity::class.java)?.hasWindowFocus() == true
            }
        }
    }

    private fun awaitStillLoading() {
        Await.until("text mode setup is blocked after opening its extractor", Await.LONG_TIMEOUT_MS) {
            PdfExtractorDebug.isTextModeSetupBlocked()
        }
        Await.until("text mode either shows its initial progress bar or finishes loading") {
            text.progressVisibility() == View.VISIBLE || text.snapshot(0)?.pageText?.isNotBlank() == true
        }
        val snapshot = text.snapshot(0)
        assertTrue(
            "text mode finished loading before the test could interrupt it, its first page already reads " +
                CapturedReflow.literal(snapshot?.pageText?.toString()?.take(SNAPSHOT_PREVIEW)) +
                ". The setup gate must keep text mode loading until the test closes it.",
            snapshot == null || !snapshot.pageTextVisible || snapshot.pageText.isNullOrBlank(),
        )
        assertEquals(
            "text mode must still be showing its initial progress bar when the test interrupts it",
            View.VISIBLE as Int?,
            text.progressVisibility(),
        )
    }

    private fun returnToTask() {
        val manager = TestEnvironment.targetContext.getSystemService(ActivityManager::class.java)
            ?: throw ConditionTimeoutException("The target context exposes no ActivityManager")
        val task = manager.appTasks.firstOrNull()
        if (task != null) {
            runCatching { task.moveToFront() }
        }
        if (waitForTextMode()) return

        TestEnvironment.uiDevice.pressRecentApps()
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.uiDevice.pressRecentApps()
        TestEnvironment.uiDevice.waitForIdle()
        if (waitForTextMode()) return

        throw ConditionTimeoutException(
            "Neither ActivityManager.AppTask.moveToFront nor the recents switch restored the text-mode task."
        )
    }

    private fun waitForTextMode(): Boolean = runCatching {
        Await.until("text mode is restored", Await.DEFAULT_TIMEOUT_MS) {
            TestEnvironment.resumedActivityOfType(TextModeActivity::class.java) != null
        }
    }.isSuccess

    private companion object {
        const val ROTATION_PAGE = 8
        const val RESTORE_PAGE = 12
        const val SHORT_HIDE_DELAY_MS = 1_000
        const val HIDE_WINDOW_MS = 6_000L
        const val SILENCE_WINDOW_MS = 2_000L
        const val PIXEL_TOLERANCE = 0.5f
        const val FLOAT_TOLERANCE = 0.001f
        const val SNAPSHOT_PREVIEW = 60
    }
}
