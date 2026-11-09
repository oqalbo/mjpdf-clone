// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoSharedCopyTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()

    @Before
    fun seedTheMostPermissiveCopyMode() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.keepSharedCopiesKey to true,
                Preferences.sharedCopyModeKey to SharedCopyMode.ALWAYS_COPY.name,
            )
        )
        StubSharerProvider.clear()
        PrivacyStores.deleteDurableCopies()
    }

    @After
    fun clearTheStubProvider() {
        StubSharerProvider.clear()
        PrivacyStores.deleteDurableCopies()
    }

    @Test
    @TestId("PRIV-03")
    fun anIncognitoOpenOfATemporaryContentUriWritesNoDurableCopy() {
        val uri = publishFixture()

        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(uri).putExtra(PDF.incognitoKey, true)
        ).use {
            reader.awaitReady()
            reader.awaitIdentity()

            PrivacyStores.holdNoDurableCopy()

            assertEquals(
                "no private durable copy may be written for an incognito share",
                emptyList<String>(),
                PrivacyStores.importedFiles().map { it.name },
            )
            assertFalse(
                "no durable copy of ${SHARED_NAME} may appear under Documents/MJ PDF",
                PrivacyStores.durableCopyNames().any { it.startsWith(SHARED_STEM) },
            )
            assertEquals(
                "no partial copy may be left behind under files/",
                emptyList<String>(),
                PrivacyStores.partFilesUnderFiles().map { it.absolutePath },
            )
            assertFalse(
                "the copy consent dialog must never appear, incognito short circuits before the setting is read",
                TestEnvironment.uiDevice.hasObject(By.text(string(R.string.copy_consent_title))),
            )
            Snackbars.assertNoneWithin()
        }

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(uri)).use {
            reader.awaitReady()
            reader.awaitIdentity()

            Await.until(
                "the same open without the incognito extra does write a durable copy",
                Await.LONG_TIMEOUT_MS,
            ) { copyExists() }
            assertTrue(
                "the control pass must produce a copy, otherwise the incognito assertion proves nothing. " +
                    "Documents/MJ PDF holds ${PrivacyStores.durableCopyNames()} and files/imported holds " +
                    "${PrivacyStores.importedFiles().map { it.name }}",
                copyExists(),
            )
        }
    }

    private fun copyExists(): Boolean =
        PrivacyStores.durableCopyNames().any { it.startsWith(SHARED_STEM) } ||
            PrivacyStores.importedFiles().any { it.name.startsWith(SHARED_STEM) }

    private fun publishFixture(): Uri {
        val bytes = PrivacyFixtures.file(PrivacyFixtures.NORMAL).readBytes()
        return StubSharerProvider.publish(SHARED_NAME, bytes)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val SHARED_NAME = "priv-shared-copy.pdf"
        const val SHARED_STEM = "priv-shared-copy"
    }
}
