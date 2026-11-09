// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

class ArtifactCleanupRule : TestWatcher() {

    private val owned = mutableListOf<File>()
    private val ownedMediaPrefixes = mutableListOf<String>()
    private val ownedMediaPaths = mutableListOf<String>()

    fun own(file: File) {
        owned.add(file)
    }

    fun ownAll(files: Iterable<File>) {
        owned.addAll(files)
    }

    fun ownImagesNamedLike(prefix: String) {
        require(prefix.isNotBlank())
        if (prefix in ownedMediaPrefixes) return
        ownedMediaPrefixes.add(prefix)
        MediaStoreProbe.deleteNamedLike(prefix)
        Await.until("the owned screenshot prefix is empty before use: $prefix") {
            MediaStoreProbe.imagesNamedLike(prefix).isEmpty()
        }
    }

    fun ownMediaUnder(relativePath: String) {
        ownedMediaPaths.add(relativePath)
    }

    override fun starting(description: Description) {
        owned.clear()
        ownedMediaPrefixes.clear()
        ownedMediaPaths.clear()
        runCatching { AppDirectories.clearAll() }
    }

    override fun finished(description: Description) {
        owned.forEach { file -> runCatching { Fixtures.delete(file) } }
        ownedMediaPrefixes.forEach { prefix -> runCatching { MediaStoreProbe.deleteNamedLike(prefix) } }
        ownedMediaPaths.forEach { path -> runCatching { MediaStoreProbe.deleteUnder(path) } }
        runCatching { AppDirectories.clearAll() }
    }
}
