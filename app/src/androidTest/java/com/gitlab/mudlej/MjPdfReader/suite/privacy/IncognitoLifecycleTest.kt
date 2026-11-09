// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.VERY_LONG)
class IncognitoLifecycleTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()
    private val home = PrivacyHome()

    @After
    fun leaveNoTaskBehind() {
        reader.finishTask()
        home.picker.dismissIfShown()
    }

    @Test
    @TestId("PRIV-16")
    @TestDuration(DurationCategory.NORMAL)
    fun incognitoSurvivesReaderRotation() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            assertTrue("the session must start incognito", reader.isIncognitoIconVisible())

            turnLeft()
            reader.awaitReady()
            assertTrue("landscape must keep the session incognito", reader.isIncognitoIconVisible())

            turnNatural()
            reader.awaitReady()
            assertTrue("turning back must keep the session incognito", reader.isIncognitoIconVisible())

            reader.jumpToPageIndex(READ_TO_PAGE_INDEX)
            PrivacyStores.holdNoRecords()
            assertEquals(
                "a rotation must not turn an incognito session into a recorded one",
                0,
                PrivacyStores.recordCount(),
            )
        }
    }

    @Test
    @TestId("PRIV-17")
    fun incognitoSurvivesRotationWhileTheFilePickerIsOpen() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        home.launch()

        home.startIncognitoPick()
        turnLeft()
        home.picker.awaitShown(Await.LONG_TIMEOUT_MS)
        home.pickFromDownloads(PrivacyFixtures.NORMAL)

        reader.awaitReady()
        reader.awaitIdentity()
        assertTrue(
            "choosing open in incognito and then turning the phone must not open a recorded session",
            reader.isIncognitoIconVisible(),
        )
        PrivacyStores.holdNoRecords()
        assertEquals("the rotated incognito pick must record nothing", 0, PrivacyStores.recordCount())

        reader.finishTask()
        turnNatural()
        home.reopen()
        val recent = home.recentTitles()
        assertTrue(
            "the Recent tab must show no row for the rotated incognito pick, it lists $recent",
            !recent.contains(PrivacyFixtures.NORMAL_TITLE),
        )
    }

    @Test
    @TestId("PRIV-18")
    fun noPersistedReadGrantIsTakenWhenThePickerRotatesDuringAnIncognitoPick() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        home.launch()
        assertEquals(
            "the persisted grant list must start empty",
            emptyList<String>(),
            PrivacyStores.persistedUris(),
        )

        home.startIncognitoPick()
        turnLeft()
        home.picker.awaitShown(Await.LONG_TIMEOUT_MS)
        home.pickFromDownloads(PrivacyFixtures.NORMAL)

        reader.awaitReady()
        reader.awaitIdentity()

        PrivacyStores.holdNoPersistedGrants()
        assertEquals(
            "a rotation during an incognito pick must leave no permanent read access",
            emptyList<String>(),
            PrivacyStores.persistedUris(),
        )
    }

    private fun turnLeft() {
        TestEnvironment.uiDevice.setOrientationLeft()
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    private fun turnNatural() {
        TestEnvironment.uiDevice.setOrientationNatural()
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    private companion object {
        const val READ_TO_PAGE_INDEX = 2
    }
}
