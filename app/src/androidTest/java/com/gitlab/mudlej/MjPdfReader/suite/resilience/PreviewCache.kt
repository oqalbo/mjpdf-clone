// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

object PreviewCache {

    const val DIRECTORY = "previews"

    fun root(): File = File(TestEnvironment.targetContext.cacheDir, DIRECTORY)

    fun directoryForOpenDocument(timeoutMs: Long = Await.LONG_TIMEOUT_MS): File =
        Await.untilNotNull("the reader writes a preview cache directory for the open document", timeoutMs) {
            root().listFiles().orEmpty().firstOrNull { it.isDirectory && it.listFiles().orEmpty().isNotEmpty() }
        }

    fun entriesFor(directory: File, page: Int): List<File> =
        directory.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("p$page-") }

    fun awaitEntryFor(directory: File, page: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): File =
        Await.untilNotNull(
            "a cached preview for page index $page appears under ${directory.absolutePath}",
            timeoutMs,
        ) { entriesFor(directory, page).firstOrNull { it.length() > 0 } }
}
