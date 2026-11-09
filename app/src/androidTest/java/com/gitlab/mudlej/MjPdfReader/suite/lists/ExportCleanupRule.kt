// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ExportCleanupRule : TestWatcher() {

    private val claimed = linkedSetOf<String>()

    fun claim(name: String): String {
        Fixtures.ensureAllFilesAccess()
        claimed.add(name)
        ListExport.deleteAllVariants(name)
        return name
    }

    fun claimHighlightsOf(documentTitle: String): String =
        claim(ListExport.highlightsFileName(documentTitle))

    fun claimNotesOf(documentTitle: String): String =
        claim(ListExport.notesFileName(documentTitle))

    override fun finished(description: Description) {
        claimed.forEach { name -> runCatching { ListExport.deleteAllVariants(name) } }
        claimed.clear()
    }
}
