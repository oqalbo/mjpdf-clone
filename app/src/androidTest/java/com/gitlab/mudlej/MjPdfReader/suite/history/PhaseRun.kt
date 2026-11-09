// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume

object PhaseRun {

    const val STATE_DIR_ARGUMENT = "mjpdfPhaseStateDir"
    const val SCENARIO_ARGUMENT = "mjpdfScenario"
    const val PHASE_INDEX_ARGUMENT = "mjpdfPhaseIndex"

    fun scenario(): String? = argument(SCENARIO_ARGUMENT)

    fun phaseIndex(): Int? = argument(PHASE_INDEX_ARGUMENT)?.toIntOrNull()

    fun stateDirectory(): File? = argument(STATE_DIR_ARGUMENT)?.let(::File)

    fun requirePhase(scenarioName: String, index: Int) {
        val runningScenario = scenario()
        Assume.assumeTrue(
            "This test is phase $index of the $scenarioName scenario and only the multi phase runner can drive it. " +
                "Run python3 testing/runner/run.py --phase-scenario $scenarioName.",
            runningScenario != null,
        )
        if (runningScenario != scenarioName) {
            throw AssertionError(
                "This phase belongs to $scenarioName but the runner is driving $runningScenario"
            )
        }
        val runningIndex = phaseIndex()
        if (runningIndex != index) {
            throw AssertionError(
                "This is phase $index of $scenarioName but the runner is on phase $runningIndex"
            )
        }
    }

    private fun argument(name: String): String? =
        InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }
}
