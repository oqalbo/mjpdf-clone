// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.MetadataWorkerTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.data.entity.fingerprint
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ScanLifecycle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@ExpectsProcessDeath
@TestId("SCAN-37")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class Scan37ProcessDeathTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()

    @Test
    @TestDuration(DurationCategory.SHORT)
    fun phaseOneCommitsOneResultAndLeavesDurablePendingWork() {
        PhaseRun.requirePhase(SCENARIO, 1)

        val huge = ScanFixtures.seededTree(ScanFixtures.HUGE)
        ScanFixtures.scanTree(huge)
        ScanPrefs.seedSelected(huge)
        MetadataWorkerTestHooks.pauseAfterCommittedResults(1)
        val beforeGeneration = ScanLifecycle.generation()

        home.launchFromLauncher(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        ScanLifecycle.awaitDiscoveryFinishedAfter(beforeGeneration, Await.VERY_LONG_TIMEOUT_MS)
        val rows = Await.untilValue(
            "one page count is committed while later durable rows remain pending",
            Await.VERY_LONG_TIMEOUT_MS,
            read = { ScanCache.under(huge) },
        ) { current ->
            current.any { it.metadataState == ScannedPdfMetadataState.COMPLETE } &&
                current.any { it.metadataState == ScannedPdfMetadataState.PENDING }
        }
        val completed = rows.first { it.metadataState == ScannedPdfMetadataState.COMPLETE }
        assertTrue("The committed result has an exact positive page count", completed.pageCount > 0)
        assertTrue("The committed result has a durable identity", !completed.hash.isNullOrEmpty())
        marker(huge).writeText(
            listOf(completed.path, completed.pageCount.toString(), completed.hash.orEmpty()).joinToString("\n")
        )
        assertTrue("The phase marker is durable before the host kills the app", marker(huge).isFile)

        val paths = rows.map { it.path }
        assertEquals("Partial discovery never duplicates a durable path", paths.size, paths.toSet().size)
        paths.forEach { path ->
            assertTrue("Every surviving row points at a file that exists on disk: $path", File(path).exists())
        }
    }

    @Test
    fun phaseTwoReconstructsOnlyPendingWorkAndCompletesTheScan() {
        PhaseRun.requirePhase(SCENARIO, 2)

        val huge = ScanFixtures.seededTree(ScanFixtures.HUGE)
        MetadataWorkerTestHooks.reset()
        val markerParts = marker(huge).readLines()
        assertEquals("The process-death marker contains path, page count, and hash", 3, markerParts.size)
        val preservedBeforeLaunch = ScanCache.byPath(markerParts[0])
            ?: throw AssertionError("The phase-one completed row was not durable")
        assertEquals(ScannedPdfMetadataState.COMPLETE, preservedBeforeLaunch.metadataState)
        assertEquals(markerParts[1].toInt(), preservedBeforeLaunch.pageCount)
        assertEquals(markerParts[2], preservedBeforeLaunch.hash)
        val preservedFingerprint = preservedBeforeLaunch.fingerprint
        val preservedDispatchBefore = ScanDebugSignals.dispatchCount(preservedFingerprint)
        val beforeGeneration = ScanLifecycle.generation()

        home.launchFromLauncher(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        ScanLifecycle.awaitDiscoveryFinishedAfter(beforeGeneration, Await.VERY_LONG_TIMEOUT_MS)
        ScanLifecycle.awaitCompletedAfter(beforeGeneration, Await.VERY_LONG_TIMEOUT_MS)

        val rows = ScanCache.awaitCountUnder(huge, ScanFixtures.HUGE_COUNT, Await.VERY_LONG_TIMEOUT_MS)
        assertEquals(
            "The resumed pass leaves no duplicate paths behind",
            rows.size,
            rows.map { it.path }.toSet().size,
        )
        assertTrue(
            "Every readable fixture reaches COMPLETE with a real page count",
            rows.all {
                it.metadataState == ScannedPdfMetadataState.COMPLETE &&
                    it.pageCount > 0 &&
                    !it.hash.isNullOrEmpty()
            },
        )
        val preservedAfterSettlement = ScanCache.byPath(markerParts[0])!!
        assertEquals("The already committed exact page count is preserved", markerParts[1].toInt(), preservedAfterSettlement.pageCount)
        assertEquals("The already committed identity is preserved", markerParts[2], preservedAfterSettlement.hash)
        assertEquals(
            "Main-process reconstruction dispatches only PENDING rows",
            preservedDispatchBefore,
            ScanDebugSignals.dispatchCount(preservedFingerprint),
        )
    }

    private fun marker(root: File) = File(root, MARKER_NAME)

    private companion object {
        const val SCENARIO = "SCAN-37"
        const val MARKER_NAME = ".scan37-completed"
    }
}
