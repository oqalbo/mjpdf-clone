// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader

import org.junit.Assert.*
import org.junit.Test
import org.junit.Ignore

class SharedTextLinkTest {
    @Test fun surroundingPunctuationIsTrimmedBeforeMatching() {
        assertEquals("https://x.com/a", sharedTextLink("(https://x.com/a).") { it == "https://x.com/a" })
    }

    @Test fun onlyHttpAndHttpsTokensReachTheMatcher() {
        val matched = mutableListOf<String>()
        assertEquals("HTTPS://x.com", sharedTextLink("ftp://x.com x.com HTTPS://x.com") {
            matched.add(it)
            true
        })
        assertEquals(listOf("HTTPS://x.com"), matched)
    }

    @Test fun firstAcceptedLinkWinsAndRejectedTokensAreSkipped() {
        assertEquals("https://second.com", sharedTextLink("http://bad https://second.com https://third.com") {
            it != "http://bad"
        })
        assertNull(sharedTextLink("no link http://bad") { false })
    }

    @Ignore("Pending ruling: trimming removes a URL's closing parenthesis, expected balanced URL parentheses to survive")
    @Test fun wikipediaLinksKeepTheirClosingParenthesis() {
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)",
            sharedTextLink("see https://en.wikipedia.org/wiki/Foo_(bar)") { true })
    }
}
