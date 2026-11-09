// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.gitlab.mudlej.MjPdfReader.support.FakeFilesContext

class DurableCopyTargetTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun directory(): File {
        val context = FakeFilesContext(temporary.newFolder("files"), temporary.newFolder("cache"))
        return context.filesDir
    }

    @Test fun freeNamesAreUsedUnchanged() {
        val directory = directory()
        assertEquals(File(directory, "Book.pdf"), DurableCopyStore.resolveTarget(directory, "Book.pdf", null))
        assertEquals(File(directory, "X.PDF"), DurableCopyStore.resolveTarget(directory, "X.PDF", null))
    }

    @Test fun differentContentAndMissingExpectedIdentityChooseTheNextName() {
        val directory = directory()
        File(directory, "x.pdf").writeText("first document")
        assertEquals(File(directory, "x (2).pdf"), DurableCopyStore.resolveTarget(directory, "x.pdf", "different hash"))
        assertEquals(File(directory, "x (2).pdf"), DurableCopyStore.resolveTarget(directory, "x.pdf", null))
    }

    @Test fun matchingCurrentOrLegacyIdentityReusesTheExistingCopy() {
        val directory = directory()
        val existing = File(directory, "x.pdf").apply { writeText("document contents") }
        val identities = requireNotNull(DocumentIdentity.of(existing))
        assertEquals(existing, DurableCopyStore.resolveTarget(directory, "x.pdf", identities.identity))
        assertEquals(existing, DurableCopyStore.resolveTarget(directory, "x.pdf", identities.legacy))
    }

    @Test fun matchingIdentityCanReuseASuffixedCopy() {
        val directory = directory()
        File(directory, "x.pdf").writeText("different contents")
        val existing = File(directory, "x (2).pdf").apply { writeText("document contents") }
        assertEquals(existing, DurableCopyStore.resolveTarget(directory, "x.pdf", requireNotNull(DocumentIdentity.of(existing)).identity))
    }

    @Test fun theHundredthNameIsTheLastAvailableAttempt() {
        val directory = directory()
        File(directory, "x.pdf").writeText("occupied")
        for (index in 2..99) File(directory, "x ($index).pdf").writeText("occupied")
        val last = File(directory, "x (100).pdf")
        assertEquals(last, DurableCopyStore.resolveTarget(directory, "x.pdf", null))
        last.writeText("occupied")
        assertNull(DurableCopyStore.resolveTarget(directory, "x.pdf", null))
    }

    @Test fun uppercasePdfCollisionUsesALowercaseSuffixInCurrentBehavior() {
        val directory = directory()
        File(directory, "X.PDF").writeText("occupied")
        assertEquals(File(directory, "X (2).pdf"), DurableCopyStore.resolveTarget(directory, "X.PDF", null))
    }
}
