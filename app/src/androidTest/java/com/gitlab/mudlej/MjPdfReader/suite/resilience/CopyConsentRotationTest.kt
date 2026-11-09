// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import androidx.test.platform.view.inspector.WindowInspectorCompat
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifacts
import android.os.Environment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
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
@TestDuration(DurationCategory.SHORT)
class CopyConsentRotationTest : ResilienceTestCase() {

    private val durableDirectory =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DURABLE_FOLDER)

    @Before
    fun armAskMode() {
        StubSharerProvider.clear()
        SettingsSeeder.seed(Preferences.sharedCopyModeKey, SharedCopyMode.ASK.name)
    }

    @After
    fun removeCopiesThisTestMade() {
        StubSharerProvider.clear()
        durableDirectory.listFiles().orEmpty()
            .filter { it.name.startsWith(SHARED_NAME_PREFIX) }
            .forEach { Fixtures.delete(it) }
    }

    @Test
    @TestId("RES-08")
    fun theCopyConsentPromptSurvivesRotation() {
        val source = fixture(ResilienceFixtures.NORMAL)
        val sharedUri = StubSharerProvider.publish(SHARED_NAME, source.readBytes())
        val before = durableFileNames()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(sharedUri)).use {
            val portraitWidth = awaitConsentDialog(Configuration.ORIENTATION_PORTRAIT)
            reader.awaitLoaded()
            reader.awaitFullQualityRender()
            val hash = reader.awaitFileHashResolved()

            TestEnvironment.uiDevice.setOrientationLeft()
            awaitConsentDialog(Configuration.ORIENTATION_LANDSCAPE, portraitWidth)

            TestEnvironment.uiDevice.setOrientationNatural()
            awaitConsentDialog(Configuration.ORIENTATION_PORTRAIT, portraitWidth)

            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(ViewActions.click())

            val copied = Await.untilValue(
                condition = "a durable copy named $SHARED_NAME appears under ${durableDirectory.absolutePath}",
                timeoutMs = Await.LONG_TIMEOUT_MS,
                read = { durableFileNames() - before },
            ) { it.isNotEmpty() }
            assertEquals(
                "accepting the prompt must produce exactly one durable copy, but it produced $copied",
                1,
                copied.size,
            )

            val record = Records.awaitRow(
                hash,
                "the record for the shared document repoints at the durable copy",
                Await.LONG_TIMEOUT_MS,
            ) { it != null && it.uri.toString() != sharedUri.toString() }!!
            assertTrue(
                "the record must no longer point at the temporary shared URI, but it reads ${record.uri}",
                record.uri.toString() != sharedUri.toString(),
            )
            assertBytesReadable(record.uri, source.length())
        }
    }

    private fun awaitConsentDialog(orientation: Int, portraitWidth: Int? = null): Int {
        val title = TestEnvironment.targetContext.getString(R.string.copy_consent_title)
        Await.until("the copy consent dialog is showing in orientation $orientation", Await.LONG_TIMEOUT_MS) {
            val configured = TestEnvironment.onMainThread {
                TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                    ?.resources?.configuration?.orientation == orientation
            }
            val display = TestEnvironment.uiDevice
            val landscape = display.displayWidth > display.displayHeight
            configured && landscape == (orientation == Configuration.ORIENTATION_LANDSCAPE) &&
                display.hasObject(By.text(title))
        }
        try {
            val width = Await.untilValue(
                "the consent window has completed its $orientation layout relative to portrait width $portraitWidth",
                Await.LONG_TIMEOUT_MS,
                read = { consentWindowWidth() },
            ) { width ->
                width > 0 && (portraitWidth == null ||
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) width > portraitWidth
                    else width == portraitWidth)
            }
            Await.untilNoThrow("the consent button is fully laid out after rotation", Await.LONG_TIMEOUT_MS) {
                onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()))
            }
            return width
        } catch (error: Throwable) {
            runCatching {
                val directory = FailureArtifacts.directoryFor("RES-08")
                TestEnvironment.uiDevice.takeScreenshot(File(directory, "before-close.png"))
                TestEnvironment.uiDevice.dumpWindowHierarchy(File(directory, "before-close.xml"))
                File(directory, "dialog-width.txt").writeText("orientation=$orientation portraitWidth=$portraitWidth actualWidth=${consentWindowWidth()}")
            }
            throw error
        }
    }

    private fun consentWindowWidth(): Int = TestEnvironment.onMainThread {
        WindowInspectorCompat.getGlobalWindowViews().firstOrNull { root ->
            root.context.packageName == TestEnvironment.targetPackage && root.hasWindowFocus() &&
                !root.isLayoutRequested && root.findViewById<View>(android.R.id.button1)?.isShown == true
        }?.width ?: 0
    }

    private fun durableFileNames(): Set<String> =
        durableDirectory.listFiles().orEmpty().map { it.name }.toSet()

    private fun assertBytesReadable(uri: Uri, expectedLength: Long) {
        val length = TestEnvironment.targetContext.contentResolver.openInputStream(uri).use { stream ->
            stream?.readBytes()?.size?.toLong()
        }
        assertEquals(
            "the copy the record points at must hold the whole source document",
            expectedLength,
            length,
        )
    }

    private companion object {
        const val DURABLE_FOLDER = "MJ PDF"
        const val SHARED_NAME_PREFIX = "res08"
        const val SHARED_NAME = "res08-consent.pdf"
    }
}
