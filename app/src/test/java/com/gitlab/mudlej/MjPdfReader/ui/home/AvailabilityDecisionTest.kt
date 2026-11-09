// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.support.FakeFilesContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AvailabilityDecisionTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun fileReadabilityTakesPrecedenceOverAccessAndMissingRequiresFullAccess() {
        assertEquals(Availability.AVAILABLE, fileAvailability(true, true))
        assertEquals(Availability.AVAILABLE, fileAvailability(true, false))
        assertEquals(Availability.ACCESS_UNKNOWN, fileAvailability(false, false))
        assertEquals(Availability.MISSING, fileAvailability(false, true))
    }

    @Test fun successfulContentOpenIsAvailableRegardlessOfGrantsAuthorityOrAccess() {
        for (grant in listOf(false, true)) {
            for (media in listOf(false, true)) {
                for (access in listOf(false, true)) {
                    assertEquals(Availability.AVAILABLE, contentAvailability(true, grant, media, access))
                }
            }
        }
    }

    @Test fun failedContentOpenUsesGrantThenAuthorityAndAccess() {
        for (media in listOf(false, true)) {
            for (access in listOf(false, true)) {
                assertEquals(Availability.MISSING, contentAvailability(false, true, media, access))
            }
        }
        assertEquals(Availability.ACCESS_UNKNOWN, contentAvailability(false, false, true, false))
        assertEquals(Availability.MISSING, contentAvailability(false, false, true, true))
        assertEquals(Availability.MISSING, contentAvailability(false, false, false, false))
        assertEquals(Availability.MISSING, contentAvailability(false, false, false, true))
    }

    @Test fun otherSchemesAreAvailableWithoutAProviderProbe() {
        val context = FakeFilesContext(files.root, files.root)
        for (access in listOf(false, true)) {
            val probe = AvailabilityProbe(context, access)
            for (uri in listOf("http://books/book.pdf", "https://books/book.pdf", "custom:book", "/relative/book.pdf")) {
                assertEquals(Availability.AVAILABLE, probe.availabilityOf(Uri.parse(uri)))
            }
        }
    }

    @Test fun fileUrisUseTheActualFileReadability() {
        val context = FakeFilesContext(files.root, files.root)
        val existing = Uri.fromFile(files.newFile("book.pdf"))
        val missing = Uri.fromFile(java.io.File(files.root, "missing.pdf"))
        assertEquals(Availability.AVAILABLE, AvailabilityProbe(context, false).availabilityOf(existing))
        assertEquals(Availability.ACCESS_UNKNOWN, AvailabilityProbe(context, false).availabilityOf(missing))
        assertEquals(Availability.MISSING, AvailabilityProbe(context, true).availabilityOf(missing))
    }
}
