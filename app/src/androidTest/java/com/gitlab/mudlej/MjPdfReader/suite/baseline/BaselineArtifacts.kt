// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifacts
import java.io.File

object BaselineArtifacts {

    const val DIRECTORY = "baseline"
    const val EXPECTED_FILE = "expected.png"
    const val ACTUAL_FILE = "actual.png"
    const val DIFF_FILE = "diff.png"
    const val REPORT_FILE = "report.txt"

    fun directoryFor(testId: String, goldenName: String): File {
        val slug = BaselineAssets.withPngSuffix(goldenName).removeSuffix(BaselineAssets.PNG_SUFFIX)
        val directory = File(FailureArtifacts.directoryFor(testId), "$DIRECTORY/$slug")
        directory.mkdirs()
        return directory
    }

    fun writeMismatch(
        testId: String,
        goldenName: String,
        expected: Bitmap,
        actual: Bitmap,
        result: ComparisonResult,
        policy: TolerancePolicy,
        reasons: List<String>,
    ): File {
        val directory = directoryFor(testId, goldenName)
        runCatching { BitmapFiles.write(expected, File(directory, EXPECTED_FILE)) }
        runCatching { BitmapFiles.write(actual, File(directory, ACTUAL_FILE)) }
        runCatching { BitmapFiles.write(DiffImage.render(actual, result), File(directory, DIFF_FILE)) }
        runCatching {
            File(directory, REPORT_FILE).writeText(report(testId, goldenName, result, policy, reasons))
        }
        return directory
    }

    fun writeSizeMismatch(
        testId: String,
        goldenName: String,
        expected: Bitmap,
        actual: Bitmap,
        message: String,
    ): File {
        val directory = directoryFor(testId, goldenName)
        runCatching { BitmapFiles.write(expected, File(directory, EXPECTED_FILE)) }
        runCatching { BitmapFiles.write(actual, File(directory, ACTUAL_FILE)) }
        runCatching {
            File(directory, REPORT_FILE).writeText(
                buildString {
                    appendLine("testId: $testId")
                    appendLine("golden: ${BaselineAssets.withPngSuffix(goldenName)}")
                    appendLine("outcome: dimension mismatch, treated as a pin failure")
                    appendLine("goldenSize: ${expected.width}x${expected.height}")
                    appendLine("captureSize: ${actual.width}x${actual.height}")
                    appendLine()
                    appendLine(message)
                    appendLine()
                    append(BaselineProfile.pins().describe())
                }
            )
        }
        return directory
    }

    fun report(
        testId: String,
        goldenName: String,
        result: ComparisonResult,
        policy: TolerancePolicy,
        reasons: List<String>,
    ): String = buildString {
        appendLine("testId: $testId")
        appendLine("golden: ${BaselineAssets.withPngSuffix(goldenName)}")
        appendLine("outcome: ${if (reasons.isEmpty()) "within tolerance" else "mismatch"}")
        reasons.forEach { appendLine("reason: $it") }
        appendLine()
        append(result.summary())
        appendLine()
        appendLine("perChannelTolerance: ${policy.perChannelTolerance}")
        appendLine("unequalPixelBudgetPercent: ${policy.maxUnequalPercentText()}")
        appendLine("hardChannelDelta: ${policy.hardChannelDelta}")
        appendLine()
        append(BaselineProfile.pins().describe())
    }
}
