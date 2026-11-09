// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import org.junit.Assert.*
import org.junit.Test

class TableOfContentsPathResolverTest {
    @Test fun pagesBeforeTheFirstEntryHaveNoBreadcrumb() {
        val resolver = TableOfContentsPathResolver.fromBookmarks(entries(bookmark("Chapter", 2)))
        assertNull(resolver.resolve(-1))
        assertNull(resolver.resolve(1))
        assertEquals("Chapter", resolver.resolve(2))
        assertEquals("Chapter", resolver.resolve(99))
        assertNull(TableOfContentsPathResolver.EMPTY.resolve(0))
    }

    @Test fun childOnTheSamePageWinsOverItsParent() {
        val resolver = TableOfContentsPathResolver.fromBookmarks(entries(
            bookmark(" Parent ", 2, bookmark("Child", 2)), bookmark("Later", 5)))
        assertEquals("Parent ▶ Child", resolver.resolve(2))
        assertEquals("Parent ▶ Child", resolver.resolve(4))
        assertEquals("Later", resolver.resolve(5))
    }

    @Test fun blankChildTitlesInheritTheParentBreadcrumb() {
        val resolver = TableOfContentsPathResolver.fromBookmarks(entries(bookmark("Parent", 0, bookmark("  ", 1))))
        assertEquals("Parent", resolver.resolve(1))
        assertNull(TableOfContentsPathResolver.fromBookmarks(entries(bookmark(" ", 0))).resolve(0))
    }

    @Test fun negativePageParentsAreSkippedButTheirChildrenKeepTheTitlePath() {
        val resolver = TableOfContentsPathResolver.fromBookmarks(entries(bookmark("Parent", -1, bookmark("Child", 2))))
        assertNull(resolver.resolve(0))
        assertEquals("Parent ▶ Child", resolver.resolve(2))
    }

    @Test fun titleDepthWinsOverTreeLevelInCurrentBehavior() {
        val resolver = TableOfContentsPathResolver.fromBookmarks(entries(bookmark("Root", 0,
            bookmark("Named", 0), bookmark(" ", 0, bookmark(" ", 0)))))
        assertEquals("Root ▶ Named", resolver.resolve(0))
    }
}
