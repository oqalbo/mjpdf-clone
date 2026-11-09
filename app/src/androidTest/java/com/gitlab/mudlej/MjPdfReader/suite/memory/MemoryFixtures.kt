// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.FixtureNames
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object MemoryFixtures {
    const val NORMAL = FixtureNames.NORMAL
    const val LARGE = FixtureNames.LARGE
    const val ARABIC = FixtureNames.ARABIC
    const val HIGHLIGHTS = FixtureNames.HIGHLIGHTS
    const val TOC = FixtureNames.TOC
    const val LINKS = FixtureNames.LINKS

    const val BIG_IMAGES = "big-images.pdf"
    const val THREE_THOUSAND_PAGES = "three-thousand-pages.pdf"
    const val SCANNED_LONG = "scanned-long.pdf"
    const val MANY_HIGHLIGHTS = "many-highlights.pdf"
    const val HUGE_500MB = "huge-500mb.pdf"
    const val FIFTY_DISTINCT_DIRECTORY = "fifty-distinct"

    const val NORMAL_PAGES = 20
    const val LARGE_PAGES = 500
    const val ARABIC_PAGES = 12
    const val BIG_IMAGES_PAGES = 12
    const val THREE_THOUSAND_PAGES_COUNT = 3000
    const val SCANNED_LONG_PAGES = 300
    const val MANY_HIGHLIGHTS_PAGES = 200
    const val MANY_HIGHLIGHTS_GROUPS = 600
    const val FIFTY_DISTINCT_COUNT = 50
    const val HUGE_500MB_PAGES = 200

    const val LARGE_SEARCH_TERM = "the"
    const val LARGE_UNIQUE_TERM = "orinoco"

    val seededRoot: File = File(Fixtures.externalRoot, "Download")

    fun file(name: String): File = Fixtures.requireSeededFixture(name)

    fun generatedOnDevice(name: String): File {
        Fixtures.ensureAllFilesAccess()
        val fixture = File(seededRoot, name)
        if (!fixture.isFile) {
            throw AssertionError(
                "$name is generated on device rather than committed, and it is not at ${fixture.absolutePath}. " +
                    "The Python E2E runner generates it into /sdcard/Download. Run python3 " +
                    "testing/runner/run.py before this class, and delete it afterwards, because " +
                    "the Orchestrator clears package data but not shared storage."
            )
        }
        return fixture
    }

    fun fiftyDistinct(): List<File> {
        Fixtures.ensureAllFilesAccess()
        val directory = File(seededRoot, FIFTY_DISTINCT_DIRECTORY)
        val files = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(PDF_SUFFIX, ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.size != FIFTY_DISTINCT_COUNT) {
            throw AssertionError(
                "${directory.absolutePath} holds ${files.size} documents, not $FIFTY_DISTINCT_COUNT. Each one " +
                    "carries distinct content so it has a distinct DocumentIdentity, which is the whole point: " +
                    "fifty copies of one file would share a hash and prove nothing."
            )
        }
        return files
    }

    private const val PDF_SUFFIX = ".pdf"
}
