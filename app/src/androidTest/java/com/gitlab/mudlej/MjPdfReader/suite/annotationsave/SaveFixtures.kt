// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object SaveFixtures {

    const val SMALL = "small-300kb.pdf"
    const val MEDIUM = "medium-2.3mb.pdf"
    const val DECOY = "decoy.pdf"
    const val NORMAL = "normal.pdf"
    const val PROTECTED = "protected.pdf"
    const val LARGE = "large.pdf"
    const val SCANNED = "scanned.pdf"

    const val PROTECTED_PASSWORD = "mjpdf"

    const val NORMAL_PAGES = 20
    const val PROTECTED_PAGES = 6
    const val LARGE_PAGES = 500
    const val SCANNED_PAGES = 8

    const val ANNOTATED_SUFFIX = "-annotated.pdf"
    const val SAVING_SUFFIX = ".saving"

    val downloads: File = Fixtures.seededRoot

    fun annotatedNameOf(fileName: String): String =
        if (fileName.endsWith(ANNOTATED_SUFFIX, ignoreCase = true)) {
            fileName
        } else if (fileName.endsWith(".pdf", ignoreCase = true)) {
            fileName.dropLast(4) + ANNOTATED_SUFFIX
        } else {
            fileName + ANNOTATED_SUFFIX
        }

    fun placeInDownloads(fixtureName: String, targetName: String): File =
        Fixtures.copyDeviceFixture(fixtureName, downloads, targetName = targetName)

    fun downloadsFilesWithPrefix(prefix: String): List<File> =
        downloads.listFiles()?.filter { it.name.startsWith(prefix) }.orEmpty()

    fun savingSiblingsWithPrefix(prefix: String): List<File> =
        downloadsFilesWithPrefix(prefix).filter { it.name.endsWith(SAVING_SUFFIX) }
}
