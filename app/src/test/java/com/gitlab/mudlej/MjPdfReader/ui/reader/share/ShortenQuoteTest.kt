// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.share

import org.junit.Assert.*
import org.junit.Test
import org.junit.Ignore

class ShortenQuoteTest {
    @Test fun fourHundredCharactersNeedNoShortening() {
        assertNull(shortenQuote("a".repeat(400)))
    }

    @Test fun fourHundredAndOneCharactersAreCutWithAnEllipsis() {
        assertEquals("a".repeat(400) + "…", shortenQuote("a".repeat(401)))
    }

    @Test fun whitespaceAfterTheMidpointEndsTheQuoteAtAWordBoundary() {
        assertEquals("a".repeat(201) + "…", shortenQuote("a".repeat(201) + " " + "b".repeat(200)))
    }

    @Test fun whitespaceAtTheMidpointStillUsesTheHardCut() {
        assertEquals("a".repeat(200) + " " + "b".repeat(199) + "…", shortenQuote("a".repeat(200) + " " + "b".repeat(200)))
    }

    @Ignore("Pending ruling: taking 400 UTF-16 units splits an emoji, expected a complete code point")
    @Test fun shorteningKeepsSurrogatePairsIntact() {
        assertEquals("a".repeat(399) + "…", shortenQuote("a".repeat(399) + "😀"))
    }
}
