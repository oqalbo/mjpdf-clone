// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import org.junit.Assert.*
import org.junit.Test

class TemporaryCopyDecisionTest {
    private val megabyte = 1024L * 1024
    private val applicationId = "com.gitlab.mudlej.MjPdfReader"

    @Test fun onlyExternalContentProvidersAreEligible() {
        assertTrue(isTemporaryCopyEligible("content", "com.example.share", applicationId))
        for (scheme in listOf(null, "file", "https", "CONTENT")) {
            assertFalse(isTemporaryCopyEligible(scheme, "com.example.share", applicationId))
        }
        for (authority in listOf(null, "media", "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents", "com.android.providers.media.documents",
            applicationId, "$applicationId.fileprovider", "${applicationId}Other")) {
            assertFalse(isTemporaryCopyEligible("content", authority, applicationId))
        }
    }

    @Test fun unknownSizeNeverAsksForConsentInCurrentBehavior() {
        assertFalse(temporaryCopyNeedsConsent(null, 0))
        assertFalse(temporaryCopyNeedsConsent(null, null))
        assertTrue(temporaryCopyFits(null, 0))
    }

    @Test fun exactlyTwoHundredAndFiftyMegabytesDoesNotAskInCurrentBehavior() {
        assertFalse(temporaryCopyNeedsConsent(250 * megabyte, null))
        assertFalse(temporaryCopyNeedsConsent(250 * megabyte, 750 * megabyte))
        assertTrue(temporaryCopyNeedsConsent(250 * megabyte + 1, null))
    }

    @Test fun freeSpaceAtTheFloorFitsButOneByteLessRequiresConsent() {
        val size = 10 * megabyte
        val free = 510 * megabyte
        assertTrue(temporaryCopyFits(size, free))
        assertFalse(temporaryCopyNeedsConsent(size, free))
        assertFalse(temporaryCopyFits(size, free - 1))
        assertTrue(temporaryCopyNeedsConsent(size, free - 1))
        assertTrue(temporaryCopyFits(size, null))
        assertFalse(temporaryCopyNeedsConsent(size, null))
    }
}
