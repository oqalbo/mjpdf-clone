// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@ExpectsProcessDeath
@TestId("ART-58")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PrintStagingColdStartTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @Test
    fun phaseOneStageThreeFilesForPrintAndOneForSharing() {
        PhaseRun.requirePhase(SCENARIO, 1)

        AppDirectories.printStaging.mkdirs()
        AppDirectories.shareCache.mkdirs()
        AppDirectories.printStaging.listFiles()?.forEach { it.delete() }
        AppDirectories.shareCache.listFiles()?.forEach { it.delete() }

        val document = ArtifactFixtures.require(ArtifactFixtures.SMALL)
        AppDirectories.writePlaceholder(AppDirectories.printStaging, FIRST_STAGED, document.readBytes())
        AppDirectories.writePlaceholder(AppDirectories.printStaging, SECOND_STAGED)
        AppDirectories.writePlaceholder(AppDirectories.printStaging, THIRD_STAGED)
        AppDirectories.writePlaceholder(AppDirectories.shareCache, KEPT_CARD)

        assertEquals(
            "phase one must leave three staged print files behind for the cold start to find",
            listOf(FIRST_STAGED, SECOND_STAGED, THIRD_STAGED).sorted(),
            AppDirectories.namesIn(AppDirectories.printStaging),
        )
        assertTrue(
            "one of the staged files must be a whole copy of a real document, so a cold start that keeps it " +
                "is keeping the user's book and not an empty placeholder",
            AppDirectories.filesIn(AppDirectories.printStaging)
                .first { it.name == FIRST_STAGED }.length() == document.length(),
        )
        assertEquals(
            "phase one must leave exactly one quote card behind, which the cold start must not touch",
            listOf(KEPT_CARD),
            AppDirectories.namesIn(AppDirectories.shareCache),
        )
    }

    @Test
    fun phaseTwoAssertTheColdStartClearedPrintStagingAndLeftTheShareCache() {
        PhaseRun.requirePhase(SCENARIO, 2)

        assertEquals(
            "App.onCreate calls PrintController.sweepStaging, which deletes everything in the print staging " +
                "directory regardless of age, so a document staged for printing may never survive a restart. " +
                "The directory still holds " + AppDirectories.namesIn(AppDirectories.printStaging),
            emptyList<String>(),
            AppDirectories.namesIn(AppDirectories.printStaging),
        )
        assertEquals(
            "the share cache has its own age based policy and must be untouched by the cold start sweep, so " +
                "the card phase one wrote must still be there",
            listOf(KEPT_CARD),
            AppDirectories.namesIn(AppDirectories.shareCache),
        )
    }

    private companion object {
        const val SCENARIO = "ART-58"
        const val FIRST_STAGED = "print-1000000000001.pdf"
        const val SECOND_STAGED = "print-1000000000002.pdf"
        const val THIRD_STAGED = "print-1000000000003.pdf"
        const val KEPT_CARD = "MJ_quote_2026_08_07-10_00_00.png"
    }
}
