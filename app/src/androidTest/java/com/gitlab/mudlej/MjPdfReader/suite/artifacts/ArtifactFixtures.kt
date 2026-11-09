// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object ArtifactFixtures {

    const val SMALL = "small-300kb.pdf"
    const val MEDIUM = "medium-2.3mb.pdf"
    const val LARGE = "large-20mb.pdf"
    const val META_RICH = "meta-rich.pdf"
    const val WRAPPED_QUOTE = "wrapped-quote.pdf"
    const val HIGHLIGHTS_RICH = "highlights-rich.pdf"
    const val HIGHLIGHTS_ARABIC = "highlights-arabic.pdf"
    const val FORMS = "forms_all_field_types.pdf"
    const val PROTECTED = "protected.pdf"
    const val VERY_LARGE = "large.pdf"
    const val NORMAL = "normal.pdf"

    const val PROTECTED_PASSWORD = "mjpdf"

    fun require(name: String): File {
        val file = Fixtures.requireSeededFixture(name, ensureAccess = false)
        if (file.length() == 0L) {
            throw AssertionError("The seeded fixture ${file.absolutePath} is zero bytes long")
        }
        return file
    }

    fun annotatedNameFor(name: String): String = name.removeSuffix(".pdf") + "-annotated.pdf"
}
