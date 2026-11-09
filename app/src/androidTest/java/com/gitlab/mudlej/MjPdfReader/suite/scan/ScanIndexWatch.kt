// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.home.LibraryScanner
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanPhase
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.DiscoverySource

class ScanIndexWatch private constructor() {

    data class Publication(
        val scanning: Boolean,
        val entries: Int,
        val generation: Long,
        val phase: ScanPhase,
        val foundCount: Int,
        val paths: List<String>,
        val detailsCompleted: Int,
        val detailsTotal: Int,
        val revision: Long,
        val showProgress: Boolean,
        val sourceFailures: Set<DiscoverySource>,
    )

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val publications = Collections.synchronizedList(mutableListOf<Publication>())

    private fun start(): ScanIndexWatch {
        val scanner = LibraryScanner.getInstance(TestEnvironment.targetContext)
        scope.launch {
            scanner.index.collect { index ->
                publications.add(
                    Publication(
                        scanning = index.scanning,
                        entries = index.entries.size,
                        generation = index.generation,
                        phase = index.phase,
                        foundCount = index.foundCount,
                        paths = index.entries.map { it.path },
                        detailsCompleted = index.detailsCompleted,
                        detailsTotal = index.detailsTotal,
                        revision = index.revision,
                        showProgress = index.showProgress,
                        sourceFailures = index.sourceFailures,
                    )
                )
            }
        }
        return this
    }

    fun stop() {
        scope.cancel()
    }

    fun seen(): List<Publication> = synchronized(publications) { publications.toList() }

    companion object {
        fun started(): ScanIndexWatch = ScanIndexWatch().start()
    }
}
