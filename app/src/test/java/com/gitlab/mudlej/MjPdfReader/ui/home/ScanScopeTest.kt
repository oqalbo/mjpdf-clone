// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanScopeTest {
    @Test fun filesystemRootSurvivesNormalizationAndSwallowsDescendants() {
        assertEquals(listOf("/"), ScanScope.normalize(linkedSetOf("/books/a", "/books", "/", "/other")))
    }

    @Test fun normalizationDropsBlanksTrimsSlashesAndKeepsSiblingPrefixes() {
        assertEquals(listOf("/books", "/bookshelf"), ScanScope.normalize(linkedSetOf("", " \t", "/books///", "/books/a/", "/bookshelf/", "/books")))
    }

    @Test fun containsDistinguishesWholeDeviceEmptyScopeAndPathBoundaries() {
        assertTrue(ScanScope.contains(null, "/books/x.pdf"))
        assertFalse(ScanScope.contains(emptyList(), "/books/x.pdf"))
        assertTrue(ScanScope.contains(listOf("/books"), "/books"))
        assertTrue(ScanScope.contains(listOf("/books/"), "/books/x.pdf"))
        assertFalse(ScanScope.contains(listOf("/books"), "/bookshelf/x.pdf"))
    }

    @Test fun displayRootsUsesTheConfiguredModeAndNormalizesSelectedLocations() {
        val locations = setOf("/books/", "/books/a", "/other")
        assertNull(ScanScope.displayRoots(ScanMode.WHOLE_DEVICE, locations))
        assertEquals(emptyList<String>(), ScanScope.displayRoots(ScanMode.NOT_CONFIGURED, locations))
        assertEquals(listOf("/books", "/other"), ScanScope.displayRoots(ScanMode.SELECTED_LOCATIONS, locations))
        assertEquals(emptyList<String>(), ScanScope.displayRoots(ScanMode.SELECTED_LOCATIONS, emptySet()))
    }

    @Test fun parentSegmentsRemainLiteralRatherThanCanonicalized() {
        assertEquals(listOf("/books/../etc"), ScanScope.normalize(setOf("/books/../etc/")))
        assertTrue(ScanScope.contains(listOf("/books"), "/books/../etc/x.pdf"))
        assertFalse(ScanScope.contains(listOf("/etc"), "/books/../etc/x.pdf"))
    }
}
