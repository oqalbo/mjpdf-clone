// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FirstPageIntakeTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("INTAKE-20")
    fun alwaysOpenAtFirstPageOpensAtPageOne() {
        val fixture = seedDocumentStoppedDeepInTheFile()

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            val state = reader.awaitFullQualityRender()

            assertEquals(
                "Always Open At First Page must land on the first page, zero based",
                0 as Int?,
                state.page,
            )
        }
    }

    @Test
    @TestId("INTAKE-21")
    fun alwaysOpenAtFirstPageDoesNotEraseTheStoredPosition() {
        val fixture = seedDocumentStoppedDeepInTheFile()
        val hash = IntakeFixtures.expectedIdentity(fixture)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitFullQualityRender()
            reader.awaitPageIndex(0)
        }

        Await.assertThroughout(
            condition = "the stored position stays at page index $STORED_PAGE",
            windowMs = IntakeDb.QUIET_WINDOW_MS,
        ) { IntakeDb.record(hash)?.pageNumber == STORED_PAGE }
    }

    private fun seedDocumentStoppedDeepInTheFile(): File {
        SettingsSeeder.seed(Preferences.alwaysOpenAtFirstPageKey, true)
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        IntakeDb.insertRecord(
            IntakeFixtures.expectedIdentity(fixture),
            Uri.fromFile(fixture),
            pageNumber = STORED_PAGE,
            fileName = "normal",
            length = IntakeFixtures.NORMAL_PAGES,
        )
        return fixture
    }

    private companion object {
        const val STORED_PAGE = 6
    }
}
