// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.fixtures

import android.provider.MediaStore
import android.content.ContentUris
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifacts
import java.io.File

data class MediaEntry(val id: Long, val relativePath: String, val displayName: String) {
    override fun toString() = "$relativePath$displayName (id $id)"
}

data class SharedStorageSnapshot(
    val paths: Set<String>,
    val mediaRows: Map<Long, MediaEntry>,
    val hadAllFilesAccess: Boolean,
    val mediaQueryFailure: String?,
)

data class SharedStorageDiff(
    val pathsAdded: List<String>,
    val pathsRemoved: List<String>,
    val mediaRowsAdded: List<MediaEntry>,
    val mediaRowsRemoved: List<MediaEntry>,
    val mediaComparisonSkipped: String?,
) {
    val isEmpty: Boolean
        get() = pathsAdded.isEmpty() && pathsRemoved.isEmpty() &&
            mediaRowsAdded.isEmpty() && mediaRowsRemoved.isEmpty()

    fun describe(): String {
        if (isEmpty && mediaComparisonSkipped == null) return "Shared storage is unchanged"
        return buildString {
            if (isEmpty) {
                appendLine("Shared storage is unchanged in the paths that were compared")
            } else {
                appendLine("Shared storage changed across this test")
            }
            mediaComparisonSkipped?.let { appendLine("MediaStore rows were not compared: $it") }
            section("Paths added", pathsAdded)
            section("Paths removed", pathsRemoved)
            section("MediaStore rows added", mediaRowsAdded.map { it.toString() })
            section("MediaStore rows removed", mediaRowsRemoved.map { it.toString() })
        }.trimEnd()
    }

    private fun StringBuilder.section(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        appendLine(title)
        lines.forEach { appendLine("  $it") }
    }
}

object SharedStorage {

    fun deleteLegacyDirectory(directory: File) {
        val resolver = TestEnvironment.targetContext.contentResolver
        val collection = MediaStore.Files.getContentUri(Fixtures.EXTERNAL_VOLUME)
        val path = directory.absolutePath
        val descendantPattern = path.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%"
        val rows = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA),
            "${MediaStore.MediaColumns.DATA} = ? OR ${MediaStore.MediaColumns.DATA} LIKE ? ESCAPE '\\'",
            arrayOf(path, descendantPattern),
            null,
        )?.use { cursor ->
            buildList<Pair<Long, String>> {
                while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1).orEmpty())
            }
        } ?: throw AssertionError("Could not inspect legacy MediaStore entries under $path")
        if (directory.exists() && !directory.deleteRecursively()) {
            throw AssertionError("Could not clear $path")
        }
        rows.sortedByDescending { it.second.length }.forEach { (id, _) ->
            resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
        }
    }

    const val DEFAULT_DEPTH = 2

    val defaultRoots: List<String> = listOf(
        "/sdcard/Documents",
        "/sdcard/Download",
        "/sdcard/Pictures",
    )

    val watchedMediaPathPrefixes: List<String> = listOf("Documents/", "Download/", "Pictures/")

    val ignoredPathPrefixes: List<String> = listOf(
        FailureArtifacts.ROOT_PATH,
        FailureArtifacts.ALTERNATE_ROOT_PATH,
    )

    val ignoredMediaPathPrefixes: List<String> = listOf(FailureArtifacts.MEDIA_RELATIVE_PATH)

    fun snapshot(roots: List<String> = defaultRoots, depth: Int = DEFAULT_DEPTH): SharedStorageSnapshot {
        val rows = LinkedHashMap<Long, MediaEntry>()
        var failure: String? = null
        try {
            queryMediaRows(rows)
        } catch (error: Throwable) {
            failure = "${error::class.java.simpleName}: ${error.message}"
        }
        return SharedStorageSnapshot(
            paths = listPaths(roots, depth),
            mediaRows = rows,
            hadAllFilesAccess = Fixtures.hasAllFilesAccess(),
            mediaQueryFailure = failure,
        )
    }

    fun diff(before: SharedStorageSnapshot, after: SharedStorageSnapshot): SharedStorageDiff {
        val mediaSkipReason = mediaSkipReason(before, after)
        return SharedStorageDiff(
            pathsAdded = (after.paths - before.paths).sorted(),
            pathsRemoved = (before.paths - after.paths).sorted(),
            mediaRowsAdded = if (mediaSkipReason != null) emptyList()
            else after.mediaRows.filterKeys { it !in before.mediaRows }.values.toList(),
            mediaRowsRemoved = if (mediaSkipReason != null) emptyList()
            else before.mediaRows.filterKeys { it !in after.mediaRows }.values.toList(),
            mediaComparisonSkipped = mediaSkipReason,
        )
    }

    private fun mediaSkipReason(before: SharedStorageSnapshot, after: SharedStorageSnapshot): String? = when {
        before.mediaQueryFailure != null -> "the first query failed with ${before.mediaQueryFailure}"
        after.mediaQueryFailure != null -> "the second query failed with ${after.mediaQueryFailure}"
        before.hadAllFilesAccess != after.hadAllFilesAccess ->
            "the all files access grant changed during the test, so the two queries saw different row sets"
        else -> null
    }

    private fun listPaths(roots: List<String>, depth: Int): Set<String> {
        val existing = roots.filter { it.isNotBlank() && it.none { character -> character.isWhitespace() } }
        if (existing.isEmpty()) return emptySet()
        val command = "find ${existing.joinToString(" ")} -maxdepth $depth"
        return Shell.executeLines(command)
            .map { it.trim() }
            .filter { it.startsWith("/") }
            .filterNot { path -> ignoredPathPrefixes.any { path.startsWith(it) } }
            .toSet()
    }

    private fun queryMediaRows(into: MutableMap<Long, MediaEntry>) {
        val resolver = TestEnvironment.targetContext.contentResolver
        val collection = MediaStore.Files.getContentUri(Fixtures.EXTERNAL_VOLUME)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val selection = watchedMediaPathPrefixes.joinToString(" OR ") {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        }
        val arguments = watchedMediaPathPrefixes.map { "$it%" }.toTypedArray()
        resolver.query(collection, projection, selection, arguments, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val relativePath = cursor.getString(pathColumn).orEmpty()
                if (ignoredMediaPathPrefixes.any { relativePath.startsWith(it) }) continue
                val id = cursor.getLong(idColumn)
                into[id] = MediaEntry(
                    id = id,
                    relativePath = relativePath,
                    displayName = cursor.getString(nameColumn).orEmpty(),
                )
            }
        }
    }
}
