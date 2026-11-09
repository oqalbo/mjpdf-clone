// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import org.junit.Assert.*
import org.junit.Test

class DocumentIdentityDecisionTest {
    @Test
    fun revalidatingAnUnidentifiableFileClearsThePreviousIdentity() {
        val decision = decideIdentity(true, "previous", null, false, true, 0)

        assertNull(decision.hash)
        assertTrue(decision.clearFileHash)
        assertTrue(decision.resetReadingDirection)
        assertFalse(decision.resetDocument)
        assertTrue(decision.useHashlessPath)
    }

    @Test
    fun revalidatingWithHistoryEnabledRereadsTheStoredReadingDirection() {
        val decision = decideIdentity(true, "known", "known", true, true, 3)

        assertEquals("known", decision.hash)
        assertTrue(decision.resetReadingDirection)
        assertFalse(decision.clearFileHash)
        assertFalse(decision.resetDocument)
    }

    @Test
    fun revalidatingInIncognitoKeepsTheResolvedReadingDirectionForAKnownHash() {
        val decision = decideIdentity(true, "known", "known", false, true, 3)

        assertEquals("known", decision.hash)
        assertFalse(decision.resetReadingDirection)
        assertFalse(decision.clearFileHash)
        assertFalse(decision.resetDocument)
    }

    @Test
    fun aChangedHashRequestsTheDocumentResetThatClearsPasswordAndRenewsTheToken() {
        val decision = decideIdentity(true, "previous", "replacement", true, true, 3)

        assertEquals("replacement", decision.hash)
        assertTrue(decision.resetDocument)
        assertFalse(decision.useHashlessPath)
        assertFalse(decision.clearFileHash)
    }

    @Test
    fun aChangedHashWithoutAUriCannotResetTheDocument() {
        assertFalse(decideIdentity(true, "previous", "replacement", true, false, 3).resetDocument)
        assertFalse(decideIdentity(true, null, "first", true, true, 0).resetDocument)
    }

    @Test
    fun anOrdinaryLoadKeepsThePreviousIdentityAndReadingDirection() {
        val decision = decideIdentity(false, "previous", null, true, true, 0)

        assertEquals("previous", decision.hash)
        assertFalse(decision.clearFileHash)
        assertFalse(decision.resetReadingDirection)
        assertFalse(decision.useHashlessPath)
    }

    @Test
    fun aFirstLoadUsesTheResolvedIdentity() {
        val decision = decideIdentity(false, null, "resolved", true, true, 0)

        assertEquals("resolved", decision.hash)
        assertFalse(decision.resetDocument)
        assertFalse(decision.useHashlessPath)
    }

    @Test
    fun theHashlessPathIsUsedOnlyAtPageZero() {
        assertTrue(decideIdentity(false, null, null, true, true, 0).useHashlessPath)
        assertFalse(decideIdentity(false, null, null, true, true, 3).useHashlessPath)
    }

    @Test
    fun alwaysOpeningAtTheFirstPageIgnoresStoredPositionAndSuppressesPersistence() {
        val opening = decideDocumentOpening(0, "known", true)

        assertEquals(0, opening.pageToOpen(40))
        assertFalse(opening.restoreStoredPage)
        assertTrue(opening.suppressPositionPersist)
    }

    @Test
    fun openingNormallyRestoresTheStoredPageWithoutSuppressingPersistence() {
        val opening = decideDocumentOpening(0, "known", false)

        assertEquals(40, opening.pageToOpen(40))
        assertTrue(opening.restoreStoredPage)
        assertFalse(opening.suppressPositionPersist)
    }

    @Test
    fun anExistingPageSurvivesTheFirstPagePreference() {
        val opening = decideDocumentOpening(3, "known", true)

        assertEquals(3, opening.pageToOpen(40))
        assertFalse(opening.restoreStoredPage)
        assertFalse(opening.suppressPositionPersist)
    }

    @Test
    fun aHashlessDocumentDoesNotRestoreAStoredPage() {
        val opening = decideDocumentOpening(0, null, false)

        assertEquals(0, opening.pageToOpen(40))
        assertFalse(opening.restoreStoredPage)
        assertFalse(opening.suppressPositionPersist)
    }
}
