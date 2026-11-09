// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.os.Environment
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import java.io.File

data class FolderNode(
    val path: String,
    val name: String,
    val subtitle: String?,
    val count: Int,
)

data class Crumb(
    val label: String,
    val path: String?,
)

class FolderIndex(
    entries: List<ScannedPdfEntry>,
    private val primaryLabel: String,
    private val primaryRoot: String = Environment.getExternalStorageDirectory().absolutePath,
) {

    private val filesByDir: Map<String, List<ScannedPdfEntry>> =
        entries.groupBy { File(it.path).parent.orEmpty() }.filterKeys { it.isNotEmpty() }

    private val childDirs = HashMap<String, MutableSet<String>>()
    private val recursiveCounts = HashMap<String, Int>()

    val roots: List<String>

    init {
        val rootsFound = sortedSetOf<String>()
        for (entry in entries) {
            val root = rootOf(entry.path, primaryRoot) ?: continue
            rootsFound.add(root)

            var dir = File(entry.path).parent ?: continue
            while (dir != root && dir.startsWith(root)) {
                recursiveCounts.merge(dir, 1, Int::plus)
                val parent = File(dir).parent ?: break
                childDirs.getOrPut(parent) { mutableSetOf() }.add(dir)
                dir = parent
            }
            recursiveCounts.merge(root, 1, Int::plus)
        }
        roots = rootsFound.toList()
    }

    fun foldersIn(dir: String): List<FolderNode> {
        return childDirs[dir].orEmpty()
            .sortedBy { File(it).name.lowercase() }
            .map { path ->
                FolderNode(path, File(path).name, null, recursiveCounts[path] ?: 0)
            }
    }

    fun filesIn(dir: String): List<ScannedPdfEntry> {
        return filesByDir[dir].orEmpty().sortedBy { File(it.path).name.lowercase() }
    }

    fun flatFolders(): List<FolderNode> {
        return filesByDir.entries
            .sortedBy { File(it.key).name.lowercase() }
            .map { (dir, files) ->
                FolderNode(dir, File(dir).name, parentLabel(dir), files.size)
            }
    }

    fun rootFolders(): List<FolderNode> {
        return roots.map { root ->
            FolderNode(root, rootLabel(root), null, recursiveCounts[root] ?: 0)
        }
    }

    fun rootLabel(root: String): String {
        return if (root == primaryRoot) primaryLabel else File(root).name
    }

    fun crumbsFor(dir: String): List<Crumb> {
        val root = rootOf(dir + File.separator, primaryRoot) ?: return emptyList()
        val crumbs = mutableListOf(Crumb(rootLabel(root), if (roots.size > 1) root else null))
        if (dir == root) {
            return crumbs
        }
        val relative = dir.removePrefix(root).trim(File.separatorChar)
        var current = root
        for (segment in relative.split(File.separatorChar)) {
            current = "$current${File.separatorChar}$segment"
            crumbs.add(Crumb(segment, current))
        }
        return crumbs
    }

    fun parentOf(dir: String): String? {
        val root = rootOf(dir + File.separator, primaryRoot) ?: return null
        if (dir == root) {
            return null
        }
        val parent = File(dir).parent ?: return null
        return if (parent == root && roots.size == 1) null else parent
    }

    private fun parentLabel(dir: String): String? {
        val root = rootOf(dir + File.separator, primaryRoot) ?: return null
        val parent = File(dir).parent ?: return null
        if (parent == root) {
            return rootLabel(root)
        }
        val relative = parent.removePrefix(root).trim(File.separatorChar)
        return "${rootLabel(root)} › ${relative.replace(File.separator, " › ")}"
    }

    companion object {


        fun rootOf(path: String): String? =
            rootOf(path, Environment.getExternalStorageDirectory().absolutePath)

        internal fun rootOf(path: String, primaryRoot: String): String? {
            if (path.startsWith("$primaryRoot${File.separator}")) {
                return primaryRoot
            }
            val segments = path.split(File.separatorChar)
            return if (segments.size > 3 && segments[1] == "storage") {
                "${File.separator}${segments[1]}${File.separator}${segments[2]}"
            } else {
                null
            }
        }
    }
}
