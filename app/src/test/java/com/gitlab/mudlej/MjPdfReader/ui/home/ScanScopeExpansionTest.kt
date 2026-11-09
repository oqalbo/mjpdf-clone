// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanScopeExpansionTest {
    @Test fun firstWholeDeviceSetupExpandsFromNoRoots() {
        assertNull(ScanScope.newRoots(emptyList(), null))
    }

    @Test fun existingWholeDeviceScopeNeverExpands() {
        assertEquals(emptyList<String>(), ScanScope.newRoots(null, null))
        assertEquals(emptyList<String>(), ScanScope.newRoots(null, listOf("/books")))
    }

    @Test fun selectedScopeExpandsToWholeDevice() {
        assertNull(ScanScope.newRoots(listOf("/books"), null))
    }

    @Test fun retainedAndNarrowedRegionsDoNotExpand() {
        assertEquals(
            emptyList<String>(),
            ScanScope.newRoots(listOf("/books/", "/other"), listOf("/books", "/books/fiction")),
        )
        assertEquals(emptyList<String>(), ScanScope.newRoots(listOf("/books"), emptyList()))
    }

    @Test fun parentExpansionReturnsOnlyTheParentAndDisjointNewRoots() {
        assertEquals(
            setOf("/books", "/new"),
            ScanScope.newRoots(
                listOf("/books/fiction", "/retained"),
                listOf("/books/fiction", "/books/", "/new", "/new/child", "/retained", "/books"),
            )?.toSet(),
        )
    }

    @Test fun siblingNamePrefixesAreNotRetainedRegions() {
        assertEquals(listOf("/bookshelf"), ScanScope.newRoots(listOf("/books"), listOf("/bookshelf")))
    }

    @Test fun filesystemRootContainsEveryAbsoluteRegion() {
        assertEquals(emptyList<String>(), ScanScope.newRoots(listOf("/"), listOf("/books")))
        assertEquals(listOf("/"), ScanScope.newRoots(emptyList(), listOf("/books", "/")))
    }
}
