// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

data class FileFacts(val path: String, val sizeBytes: Long, val lastModified: Long) {
    override fun toString(): String = "$path ($sizeBytes bytes, mtime $lastModified)"
}

data class TreeSnapshot(val entries: Map<String, FileFacts>) {
    fun factsFor(path: String): FileFacts? = entries[path]
}

data class TreeDiff(
    val added: List<FileFacts>,
    val removed: List<FileFacts>,
    val modified: List<Pair<FileFacts, FileFacts>>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && modified.isEmpty()

    fun describe(): String {
        if (isEmpty) return "the tree is unchanged"
        return buildString {
            if (added.isNotEmpty()) appendLine("added:\n  " + added.joinToString("\n  "))
            if (removed.isNotEmpty()) appendLine("removed:\n  " + removed.joinToString("\n  "))
            if (modified.isNotEmpty()) {
                appendLine("modified:")
                modified.forEach { (before, after) -> appendLine("  ${before} became ${after}") }
            }
        }.trimEnd()
    }
}

object FileTree {
    val dataDirectory: File get() = File(TestEnvironment.targetContext.applicationInfo.dataDir)

    val externalRoot: File get() = android.os.Environment.getExternalStorageDirectory()

    val defaultIgnoredFragments: List<String> = listOf(
        "/cache/mjpdf",
        "/code_cache",
        "/no_backup",
        "/files/sharer",
        "/Documents/mjpdf-test",
        "/Documents/mjpdf-test-artifacts",
        "/mjpdf-artifacts",
    )

    val bulkFixtureFragments: List<String> = listOf(
        "/Download/perf-library-100k",
        "/Download/fifty-distinct",
        "/Download/library",
        "/Download/scan",
        "/Download/backups",
    )

    fun snapshot(
        root: File,
        ignoredFragments: List<String> = defaultIgnoredFragments,
        maxDepth: Int = Int.MAX_VALUE,
    ): TreeSnapshot {
        val entries = LinkedHashMap<String, FileFacts>()
        if (!root.exists()) return TreeSnapshot(entries)
        root.walkTopDown()
            .maxDepth(maxDepth)
            .onEnter { directory -> ignoredFragments.none { directory.absolutePath.contains(it) } }
            .filter { it.isFile }
            .forEach { file ->
                val path = file.absolutePath
                if (ignoredFragments.any { path.contains(it) }) return@forEach
                entries[path] = runCatching { FileFacts(path, file.length(), file.lastModified()) }
                    .getOrDefault(FileFacts(path, -1L, -1L))
            }
        return TreeSnapshot(entries)
    }

    fun sharedStorageSnapshot(): TreeSnapshot =
        snapshot(externalRoot, defaultIgnoredFragments + bulkFixtureFragments, SHARED_STORAGE_DEPTH)

    const val SHARED_STORAGE_DEPTH = 4

    fun diff(before: TreeSnapshot, after: TreeSnapshot): TreeDiff {
        val added = after.entries.filterKeys { it !in before.entries }.values.toList()
        val removed = before.entries.filterKeys { it !in after.entries }.values.toList()
        val modified = after.entries.mapNotNull { (path, now) ->
            val then = before.entries[path] ?: return@mapNotNull null
            if (then.sizeBytes == now.sizeBytes && then.lastModified == now.lastModified) null else then to now
        }
        return TreeDiff(added, removed, modified)
    }

    fun relativeTo(root: File, path: String): String = path.removePrefix(root.absolutePath).trimStart('/')
}
