// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import android.content.Intent
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.FixtureNames
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.io.File

object ResilienceFixtures {

    const val NORMAL = FixtureNames.NORMAL
    const val LARGE = FixtureNames.LARGE
    const val SCANNED = FixtureNames.SCANNED
    const val PROTECTED = FixtureNames.PROTECTED
    const val CORRUPT = FixtureNames.CORRUPT
    const val ARABIC = FixtureNames.ARABIC

    const val LARGE_PAGE_COUNT = 500
    const val SCANNED_PAGE_COUNT = 8
    const val ARABIC_PAGE_COUNT = 12
    const val PROTECTED_PAGE_COUNT = 6

    const val PROTECTED_PASSWORD = "mjpdf"

    fun place(name: String, targetDirectory: File): File = Fixtures.copyDeviceFixture(name, targetDirectory)

    fun onlineViewIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), PDF.FILE_TYPE)
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
        }

    fun incognitoViewIntent(file: File): Intent =
        ReaderRobot.viewIntent(file).putExtra(PDF.incognitoKey, true)
}
