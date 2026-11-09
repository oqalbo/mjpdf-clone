// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationSaveDecisionsTest {
    @Test
    fun incompleteSourceTakesPriorityOverVerificationAndRescue() {
        for (unconfirmed in listOf(false, true)) {
            for (rescue in listOf(null, "rescue.pdf")) {
                assertEquals(SaveFailureMessage.Incomplete, saveFailureMessage(SaveOutcome.Failure(rescue, unconfirmed, true)))
            }
        }
    }

    @Test
    fun unconfirmedSaveTakesPriorityOverRescue() {
        for (rescue in listOf(null, "rescue.pdf")) {
            assertEquals(SaveFailureMessage.Unconfirmed, saveFailureMessage(SaveOutcome.Failure(rescue, true)))
        }
    }

    @Test
    fun rescueMessageTakesPriorityOverGenericFailure() {
        assertEquals(SaveFailureMessage.Rescue, saveFailureMessage(SaveOutcome.Failure("rescue.pdf", false)))
        assertEquals(SaveFailureMessage.Generic, saveFailureMessage(SaveOutcome.Failure(null, false)))
    }

    @Test
    fun copyingOntoTheSourceShowsTheSameFileDialog() {
        assertEquals(SaveRouting.SameFileDialog, saveRouting(isCopy = true, sameDocument = true))
    }

    @Test
    fun updatingADifferentDocumentAsksToConfirmOverwriting() {
        assertEquals(SaveRouting.ConfirmOverwrite, saveRouting(isCopy = false, sameDocument = false))
    }

    @Test
    fun copyingElsewhereAndUpdatingTheSourceSaveImmediately() {
        assertEquals(SaveRouting.Save, saveRouting(isCopy = true, sameDocument = false))
        assertEquals(SaveRouting.Save, saveRouting(isCopy = false, sameDocument = true))
    }

    @Test
    fun suggestedNamesPreserveExistingSuffixesAndSupplyMissingOnes() {
        val cases = mapOf(
            "a.PDF" to "a-annotated.pdf",
            "a-annotated.pdf" to "a-annotated.pdf",
            "a" to "a-annotated.pdf",
            "" to "document-annotated.pdf",
            "  " to "document-annotated.pdf",
            "A-ANNOTATED.PDF" to "A-ANNOTATED.PDF",
        )
        for ((name, expected) in cases) assertEquals(name, expected, suggestedAnnotatedFileName(name))
    }
}
