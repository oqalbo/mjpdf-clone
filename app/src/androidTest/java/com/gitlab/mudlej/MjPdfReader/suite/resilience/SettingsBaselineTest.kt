// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SettingsBaselineTest : ResilienceTestCase() {

    @Test
    @TestId("RES-17")
    fun theAlwaysHideMarginsBaselineSurvivesRecreation() {
        val file = fixture(MARGINS_FIXTURE)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            awaitReaderReady()

            openSettingsFromReader()
            assertEquals(
                "opening settings must capture the margin setting as it was at that moment. The reader is " +
                    "behind the settings screen and is therefore live rather than resumed, so its view model " +
                    "has to be reached through the live instance",
                false,
                readerViewModelBehindSettings(),
            )

            val readerBeforeRecreation = ReaderProbe.liveReaderOrNull()
                ?: throw AssertionError("no reader activity is alive behind the settings screen")
            Rotation.toLandscape(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
            Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
            assertEquals(
                "recreating the foreground Settings activity must retain the stopped reader that owns the " +
                    "captured baseline",
                System.identityHashCode(readerBeforeRecreation),
                System.identityHashCode(ReaderProbe.liveReaderOrNull()),
            )
            assertEquals(
                "the retained reader must preserve its baseline across the foreground Settings recreation",
                false,
                readerViewModelBehindSettings(),
            )

            SettingsSeeder.seed(Preferences.alwaysHideMarginsKey, true)
            TestEnvironment.uiDevice.pressBack()

            Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReaderReady()
            val after = settledFullQuality()

            assertNull(
                "the baseline must be consumed and cleared once the reader has compared against it",
                ReaderProbe.viewModel().alwaysHideMarginsAtSettingsOpen,
            )
            assertEquals(
                "the changed setting must enable margin cropping after the retained baseline is consumed",
                true,
                ReaderProbe.viewModel().cropMarginsEnabled,
            )
            Await.until("the restored reader applies its detected crop margins", Await.VERY_LONG_TIMEOUT_MS) {
                ReaderTestHooks.cropDetectionRunOutcome == ReaderTestHooks.CROP_APPLIED
            }
            assertTrue(
                "the PDFView must apply the crop rather than only updating its view model",
                ReaderProbe.onReader { activity ->
                    activity.findViewById<com.github.barteksc.pdfviewer.PDFView>(R.id.pdfView)
                        .isCropMarginsEnabled
                },
            )
            assertTrue(
                "the reload must settle at full quality, not leave a preview on screen",
                after.isFullyRendered,
            )
        }
    }

    private fun readerViewModelBehindSettings(): Boolean? {
        val readerActivity = ReaderProbe.liveReaderOrNull()
            ?: throw AssertionError("no reader activity is alive behind the settings screen")
        return ReaderProbe.viewModel(readerActivity).alwaysHideMarginsAtSettingsOpen
    }

    private fun openSettingsFromReader() {
        onView(withId(R.id.readerActionsOption)).perform(ViewActions.click())
        val label = TestEnvironment.targetContext.getString(R.string.settings)
        val row = TestEnvironment.uiDevice.wait(
            androidx.test.uiautomator.Until.findObject(By.text(label)),
            Await.DEFAULT_TIMEOUT_MS,
        ) ?: throw AssertionError("the reader actions menu offers no \"$label\" row")
        row.click()
        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private companion object {
        const val MARGINS_FIXTURE = "margins.pdf"
    }
}
