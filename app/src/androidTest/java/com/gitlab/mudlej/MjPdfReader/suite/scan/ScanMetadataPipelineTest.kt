// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.core.debug.MetadataWorkerTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataFailure
import com.gitlab.mudlej.MjPdfReader.data.entity.fingerprint
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ScanLifecycle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.home.LibraryScanner
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.MetadataMemorySample
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.MetadataProtocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ScanMetadataPipelineTest : ScanTestCase() {
    @After
    fun resetMetadataHooks() = MetadataWorkerTestHooks.reset()

    @Test
    @TestId("SCAN-43")
    fun everyReadablePdfGetsMetadataWithoutViewportOrOpen() {
        grantAccess()
        val root = testDirectory()
        val normal = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "a-normal.pdf")
        val large = ScanFixtures.copySharedAs(ScanFixtures.LARGE, root, "b-large.pdf")
        ScanPrefs.seedSelected(root)
        MetadataWorkerTestHooks.pauseDispatch()

        val baseline = scanBaseline()
        launchHome()
        ScanCache.awaitRowExists(normal)
        ScanCache.awaitRowExists(large)
        Await.until("both discovered PDFs are visible before metadata is released", Await.VERY_LONG_TIMEOUT_MS) {
            val paths = home.library().items().mapNotNull { it.uri.path }.toSet()
            normal.absolutePath in paths && large.absolutePath in paths
        }
        assertTrue("required-metadata progress remains visible", home.library().scanProgress() != null)
        val orderBefore = home.library().items().map { it.stableKey }

        LibraryScanner.getInstance(TestEnvironment.targetContext).resumeMetadataDispatch()
        ScanCache.awaitComplete(normal, 20, Await.VERY_LONG_TIMEOUT_MS)
        ScanCache.awaitComplete(large, ScanFixtures.LARGE_PAGE_COUNT, Await.VERY_LONG_TIMEOUT_MS)
        awaitScanComplete(baseline)
        assertEquals("Page-count updates and settlement retain deterministic key order", orderBefore, home.library().items().map {
            it.stableKey
        })
        assertEquals("The metadata process never opens two scanner documents", 1, ScanDebugSignals.snapshot().maxActiveMetadataDocuments)
    }

    @Test
    @TestId("SCAN-44")
    fun workerDeathRetriesOnlyTheInFlightFingerprint() {
        grantAccess()
        val root = testDirectory()
        val prior = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "a-prior.pdf")
        val victim = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "b-victim.pdf")
        val later = ScanFixtures.copySharedAs(ScanFixtures.LARGE, root, "c-later.pdf")
        ScanPrefs.seedSelected(root)
        MetadataWorkerTestHooks.pauseDispatch()
        val debugBefore = ScanDebugSignals.snapshot()
        val discoveryBefore = ScanDebugSignals.discoveryRuns()
        val mainPid = debugBefore.mainPid
        val baseline = scanBaseline()

        startBackendScan()
        val priorRow = ScanCache.awaitRowExists(prior)
        val victimRow = ScanCache.awaitRowExists(victim)
        val laterRow = ScanCache.awaitRowExists(later)
        MetadataWorkerTestHooks.killOnce(victimRow.fingerprint)
        LibraryScanner.getInstance(TestEnvironment.targetContext).resumeMetadataDispatch()

        val priorComplete = ScanCache.awaitComplete(prior, 20, Await.VERY_LONG_TIMEOUT_MS)
        ScanCache.awaitComplete(victim, 20, Await.VERY_LONG_TIMEOUT_MS)
        ScanCache.awaitComplete(later, ScanFixtures.LARGE_PAGE_COUNT, Await.VERY_LONG_TIMEOUT_MS)
        ScanLifecycle.awaitCompletedAfter(baseline.generation, Await.VERY_LONG_TIMEOUT_MS)
        assertEquals("The already committed count survives worker death", 20, priorComplete.pageCount)
        assertEquals("Only the interrupted fingerprint is retried", 1, ScanDebugSignals.dispatchCount(priorRow.fingerprint))
        assertEquals("Only the interrupted fingerprint is retried", 2, ScanDebugSignals.dispatchCount(victimRow.fingerprint))
        assertEquals("Later work is dispatched once", 1, ScanDebugSignals.dispatchCount(laterRow.fingerprint))
        assertEquals("Metadata death never restarts discovery", 1, ScanDebugSignals.discoveryRuns() - discoveryBefore)
        assertEquals("The main process survives", mainPid, ScanDebugSignals.snapshot().mainPid)
        val freshInstances = ScanDebugSignals.snapshot().workerInstances - debugBefore.workerInstances
        assertTrue("A fresh metadata worker instance reconnects after death", freshInstances.size >= 2)
        assertTrue(
            "The retry reconnects with a fresh worker PID",
            (ScanDebugSignals.snapshot().workerPids - debugBefore.workerPids).size >= 2,
        )
        assertTrue("Every metadata PID differs from the main PID", ScanDebugSignals.snapshot().workerPids.all { it != mainPid })
        val paths = ScanCache.under(root).map { it.path }
        assertEquals("The final library contains no duplicate paths", paths.size, paths.toSet().size)
    }

    @Test
    @TestId("SCAN-45")
    fun repeatWorkerKillerFailsAndLaterRowsContinue() {
        grantAccess()
        val root = testDirectory()
        val victim = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "a-hostile.pdf")
        val later = ScanFixtures.copySharedAs(ScanFixtures.LARGE, root, "z-later.pdf")
        ScanPrefs.seedSelected(root)
        MetadataWorkerTestHooks.pauseDispatch()
        val debugBefore = ScanDebugSignals.snapshot()
        val mainPid = debugBefore.mainPid
        val baseline = scanBaseline()

        launchHome()
        val victimRow = ScanCache.awaitRowExists(victim)
        val laterRow = ScanCache.awaitRowExists(later)
        MetadataWorkerTestHooks.killAlways(victimRow.fingerprint)
        LibraryScanner.getInstance(TestEnvironment.targetContext).resumeMetadataDispatch()

        val failed = ScanCache.awaitFailed(
            victim,
            ScannedPdfMetadataFailure.WORKER_CRASH,
            Await.VERY_LONG_TIMEOUT_MS,
        )
        assertEquals(2, failed.metadataWorkerDeaths)
        assertEquals("A failed page count is unambiguously unavailable", 0, failed.pageCount)
        ScanCache.awaitComplete(later, ScanFixtures.LARGE_PAGE_COUNT, Await.VERY_LONG_TIMEOUT_MS)
        awaitScanComplete(baseline)
        assertEquals(2, ScanDebugSignals.dispatchCount(victimRow.fingerprint))
        assertEquals(1, ScanDebugSignals.dispatchCount(laterRow.fingerprint))
        assertEquals(mainPid, ScanDebugSignals.snapshot().mainPid)
        assertTrue(
            "Both retries used fresh worker instances",
            (ScanDebugSignals.snapshot().workerInstances - debugBefore.workerInstances).size >= 2,
        )
        assertTrue(
            "Repeated worker death is observed across fresh PIDs",
            (ScanDebugSignals.snapshot().workerPids - debugBefore.workerPids).size >= 2,
        )
        assertTrue(ScanDebugSignals.snapshot().workerPids.all { it != mainPid })
        val paths = ScanCache.under(root).map { it.path }
        assertEquals(paths.size, paths.toSet().size)
    }

    @Test
    @TestId("SCAN-48")
    @TestDuration(DurationCategory.NORMAL)
    fun hungWorkerRetriesOnlyTheInFlightFingerprint() {
        assertHungWorkerRecovery(repeated = false)
    }

    @Test
    @TestId("SCAN-49")
    @TestDuration(DurationCategory.NORMAL)
    fun repeatedlyHungWorkerFailsButRemainsReadableAndLaterRowsComplete() {
        assertHungWorkerRecovery(repeated = true)
    }

    @Test
    @TestId("SCAN-50")
    @TestDuration(DurationCategory.NORMAL)
    fun metadataWorkerWaitingForAcknowledgementOutlivesWatchdog() {
        val first = ScanFixtures.sharedFile(ScanFixtures.NORMAL)
        val second = ScanFixtures.sharedFile(ScanFixtures.LARGE)
        MetadataWorkerBinding().use { binding ->
            val worker = binding.connect()
            binding.process(first)
            binding.awaitHealthyResult(first, 20, worker)

            SystemClock.sleep(MetadataWorkerBinding.WATCHDOG_MS + 500L)
            assertTrue("A delivered result disarms the extraction watchdog", binding.isAlive())
            assertEquals("Waiting for ACK never kills the worker", 0L, binding.deathAt.get())
            binding.acknowledge(first)
            binding.process(second)
            binding.awaitHealthyResult(second, ScanFixtures.LARGE_PAGE_COUNT, worker)
            binding.acknowledge(second)
        }
    }

    @Test
    @TestId("SCAN-51")
    fun unbindingHungMetadataWorkerKillsItBeforeWatchdog() {
        val file = ScanFixtures.sharedFile(ScanFixtures.NORMAL)
        val previousWorker = MetadataWorkerBinding().use { binding ->
            val worker = binding.connect()
            val startedAt = SystemClock.elapsedRealtime()
            binding.process(file, MetadataProtocol.DEBUG_HANG)
            SystemClock.sleep(250L)
            assertTrue("The hung extraction is still alive before unbinding", binding.isAlive())
            assertTrue("The hung extraction produces no result", binding.results.isEmpty())
            binding.unbind()
            Await.until("unbinding kills the hung worker", Await.LONG_TIMEOUT_MS) {
                binding.deathAt.get() != 0L
            }
            assertTrue(
                "Unbinding kills the worker before its five-second extraction deadline",
                binding.deathAt.get() - startedAt < MetadataWorkerBinding.WATCHDOG_MS,
            )
            assertTrue("The hung extraction never reports a result", binding.results.isEmpty())
            worker
        }

        MetadataWorkerBinding().use { binding ->
            val worker = binding.connect()
            assertNotEquals("A fresh binding gets a new process", previousWorker.pid, worker.pid)
            assertNotEquals("A fresh binding gets a new worker instance", previousWorker.instance, worker.instance)
            binding.process(file)
            binding.awaitHealthyResult(file, 20, worker)
            binding.acknowledge(file)
        }
    }

    @Test
    @TestId("SCAN-52")
    @TestDuration(DurationCategory.NORMAL)
    fun hungMetadataWorkerExpiresAtFiveSecondsWithoutReturningAResult() {
        val file = ScanFixtures.sharedFile(ScanFixtures.NORMAL)
        MetadataWorkerBinding().use { binding ->
            binding.connect()
            val startedAt = SystemClock.elapsedRealtime()
            binding.process(file, MetadataProtocol.DEBUG_HANG)

            Await.until("the bound hung worker reaches its deadline", MetadataWorkerBinding.WATCHDOG_MS + 3_000L) {
                binding.deathAt.get() != 0L
            }

            val elapsed = binding.deathAt.get() - startedAt
            assertTrue(
                "The watchdog must not expire early: ${elapsed}ms",
                elapsed >= MetadataWorkerBinding.WATCHDOG_MS - 100L,
            )
            assertTrue(
                "The watchdog expires promptly: ${elapsed}ms",
                elapsed < MetadataWorkerBinding.WATCHDOG_MS + 3_000L,
            )
            assertTrue("The timed-out extraction never delivers a result", binding.results.isEmpty())
        }
    }

    private fun startBackendScan() {
        LibraryScanner.getInstance(TestEnvironment.targetContext).refresh()
    }

    private fun assertHungWorkerRecovery(repeated: Boolean) {
        grantAccess()
        val root = testDirectory()
        val prior = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "a-prior.pdf")
        val victim = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "b-victim.pdf")
        val later = ScanFixtures.copySharedAs(ScanFixtures.LARGE, root, "c-later.pdf")
        ScanPrefs.seedSelected(root)
        MetadataWorkerTestHooks.pauseDispatch()
        val debugBefore = ScanDebugSignals.snapshot()
        val baseline = scanBaseline()

        startBackendScan()
        val priorRow = ScanCache.awaitRowExists(prior)
        val victimRow = ScanCache.awaitRowExists(victim)
        val laterRow = ScanCache.awaitRowExists(later)
        TestEnvironment.targetContext.contentResolver.notifyChange(
            android.provider.MediaStore.Files.getContentUri("external"), null, false,
        )
        if (repeated) {
            MetadataWorkerTestHooks.hangAlways(victimRow.fingerprint)
        } else {
            MetadataWorkerTestHooks.hangOnce(victimRow.fingerprint)
        }
        LibraryScanner.getInstance(TestEnvironment.targetContext).resumeMetadataDispatch()

        val priorComplete = ScanCache.awaitComplete(prior, 20, Await.LONG_TIMEOUT_MS)
        if (repeated) {
            val failed = ScanCache.awaitFailed(victim, ScannedPdfMetadataFailure.WORKER_CRASH, Await.LONG_TIMEOUT_MS)
            assertEquals("Two watchdog deaths exhaust the retry budget", 2, failed.metadataWorkerDeaths)
            assertEquals("Failed metadata has no page count", 0, failed.pageCount)
        } else {
            ScanCache.awaitComplete(victim, 20, Await.LONG_TIMEOUT_MS)
        }
        ScanCache.awaitComplete(later, ScanFixtures.LARGE_PAGE_COUNT, Await.LONG_TIMEOUT_MS)
        ScanLifecycle.awaitCompletedAfter(baseline.generation, Await.LONG_TIMEOUT_MS)

        assertEquals("Previously committed metadata survives the watchdog", priorComplete, ScanCache.byPath(prior))
        assertEquals("Prior work is never redispatched", 1, ScanDebugSignals.dispatchCount(priorRow.fingerprint))
        assertEquals("Only the hung fingerprint is retried", 2, ScanDebugSignals.dispatchCount(victimRow.fingerprint))
        assertEquals("Later work runs once", 1, ScanDebugSignals.dispatchCount(laterRow.fingerprint))
        assertEquals(0, ScanDebugSignals.workerDeathCount(priorRow.fingerprint))
        assertEquals(if (repeated) 2 else 1, ScanDebugSignals.workerDeathCount(victimRow.fingerprint))
        assertEquals(0, ScanDebugSignals.workerDeathCount(laterRow.fingerprint))
        assertEquals("Watchdog recovery does not repeat discovery", 1, ScanDebugSignals.discoveryRuns() - baseline.discoveryRuns)
        val debugAfter = ScanDebugSignals.snapshot()
        assertEquals("The main process survives", debugBefore.mainPid, debugAfter.mainPid)
        val expectedWorkers = if (repeated) 3 else 2
        assertTrue("Recovery uses fresh worker instances", (debugAfter.workerInstances - debugBefore.workerInstances).size >= expectedWorkers)
        assertTrue("Recovery uses fresh worker PIDs", (debugAfter.workerPids - debugBefore.workerPids).size >= expectedWorkers)
        assertTrue("Metadata runs outside the main process", debugAfter.workerPids.all { it != debugAfter.mainPid })
        val expectedPaths = setOf(prior.absolutePath, victim.absolutePath, later.absolutePath)
        val cachedPaths = ScanCache.under(root).map { it.path }
        assertEquals(expectedPaths, cachedPaths.toSet())
        assertEquals("The cache contains no duplicates", expectedPaths.size, cachedPaths.size)
        launchHome()
        home.library().awaitItemCount(3, Await.LONG_TIMEOUT_MS)
        assertEquals("All valid PDFs remain visible", expectedPaths, home.library().items().map { it.uri.path }.toSet())
        assertEquals("Scan progress finishes even after watchdog failure", null, home.library().scanProgress())

        val victimIndex = home.library().items().indexOfFirst { it.uri.path == victim.absolutePath }
        home.library().cellAt(victimIndex).click()
        val reader = ReaderRobot()
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        reader.assertPageCount(20)
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
        assertEquals("Opening the victim keeps the main process alive", debugBefore.mainPid, ScanDebugSignals.snapshot().mainPid)
    }

    @Test
    @TestId("SCAN-46")
    fun effectiveLowMemorySignalRecyclesWithoutCrashAccounting() {
        grantAccess()
        val root = testDirectory()
        val first = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "a-first.pdf")
        val second = ScanFixtures.copySharedAs(ScanFixtures.LARGE, root, "b-second.pdf")
        ScanPrefs.seedSelected(root)
        MetadataWorkerTestHooks.pauseDispatch()
        val debugBefore = ScanDebugSignals.snapshot()
        val recycleBefore = debugBefore.intentionalRecycles
        val baseline = scanBaseline()

        launchHome()
        val firstRow = ScanCache.awaitRowExists(first)
        val secondRow = ScanCache.awaitRowExists(second)
        MetadataWorkerTestHooks.injectMemorySample(
            MetadataMemorySample(1_000, 100, 2_000, true, 10, 10)
        )
        LibraryScanner.getInstance(TestEnvironment.targetContext).resumeMetadataDispatch()

        ScanCache.awaitComplete(first, 20, Await.VERY_LONG_TIMEOUT_MS)
        ScanCache.awaitComplete(second, ScanFixtures.LARGE_PAGE_COUNT, Await.VERY_LONG_TIMEOUT_MS)
        awaitScanComplete(baseline)
        val debugAfter = ScanDebugSignals.snapshot()
        assertEquals("One injected low-memory result causes one intentional recycle", 1, debugAfter.intentionalRecycles - recycleBefore)
        assertEquals("The policy reports the typed low-memory reason", "LOW_MEMORY", debugAfter.lastRecycleReason)
        assertEquals(0, ScanDebugSignals.workerDeathCount(firstRow.fingerprint))
        assertEquals(0, ScanDebugSignals.workerDeathCount(secondRow.fingerprint))
        assertTrue(
            "The remaining queue runs in a fresh worker instance and baseline",
            (debugAfter.workerInstances - debugBefore.workerInstances).size >= 2,
        )
        assertTrue(
            "Intentional recycle reconnects with a new PID",
            (debugAfter.workerPids - debugBefore.workerPids).size >= 2,
        )
        assertTrue(
            "Real and injected effective samples are both observable",
            debugAfter.realMemorySamples > debugBefore.realMemorySamples &&
                debugAfter.effectiveMemorySamples > debugBefore.effectiveMemorySamples,
        )
        assertNotEquals(debugAfter.mainPid, debugAfter.workerPid)
    }
}
