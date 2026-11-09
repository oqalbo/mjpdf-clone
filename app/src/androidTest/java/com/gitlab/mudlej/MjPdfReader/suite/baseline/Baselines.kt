// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

object Baselines {

    const val TAG = "MjBaseline"

    const val ALLOW_GPU_MISMATCH_ARGUMENT = "baseline.allowGpuMismatch"

    fun assertMatches(
        testId: String,
        goldenName: String,
        actual: Bitmap,
        expectation: GateExpectation,
        policy: TolerancePolicy = TolerancePolicy.DEFAULT,
    ): ComparisonResult {
        requirePinnedGpu()
        CaptureGate.check(actual, expectation)

        val profile = BaselineProfile.current()
        if (BaselineRecording.isRecording()) {
            recordAndRefuse(profile, testId, goldenName, actual, expectation, policy)
        }

        val expected = BaselineAssets.readGolden(profile, goldenName)
        val result = compareOrReportSize(testId, goldenName, expected, actual, policy)
        val reasons = result.verdict(policy)

        if (reasons.isEmpty()) {
            Log.i(
                TAG,
                "$testId matched ${BaselineAssets.withPngSuffix(goldenName)} at " +
                    "${result.formattedPercent()} percent mismatch, ${result.unequalPixels} unequal pixels " +
                    "of ${result.totalPixels}",
            )
            return result
        }

        val directory = BaselineArtifacts.writeMismatch(
            testId, goldenName, expected, actual, result, policy, reasons,
        )
        throw BaselineImageMismatch(
            buildString {
                appendLine("$testId does not match ${BaselineAssets.withPngSuffix(goldenName)}.")
                reasons.forEach { appendLine("  $it") }
                appendLine()
                append(result.summary())
                appendLine()
                appendLine("Evidence written to ${directory.absolutePath}:")
                appendLine("  ${BaselineArtifacts.EXPECTED_FILE}, the golden as committed")
                appendLine("  ${BaselineArtifacts.ACTUAL_FILE}, exactly what was captured")
                appendLine("  ${BaselineArtifacts.DIFF_FILE}, unequal pixels in magenta over a faded capture")
                appendLine("  ${BaselineArtifacts.REPORT_FILE}, the numbers above plus the device pin set")
                appendLine()
                append(
                    "If this rendering change was intended, re record the golden with " +
                        "${BaselineRecording.ARGUMENT}=true and say in the merge request which code change " +
                        "moved it. If it was not, this is the regression."
                )
            }
        )
    }

    fun assertByteIdentical(first: Bitmap, second: Bitmap, what: String) {
        if (first.width != second.width || first.height != second.height) {
            throw BaselineImageMismatch(
                "$what: the two captures differ in size, ${first.width}x${first.height} against " +
                    "${second.width}x${second.height}"
            )
        }
        if (PixelComparison.areByteIdentical(first, second)) return
        val result = PixelComparison.compare(first, second, TolerancePolicy.EXACT)
        throw BaselineImageMismatch(
            "$what: the two captures are not byte identical. ${result.unequalPixels} of ${result.totalPixels} " +
                "pixels differ, largest channel delta ${result.largestDelta?.describe()}. This test does not " +
                "use the tolerance policy: the rendering pipeline is expected to be exactly deterministic " +
                "within one run, and every other baseline rests on that."
        )
    }

    fun requirePinnedGpu() {
        if (allowsGpuMismatch()) {
            val pins = BaselineProfile.pins()
            if (!pins.isSwiftShader) {
                Log.w(
                    TAG,
                    "The GPU is \"${pins.gpuRenderer}\", not swiftshader, and $ALLOW_GPU_MISMATCH_ARGUMENT is " +
                        "set. Every pixel result in this run is suspect and no golden recorded here may be " +
                        "committed. The profile resolves to ${pins.profile}.",
                )
            }
            return
        }
        BaselineProfile.requireSwiftShader()
    }

    fun allowsGpuMismatch(): Boolean {
        val raw = runCatching {
            InstrumentationRegistry.getArguments().getString(ALLOW_GPU_MISMATCH_ARGUMENT)
        }.getOrNull() ?: return false
        return raw.equals("true", ignoreCase = true) || raw == "1"
    }

    private fun compareOrReportSize(
        testId: String,
        goldenName: String,
        expected: Bitmap,
        actual: Bitmap,
        policy: TolerancePolicy,
    ): ComparisonResult = try {
        PixelComparison.compare(expected, actual, policy)
    } catch (mismatch: BaselinePinMismatch) {
        BaselineArtifacts.writeSizeMismatch(
            testId, goldenName, expected, actual, mismatch.message.orEmpty(),
        )
        throw mismatch
    }

    private fun recordAndRefuse(
        profile: String,
        testId: String,
        goldenName: String,
        actual: Bitmap,
        expectation: GateExpectation,
        policy: TolerancePolicy,
    ): Nothing {
        val staged = BaselineRecording.stage(profile, goldenName, actual)
        val comparison = if (BaselineAssets.goldenExists(profile, goldenName)) {
            runCatching {
                val expected = BaselineAssets.readGolden(profile, goldenName)
                val result = PixelComparison.compare(expected, actual, policy)
                val reasons = result.verdict(policy)
                if (reasons.isEmpty()) {
                    "The staged image still matches the committed golden at " +
                        "${result.formattedPercent()} percent mismatch, so re recording changes nothing."
                } else {
                    BaselineArtifacts.writeMismatch(
                        testId, goldenName, expected, actual, result, policy, reasons,
                    )
                    "The staged image differs from the committed golden: ${reasons.joinToString("; ")}. " +
                        "The diff for it is in ${BaselineArtifacts.directoryFor(testId, goldenName).absolutePath}."
                }
            }.getOrElse { "The committed golden could not be compared against: $it" }
        } else {
            "No golden was committed for ${BaselineAssets.withPngSuffix(goldenName)} in profile $profile, " +
                "so this is a first recording and there was nothing to compare against."
        }
        Log.w(TAG, "$testId recorded ${staged.name} for ${expectation.contentKey}")
        throw BaselineRecorded(BaselineRecording.refusalMessage(profile, listOf(staged), comparison))
    }
}
