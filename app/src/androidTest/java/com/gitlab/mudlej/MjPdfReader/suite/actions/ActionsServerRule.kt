// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.net.LocalPdfServer
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

class ActionsServerRule : TestWatcher() {

    val server = LocalPdfServer()

    private var namesBefore: Set<String> = emptySet()
    private var hadAccessAtStart = false

    fun servePdf(path: String, fixtureName: String): String =
        server.servePdfBytes(path, ActionsFixtures.file(fixtureName).readBytes())

    override fun starting(description: Description) {
        hadAccessAtStart = Fixtures.hasAllFilesAccess()
        namesBefore = downloadNames()
        server.start()
    }

    override fun finished(description: Description) {
        server.shutdown()
        if (!hadAccessAtStart || !Fixtures.hasAllFilesAccess()) return
        (downloadNames() - namesBefore).forEach { name ->
            runCatching { Fixtures.delete(File(ActionsFixtures.seededRoot, name)) }
        }
    }

    private fun downloadNames(): Set<String> = ActionsFixtures.seededRoot.list()?.toSet().orEmpty()
}
