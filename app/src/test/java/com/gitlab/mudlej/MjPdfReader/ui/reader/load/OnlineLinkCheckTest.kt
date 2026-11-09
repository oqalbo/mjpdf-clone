// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineLinkCheckTest {
    @Test
    fun httpLinkNeedsConfirmationBeforeItOpens() {
        val link = "http://example.org/book.pdf"

        assertEquals(OnlineLinkCheck.NeedsHttpConfirmation, check(link))
        assertEquals(OnlineLinkCheck.Open, check(link, link))
    }

    @Test
    fun editingAConfirmedLinkRequiresConfirmationAgain() {
        val confirmed = "http://example.org/book.pdf"

        assertEquals(OnlineLinkCheck.NeedsHttpConfirmation, check("http://example.org/other.pdf", confirmed))
        assertEquals(OnlineLinkCheck.NeedsHttpConfirmation, check("http://example.org/book.pdf?download=1", confirmed))
    }

    @Test
    fun missingHostOrUnsupportedSchemeIsInvalidEvenWhenConfirmed() {
        for (link in listOf("http:///book.pdf", "file:///book.pdf", "ftp://example.org/book.pdf", "book.pdf")) {
            assertEquals(link, OnlineLinkCheck.Invalid, check(link, link))
        }
        assertEquals(OnlineLinkCheck.Invalid, checkOnlineLink("http", "  ", "http://  /book.pdf", null))
    }

    @Test
    fun httpsAndMixedCaseSchemesAreAccepted() {
        assertEquals(OnlineLinkCheck.Open, check("https://example.org/book.pdf"))
        assertEquals(OnlineLinkCheck.Open, checkOnlineLink("HTTPS", "example.org", "HTTPS://example.org/book.pdf", null))
        assertEquals(OnlineLinkCheck.NeedsHttpConfirmation, checkOnlineLink("HTTP", "example.org", "HTTP://example.org/book.pdf", null))
    }

    private fun check(link: String, confirmed: String? = null): OnlineLinkCheck {
        val uri = URI(link)
        return checkOnlineLink(uri.scheme, uri.host, link, confirmed)
    }
}
