// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import com.google.android.material.checkbox.MaterialCheckBox
import java.io.File

internal class ScanLocationSelection(private val selected: LinkedHashSet<String>) {
    fun stateOf(path: String): Int {
        return when {
            selected.any { path == it || path.startsWith("$it${File.separatorChar}") } ->
                MaterialCheckBox.STATE_CHECKED
            selected.any { it.startsWith("$path${File.separatorChar}") } ->
                MaterialCheckBox.STATE_INDETERMINATE
            else -> MaterialCheckBox.STATE_UNCHECKED
        }
    }

    fun toggle(path: String) {
        if (!selected.remove(path)) {
            selected.removeAll { it.startsWith("$path${File.separatorChar}") }
            selected.add(path)
        }
    }

    fun isLocked(path: String): Boolean =
        selected.any { it != path && path.startsWith("$it${File.separatorChar}") }
}

internal fun shouldPersist(previousMode: ScanMode, previous: Set<String>, selected: Set<String>): Boolean =
    previousMode != ScanMode.SELECTED_LOCATIONS ||
        ScanScope.normalize(selected).toSet() != ScanScope.normalize(previous).toSet()

internal fun crumbsFor(volumes: List<File>, dir: File, label: (File) -> String): List<Crumb> {
    val volume = volumes.firstOrNull { volume ->
        dir.absolutePath == volume.absolutePath
            || dir.absolutePath.startsWith("${volume.absolutePath}${File.separatorChar}")
    } ?: return emptyList()

    val crumbs = mutableListOf(Crumb(label(volume), volume.absolutePath))
    if (dir.absolutePath == volume.absolutePath) {
        return crumbs
    }
    val relative = dir.absolutePath.removePrefix(volume.absolutePath).trim(File.separatorChar)
    var current = volume.absolutePath
    for (segment in relative.split(File.separatorChar)) {
        current = "$current${File.separatorChar}$segment"
        crumbs.add(Crumb(segment, current))
    }
    return crumbs
}
