// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScanBaselineScopeTest {
    @Test fun rootOrderDoesNotChangeTheBaselineIdentity() {
        assertEquals(
            LibraryScanCoordinator.baselineScopeKey(listOf("/books", "/downloads")),
            LibraryScanCoordinator.baselineScopeKey(listOf("/downloads", "/books")),
        )
    }

    @Test fun wholeDeviceAndEmptySelectedScopeHaveDifferentIdentities() {
        assertNotEquals(
            LibraryScanCoordinator.baselineScopeKey(null),
            LibraryScanCoordinator.baselineScopeKey(emptyList()),
        )
    }

    @Test fun delimitersInRootNamesCannotCollideWithMultipleRoots() {
        assertNotEquals(
            LibraryScanCoordinator.baselineScopeKey(listOf("/a", "/b")),
            LibraryScanCoordinator.baselineScopeKey(listOf("/a2:/b")),
        )
    }
}
