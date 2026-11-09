// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import org.junit.Assert.*
import org.junit.Test

class DocumentLinksPolicyTest {
    @Test fun fileSchemesAreBlockedRegardlessOfCase() {
        for (scheme in listOf("file", "FILE", "FiLe")) {
            assertFalse(isOpenableDocumentLink(scheme, "$scheme:///x", "$scheme:///x"))
        }
    }

    @Test fun rawAndDecodedControlCharactersAreBlocked() {
        assertFalse(isOpenableDocumentLink("https", "https://x/a%0Ab", "https://x/a\nb"))
        assertFalse(isOpenableDocumentLink("https", "https://x/a\tb", "https://x/ab"))
        assertFalse(isOpenableDocumentLink("https", "https://x/a%7Fb", "https://x/a\u007fb"))
        assertTrue(isOpenableDocumentLink("https", "https://x/a%20b", "https://x/a b"))
    }

    @Test fun relativeLinksWithoutASchemeAreBlocked() {
        assertFalse(isOpenableDocumentLink(null, "chapter.pdf", "chapter.pdf"))
    }

    @Test fun contentIntentAndJavascriptSchemesPassInCurrentBehavior() {
        for (scheme in listOf("content", "intent", "javascript")) {
            assertTrue(scheme, isOpenableDocumentLink(scheme, "$scheme:target", "$scheme:target"))
        }
    }
}
