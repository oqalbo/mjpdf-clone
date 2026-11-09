// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanGenerationPlannerTest {
    @Test
    fun pendingFullRescanSurvivesEveryIncomingReason() {
        for (incoming in ScanRefreshReason.entries) {
            assertEquals(
                ScanRefreshReason.FULL_RESCAN,
                ScanGenerationPlanner.mergePending(ScanRefreshReason.FULL_RESCAN, incoming),
            )
        }
    }

    @Test
    fun incomingReasonReplacesEveryPendingReasonExceptFullRescan() {
        for (pending in listOf(null, ScanRefreshReason.FOREGROUND, ScanRefreshReason.OBSERVER, ScanRefreshReason.MANUAL)) {
            for (incoming in ScanRefreshReason.entries) {
                assertEquals(incoming, ScanGenerationPlanner.mergePending(pending, incoming))
            }
        }
    }

    @Test
    fun onlyCancelledIncompleteDiscoveryRetainsWalksWithinTheNewScope() {
        val walks = listOf(ScanWalk(listOf("/books"), listOf("/books/private")))
        val scope = listOf("/books/selected", "/books/private")
        for (reason in ScanRefreshReason.entries) {
            for (cancelled in listOf(false, true)) {
                for (complete in listOf(false, true)) {
                    val expected = if (reason != ScanRefreshReason.FULL_RESCAN && cancelled && !complete) {
                        listOf(ScanWalk(listOf("/books/selected"), listOf("/books/private")))
                    } else emptyList()
                    assertEquals(
                        "$reason cancelled=$cancelled complete=$complete",
                        expected,
                        ScanGenerationPlanner.retainedWalks(reason, cancelled, complete, walks, scope),
                    )
                }
            }
        }
    }

    @Test
    fun unconfiguredScanNeverShowsFindingProgress() {
        for (reason in ScanRefreshReason.entries) {
            for (walks in listOf(false, true)) {
                assertFalse(ScanGenerationPlanner.findingProgressVisible(false, reason, walks, false, "", null))
            }
        }
    }

    @Test
    fun fullRescanAndFilesystemWalksShowProgressEvenWithAnExistingIndexAndBaseline() {
        val roots = listOf("/books")
        val baseline = LibraryScanCoordinator.baselineScopeKey(roots)
        assertTrue(ScanGenerationPlanner.findingProgressVisible(true, ScanRefreshReason.FULL_RESCAN, false, true, baseline, roots))
        for (reason in ScanRefreshReason.entries) {
            assertTrue(ScanGenerationPlanner.findingProgressVisible(true, reason, true, true, baseline, roots))
        }
    }

    @Test
    fun mediaStoreOnlyProgressNeedsAnEmptyIndexAndADifferentBaseline() {
        for (roots in listOf(null, emptyList(), listOf("/books"))) {
            val baseline = LibraryScanCoordinator.baselineScopeKey(roots)
            for (reason in listOf(ScanRefreshReason.FOREGROUND, ScanRefreshReason.MANUAL, ScanRefreshReason.OBSERVER)) {
                assertTrue(ScanGenerationPlanner.findingProgressVisible(true, reason, false, false, "different", roots))
                assertFalse(ScanGenerationPlanner.findingProgressVisible(true, reason, false, false, baseline, roots))
                assertFalse(ScanGenerationPlanner.findingProgressVisible(true, reason, false, true, "different", roots))
            }
        }
    }

    @Test
    fun wholeDeviceWalkUsesVolumesAndIncludesFilesystemDiscovery() {
        val plan = ScanGenerationPlanner.buildWalks(null, listOf("/primary", "/removable"))

        assertEquals(listOf(ScanWalk(listOf("/primary", "/removable"))), plan.walks)
        assertEquals(setOf(DiscoverySource.MEDIA_STORE, DiscoverySource.FILESYSTEM), plan.sources)
    }

    @Test
    fun explicitRootsOverrideVolumesAndKeepRetainedWalksAndExclusions() {
        val retained = ScanWalk(listOf("/previous"), listOf("/previous/private"))
        val plan = ScanGenerationPlanner.buildWalks(
            listOf("/requested"), listOf("/volume"), listOf(ScanWalk(emptyList()), retained), listOf("/requested/private"),
        )

        assertEquals(listOf(retained, ScanWalk(listOf("/requested"), listOf("/requested/private"))), plan.walks)
        assertEquals(setOf(DiscoverySource.MEDIA_STORE, DiscoverySource.FILESYSTEM), plan.sources)
    }

    @Test
    fun emptyWalksLeaveOnlyMediaStoreDiscovery() {
        for (plan in listOf(
            ScanGenerationPlanner.buildWalks(emptyList(), listOf("/volume"), listOf(ScanWalk(emptyList()))),
            ScanGenerationPlanner.buildWalks(null, emptyList()),
        )) {
            assertEquals(emptyList<ScanWalk>(), plan.walks)
            assertEquals(setOf(DiscoverySource.MEDIA_STORE), plan.sources)
        }
    }

    @Test
    fun incompleteTerminalPublishPreservesExistingProgressVisibility() {
        for (visible in listOf(false, true)) {
            val terminal = ScanGenerationPlanner.terminalPublish(true, visible)
            assertEquals(ScanPhase.INCOMPLETE, terminal.phase)
            assertEquals(visible, terminal.showProgress)
        }
    }

    @Test
    fun completeTerminalPublishAlwaysHidesProgress() {
        for (visible in listOf(false, true)) {
            val terminal = ScanGenerationPlanner.terminalPublish(false, visible)
            assertEquals(ScanPhase.IDLE, terminal.phase)
            assertFalse(terminal.showProgress)
        }
    }
}
