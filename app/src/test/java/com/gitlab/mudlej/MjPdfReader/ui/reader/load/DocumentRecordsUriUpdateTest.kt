// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import org.junit.Assert.*
import org.junit.Test

class DocumentRecordsUriUpdateTest {
    @Test fun identicalUrisAreNeverUpdated() {
        for (canonical in listOf(false, true)) {
            for (readable in listOf(false, true)) {
                assertFalse(shouldUpdateRecordUri("content://same", "content://same", canonical, readable))
            }
        }
    }

    @Test fun readableStoredUriIsKeptWhenNoCanonicalFileExists() {
        assertFalse(shouldUpdateRecordUri("content://old", "content://current", false, true))
    }

    @Test fun canonicalFilesUnreadableStoredUrisAndMissingRecordsAreUpdated() {
        assertTrue(shouldUpdateRecordUri("content://old", "file:///durable", true, true))
        assertTrue(shouldUpdateRecordUri("content://old", "content://current", false, false))
        assertTrue(shouldUpdateRecordUri(null, "content://current", false, false))
    }

    @Test fun recordNameRemovalIsCaseSensitiveInCurrentBehavior() {
        assertEquals("A", recordFileName("A.pdf"))
        assertEquals("A.PDF", recordFileName("A.PDF"))
        assertEquals("A.epub", recordFileName("A.epub"))
    }
}
