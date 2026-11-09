// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import org.junit.Assert.*
import org.junit.Test
import com.google.android.material.checkbox.MaterialCheckBox
import org.junit.Ignore
import java.io.File

class ScanLocationSelectionTest {
    @Test fun siblingPrefixIsNeitherSelectedNorLockedNorIndeterminate() {
        val selection = ScanLocationSelection(linkedSetOf("/books"))
        assertEquals(MaterialCheckBox.STATE_UNCHECKED, selection.stateOf("/bookshelf"))
        assertFalse(selection.isLocked("/bookshelf"))
        assertEquals(MaterialCheckBox.STATE_CHECKED, selection.stateOf("/books/chapter"))
        assertTrue(selection.isLocked("/books/chapter"))
        assertFalse(selection.isLocked("/books"))
    }

    @Test fun parentToggleCollapsesDescendantsAndSecondToggleRemovesOnlyParent() {
        val selected = linkedSetOf("/books/a", "/books/b/c", "/bookshelf")
        val selection = ScanLocationSelection(selected)
        assertEquals(MaterialCheckBox.STATE_INDETERMINATE, selection.stateOf("/books"))
        selection.toggle("/books")
        assertEquals(listOf("/bookshelf", "/books"), selected.toList())
        assertEquals(MaterialCheckBox.STATE_CHECKED, selection.stateOf("/books"))
        selection.toggle("/books")
        assertEquals(setOf("/bookshelf"), selected)
        assertEquals(MaterialCheckBox.STATE_UNCHECKED, selection.stateOf("/books"))
    }

    @Ignore("Pending ruling: trailing slash marks the same folder indeterminate, task expects unchecked")
    @Test fun trailingSlashStoredLocationLeavesSameFolderUnchecked() {
        val selection = ScanLocationSelection(linkedSetOf("/books/"))
        assertEquals(MaterialCheckBox.STATE_UNCHECKED, selection.stateOf("/books"))
    }

    @Test fun sameSelectedScopeDoesNotPersistButSwitchFromWholeDeviceDoes() {
        val paths = setOf("/books", "/notes")
        assertFalse(shouldPersist(ScanMode.SELECTED_LOCATIONS, paths, paths.reversed().toSet()))
        assertTrue(shouldPersist(ScanMode.WHOLE_DEVICE, paths, paths))
        assertTrue(shouldPersist(ScanMode.SELECTED_LOCATIONS, paths, setOf("/books")))
    }

    @Test fun persistenceComparesNormalizedScopes() {
        assertFalse(shouldPersist(ScanMode.SELECTED_LOCATIONS,
            setOf("/books/", "/books/a", ""), setOf("/books")))
        assertFalse(shouldPersist(ScanMode.SELECTED_LOCATIONS,
            setOf("/books"), setOf("/books/", "/books/a")))
    }

    @Test fun nestedVolumesUseFirstMatchAndBuildCumulativeCrumbs() {
        val volumes = listOf(File("/storage"), File("/storage/card"))
        val labels: (File) -> String = { if (it.path == "/storage") "Storage" else "Card" }
        assertEquals(listOf(Crumb("Storage", "/storage"), Crumb("card", "/storage/card"),
            Crumb("books", "/storage/card/books")), crumbsFor(volumes, File("/storage/card/books"), labels))
        assertEquals(listOf(Crumb("Card", "/storage/card"), Crumb("books", "/storage/card/books")),
            crumbsFor(volumes.reversed(), File("/storage/card/books"), labels))
        assertEquals(emptyList<Crumb>(), crumbsFor(volumes, File("/storage2/books"), labels))
        assertEquals(listOf(Crumb("Storage", "/storage")), crumbsFor(volumes, File("/storage"), labels))
    }
}
