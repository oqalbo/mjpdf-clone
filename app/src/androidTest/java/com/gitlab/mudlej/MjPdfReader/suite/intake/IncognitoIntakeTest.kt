// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoIntakeTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val picker = DocumentPickerRobot()

    @Test
    @TestId("INTAKE-23")
    @TestDuration(DurationCategory.SHORT)
    fun anIncognitoOpenTakesNoPermissionAndCachesNoPreview() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val identity = IntakeFixtures.expectedIdentity(fixture)

        ActivityScenario.launch<MainActivity>(IntakeReader.incognitoViewIntent(Uri.fromFile(fixture))).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitFullQualityRender()

            assertTrue(
                "the incognito indicator must be visible while reading privately",
                IntakeReader.isIncognitoIconVisible(),
            )

            ReaderNavigation.jumpToPage(reader, INCOGNITO_PAGE)
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        assertEquals(
            "an incognito open must leave no standing read permission",
            emptyList<String>(),
            IntakeReader.persistedPermissions().map { it.uri.toString() },
        )
        Await.assertThroughout(
            condition = "no preview for this document is written to disk",
            windowMs = IntakeDb.QUIET_WINDOW_MS,
        ) { !IntakeReader.hasPreviewsForIdentity(identity) }
    }

    @Test
    @TestId("INTAKE-24")
    fun incognitoSurvivesARotationDuringThePicker() {
        val picked = Fixtures.pickerFixture(IntakeFixtures.NORMAL).name

        IntakeReader.startFromOutside(IntakeReader.incognitoViewIntent(null))
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        picker.awaitShown(Await.LONG_TIMEOUT_MS)

        TestEnvironment.uiDevice.setOrientationLeft()
        TestEnvironment.uiDevice.waitForIdle()
        picker.awaitShown(Await.LONG_TIMEOUT_MS)

        picker.pickFromDownloads(picked)
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded()
        reader.awaitFullQualityRender()

        assertTrue(
            "a configuration change must not downgrade the session out of incognito",
            IntakeReader.isIncognitoIconVisible(),
        )
        IntakeDb.holdEmpty()
        assertEquals(
            "an incognito pick must leave no standing read permission",
            emptyList<String>(),
            IntakeReader.persistedPermissions().map { it.uri.toString() },
        )
    }

    private companion object {
        const val INCOGNITO_PAGE = 3
    }
}
