// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

internal object ScanGenerationPlanner {
    data class WalkPlan(val walks: List<ScanWalk>, val sources: Set<DiscoverySource>)
    data class TerminalPublish(val phase: ScanPhase, val showProgress: Boolean)

    fun mergePending(current: ScanRefreshReason?, incoming: ScanRefreshReason): ScanRefreshReason =
        if (current == ScanRefreshReason.FULL_RESCAN) current else incoming

    fun retainedWalks(
        reason: ScanRefreshReason,
        discoveryCancelled: Boolean,
        filesystemComplete: Boolean,
        walks: List<ScanWalk>,
        scope: List<String>?,
    ): List<ScanWalk> =
        if (reason != ScanRefreshReason.FULL_RESCAN && discoveryCancelled && !filesystemComplete) {
            walks.map { it.within(scope) }
        } else emptyList()

    fun findingProgressVisible(
        configured: Boolean,
        reason: ScanRefreshReason,
        walksNonEmpty: Boolean,
        hasEntries: Boolean,
        baseline: String?,
        roots: List<String>?,
    ): Boolean = configured && (reason == ScanRefreshReason.FULL_RESCAN || walksNonEmpty ||
        (!hasEntries && baseline != LibraryScanCoordinator.baselineScopeKey(roots)))

    fun buildWalks(
        requestedRoots: List<String>?,
        volumeRoots: List<String>,
        retainedWalks: List<ScanWalk> = emptyList(),
        excluded: List<String> = emptyList(),
    ): WalkPlan {
        val requested = requestedRoots?.toList() ?: volumeRoots
        val walks = (retainedWalks + ScanWalk(requested, excluded.toList())).filter { it.roots.isNotEmpty() }
        val sources = if (walks.isEmpty()) setOf(DiscoverySource.MEDIA_STORE) else DiscoverySource.entries.toSet()
        return WalkPlan(walks, sources)
    }

    fun terminalPublish(sessionIncomplete: Boolean, showProgress: Boolean) = TerminalPublish(
        if (sessionIncomplete) ScanPhase.INCOMPLETE else ScanPhase.IDLE,
        sessionIncomplete && showProgress,
    )
}
