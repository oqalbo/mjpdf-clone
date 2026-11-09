// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import org.junit.Assert.*
import org.junit.Test

class TableOfContentsTreeTest {
    @Test fun collapsedRootsExpandAndCollapseInSiblingOrder() {
        val tree = TableOfContentsTree().apply {
            roots = entries(bookmark("Parent", 0, bookmark("Child", 1, bookmark("Leaf", 2))), bookmark("Other", 3))
        }
        assertEquals(listOf("0", "1"), tree.buildRows().map { it.entry.path })
        assertEquals(listOf(true, false), tree.buildRows().map { it.expandable })
        tree.expandAll()
        assertEquals(listOf("0", "0.0", "0.0.0", "1"), tree.buildRows().map { it.entry.path })
        assertEquals(listOf(true, true, false, false), tree.buildRows().map { it.expanded })
        tree.collapseAll()
        assertEquals(listOf("0", "1"), tree.buildRows().map { it.entry.path })
        assertEquals(emptyList<String>(), tree.getExpandedEntryPaths())
    }

    @Test fun expandingAncestorsAddsEveryParentButNotTheSelectedEntry() {
        val tree = TableOfContentsTree()
        tree.expandAncestors("0.2.1")
        assertEquals(setOf("0", "0.2"), tree.getExpandedEntryPaths().toSet())
        tree.expandAncestors("1")
        assertEquals(setOf("0", "0.2"), tree.getExpandedEntryPaths().toSet())
    }

    @Test fun matchingDescendantsAreVisibleWithoutChangingSavedExpansion() {
        val tree = TableOfContentsTree().apply {
            roots = entries(bookmark("Root", 2, bookmark("Needle", 3), bookmark("Unrelated", 4)))
            query = " nEeDle "
        }
        assertEquals(listOf("0", "0.0"), tree.buildRows().map { it.entry.path })
        assertEquals(2, tree.visibleEntryCount(tree.roots))
        assertTrue(tree.toggleExpanded(tree.roots.first()))
        assertEquals(emptyList<String>(), tree.getExpandedEntryPaths())
        tree.query = null
        assertEquals(listOf("0"), tree.buildRows().map { it.entry.path })
    }

    @Test fun numericQueriesMatchDisplayedPagesOneTenAndTwentyOne() {
        val tree = TableOfContentsTree().apply {
            roots = entries(bookmark("First", 0), bookmark("Tenth", 9), bookmark("Twenty first", 20), bookmark("Second", 1))
            query = "1"
        }
        assertEquals(listOf(0L, 9L, 20L), tree.buildRows().map { it.entry.pageIdx })
    }

    @Test fun matchingAParentDoesNotExposeUnmatchedChildren() {
        val tree = TableOfContentsTree().apply {
            roots = entries(bookmark("Needle", 2, bookmark("Unrelated", 3)))
            query = "Needle"
        }
        val row = tree.buildRows().single()
        assertEquals("0", row.entry.path)
        assertFalse(row.expandable)
        assertFalse(row.expanded)
    }

    @Test fun restoredExpansionCanBeToggledAndBlankQueriesDoNotFilter() {
        val tree = TableOfContentsTree().apply {
            roots = entries(bookmark("Parent", 0, bookmark("Child", 1)))
            setExpandedEntryPaths(listOf("0", "old"))
            query = "  "
        }
        assertEquals(listOf("0", "0.0"), tree.buildRows().map { it.entry.path })
        assertFalse(tree.toggleExpanded(tree.roots.single()))
        assertEquals(listOf("0"), tree.buildRows().map { it.entry.path })
        assertTrue(tree.toggleExpanded(tree.roots.single()))
        tree.setExpandedEntryPaths(listOf("replacement"))
        assertEquals(listOf("replacement"), tree.getExpandedEntryPaths())
    }
}
