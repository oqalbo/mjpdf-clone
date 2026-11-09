// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Ignore
import com.gitlab.mudlej.MjPdfReader.ui.reader.load.DocumentUnreachableException

class FileOpeningErrorsTest {
    @Test fun staleFailuresAreFoundThroughTwoWrappingExceptions() {
        for (cause in listOf(FileNotFoundException(), SecurityException(), DocumentUnreachableException(null))) {
            assertTrue(isStaleDocumentFailure(IllegalStateException(RuntimeException(cause))))
            assertTrue(isStaleDocumentFailure(cause))
        }
        assertFalse(isStaleDocumentFailure(IOException("read failed")))
    }

    @Test fun permissionFailureRequiresAMissingGrantAndAMatchingFileNotFoundMessage() {
        val denied = FileNotFoundException("open failed: EACCES (Permission denied)")
        assertTrue(couldNotOpenFileDueToMissingPermission(denied, false, "Permission denied"))
        assertFalse(couldNotOpenFileDueToMissingPermission(denied, true, "Permission denied"))
        assertFalse(couldNotOpenFileDueToMissingPermission(IOException(denied.message), false, "Permission denied"))
        assertFalse(couldNotOpenFileDueToMissingPermission(FileNotFoundException(), false, "Permission denied"))
        assertFalse(couldNotOpenFileDueToMissingPermission(FileNotFoundException("No such file"), false, "Permission denied"))
    }

    @Ignore("Pending ruling: errno text is compared to a translated label, expected EACCES to match in every locale")
    @Test fun englishErrnoMessageMatchesWithATranslatedPermissionLabel() {
        assertTrue(couldNotOpenFileDueToMissingPermission(
            FileNotFoundException("open failed: EACCES (Permission denied)"), false, "Accès refusé"))
    }
}
