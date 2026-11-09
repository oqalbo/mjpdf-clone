// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader

import org.junit.Assert.*
import org.junit.Test

class DocumentStateTitleTest {
    @Test fun titlesStripAnyFinalExtensionInCurrentBehavior() {
        for ((name, title) in listOf("A.PDF" to "A", "A.pdf" to "A", "A.epub" to "A",
            "A.chapter.pdf" to "A.chapter", "A" to "A", ".pdf" to "", "" to "")) {
            assertEquals(name, title, DocumentState(name = name).getTitle())
        }
    }

    @Test fun pageCountersUseTheEarlierRangeStartAndOneBasedPages() {
        assertEquals("[3-4/10]", DocumentState(pageNumber = 3, pageRangeStart = 2, pageRangeEnd = 3, length = 10).getPageCounterText())
        assertEquals("[3/10]", DocumentState(pageNumber = 2, pageRangeStart = 4, pageRangeEnd = 2, length = 10).getPageCounterText())
    }
}
