// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test

class FolderIndexTest {
    private val primary = "/storage/emulated/0"
    private val secondary = "/storage/ABCD-1234"

    private fun index(vararg paths: String) = FolderIndex(
        paths.map { ScannedPdfEntry(it, 10, 1_000, null) }, "Internal storage", primary,
    )

    @Test fun countsRollUpWithoutInventingAChildForRootFiles() {
        val nested = index("$primary/A/B/x.pdf", "$primary/A/y.pdf")
        assertEquals(listOf(FolderNode(primary, "Internal storage", null, 2)), nested.rootFolders())
        assertEquals(listOf(FolderNode("$primary/A", "A", null, 2)), nested.foldersIn(primary))
        assertEquals(listOf(FolderNode("$primary/A/B", "B", null, 1)), nested.foldersIn("$primary/A"))
        val withRootFile = index("$primary/A/B/x.pdf", "$primary/A/y.pdf", "$primary/z.pdf")
        assertEquals(3, withRootFile.rootFolders().single().count)
        assertEquals(nested.foldersIn(primary), withRootFile.foldersIn(primary))
        assertEquals(listOf("$primary/z.pdf"), withRootFile.filesIn(primary).map { it.path })
    }

    @Test fun volumeRootsUseThePrimaryLabelAndSecondaryVolumeName() {
        val folders = index("$primary/a.pdf", "$secondary/Books/x.pdf")
        assertEquals(listOf(secondary, primary), folders.roots)
        assertEquals(listOf("ABCD-1234", "Internal storage"), folders.rootFolders().map { it.name })
        assertEquals(secondary, FolderIndex.rootOf("$secondary/Books/x.pdf", primary))
    }

    @Test fun outsideStorageAppearsOnlyInFlatFoldersWithNoSubtitle() {
        val folders = index("/data/user/0/app/x.pdf")
        assertNull(FolderIndex.rootOf("/data/user/0/app/x.pdf", primary))
        assertEquals(emptyList<FolderNode>(), folders.rootFolders())
        assertEquals(listOf(FolderNode("/data/user/0/app", "app", null, 1)), folders.flatFolders())
    }

    @Test fun parentNavigationSkipsTheOnlyRootButRetainsMultipleRoots() {
        val single = index("$primary/A/B/x.pdf")
        assertNull(single.parentOf(primary))
        assertNull(single.parentOf("$primary/A"))
        assertEquals("$primary/A", single.parentOf("$primary/A/B"))
        val multiple = index("$primary/A/x.pdf", "$secondary/x.pdf")
        assertEquals(primary, multiple.parentOf("$primary/A"))
        assertNull(multiple.parentOf(primary))
    }

    @Test fun nestedBreadcrumbsHaveCumulativePathsAndOnlyMultipleRootsAreClickable() {
        val single = index("$primary/A/B/x.pdf")
        val expected = listOf(Crumb("Internal storage", null), Crumb("A", "$primary/A"), Crumb("B", "$primary/A/B"))
        assertEquals(expected, single.crumbsFor("$primary/A/B"))
        assertEquals(listOf(Crumb("Internal storage", null)), single.crumbsFor(primary))
        val multiple = index("$primary/A/B/x.pdf", "$secondary/x.pdf")
        assertEquals(expected.mapIndexed { i, crumb -> if (i == 0) crumb.copy(path = primary) else crumb }, multiple.crumbsFor("$primary/A/B"))
    }

    @Ignore("Pending ruling: emulated user 01 falls back to /storage/emulated instead of its own root")
    @Test fun similarPrimaryPrefixKeepsTheOtherEmulatedUserRoot() {
        assertEquals("/storage/emulated/01", FolderIndex.rootOf("/storage/emulated/01/x.pdf", primary))
    }
}
