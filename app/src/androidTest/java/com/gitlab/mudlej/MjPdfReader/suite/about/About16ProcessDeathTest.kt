// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.RealOnboarding
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@ExpectsProcessDeath
@RealOnboarding
@TestId("ABOUT-16")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class About16ProcessDeathTest {

    @get:Rule
    val rules = SuiteRules.standard()


    @Test
    fun phaseOneAssertCleanInstallOnboardingState() {
        PhaseRun.requirePhase(SCENARIO, 1)
        assertTrue("the clean install must still require onboarding", Onboarding.firstInstall())
        assertTrue(
            "the release notes flag must still be armed before the process-death scenario",
            Onboarding.showFeaturesDialog(),
        )
    }

    @Test
    fun phaseTwoAssertTheIntroCameBackOnTheSamePage() {
        PhaseRun.requirePhase(SCENARIO, 2)
        val stateDirectory = requireNotNull(PhaseRun.stateDirectory()) {
            "The multi-phase runner did not provide its state directory"
        }
        assertTrue(
            "the host did not observe the restored intro page and finish onboarding",
            stateDirectory.resolve(COMPLETION_MARKER).isFile,
        )
    }

    private companion object {
        const val SCENARIO = "ABOUT-16"
        const val COMPLETION_MARKER = "ui-What-s-New-Library"
    }
}
