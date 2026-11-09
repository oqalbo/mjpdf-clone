// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import android.net.Uri
import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object PrivacyFixtures {

    const val NORMAL = "normal.pdf"
    const val SECOND = "hist-beta.pdf"
    const val SCANNED = "scanned.pdf"
    const val ARABIC = "arabic-rtl.pdf"
    const val LARGE = "large.pdf"
    const val TOC = "toc.pdf"
    const val LINKS = "links.pdf"
    const val HIGHLIGHTS_RICH = "highlights-rich.pdf"

    const val NORMAL_PAGES = 20
    const val SCANNED_PAGES = 8
    const val ARABIC_PAGES = 12
    const val LARGE_PAGES = 500

    const val NORMAL_TITLE = "normal"
    const val SECOND_TITLE = "hist-beta"

    const val DISTINCTIVE_NAME = "priv-recents-marker-qxzv.pdf"
    const val DISTINCTIVE_TITLE = "priv-recents-marker-qxzv"

    val seededRoot: File = File(Fixtures.externalRoot, "Download")

    fun file(name: String): File = Fixtures.requireSeededFixture(name)

    fun uri(name: String): Uri = file(name).toUri()

    fun copyInto(directory: File, name: String, targetName: String = name): File {
        val source = file(name)
        directory.mkdirs()
        val target = File(directory, targetName)
        source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        if (target.length() != source.length()) {
            throw AssertionError(
                "The copy of $name landed at ${target.length()} bytes against a source of ${source.length()}"
            )
        }
        return target
    }

    fun identityOf(fixture: File): String = DocumentIdentity.of(fixture)?.identity
        ?: throw AssertionError(
            "DocumentIdentity could not read ${fixture.absolutePath}, so no test can name the record it would produce"
        )

    fun legacyIdentityOf(fixture: File): String = DocumentIdentity.of(fixture)?.legacy
        ?: throw AssertionError(
            "DocumentIdentity could not read ${fixture.absolutePath}, so no test can seed its legacy record"
        )

    fun titleOf(name: String): String = name.removeSuffix(".pdf")
}
