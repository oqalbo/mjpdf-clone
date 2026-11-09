// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import java.io.File
import java.nio.file.Files
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RenameDecisionTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun trimmedNameTargetsTheSameDirectoryAndChecksExistenceOnce() {
        val source = files.newFile("old.pdf")
        val checked = mutableListOf<File>()
        val target = File(files.root, "new.pdf")
        assertEquals(RenameDecision.Target(target), renameTarget(source.path, "  new \t") { checked.add(it); false })
        assertEquals(listOf(target), checked)
    }

    @Test fun emptyAndSeparatorNamesAreRefusedBeforeCheckingExistence() {
        for (input in listOf("", " \t", "a${File.separator}b")) {
            assertEquals(RenameDecision.Invalid, renameTarget("/books/old.pdf", input) { error("invalid name must not probe a file") })
        }
    }

    @Test fun existingDifferentFileIsRefused() {
        val source = files.newFile("old.pdf")
        files.newFile("taken.pdf")
        assertEquals(RenameDecision.Exists, renameTarget(source.path, "taken", File::exists))
    }

    @Ignore("Pending ruling: an input ending in .pdf gets a second .pdf suffix")
    @Test fun suppliedPdfSuffixIsNotDuplicated() {
        assertEquals(RenameDecision.Target(File("/books/x.pdf")), renameTarget("/books/old.pdf", "x.pdf") { false })
    }

    @Ignore("Pending ruling: an existing case-only target is refused even when it resolves to the source")
    @Test fun caseOnlyRenameAllowsTheSameCanonicalFile() {
        val source = files.newFile("Book.pdf")
        val target = File(files.root, "book.pdf")
        Files.createSymbolicLink(target.toPath(), source.toPath())
        assertEquals(source.canonicalFile, target.canonicalFile)
        assertEquals(RenameDecision.Target(target), renameTarget(source.path, "book", File::exists))
    }

    @Test fun infoLineChoosesCombinedPagesOrSizeOrEmptyAndFormatsSizeInUsLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val cases = listOf(
                Triple(12, 1572864L, "12 pages, 1.50 MB"),
                Triple(12, 0L, "12 pages, 0.00 MB"),
                Triple(12, null, "12 pages"),
                Triple(0, 1572864L, "1.50 MB"),
                Triple(0, null, ""),
            )
            for ((length, bytes, expected) in cases) {
                val calls = mutableListOf<String>()
                val actual = infoLine(length, bytes,
                    pages = { calls.add("pages"); "$it pages" },
                    size = { calls.add("combined"); "$length pages, $it" },
                )
                assertEquals("length=$length bytes=$bytes", expected, actual)
                val expectedCalls = when {
                    length <= 0 -> emptyList()
                    bytes != null -> listOf("combined")
                    else -> listOf("pages")
                }
                assertEquals("length=$length bytes=$bytes", expectedCalls, calls)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }
}
